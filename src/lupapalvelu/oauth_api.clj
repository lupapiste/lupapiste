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
   :body   (str "Missing or invalid parameters: scope must be present one or more times, "
                "other parameters should be present exactly once when required")})

(defn bad-scope [scopes]
  {:status 403
   :body   (str "Invalid scope: " (ss/join ", " scopes))})

(defn invalid-scopes? [scopes client]
  (not (set/subset? (set scopes) (set (get-in client [:oauth :scopes])))))

(defn error-redirect [client reason]
  {:status  307
   :headers {"Location" (str (get-in client [:oauth :callback :failure-url]) "?error=" reason)}})

(defn- parse-scope [scope]
  (->> (if (sequential? scope)
         scope
         (ss/split scope #","))
       (map ss/trim)
       (remove ss/blank?)))

(defpage [:get "/oauth/authorize"]
  {:keys [client_id scope lang response_type]}
  (let [{:keys [uri query-string] :as req} (request/ring-request)
        client (when client_id (usr/get-user-by-oauth-id client_id))
        user (usr/current-user req)
        scope-vec (parse-scope scope)]
    (cond
      (not (:id user))
      (ssess/merge-to-session
        req
        (resp/redirect (str (env/value :host) "/app/" (or lang "fi") "/welcome#!/login"))
        {:redirect-after-login (str uri "?" query-string)})

      (not (and (seq scope-vec) client (string? lang) (string? response_type)))
      bad-request

      (invalid-scopes? scope-vec client)
      (bad-scope scope-vec)

      (oauth/payment-required-but-not-available? scope-vec user)
      (error-redirect client "cannot_pay")

      :else
      (hiccup.core/html
        (oauth/authorization-page-hiccup client
                                         scope-vec
                                         lang
                                         user
                                         response_type
                                         (get-in req [:cookies web/anti-csrf-cookie-name :value]))))))

(defpage [:post "/oauth/authorize"]
  {:keys [client_id scope lang accept cancel response_type]}
  (let [client (usr/get-user-by-oauth-id client_id)
        user (usr/current-user (request/ring-request))
        scope-vec (parse-scope scope)]
    (cond
      (not (:id user))
      (web/redirect-after-logout lang)

      cancel
      (error-redirect client "authorization_cancelled")

      (not (and client (seq scope-vec) (string? lang) (string? accept)))
      bad-request

      (invalid-scopes? scope-vec client)
      (bad-scope scope-vec)

      (oauth/payment-required-but-not-available? scope-vec user)
      (error-redirect client "cannot_pay")

      :else
      (anti-forgery/crosscheck-token
        (fn [_]
          (let [token (if (= response_type "token")
                        (str "#token=" (oauth/grant-access-token client scope-vec user))
                        (str "?code=" (oauth/grant-authorization-code client scope-vec user)))]
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
