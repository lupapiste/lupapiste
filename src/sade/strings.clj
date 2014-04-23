(ns sade.strings
  (:require [clojure.string :as s])
  (:import [java.text Normalizer Normalizer$Form]
           [org.apache.commons.lang3 StringUtils]))

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

(defn contains [^String s ^CharSequence needle]
  (when (and s needle)
    (.contains s needle)))

(defn suffix
  "Returns a substring from the end of last occurance of separator till the end of s"
  [^String s ^String separator]
  (when s
    (if (and separator (not (.isEmpty separator)) (contains s separator))
      (.substring s (+ (.lastIndexOf s separator) (.length separator)))
      s)))

(defn de-accent
  "Replaces accent characters with base letters"
  [^String s]
  (when s (let [normalized (Normalizer/normalize s Normalizer$Form/NFD)]
    (clojure.string/replace normalized #"\p{InCombiningDiacriticalMarks}+" ""))))

(defn remove-leading-zeros [^String s] (when s (.replaceFirst s "^0+(?!$)", "")))

(defn starts-with [^String s ^String prefix]
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

;; Commons-lang3 wrappers
(defn numeric?
  "http://commons.apache.org/proper/commons-lang/javadocs/api-release/org/apache/commons/lang3/StringUtils.html#isNumeric(java.lang.CharSequence)"
  [s] (and (string? s) (StringUtils/isNumeric s)))

(defn substring [^String s  ^Integer start ^Integer end]
  (StringUtils/substring s start end))

(defn decimal-number? [s]
  (or (numeric? s) (if (and (string? s) (re-matches #"^\d+\.\d+$" s)) true false)))

;; Nil-safe wrappers to clojure.string

(defn lower-case ^String [^CharSequence x] (when x (s/lower-case x)))

(defn upper-case ^String [^CharSequence x] (when x (s/upper-case x)))

(defn trim ^String [^CharSequence x] (when x (s/trim x)))

(defn split ^String [^CharSequence s ^java.util.regex.Pattern re] (when s (s/split s re)))

; alias common clojure.string stuff, so that you dont need to require both namespaces:

(def blank? s/blank?)

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
