(ns lupapalvelu.pdf.html-templates.inspection-summary-template
  (:require [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util]
            [net.cgrand.enlive-html :as enlive]
            [lupapalvelu.application :as app]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pdf.html-template-common :as common]
            [lupapalvelu.pdf.html-templates.document-data-converter :as doc-convert]
            [lupapalvelu.pdf.html-templates.application-info-template :as app-info]))

(def inspection-summary-template
  [:div#inspection-summary
   [:h3#inspection-summary-title]
   [:div [:b#inspection-summary-name]]
   [:div#inspection-summary-operation]
   [:table#inspection-summary-targets
    [:thead ; Is repeated in every page when the table is splitted into multiple pages in pdf
     [:tr#target-header
      [:th#name]
      [:th#attachments]
      [:th#finished]
      [:th#inspection-date]
      [:th#finished-date]
      [:th#finished-by]]]
    [:tbody
     [:tr#target-data
      [:td#name]
      [:td#attachments]
      [:td#finished]
      [:td#inspection-date]
      [:td#finished-date]
      [:td#finished-by]]]]])

(defn- target-attachments [{:keys [attachments]} {target-id :id}]
  (->> (filter (comp #{target-id} :id :target) attachments)
       (map (comp :filename :latestVersion))))

(defn inspection-summary-transformation [application lang summary-id]
  (let [summary   (util/find-by-id summary-id (:inspection-summaries application))
        operation (util/find-by-id (get-in summary [:op :id]) (app/get-operations application))]
    (enlive/transformation
     [:#inspection-summary-title]      (enlive/content (i18n/localize lang "inspection-summary.tab.title"))
     [:#inspection-summary-name]       (enlive/content (:name summary))
     [:#inspection-summary-operation]  (enlive/content (app-info/get-operation-info application lang operation))
     [:#inspection-summary-targets :#target-header]
     (enlive/transformation
      [:#name]          (enlive/content (i18n/localize lang "inspection-summary.targets.table.target-name"))
      [:#attachments]   (enlive/content (i18n/localize lang "inspection-summary.targets.table.attachments"))
      [:#finished]      (enlive/content (i18n/localize lang "inspection-summary.targets.table.finished"))
      [:#inspection-date] (enlive/content (i18n/localize lang "inspection-summary.targets.table.inspection-date"))
      [:#finished-date] (enlive/content (i18n/localize lang "inspection-summary.targets.table.finished-date"))
      [:#finished-by]   (enlive/content (i18n/localize lang "inspection-summary.targets.table.marked-by")))
     [:#inspection-summary-targets :#target-data]
     (enlive/clone-for [{:keys [target-name finished inspection-date finished-date finished-by] :as target} (:targets summary)]
                       [:#name]          (enlive/content target-name)
                       [:#attachments]   (enlive/content (->> (target-attachments application target) (common/wrap-map :div)))
                       [:#finished]      (enlive/content (when finished (i18n/localize lang "yes")))
                       [:#inspection-date] (enlive/content (util/to-local-date inspection-date))
                       [:#finished-date] (enlive/content (util/to-local-date finished-date))
                       [:#finished-by]   (enlive/content (->> ((juxt :firstName :lastName) finished-by) (remove nil?) (ss/join " ")))))))

(def project-description-template
  [:div#project-desc])

(defn project-description-tranformation [application lang]
  (enlive/transformation
   [:#project-desc] (some-> (util/find-first (comp #{:hankkeen-kuvaus} :subtype :schema-info) (:documents application))
                            (doc-convert/doc->html lang)
                            enlive/html
                            enlive/content)))

(def inspection-summary-page-template
  (->> [app-info/application-info-template
        project-description-template
        inspection-summary-template]
       (common/page-content)
       (common/html-page nil)))

(enlive/deftemplate inspection-summary (enlive/html inspection-summary-page-template) [application foreman-apps lang summary-id]
  [:head :style] (enlive/content (common/styles))
  [:body]        (enlive/do-> (app-info/application-info-transformation application foreman-apps lang)
                              (project-description-tranformation application lang)
                              (inspection-summary-transformation application lang summary-id)))
