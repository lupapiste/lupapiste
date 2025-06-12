(ns sade.xml
  (:require [clojure.xml :as xml]
            [net.cgrand.enlive-html :as enlive]
            [sade.strings :as ss])
  (:import [java.io File InputStream Reader FilterReader StringReader InputStreamReader]
           [javax.xml XMLConstants]
           [javax.xml.parsers SAXParserFactory SAXParser]
           [org.apache.commons.io.input CharSequenceInputStream ReaderInputStream]
           [org.apache.commons.lang3 StringEscapeUtils]
           [org.xml.sax.helpers DefaultHandler]))

(set! *warn-on-reflection* true)

(defn escape-xml
  "Call org.apache.commons.lang3.StringEscapeUtils/escapeXML10"
  [^String s]
  (StringEscapeUtils/escapeXml10 s))

(defprotocol XMLSource
  (parse-with [self ^SAXParser parser ^DefaultHandler ch]))

(extend-protocol XMLSource
  File
  (parse-with [f, ^SAXParser parser, ^DefaultHandler ch] (.parse parser f ch))

  InputStream
  (parse-with [is, ^SAXParser parser, ^DefaultHandler ch] (.parse parser is ch))

  String
  (parse-with [s, ^SAXParser parser, ^DefaultHandler ch] (.parse parser s ch)))

(defn make-startparse
  "Make a `startparse` function for `clojure.xml/parse`."
  ([options] (make-startparse true options))
  ([validating? options] (let [parser-factory (SAXParserFactory/newInstance)
                               _ (.setValidating parser-factory validating?)
                               _ (doseq [[k v] options]
                                   (.setFeature parser-factory k v))]
                           (fn [s ch] (parse-with s (.newSAXParser parser-factory) ch)))))

(def startparse-sax-no-doctype
  "Safer version of clojure.xml/startparse-sax"
  ;; https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#jaxp-documentbuilderfactory-saxparserfactory-and-dom4j
  ;; clojure.xml now disables three specific flags, which are listed on the cheatsheet
  ;; for a case where you can't completely disable DTD, which is the disallow-doctype-decl feature here.
  ;; -> these options should be safer than the new clojure.xml options.
  ;; Cheatsheet also mentions setXIncludeAware false, but that options should
  ;; already default to false (per Java docs).
  (make-startparse {XMLConstants/FEATURE_SECURE_PROCESSING                 true
                    "http://apache.org/xml/features/disallow-doctype-decl" true}))

(defn parse-string
  "Parse the String `s` as XML."
  [^String s ^String encoding]
  (xml/parse (CharSequenceInputStream. s encoding) startparse-sax-no-doctype))

(defn- sanitized-reader
  "A `FilterReader` that wraps `reader` and skips characters 0xFEFF-0xFFFF."
  ^FilterReader [^Reader reader]
  (letfn [(filter-sub-charray! ^long [^chars arr, ^long offset, ^long length]
            ;; This is one of those cases where the equivalent Java code not only might be faster
            ;; but certainly would be more readable. NEVER SURRENDER:
            (let [end (+ offset length)]
              (loop [current-index offset, write-index offset]
                (if (< current-index end)
                  (let [c (aget arr current-index)]
                    (if (< (int c) 0xFEFF)
                      (do (when (< write-index current-index)
                            (aset arr write-index c))
                          (recur (inc current-index) (inc write-index)))
                      (recur (inc current-index) write-index)))
                  (- write-index offset)))))]
    (proxy [FilterReader] [reader]
      (read
        ;; Read characters until we get one that is < 0xFEFF:
        ([] (loop []
              (let [c (.read reader)]
                (if (< c 0xFEFF)
                  c
                  (recur)))))

        ;; Read characters into an array window, then compact the < 0xFEFF ones to the start of the window
        ;; and return the length of that compacted prefix:
        ([^chars cbuf, ^long offset, ^long length]
         (let [n-read (.read reader cbuf offset length)]
           (case n-read
             -1 n-read
             (filter-sub-charray! cbuf offset n-read))))))))

(defprotocol IntoReader
  (->reader [self ^String encoding]))

(extend-protocol IntoReader
  InputStream
  (->reader [self ^String encoding] (InputStreamReader. self encoding))

  String
  ;; Unlike `clojure.java.io/reader` this returns a reader for the string contents instead of URI resolution madness:
  (->reader [self _] (StringReader. self)))

(defn parse
  "Parse the IntoReader `cs` as XML, skipping Unicode code points FEFF-FFFF. Closes `cs` if applicable."
  [cs & {:keys [^String encoding] :or {encoding "UTF-8"}}]
  (with-open [cs (ReaderInputStream. (sanitized-reader (->reader cs encoding)) encoding)]
    (xml/parse cs startparse-sax-no-doctype)))

