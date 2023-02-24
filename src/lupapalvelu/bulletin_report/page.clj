(ns lupapalvelu.bulletin-report.page
  (:require [clojure.java.io :as io]
            [garden.core :as garden]
            [lupapalvelu.pate.markup :as markup]
            [rum.core :as rum]
            [sade.css :as css]
            [sade.strings :as ss]
            [sade.util :as util]
            [selmer.filter-parser :refer [escape-html]]
            [selmer.parser :as parser]))

(def TEMPLATE-PATH "templates/bulletin-report")
(def MARGIN        "4em")

(def style-definitions (garden/css
                         [[:html {:font-family "'Mulish'"
                                  :color       css/black
                                  :font-size   "9pt"}]
                          [:body {:margin-left  MARGIN
                                  :margin-right MARGIN}]
                          [:h1 {:color     css/night-sky
                                :font-size "20px"}]
                          [:h2 {:color     css/night-sky
                                :font-size "18px"}]
                          [:table.visits {:border :none}
                           [:td.time {:width "100%"}]]
                          [:div.bulletin
                           [:table.details
                            [:tr.below-one [:td {:padding-bottom "0.5em"}]]
                            [:tr.below-two [:td {:padding-bottom "1em"}]]
                            [:tr.above-one [:td {:padding-top "0.5em"}]]
                            [:tr.above-two [:td {:padding-top "1em"}]]
                            [:td {:vertical-align :top}]
                            [:td.left {:white-space    :nowrap
                                       :padding-right  "2em"}]
                            [:td.right {:width          "100%"}
                             [:ul {:list-style-type :none
                                   :padding-left    0}]]]
                           [:p {:margin-top    "0"
                                :margin-bottom "0.25em"}]
                           [:ul {:margin-top    "0"
                                 :margin-bottom "0"}]
                           [:ol {:margin-top    "0"
                                 :margin-bottom "0"}]
                           ;; wkhtmltopdf does not seem to support text-decoration?
                           [:span.underline {:border-bottom "1px solid black"}]]]))

(defn render-template
  "Renders `template-id.lang.djhtml` template from `TEMPLATE-PATH`. Fallbacks to Finnish, if
  the template is not found."
  [template-id context]
  (letfn [(template [lang]
            (let [filename (name (util/kw-path template-id lang :djhtml))]
              (when (io/resource (str TEMPLATE-PATH "/" filename))
                filename)))]
    (some-> (or (template (:lang context :fi)) (template :fi))
            (parser/render-file context
                                {:custom-resource-path TEMPLATE-PATH}))))

(defn html
  "Full HTML page. Includes `style-definitions` and page numbering support."
  [body-as-string]
  (str "<!DOCTYPE html>"
       (rum/render-static-markup
         [:html
          [:head
           [:meta {:http-equiv "content-type"
                   :content    "text/html; charset=UTF-8"}]
           [:style
            {:type "text/css"
             :dangerouslySetInnerHTML
             {:__html style-definitions}}]]
          [:body
           [:div {:dangerouslySetInnerHTML
                  {:__html body-as-string}}]]])))

(defn string->html
  "Transforms `target` string into HTML. The result is Selmer safe. If `markup?` is true,
  then the target parsed as markup. Returns nil for blank `target`."
  ([target markup?]
   (when-not (ss/blank? target)
     (if markup?
       (rum/render-static-markup (markup/markup->tags target))
       (escape-html target))))
  ([target]
   (string->html target false)))
