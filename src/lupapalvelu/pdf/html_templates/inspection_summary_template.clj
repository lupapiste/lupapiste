(ns lupapalvelu.pdf.html-templates.inspection-summary-template
  (:require [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util]
            [net.cgrand.enlive-html :as enlive]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pdf.html-template-common :as common]
            [lupapalvelu.pdf.html-templates.application-info-template :as app-info]))

(def inspection-summary-template
  [[:h3#inspection-summary-title]
   [:table#inspection-summary
    [:thead ; Is repeated in every page when the table is splitted into multiple pages in pdf
     [:tr#target-header
      [:th#name]
      [:th#attachments]
      [:th#finished]
      [:th#finished-date]
      [:th#finished-by]]]
    [:tbody
     [:tr#target-data
      [:td#name]
      [:td#attachments]
      [:td#finished]
      [:td#finished-date]
      [:td#finished-by]]]]])

(defn- target-attachments [{attachments :attachments :as application} {target-id :id}]
  (->> (filter (comp #{target-id} :id :target) attachments)
       (map (comp vector :filename :latestVersion))))

(defn inspection-summary-transformation [application lang summary-id]
  (let [summary (util/find-by-id summary-id (:inspection-summaries application))]
    (enlive/transformation
     [:#inspection-summary-title] (enlive/content (i18n/localize lang "inspection-summary.tab.title"))
     [:#inspection-summary :#target-header]
     (enlive/transformation
      [:#name]          (enlive/content (i18n/localize lang "inspection-summary.targets.table.target-name"))
      [:#attachments]   (enlive/content (i18n/localize lang "inspection-summary.targets.table.attachments"))
      [:#finished]      (enlive/content (i18n/localize lang "inspection-summary.targets.table.finished"))
      [:#finished-date] (enlive/content (i18n/localize lang "inspection-summary.targets.table.date"))
      [:#finished-by]   (enlive/content (i18n/localize lang "inspection-summary.targets.table.marked-by")))
     [:#inspection-summary :#target-data]
     (enlive/clone-for [{:keys [target-name finished finished-date finished-by] :as target} (:targets summary)]
                       [:#name]          (enlive/content target-name)
                       [:#attachments]   (enlive/content (->> (target-attachments application target) (common/wrap-map :div)))
                       [:#finished]      (enlive/content (when finished (i18n/localize lang "yes")))
                       [:#finished-date] (enlive/content (util/to-local-date finished-date))
                       [:#finished-by]   (enlive/content (->> ((juxt :firstName :lastName) finished-by) (remove nil?) (ss/join " ")))))))

(def inspection-summary-page-template
  (common/html-page nil
                       (common/page-content [app-info/application-info-template
                                                inspection-summary-template])))

(enlive/deftemplate inspection-summary (enlive/html inspection-summary-page-template) [application lang summary-id]
  [:head :style] (enlive/content (common/styles))
  [:body]        (enlive/do-> (app-info/application-info-transformation application lang)
                              (inspection-summary-transformation application lang summary-id)))
