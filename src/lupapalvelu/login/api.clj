(ns lupapalvelu.login.api
  "Login and related semi-static pages.
  In the following endpoint (functionality) definitions, :lang parameter is always
  optional and its value eventually is :fi or :sv. Unsupported languages resolved to
  user's language and ultimately Finnish. For POST endpoints :lang is given as (typically
  hidden) form parameter.

  GET /login/:lang

  Show login page.

  POST /login

  Depending on the parameters and organization configurations, the result is:

  - New login page with error note
  - User is resolved, logged in and redirected their 'home view'.
  - Blank inputs are not considered errors. Login page is simply returned as is.
  - If the username matches an AD domain for any organization, the corresponding
    redirection is returned.

  GET /reset-password/:lang

  Show password reset page.

  POST /reset-password

  The result depends on the given email:

  - Blank: page is returned as is
  - Invalid (ill-formatted): Page with error note.

  - Valid: Page with note informing that reset link is sent. For security reasons, we do
    not tell whether the email is actually used in Lupapiste or not.

  GET /info/:lang

  Information page that combines multiple HTML resources.

  GET /page/:page-id/:lang

  Page for an individual :page-id.:lang.html resource (or 404)."
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.ident.ad-login :as ad-login]
            [lupapalvelu.login.page :refer [page-response page-content-html]]
            [lupapalvelu.password-reset :as pw-reset]
            [lupapalvelu.user :as usr]
            [lupapalvelu.web :as web]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as resp]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.util.response :as ring-resp]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as sv]))

(defn- resolve-anti-csrf
  "Anti-CSRF is not currently enforced."
  [{anti-csrf-field :__anti-forgery-token} anti-csrf?]
  (let [anti-csrf (get-in (request/ring-request)
                          [:cookies web/anti-csrf-cookie-name :value])]
    (if (and anti-csrf?
             (not (and anti-csrf
                       (anti-forgery/secure-eql? anti-csrf anti-csrf-field))))
      :anti-csrf-check-failed!
      anti-csrf)))

(defn view-fields [fields]
  (->> (case (:view fields)
         :login          [:username :password :hide-next? :form-error]
         :reset-password [:username :sent-email :form-error]
         :page           [:page-id]
         [])
       (concat [:lang :view :anti-csrf :logged-in?])
       (select-keys fields)))

(defn- process-fields
  ([{:keys [lang hide-next? page-id] :as params}]
   (let [view   (keyword (:view params :login))
         user   (usr/current-user (request/ring-request))
         fields (-> {:lang       (keyword (if (i18n/supported-lang? lang)
                                            lang
                                            (:language user :fi)))
                     :logged-in? (boolean user)
                     :hide-next? (boolean hide-next?)
                     :anti-csrf  (resolve-anti-csrf params true)
                     :view       view
                     :page-id    (ss/lower-case page-id)}
                    (merge (select-keys params [:username :password
                                                :form-error :sent-email]))
                    util/strip-nils)]
     (view-fields fields)))
  ([params view]
   (process-fields (assoc params :view view))))

(defn- success-response [{:keys [lang]} {:keys [applicationpage session] :as response}]
  (let [url          (format "/app/%s/%s"
                             (name (or (:lang response) lang)) applicationpage)
        redirect-url (:redirect-after-login session)]
    (-> (cond
          (nil? redirect-url)               url
          (ss/starts-with redirect-url "/") redirect-url
          :else                             (str url "#!/" redirect-url))
        resp/redirect
        (merge (select-keys response [:session :cookies])))))

(defn- already-logged-in []
  (when (usr/current-user (request/ring-request))
    (resp/redirect "/")))

(defpage [:post "/login"] {:keys [username password] :as params}
  (or (already-logged-in)
      (let [request      (request/ring-request)
            username     (ss/canonize-email username)
            fields       (process-fields params :login)
            login-page   (fn [error]
                           (page-response (assoc fields
                                                 :form-error error
                                                 :hide-next? true)))
            ad-login-uri (ad-login/ad-login-uri username)]
        (cond
          ad-login-uri                         (resp/redirect ad-login-uri)
          (some ss/blank? [username password]) (login-page nil)
          (usr/throttle-login? username)       (login-page :error.login-throttle)
          :else
          (let [{ok? :ok :as response} (usr/login request username password)]
            (if ok?
              (success-response fields response)
              (login-page (:text response))))))))

(defn- get-view-response
  ([view lang]
   (or (already-logged-in)
       (page-response (process-fields {:lang lang} view))))
  ([view]
   (get-view-response view :fi)))

(defpage [:get "/login/:lang"] {:keys [lang]}
  (get-view-response :login lang))

(defpage [:get "/login"] _
  (get-view-response :login))

(defpage [:get "/reset-password"] _
  (get-view-response :reset-password))

(defpage [:get "/reset-password/:lang"] {lang :lang}
  (get-view-response :reset-password lang))

(defpage [:post "/reset-password"] params
  (or (already-logged-in)
      (let [{:keys [username]
             :as   fields} (process-fields params :reset-password)
            email          (ss/canonize-email username)
            blank?         (ss/blank? username)
            valid?         (sv/valid-email? email)]
        ;; Every valid email is considered success, since we do not want to leak whether
        ;; the email is in the system or not.
        (when valid?
          (pw-reset/reset-password-by-email email))
        (page-response (assoc fields
                              :sent-email (when valid? email)
                              :form-error (when-not (or valid? blank?)
                                            :error.email))))))

(defpage [:get "/info/:lang"] {lang :lang}
  (page-response (process-fields {:lang lang} :info)))

(defpage [:get "/info"] _
  (page-response (process-fields {} :info)))

(defn- page-or-bust [{:keys [page-id] :as params}]
  (let [fields (process-fields params :page)
        html   (page-content-html fields page-id)]
    (if html
      (page-response (assoc fields :html html))
      (ring-resp/not-found page-id))))

(defpage [:get "/page/:page-id/:lang"] {:keys [page-id] :as params}
  (page-or-bust params))

(defpage [:get "/page/:page-id"] {:keys [page-id] :as params}
  (page-or-bust params))
