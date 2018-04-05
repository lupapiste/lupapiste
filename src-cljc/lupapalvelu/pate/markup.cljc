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
  <number.> notation (e.g., 1.). The actual numbers do not
  matter. Indentation (spaces) denotes list scopes. List types can be
  mixed freely:

  * List 1, item 1
  - List 1, item 2
    - Sublist, item 1
    + Sublist, item2. This text
  continues here (no blank line)
  - List 1, item 3

  * List 2, item 1
  1. Numbered, item 1
  1. Numbered, item 2
     * Sublist of numbered, item 1

  Blockquotes are marked with >. Indentation does not matter. A
  blockquote is always top-level block.

  > this is blockquote
  * List 1
    > another blockquote (also on the top-level)
    * List 2 (not a sublist)

  If a line is not part of the list of blockquote it is then paragraph:

  Paragraph starts and
  continues on the next line.

  After blank line, a new paragraph
  * List

  Third paragraph.

  Block (heading, list, blockquote or paragraph) can contain formatted
  text and links.

  Markup formatting conventions:

  *this is bold*    -> [:strong ...]
  _underlined text_ -> [:span.underline ...]
  /italics/         -> [:em ...]
  ^superscript^     -> [:sup ...]

  Formats can be combined:

  *_/ bold underlined italics/_* -> [:strong [:span.underline [:em ...]]]

  Mismatched and unclosed formats are resolved eventually. In other
  words, even if the result is not exactly what you would expect, the
  parser should not fail.

  Links:

  [ url | text]  -> [:a {:href url :target :_blank} text]

  , where url must contain protocol part. Text cannot contain
  formatting, but it follows the enclosing format:

  Here is * [https://www.example.org/foobar/index.html| bold link]*

  Note: link cannot be preceded by non-whitespace character:

  This is not link[http://example.net|example]

  This is link [http://example.net|example]-entity

  Special characters can be quoted with backslash:

  \\* Paragraph
  * List

  /hi \\*\\_\\/ bye/ -> [:em hi *_/ bye]

  [http://example.com | \\[ example\\]]
  "
  (:require [clojure.string :as s]
            [instaparse.core :as insta]))

(def markup-parser
  (insta/parser
   "<Lines>      := (Heading / List / Quote / Blank / Paragraph)+
    <EOL>        := <'\\r'? '\\n'>
    WS           := <' '+>
    WSEOL        := EOL
    Escape       := <'\\\\'> ( '*' | '/' | '_' | '^' | '\\\\'
                             | '.' | '#' | '-' | '+' | '|'
                             | '[' | ']' | '>' )
    Bracket      := '[' | ']'
    <Text>       := Escape / Link / Plain / Bracket
    <Texts>      := (Text (WS? Text)*)+
    <Plain>      := #'[^\\s\\[\\]\\\\]+'
    Url          := #'https?://[a-zA-Z0-9\\-\\.:_/?&#]+'
    <LinkText>   := (Escape / WS / Plain)+
    Link         := <'[' WS?> Url < WS? '|' WS?> LinkText <WS? ']'>
    Spaces       := ' '+
    Bullet       := < ('-' | '*' | '+') WS>
    List         := Spaces? (Bullet | Number) Regular+
    Number       := <#'[0-9]+\\.' WS>
    Title        := '#'+ <WS>
    Heading      := <WS?> Title Regular+
    <QuoteMark>  := <'>' WS>
    Quote        := <WS>? QuoteMark Regular+
    <NotSpecial> := !(Bullet | Number | QuoteMark | Blank)
    <Regular>    := (<WS?> NotSpecial Texts <WS?>)+ WSEOL
    Paragraph    := Regular+
    Blank        := (WS? EOL)+
"))

(def text-formats {"*" :strong
                   "/" :em
                   "_" :span.underline
                   "^" :sup})

(defn- parse [s]
  (->> (s/split (or s "") #"^\\s*\r?\n")
       (remove s/blank?)
       (map #(markup-parser (str % "\n")))
       (apply concat)))

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

(defn- close-scope [[closing & others  :as scopes] & [trim?]]
  (if-let [tag (:tag closing)]
    (add-to-scope others (consv tag {} (:data closing)))
    scopes))

(defn- close-all-scopes [scopes]
  (loop [[x & xs :as scs] scopes]
    (if (:tag x)
      (recur (close-scope scs))
      scs)))

(defn- ws-escape [x]
  (if (string? x)
    x
    (case (first x)
      :WS " "
      :Escape (last x)
      :Bracket (last x)
      :WSEOL " "
      x)))

(defn- ws-escape-all [x]
  (->> x (map ws-escape) s/join))

(defn- resolve-link [[_ url & text]]
  (let [[http & parts] (rest url)]
    [:a {:href (str http (ws-escape-all parts))
         :target :_blank}
     (ws-escape-all text)]))

(defn- split-markup [markup]
  (reduce (fn [acc m]
            (concat acc
                    (if (string? m)
                      (map str (vec m))
                      [m])))
          []
          markup))

(defn- text-tags [markup]
  (loop [[x & xs]                    (split-markup markup)
         [scope & others :as scopes] []]
    (let [{scope-tag  :tag
           scope-data :data} scope
          format-tag         (get text-formats x)]
      (cond
        (or (nil? x)
            (and (= (first x) :WSEOL)
                 (empty? xs)))
        (close-all-scopes scopes)

        format-tag
        (recur xs
               (if (= scope-tag format-tag)
                 (close-scope scopes)
                 (new-scope scopes format-tag)))

        :else
        (recur xs (add-to-scope scopes
                                (if (= :Link (first x))
                                  (resolve-link x)
                                  (ws-escape x))))))))

(defn- resolve-list [markup]
  (loop [[x & xs :as markup] (rest markup)
         m                   {:list-depth 0}]
    (case (first x)
      :Spaces (recur xs (assoc m :list-depth (-> x rest count)))
      :Bullet (recur xs (assoc m :list-type :ul))
      :Number (recur xs (assoc m :list-type :ol))
      (assoc m :list-tag (consv :li {} (text-tags markup))))))

(defn- list-tag [scopes markup]
  (let [{:keys [list-depth list-type list-tag]} (resolve-list markup)]
    (loop [[scope & others :as scopes] scopes]
      (let [depth (:depth scope)]
        (cond
          (or (not depth)
              (> list-depth depth))
          (add-to-scope (new-scope scopes list-type :depth list-depth)
                        list-tag)

          (and (= depth list-depth))
          (if (= (:tag scope) list-type)
            (add-to-scope scopes list-tag)
            (-> (close-scope scopes)
                (new-scope list-type :depth list-depth)
                (add-to-scope list-tag)))

          (> depth list-depth)
          (recur (close-scope scopes)))))))

(defn- tagify [[tag & content]]
  (case tag
    :Blank nil
    :Heading (let [level (-> content first rest count)]
               (consv (keyword (str "h" (min level 6)))
                      {}
                      (text-tags (rest content))))
    (consv (case tag
             :Paragraph :p
             :Quote     :blockquote)
           {}
           (text-tags content))))

(defn- block-tags [markup]
  (loop [[x & xs]                    markup
         [scope & others :as scopes] []]
    (case (first x)
      nil (close-all-scopes scopes)

      :List (recur xs (list-tag scopes x))

      (recur xs (add-to-scope (close-all-scopes scopes)
                              (tagify x))))))

(defn markup->tags
  "Converts given markup to Hiccup-like Rum-compliant tags. See the
  namespace documentation for the markup syntax. Every tag has an
  attribute map even if it is empty. This makes it easy to add
  specific attributes lates (e.g., React keys)"
  [markup]
  (block-tags (parse markup)))
