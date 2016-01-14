(ns lupapalvelu.autologin
  (:require [pandect.core :as pandect]
            [sade.core :refer :all]
            [sade.http :as http]
            [sade.util :as util]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.user :as user]))

; salasana = aikaleima + ”_” + hash
; hash = HMAC-SHA256(tunnus + IP + aikaleima, salaisuus)

(defn- parse-ts-hash [password]
  (when password
    (let [parts (re-matches #"(\d{13})_([0-9a-f]{64})" password)]
      (rest parts))))

(defn- valid-hash? [hash username ip ts secret]
  (let [expected-hash (pandect/sha256-hmac (str username ip ts) secret)]
    (= hash expected-hash)))

(def five-min-in-ms (* 5 60 1000))

(defn- valid-timestamp? [ts now]
  (if (and now ts)
    (let [delta (util/abs (- now (util/to-long ts)))]
      (< delta five-min-in-ms))
    false))

(defn autologin [request]
  (when-let [[username password] (http/decode-basic-auth request)]
    (let [ip nil ; TODO
          secret nil ; TODO resolve by ID
          [ts hash] (parse-ts-hash password)]
      (when (and secret ts hash
                 (valid-hash? hash username ip ts secret)
                 (valid-timestamp?))
        ; TODO
        ; load user
        ; check ip is enebled in user's organization
        ))))
