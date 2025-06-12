(ns lupapalvelu.bim.data-update
  "Inbound BIM service response handlers.
  Extraction responses are persisted to the `:ifc-data` MongoDB collection — not directly
  into the application. The user must explicitly invoke `apply-ifc-model-data` to write
  the data to the application document.
  Checking responses are written to the matching version entry in `:linked-file-metadata`."
  (:require [lupapalvelu.bim.bim-extracted-data :as bim-extracted-data]
            [lupapalvelu.bim.protocol :as bim-proto]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.job :as job]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.file-storage :as storage]
            [monger.operators :refer :all]
            [sade.util :as util]))

(defn store-ifc-data-snapshot!
  "Inbound handler for Bimmer extraction responses.
  Parses the response, persists the IFC snapshot, and updates the job sub-task status."
  [instance
   system
   {:keys [response-to] :as message}]
  {:pre [(map? system)]}
  (let [{:keys [application-id
                schema-name
                operation-name
                file-id
                object-key
                linked-metadata-file-version-id
                query]} (-> response-to
                            job/find-job
                            (get-in [:value "ifc-data-snapshot"]))]
    (log/with-logging-context {:applicationId application-id :fileId file-id}
      (try
        (if-let [properties (bim-proto/-extraction-response instance message)]
          (do (bim-extracted-data/save-ifc-data-snapshot! (schemas/get-schema 1 schema-name)
                                                          {:linked-metadata-file-version-id linked-metadata-file-version-id
                                                           :operation-name                  operation-name
                                                           :properties                      properties
                                                           :query                           query})
              (job/update-by-id response-to "ifc-data-snapshot" {:status         :done
                                                                 :application-id application-id
                                                                 :object-key     object-key}))
          (do (log/warnf "IFC snapshot response did not include :properties for application=%s" application-id)
              (job/update-by-id response-to "ifc-data-snapshot" {:status         :error
                                                                 :application-id application-id
                                                                 :object-key     object-key})))
        (catch Exception e
          (log/errorf e "Failed to store IFC snapshot to application=%s" application-id)
          (job/update-by-id response-to "ifc-data-snapshot" {:status         :error
                                                             :application-id application-id
                                                             :object-key     object-key}))))))

(defn find-sub-task-by-external-job-id [job external-job-id]
  (->> job
       :value
       (util/find-first #(-> (second %) :external-job-id (= external-job-id)))))

(defn remove-previous-checking-file [check-type-kw application-id file-id object-key]
  (when-let [previous-file-id (some-> (mongo/select-one :linked-file-metadata
                                                        {:target-entity.file-id file-id
                                                         :versions              {$elemMatch {:target-file-path object-key}}}
                                                        {:versions {$elemMatch {:target-file-path object-key}}})
                                      :versions
                                      first                 ; query projection returns only the relevant version
                                      :bim
                                      check-type-kw
                                      :result-file-id)]
    (storage/delete-app-file-from-storage application-id previous-file-id :gcs)))

(defn update-checking-data
  "Persist check data to version metadata."
  [instance
   system
   {:keys                            [response-to]
    {:keys [external-job-id status]} :data
    :as                              message}]
  {:pre [(map? system)]}
  (log/debugf "Received BIM check result for request=%s" response-to)
  (let [job                                                       (job/find-job response-to)
        [sub-task-name {:keys [application-id check-type
                               operation-id file-id object-key]}] (find-sub-task-by-external-job-id job external-job-id)
        sub-task-prev-state                                       (get-in job [:value sub-task-name])
        check-type-kw                                             (if (= "compliance" (name check-type))
                                                                    :compliance-result
                                                                    :validation-result)]
    (log/with-logging-context
      {:applicationId   application-id
       :file-id         file-id
       :object-key      object-key
       :check-type      check-type
       :external-job-id external-job-id
       :status          status}
      (try
        ;; Remove old checking data file from gcs if one exists
        (remove-previous-checking-file check-type-kw application-id file-id object-key)
        (mongo/update-by-query :linked-file-metadata
                               {:target-entity.file-id file-id
                                :versions              {$elemMatch {:target-file-path object-key}}}
                               {$set {(util/kw-path :versions.$.bim check-type-kw)
                                      (bim-proto/-checking-response instance message)}})
        (job/update-by-id response-to sub-task-name (merge sub-task-prev-state {:status :done}))
        (catch Exception e
          (log/errorf e "Failed to save BIM check data to application=%s, operation=%s" application-id operation-id)
          (job/update-by-id response-to sub-task-name (merge sub-task-prev-state {:status :error})))))))
