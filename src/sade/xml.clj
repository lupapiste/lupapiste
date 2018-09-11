(ns sade.xml
  (:require [clojure.xml :as xml]
            [clojure.string :as s]
            [net.cgrand.enlive-html :as enlive])
  (:import  [org.apache.commons.lang3 StringEscapeUtils]))


(defn escape-xml
  "http://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringEscapeUtils.html#escapeXml10%28java.lang.String%29"
  [^String s] (StringEscapeUtils/escapeXml10 s))

;; Safer version of clojure.xml/startparse-sax
(defn startparse-sax-no-doctype [s ch]
  (..
    (doto (javax.xml.parsers.SAXParserFactory/newInstance)
      (.setFeature javax.xml.XMLConstants/FEATURE_SECURE_PROCESSING true)
      (.setFeature "http://apache.org/xml/features/disallow-doctype-decl" true))
    (newSAXParser)
    (parse s ch)))

(defn parse-string [^String s encoding]
  {:pre [(not (s/blank? s))]}
  (xml/parse (java.io.ByteArrayInputStream. (.getBytes s encoding)) startparse-sax-no-doctype))

(defn parse [^String s & {:keys [encoding] :or {encoding "UTF-8"}}]
  {:pre [(not (s/blank? s))]}
  (let [xml (-> s s/trim (s/replace #"[\uFEFF-\uFFFF]", ""))]
    (if (.startsWith xml "<")
      (parse-string xml encoding)
      (xml/parse xml startparse-sax-no-doctype))))

(defn attr [xml] (:attrs xml))
(defn text [xml] (-> xml :content first))

(def under enlive/has)
(defn has-text [text] (enlive/text-pred (partial = text)))

(defn select [xml & path] (enlive/select xml (-> path vector flatten)))
(defn select1 [xml & path] (first (apply select xml path)))

(defn get-text [xml & selector] (-> xml (select1 (-> selector vector flatten)) text))

(defn extract [xml m] (into {} (for [[k v] m] [k (->> v butlast (apply select1 xml) ((last v)))])))
(defn children [xml] (:content xml))
(defn convert [xml m] (map #(extract % m) (when (-> xml nil? not) (-> xml vector flatten))))
(defn fields-as-text [coll] (into {} (for [v coll] [v [v text]])))

;;
;; lossless XML to EDN simplification (from metosin with love)
;;

(declare xml->edn)
(defn- attr-name [k] (keyword (str "#" (name k))))
(defn- decorate-attrs [m] (zipmap (map attr-name (keys m)) (vals m)))
(defn- merge-to-vector [m1 m2] (merge-with #(flatten [%1 %2]) m1 m2))
(defn- childs? [v] (map? (first v)))
(defn- lift-text-nodes [m] (if (= (keys m) [:##text]) (val (first m)) m))
(defn- parts [{:keys [content]}]
  (merge {}  #_(decorate-attrs attrs)
         (if (childs? content)
           (reduce merge-to-vector (map xml->edn content))
           (hash-map :##text (first content)))))

(defn xml->edn [xml] (hash-map (:tag xml) (-> xml parts lift-text-nodes)))

;;
;; get attribute value
;;

(defn select1-attribute-value [xml selector attribute-name]
  (-> (first (enlive/select xml selector)) :attrs attribute-name))


;;
;; Emit
;;

(defn- append-attribute [builder map-entry]
  (let [v (-> map-entry val (#(if (keyword? %) (name %) (str %))) escape-xml)]
    (-> builder
     (.append \space)
     (.append (name (key map-entry)))
     (.append "=\"")
     (.append v)
     (.append \"))))

(defn- append-element [e, ^java.lang.StringBuilder b]
  (cond
    (string? e)     (.append b (escape-xml e))
    (map? e)        (do
                      (-> b (.append  \<) (.append (name (:tag e))))
                      (doseq [attr (:attrs e)] (append-attribute b attr) )
                      (if-not (:content e)
                        (.append b "/>")
                        (do
                          (.append b ">")
                          (doseq [c (:content e)] (append-element c b))
                          (-> b (.append "</") (.append (name (:tag e))) (.append ">")))))
    (sequential? e) (doseq [c e] (append-element c b))
    (not (nil? e))  (.append b e)))

(defn element-to-string [e]
  (let [^java.lang.StringBuilder builder (java.lang.StringBuilder.)]
    (append-element e builder)
    (.toString builder)))
