(ns lupapalvelu.json
  "JSON encoding and decoding with settings suitable for Lupapiste."
  (:require [jsonista.core :as json]
            [sade.core :refer [def-]]
            [sade.strings :as ss])
  (:import [com.fasterxml.jackson.core JsonGenerator JsonFactory JsonToken JsonParser]
           [com.fasterxml.jackson.databind ObjectMapper]
           [java.io BufferedReader Reader Writer]))

(set! *warn-on-reflection* true)

(defn- encode-byte-array
  "Encode byte arrays as JSON arrays (instead of strings with the same byte content)."
  [^bytes bytes ^JsonGenerator fmt]
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
  ([str decode-key-fn]
   (when-not (ss/blank? str)
     (json/read-value str (mapper decode-key-fn)))))

(defn- reader-trim-left
  "Throw away whitespace from start of `rdr`, return `rdr`."
  ^BufferedReader [^BufferedReader rdr]
  (loop []
    (.mark rdr 1)
    (if (Character/isWhitespace (.read rdr))
      (recur)
      (do (.reset rdr)
          rdr))))

(defn decode-stream
  "Like `decode`, but decodes from a `BufferedReader` instead of a string."
  ([is] (decode-stream is false))
  ([^BufferedReader rdr decode-key-fn]
   (let [rdr (reader-trim-left rdr)]
     (.mark rdr 1)
     (case (.read rdr)
       -1 nil
       (do (.reset rdr)
           (json/read-value rdr (mapper decode-key-fn)))))))

(extend-protocol json/ReadValue
  JsonParser
  (-read-value [this ^ObjectMapper mapper]
    (.readValue mapper this ^Class Object)))

(extend-protocol json/WriteValue
  JsonGenerator
  (-write-value [this value ^ObjectMapper mapper]
    (.writeValue mapper this value)))

(defn make-json-array-reader
  "Returns a function that for each call returns either JSON array item or `::end`."
  ([^Reader reader ^ObjectMapper mapper]
   (let [^JsonFactory factory     (.getFactory mapper)
         ^JsonParser parser       (.createParser factory reader)
         ^JsonToken next-token    #(.nextToken parser)]
     (assert (= (next-token) JsonToken/START_ARRAY)
             "Parsing error, no array.")
     (fn []
       (if (= (next-token) JsonToken/END_ARRAY)
         ::end
         (json/read-value parser mapper)))))
  ([^Reader reader]
   (make-json-array-reader reader
                           (json/object-mapper {:decode-key-fn true}))))

(defn process-json-array
  "Reads a JSON array from `reader`, processes each item `process-fn` (function that takes
  and returns an item) and writes the results as an array to `writer`. Both `reader` and
  `writer` should be called within `with-open`. The default object mapper can be
  overridden with `mapper`. Throws if the `reader` does not provide an array."
  ([^Reader reader ^Writer writer process-fn ^ObjectMapper mapper]
   (let [^JsonFactory factory     (.getFactory mapper)
         ^JsonParser parser       (.createParser factory reader)
         ^JsonToken next-token    #(.nextToken parser)
         ^JsonGenerator generator (.createGenerator factory writer)]
     (assert (= (next-token) JsonToken/START_ARRAY)
             "Parsing error, no array.")
     (.writeStartArray generator)
     (while (not= (next-token) JsonToken/END_ARRAY)
       (json/write-value generator
                         (process-fn (json/read-value parser mapper))
                         mapper))
     (.writeEndArray generator)
     (.flush generator)))
  ([^Reader reader ^Writer writer process-fn]
   (process-json-array reader
                       writer
                       process-fn
                       (json/object-mapper {:pretty        true
                                            :strip-nils    true
                                            :decode-key-fn true}))))
