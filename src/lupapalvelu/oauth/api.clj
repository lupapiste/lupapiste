(ns lupapalvelu.oauth.api
  "OAuth API with the following features:

    - Supports both authorization codes and tokens, the former can be converted to the latter.
    - Token refresh
    - Consent and login UIs with l10n support
    - Consents are scoped and stored into user data. Thus, the consent is needed to be
      given only once.

  Implementation notes:

    - `/oauth/authorize` is the authorization starting point that is given
      `schemas/Fields` conformant parameters.
    - The authorization succeeds when

      1. The user has already consented earlier
      2. The user consents now (via UI)
      3. If the user is not currently logged in, she can consent with her credentials in
         the UI.

    - `/oauth/language` changes the OAuth UI language
    - `/oauth/login` checks the given credentials and updates consent, BUT DOES NOT create
      Lupapiste session. For AD login, sends a SAML request (see also
     `ad-login/process-relay-state` multimethod).
    - `/oauth/cancel` redirect either to given `cancel_callback` or fallbacks to client root.
    - `/oauth/logout` logouts the user from Lupapiste. The use case is that the user wants
      to authorize OAauth with the different credentials. After logout, the OAuth login
      page is shown.
    - `/oauth/consent` adds consent for the logged in user.
    - `/oauth/token` Either converts earlier received code to token
      (grant type is `authorization_code`) or refreshes token (`refresh_token`)"
  (:require [clj-http.client :as clj-http]
            [clojure.pprint :as pprint]
            [lupapalvelu.ident.ad-login :as ad-login]
            [lupapalvelu.ident.ad-login-util :as ad-util]
            [lupapalvelu.oauth.core :as oauth]
            [lupapalvelu.oauth.page :refer [page-response]]
            [lupapalvelu.oauth.schemas :as schemas :refer [FieldsOrError]]
            [lupapalvelu.user :as usr]
            [lupapalvelu.web :as web]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as resp]
            [ring.middleware.anti-forgery :as anti-forgery]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.timbre :as timbre])
  (:import [java.io StringWriter]))

(def bad-request
  {:status 400
   :body   (str "Missing or invalid parameters: scope must be present one or more times, "
                "other parameters should be present exactly once when required")})

(defn- client-redirect
  ([{:keys [client] :as fields} callback-key params hash?]
   (let [url (-> client :oauth :callback-url (str (callback-key fields)))]
     {:status  302
      :headers {"Location" (cond-> url
                             params (str (if hash? "#" "?")
                                         (clj-http/generate-query-string params)))}}))
  ([fields callback-key]
   (client-redirect fields callback-key nil false))
  ([fields callback-key params]
   (client-redirect fields callback-key params false)))

(defn error-redirect [fields error]
  (client-redirect fields :error_callback {:error error}))

