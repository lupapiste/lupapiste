(ns lupapalvelu.crypto
  (:import [java.security SecureRandom MessageDigest]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]
           [java.util Base64 Base64$Decoder Base64$Encoder]
           [org.bouncycastle.crypto BufferedBlockCipher]
           [org.bouncycastle.crypto.engines AESEngine RijndaelEngine]
           [org.bouncycastle.crypto.modes CBCBlockCipher]
           [org.bouncycastle.crypto.paddings PKCS7Padding PaddedBufferedBlockCipher ZeroBytePadding]
           [org.bouncycastle.crypto.params KeyParameter ParametersWithIV]))

(set! *warn-on-reflection* true)

(defn make-iv
  "Random bytes for initialization vector. Default is 32"
  ([] (make-iv 32))
  ([num-bytes]
   (let [result (byte-array num-bytes)]
     (.nextBytes (SecureRandom.) result)
     result)))

(defn make-iv-128 []
  (make-iv 16))

(def ^:private ciphers
  {:rijndael (fn [encrypt? crypto-key crypto-iv]
               (doto (-> (RijndaelEngine. 256)
                         (CBCBlockCipher.)
                         (PaddedBufferedBlockCipher. (ZeroBytePadding.)))
                 (.init encrypt? (ParametersWithIV. (KeyParameter. (into-array Byte/TYPE crypto-key))
                                                    (into-array Byte/TYPE crypto-iv)))))

   :aes      (fn [encrypt? crypto-key crypto-iv]
               (doto (-> (AESEngine.)
                         (CBCBlockCipher.)
                         (PaddedBufferedBlockCipher. (PKCS7Padding.)))
                 (.init encrypt? (ParametersWithIV. (KeyParameter. (into-array Byte/TYPE crypto-key))
                                                    (into-array Byte/TYPE crypto-iv)))))})

(defn- crypt ^bytes [^BufferedBlockCipher cipher ^bytes data]
  (let [in-size  (alength data)
        out-size (.getOutputSize cipher in-size)
        out      (byte-array out-size)
        out-len  (.processBytes cipher data 0 in-size out 0)
        out-len  (+ out-len (.doFinal cipher out out-len))]
    (if (< out-len out-size)
      (byte-array out-len out)
      out)))

(defn encrypt
  [crypto-key crypto-iv cipher data]
  {:pre [(contains? ciphers cipher)]}
  (crypt ((cipher ciphers) true crypto-key crypto-iv) data))

(defn decrypt
  [crypto-key crypto-iv cipher data]
  {:pre [(contains? ciphers cipher)]}
  (crypt ((cipher ciphers) false crypto-key crypto-iv) data))

(defn str->bytes ^bytes [^String s] (.getBytes s "UTF-8"))
(defn bytes->str ^String [^bytes b] (String. b "UTF-8"))

(def ^:private ^Base64$Encoder b64-encoder (Base64/getEncoder))
(def ^:private ^Base64$Decoder b64-decoder (Base64/getDecoder))

(defn base64-encode ^bytes [^bytes data] (.encode b64-encoder data))
(defn base64-decode ^bytes [^bytes data] (.decode b64-decoder data))

(defn base64-random-bytes
  "Generates a random buffer of `num-bytes` bytes and returns it as a Base64 encoded string"
  [num-bytes]
  (-> num-bytes make-iv base64-encode bytes->str))

(defn url-encode [^String s] (java.net.URLEncoder/encode s "UTF-8"))

(defn encrypt-aes-string
  "Encrypt string with AES using given base64 passowrd string and IV byte array"
  [s crypto-key-s crypto-iv]
  (let [crypto-key (-> crypto-key-s str->bytes base64-decode)]
    (->> s
         str->bytes
         (encrypt crypto-key crypto-iv :aes)
         base64-encode
         bytes->str)))

(defn decrypt-aes-string
  "Arguments are expected to be base64 encoded"
  [s crypto-key-s crypto-iv-s]
  (let [crypto-key (-> crypto-key-s str->bytes base64-decode)
        crypto-iv  (-> crypto-iv-s str->bytes base64-decode)]
    (->> s
         str->bytes
         base64-decode
         (decrypt crypto-key crypto-iv :aes)
         bytes->str)))


(defn calculate-md5-base64 [^bytes data]
  (let [md5 (MessageDigest/getInstance "MD5")]
    (.encodeToString (Base64/getEncoder) (.digest md5 data))))

(defn hmac-sha512 [^String data ^String secret]
  (let [decoder (Base64/getDecoder)
        secret-bytes (.decode decoder secret)
        secret-key (SecretKeySpec. secret-bytes "HmacSHA512")
        mac (doto (Mac/getInstance "HmacSHA512")
              (.init secret-key))]
    (.encodeToString (Base64/getEncoder)
                     (.doFinal mac (.getBytes data "UTF-8")))))