(defn attr [xml] (:attrs xml))
(defn text [xml] (-> xml :content first))

(def under enlive/has)
(defn has-text [text] (enlive/text-pred (partial = text)))

(defn select
  "Wrapper for `enlive/select` that flattens the `path`. While convenient, this makes some
  use cases impossible (e.g., pseudo classes in selectors)."
  [xml & path] (enlive/select xml (-> path vector flatten)))
(defn select1 [xml & path] (first (apply select xml path)))

(defn get-text [xml & selector] (-> xml (select1 (-> selector vector flatten)) text))

(defn extract [xml m]
  (persistent! (reduce-kv (fn [res k v] (assoc! res k (->> v butlast (apply select1 xml) ((last v)))))
                          (transient {}) m)))
(defn children [xml] (:content xml))
(defn convert [xml m] (map #(extract % m) (when (-> xml nil? not) (-> xml vector flatten))))
(defn fields-as-text [coll] (into {} (map (fn [v] [v [v text]])) coll))

;;
;; lossless XML to EDN simplification (from metosin with love)
;;

(def xml->edn
  (let [merge-to-vector (fn
                          ([m] m)
                          ([m1 m2] (merge-with #(flatten [%1 %2]) m1 m2)))
        childs? (fn [v] (map? (first v)))
        lift-text-nodes (fn [m] (if (= (keys m) [:##text]) (val (first m)) m))]
    (letfn [(parts [{:keys [content]}]
              (if (childs? content)
                (transduce (map xml->edn) merge-to-vector {} content)
                (hash-map :##text (first content))))
            (xml->edn [xml] (hash-map (:tag xml) (-> xml parts lift-text-nodes)))]
      xml->edn)))

;; Map to XML

(defrecord Element [tag attrs content])

(defn map->xml
  "Transforms map into XML structure."
  [m]
  (for [[k v] m]
    (->Element (keyword k)
               nil
               (cond
                 (map? v)        (map->xml v)
                 (sequential? v) (mapcat map->xml v)
                 :else           [(str v)]))))

;;
;; get attribute value
;;

(defn select1-attribute-value [xml selector attribute-name]
  (-> (first (enlive/select xml selector)) :attrs attribute-name))


;;
;; Emit
;;

(defn- append-attribute [^StringBuilder builder map-entry]
  (let [v (-> map-entry val (#(if (keyword? %) (name %) (str %))) escape-xml)]
    (-> builder
        (.append \space)
        (.append (name (key map-entry)))
        (.append "=\"")
        (.append v)
        (.append \"))))

(defn- append-element! [e, ^StringBuilder b]
  (cond
    (string? e)     (.append b (escape-xml e))
    (map? e)        (do (-> b (.append \<) (.append (name (:tag e))))
                        (doseq [attr (:attrs e)] (append-attribute b attr))
                        (if-not (:content e)
                          (.append b "/>")
                          (do
                            (.append b ">")
                            (append-element! (:content e) b)
                            (-> b (.append "</") (.append (name (:tag e))) (.append ">")))))
    (sequential? e) (doseq [c e] (append-element! c b))
    (some? e)       (.append b e)))

(defn element-to-string [e]
  (let [^StringBuilder builder (StringBuilder.)]
    (append-element! e builder)
    (.toString builder)))

(def ^:private bad-xml-whitespace
  "Ranges of invalid whitespace characters.
   [\u0000-\u0008]: Control characters from `U+0000` to `U+0008`.
   [\u000B\u000C]: Vertical Tab (`U+000B`) and Form Feed (`U+000C`).
   [\u000E-\u001F]: Control characters from `U+000E` to `U+001F`.
   [\u007F]: Delete character (`U+007F`).
   [\u0085]: Next Line (`U+0085`).
   [\u00A0]: Non-breaking space (`U+00A0`).
   [\u1680]: Ogham space mark (`U+1680`).
   [\u180E]: Mongolian vowel separator (`U+180E`).
   [\u2000-\u200F]: Various spaces and formatting characters in the `U+2000` block.
   [\u2028\u2029]: Line separator (`U+2028`) and paragraph separator (`U+2029`).
   [\u202F]: Narrow no-break space (`U+202F`).
   [\u205F]: Medium mathematical space (`U+205F`).
   [\u3000]: Ideographic space (`U+3000`)."
  #"[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F\u0085\u00A0\u1680\u180E\u2000-\u200F\u2028\u2029\u202F\u205F\u3000]+")

(defn purge-xml-string
  "Replaces every invalid XML whitespace sequence in `s` with space. Nil-safe and returns
  nil on blanks."
  [s]
  (some-> s (ss/replace bad-xml-whitespace " ") ss/blank-as-nil))