(defn- parse-scope [scope]
  (->> (if (sequential? scope)
         scope
         (ss/split scope #","))
       (map ss/trim)
       (remove ss/blank?)))

(defn- resolve-anti-csrf [{anti-csrf-field :__anti-forgery-token} anti-csrf?]
  (let [anti-csrf (get-in (request/ring-request)
                          [:cookies web/anti-csrf-cookie-name :value])]
    (if (and anti-csrf?
             (not (and anti-csrf
                       (anti-forgery/secure-eql? anti-csrf anti-csrf-field))))
      :anti-csrf-check-failed!
      anti-csrf)))

(sc/defn ^:always-validate process-fields :- FieldsOrError
  ([{:keys [client_id scope lang response_type
            success_callback error_callback cancel_callback]
     :as   params} anti-csrf?]
   (let [request (request/ring-request)
         user    (some-> request
                         usr/current-user
                         :id
                         (usr/get-user-by-id schemas/user-keys))]
     (-> {:client           (some-> client_id
                                    usr/get-user-by-oauth-id
                                    (select-keys schemas/client-keys))
          :scope            scope
          :scope-vec        (parse-scope scope)
          :user             user
          :lang             (keyword (or lang (:language user :fi)))
          :response_type    response_type
          :success_callback success_callback
          :cancel_callback  cancel_callback
          :error_callback   error_callback
          :anti-csrf        (resolve-anti-csrf params anti-csrf?)}
         util/strip-nils
         schemas/validate-fields)))
  ([params]
   (process-fields params false)))

(defn- error-response [{:keys [status body]}]
  (timbre/error "OAuth error." body)
  (let [w (StringWriter.)]
    (pprint/pprint body w)
    {:status status
     :body   (str "ERROR\n\n" w)}))

(defn- success-response
  ([{:keys [response_type scope-vec user client] :as fields} csrf-check?]
   (let [redirect-fn (partial client-redirect fields :success_callback)
         response    (if (= response_type "token")
                       (redirect-fn {:token (oauth/grant-access-token client scope-vec user)} true)
                       (redirect-fn {:code (oauth/grant-authorization-code client scope-vec user)}))]
     (if csrf-check?
       (anti-forgery/crosscheck-token
         (constantly response)
         (request/ring-request)
        web/anti-csrf-cookie-name
        web/csrf-attack-handler)
       response)))
  ([fields]
   (success-response fields false)))

(defn- ad-login-redirect
  "Initiate AD login for the user authentication.."
  [fields email]
  (let [domain (ad-util/email-domain email)]
    (->> (select-keys fields [:scope :lang :response_type
                              :success_callback :error_callback :cancel_callback])
         (merge {:target    :oauth
                 :client_id (oauth/client-id fields)})
         (ad-login/saml-request-redirect domain))))

(defpage [:get "/oauth/authorize"] params
  (let [{:keys [error] :as fields} (process-fields params)]
    (cond
      error                     (error-response error)
      (oauth/consented? fields) (success-response fields)
      :else                     (page-response fields))))

(defpage [:post "/oauth/cancel"] params
  (let [{:keys [error cancel_callback] :as fields} (process-fields params)]
    (cond
      error           (error-response error)
      cancel_callback (client-redirect fields :cancel_callback)
      :else           (error-redirect fields "authorization_cancelled"))))

(defpage [:post "/oauth/consent"] params
  (let [{:keys [error user] :as fields} (process-fields params)]
    (cond
      error       (error-response error)
      (nil? user) bad-request
      :else       (-> fields oauth/add-consent success-response))))

(defpage [:post "/oauth/login"] {:keys [username password] :as params}
  (let [username       (ss/canonize-email username)
        {:keys [error user]
         :as   fields} (process-fields params)
        login-page     (partial page-response fields
                                :username username
                                :password password
                                :form-error)]
    (cond
      error                                (error-response error)
      user                                 bad-request
      (ad-login/ad-login? username)        (ad-login-redirect fields username)
      (some ss/blank? [username password]) (login-page :error.login)
      (usr/throttle-login? username)       (login-page :error.login-throttle)
      :else
      (if-let [user (usr/get-user-with-password username password)]
        (let [fields       (assoc fields :user user)
              user-warning (oauth/check-user fields)]
          (usr/clear-logins username)
          (if user-warning
            (login-page user-warning)
            (-> fields oauth/add-consent success-response)))
        (login-page :error.login)))))

(defpage [:post "/oauth/logout"] params
  (let [{:keys [error] :as fields} (process-fields params)]
    (if error
      (error-response error)
      (merge (web/kill-session!)
             (page-response (dissoc fields :user))))))

(defpage [:post "/oauth/language/:new-lang"] {:keys [new-lang] :as params}
  (let [{:keys [error] :as fields} (process-fields (assoc params :lang new-lang))]
    (if error
      (error-response error)
      (page-response fields))))

(defpage [:post "/oauth/token"]
  {:keys [client_id client_secret grant_type code refresh_token]}
  (letfn [(token-fn [fun err-msg & args]
            (if (some ss/blank? args)
              bad-request
              (if-let [token-response (apply fun args)]
                (resp/json token-response)
                {:status 401 :body err-msg})))]

    (case grant_type
     "authorization_code"
     (token-fn oauth/access-token-response
               "Invalid client credentials or authorization code"
               client_id client_secret code)

     "refresh_token"
     (token-fn oauth/refresh-token-response
               "Refresh token failed."
               client_id client_secret refresh_token)

     {:status 400
      :body "Unknown grant type"})))

(defmethod ad-login/process-relay-state :oauth
  [{:keys [relay-state user error]}]
  (try
    (let [{f-error :error f-user :user
           :as     fields} (process-fields relay-state)]
      (cond
        (or error f-error) (error-response (or error f-error))
        f-user             bad-request
        (not user)         (page-response fields :form-error :oauth.warning.ad-login-failed)
        :else
        (let [fields'      (assoc fields :user user)
              user-warning (oauth/check-user fields')]
          (if user-warning
            (page-response fields :username (:email user) :form-error user-warning)
            (-> fields' oauth/add-consent success-response)))))
    (catch Exception e
      (error-response {:status 400
                       :body   (str "OAuth AD login failed. " (ex-message e))}))))
