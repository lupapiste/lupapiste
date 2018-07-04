(ns lupapalvelu.ssokeys
  (:require [lupapalvelu.mongo :as mongo]
            [schema.core :as sc]
            [sade.core :refer [fail fail!]]
            [sade.env :as env]
            [sade.crypt :as crypt]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.validators :as v])
  (:import  [com.mongodb DuplicateKeyException]))

(sc/defschema SsoKey
  {:id                        ssc/ObjectIdStr
   :ip                        ssc/IpAddress
   :key                       sc/Str
   :crypto-iv                 sc/Str
   (sc/optional-key :comment) sc/Str})

(sc/defschema UnencryptedKey (ssc/min-length-string 20))

(defn validate-ip [ip]
  (when-not (v/ip-address? ip) (fail :error.illegal-ip-address)))

(defn validate-id [id]
  (when-not (nil? (sc/check ssc/ObjectIdStr id)) (fail :error.invalid-id)))

(defn validate-key [key]
  (when-not ((some-fn ss/blank? (comp nil? (sc/checker UnencryptedKey))) key) (fail :error.invalid-key)))

(defn- encode-key [secret-key]
  (let [crypto-iv   (crypt/make-iv-128)
        crypted-key (crypt/encrypt-aes-string secret-key (env/value :sso :basic-auth :crypto-key) crypto-iv)
        crypto-iv-s (-> crypto-iv crypt/base64-encode crypt/bytes->str)]
    {:key crypted-key :crypto-iv crypto-iv-s}))

(defn update-sso-key [sso-key ip secret-key comment]
  (->> {:ip ip
        :comment comment}
       (merge sso-key (when-not (ss/blank? secret-key) (encode-key secret-key)))
       (remove (comp ss/blank? val))
       (into {})
       (sc/validate SsoKey)))

(defn create-sso-key [ip secret-key comment]
  {:pre [(not (ss/blank? ip))
         (not (ss/blank? secret-key))]}
  (update-sso-key {:id (mongo/create-id)} ip secret-key comment))

(defn update-to-db [{id :id :as sso-key}]
  (try
    (mongo/update-by-id :ssoKeys id (mongo/with-_id sso-key) :upsert true)
    (catch DuplicateKeyException e (fail! :error.ip-already-in-use)))
  id)

(defn remove-from-db [id]
  (mongo/remove :ssoKeys id))

(defn get-sso-key-by-id [id]
  (or (mongo/select-one :ssoKeys {:_id id})
      (fail! :error.unknown-id)))

(defn get-all-sso-keys []
  (mongo/select :ssoKeys {} {:_id 1 :ip 1 :comment 1}))
