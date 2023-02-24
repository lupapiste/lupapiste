(ns lupapalvelu.company-contract
  "Generates Lupapiste Yritystili contract pdfs."
  (:require [clojure.java.io :as io]
            [garden.core :as garden]
            [garden.selectors :as sel]
            [hiccup.core :as h]
            [lupapalvelu.company :refer [Company]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [rum.core :as rum]
            [sade.env :refer [in-dev]]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream InputStream]))

(def cell-widths (range 10 101 5))
(def default-font {:font-family "'Mulish'"
                   :font-size   "11pt"})
(def h1-font {:font-weight    :bold
              :font-size      "24pt"})
(def h2-font {:font-size      "14pt"
              :text-transform :uppercase})
(def default-indent {:padding-left "6em"})
(def extra-indent {:padding-left "8em"})

(defn- html [body]
  (str "<!DOCTYPE html>"
       (rum/render-static-markup
         [:html
          [:head
           [:meta {:http-equiv "content-type"
                   :content    "text/html; charset=UTF-8"}]
           [:style
            {:type "text/css"
             :dangerouslySetInnerHTML
                   {:__html (garden/css
                              [[:html default-font]
                               [:body {:padding-left  "2em"
                                       :padding-right "2em"}]
                               [:h1 (merge h1-font
                                           {:padding-bottom   "4em"
                                            :border-bottom    "1px solid #afccef"
                                            :page-break-after :always})]
                               [:.section {:page-break-inside :avoid}

                                [:h2 (merge h2-font
                                            {:margin-top "2em"})]
                                [:h3 (merge default-indent
                                            {:font-weight :bold})]
                                [:p (merge default-indent
                                           {:margin-top   "1em"})
                                 [:a {:color "#F93A6D"}
                                  ;; Lupapiste link is often rendered too close to the preceding word.
                                  [:&.gap {:margin-left "0.5em"}]]]
                                [:p.single {:margin-top    "0"
                                            :margin-bottom "0"
                                            :min-height    "1em"}
                                 [:&.indent extra-indent]]
                                [:ol {:padding-left "7em"}]]
                               [:.table-section {:display :table
                                                 :width   "100%"}
                                [:&.border-top {:margin-top  "1em"
                                                :border-top  "1px solid black"
                                                :padding-top "1em"}]
                                [:&.border-bottom {:margin-bottom  "1em"
                                                   :border-bottom  "1px solid black"
                                                   :padding-bottom "1em"}]
                                [:&.header {:padding       0
                                            :border-bottom "1px solid black"}]
                                [:&.footer {:border-top "1px solid black"}]
                                [:>.row {:display :table-row}
                                 [:&.border-top [:>.cell {:border-top "1px solid black"}]]
                                 [:&.border-bottom [:>.cell {:border-bottom "1px solid black"}]]
                                 [:&.pad-after [:>.cell {:padding-bottom "0.5em"}]]
                                 [:&.pad-before [:>.cell {:padding-top "0.5em"}]]
                                 [:.cell {:display       :table-cell
                                          :white-space   :pre-wrap
                                          :padding-right "1em"}
                                  [:&:last-child {:padding-right 0}]
                                  [:&.right {:text-align :right}]
                                  [:&.center {:text-align :center}]
                                  [:&.bold {:font-weight :bold}]
                                  [:&.nowrap {:white-space :nowrap}]
                                  [:&.indent {:padding-left "2em"}]
                                  [:&.page-number {:font-weight :bold
                                                   :font-size   "1.1em"}]
                                  [:&.border-top {:border-top "1px solid black"}]]
                                 (map (fn [n]
                                        [(keyword (str ".cell.cell--" n))
                                         {:width (str n "%")}])
                                      cell-widths)
                                 [:&.spaced
                                  [(sel/+ :.row :.row)
                                   [:.cell {:padding-top "0.5em"}]]]]]
                               ])}}]]
          [:body body]])))

(defn- loc [{lang :lang} & terms]
  (i18n/localize lang :company-contract terms))

(def header-html
  (-> [:div {:style "width: 100%"}
       [:style html-pdf/simple-header-style]
       [:div.header
        [:div.right
         [:div.logo
          html-pdf/lupapiste-logo]]]]
      h/html))

