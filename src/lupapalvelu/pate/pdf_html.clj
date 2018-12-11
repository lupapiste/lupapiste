(ns lupapalvelu.pate.pdf-html
  "HTML facilities for Pate verdicts. Provides a simple schema-based
  mechanism for the layout definition and generation. The resulting
  HTML can be converted into PDF via `lupapalvelu.pdf.html-template`
  functions."
  (:require [clojure.java.io :as io]
            [garden.core :as garden]
            [garden.selectors :as sel]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.columns :as cols]
            [lupapalvelu.pate.date :as date]
            [lupapalvelu.pate.legacy-schemas :as legacy]
            [lupapalvelu.pate.markup :as markup]
            [lupapalvelu.pate.pdf-layouts :as layouts]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.shared-schemas :as shared-schemas]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]
            [lupapalvelu.pdf.html-template-common :as common]
            [rum.core :as rum]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :refer [defschema] :as sc]
            [swiss.arrows :refer :all]))


(def page-number-script
  [:script
   {:dangerouslySetInnerHTML
    {:__html
     (-> common/wkhtmltopdf-page-numbering-script-path
         io/resource
         slurp)}}])

(defn html [body & [script?]]
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
                      [[:* {:font-family "'Carlito', sans-serif"}]
                       [:.permit {:text-transform :uppercase
                                  :font-weight    :bold}]
                       [:.preview {:text-transform :uppercase
                                   :color          :red
                                   :font-weight    :bold
                                   :letter-spacing "0.2em"}]
                       [:div.header {:padding-bottom "1em"}]
                       [:div.footer {:padding-top "1em"}]
                       [:.page-break {:page-break-before :always}]
                       [:.section {:display :table
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
                          [:&.nowrap {:white-space :nowrap}]]
                         (map (fn [n]
                                [(keyword (str ".cell.cell--" n))
                                 {:width (str n "%")}])
                              layouts/cell-widths)
                         [:&.spaced
                          [(sel/+ :.row :.row)
                           [:.cell {:padding-top "0.5em"}]]]]]
                       [:.markup
                        [:p {:margin-top    "0"
                             :margin-bottom "0.25em"}]
                        [:ul {:margin-top    "0"
                              :margin-bottom "0"}]
                        [:ol {:margin-top    "0"
                              :margin-bottom "0"}]
                        ;; wkhtmltopdf does not seem to support text-decoration?
                        [:span.underline {:border-bottom "1px solid black"}]]])}}]]
         [:body body (when script?
                       page-number-script)]])))

(defn organization-name
  ([lang {organization :organization}]
   (org/get-organization-name organization lang))
  ([lang application verdict]
   (or (get-in verdict [:references :organization-name])
       (organization-name lang application))))

(defn verdict-header
  [lang application {:keys [category published legacy?] :as verdict}]
  [:div.header
   [:div.section.header
    (let [category-kw    (util/kw-path (when legacy? :legacy) category)
          legacy-kt-ymp? (contains? #{:legacy.kt :legacy.ymp}
                                    category-kw)
          loc-fn         (fn [& kws]
                           (apply i18n/localize lang (flatten kws)))
          contract?      (vc/contract? verdict)
          proposal?      (vc/proposal? verdict)
          non-migration-contract? (and contract?
                                       (not (vc/has-category? verdict
                                                              :migration-contract)))]
      [:div.row.pad-after
       [:div.cell.cell--40
        (organization-name lang application verdict)
        (when-let [boardname (some-> verdict :references :boardname)]
          [:div boardname])]
       [:div.cell.cell--20.center
        [:div (cond
                (and (not published)
                     (not proposal?))
                [:span.preview (i18n/localize lang :pdf.preview)]

                (and (not contract?)
                     (not legacy-kt-ymp?)
                     (not proposal?))
                (i18n/localize lang (case category-kw
                                      :p :pdf.poikkeamispaatos
                                      :attachmentType.paatoksenteko.paatos))
                proposal?
                (i18n/localize lang :pate-verdict-proposal))]]
       [:div.cell.cell--40.right
        [:div.permit (loc-fn (cond
                               legacy-kt-ymp?          :attachmentType.paatoksenteko.paatos
                               non-migration-contract? :pate.verdict-table.contract
                               :else
                               (case category-kw
                                 :ya        [:pate.verdict-type
                                             (cols/dict-value verdict :verdict-type)]
                                 :legacy.ya [:pate.verdict-type
                                             (schema-util/ya-verdict-type application)]
                                 [:pdf category :permit])))]]])
    [:div.row
     [:div.cell.cell--40
      (layouts/add-unit lang :section (cols/dict-value verdict :verdict-section))]
     [:div.cell.cell--20.center
      [:div (cols/dict-value verdict :verdict-date)]]
     [:div.cell.cell--40.right.page-number
      (i18n/localize lang :pdf.page)
      " " [:span#page-number ""]]]]])

(defn verdict-footer []
  [:div.footer
   [:div.section
    [:div.row.pad-after.pad-before
     [:cell.cell--100 {:dangerouslySetInnerHTML {:__html "&nbsp;"}}]]]])
