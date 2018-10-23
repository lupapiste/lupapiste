(ns lupapalvelu.json
  "JSON encoding and decoding with settings suitable for Lupapiste."
  (:require [jsonista.core :as json]
            [sade.core :refer [def-]])
  (:import [com.fasterxml.jackson.core JsonGenerator]))

(defn- encode-byte-array
  "Encode byte arrays as JSON arrays (instead of strings with the same byte content)."
  [bytes ^JsonGenerator fmt]
  (.writeStartArray fmt)
  (dotimes [i (alength bytes)]
    (.writeNumber fmt (int (aget bytes i))))
  (.writeEndArray fmt))

(def- mapper
  "Get an object mapper that uses `encode-byte-array` and decodes object keys with `decode-key-fn`."
  (memoize
    (fn [decode-key-fn]
      ;; Onnistuu.fi integration claimed to need this byte array encoder:
      (json/object-mapper {:encoders      {(Class/forName "[B") encode-byte-array}
                           :decode-key-fn decode-key-fn}))))

(defn encode
  "JSON-encode `v` as a string with Lupapiste encoder settings."
  [v]
  (json/write-value-as-string v (mapper true)))

(defn encode-stream
  "JSON-encode `v` into the `OutputStream` `to` with Lupapiste encoder settings."
  [v to]
  (json/write-value to v (mapper true))
  to)

(defn decode
  "JSON-decode the string `str`, applying `decode-key-fn` to the keys. `decode-key-fn` can also be `true` or `false`
  (~ `keyword` or ~ `identity` respectively, but with less indirection). The default `decode-key-fn` value is `false`."
  ([str] (decode str false))
  ([str decode-key-fn] (json/read-value str (mapper decode-key-fn))))

(def decode-stream
  "Like `decode`, but decodes from an `InputStream` instead of a string."
  decode)                                                   ; Due to `jsonista.core/ReadValue` these can be identical.
