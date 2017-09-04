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

(defpage [:get "/oauth/authorize"]
  {:keys [client_id scope lang]}
  (let [{:keys [uri query-string] :as req} (request/ring-request)
        client (when client_id (usr/get-user-by-oauth-id client_id))
        user (usr/current-user req)]
    (cond
      (not (:id user))
      (ssess/merge-to-session
        req
        (resp/redirect (str (env/value :host) "/app/" (or lang "fi") "/welcome#!/login"))
        {:redirect-after-login (str uri "?" query-string)})

      (not (and client scope lang))
      {:status 400
       :body   "Missing or invalid parameters"}

      (not (set/subset? (set (ss/split scope #",")) (set (get-in client [:oauth :scopes]))))
      {:status 403
       :body   (str "Invalid scope: " scope)}

      (oauth/payment-required-but-not-available? scope user)
      {:status  307
       :headers {"Location" (str (get-in client [:oauth :callback :failure-url]) "?error=cannot_pay")}}

      :else
      (hiccup.core/html
        (oauth/authorization-page-hiccup client
                                         scope
                                         lang
                                         user
                                         (get-in req [:cookies web/anti-csrf-cookie-name :value]))))))

(defpage [:post "/oauth/authorize"]
  {:keys [client_id scope lang accept cancel]}
  (let [client (usr/get-user-by-oauth-id client_id)
        user (usr/current-user (request/ring-request))]
    (cond
      (not (:id user))
      (web/redirect-after-logout lang)

      cancel
      {:status  307
       :headers {"Location" (str (get-in client [:oauth :callback :failure-url]) "?error=authorization_cancelled")}}

      (not (and client scope lang accept))
      {:status 400
       :body   "Missing or invalid parameters"}

      (not (set/subset? (set (ss/split scope #",")) (set (get-in client [:oauth :scopes]))))
      {:status 403
       :body   (str "Invalid scope: " scope)}

      (oauth/payment-required-but-not-available? scope user)
      {:status  307
       :headers {"Location" (str (get-in client [:oauth :callback :failure-url]) "?error=cannot_pay")}}

      :else
      (anti-forgery/crosscheck-token
        (fn [request]
          (let [token (oauth/grant-access-token client scope user)]
            {:status  307
             :headers {"Location" (str (get-in client [:oauth :callback :success-url]) "?token=" token)}}))
        (request/ring-request)
        web/anti-csrf-cookie-name
        web/csrf-attack-hander))))
