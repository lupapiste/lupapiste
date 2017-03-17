(ns lupapalvelu.pdf.html-template-common
  (:require [clojure.data.codec.base64 :as base64]
            [clojure.java.io :as io]
            [net.cgrand.enlive-html :as enlive]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util])
    (:import [java.io InputStream]))

(def styles-path "private/pdf/style/")

(def lupa-img-path "public/lp-static/img/lupapiste-logo.png")

(defn styles
  ([] (-> styles-path io/resource io/file .listFiles styles))
  ([files] (-> (ss/join " " (map slurp files))
               (ss/replace #"\s+" " "))))

(defn image-src [file mime-type]
  (with-open [in (io/input-stream file)]
    (let [byte-arr (byte-array (.length file))]
      (println (.length file))
      (.read in byte-arr)
      (->> (String. (base64/encode byte-arr) "utf-8")
           (str "data:" mime-type ";base64,")))))

(defn wrap-map [element coll]
  (enlive/html (map (partial vector element) coll)))

(def html-head-template
  [[:meta {:http-equiv "content-type" :content "text/html; charset=UTF-8"}]
   [:style]])

(def content-tag :div.page-content)

(def header-tag :div.page-header)

(def header-template [header-tag [:span#lupa-img] [:span#print-date]])

(def footer-tag :div.page-footer)

(def application-footer-template [footer-tag [:span#application-id] " - " [:span#host-address] [:span#page-number]])

(defn common-content-transformation [application lang]
  (enlive/transformation
   [:#lupa-img]       (enlive/content (enlive/html [:img {:src (image-src (io/file (io/resource lupa-img-path)) "img/png")}]))
   [:#print-date]     (enlive/content (util/to-local-date (now)))
   [:#application-id] (enlive/content (:id application))
   [:#host-address]   (enlive/content (env/value :host))))

(defn- flat-rows
  "Recursively flattens hiccup-style html template rows"
  [rows]
  (if (sequential? (first rows))
    (reduce (fn [result row]
              (concat result (flat-rows row)))
            [] rows)
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

(enlive/deftemplate basic-header (enlive/html (html-page nil header-template)) [application lang]
  [:head :style] (enlive/content (styles))
  [header-tag] (common-content-transformation application lang))

(enlive/deftemplate basic-footer (enlive/html (html-page nil application-footer-template)) [application lang]
  [:head :style] (enlive/content (styles))
  [footer-tag]   (common-content-transformation application lang))



(comment
  (apply vector :head (flat-rows [:foo [:bar]]))

  (apply str (basic-header nil "fi"))

  (apply str (basic-footer nil "fi"))

  (flat-rows
   [[[[:foo] [:bar]]
     [:baz [:zoo]] [:buu]]
    [:quu]
    [[:quz]]])

  [

   [

    [

     [:foo]
     [:bar]

     ]
    [:baz [:zoo]]
    [:buu]

    ]

   [:quu]
   [

    [:quz]

    ]

   ])
