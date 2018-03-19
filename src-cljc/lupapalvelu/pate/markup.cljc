(ns lupapalvelu.pate.markup
  "Simple and safe markup handling."
  (:require [instaparse.core :as insta]
            [clojure.string :as s]))

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

(defn new-scope [scopes tag & kvs]
  (cons (merge {:tag tag :data []}
               (apply hash-map kvs)) scopes))

(defn add-to-data [data x]
  (->> (if (and (-> data last string?)
                (string? x))
         (conj (-> data butlast vec) (str (last data) x))
         (conj (vec data) x))
       (remove nil?)))

(defn add-to-scope [[scope & others :as scopes] add]
  (if (:tag scope)
    (-> (update scope :data #(vec (add-to-data % add)))
        (cons others)
        vec)
    (add-to-data scopes add)))

(defn close-scope [[closing & others  :as scopes] & [trim?]]
  (if-let [tag (:tag closing)]
    (add-to-scope others (vec (cons tag (:data closing))))
    scopes))

(defn close-all-scopes [scopes]
  (loop [[x & xs :as scs] scopes]
    (if (:tag x)
      (recur (close-scope scs))
      scs)))

(defn ws-escape [x]
  (if (string? x)
    x
    (case (first x)
      :WS " "
      :Escape (last x)
      :WSEOL " "
      x)))

(defn ws-escape-all [x]
  (->> x (map ws-escape) s/join))

(defn resolve-link [[_ url & text]]
  (let [[http & parts] (rest url)]
    [:a {:href (str http (ws-escape-all parts))
         :target :_blank}
     (ws-escape-all text)]))

(defn text-tags [markup]
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

(defn resolve-list [markup]
  (loop [[x & xs :as markup] (rest markup)
         m                   {:list-depth 0}]
    (case (first x)
      :Spaces (recur xs (assoc m :list-depth (-> x rest count)))
      :Bullet (recur xs (assoc m :list-type :ul))
      :Number (recur xs (assoc m :list-type :ol))
      (assoc m :list-tag (->> (text-tags markup)
                              (cons :li)
                              vec)))))

(defn list-tag [scopes markup]
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

(defn tagify [[tag & txt-tags]]
  (when-not (= tag :Blank)
    (->> (text-tags txt-tags)
         (cons (case tag
                 :Paragraph :p
                 :Quote     :blockquote))
         vec)))

(defn block-tags [markup]
  (loop [[x & xs]                    markup
         [scope & others :as scopes] []]
    (case (first x)
      nil (close-all-scopes scopes)

      :List (recur xs (list-tag scopes x))

      (recur xs (add-to-scope (close-all-scopes scopes)
                              (tagify x))))))

(defn markup->tags [markup]
  (block-tags (parse markup)))


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

  Tavallista tekstiä jonka perässä [http://evolta.fi:900/h\\]ii/hoo/index.html|linkki\\| jonnekin]")

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
  * dii * doo
  1. Num 1.
  2. Num 2.")
