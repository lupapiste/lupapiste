(ns lupapalvelu.autologin
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]
            [slingshot.slingshot :refer [throw+ try+]]
            [clojure.core.memoize :as memo]
            [pandect.core :as pandect]
            [monger.operators :refer :all]
            [noir.response :as resp]
            [sade.core :refer :all]
            [sade.crypt :as crypt]
            [sade.env :as env]
            [sade.http :as http]
            [sade.util :as util]
            [sade.strings :as ss]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.user :as user]))


(def cache-ttl (* 10 60 1000)) ; 10 min

; salasana = aikaleima + "_" + hash
; hash = HMAC-SHA256(tunnus + IP + aikaleima, salaisuus)

(defn- parse-ts-hash [password]
  (when-not (ss/blank? password)
    (let [parts (re-matches #"(\d{13})_([0-9a-f]{64})" password)]
      (rest parts))))

(defn- valid-hash? [hash username ip ts secret]
  (if secret
    (let [expected-hash (pandect/sha256-hmac (str username ip ts) secret)]
      (boolean (or (= hash expected-hash) (error "Expected hash" expected-hash "of" username ip ts "- got" hash ))))
    false))

(def five-min-in-ms (* 5 60 1000))

(defn- valid-timestamp? [ts now]
  (let [delta (util/abs (- now (util/to-long ts)))]
    (boolean (or (< delta five-min-in-ms) (debug "Too much time difference" delta)))))

(defn- load-secret-from-db [ip]
  (let [{:keys [key crypto-iv] } (mongo/select-one :ssoKeys {:ip ip} {:key 1 :crypto-iv 1})
        master-key (env/value :sso :basic-auth :crypto-key)]
    (if (and key crypto-iv)
      (if-not (ss/blank? master-key)
        (try
          (crypt/decrypt-aes-string key master-key crypto-iv)
          (catch Exception e
            (error "Failed to decrypt" key crypto-iv (.getMessage e))))
        (error "Failed to load master key"))
      (error "No key for" ip))))

(def load-secret (memo/ttl load-secret-from-db :ttl/threshold cache-ttl))

(def allowed-ip? (memo/ttl organization/allowed-ip? :ttl/threshold cache-ttl))

(defn autologin [request]
  (let [[email password] (http/decode-basic-auth request)
        ip (http/client-ip request)
        [ts hash] (parse-ts-hash password)]

    (when (and ts hash
            (valid-hash? hash email ip ts (load-secret ip))
            (valid-timestamp? ts (now)))
      (let [user (user/get-user-by-email (user/canonize-email email))
            organization-ids (user/organization-ids user)]

        (trace "autologin (if allowed by organization)" (user/session-summary user))

        (cond
          (not (:enabled user)) (throw+ {:type ::autologin :text "User not enabled"})
          (not (some (partial allowed-ip? ip) organization-ids)) (throw+ {:type ::autologin :text "Illegal IP address"})
          :else (user/session-summary user))))))

(defn catch-autologin-failure [handler]
  (fn [request]
    (try+
      (handler request)
      (catch [:type ::autologin] {text :text}
        (resp/status 403 text)))))
