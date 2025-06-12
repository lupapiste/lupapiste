(ns lupapalvelu.bim.api
  "API for handling IFC and CSET files for BIM integration.
  The IFC files are linked to a specific application;
  the CSET files are stored in their own bucket and not linked to any single application or organization."
  (:require [lupapalvelu.action :as action :refer [defcommand defquery defraw update-application]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.bim.bim-extracted-data :as bim-extracted-data]
            [lupapalvelu.bim.checking-plan.schema :as bim-cp-schema]
            [lupapalvelu.bim.core :as bim]
            [lupapalvelu.bim.schema :as bim-schema]
            [lupapalvelu.document.document :as doc]
            [lupapalvelu.document.document-api :as doc-api]
            [lupapalvelu.document.model :as doc-model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.job :as job]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.location :as location]
            [lupapalvelu.logging :as log :refer [errorf]]
            [lupapalvelu.organization :as org]
            [lupapalvelu.states :as states]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.storage.gcs :as gcs]
            [lupapalvelu.storage.object-storage :as object-storage]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.token :as token]
            [sade.core :refer [fail ok]]
            [sade.schemas :as ssc]
            [schema.core :as sc]
            [slingshot.slingshot :refer [try+]]))

;; TODO: There are no itests for this API?

(defcommand read-model-data
  {:description      "Extract model data from IFC and incorporate it into the application.
                     Work is done asynchronously, so a job is returned."
   :parameters       [id operation-id bucket file-version-id]
   :input-validators [(partial action/non-blank-parameters [:id :operation-id :bucket :file-version-id])]
   :permissions      linked-files/linked-file-edit-permissions
   :pre-checks       [bim/validate-bim-application
                      (bim/model-phase-pre-check [:file-version-id] :is-version-id? true)]}
  [{:keys [user system]}]
  (ok :job (bim/make-extraction-job {:application-id id
                                     :operation-id   operation-id
                                     :user           (:email user)
                                     :bucket         (gcs/actual-bucket-name bucket)
                                     :object-key     file-version-id}
                                    system)))

(def long-http-timeout 10000)

(defquery read-model-data-job
  {:parameters       [id job-id version]
   :input-validators [(partial action/non-blank-parameters [:id :job-id :version])]
   :permissions      [{:required [:application/read]}]
   :description      "Returns state of model data job."}
  [{:keys [user]}]
  (try+
    (let [job  (job/find-job job-id)
          task (get-in job [:value "task"])]
      (if (or (not= (:user task) (:email user)) ;; User only known if job ready.
              (not= (:application-id task) id))
        (fail :error.unauthorized)
        (ok (bim/job-status job-id version long-http-timeout))))
    (catch [:error :not-found] _
      (fail :error.unauthorized))))

