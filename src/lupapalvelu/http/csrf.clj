(ns lupapalvelu.http.csrf
  "CSRF token handling for login responses and request validation."
  (:require [noir.request :as request]
            [ring.middleware.anti-forgery :as anti-forgery]
            [sade.env :as env]
            [sade.strings :as ss]))

(def anti-csrf-cookie-name (or (ss/blank-as-nil (env/value :anti-csrf-cookie-name)) "anti-csrf-token"))

(defn new-csrf-cookie
  "Generate a new CSRF token cookie for login response.
   Returns nil if anti-csrf is disabled."
  []
  (when-not (env/feature? :disable-anti-csrf)
    (let [cookie-attrs (dissoc (env/value :cookie) :http-only)]
      (anti-forgery/new-csrf-token-cookie anti-csrf-cookie-name cookie-attrs))))

(defn resolve-anti-csrf
  "Anti-CSRF is not currently enforced."
  [{anti-csrf-field :__anti-forgery-token} anti-csrf?]
  (let [anti-csrf (get-in (request/ring-request)
                          [:cookies anti-csrf-cookie-name :value])]
    (if (and anti-csrf?
             (not (and anti-csrf
                       (anti-forgery/secure-eql? anti-csrf anti-csrf-field))))
      :anti-csrf-check-failed!
      anti-csrf)))
