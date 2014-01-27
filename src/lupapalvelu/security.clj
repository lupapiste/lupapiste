(ns lupapalvelu.security
  (:require [sade.env :as env])
  (:import [org.mindrot.jbcrypt BCrypt]))

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

(defn dispense-salt []
  (BCrypt/gensalt (or (env/value :salt-strength) 10)))

(defn get-hash
  ([password]
    (get-hash password (dispense-salt)))
  ([password salt]
    (BCrypt/hashpw password salt)))

(defn check-password [candidate hashed]
  (when (and candidate hashed)
    (BCrypt/checkpw candidate hashed)))