(defn model-check-running? [check-type application-id target-file-path]
  (->> (linked-files/linked-file-running-jobs application-id target-file-path)
       (keep (comp :status (keyword (bim-cp-schema/check-type->sub-task-name check-type)) :value))
       (some #(not (#{"done" "error"} %)))))

(defn- compliance-check-allowed-prechecker []
  (fn [{:keys [organization data]}]
    (when-let [check-type (:check-type data)]
      (when (= check-type "compliance")
        ((org/organization-flag-pre-check :bim-compliance-check-enabled true) {:organization organization})))))

(defcommand check-model
  {:description      "Run validation or compliance checks on model and save results in the model version.
                     Work is done asynchronously, so a job is returned"
   :parameters       [id check-type operation-id file-id bucket target-file-path model-type]
   :input-validators [{:id               ssc/ApplicationId
                       :check-type       (sc/enum "ids" "compliance")
                       :operation-id     ssc/NonBlankStr
                       :file-id          ssc/NonBlankStr
                       :bucket           ssc/NonBlankStr
                       :target-file-path ssc/NonBlankStr
                       :model-type       bim-schema/ModelType}]
   :permissions      linked-files/linked-file-edit-permissions
   :pre-checks       [bim/validate-bim-application
                      (compliance-check-allowed-prechecker)]}
  [{:keys [system]}]
  (if (model-check-running? check-type id target-file-path)
    (fail :model-check-already-running)
    (ok :job (bim/make-checking-job {:application-id id
                                     :operation-id   operation-id
                                     :file-id        file-id
                                     :model-type     model-type
                                     :bucket         (gcs/actual-bucket-name bucket)
                                     :object-key     target-file-path
                                     :check-type     check-type}
                                    system))))

(defcommand check-model-webhook
  {:description      "Webhook for BIM integration(s) to return their model validation or compliance check results.
  Currently only used for Solibri so doesn't contain the results themselves; those must be fetched separately.
  The actual webhook that redirects to this is defined in `lupapalvelu.web`"
   :parameters       [status jobId tokenId]
   :user-roles       #{:anonymous}
   :input-validators [(partial action/non-blank-parameters [:status :jobId :tokenId])]}
  [command]
  (let [{:keys [token-type]} (token/get-usable-token tokenId :consume true)
        {:keys [internal-job-id
                sub-task]}   (bim/external-job-id->internal-job-sub-task jobId)
        context              (if sub-task
                               (select-keys sub-task [:application-id :file-id :object-key :check-type :integration :external-job-id])
                               {:external-job-id jobId})
        publisher            (components/command->publisher command :bim/checking-response)]
    (log/with-logging-context
      {:applicationId   (:application-id context)
       :file-id         (:file-id context)
       :object-key      (:object-key context)
       :check-type      (:check-type context)
       :integration     (:integration context)
       :external-job-id (:external-job-id context)}
      (cond
        (nil? token-type)               (errorf "Token expired or not found for BIM webhook job %s" jobId)
        (not= token-type :bim-checking) (errorf "Incorrect token used for BIM webhook job %s: %s" jobId token-type)
        (nil? internal-job-id)          (errorf "Job not found for BIM integration webhook job %s" jobId)
        (nil? publisher)                (errorf "No publisher available for :bim/checking-response, job %s." jobId)
        :else                           (publisher {:handler            :run-model-check
                                                    :response-to        internal-job-id
                                                    :response-expected? false
                                                    :message-id         internal-job-id
                                                    :data               (assoc context
                                                                               :succeeded? (= status "FINISHED")
                                                                               :status status)})))
    (ok)))

(defcommand generate-xkt-file
  {:description      "Generate an XKT file for the BIM viewer from the IFC file and save results in the model version.
   Work is done asynchronously, so a job is returned.
   If a currently active job is found, return it instead of starting a new one."
   :parameters       [id operation-id ifc-object-key]
   :input-validators [{:id             ssc/ApplicationId
                       :operation-id   ssc/ObjectIdStr
                       :ifc-object-key ssc/NonBlankStr}]
   :permissions      [{:required [:application/read]}]
   :pre-checks       [bim/validate-bim-application]}
  [{:keys [system]}]
  (if (bim/file-size-under-bim-viewer-limit? (bim/ifc-file-size id ifc-object-key))
    (ok :job (or (bim/active-xkt-job-for-ifc-file id ifc-object-key)
                 (bim/make-ifc-to-xkt-job {:application-id id
                                           :operation-id   operation-id
                                           :bucket         (gcs/actual-bucket-name object-storage/application-bucket)
                                           :object-key     ifc-object-key}
                                          system)))
    (fail :ifc-file-too-big :max-size bim/bim-viewer-max-filesize)))

(defquery xkt-file-info
  {:description      "Returns the status for XKT file generated from the given IFC file"
   :parameters       [id ifc-version-id ifc-object-key]
   :input-validators [{:id             ssc/ApplicationId
                       :ifc-version-id ssc/NonBlankStr
                       :ifc-object-key ssc/NonBlankStr}]
   :permissions      [{:required [:application/read]}]
   :pre-checks       [bim/validate-bim-application]}
  [_]
  (if (bim/file-size-under-bim-viewer-limit? (bim/ifc-file-size id ifc-object-key))
    (let [job-id (some-> (bim/active-xkt-job-for-ifc-file id ifc-object-key) :id str)]
      (ok {:status (cond
                     job-id                                        "generating"
                     (bim/xkt-file-for-ifc-file id ifc-version-id) "available"
                     :else                                         "failed")
           :job-id job-id}))
    (fail :ifc-file-too-big :max-size bim/bim-viewer-max-filesize)))

(defraw download-xkt-file
  {:description      "Returns the XKT file for the given BIM IFC file for viewing in the BIM viewer"
   :parameters       [id ifc-version-id]
   :input-validators [{:id             ssc/ApplicationId
                       :ifc-version-id ssc/NonBlankStr}]
   :permissions      [{:required [:application/read]}]
   :pre-checks       [bim/validate-bim-application]}
  [_]
  (let [{:keys [bucket object-key]} (bim/xkt-file-for-ifc-file id ifc-version-id)]
    (if (and bucket object-key)
      (-> (object-storage/download (storage/default-storage) bucket object-key)
          (att/output-attachment true))
      (fail :error.not-found))))

(defn- ifc-app-doc-matches-doc-id
  "Pre-check that verifies the application document found via the BIM linked file
  (through operation-id) is the same document as the one identified by :docId.
  Prevents mismatch between the BIM-linked operation document and the explicitly
  supplied target document."
  [{:keys [application document data]}]
  (when (and document data)
    (when-let [linked-file (linked-files/find-one-linked-file-with-metadata {:target-entity/application-id (:id data)
                                                                             :versions/file-version-id     (:file-version-id data)})]
      (let [app-doc (some-> linked-file :target-entity :operation-id (as-> op-id (domain/get-document-by-operation application op-id)))]
        (cond
          (nil? app-doc)                      (fail :error.document-not-found)
          (not= (:id app-doc) (:id document)) (fail :error.bim-ifc-doc-does-not-match-app-doc))))))

(defn- apply-ifc-data-to-doc!
  "Applies `data-map` to `doc` as a trusted system-level write.
  Skips the UI readonly guard by calling persist-model-updates directly after
  filtering out readonly fields. To allow overwriting readonly fields in the future,
  remove the remove-readonly-model-updates call."
  [command application doc data-map]
  (let [top-level-paths (map vector (keys data-map))
        model-updates   (->> (doc-model/map2updates [] data-map)
                             (doc-persistence/remove-readonly-model-updates doc))
        {:keys [mongo-query mongo-updates]} (doc-persistence/removing-updates-by-path
                                              "documents" (:id doc) top-level-paths)]
    (update-application command mongo-query mongo-updates)
    (doc-persistence/persist-model-updates application "documents" doc model-updates (:created command))))

(defcommand apply-ifc-model-data
  {:description    "Overwrites the application document fields with data previously fetched from the IFC model.
                   Reads from the already-stored :ifc-data snapshot and persists
                   the mapped field values directly into the matching application document.
                   Also updates the rakennuspaikka document with kaavatilanne and hallintaperuste if present.
                   If IfcMapConversion coordinates are present in the snapshot, also updates the
                   operation location on the Tietomalli tab."
   :request-schema {:id              ssc/ApplicationId
                    :file-version-id ssc/NonBlankStr
                    :docId           ssc/NonBlankStr}
   :contexts       [doc/document-context]
   :permissions    doc-api/document-edit-permissions
   :pre-checks     [bim/validate-bim-application
                    (doc/editable-by-state? states/update-doc-states)
                    doc/doc-disabled-validator
                    ifc-app-doc-matches-doc-id]}
  [{:keys [document application data] :as command}]
  (let [file-version-id (:file-version-id data)
        ifc-doc         (bim-extracted-data/fetch-ifc-doc file-version-id)]
    (if (nil? ifc-doc)
      (fail :error.not-found)
      (do
        (apply-ifc-data-to-doc! command application document (:data ifc-doc))
        (when-let [rakennuspaikka-data (not-empty (:rakennuspaikka-data ifc-doc))]
          (when-let [rakennuspaikka-doc (domain/get-document-by-name application "rakennuspaikka")]
            (apply-ifc-data-to-doc! command application rakennuspaikka-doc rakennuspaikka-data)))
        (when-let [{:keys [x y]} (:location ifc-doc)]
          (when-let [operation-id (get-in document [:schema-info :op :id])]
            (location/set-operation-location command {:operation-id operation-id
                                                      :x            x
                                                      :y            y})))
        (ok)))))

(defquery ifc-data-file-version-ids
  {:description    "Returns the set of file-version-ids that have an :ifc-data snapshot
                     for the given application. Used by the frontend to determine which
                     IFC files are ready to have their data applied to the application document."
   :request-schema {:id ssc/ApplicationId}
   :permissions    [{:required [:application/read]}]
   :pre-checks     [bim/validate-bim-application]}
  [{{id :id} :data}]
  (let [file-version-ids (->> (linked-files/find-many-linked-files-with-metadata {:application-id id} :only-fields [:versions])
                              (mapcat :versions)
                              (keep :file-version-id))]
    (ok :file-version-ids (bim-extracted-data/fetch-ifc-data-file-version-ids file-version-ids))))

(defquery fetch-ifc-doc
  {:description      "Returns the IFC-extracted document (docgen format) for a given file version,
                     alongside the matching application document for the same operation.
                     The ifc-doc contains schema-info and data mapped from IFC properties,
                     suitable for rendering with the docgen form component."
   :parameters       [id file-version-id]
   :input-validators [{:id              ssc/ApplicationId
                       :file-version-id ssc/NonBlankStr}]
   :permissions      [{:required [:application/read]}]
   :pre-checks       [bim/validate-bim-application]}
  [{:keys [application]}]
  (if-let [ifc-doc (bim-extracted-data/fetch-ifc-doc file-version-id)]
    (let [linked-file  (linked-files/find-one-linked-file-with-metadata
                         {:target-entity/application-id id
                          :versions/file-version-id     file-version-id})
          operation-id (some-> linked-file :target-entity :operation-id)
          app-doc      (when operation-id
                         (domain/get-document-by-operation application operation-id))]
      (ok :ifc-doc ifc-doc :app-doc app-doc))
    (fail :error.not-found)))
