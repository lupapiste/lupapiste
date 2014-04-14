(ns sade.common-reader
  (:require [clojure.string :as s]
            [clj-time.coerce :as coerce]
            [clj-time.format :as timeformat]
            [sade.http :as http]
            [sade.env :as env]
            [sade.util :refer [prewalk-map postwalk-map boolean? convert-values]]
            [sade.xml :refer :all]
            [sade.strings :as ss]))

;;
;; parsing time (TODO: might be copy-pasted from krysp)
;;

(defn parse-date
  "Parses date using given format. Defaults to the one used by CGI's KRYSP implementation"
  ([format ^String s]
    (assert (keyword? format))
    (timeformat/parse (timeformat/formatters format) s))
  ([^String s]
    (timeformat/parse (timeformat/formatter "YYYY-MM-dd'Z'") s)))

(defn parse-datetime [^String s]
  (timeformat/parse (timeformat/formatters :date-time-parser) s))

(defn unparse-datetime [format dt]
  (timeformat/unparse (timeformat/formatters format) dt))

;;
;; Common
;;

(defn strip-key
  "removes namespacey part of a keyword key"
  [k] (if (keyword? k) (-> k name (s/split #":") last keyword) k))

(defn strip-keys
  "removes recursively all namespacey parts from map keywords keys"
  [m] (postwalk-map (partial map (fn [[k v]] [(strip-key k) v])) m))

(defn strip-nils
  "removes recursively all keys from map which have value of nil"
  [m] (postwalk-map (partial filter (comp not nil? val)) m))

(defn strip-empty-maps
  "removes recursively all keys from map which have empty map as value"
  [m] (postwalk-map (partial filter (comp (partial not= {}) val)) m))

(defn to-boolean
  "converts 'true' and 'false' strings to booleans. returns others as-are."
  [v] (condp = v
        "true" true
        "false" false
        v))

(defn to-int
  "Converts strings looking like decimal numbers to ints"
  [v] (if (ss/numeric? v) (Integer/parseInt v 10) v))

(defn to-timestamp
  "Parses yyyy-mm-dd date and converts to timestamp"
  [v] (cond
        (nil? v) nil
        (re-matches #"^\d{4}-\d{2}-\d{2}Z$" v) (coerce/to-long (parse-date v))
        (re-matches #"^\d{4}-\d{2}-\d{2}$" v)  (coerce/to-long (parse-date :year-month-day v))
        (re-matches #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z?$" v) (coerce/to-long (parse-datetime v))
        :else v))

(defn convert-booleans
  "Changes recursively all stringy boolean values to booleans"
  [m] (convert-values m to-boolean))

(defn convert-values-of-keys [m keys converter]
  (convert-values m (fn [k _] ((set keys) k)) converter))

(defn convert-keys-to-ints
  "Changes recursively all decimal integer string values to ints"
  [m keys] (convert-values-of-keys m keys to-int))

(defn convert-keys-to-timestamps
  "Changes recursively all string values to timestamps (longs)"
  [m keys] (convert-values-of-keys m keys to-timestamp))

(defn ensure-sequental
  "Makes sure that the value of key k in map m is sequental"
  [m k] (let [v (k m)] (if (and v (not (sequential? v))) (assoc m k [v]) m)))

(defn strip-xml-namespaces
  "strips namespace-part of xml-element-keys"
  [xml] (postwalk-map (partial map (fn [[k v]] [k (if (= :tag k) (strip-key v) v)])) xml))

(defn translate
  "translates a value against the dictionary. return nil if cant be translated."
  [dictionary k & {:keys [nils] :or {nils false}}]
  (or (dictionary k) (and nils k) nil))

(defn translate-keys
  "translates all keys against the dictionary. loses all keys without translation."
  [dictionary m] (postwalk-map (partial map (fn [[k v]] (when-let [translation (translate dictionary k)] [translation v]))) m))

(defn as-is
  "read one element from xml with enlive selector, converts to edn and strip namespaces."
  [xml & selector] (-> (select1 xml (-> selector vector flatten)) xml->edn strip-keys))

(defn all-of
  "read one element from xml with enlive selector, converts it's val to edn and strip namespaces."
  [xml & selector] (-> xml (as-is (-> selector vector flatten)) vals first))

(defn map-index
  "transform a collection into keyord-indexed map (starting from 0)."
  [c] (into {} (map (fn [[k v]] [(keyword (str k)) v]) (map-indexed vector c))))

(defn index-maps
  "transform a form with replacing all sequential collections with keyword-indexed maps."
  [m] (postwalk-map (partial map (fn [[k v]] [k (if (sequential? v) (map-index v) v)])) m))

(defn get-xml
  ([url]
    (get-xml url nil false))
  ([url credentials raw?]
    {:pre [url
           (or (nil? credentials) (= (count credentials) 2))
           (or (nil? raw?) (boolean? raw?))]}
    (let [raw (:body (if credentials
                       (http/get url :basic-auth credentials)
                       (http/get url)))
          xml (parse raw)]
      (if raw? raw xml))))

(defn get-xml-with-post
  ([url options]
    (get-xml-with-post url options nil false))
  ([url options credentials raw?]
    {:pre [url
           (or (nil? options) (map? options))
           (or (nil? credentials) (= (count credentials) 2))
           (or (nil? raw?) (boolean? raw?))]}
    (let [raw (:body (if credentials
                       (http/post url (assoc options :basic-auth credentials))
                       (http/post url options)))
          xml (parse raw)]
      (if raw? raw xml))))


(defn get-boolean [xml & selector] (to-boolean (apply get-text xml selector)))

(defn get-date [xml & selector] (to-timestamp (apply get-text xml selector)))

