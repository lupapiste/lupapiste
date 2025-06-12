(ns sade.strings
  "Nil safe wrappers for some `clojure.string` functions and other utilities."
  #_{:clj-kondo/ignore [:discouraged-namespace]}
  (:require [clojure.string :as s]
            [clojure.walk :as walk])
  #?(:clj (:import [clojure.lang Keyword]
                   [java.io ByteArrayInputStream]
                   [java.math RoundingMode]
                   [java.nio.charset Charset]
                   [java.text Normalizer Normalizer$Form NumberFormat]
                   [java.util Base64 Base64$Decoder Locale]))
  (:refer-clojure :exclude [replace]))

#?(:clj (set! *warn-on-reflection* true))

(defn lower-case [x] (when x (s/lower-case x)))

(defn in-lower-case? [x] (boolean (and x (= x (lower-case x)))))

(defn upper-case [x] (when x (s/upper-case x)))

(defn capitalize [x] (when x (s/capitalize x)))

(defn trim [x] (when x (s/trim x)))
(defn triml [x] (when x (s/triml x)))
(defn trimr [x] (when x (s/trimr x)))

(defn trim-newline [x] (when x (s/trim-newline x)))

(defn split
  ([s re] (when s (s/split s re)))
  ([s re limit] (when s (s/split s re limit))))

(defn split-lines [s] (some-> s s/split-lines))

(defn replace [s match replacement] (when s (s/replace s match replacement)))

(def ^{:doc "Alias to clojure.string/blank?"} blank? s/blank?)

(defn not-blank? [s] (not (blank? s)))

(def ^{:doc "Arguments: [coll] or [separator coll]. Alias to clojure.string/join"} join s/join)

(defn join-non-blanks
  ([separator coll]
   (join separator (remove blank? coll)))
  ([coll]
   (join (remove blank? coll))))

(defn stringwalk
  "Walks the given `form` and updates every string with `f`."
  [f form]
  {:pre [(fn? f)]}
  (walk/postwalk (fn [v]
                   (cond-> v
                     (string? v) f))
                 form))

(def trimwalk
  "Walks the given `form` and trims every string."
  (partial stringwalk trim))

(defn blank-as-nil
  "If given string is blank, returns nil. Else returns string."
  [string]
  (when-not (blank? string) string))

(defn trim-or-nil
  "Returns trimmed `s` or nil if `s` is blank."
  [s]
  (some-> s trim blank-as-nil))

(defn trim-join
  "Flattens, trims, ignores blanks and joins. Returns nil if the result is blank."
  [separator & parts]
  (some->> (flatten parts)
           trimwalk
           (join-non-blanks separator)
           blank-as-nil))

(defn includes? [s substr]
  (boolean (when (and s substr)
             (s/includes? s substr))))

(defn includes-i?
  "Similar to `includes?` but trims and lowercases the arguments."
  [s substr]
  (letfn [(f [s] (some-> s trim lower-case))]
    (boolean (includes? (f s) (f substr)))))

(defn last-n [n s]
  (when s
    (apply str (take-last n s))))

(defn limit
  ([s max-length]
    (limit s max-length nil))
  ([s max-length truncated-info]
    (when (and s max-length)
      (if (> (count s) max-length)
        (let [truncated (subs s 0 max-length)]
          (if truncated-info
            (str truncated truncated-info)
            truncated))
        s))))

(defn optional-string? [x]
  (or (nil? x) (string? x)))

(def other-than-string? (complement optional-string?))

#?(:clj (def ascii-pattern #"^[\p{ASCII}]+$"))

#?(:clj (def non-printables #"[\p{Cntrl}]"))

#?(:clj
   (defn strip-non-printables [s]
     (when s
       (s/replace s non-printables ""))))

(defn remove-leading-zeros [s]
  (cond
    (nil? s)               nil
    (re-matches #"^0+$" s) "0"
    :else                  (s/replace s #"^0+", "")))

(defn starts-with? [s prefix]
  (when (and s prefix)
    (s/starts-with? s prefix)))

(defn ends-with? [s postfix]
  (when (and s postfix)
    (s/ends-with? s postfix)))

(defn starts-with-i? [s prefix]
  (when (and s prefix)
    (s/starts-with? (s/lower-case s) (s/lower-case prefix))))

(defn ends-with-i? [s postfix]
  (when (and s postfix)
    (s/ends-with? (s/lower-case s) (s/lower-case postfix))))

(defn substring
  "Nil-safe wrapper for `subs`. Allows `end` to be over bounds (fallbacks to last
  index). For Clojure code, see also `StringUtils/substring` that supports negative
  positions."
  [s start end]
  (some-> s (subs start (min end (count s)))))

(defn unescape-html
  "Change HTML character entities into special characters. Like `hiccup.util/escape-html`
  but backwards."
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
      (replace "&auml;"  "ä")
      (replace "&Auml;"  "Ä")
      (replace "&ouml;"  "ö")
      (replace "&Ouml;"  "Ö")
      (replace "&aring;" "å")
      (replace "&Aring;" "Å")))

#?(:clj (defn ascii? [s]
          (not (nil? (re-matches ascii-pattern s)))))

(defn scandics->ascii [s]
  (when s
    (s/escape s {(char 228) "a", (char 229) "a", (char 246) "o"
                 (char 196) "A", (char 197) "A", (char 214) "O"})))

