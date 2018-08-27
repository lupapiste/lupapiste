(ns lupapalvelu.pdf.html-template-common
  (:require [clojure.data.codec.base64 :as base64]
            [clojure.java.io :as io]
            [net.cgrand.enlive-html :as enlive]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util]))

(def styles-path "private/pdf/style/")

(def wkhtmltopdf-page-numbering-script-path "private/pdf/js/wkhtmltopdf-page-numbering.js")

(def lupa-img-path "public/lp-static/img/lupapiste-logo.png")

(defn styles
  ([] (let [this-path (util/this-jar lupapalvelu.main)]
        (if (ss/ends-with this-path ".jar") ; running jar?
          (->> styles-path (util/list-jar this-path) (remove ss/blank?) (map (util/fn->> (str styles-path) io/resource)) styles)
          (->> styles-path io/resource io/file .listFiles styles))))
  ([files] (-> (ss/join " " (map slurp files))
               (ss/replace #"\s+" " "))))

(defn wkhtmltopdf-page-numbering-script
  ([] (-> wkhtmltopdf-page-numbering-script-path
          io/resource
          slurp)))

(defn image-src [resource mime-type]
  (with-open [in (io/input-stream resource)]
    (let [byte-arr (byte-array 6144)] ; buffer size must be multiple of 3 to avoid incorrect padding
      (loop [result []]
        (if (pos? (.read in byte-arr))
          (recur (conj result (String. (base64/encode byte-arr) "utf-8")))
          (str "data:" mime-type ";base64," (apply str result)))))))

(defn wrap-map [element coll]
  (enlive/html (map (partial vector element) coll)))

(def html-head-template
  [[:meta {:http-equiv "content-type" :content "text/html; charset=UTF-8"}]
   [:style]])

(def content-tag :div.page-content)

(def header-tag :div.page-header)

(def header-template [header-tag [:span#lupa-img] [:span#print-date]])

(def footer-tag :div.page-footer)

(def footer-template [[footer-tag
                       [:span#host-address]
                       [:span.align-content-right [:span#page-number] "/" [:span#number-of-pages]]]
                      [:script#page-numbering {:type "text/javascript"}]])

(def application-footer-template [[footer-tag
                                   [:span [:span#application-id] " - " [:span#host-address]]
                                   [:span.align-content-right [:span#page-number] "/" [:span#number-of-pages]]]
                                  [:script#page-numbering {:type "text/javascript"}]])

(defn common-content-transformation [application]
  (enlive/transformation
   [:#lupa-img]       (enlive/content (enlive/html [:img {:src (image-src (io/resource lupa-img-path) "img/png")}]))
   [:#print-date]     (enlive/content (util/to-local-date (now)))
   [:#application-id] (enlive/content (:id application))
   [:#host-address]   (enlive/content (env/value :host))
   [:script#page-numbering] (enlive/content (wkhtmltopdf-page-numbering-script))))

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

(enlive/deftemplate basic-header (enlive/html (html-page nil header-template)) []
  [:head :style] (enlive/content (styles))
  [:body]        (common-content-transformation nil))

(enlive/deftemplate basic-footer (enlive/html (html-page nil footer-template)) []
  [:head :style] (enlive/content (styles [(str styles)]))
  [:body]        (common-content-transformation nil))

(enlive/deftemplate basic-application-footer (enlive/html (html-page nil application-footer-template)) [application]
  [:head :style] (enlive/content (styles))
  [:body]        (common-content-transformation application))
