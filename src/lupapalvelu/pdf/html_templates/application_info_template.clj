(ns lupapalvelu.pdf.html-templates.application-info-template
  (:require [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pdf.html-template-common :as common]
            [lupapalvelu.pdf.html-templates.document-data-converter :as doc-convert]
            [net.cgrand.enlive-html :as enlive]
            [sade.date :as date]
            [sade.property :as property]
            [sade.strings :as ss]
            [sade.util :as util]))

(def application-info-template
  [:div#application-info
   [:h3#application-info-title]
   [:table#application-info-content
    [:tr
     [:td [:div [:b#id-title]] [:div#id-value]]
     [:td [:div [:b#address-title]] [:div#address-value]]]
    [:tr
     [:td [:div [:b#property-title]] [:div#property-value]]
     [:td [:div [:b#municipality-title]] [:div#municipality-value]]]
    [:tr
     [:td [:div [:b#submitted-title]] [:div#submitted-value]]
     [:td [:div [:b#state-title]] [:div#state-value]]]
    [:tr
     [:td [:div [:b#applicant-title]] [:div#applicant-value]]
     [:td [:div [:b#handlers-title]] [:div#handlers-value]]]
    [:tr
     [:td {:colspan "2"} [:div [:b#foremen-title]] [:div#foremen-value]]]
    [:tr
     [:td {:colspan "2"} [:div [:b#operations-title]] [:div#operations-value]]]]])

(defn- get-handlers [{:keys [handlers]} lang]
  (map #(format "%s %s (%s)" (:firstName %) (:lastName %) (get-in % [:name (keyword lang)])) handlers))

(defn get-operation-info [{documents :documents} lang {op-name :name op-id :id op-desc :description}]
  (let [doc  (util/find-first (comp #{op-id} :id :op :schema-info) documents)
        desc (->> [(get-in doc [:data :tunnus :value]) op-desc]
                  (remove ss/blank?)
                  (ss/join ": "))]
    (when doc
      (->> (concat [(i18n/localize lang "operations" op-name) desc] (schemas/resolve-accordion-field-values doc))
           (remove ss/blank?)
           (ss/join " - ")))))

(defn- get-operations [{:keys [primaryOperation secondaryOperations] :as application} lang]
  (->> (cons primaryOperation secondaryOperations)
       (remove nil?)
       (map (partial get-operation-info application lang))))

(defn- get-foreman-info [foreman-doc lang]
  (format "%s %s (%s)"
          (doc-convert/get-value-in foreman-doc lang [:henkilotiedot :etunimi])
          (doc-convert/get-value-in foreman-doc lang [:henkilotiedot :sukunimi])
          (doc-convert/get-value-in foreman-doc lang [:kuntaRoolikoodi])))

(defn- get-foremen [foreman-apps lang]
  (map (util/fn->> :documents
                   (util/find-first (comp #{"tyonjohtaja-v2"} :name :schema-info))
                   (#(get-foreman-info % lang)))
       foreman-apps))

(defn application-info-transformation [application foreman-apps lang]
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
    [:#submitted-value]    (enlive/content (or (date/finnish-date (:submitted application) :zero-pad) "-"))
    [:#id-title]           (enlive/content (i18n/localize lang "application.id"))
    [:#id-value]           (enlive/content (:id application))
    [:#handlers-title]     (enlive/content (i18n/localize lang "application.handlers"))
    [:#handlers-value]     (enlive/content (common/wrap-map :div (get-handlers application lang)))
    [:#address-title]      (enlive/content (i18n/localize lang "application.address"))
    [:#address-value]      (enlive/content (:address application))
    [:#applicant-title]    (enlive/content (i18n/localize lang "applicant"))
    [:#applicant-value]    (enlive/content (common/wrap-map :div (:_applicantIndex application)))
    [:#foremen-title]      (enlive/content (i18n/localize lang "foreman.allForemen"))
    [:#foremen-value]      (enlive/content (common/wrap-map :div (get-foremen foreman-apps lang)))
    [:#operations-title]   (enlive/content (i18n/localize lang "operations"))
    [:#operations-value]   (enlive/content (common/wrap-map :div (get-operations application lang))))))
