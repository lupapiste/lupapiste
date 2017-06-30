(ns lupapalvelu.reports.applications
  (:require [dk.ative.docjure.spreadsheet :as spreadsheet]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :refer [now]]
            [lupapalvelu.action :refer [defraw]]
            [lupapalvelu.application :as app]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.reports.parties :as parties])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream OutputStream)))

(defn handler-roles-org [org-id]
  (mongo/select-one :organizations {:_id org-id} [:handler-roles]))

(defn open-applications-for-organization [organizationId excluded-operations]
  (let [query     (cond-> {:organization organizationId
                           :state {$in ["submitted" "open" "draft"]}
                           :infoRequest false}
                    excluded-operations (assoc :primaryOperation.name {$nin excluded-operations}))
        roles-org (handler-roles-org organizationId)]
    (map #(app/enrich-application-handlers % roles-org)
         (mongo/select :applications
                       query
                       [:_id :created :opened :submitted :modified
                        :state :handlers
                        :primaryOperation :secondaryOperations]))))

(defn submitted-applications-between [orgId startTs endTs excluded-operations]
  (let [now       (now)
        query     (cond-> {:organization orgId
                                        ;:state {$in ["submitted" "open" "draft"]
                           :submitted {$gte startTs
                                       $lte (if (< now endTs) now endTs)}
                           :infoRequest false}
                    excluded-operations (assoc :primaryOperation.name {$nin excluded-operations}))
        roles-org (handler-roles-org orgId)]
    (map #(app/enrich-application-handlers % roles-org)
         (mongo/select :applications
                       query
                       [:_id :submitted :modified :state :handlers
                        :primaryOperation :secondaryOperations :verdicts]
                       {:submitted 1}))))

(defn parties-between-data [orgId startTs endTs]
  (let [now (now)]
    (mongo/select :applications
                  {:organization orgId
                   :permitType "R"
                   :submitted {$gte startTs
                               $lte (if (< now endTs) now endTs)}
                   :infoRequest false}
                  [:_id :submitted  :address :modified :state
                   :documents.schema-info.name :documents.schema-info.subtype
                   :documents.data
                   :primaryOperation]
                  {:submitted 1})))

(defn- authority [app]
  (->> app
       :handlers
       (util/find-first :general)
       ((juxt :firstName :lastName))
       (ss/join " ")))

(defn- other-handlers [lang app]
  (->> app
       :handlers
       (remove :general)
       (map #(format "%s %s (%s)" (:firstName %) (:lastName %) (get-in % [:name (keyword lang)])))
       (ss/join ", ")))

(defn- localized-state [lang app]
  (i18n/localize lang (get app :state)))

(defn- date-value [key app]
  (util/to-local-date (get app key)))

(defn- verdict-date [app]
  (some-> app :verdicts first :paatokset first :paivamaarat :anto util/to-local-date))

(defn- localized-operation [lang operation]
  (i18n/localize lang "operations" (:name operation)))

(defn- localized-primary-operation [lang app]
  (localized-operation lang (:primaryOperation app)))

(defn- localized-secondary-operations [lang {:keys [secondaryOperations]}]
  (when (seq secondaryOperations)
    (ss/join "\n" (map (partial localized-operation lang) secondaryOperations))))

(defn ^OutputStream xlsx-stream
  ([data sheet-name header-row-content row-fn]
    (xlsx-stream [{:sheet-name sheet-name
                   :header header-row-content
                   :row-fn row-fn
                   :data data}]))
  ([sheets]
   {:pre [(every? (every-pred :sheet-name :header :row-fn) sheets)]}
   (let [wb (apply spreadsheet/create-workbook
                   (apply concat
                          (reduce (fn [wb-sheets conf]
                                    (conj wb-sheets
                                          [(:sheet-name conf)
                                           (concat [(:header conf)]
                                                   (map (:row-fn conf) (:data conf)))]))
                                  []
                                  sheets)))]
     ; Format each sheet
     (doseq [sheet (spreadsheet/sheet-seq wb)
             :let [header-row   (-> sheet spreadsheet/row-seq first)
                   column-count (.getLastCellNum header-row)
                   bold-style   (spreadsheet/create-cell-style! wb {:font {:bold true}})]]
       (spreadsheet/set-row-style! header-row bold-style)
       (doseq [i (range column-count)]
         (.autoSizeColumn sheet i)))

     (with-open [out (ByteArrayOutputStream.)]
       (spreadsheet/save-workbook-into-stream! out wb)
       (ByteArrayInputStream. (.toByteArray out))))))

(defn ^OutputStream open-applications-for-organization-in-excel! [organizationId lang excluded-operations]
  ;; Create a spreadsheet and save it
  (let [data               (open-applications-for-organization organizationId excluded-operations)
        sheet-name         (str (i18n/localize lang "applications.report.open-applications.sheet-name-prefix")
                                " "
                                (util/to-local-date (now)))
        header-row-content (map (partial i18n/localize lang) ["applications.id.longtitle"
                                                              "applications.authority"
                                                              "application.handlers.other"
                                                              "applications.status"
                                                              "applications.opened"
                                                              "applications.submitted"
                                                              "applications.lastModified"
                                                              "operations.primary"
                                                              "application.operations.secondary"])
        row-fn (juxt :id
                     authority
                     (partial other-handlers lang)
                     (partial localized-state lang)
                     (partial date-value :opened)
                     (partial date-value :submitted)
                     (partial date-value :modified)
                     (partial localized-primary-operation lang)
                     (partial localized-secondary-operations lang))]
    (xlsx-stream data sheet-name header-row-content row-fn)))

(defn ^OutputStream applications-between-excel [organizationId startTs endTs lang excluded-operations]
  (let [data               (submitted-applications-between organizationId startTs endTs excluded-operations)
        sheet-name         (str (i18n/localize lang "applications.report.applications-between.sheet-name-prefix")
                                " "
                                (util/to-local-date (now)))
        header-row-content (map (partial i18n/localize lang) ["applications.id.longtitle"
                                                              "applications.authority"
                                                              "application.handlers.other"
                                                              "applications.status"
                                                              "applications.submitted"
                                                              "verdictGiven"
                                                              "operations.primary"
                                                              "application.operations.secondary"])
        row-fn (juxt :id
                     authority
                     (partial other-handlers lang)
                     (partial localized-state lang)
                     (partial date-value :submitted)
                     verdict-date
                     (partial localized-primary-operation lang)
                     (partial localized-secondary-operations lang))]

    (xlsx-stream data sheet-name header-row-content row-fn)))


  (defn ^OutputStream parties-between-excel [organizationId startTs endTs lang]
    (let [[foreman-apps other-apps] ((juxt filter remove) foreman/foreman-app? (parties-between-data organizationId startTs endTs))
          result-map {:private-applicants  []
                      :company-applicant   []
                      :designers           []
                      :foremen             []}
          reducer-fn (fn [res app]
                       (-> res
                           (update :private-applicants concat (parties/private-applicants app lang))
                           (update :company-applicants concat (parties/company-applicants app lang))
                           (update :designers concat (parties/designers app lang))))
          enriched-foremen (parties/enrich-foreman-apps foreman-apps)
          data (-> (reduce reducer-fn result-map other-apps)
                   (assoc :foremen (map #(parties/foremen % lang) enriched-foremen)))
          ;; (-> (.getCreationHelper wb) (.createHyperlink help HyperlinkType/URL) (.setAddress "https://...") )
          ;; cell.setHyperlink(link)
          sheets [{:sheet-name "Henkilöhakijat"
                   :header (parties/applicants-field-localization :private lang)
                   :row-fn parties/private-applicants-row-fn
                   :data (:private-applicants data)}
                  {:sheet-name "Yritysakijat"
                   :header (parties/applicants-field-localization :company lang)
                   :row-fn parties/company-applicants-row-fn
                   :data (:company-applicants data)}
                  {:sheet-name "Suunnittelijat"
                   :header (parties/designer-fields-localized lang)
                   :row-fn parties/designers-row-fn
                   :data (:designers data)}
                  {:sheet-name "Tyonjohtajat"
                   :header (parties/foreman-fields-lozalized lang)
                   :row-fn parties/foremen-row-fn
                   :data (:foremen data)}]
          ]
      (xlsx-stream sheets)))
