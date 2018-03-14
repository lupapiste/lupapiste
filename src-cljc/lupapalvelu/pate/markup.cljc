(ns lupapalvelu.pate.markup
  "Simple and safe markup handling."
  (:require [instaparse.core :as insta]))

#_(def markup-parser
  (insta/parser
   "<Lines>      := (List / Ordered / Quote / Blank / Paragraph)+
    <EOL>        := <'\\n'>
    <WS>         := <' '+>
    <KeepWS>     := ' '+
    <Text>       := Link / Bold / Underline / Italics / Plain
    <Texts>      := (Text (Spaces? Text)*)+
    <Plain>      := #'[^\\s*_/]+'
    <Plains>     := (Plain (WS? Plain)*)*
    <Star>       := <'*'>
    <Dash>       := <'_'>
    <Slash>      := <'/'>
    Bold         := Star Texts? Star
    Underline    := Dash Texts? Dash
    Italics      := Slash Texts? Slash
    <Url>        := #'https?://[a-zA-Z0-9\\-\\.:_/]+'
    <LinkText>   := #'[^|]+'
    Link         := <'['> LinkText <'|'> Url <']'>
    Spaces       := ' '+
    <Bullet>     := < ('-' | '*' | '+') WS>
    List         := Spaces Bullet Regular+
    <Number>     := <#'[0-9]+\\.' WS>
    Ordered      := Spaces Number Regular+
    <QuoteMark>  := <'>' WS>
    Quote        := WS QuoteMark Regular+
    <NotSpecial> := !(Bullet | Number | QuoteMark | Blank)
    <Regular>    := (Spaces? NotSpecial Texts Spaces?)+ EOL
    Paragraph    := Regular+
    Blank        := (WS? EOL)+
"))

(def markup-parser
  (insta/parser
   "<Lines>      := (List / Ordered / Quote / Blank / Paragraph)+
    <EOL>        := <'\\r'? '\\n'>
    <WS>         := <' '+>
    Escape       := <'\\\\'> ( '*' | '\\\\' | '-' | '+' | '|' | '[' | ']' | '>' )
    <Text>       := ( Escape / Link / Plain)
    <Texts>      := (Text (WS? Text)*)+
    <Plain>      := #'\\S+'
    <Url>        := #'https?://[a-zA-Z0-9\\-\\.:_/]+'
    <LinkText>   := #'[^|]+'
    Link         := <'['> LinkText <'|'> Url <']'>
    Spaces       := ' '+
    <Bullet>     := < ('-' | '*' | '+') WS>
    List         := Spaces? Bullet Regular+
    <Number>     := <#'[0-9]+\\.' WS>
    Ordered      := Spaces? Number Regular+
    <QuoteMark>  := <'>' WS>
    Quote        := WS? QuoteMark Regular+
    <NotSpecial> := !(Bullet | Number | QuoteMark | Blank)
    <Regular>    := (WS? NotSpecial Texts WS?)+ EOL
    Paragraph    := Regular+
    Blank        := (WS? EOL)+
"))

(defn parse [s]
  (markup-parser (str s "\n")))

(defn count-spaces [[kw spaces & xs :as parsed] ]
  (-> spaces rest count))

(declare resolve-tags)

(defn context-tag [{:keys [tag data]} & [extra]]
  [(if (= tag :List) :ul :ol)
   (cond-> data
     (seq extra) (conj extra))])

(defn collapse-context [[ctx & ctxs :as context]]
  (let [[x & xs] ctxs]
    (cons (update x :data #(conj % (context-tag ctx)))
          xs)))

(defn reduce-context [context]
  (reduce (fn [acc ctx]
            [(context-tag ctx acc)]
            #_(collapse-context (cons ctx acc)))
          []
          context))

(defn resolve-list [[x & xs :as parsed] resolved [ctx & ctxs :as context]]
  (let [spaces (count-spaces x)]
    (case (compare spaces (:depth ctx))
      0  {:parsed   xs
          :resolved resolved
          :context  (cons (update ctx :data #(conj % x)) ctxs)}
      -1 (if (seq ctxs)
           {:parsed   parsed
            :resolved resolved
            :context (collapse-context context)
            #_(cons (update (first ctxs) :data #(conj % [:ul (:data ctx)]))
                            (rest ctxs))}
           {:parsed   parsed
            :resolved (conj resolved (context-tag ctx))
            :context  []})
      1  {:parsed   parsed
          :resolved resolved
          :context (cons {:depth spaces
                          :data []
                          :tag (first x)}
                         context)})
    ))

(defn resolve-tags
  [{:keys [parsed resolved context] :as options}]
  (let [[x & xs] parsed]
    (if x
      (case (first x)
        :List (resolve-tags (resolve-list parsed resolved context))
        :Ordered (resolve-tags (resolve-list parsed resolved context))
        (resolve-tags {:parsed xs
                       :resolved (concat resolved (reduce-context context) [x])
                       :context []}))
      options)))



(defn markup->tags [markup]
  (let  [{:keys [resolved
                 context]} (resolve-tags {:parsed (parse markup)
                                          :resolved []
                                          :context []})]
    (concat resolved (reduce-context context))))


(def txt
  "hello world
  Tämä on *bold*
  * Yksi
  ja jatkuu
  * Kaksi


     - aaa
     - bbb lisää tekstiä
  Teksti jatkuu uudella rivillä.
  1. Numba 1
  2. Numba 2
  Ja /kursiivilla/ jatketaan
  ja _vielä alleviivaus_
  > blockquote *hei* hou

  * Jeah

  Tavallista tekstiä jonka perässä [linkki jonnekin|http://evolta.fi:900/hii/hoo/index.html]")

(def txt2
  "hello world

  Tämä on *bold*
  * Yksi
  * Kaksi
  ja jatkuu
     - kolme
  + Neljä")

(def txt3
  "     * Yksi
  ja jatkuu

  - hei")

(def txt4 "hii *hoo*")

(def txt5 "hii hoo
   1. Eka
  ... ja 1. jatkuu > juu nääs
  2. Toka

  ** foo * bar
  * dii * doo")
