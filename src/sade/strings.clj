(ns sade.strings
  (:require [clojure.string :as s])
  (:import [java.text Normalizer Normalizer$Form]
           [org.apache.commons.lang3 StringUtils]
           [org.apache.commons.codec.binary Base64])
  (:refer-clojure :exclude [replace contains? empty?]))

(def utf8 (java.nio.charset.Charset/forName "UTF-8"))

(defn utf8-bytes ^bytes  [^String s] (when s (.getBytes s utf8)))

(defn utf8-str ^String [^bytes b] (when b (String. b "UTF-8")))

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

(defn empty? [^CharSequence s]
  (if s
    (.isEmpty s)
    true))

(defn optional-string? [x]
  (or (nil? x) (string? x)))

(def other-than-string? (complement optional-string?))

(defn in-lower-case? [^String s]
  (if s
    (= s (.toLowerCase s))
    false))

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

(def ascii-pattern #"^[\p{ASCII}]+$")

(def non-printables #"[\p{Cntrl}]")

(defn strip-non-printables [^String s] (when s (s/replace s non-printables "")))

(defn remove-leading-zeros [^String s] (when s (.replaceFirst s "^0+(?!$)", "")))

(defn starts-with ^Boolean [^String s ^String prefix]
  (when (and s prefix)
    (.startsWith s prefix)))

(defn ends-with [^String s ^String postfix]
  (when (and s postfix)
    (.endsWith s postfix)))

(defn starts-with-i [^String s ^String prefix]
  (when (and s prefix)
    (.startsWith (.toLowerCase s) (.toLowerCase prefix))))

(defn ends-with-i [^String s ^String postfix]
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

(defn base64-decode
  "Decode a base64 encoded string using UTF-8."
  ^String [^String s]
  (utf8-str (Base64/decodeBase64 (utf8-bytes s))))

;; Commons-lang3 wrappers
(defn numeric?
  "http://commons.apache.org/proper/commons-lang/javadocs/api-release/org/apache/commons/lang3/StringUtils.html#isNumeric(java.lang.CharSequence)"
  [s] (and (string? s) (StringUtils/isNumeric s)))

(defn ascii? [s]
  (not (nil? (re-matches ascii-pattern s))))

(defn substring [^String s  ^Integer start ^Integer end]
  (StringUtils/substring s start end))

(defn decimal-number? [s]
  (or (numeric? s) (if (and (string? s) (re-matches #"^\d+\.\d+$" s)) true false)))

(defn zero-pad
  "Pad 's' with zeros so that its at least 'c' characters long"
  [^Integer c ^String s]
  (StringUtils/leftPad s  c \0))

;; Nil-safe wrappers to clojure.string

(defn lower-case ^String [^CharSequence x] (when x (s/lower-case x)))

(defn upper-case ^String [^CharSequence x] (when x (s/upper-case x)))

(defn capitalize ^String [^CharSequence x] (when x (s/capitalize x)))

(defn trim ^String [^CharSequence x] (when x (s/trim x)))

(defn split
  ([^CharSequence s ^java.util.regex.Pattern re] (when s (s/split s re)))
  ([^CharSequence s ^java.util.regex.Pattern re ^Integer limit] (when s (s/split s re limit))))

(defn replace ^String [^CharSequence s match replacement] (when s (s/replace s match replacement)))

; alias common clojure.string stuff, so that you dont need to require both namespaces:

(def ^{:doc "Alias to clojure.string/blank?"} blank? s/blank?)

(defn not-blank? [s] (not (blank? s)))

(def ^{:doc "Arguments: [coll] or [separator coll]. Alias to clojure.string/join"} join s/join)

;; File name handling

(def windows-filename-max-length 255)

(defn encode-filename
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

(defn fuzzy-re
  "Takes search term and turns it into 'fuzzy' regular expression
  string (not pattern!) that matches any string that contains the
  substrings in the correct order. The search term is split both for
  regular whitespace and Unicode no-break space. The original string
  parts are escaped for (inadvertent) regex syntax.
  Sample matching: 'ear onk' will match 'year of the monkey' after fuzzying"
  [term]
  (let [whitespace "[\\s\u00a0]+"
        fuzzy      (->> (split term (re-pattern whitespace))
                        (map #(java.util.regex.Pattern/quote %))
                        (join (str ".*" whitespace ".*")))]
    (str "^.*" fuzzy ".*$")))
