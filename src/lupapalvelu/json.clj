(ns lupapalvelu.json
  (:require [jsonista.core :as json]
            [sade.core :refer [def-]])
  (:import [com.fasterxml.jackson.core JsonGenerator]))

(defn- encode-byte-array [bytes ^JsonGenerator fmt]
  (.writeStartArray fmt)
  (dotimes [i (alength bytes)]
    (.writeNumber fmt (int (aget bytes i))))
  (.writeEndArray fmt))

(def- mapper
  (memoize
    (fn [decode-key-fn]
      ;; Onnistuu.fi integration claimed to need this byte array encoder:
      (json/object-mapper {:encoders      {(Class/forName "[B") encode-byte-array}
                           :decode-key-fn decode-key-fn}))))

(defn encode [v] (json/write-value-as-string v (mapper true)))

(defn encode-stream [v to]
  (json/write-value to v (mapper true))
  to)

(defn decode
  ([v] (decode v false))
  ([v decode-key-fn] (json/read-value v (mapper decode-key-fn))))

(def decode-stream decode)
