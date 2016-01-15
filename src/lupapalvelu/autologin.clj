(ns lupapalvelu.autologin
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]
            [clojure.core.memoize :as memo]
            [pandect.core :as pandect]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.crypt :as crypt]
            [sade.env :as env]
            [sade.http :as http]
            [sade.util :as util]
            [sade.strings :as ss]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.user :as user]))

; salasana = aikaleima + "_" + hash
; hash = HMAC-SHA256(tunnus + IP + aikaleima, salaisuus)

(defn- parse-ts-hash [password]
  (when-not (ss/blank? password)
    (let [parts (re-matches #"(\d{13})_([0-9a-f]{64})" password)]
      (rest parts))))

(defn- valid-hash? [hash username ip ts secret]
  (let [expected-hash (pandect/sha256-hmac (str username ip ts) secret)]
    (boolean (or (= hash expected-hash) (error "Expected hash" expected-hash "of" username ip ts "- got" hash )))))

(def five-min-in-ms (* 5 60 1000))

(defn- valid-timestamp? [ts now]
  (let [delta (util/abs (- now (util/to-long ts)))]
    (boolean (or (< delta five-min-in-ms) (debug "Too much time difference" delta)))))

(defn- load-secret [ip]
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

(defn allowed-ip? [ip organization-id]
  (organization/allowed-ip? ip organization-id))

#_(def allowed-ip?
   (memo/ttl organization/allowed-ip? :ttl/threshold 10000))

(defn autologin [request]
  (let [[email password] (http/decode-basic-auth request)
        ip (http/client-ip request)
        [ts hash] (parse-ts-hash password)]

    (debug (:uri request) "- X-Debug:" (get-in request [:headers "x-debug"]))

    (when (and ts hash
            (env/feature? :louhipalvelin)
            (valid-hash? hash email ip ts (load-secret ip))
            (valid-timestamp? ts (now)))
      (let [user (user/get-user-by-email email)
            organization-ids (user/organization-ids user)]

        (debug "autologin" (user/session-summary user))

        (when (and (seq organization-ids) (some (partial allowed-ip? ip) organization-ids))
          (user/session-summary user))))))
