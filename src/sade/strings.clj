(ns sade.strings
  (:import [java.text Normalizer Normalizer$Form]
           [org.apache.commons.lang3 StringUtils]))

(defn last-n [n s]
  (when s
    (apply str (take-last n s))))

(defn suffix
  "Returns a substring from the end of last occurance of separator till the end of s"
  [s separator]
  (when s
    (if (and separator (not (.isEmpty separator)) (.contains s separator))
      (.substring s (+ (.lastIndexOf s separator) (.length separator)))
      s)))

(defn de-accent
  "Replaces accent characters with base letters"
  [s]
  (when s (let [normalized (Normalizer/normalize s Normalizer$Form/NFD)]
    (clojure.string/replace normalized #"\p{InCombiningDiacriticalMarks}+" ""))))

(defn remove-leading-zeros [s] (when s (.replaceFirst s "^0+(?!$)", "")))

(defn starts-with [^String s ^String prefix]
  (when (clojure.core/and s prefix)
    (.startsWith s prefix)))

(defn starts-with-i [^String s ^String prefix]
  (when (clojure.core/and s prefix)
    (.startsWith (.toLowerCase s) (.toLowerCase prefix))))

;; Commons-lang3 wrapper
(defn numeric?
  "http://commons.apache.org/proper/commons-lang/javadocs/api-3.1/org/apache/commons/lang3/StringUtils.html#isNumeric(java.lang.CharSequence)"
  [s] (and (string? s) (StringUtils/isNumeric s)))

(defn decimal-number? [s]
  (or (numeric? s) (if (and (string? s) (re-matches #"^\d+\.\d+$" s)) true false)))


