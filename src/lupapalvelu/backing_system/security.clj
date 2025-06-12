(ns lupapalvelu.backing-system.security
  (:require [lupapalvelu.crypto :as crypt]
            [sade.env :as env]
            [sade.strings :as ss]))

(defn- encrypt [plaintext crypto-iv]
  (crypt/encrypt-aes-string plaintext
                            (env/value :backing-system :crypto-key)
                            crypto-iv))

(defn- non-blanks? [& args]
  (every? ss/not-blank? args))

(defn encode-credentials
  "Encodes `username` and `password` into `username`, (encrypted) `password`, `crypto-iv`
  map. Nil if `username` or `password` is blank."
  ([username password]
   (when (non-blanks? username password)
     (let [crypto-iv        (crypt/make-iv-128)
           crypted-password (encrypt password crypto-iv)
           crypto-iv-s      (-> crypto-iv crypt/base64-encode crypt/bytes->str)]
       {:username username :password crypted-password :crypto-iv crypto-iv-s})))
  ([{:keys [username password]}]
   (encode-credentials username password)))

(defn decode-password
  "Decode password that was originally generated (together with the init-vector) by
  `encode-credentials`. Arguments are base64 encoded. Returns nil if either `password` or
  `crypto-iv` is blank."
  ([password crypto-iv]
   (when (non-blanks? password crypto-iv)
     (crypt/decrypt-aes-string password
                               (env/value :backing-system :crypto-key)
                               crypto-iv)))
  ([{:keys [password crypto-iv]}]
   (decode-password password crypto-iv)))

(defn get-credentials
  "Returns `username`, decrypted `password` pair or nil (if blank arguments)."
  [{:keys [username password crypto-iv]}]
  (when (non-blanks? username crypto-iv password)
    [username (decode-password password crypto-iv)]))
