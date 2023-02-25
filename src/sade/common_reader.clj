(ns sade.common-reader
  (:require [lupapalvelu.logging :as logging]
            [sade.date :as date]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util :refer [postwalk-map convert-values]]
            [sade.xml :refer :all]
            [taoensso.timbre :refer [debugf error]]))

;;
;; Common
;;

(defn strip-key
  "removes namespacey part of a keyword key"
  [k] (if (keyword? k) (-> k name (ss/split #":") last keyword) k))

(defn strip-keys
  "removes recursively all namespacey parts from map keywords keys"
  [m] (postwalk-map (partial map (fn [[k v]] [(strip-key k) v])) m))

(defn to-boolean
  "converts 'true' and 'false' strings to booleans. returns others as-are."
  [v] (condp = v
        "true" true
        "false" false
        v))

(defn to-int
  "Converts strings looking like decimal numbers to ints"
  [v] (if (ss/numeric? v) (Integer/parseInt v 10) v))

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
  [m keys] (convert-values-of-keys m keys date/timestamp))

(defn strip-xml-namespaces
  "strips namespace-part of xml-element-keys"
  [xml] (postwalk-map (partial map (fn [[k v]] [k (if (= :tag k) (strip-key v) v)])) xml))

(def cleanup (comp util/strip-empty-maps util/strip-nils))

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
  "read one element from xml with enlive selector, converts it's vals to edn and strip namespaces.
   Be careful not to use for getting adjacent elements, as this function uses as-is and select1."
  [xml & selector] (-> xml (as-is (-> selector vector flatten)) vals first))

(defn map-index
  "transform a collection into keyword-indexed map (starting from 0)."
  [c] (into {} (map (fn [[k v]] [(keyword (str k)) v]) (map-indexed vector c))))

(defn index-maps
  "transform a form with replacing all sequential collections with keyword-indexed maps."
  [m] (postwalk-map (partial map (fn [[k v]] [k (if (sequential? v) (map-index v) v)])) m))

(defn- do-get-xml [http-fn url {debug-log? :debug-event :as opts} raw?]
  (let [options (merge {:socket-timeout 120000, :conn-timeout 30000, :throw-fail! (not raw?)} opts)
        {:keys [status body reason-phrase request-time] :as resp} (http-fn url options)
        stripped-url (first (ss/split url #"\?"))
        body-excerpt (ss/trim-newline (apply str (take 50 body)))]
    (when debug-log? (debugf "Received status %s %s in %.3f seconds from url %s" status reason-phrase (/ (double request-time) 1000) stripped-url))
    (if-not (ss/blank? body)
      (do
        (when debug-log?
          (debugf "Response body starts with: '%s'..." body-excerpt)
          (logging/log-event :debug {:ns    "sade.common-reader"
                                     :event "Received XML"
                                     :data  (assoc (select-keys resp [:status :reason-phrase :request-time])
                                              :url stripped-url
                                              :body-excerpt body-excerpt)}))
        (if raw? body (parse body)))
      (do
        (error "Received an empty XML response with GET from url: " url)
        nil))))

(defn get-xml
  ([url options]
    (get-xml url options nil false))
  ([url options credentials]
    (get-xml url options credentials false))
  ([url options credentials raw?]
    {:pre [(string? url)
           (or (nil? options) (map? options))
           (or (nil? credentials) (= (count credentials) 2))
           (or (nil? raw?) (boolean? raw?))]}
    (let [options (merge options (when credentials {:basic-auth credentials}))]
      (do-get-xml http/get url options raw?))))

(defn get-xml-with-post
  ([url options]
    (get-xml-with-post url options nil false))
  ([url options credentials raw?]
    {:pre [url
           (or (nil? options) (map? options))
           (or (nil? credentials) (= (count credentials) 2))
           (or (nil? raw?) (boolean? raw?))]}
    (let [options (if credentials (assoc options :basic-auth credentials) options)]
      (do-get-xml http/post url options raw?))))


(defn get-boolean [xml & selector] (to-boolean (apply get-text xml selector)))

(defn get-date [xml & selector] (date/timestamp (apply get-text xml selector)))

(defn convert-double-to-int
  "Converts given key value to integer in map. Returns unchanged map if cant be converted."
  [m k]
  (let [converted (int (util/->double (k m)))]
    (if (zero? converted) m (assoc m k (str converted)))))
