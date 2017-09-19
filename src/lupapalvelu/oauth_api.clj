(ns lupapalvelu.oauth-api
  (:require [sade.session :as ssess]
            [noir.response :as resp]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [lupapalvelu.user :as usr]
            [sade.env :as env]
            [clojure.set :as set]
            [sade.strings :as ss]
            [lupapalvelu.oauth :as oauth]
            [ring.middleware.anti-forgery :as anti-forgery]
            [lupapalvelu.web :as web]))

(def bad-request
  {:status 400
   :body   "Missing or invalid parameters"})

(defn bad-scope [scope]
  {:status 403
   :body   (str "Invalid scope: " scope)})

(defn invalid-scopes? [scope client]
  (not (set/subset? (set (ss/split scope #",")) (set (get-in client [:oauth :scopes])))))

(defn error-redirect [client reason]
  {:status  307
   :headers {"Location" (str (get-in client [:oauth :callback :failure-url]) "?error=" reason)}})

(defpage [:get "/oauth/authorize"]
  {:keys [client_id scope lang response_type]}
  (let [{:keys [uri query-string] :as req} (request/ring-request)
        client (when client_id (usr/get-user-by-oauth-id client_id))
        user (usr/current-user req)]
    (cond
      (not (:id user))
      (ssess/merge-to-session
        req
        (resp/redirect (str (env/value :host) "/app/" (or lang "fi") "/welcome#!/login"))
        {:redirect-after-login (str uri "?" query-string)})

      (not (and client scope lang response_type))
      bad-request

      (invalid-scopes? scope client)
      (bad-scope scope)

      (oauth/payment-required-but-not-available? scope user)
      (error-redirect client "cannot_pay")

      :else
      (hiccup.core/html
        (oauth/authorization-page-hiccup client
                                         scope
                                         lang
                                         user
                                         response_type
                                         (get-in req [:cookies web/anti-csrf-cookie-name :value]))))))

(defpage [:post "/oauth/authorize"]
  {:keys [client_id scope lang accept cancel response_type]}
  (let [client (usr/get-user-by-oauth-id client_id)
        user (usr/current-user (request/ring-request))]
    (cond
      (not (:id user))
      (web/redirect-after-logout lang)

      cancel
      (error-redirect client "authorization_cancelled")

      (not (and client scope lang accept))
      bad-request

      (invalid-scopes? scope client)
      (bad-scope scope)

      (oauth/payment-required-but-not-available? scope user)
      (error-redirect client "cannot_pay")

      :else
      (anti-forgery/crosscheck-token
        (fn [_]
          (let [token (if (= response_type "token")
                        (str "#token=" (oauth/grant-access-token client scope user))
                        (str "?code=" (oauth/grant-authorization-code client scope user)))]
            {:status  307
             :headers {"Location" (str (get-in client [:oauth :callback :success-url]) token)}}))
        (request/ring-request)
        web/anti-csrf-cookie-name
        web/csrf-attack-hander))))

(defpage [:post "/oauth/token"]
  {:keys [client_id client_secret grant_type code]}
  (cond
    (not (and client_id client_secret grant_type code))
    bad-request

    (not= grant_type "authorization_code")
    {:status 400
     :body "Unknown grant type"}

    :else
    (if-let [token-response (oauth/access-token-response client_id client_secret code)]
      (resp/json token-response)
      {:status 401 :body "Invalid client credentials or authorization code"})))