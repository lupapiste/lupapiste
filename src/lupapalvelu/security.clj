(ns lupapalvelu.security
  (:require [lupapalvelu.crypto :as crypt]
            [lupapalvelu.logging :refer [warn]]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.password :as pw]
            [sade.strings :as ss])
  (:import [org.mindrot.jbcrypt BCrypt]))

(set! *warn-on-reflection* true)

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

(defn valid-password-length? [password]
  (>= (count password) (env/value :password :minlength)))  ; length should match the length in util.js

(defn valid-password-score? [password user]
  ;; score should match util.js
  (let [min-score  (env/value :password :minscore)]
    (>= (pw/measure-password-quality password user) min-score)))

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
    (-> string crypt/str->bytes crypt/base64-decode crypt/bytes->str)
    (catch Exception _)))

(defn check-credentials-from-basic-auth
  [request pwd-hash-fn]
  (let [auth ((:headers request) "authorization")
        cred (and auth (decode-base64 (last (re-find #"^Basic (.*)$" auth))))
        [user pass] (and cred (ss/split (str cred) #":" 2))]
    (= pass (pwd-hash-fn user))))

(defn valid-password-length-validator [pw-parameter command]
  (when-let [pw (get-in command [:data pw-parameter])]
    (when-not (valid-password-length? pw)
      (fail :error.password.minlength))))

(defn valid-password-score-validator [pw-parameter user-parameter command]
  (when-let [pw (get-in command [:data pw-parameter])]
    (when-let [user (get command user-parameter)]
      (when-not (valid-password-score? pw user)
        (warn "Frontend failed to evaluate password score!")
        (fail :error.password.minscore)))))
