(ns lupapalvelu.ya-extension
  "YA extension application related. Preferred namespace alias yax."
  (:require [sade.util :as su]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.application-meta-fields :as meta-fields]))

(defn no-ya-extension-application
  "Prechecker that fails if the application permit is not non-extension YA."
  [{application :application}]
  (when (su/=as-kw (-> application :primaryOperation :name) :ya-jatkoaika)
    (fail :error.operation-not-supported)))

(defn- extension-link-permits [application]
  (filter #(su/=as-kw (:operation %) :ya-jatkoaika)
          (or (:linkPermitData application)
              (:linkPermitData (meta-fields/enrich-with-link-permit-data
                                application)))))

(defn has-extension-link-permits
  "Prechecker that fails if the application does not have any
  extension applications."
  [{application :application}]
  (when (empty? (extension-link-permits application))
    (fail :error.no-extension-applications)))

(defn no-ya-backend
  "Prechecker that fails if the organisation has defined backend
  system for YA applications."
  [{organization :organization}]
  (when (ss/not-blank? (-> @organization :krysp :YA :url))
    (fail :error.has-ya-backend)))

(defn- details [app-id]
  (let [app (domain/get-application-no-access-checking app-id)
        doc (domain/get-document-by-name app  :tyo-aika-for-jatkoaika)]
    {:id app-id
     :startDate (-> doc :data :tyoaika-alkaa-pvm :value)
     :endDate (-> doc :data :tyoaika-paattyy-pvm :value)
     :state (:state app)}))

(defn extensions-details
  "Details for all YA extension link permits."
  [application]
  (map (comp details :id)
       (extension-link-permits application)))
