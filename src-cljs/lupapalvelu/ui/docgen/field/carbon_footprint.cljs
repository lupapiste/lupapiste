(ns lupapalvelu.ui.docgen.field.carbon-footprint
  (:require [lupapalvelu.low-carbon-assessment.climate-report-utils :as climate-report-utils]
            [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.help-toggle :as help-toggle]
            [lupapalvelu.ui.docgen.feature.climate-reports :as climate-reports]
            [lupapalvelu.ui.docgen.field.docgen-group :as docgen-group]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [schema.core :as sc]))

(def kg-co2-e-m2-year
  [:span.mx-1 "kgCO" [:sub "2"] "e/m" [:sup "2"] "/a"])

(def kg-co2-e-m2
  [:span.mx-1 "kgCO" [:sub "2"] "e/m" [:sup "2"]])

(def kg-co2-e
  [:span.mx-1 "kgCO" [:sub "2"] "e"])

(def m2
  [:span.mx-1 "m" [:sup "2"]])

(defn- carbon-footprint-results [doc-id]
  (let [climate-report-data    (<sub [::climate-reports/climate-report-data doc-id])
        net-heated-area        (<sub [::climate-reports/net-heated-area doc-id])
        carbon-footprint       (climate-report-utils/carbon-footprint climate-report-data)
        carbon-footprint-total (climate-report-utils/total-carbon-footprint climate-report-data net-heated-area)]
    [:div
     [:div.font-bold
      (common/loc "hiilijalanjalki.loppusumma")
      [help-toggle/help-toggle
       {:help-content (common/loc-html-reagent :span.group-help-text.font-normal "hiilijalanjalki.loppusumma.help")}]]
     [:div.flex.flex-row.ml-11
      [:div.flex.flex-col.font-bold
       [:span.mx-1.flex.items-center
        [common/format-number carbon-footprint]]
       [:span.mx-1.flex.items-center
        [common/format-number carbon-footprint-total]]]
      [:div.flex.flex-col
       [:span.mx-1 kg-co2-e-m2-year]
       [:span.mx-1 kg-co2-e]]]]))

(defn- net-heated-area-sum [doc-id]
  (let [net-heated-area (<sub [::climate-reports/net-heated-area doc-id])]
    [:div
     [:div.font-bold
      (common/loc "ilmastoselvitys.lammitetty-nettoala-kokonaissumma")]
     [:div.flex.flex-row.ml-11
      [:span.mx-1.flex.items-center.font-bold
       [common/format-number net-heated-area]]
      [:span.mx-1 m2]]]))

(defn- carbon-value-box [value over-limit?]
  [:span.mx-1.flex.items-center
   (if over-limit?
     [:i.warning--filled.mb-0.mr-3 "!"]
     [:i.lupicon-check.positive--filled.mb-0.mr-3])
   [common/format-number value]])

(defn- carbon-footprint-limits [doc-id]
  (let [climate-report-data          (<sub [::climate-reports/climate-report-data doc-id])
        carbon-footprint             (climate-report-utils/carbon-footprint climate-report-data)
        epbd-value                   (climate-report-utils/epbd-value climate-report-data)
        low-carbon-assessment-limits (<sub [::climate-reports/low-carbon-assessment-limits doc-id])
        {:keys [limit epbd-limit]}   low-carbon-assessment-limits
        over-limit?                  (> carbon-footprint limit)]
    [:div.flex.flex-col.justify-start.mb-2
     [:div {:class ["grow-[2]" "font-bold"]}
      [:span (common/loc "hiilijalanjalki.raja-arvot")]
      [help-toggle/help-toggle
       {:help-content (common/loc-html-reagent :span.group-help-text.font-normal "hiilijalanjalki.raja-arvot.help")}]]
     [:div.flex.flex-row.ml-4.items-center {:class ["grow-[3]"]}
      [:div.flex.flex-col.font-bold
       [carbon-value-box carbon-footprint over-limit?]
       [carbon-value-box epbd-value over-limit?]]
      [:div.flex.flex-col
       [:span.mx-1 kg-co2-e-m2-year]
       [:span.mx-1 kg-co2-e-m2]]
      [:div.flex.flex-col
       [:span.mx-1 "(>" limit ")"]
       [:span.mx-1 "(>" epbd-limit ")"]]
      (when over-limit?
        (let [carbon-footprint-delta (common/format-number (- carbon-footprint limit))
              epbd-delta             (common/format-number (- epbd-value epbd-limit))
              percentage-over-limit  (common/->percentage-over-limit carbon-footprint limit)]
          [:div.flex.flex-col
           [:span.flex.items-center
            [:i.info--filled.mx-2.mb-0 "i"]
            [:span.mx-1.font-bold.text-sm
             [common/loc "hiilijalanjalki.ylittaa-raja-arvon"
              carbon-footprint-delta
              percentage-over-limit]]]
           [:span
            [:i.info--filled.mx-2.mb-0 "i"]
            [:span.mx-1.font-bold.text-sm
             [common/loc "hiilijalanjalki.ylittaa-raja-arvon"
              epbd-delta
              percentage-over-limit]]]]))]]))

(defn- render-carbon-footprint-results [doc-id]
  (let [location-doc?                (<sub [::climate-reports/location-climate-report? doc-id])
        low-carbon-assessment-limits (<sub [::climate-reports/low-carbon-assessment-limits doc-id])]
    (when low-carbon-assessment-limits
      [:div.flex.flex-col.justify-start.mx-2.mb-6.px-8.py-4.bg-ui-light-gray.rounded-sm.leading-loose
       (when location-doc?
         [net-heated-area-sum doc-id])
       [carbon-footprint-results doc-id]
       (when-not location-doc?
         [carbon-footprint-limits doc-id])])))

(sc/defn render
  [{:keys [doc-id] :as args} :- schemas/DocgenFieldArguments]
  [:div
   [docgen-group/render args]
   [render-carbon-footprint-results doc-id]])

