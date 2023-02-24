(ns lupapalvelu.pate.markup
  "Simple and safe markup handling.

  markup->tags takes markup string and returns Rum-compliant
  Hiccup-like syntax.

  The markup syntax is line-oriented. Empty line resets the current
  context (e.g., open formatting chars are implicitly closed).

  Top-level blocks are either headings, lists, blockquotes or
  paragraphs.

  Heading levels (h1-h6) are denoted with # characters

  # H1 heading
  ## H2 heading
  ...
  ###### H6 heading
  ############## H6 heading

  Lists: if a line begins with list character (*, + or -. Characters
  can be used interchangeably and must be followed by space) a list is
  created. For numbered lists, the list items are marked with
  <number.> notation (e.g., 1.).

  If the number itself is 1, normal numbering is used.
  Otherwise the given number determines what the numbering will be from that point onward in the list.

  Indentation (spaces) denotes list scopes. List types can be
  mixed freely:

  * List 1, item 1
  - List 1, item 2
    - Sublist, item 1
    + Sublist, item2. This text ends
  new line here, not in the list
  - List 2 on the same level as one, item 3

  * List 3, item 1
  1. Numbered, item 1
  1. Numbered, item 2
     * Sublist of numbered, item 1

  Blockquotes are marked with >. Indentation does not matter. A
  blockquote is always top-level block.

  > this is blockquote
  * List 1
    > another blockquote (also on the top-level)
    * List 2 (not a sublist)

  If a line is not part of the list of blockquote it is then just text (span)

  Text starts and
  continues on the next line. The line break is preserved.

  Blank lines are just breaks (br)
  * List

  Third paragraph.

  Block (heading, list, blockquote or paragraph) can contain formatted
  text and links.

  Markup formatting conventions:

  *this is bold*    -> [:strong ...]
  ~underlined text~ -> [:span.underline ...]
  _italics_         -> [:em ...]
  ^superscript^     -> [:sup ...]

  Formats can be combined:

  *~_ bold underlined italics_~* -> [:strong [:span.underline [:em ...]]]

  Mismatched and unclosed formats are resolved eventually. In other
  words, even if the result is not exactly what you would expect, the
  parser should not fail.

  Links:

  [ text | url]  -> [:a {:href url :target :_blank} text]

  , where url must contain protocol part. Text cannot contain
  formatting, but it follows the enclosing format:

  Here is * [bold link | https://www.example.org/foobar/index.html]*

  Any character can be quoted with backslash:

  \\* Paragraph
  * List

  _hi \\*\\_\\/ bye_ -> [:em hi *_/ bye]

  [[example] | http://example.com\\]]
  "
  (:require [clojure.string :as s]
            [sade.shared-util :as util]))

(def text-formats {"*" :strong
                   "_" :em
                   "~" :span.underline
                   "^" :sup})

(defn- consv
  ([x seq]
   (vec (cons x seq)))
  ([x y seq]
   (consv x (cons y seq))))

(defn- new-scope [scopes tag & kvs]
  (cons (merge {:tag tag :data []}
               (apply hash-map kvs)) scopes))

(defn- add-to-data [data x]
  (->> (if (and (-> data last string?)
                (string? x))
         (conj (-> data butlast vec) (str (last data) x))
         (conj (vec data) x))
       (remove nil?)))

(defn- add-to-scope [[scope & others :as scopes] add]
  (if (:tag scope)
    (consv (update scope :data #(vec (add-to-data % add)))
           others)
    (add-to-data scopes add)))

(defn- close-scope
  "Takes the next scope and splits it into a parent and child elements (e.g. <ol> and <li> elements) if able"
  [[scope & others :as scopes]]
  (if-let [tag (:tag scope)]
    (->> (:data scope) ; The children are stored in "data"
         (consv tag {})
         (add-to-scope others))
    scopes))

(defn- close-all-scopes [scopes]
  (loop [[x :as scs] scopes]
    (if (:tag x)
      (recur (close-scope scs))
      scs)))

(defn- resolve-link [url text]
  [:a {:href   (s/trim url)
       :target :_blank}
   (s/trim text)])

(defn- get-li-opts
  "Value overrides the default numbering from that point onward in ordered lists.
  Using the number '1' allows the user to use the default numbering"
  [{:keys [type index]}]
  (cond-> {}
    (and (= type :ordered)
         (number? index)
         (not= index 1))
    (assoc :value index)))

(defn- resolve-list [{:keys [type level text] :as tag}]
  {:list-depth level
   :list-type  (if (= type :bullet) :ul :ol)
   :list-tag   (consv :li (get-li-opts tag) text)})

(def bullet-line #"^([ \t]*)[*|\-|+][ \t]+(.*)$")
(def ordered-line #"^([ \t]*)(\d+)\.[ \t]+(.*)$")
(def quote-line #"^[ \t]*>[ \t]+(.*)$")
(def header-line #"^[ \t]*(#+)[ \t]+(.*)$")
(def link-definition #"\[([^|]+)\|\s*(https?://[a-zA-Z0-9\-\.:_/?&#]+)\s*\]")

(defn- parse-line [line]
  (if-let [[_ indent text] (re-find bullet-line line)]
    {:type  :bullet
     :level (count indent)
     :text  (s/trim text)}
    (if-let [[_ indent index text] (re-find ordered-line line)]
      {:type  :ordered
       :index (util/->int index)
       :level (count indent)
       :text  (s/trim text)}
      (if-let [[_ text] (re-find quote-line line)]
        {:type :quote
         :text text}
        (if-let [[_ level text] (re-find header-line line)]
          {:type  :header
           :level (count level)
           :text  (s/trim text)}
          (if (s/blank? line)
            {:type :empty}
            {:type :text
             :text (s/trim line)}))))))

(defn- text-tags [text]
  (loop [[x & xs :as txt]   (s/split text #"")
         [scope :as scopes] []]
    (let [{scope-tag :tag} scope
          format-tag       (get text-formats x)]

      (cond
        (nil? x)
        (close-all-scopes scopes)

        format-tag
        (recur xs
               (if (= scope-tag format-tag)
                 (close-scope scopes)
                 (new-scope scopes format-tag)))

        (= x "\\")
        (recur (rest xs) (add-to-scope scopes (or (first xs) "")))

        (= x "[")
        (let [[full-match text url] (re-find link-definition (s/join txt))]
          (if url
            (recur (drop (dec (count full-match)) xs)
                   (add-to-scope scopes (resolve-link url text)))
            (recur xs (add-to-scope scopes x))))

        :else
        (recur xs (add-to-scope scopes x))))))

(defn- list-tag
  "Lists are bullet point or ordered lists (i.e. eventually enclosed in <ol> or <ul> elements)"
  [scopes line-tag]
  (let [{:keys [list-depth
                list-type
                list-tag]} (resolve-list line-tag)
        add-new-scope      #(-> (new-scope % list-type :depth list-depth)
                                (add-to-scope list-tag))]
    (loop [[scope :as scopes] scopes]
      (let [depth (:depth scope)]
        (cond
          (or (not depth)
              (> list-depth depth))
          (add-new-scope scopes)

          (and (= depth list-depth))
          (if (= (:tag scope) list-type)
            (add-to-scope scopes list-tag)
            (-> (close-scope scopes)
                (add-new-scope)))

          (> depth list-depth)
          (recur (close-scope scopes)))))))

(defn- tagify [{:keys [type level text]}]
  (letfn [(tag-fn [tag break?]
            (cond-> (consv tag {} text)
              break? (conj [:br {}])))]
    (case type
     :empty  [:br {}]
     :header (tag-fn (keyword (str "h" (min level 6))) false)
     :quote  (tag-fn :blockquote false)
     :text   (tag-fn :span true))))

(defn- block-tags [line-tags]
  (loop [[x & xs] line-tags
         scopes   []]
    (case (:type x)
      nil                (close-all-scopes scopes)
      (:bullet :ordered) (recur xs (list-tag scopes x))
      (recur xs (add-to-scope (close-all-scopes scopes)
                              (tagify x))))))

(defn line->tags [line]
  (let [{:keys [type text] :as parsed-line} (parse-line (s/trimr line))]
    (cond-> (select-keys parsed-line [:type :level :index])
      (not= type :empty) (assoc :text (text-tags text)))))

(defn markup->tags
  "Converts given markup to Hiccup-like Rum-compliant tags. See the
  namespace documentation for the markup syntax. Every tag has an
  attribute map even if it is empty. This makes it easy to add
  specific attributes later (e.g., React keys)"
  [markup]
  (some->> (not-empty markup)
           (s/trimr)
           (s/split-lines)
           (drop-while s/blank?)
           (map line->tags)
           (block-tags)))
