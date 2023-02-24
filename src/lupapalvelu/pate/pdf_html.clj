(ns lupapalvelu.pate.pdf-html
  "HTML facilities for Pate verdicts. Provides a simple schema-based
  mechanism for the layout definition and generation. The resulting
  HTML can be converted into PDF via `lupapalvelu.pdf.html-template`
  functions."
  (:require [garden.core :as garden]
            [garden.selectors :as sel]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.columns :as cols]
            [lupapalvelu.pate.pdf-layouts :as layouts]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.verdict-common :as vc]
            [rum.core :as rum]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all]))


(def css-for-template
  (garden/css
    [[:html {:font-family "'Mulish'"
             :font-size   "9pt"}]
     [:.permit {:text-transform :uppercase
                :font-weight    :bold}]
     [:.preview {:text-transform :uppercase
                 :color          :red
                 :font-weight    :bold
                 :letter-spacing "0.2em"}]
     [:div.header {:width         "100%"
                   :padding-left  "3.25em"
                   :padding-right "3.25em"
                   :line-height   "1.25rem"
                   :font-size     "7pt"}]
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
        [:&.left {:text-align :left}]
        [:&.tight-right {:text-align    :right
                         :padding-right 0}]
        [:&.center {:text-align :center}]
        [:&.bold {:font-weight :bold}]
        [:&.nowrap {:white-space :nowrap}]
        [:&.indent {:padding-left "2em"}]
        [:&.border-top {:border-top "1px solid black"}]]
       (map (fn [n]
              [(keyword (str ".cell.cell--" n))
               {:width (str n "%")}])
            layouts/cell-widths)
       [:&.spaced
        [(sel/+ :.row :.row)
         [:.cell {:padding-top "0.5em"}]]]]]

     [:table {:border-collapse :collapse}
      [:&.pad-before {:padding-top "0.5em"
                      :margin-top  "0.5em"}]
      [:th {:padding-top    "0.5em"
            :vertical-align :bottom}]
      [:td {:vertical-align :top}]
      [:.full-width {:width "100%"}]
      [:.pad-left {:padding-left "1em"}]
      [:.pad-left-small {:padding-left "0.5em"}]
      [:.right {:text-align :right}]
      [:.left {:text-align :left}]
      [:.tight-right {:text-align    :right
                      :padding-right 0}]
      [:.center {:text-align :center}]
      [:.bold {:font-weight :bold}]
      [:.nowrap {:white-space :nowrap}]
      [:.indent {:padding-left "2em"}]
      [:.border-top {:border-top "1px solid black"}]]
     [:.markup
      [:p {:margin-top    "0"
           :margin-bottom "0.25em"}]
      [:ul {:margin-top    "0"
            :margin-bottom "0"}]
      [:ol {:margin-top    "0"
            :margin-bottom "0"}]
      ;; wkhtmltopdf does not seem to support text-decoration?
      [:span.underline {:border-bottom "1px solid black"}]]
     [:div.divider {:margin-top  "1em"
                    :border-top  "1px solid black"
                    :padding-top "1em"}]]))

(defn html [body]
  (str "<!DOCTYPE html>"
       (rum/render-static-markup
         [:html
          [:head
           [:meta {:http-equiv "content-type"
                   :content    "text/html; charset=UTF-8"}]
           [:style
            {:type                    "text/css"
             :dangerouslySetInnerHTML {:__html css-for-template}}]]
          [:body body]])))

(defn organization-name
  ([lang {organization :organization}]
   (org/get-organization-name organization lang))
  ([lang application verdict]
   (or (get-in verdict [:references :organization-name])
       (organization-name lang application))))

(defn verdict-subtitle [lang application {:keys [category legacy? template] :as verdict}]
  (let [category-kw              (util/kw-path (when legacy? :legacy) category)
        legacy-kt-ymp?           (contains? #{:legacy.kt :legacy.ymp}
                                            category-kw)
        loc-fn                   (fn [& kws]
                                   (apply i18n/localize lang (flatten kws)))
        contract?                (vc/contract? verdict)
        non-migration-contract?  (and contract?
                                      (not (vc/has-category? verdict
                                                             :migration-contract)))
        {:keys [subtitle]} template]
    (or subtitle
        (loc-fn (cond
                  legacy-kt-ymp?          :attachmentType.paatoksenteko.paatos
                  non-migration-contract? :pate.verdict-table.contract
                  :else
                  (case category-kw
                    :ya            [:pate.verdict-type
                                    (cols/dict-value verdict :verdict-type)]
                    :legacy.ya     [:pate.verdict-type
                                    (schema-util/ya-verdict-type application)]
                    (:p :legacy.p) [:pdf.p (:permitSubtype application)]
                    [:pdf category :permit]))))))

(defn pdf-title
  [lang application verdict]
  (ss/join-non-blanks " - "
                      [(:id application)
                       (verdict-subtitle lang application verdict)
                       (cols/dict-value verdict :verdict-date)
                       (ss/join-non-blanks ", "
                                           [(organization-name lang application verdict)
                                            (some-> verdict :references :boardname)])]))

(defn verdict-header
  [lang application {:keys [category legacy? template preview?] :as verdict}]
  [:div.header
   [:div.section.header
    (let [category-kw    (util/kw-path (when legacy? :legacy) category)
          legacy-kt-ymp? (contains? #{:legacy.kt :legacy.ymp}
                                    category-kw)
          contract?      (vc/contract? verdict)
          proposal?      (vc/proposal? verdict)
          {:keys [title]} template]
      [:div.row.pad-after
       [:div.cell.cell--40
        (organization-name lang application verdict)
        (when-let [boardname (some-> verdict :references :boardname)]
          [:div boardname])]
       [:div.cell.cell--20.center
        [:div (cond
                preview?
                [:span.preview (i18n/localize lang :pdf.preview)]

                proposal?
                (i18n/localize lang :pate-verdict-proposal)

                title title

                (and (not contract?)
                     (not legacy-kt-ymp?))
                (i18n/localize lang (case category-kw
                                      :p :empty
                                      :attachmentType.paatoksenteko.paatos)))]]
       [:div.cell.cell--40.right
        [:div.permit (verdict-subtitle lang application verdict)]]])
    [:div.row
     [:div.cell.cell--40
      (layouts/add-unit lang :section (cols/dict-value verdict :verdict-section))]
     [:div.cell.cell--20.center
      [:div (cols/dict-value verdict :verdict-date)]]
     [:div.cell.cell--40.right.page-number
      (i18n/localize lang :pdf.page)
      " " [:span.pageNumber]]]]])

(defn verdict-footer []
  [:div.footer])
