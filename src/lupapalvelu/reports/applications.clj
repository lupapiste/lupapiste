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
   {:pre [(every? (every-pred :sheet-name :header :row-fn :data) sheets)]}
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

(defn applications-between-excel [organizationId startTs endTs lang excluded-operations]
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


  (defn parties-between-excel [organizationId startTs endTs lang]
    (let [apps (parties-between-data organizationId startTs endTs)
          result-map {:private-applicants  []
                      :company-applicant   []
                      :designers           []
                      :foremen             []}
          reducer-fn (fn [res app]
                       (-> res
                           (update :private-applicants concat (parties/private-applicants app lang))
                           (update :company-applicants concat (parties/company-applicants app lang))
                           (update :designers concat (parties/designers app lang))
                           (update :foremen concat (parties/foremen app lang))))
          data (reduce reducer-fn result-map apps)
          _ (clojure.pprint/pprint data)
          row-fn parties/private-applicants-row-fn]
      (xlsx-stream (:private-applicants data) "foo" (map str [:id-link :id :address :primaryOperation :sukunimi :suoramarkkinointilupa :turvakielto :osoite :puhelin :sahkoposti]) row-fn)))
