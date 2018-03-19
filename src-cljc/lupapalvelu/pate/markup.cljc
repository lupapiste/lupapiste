(ns lupapalvelu.pate.markup
  "Simple and safe markup handling.

  markup->tags takes markup string and returns Rum-compliant Hiccup-like syntax.

  The markup syntax is line-oriented. Empty line resets the current
  context (e.g., open formatting chars are implicitly closed).

  Top-level blocks are either paragraphs, lists or blockquotes.

  Lists: if a line begins with list character (*, + or -. Characters
  can be used interchangeably) a list is created. For numbered lists,
  the list items are marked with <number.> notation (e.g., 1.). The
  actual numbers do not matter. Indentation (spaces) denotes list
  scopes. List types can be mixed freely:

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

  Block (list, blockquote or paragraph) can contain formatted text and links.

  Markup formatting conventions:

  *this is bold*    -> [:strong ...]
  _underlined text_ -> [:span.underline ...]
  /italics/         -> [:em ...]

  Formats can be combined:

  *_/ bold underlined italics/_* -> [:strong [:span.underline [:em ...]]]

  Mismatched and unclosed formats are resolved eventually. In other
  words, even if the result is not exactly what you would expect, the
  parser should not fail.

  Links:

  [ url | text]  -> [:a {:href url :target :_blank} text]

  , where url must contain protocol part. Text cannot contain formatting, but it follows the enclosing format:

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
   "<Lines>      := (List / Quote / Blank / Paragraph)+
    <EOL>        := <'\\r'? '\\n'>
    WS           := <' '+>
    WSEOL        := EOL
    Escape       := <'\\\\'> ( '*' | '/' | '_' | '\\\\' | '.'
                             | '-' | '+' | '|' | '[' | ']' | '>' )
    <Text>       := Escape / Link / Plain
    <Texts>      := (Text (WS? Text)*)+
    <Plain>      := #'\\S'+
    Url          := #'https?://' #'[a-zA-Z0-9\\-\\.:_/?&#+]+'
    <LinkText>   := (Escape / WS / Plain)+
    Link         := <'[' WS?> Url < WS? '|' WS?> LinkText <WS? ']'>
    Spaces       := ' '+
    Bullet       := < ('-' | '*' | '+') WS>
    List         := Spaces? (Bullet | Number) Regular+
    Number       := <#'[0-9]+\\.' WS>
    <QuoteMark>  := <'>' WS>
    Quote        := <WS>? QuoteMark Regular+
    <NotSpecial> := !(Bullet | Number | QuoteMark | Blank)
    <Regular>    := (<WS?> NotSpecial Texts <WS?>)+ WSEOL
    Paragraph    := Regular+
    Blank        := (WS? EOL)+
"))

(def text-formats {"*" :strong
                   "/" :em
                   "_" :span.underline})

(defn parse [s]
  (markup-parser (str s "\n")))

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
    (-> (update scope :data #(vec (add-to-data % add)))
        (cons others)
        vec)
    (add-to-data scopes add)))

(defn- close-scope [[closing & others  :as scopes] & [trim?]]
  (if-let [tag (:tag closing)]
    (add-to-scope others (vec (cons tag (:data closing))))
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
      :WSEOL " "
      x)))

(defn- ws-escape-all [x]
  (->> x (map ws-escape) s/join))

(defn- resolve-link [[_ url & text]]
  (let [[http & parts] (rest url)]
    [:a {:href (str http (ws-escape-all parts))
         :target :_blank}
     (ws-escape-all text)]))

(defn- text-tags [markup]
  (loop [[x & xs]                    markup
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
      (assoc m :list-tag (->> (text-tags markup)
                              (cons :li)
                              vec)))))

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

(defn- tagify [[tag & txt-tags]]
  (when-not (= tag :Blank)
    (->> (text-tags txt-tags)
         (cons (case tag
                 :Paragraph :p
                 :Quote     :blockquote))
         vec)))

(defn- block-tags [markup]
  (loop [[x & xs]                    markup
         [scope & others :as scopes] []]
    (case (first x)
      nil (close-all-scopes scopes)

      :List (recur xs (list-tag scopes x))

      (recur xs (add-to-scope (close-all-scopes scopes)
                              (tagify x))))))

(defn markup->tags [markup]
  (block-tags (parse markup)))
