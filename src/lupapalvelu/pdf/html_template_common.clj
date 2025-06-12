(ns lupapalvelu.pdf.html-template-common
  (:require [clojure.java.io :as io]
            [lupapalvelu.i18n :as i18n]
            [net.cgrand.enlive-html :as enlive]
            [sade.strings :as ss]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(def styles-path "private/pdf/style/")

(defn styles
  ([] (let [this-path (util/this-jar lupapalvelu.main)]
        (if (ss/ends-with? this-path ".jar") ; running jar?
          (->> styles-path (util/list-jar this-path) (remove ss/blank?) (map (util/fn->> (str styles-path) io/resource)) styles)
          (->> styles-path io/resource io/file .listFiles styles))))
  ([files] (-> (ss/join " " (map slurp files))
               (ss/replace #"\s+" " "))))

(defn wrap-map [element coll]
  (enlive/html (map (partial vector element) coll)))

(def html-head-template
  [[:meta {:http-equiv "content-type" :content "text/html; charset=UTF-8"}]
   [:style]])

(def content-tag :div.page-content)

(defn- flat-rows
  "Recursively flattens hiccup-style html template rows into a list of valid hiccup html.
  (flat-rows [[[:row1 [:inner-tag]] [[:row2]]] [[[[:row3]]]]]) => ([:row1 [:inner-tag]] [:row2] [:row3])"
  [rows]
  (if (sequential? (first rows))
    (reduce (fn [result row] (concat result (flat-rows row))) [] rows)
    [rows]))

(defn page-content [rows]
  (apply vector content-tag
         (flat-rows rows)))

(defn html-page [head-rows body-rows]
  [:html
   (apply vector :head
          (flat-rows [html-head-template head-rows]))
   (apply vector :body
          (flat-rows body-rows))])

(defn apply-page [enlive-template & args]
  (->> (apply enlive-template args)
       (apply str "<!DOCTYPE html>"))) ;; wkhtmltopdf requires DOCTYPE tag in html files.

(defn map->style
  "Takes a clojure map and turns it into a css-style string
  Example:

  {:display        \"flex\"
   :flex-direction \"column\"}

  Turns into:

  '\"display\": \"flex\"; \"flex-direction\": \"column\";'"
  [opts]
  (->> opts
       (map (fn [[k v]] (str (name k) ":" v ";")))
       (apply str)))

(defn default-footer
  "This is a rewrite of the defaultFooterTemplate, which is hard-coded to english, found in the `pdfkromi` repo.
  The pageNumber and totalPages spans are magical css classes, that gets converted to the actual numbers by the pdf-generator.

  My guess was that the 'title' span would get populated with the optional :title argument of `html->pdf`, but that does not
  seem to be the case. Instead 'title' get populate with the value of the <title> html tag, if present in the html document"

  [lang]
  [:div {:style (map->style {:font-family  "'Helvetica','liberation_sansregular',sans-serif !important"
                             :font-size    "9px !important"
                             :text-align   "right"
                             :width        "100%"
                             :color        "black"
                             :margin-right "20px"})}
   [:span.title]
   (str " " (i18n/localize lang :pdf.page) " ")
   [:span.pageNumber]
   " / "
   [:span.totalPages]])

