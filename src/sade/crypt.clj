(ns sade.crypt
  (:require [crypto.random :as cr])
  (:import [org.bouncycastle.crypto BufferedBlockCipher]
           [org.bouncycastle.crypto.engines RijndaelEngine AESEngine]
           [org.bouncycastle.crypto.modes CBCBlockCipher]
           [org.bouncycastle.crypto.paddings PaddedBufferedBlockCipher ZeroBytePadding PKCS7Padding]
           [org.bouncycastle.crypto.params KeyParameter ParametersWithIV]
           [org.apache.commons.codec.binary Base64]))

(set! *warn-on-reflection* true)

(defn make-iv []
  (cr/bytes 32))

(def ^:private ciphers
  {:default (fn [encrypt? crypto-key crypto-iv]
              (doto (-> (RijndaelEngine. 256)
                        (CBCBlockCipher.)
                        (PaddedBufferedBlockCipher. (ZeroBytePadding.)))
                (.init encrypt? (ParametersWithIV. (KeyParameter. (into-array Byte/TYPE crypto-key))
                                                   (into-array Byte/TYPE crypto-iv)))))

   :aes     (fn [encrypt? crypto-key crypto-iv]
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
  ([crypto-key crypto-iv data]
   (encrypt crypto-key crypto-iv data :default))
  ([crypto-key crypto-iv data cipher]
   (crypt ((cipher ciphers) true crypto-key crypto-iv) data)))

(defn decrypt 
  ([crypto-key crypto-iv data]
   (decrypt crypto-key crypto-iv data :default))
  ([crypto-key crypto-iv data cipher]
   (crypt ((cipher ciphers) false crypto-key crypto-iv) data)))

(defn str->bytes ^bytes [^String s] (.getBytes s "UTF-8"))
(defn bytes->str ^String [^bytes b] (String. b "UTF-8"))

(defn base64-encode [^bytes data] (Base64/encodeBase64 data))
(defn base64-decode [^bytes data] (Base64/decodeBase64 data))

(defn url-encode [^String s] (java.net.URLEncoder/encode s "UTF-8"))
