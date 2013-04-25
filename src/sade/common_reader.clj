(ns sade.common-reader
(:use sade.xml)
(:require [clojure.string :as s]
          [clojure.walk :refer [postwalk prewalk]]
          [clj-time.format :as timeformat]
          [clj-http.client :as http]))

;;
;; parsing time (TODO: might be copy-pasted from krysp)
;;

(defn parse-datetime [s]
  (timeformat/parse (timeformat/formatter "YYYY-MM-dd'T'HH:mm:ss'Z'") s))

(defn unparse-datetime [format dt]
  (timeformat/unparse (timeformat/formatters format) dt))

;;
;; Common
;;

(defn postwalk-map
  "traverses m and applies f to all maps within"
  [f m] (postwalk (fn [x] (if (map? x) (into {} (f x)) x)) m))

(defn prewalk-map
  "traverses m and applies f to all maps within"
  [f m] (prewalk (fn [x] (if (map? x) (into {} (f x)) x)) m))

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

(defn convert-booleans
  "changes recursively all stringy boolean values to booleans"
  [m] (postwalk-map (partial map (fn [[k v]] [k (to-boolean v)])) m))

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
    (get-xml url nil))
  ([url credentials]
    (let [raw (:body (if credentials (http/get url {:basic-auth credentials}) (http/get url)))
          xml (parse raw)]
      xml)))


