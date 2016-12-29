(ns lupapalvelu.reports.applications
  (:require [dk.ative.docjure.spreadsheet :as spreadsheet]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.i18n :as i18n]
            [sade.core :refer [now]]
            [lupapalvelu.action :refer [defraw]])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream OutputStream)))

(defn open-applications-for-organization [organizationId excluded-operations]
  (let [query (cond-> {:organization organizationId
                       :state {$in ["submitted" "open" "draft"]}
                       :infoRequest false}
                excluded-operations (assoc :primaryOperation.name {$nin excluded-operations}))]
    (mongo/select :applications
                  query
                  [:_id :created :opened :submitted :modified
                   :state :authority.firstName :authority.lastName
                   :primaryOperation :secondaryOperations]
                  {:authority.lastName 1})))

(defn- authority [app]
  (->> app :authority ((juxt :firstName :lastName)) (ss/join " ")))

(defn- localized-state [lang app]
  (i18n/localize lang (get app :state)))

(defn- date-value [key app]
  (util/to-local-date (get app key)))

(defn- localized-operation [lang operation]
  (i18n/localize lang "operations" (:name operation)))

(defn- localized-primary-operation [lang app]
  (localized-operation lang (:primaryOperation app)))

(defn- localized-secondary-operations [lang {:keys [secondaryOperations]}]
  (when (seq secondaryOperations)
    (ss/join "\n" (map (partial localized-operation lang) secondaryOperations))))

(defn ^OutputStream xlsx-stream
  [data sheet-name header-row-content row-fn]
  (let [wb           (spreadsheet/create-workbook
                       sheet-name
                       (concat [header-row-content] (map row-fn data)))
        sheet        (first (spreadsheet/sheet-seq wb))
        header-row   (-> sheet spreadsheet/row-seq first)
        column-count (count header-row-content)]
    (spreadsheet/set-row-style! header-row (spreadsheet/create-cell-style! wb {:font {:bold true}}))
    ; Expand columns to fit all their contents
    (doseq [i (range column-count)]
      (.autoSizeColumn sheet i))

    (with-open [out (ByteArrayOutputStream.)]
      (spreadsheet/save-workbook-into-stream! out wb)
      (ByteArrayInputStream. (.toByteArray out)))))

(defn ^OutputStream open-applications-for-organization-in-excel! [organizationId lang excluded-operations]
  ;; Create a spreadsheet and save it
  (let [data               (open-applications-for-organization organizationId excluded-operations)
        sheet-name         (str (i18n/localize lang "applications.report.open-applications.sheet-name-prefix")
                                " "
                                (util/to-local-date (now)))
        header-row-content (map (partial i18n/localize lang) ["applications.id.longtitle"
                                                              "applications.authority"
                                                              "applications.status"
                                                              "applications.opened"
                                                              "applications.submitted"
                                                              "applications.lastModified"
                                                              "operations.primary"
                                                              "application.operations.secondary"])
        row-fn (juxt :id
                     authority
                     (partial localized-state lang)
                     (partial date-value :opened)
                     (partial date-value :submitted)
                     (partial date-value :modified)
                     (partial localized-primary-operation lang)
                     (partial localized-secondary-operations lang))]
    (xlsx-stream data sheet-name header-row-content row-fn)))

