(ns lupapalvelu.autologin
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]
            [clojure.core.memoize :as memo]
            [pandect.core :as pandect]
            [monger.operators :refer :all]
            [sade.core :refer :all]
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
  (debug "Hashing" username ip ts "with" secret)
  (let [expected-hash (pandect/sha256-hmac (str username ip ts) secret)]
    (if (= hash expected-hash)
      true
      (do
        (error "Expected hash" expected-hash "got" hash)
        false))))

(def five-min-in-ms (* 5 60 1000))

(defn- valid-timestamp? [ts now]
  (if (and now ts)
    (let [delta (util/abs (- now (util/to-long ts)))]
      (if (< delta five-min-in-ms)
        true
        (do
          (debug "Too much time difference" delta)
          false))
      )
    false))

(defn- load-secret [ip]
  "LUPAPISTE"
  #_(when-let [key (mongo/select-one :ssoKeys {:ip ip} {:key 1 :crypto-iv 1})]
     ; TODO
     (if (:crypto-iv key)
       key
       key)
     )
  )

(defn- allowed-ip? [ip organizations]
  true ; TODO
  )

(defn autologin [request]
  (let [[email password] (http/decode-basic-auth request)
        ip (http/client-ip request)
        secret (load-secret ip)
        [ts hash] (parse-ts-hash password)]

    (debug "Debug header" (get-in request [:headers "x-debug"]))

    (when (and secret ts hash
            (env/feature? :louhipalvelin)
            (valid-hash? hash email ip ts secret)
            (valid-timestamp? ts (now)))
      (let [user (user/get-user-by-email email)
            organizations (user/users-organizations user)]
        (when (allowed-ip? ip organizations)
          (user/session-summary user))))))
