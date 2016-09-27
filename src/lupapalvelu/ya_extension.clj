(ns lupapalvelu.ya-extension
  "YA extension application related. Preferred namespace alias yax."
  (:require [sade.util :as su]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.application-meta-fields :as meta-fields]))

(defn- extension-link-permits [application]
  (filter #(su/=as-kw (:operation %) :ya-jatkoaika)
          (or (:appsLinkingToUs application)
              (:appsLinkingToUs (meta-fields/enrich-with-link-permit-data
                                application)))))

(defn has-extension-link-permits
  "Prechecker that fails if the application does not have any
  extension applications."
  [{application :application}]
  (when (empty? (extension-link-permits application))
    (fail :error.no-extension-applications)))

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
