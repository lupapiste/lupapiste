(ns lupapalvelu.ssokeys
  (:require [lupapalvelu.mongo :as mongo]
            [schema.core :as sc]
            [sade.core :refer [fail fail!]]
            [sade.env :as env]
            [sade.crypt :as crypt]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.validators :as v]))

(sc/defschema SsoKey
  {:id                        ssc/ObjectIdStr
   :ip                        ssc/IpAddress
   :key                       sc/Str
   :crypto-iv                 sc/Str
   (sc/optional-key :comment) sc/Str})

(sc/defschema RawKey (ssc/min-length-string 6))

(defn validate-ip [ip]
  (when-not (v/ip-address? ip) (fail :error.illegal-ip-address)))

(defn validate-id [id]
  (when-not (sc/check ssc/ObjectIdStr id) (fail :error.invalid-id)))

(defn validate-key [key]
  (when-not (sc/check RawKey key) (fail :error.illegal-key)))

(defn- encode-key [secret-key]
  (let [crypto-iv   (crypt/make-iv-128)
        crypted-key (crypt/encrypt-aes-string secret-key (env/value :sso :basic-auth :crypto-key) crypto-iv)
        crypto-iv-s (-> crypto-iv crypt/base64-encode crypt/bytes->str)]
    {:key crypted-key :crypto-iv crypto-iv-s}))

(defn create-sso-key [ip secret-key comment]
  (sc/validate SsoKey
               (cond->   {:id       (mongo/create-id)
                          :ip       ip}
                 true    (merge (encode-key secret-key))
                 comment (assoc :comment comment))))

(defn update-sso-key [sso-key ip comment]
  (sc/validate SsoKey
               (cond->   sso-key
                 ip      (assoc :ip ip)
                 comment (assoc :comment comment)
                 (ss/blank? comment) (dissoc :comment))))

(defn update-to-db [{id :id :as sso-key}]
  (mongo/update-by-id :ssoKeys id sso-key :upsert true)
  id)

(defn remove-from-db [id]
  (mongo/remove :ssoKeys id))

(defn get-sso-key-by-id [id]
  (or (mongo/select-one :ssoKeys {:id id})
      (fail! :error.unknown-id)))

(defn get-all-sso-keys []
  (mongo/select :ssoKeys {} {:id 1 :ip 1 :comment 1}))
