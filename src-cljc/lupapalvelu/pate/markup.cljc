(ns lupapalvelu.pate.markup
  "Simple and safe markup handling."
  (:require [instaparse.core :as insta]
            [rum.core :as rum]
            [clojure.string :as s]))

(def markup-parser
  (insta/parser
   "<Lines>      := (List / Ordered / Quote / Blank / Paragraph)+
    <EOL>        := <'\\r'? '\\n'>
    WS           := <' '+>
    Escape       := <'\\\\'> ( '*' | '/' | '_' | '\\\\' | '.'
                             | '-' | '+' | '|' | '[' | ']' | '>' )
    <Text>       := Escape / Link / Plain
    <Texts>      := (Text (WS? Text)*)+
    <Plain>      := #'\\S'+
    Url          := #'https?://' (#'[a-zA-Z0-9\\-\\.:_/]' | Escape)+
    <LinkText>   := (Escape / WS / Plain)+
    Link         := <'['> LinkText <'|'> Url <']'>
    Spaces       := ' '+
    <Bullet>     := < ('-' | '*' | '+') WS>
    List         := Spaces? Bullet Regular+
    <Number>     := <#'[0-9]+\\.' WS>
    Ordered      := Spaces? Number Regular+
    <QuoteMark>  := <'>' WS>
    Quote        := <WS>? QuoteMark Regular+
    <NotSpecial> := !(Bullet | Number | QuoteMark | Blank)
    <Regular>    := (WS? NotSpecial Texts WS?)+ EOL
    Paragraph    := Regular+
    Blank        := (WS? EOL)+
"))

(def text-formats {"*" :strong
                   "/" :em
                   "_" :span.underline})

(defn parse [s]
  (markup-parser (str s "\n")))

(defn new-scope [scopes scope]
  (cons scope scopes))

(defn add-to-data [data x]
  (->> (if (and (-> data last string?)
                (string? x))
         (conj (-> data butlast vec) (str (last data) x))
         (conj data x))
       (remove nil?)
       vec))

(defn add-to-scope [[scope & others :as scopes] add]
  (if (:tag scope)
    (-> (update scope :data #(add-to-data % add))
        (cons others)
        vec)
    (conj (vec scopes) add)))

(defn close-scope [[closing & others  :as scopes]]
  (if-let [tag (:tag closing)]
    (add-to-scope others (vec (cons tag (:data closing))))
    scopes))

(defn close-all-scopes [scopes]
  (loop [[x & xs :as scs] scopes]
    (if (:tag x)
      (recur (close-scope scs))
      scs)))

(defn count-spaces [[kw spaces & xs :as parsed] ]
  (if (= (first spaces) :Spaces)
    (-> spaces rest count)
    0))

(declare resolve-tags)

(defn context-tag [{:keys [tag data]} & [extra]]
  (->> (cond-> data
         (seq extra) (conj extra))
       (cons (case tag
               :List    :ul
               :Ordered :ol
               tag))
       (remove nil?)
       vec))

(defn collapse-context [[ctx & ctxs :as context]]
  (let [[x & xs] ctxs]
    (cons (update x :data #(conj % (context-tag ctx)))
          xs)))

(defn reduce-context [context]
  (reduce (fn [acc ctx]
            [(context-tag ctx acc)])
          []
          context))

(defn ws-escape [x]
  (if (string? x)
    x
    (case (first x)
      :WS " "
      :Escape (last x)
      x)))

(defn ws-escape-all [x]
  (->> x (map ws-escape) s/join))

(declare text-tags)

(defn resolve-link [link]
  (let [[_ http & url] (last link)]
    [:a {:href (str http (ws-escape-all url))
        :target :_blank}
     (ws-escape-all (-> link rest butlast))]))

(defn text-tags [markup]
  (loop [[x & xs]               markup
         scopes                 []
         {tag :tag :as context} nil]
    (let [format-tag (get text-formats x)]
      (cond
        (nil? x)
        (reduce (fn [acc ctx]
                  (cond-> (vec (context-tag ctx))
                    acc (conj acc)))
                nil
                (cons context scopes))

        format-tag
        (if (= tag format-tag)
          (let [scope (first scopes)]
            (recur xs
                   (rest scopes)
                   (update scope
                           :data
                           #(conj (vec %) (context-tag context)))))
          (recur xs (cons context scopes) {:tag  format-tag
                                           :data ""}))
        :else
        (recur xs scopes (update context
                                 :data
                                 (fn [data]
                                   (let [item   (if (= :Link (first x))
                                                  (resolve-link x)
                                                  (ws-escape x))
                                         latest (last data)]
                                     (if (and (string? item)
                                              (string? latest))
                                       (conj (vec (butlast data))
                                             (str latest item))
                                       (conj (vec data) item))))))
        ))))

(defn tagify [[tag & txt-tags]]
  (when-not (= tag :Blank)
    (->> (text-tags txt-tags)
         (cons (case tag
                 :Paragraph :p
                 :Quote     :blockquote))
         vec)))

(defn resolve-list [[x & xs :as parsed] resolved [ctx & ctxs :as context]]
  (let [spaces (count-spaces x)]
    (case (compare spaces (:depth ctx))
      0  {:parsed   xs
          :resolved resolved
          :context  (cons (update ctx
                                  :data
                                  #(->> (drop (if (zero? spaces) 1 2) x)
                                        text-tags
                                        (cons :li)
                                        vec
                                        (conj %)
                                        vec))
                          ctxs)}
      -1 (if (seq ctxs)
           {:parsed   parsed
            :resolved resolved
            :context  (collapse-context context)}
           {:parsed   parsed
            :resolved (conj resolved  (context-tag ctx))
            :context  []})
      1  {:parsed   parsed
          :resolved resolved
          :context  (cons {:depth spaces
                           :data  []
                           :tag   (first x)}
                          context)})))

(defn resolve-scopes
  [{:keys [parsed resolved context] :as options}]
  (let [[x & xs] parsed]
    (if x
      (case (first x)
        :List (resolve-scopes (resolve-list parsed resolved context))
        :Ordered (resolve-scopes (resolve-list parsed resolved context))
        (resolve-scopes {:parsed xs
                         :resolved (->> [(tagify x)]
                                        (remove nil?)
                                        (concat resolved
                                                (reduce-context context)))
                         :context []}))
      options)))

(defn markup->tags [markup]
  (let  [{:keys [resolved
                 context]} (resolve-scopes {:parsed (parse markup)
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

  Tavallista tekstiä jonka perässä [linkki\\| jonnekin|http://evolta.fi:900/h\\]ii/hoo/index.html]")

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