(defn- footer [context]
  [:div {:style "width: 100%"}
   [:style html-pdf/simple-footer-style]
   [:div.footer
    [:div.left
     (loc context :cloudpermit) [:br]
     (loc context :cloudpermit.street) [:br]
     (loc context :cloudpermit.city) [:br]
     [:a {:href "https://cloudpermit.com"} "cloudpermit.com"]]
    [:div.right
     [:span.pageNumber ""]]]])


(def COMMENT     #"^%%.*")
(def TITLE       #"^(#+)\s+(.*)")
(def SINGLE      #"^#(--|----)(\s+(.*)|\s*)")
(def PLACEHOLDER #"^\$\{([\w\.\-]+)}")

(defschema Tag
  "Simplified Hiccup-like HTML element."
  [(sc/one (sc/enum :h1 :h2 :h3 :p :p.single :p.single.indent
                    :a :a.gap :div.section) "Supported tag") sc/Any])

(defschema Context
  {sc/Keyword sc/Any})

(def Lang (sc/pred i18n/supported-lang? "Supported language"))

(def Contact (sc/pred #(let [v (vals (select-keys % [:firstName :lastName]))]
                         (and (every? string? v)
                              (some ss/not-blank? v)))
                      "Contact fullname"))

(defschema Account
  {:type        (sc/pred (partial util/includes-as-kw? [:account5 :account15 :account30])
                         "Company account type")
   :price       (sc/pred pos? "Positive number")
   :billingType (sc/pred (partial util/includes-as-kw? [:monthly :yearly])
                         "Company account billing type")})

(sc/defn ^:private ^:always-validate parse-title :- (sc/maybe Tag)
  [line :- sc/Str]
  (when-let [[_ level text] (re-matches TITLE line)]
    (let [level (count level)]
      (when (< level 4)
        [(keyword (str "h" level)) (ss/trim text)]))))

(sc/defn ^:private ^:always-validate parse-single :- (sc/maybe Tag)
  [line :- sc/Str]
  (when-let [[_ level text] (re-matches SINGLE line)]
    [(util/kw-path :p.single (when (= level "----") :indent)) (ss/trim text)]))

(sc/defn ^:private ^:always-validate fill-placeholders :- [sc/Any]
  [context :- Context
   text    :- sc/Str]
  (loop [text    text
         current ""
         content []]
    (let [close-current       #(cond-> content
                                 (not-empty current) (conj current))
          [placeholder value] (re-find PLACEHOLDER text)]
      (cond
        (empty? text) (close-current)
        value         (recur (subs text (count placeholder))
                             ""
                             (conj (close-current)
                                   (or (get-in context (util/split-kw-path value))
                                       (assert false (str "No value for " value " in context.")))))
        :else         (recur (subs text 1) (str current (first text)) content)))))

(sc/defn ^:private ^:always-validate make-tag :- Tag
  [context :- Context
   tag     :- sc/Keyword
   text    :- sc/Str]
  (->> (fill-placeholders context text)
       (cons tag)
       vec))

(sc/defn ^:private ^:always-validate sections-for-tags :- [Tag]
  "Divides contents into sections (for more sensible paging) and sets the H2 numbering."
  [tags :- [Tag]]
  (loop [[x & xs] tags
         tags     []
         section  nil
         h2n      1]
    (let [close-section #(cond-> tags
                           section (conj (vec (cons :div.section section))))]
      (case (first x)
        nil (close-section)
        :h1 (recur xs (conj (close-section) x) nil h2n)
        :h2 (recur xs (close-section) [(vec (concat [:h2 (str h2n ". ")] (rest x)))] (inc h2n))
        (if section
          (recur xs tags (conj section x) h2n)
          (recur xs (conj tags x) nil h2n))))))

(sc/defn ^:private ^:always-validate text->tags :- [Tag]
  "Parses `text` and processes it into HTML tags. `context` is used for resolving the placeholders."
  [context :- Context
   text    :- sc/Str ]
  (loop [[line & xs] (map ss/trim (ss/split text #"\n"))
         open        nil
         tags        []]
    (let [close-open (fn []
                       (cond-> tags
                         open (conj open)))
          add-tag    (fn [tag text]
                       (conj (close-open) (make-tag context tag text)))
          title      (when line (parse-title line))
          single     (when line (parse-single line))]
      (cond
        (nil? line)               (sections-for-tags (close-open))
        (ss/blank? line)          (recur xs nil (close-open))
        (re-matches COMMENT line) (recur xs open tags)
        title                     (recur xs nil (add-tag (first title) (second title)))
        single                    (recur xs nil (add-tag (first single) (second single)))
        open                      (recur xs (vec (concat open [" "]
                                                         (rest (make-tag context :p line)))) tags)
        :else                     (recur xs (make-tag context :p line) tags)))))

(sc/defn ^:private ^:always-validate contract-tags :- [Tag]
  [{:keys [lang] :as context} :- (assoc Context :lang Lang)]
  (->> (format "contracts/company-contract-%s.txt" (name lang))
       io/resource
       slurp
       (text->tags context)
       seq))

(sc/defn ^:private ^:always-validate contract-pdf :- InputStream
  [{:keys [company] :as context} :- Context]
  (:pdf-file-stream (laundry-client/html-to-pdf (html (contract-tags context))
                                                header-html
                                                (h/html (footer context))
                                                (str "Contract for " (:name company))
                                                {:top    "25mm"
                                                 :bottom "25mm"
                                                 :left   "9mm"
                                                 :right  "9mm"})))


(sc/defn ^:private ^:always-validate link :- Tag
  ([{:keys [lang url-key text tag]} :- {:lang    Lang
                                        :url-key sc/Keyword
                                        :text    (sc/maybe sc/Str)
                                        :tag     (sc/enum :a :a.gap)}]
   (let [url (i18n/localize lang :company-contract.url url-key)]
     [tag {:href url} (or text url)]))
  ([lang url-key text]
   (link {:lang lang :url-key url-key :text text :tag :a}))
  ([lang url-key] (link lang url-key nil)))

(sc/defn ^:private ^:always-validate ->context :- Context
  [lang    :- Lang
   company :- Company
   contact :- Contact
   account :- Account]
  (let [{:keys [billingType price
                type]} (ss/trimwalk account)
        account        {:type   (i18n/localize lang :register :company type :title)
                        :months (if (util/=as-kw billingType :monthly) 1 12)
                        :price  price}]
    (ss/trimwalk {:lang           lang
                  :company        company
                  :contact        contact
                  :account        account
                  :tos-link       (link lang :tos)
                  :pro-link       (link lang :pro)
                  :prices-link    (link lang :prices)
                  :registry-link  (link lang :registry)
                  :lupapiste-link (link {:lang    lang
                                         :url-key :lupapiste
                                         :text    "www.lupapiste.fi"
                                         :tag     :a.gap})})))

(sc/defn ^:always-validate generate-company-contract :- ByteArrayInputStream
  "Generates company contract PDF. The contract templates (for each supported language) are text
  resources that are parsed and processed into HTML that is passed to muuntaja. Resulting PDF is
  returned as ByteArrayInputStream.

  Called from `lupapalvelu.onnistuu.process/fetch-document`."
  [lang       :- Lang
   company    :- Company
   contact    :- Contact
   account    :- Account]
  (with-open [^InputStream in (contract-pdf (->context lang company contact account))
              out             (ByteArrayOutputStream.)]
    (io/copy in out)
    (ByteArrayInputStream. (.toByteArray out))))

(in-dev
  (def test-company {:name               "Yritys Oy"
                     :accountType        "account15"
                     :billingType        "monthly"
                     :customAccountLimit nil ; blech
                     :y                  "3480128-7"
                     :address1           "Firmatie 10"
                     :zip                "12345"
                     :po                 "Tehdasalue"})
  (def test-account {:type "account15" :price 12 :billingType "monthly"})
  (def test-contact {:firstName "Yrjö"
                     :lastName  "Yhteyshenkilö"})
  (defn contract->file
    "Simple REPL-friendly development convenience function."
    ([lang filename]
     (contract->file lang test-company test-contact test-account filename))
    ([lang company contact account filename]
     (with-open [^ByteArrayInputStream stream (generate-company-contract lang company contact account)]
        (io/copy stream (io/file filename))))))
