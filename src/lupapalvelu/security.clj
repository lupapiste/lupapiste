(ns lupapalvelu.security
  (:require [sade.strings :as ss]
            [sade.env :as env]
            [sade.core :refer :all]
            [clojure.data.codec.base64 :as base64])
  (:import [org.mindrot.jbcrypt BCrypt]))

;;
;; Password generation and checking:
;;

(def- token-chars (concat (range (int \0) (inc (int \9)))
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
  (when (and candidate (not (ss/blank? hashed)))
    (BCrypt/checkpw candidate hashed)))

(defn- decode-base64 [string]
  (try
    (apply str (map char (base64/decode (.getBytes string))))
    (catch Exception _)))

(defn check-credentials-from-basic-auth
  [request pwd-hash-fn]
  (let [auth ((:headers request) "authorization")
        cred (and auth (decode-base64 (last (re-find #"^Basic (.*)$" auth))))
        [user pass] (and cred (ss/split (str cred) #":" 2))]
    (= pass (pwd-hash-fn user))))