(ns lupapalvelu.security
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.strings :as s]
            [sade.env :as env]
            [noir.request :as request]
            [noir.session :as session])
  (:import [org.mindrot.jbcrypt BCrypt]
           [com.mongodb MongoException MongoException$DuplicateKey]))

;;
;; Password generation and checking:
;;

(def ^:private token-chars (concat (range (int \0) (inc (int \9)))
                                   (range (int \A) (inc (int \Z)))
                                   (range (int \a) (inc (int \z)))))

(defn random-password
  ([]
    (random-password 40))
  ([len]
    (apply str (repeatedly len (comp char (partial rand-nth token-chars))))))

(defn valid-password? [password]
    (>= (count password) (env/value :password :minlength)))  ; length should match the length in util.js

(defn get-hash [password salt]
  (BCrypt/hashpw password salt))

(defn dispense-salt []
  (BCrypt/gensalt (or (env/value :salt-strength) 10)))

(defn check-password [candidate hashed]
  (BCrypt/checkpw candidate hashed))
