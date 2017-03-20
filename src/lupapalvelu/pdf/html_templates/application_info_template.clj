(ns lupapalvelu.pdf.html-templates.application-info-template
  (:require [sade.property :as property]
            [sade.util :as util]
            [sade.strings :as ss]
            [net.cgrand.enlive-html :as enlive]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pdf.html-template-common :as common]
            [lupapalvelu.document.schemas :as schemas]))

(def application-info-template
  [:div#application-info
   [:h3#application-info-title]
   [:table#application-info-content
    [:tr
     [:td [:div [:b#municipality-title]] [:div#municipality-value]]
     [:td [:div [:b#state-title]] [:div#state-value]]]
    [:tr
     [:td [:div [:b#property-title]] [:div#property-value]]
     [:td [:div [:b#submitted-title]] [:div#submitted-value]]]
    [:tr
     [:td [:div [:b#id-title]] [:div#id-value]]
     [:td [:div [:b#handlers-title]] [:div#handlers-value]]]
    [:tr
     [:td [:div [:b#address-title]] [:div#address-value]]
     [:td [:div [:b#applicant-title]] [:div#applicant-value]]]
    [:tr
     [:td {:colspan "2"} [:div [:b#operations-title]] [:div#operations-value]]]]])

(defn- get-handlers [{handlers :handlers :as application} lang]
  (map #(format "%s: %s %s" (get-in % [:name (keyword lang)]) (:firstName %) (:lastName %)) handlers))

(defn get-operation-info [{documents :documents} lang {op-name :name op-id :id op-desc :description}]
  (let [doc  (util/find-first (comp #{op-id} :id :op :schema-info) documents)
        desc (->> [(get-in doc [:data :tunnus :value]) op-desc]
                  (remove ss/blank?)
                  (ss/join ": "))]
    (->> (concat [(i18n/localize lang "operations" op-name) desc] (schemas/resolve-accordion-field-values doc))
         (remove ss/blank?)
         (ss/join " - "))))

(defn- get-operations [{:keys [primaryOperation secondaryOperations] :as application} lang]
  (->> (cons primaryOperation secondaryOperations)
       (map (partial get-operation-info application lang))))

(defn application-info-transformation [application lang]
  (enlive/transformation
   [:#application-info-title] (enlive/content (i18n/localize lang "application.basicInfo"))
   [:#application-info-content]
   (enlive/transformation
    [:#municipality-title] (enlive/content (i18n/localize lang "application.municipality"))
    [:#municipality-value] (enlive/content (i18n/localize lang "municipality" (:municipality application)))
    [:#state-title]        (enlive/content (i18n/localize lang "application.state"))
    [:#state-value]        (enlive/content (i18n/localize lang (:state application)))
    [:#property-title]     (enlive/content (i18n/localize lang "application.property"))
    [:#property-value]     (enlive/content (if (:propertyId application)
                                             (property/to-human-readable-property-id (:propertyId application))
                                             (i18n/localize lang "application.export.empty")))
    [:#submitted-title]    (enlive/content (i18n/localize lang "application.submissionDate"))
    [:#submitted-value]    (enlive/content (or (util/to-local-date (:submitted application)) "-"))
    [:#id-title]           (enlive/content (i18n/localize lang "application.id"))
    [:#id-value]           (enlive/content (:id application))
    [:#handlers-title]     (enlive/content (i18n/localize lang "application.handlers"))
    [:#handlers-value]     (enlive/content (common/wrap-map :div (get-handlers application lang)))
    [:#address-title]      (enlive/content (i18n/localize lang "application.address"))
    [:#address-value]      (enlive/content (:address application))
    [:#applicant-title]    (enlive/content (i18n/localize lang "applicant"))
    [:#applicant-value]    (enlive/content (common/wrap-map :div (:_applicantIndex application)))
    [:#operations-title]   (enlive/content (i18n/localize lang "operations"))
    [:#operations-value]   (enlive/content (common/wrap-map :div (get-operations application lang))))))

(comment

  (lupapalvelu.mongo/connect!)

  (def application (lupapalvelu.domain/get-application-no-access-checking "LP-753-2017-90006"))
  (def application (update-in application [:inspection-summaries 0 :targets] (partial map-indexed #(update %2 :target-name str "__" %1))))

  (def lang "fi")


  (app-info application lang)


)
