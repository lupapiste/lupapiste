(ns sade.strings
  (:require [clojure.string :as s]
            [clojure.walk :as walk]
            [sade.shared-strings :as shared])
  (:import [java.text Normalizer Normalizer$Form]
           [java.nio.charset Charset]
           [java.util Base64]
           [org.apache.commons.lang3 StringUtils]
           [clojure.lang Keyword]
           (java.io ByteArrayInputStream))
  (:refer-clojure :exclude [replace contains? empty?]))

(defmacro defalias [alias from]
  `(do (def ~alias ~from)
       (alter-meta! #'~alias merge (select-keys (meta #'~from) [:arglists]))
       ~alias))

(def ^Charset utf8 (Charset/forName "UTF-8"))

(defn utf8-bytes [^String s] (when s (.getBytes s utf8)))

(defn ^String utf8-str [^bytes b] (when b (String. b "UTF-8")))

(defn last-n [n ^String s]
  (when s
    (apply str (take-last n s))))

(defn limit
  ([^String s max-length]
    (limit s max-length nil))
  ([^String s max-length truncated-info]
    (when (and s max-length)
      (if (> (.length s) max-length)
        (let [truncated (.substring s 0 max-length)]
          (if truncated-info
            (str truncated truncated-info)
            truncated))
        s))))

(defn contains? [^String s ^CharSequence needle]
  (when (and s needle)
    (.contains s needle)))

(defn empty? [^String s]
  (if s
    (.isEmpty s)
    true))

(defn optional-string? [x]
  (or (nil? x) (string? x)))

(def other-than-string? (complement optional-string?))

(defalias in-lower-case? shared/in-lower-case?)

(defn suffix
  "Returns a substring from the end of last occurance of separator till the end of s"
  [^String s ^String separator]
  (when s
    (if (and separator (not (empty? separator)) (contains? s separator))
      (.substring s (+ (.lastIndexOf s separator) (.length separator)))
      s)))

(defn de-accent
  "Replaces accent characters with base letters"
  [^String s]
  (when s (let [normalized (Normalizer/normalize s Normalizer$Form/NFD)]
    (clojure.string/replace normalized #"\p{InCombiningDiacriticalMarks}+" ""))))

(defn normalize
  "The NFC normalization is prudent when `s` may contain combining umlauts (e.g., a + (char 776) -> ä)."
  [^String s]
  (some-> s (Normalizer/normalize Normalizer$Form/NFC)))

(def ascii-pattern #"^[\p{ASCII}]+$")

(def non-printables #"[\p{Cntrl}]")

(defn strip-non-printables [^String s] (when s (s/replace s non-printables "")))

(defn remove-leading-zeros [^String s] (when s (.replaceFirst s "^0+(?!$)", "")))

(defn ^Boolean starts-with [^String s ^String prefix]
  (when (and s prefix)
    (.startsWith s prefix)))

(defn ^Boolean ends-with [^String s ^String postfix]
  (when (and s postfix)
    (.endsWith s postfix)))

(defn ^Boolean starts-with-i [^String s ^String prefix]
  (when (and s prefix)
    (.startsWith (.toLowerCase s) (.toLowerCase prefix))))

(defn ^Boolean ends-with-i [^String s ^String postfix]
  (when (and s postfix)
    (.endsWith (.toLowerCase s) (.toLowerCase postfix))))

(defn unescape-html
  "Change HTML character entities into special characters. Like hiccup.util/escape-html but backwards."
  [^String s]
  (.. s
    (replace "&amp;"  "&")
    (replace "&lt;"   "<")
    (replace "&gt;"   ">")
    (replace "&quot;" "\"")))

(defn unescape-html-scandinavian-characters
  "Change HTML character entities into Scandinavian characters."
  [^String s]
  (.. s
    (replace "&auml;"  "\u00e4")
    (replace "&Auml;"  "\u00c4")
    (replace "&ouml;"  "\u00f6")
    (replace "&Ouml;"  "\u00d6")
    (replace "&aring;" "\u00e5")
    (replace "&Aring;" "\u00c5")))

(def ^:private base64-decoder (Base64/getMimeDecoder))

(defn base64-decode
  "Decode a base64 encoded string using UTF-8."
  ^String [^String s]
  (when s
    (utf8-str (.decode base64-decoder ^bytes (utf8-bytes s)))))

;; Commons-lang3 wrappers
(defn numeric?
  "http://commons.apache.org/proper/commons-lang/javadocs/api-release/org/apache/commons/lang3/StringUtils.html#isNumeric(java.lang.CharSequence)"
  [s] (and (string? s) (StringUtils/isNumeric s)))

(defn ascii? [s]
  (not (nil? (re-matches ascii-pattern s))))

(defn scandics->ascii [s]
  (when s
    (s/escape s {(char 228) "a", (char 229) "a", (char 246) "o"
                 (char 196) "A", (char 197) "A", (char 214) "O"})))

(defn substring [^String s  ^Integer start ^Integer end]
  (StringUtils/substring s start end))

(defn decimal-number? [s]
  (or (numeric? s) (if (and (string? s) (re-matches #"^\d+\.\d+$" s)) true false)))

(defn zero-pad
  "Pad 's' with zeros so that its at least 'c' characters long"
  [^Integer c ^String s]
  (StringUtils/leftPad s  c \0))

;; Nil-safe wrappers to clojure.string

(defalias lower-case      shared/lower-case)
(defalias upper-case      shared/upper-case)
(defalias capitalize      shared/capitalize)
(defalias trim            shared/trim)
(defalias trim-newline    shared/trim-newline)
(defalias split           shared/split)
(defalias split-lines     shared/split-lines)
(defalias replace         shared/replace)
(def blank?               shared/blank?)
(defalias not-blank?      shared/not-blank?)
(defalias blank-as-nil    shared/blank-as-nil)
(def join                 shared/join)
(defalias join-non-blanks shared/join-non-blanks)
(defalias trimwalk        shared/trimwalk)
;; File name handling

(def windows-filename-max-length 255)

(defn ^String encode-filename
  "Replaces all non-ascii chars and other that the allowed punctuation with dash.
   UTF-8 support would have to be browser specific, see http://greenbytes.de/tech/tc2231/"
  [unencoded-filename]
  (when-let [de-accented (de-accent unencoded-filename)]
    (s/replace
      (last-n windows-filename-max-length de-accented)
      #"[^a-zA-Z0-9\.\-_ ]" "-")))

(defn escaped-re-pattern
  [string]
  (re-pattern (str "\\Q" string "\\E")))

(defn to-camel-case
  [string]
  (s/replace string #"-(\w)" #(upper-case (second %1))))

(defn =trim-i
  "Compares trimmed lower-cased versions of strings."
  [& xs]
  (apply = (map (comp trim lower-case) xs)))

(defn strip-non-letters [string]
  (apply str (filter #(Character/isLetter ^char %) string)))

(defn =alpha-i
  "Compares strings after removing non-letters and lower-casing"
  [& xs]
  (apply = (map (comp lower-case strip-non-letters) xs)))

(def whitespace-pattern #"[\s\p{Punct}]+")

(defn fuzzy-re
  "Takes search term and turns it into 'fuzzy' regular expression
  string (not pattern!) that matches any string that contains the
  substrings in the correct order. The search term is split for
  regular whitespace, Unicode no-break space and punctuation. The
  original string parts are escaped for (inadvertent) regex syntax.
  Sample matching: 'ear onk' will match 'year of the monkey' after
  fuzzying.

  Note: Since the term is typically used in mongo queries, we must
  keep in mind that regex syntax differs from Clojure (Java) to Mongo"
  [term]
  (when (not-blank? term)
    (let [fuzzy (->> (split term whitespace-pattern)
                     (map #(java.util.regex.Pattern/quote %))
                     (join ".+"))]
      (str "^.*" fuzzy ".*$"))))

(defprotocol ToPlainString
  (->plain-string [value]))

(extend-protocol ToPlainString
  Keyword
  (->plain-string [value] (name value))

  String
  (->plain-string [value] value)

  Object
  (->plain-string [value] (.toString value))

  nil
  (->plain-string [_] ""))

(def canonize-email (comp lower-case trim))

(defn strip-trailing-slashes [string]
  (replace string #"/+$" ""))

(defn serialize
  "Serialze with `pr-str` but enforce evaluation first. The resulting
  string can be parsed with `clojure.edn/read-string`."
  [arg]
  (pr-str (walk/postwalk identity arg)))

(defn trim-to [len text]
  (when text
    (let [max (min len (count text))]
      (subs text 0 max))))

(defn join-file-path
  "Joins `parts` with slash character. `parts` can be strings or string lists. Blank parts
  are ignored. Result is trimmed but the middle parts are not. Returns nil for blank paths."
  [& parts]
  (as-> (flatten parts) $
        (join-non-blanks "/" $)
        (replace $ #"//+" "/")
        (trim $)
        (blank-as-nil $)))

(defn ->inputstream
  "Avoids the deprecated `java.io.StringBufferInputStream` that outputs in ANSI format"
  [string]
  (some-> string (.getBytes "UTF-8") ByteArrayInputStream.))