(defn numeric?
  "True if `s` consists only of numbers, with an optional sign prefix."
  [s]
  (boolean
    (when (string? s)
      (re-matches #"^[\-+]?\d+$" s))))

(defn decimal-number? [s]
  (or (numeric? s)
      (and (string? s)
           (boolean (re-matches #"^\d+\.\d+$" s)))))

(def windows-filename-max-length 255)

(defn =trim-i
  "Compares trimmed lower-cased versions of strings."
  [& xs]
  (apply = (map (comp trim lower-case) xs)))

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
  (-> (flatten parts)
      (as-> $ (join-non-blanks "/" $))
      (replace #"//+" "/")
      trim
      blank-as-nil))

#?(:clj
   (defn escaped-re-pattern
     [string]
     (re-pattern (str "\\Q" string "\\E"))))

#?(:clj
   (def ^Charset utf8 (Charset/forName "UTF-8")))

#?(:clj
   (defn utf8-bytes ^bytes [^String s] (when s (.getBytes s utf8))))

#?(:clj
   (defn utf8-str ^String [^bytes b] (when b (String. b "UTF-8"))))

#?(:clj
   (defn de-accent
     "Replaces accent characters with base letters"
     [^String s]
     (when s (let [normalized (Normalizer/normalize s Normalizer$Form/NFD)]
               (clojure.string/replace normalized #"\p{InCombiningDiacriticalMarks}+" "")))))

#?(:clj
   (defn normalize
     "The NFC normalization is prudent when `s` may contain combining umlauts (e.g., a + (char 776) -> ä)."
     [^String s]
     (some-> s (Normalizer/normalize Normalizer$Form/NFC))))

#?(:clj
   (def ^:private ^Base64$Decoder base64-decoder (Base64/getMimeDecoder)))

#?(:clj
   (defn base64-decode
     "Decode a base64 encoded string using UTF-8."
     ^String [^String s]
     (when s
       (utf8-str (.decode base64-decoder (utf8-bytes s))))))

(defn zero-pad
  "Left-pad `s` with zeros so that its at least `n` characters long."
  [n s]
  (let [gap (- n (count s))]
    (if (pos? gap)
      (str (apply str (take gap (repeat "0"))) s)
      s)))

#?(:clj
   (defn encode-filename
     "Replaces all non-ascii chars and other that the allowed punctuation with dash.
   UTF-8 support would have to be browser specific, see http://greenbytes.de/tech/tc2231/"
     ^String [unencoded-filename]
     (when-let [de-accented (de-accent unencoded-filename)]
       (s/replace
         (last-n windows-filename-max-length de-accented)
         #"[^a-zA-Z0-9\.\-_ ]" "-"))))

#?(:clj
   (defn strip-non-letters [string]
     (apply str (filter #(Character/isLetter ^char %) string))))

#?(:clj
   (defn =alpha-i
   "Compares strings after removing non-letters and lower-casing"
     [& xs]
     (apply = (map (comp lower-case strip-non-letters) xs))))

#?(:clj
   (def whitespace-pattern #"[\s\p{Punct}]+"))

#?(:clj
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
      (str "^.*" fuzzy ".*$")))))

(defprotocol ToPlainString
  (->plain-string [value]))

#?(:clj
   (extend-protocol ToPlainString
     Keyword
     (->plain-string [value] (name value))

     String
     (->plain-string [value] value)

     Object
     (->plain-string [value] (.toString value))

     nil
     (->plain-string [_] "")))

#?(:cljs
   (extend-protocol ToPlainString
     Keyword
     (->plain-string [value] (name value))

     string
     (->plain-string [value] value)

     object
     (->plain-string [value] (.toString value))

     number
     (->plain-string [value] (str value))

     nil
     (->plain-string [_] "")))

#?(:clj
   (defn ->inputstream
     "Avoids the deprecated `java.io.StringBufferInputStream` that outputs in ANSI format"
     ^ByteArrayInputStream [^String string]
     (some-> string (.getBytes "UTF-8") ByteArrayInputStream.)))

#?(:clj
   (defn format-fixed
   "Like [[clojure.core/format]], but uses a fixed Locale to avoid e.g. `.` vs `,` changes in decimals."
   [fmt & args]
   (String/format Locale/ROOT fmt (into-array args))))

#?(:clj
   (defn decimal-formatter-fn
     "Returns a nil-safe number formatter function, which returns strings with given precision"
     [^long min-precision ^long max-precision]
     (let [formatter (doto (NumberFormat/getInstance Locale/ROOT)
                       (.setGroupingUsed false)
                       (.setMinimumFractionDigits min-precision)
                       (.setMaximumFractionDigits max-precision)
                       ;; HALF_EVEN is the default, but let's make it explicit
                       (.setRoundingMode RoundingMode/HALF_EVEN))]
       (fn [value]
         (when value
           (.format formatter value))))))

(defn string-split
  "Like `split` but the `separator` is string. `limit` is the number of resulting parts (one
  -> no splits). Non-positive `limit` values are ignored."
  ([s separator limit]
   (let [limit (when (and (number? limit) (pos? limit))
                 limit)]
     #?(:clj
        (apply split (cond-> [s (escaped-re-pattern separator)]
                       limit (conj limit))))
     #?(:cljs
        (js->clj (apply js/_.split (cond-> [s separator]
                                     limit (conj limit)))))))
  ([s separator]
   (string-split s separator nil)))

(defn suffix
  "Returns a substring from the end of last occurrence of `separator` till the end of
  `s`. If the separator is not found (or is nil/empty string), `s` is returned
  unchanged. If `s` ends with `separator`, an empty string is returned."
  [s separator]
  (if (or (nil? s) (empty? separator))
    s
    (-> s (string-split separator) last (or ""))))
