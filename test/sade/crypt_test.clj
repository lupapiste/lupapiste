(ns sade.crypt-test
  (:require [sade.crypt :as c]
            [midje.sweet :refer :all]))

(fact "plain -> encrypt -> decrypt -> plain"
  (let [plain       "Hello, world!"
        crypto-key  (range 32)
        crypto-iv   (range 32)
        encrypted   (c/encrypt crypto-key crypto-iv (.getBytes plain "UTF-8"))
        decrypted   (c/decrypt crypto-key crypto-iv encrypted)
        result      (String. decrypted "UTF-8")]
    plain => result))

(fact "plain -> encrypt-aes -> decrypt-aes -> plain"
  (let [plain       "Hello, world!"
        crypto-key  (range 32)
        crypto-iv   (range 16)
        encrypted   (c/encrypt crypto-key crypto-iv :aes (.getBytes plain "UTF-8"))
        decrypted   (c/decrypt crypto-key crypto-iv :aes encrypted)
        result      (String. decrypted "UTF-8")]
    plain => result))

(fact "text -> base 64 encode -> base 64 decode -> text"
  (-> "Hello, world!"
      (.getBytes "UTF-8")
      (c/base64-encode)
      (c/base64-decode)
      (String. "UTF-8"))
    => "Hello, world!")
