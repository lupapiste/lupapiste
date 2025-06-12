(ns lupapalvelu.integrations-api
  "API for commands/functions working with integrations (ie. KuntaGML, Asianhallinta)."
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defcommand defquery defraw update-application] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.autologin :as autologin]
            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.backing-system.asianhallinta.core :as ah]
            [lupapalvelu.backing-system.core :as bs]
            [lupapalvelu.backing-system.dmcity.config :as dm-cfg]
            [lupapalvelu.backing-system.dmcity.core :as dmcity]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.document.building :as doc-building]
            [lupapalvelu.document.document :as doc]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.integrations.document-export :as doc-export]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.logging :refer [error errorf]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notices.util :as notices-util]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.verdict :refer [pate-enabled pate-kuntagml-integration-configured]]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.planning-permit :as planning-permit]
            [lupapalvelu.rest.config :as config]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.ryhti.api.validation :as ryhti-validation]
            [lupapalvelu.sftp.core :as sftp]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [lupapalvelu.tiedonohjaus :as tos]
            [lupapalvelu.user :as user]
            [lupapalvelu.ya-extension :as yax]
            [monger.operators :refer [$in $push $set $unset]]
            [noir.response :as resp]
            [sade.core :refer :all]
            [sade.http :as http]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as validators]
            [schema.core :as sc]
            [swiss.arrows :refer [-<>>]])
  (:import [java.io InputStream]))

(set! *warn-on-reflection* true)

(def non-blocking-transfers #{"verdict-attachment-print-order"})

(defn- has-asianhallinta-operation [{{:keys [primaryOperation]} :application}]
  (when-not (operations/get-operation-metadata (:name primaryOperation) :asianhallinta)
    (fail :error.integration.asianhallinta-disabled)))

(defn- asianhallinta-enabled [{:keys [organization application]}]
  (when-not (-> (org/resolve-organization-scope (:municipality application) (:permitType application) @organization)
                ah/asianhallinta-enabled?)
    (fail :error.integration.asianhallinta-disabled)))

(defn- asianhallinta-unavailable-for-application [command]
  (when-not ((some-fn has-asianhallinta-operation asianhallinta-enabled) command)
    (fail :error.integration.asianhallinta-available)))

(defn- temporary-approve-prechecks
    "Temporary fix: case management and regular approve mechanism can
  coexist for environmental applications."
  [command]
  (when-let [cm-fail (asianhallinta-unavailable-for-application command)]
    (when-not (-> command :application :permitType keyword #{:YI :YL :YM :VVVL :MAL})
      cm-fail)))

(defn- blocking-transfers? [command]
  (some->> command
           :application
           :transfers
           (remove (comp non-blocking-transfers :type))
           seq))

(defn- blocking-transfers-not-done
  [command]
  (when (blocking-transfers? command)
    (fail :error.already-sent)))

;;
;; Application approval
;;

(defn get-transfer-item [type {:keys [created user]} & [data-map]]
  (let [transfer {:type type
                  :user (select-keys user [:id :role :firstName :lastName])
                  :timestamp created}]
    (if-not (and (map? data-map) (seq (:data data-map)))
      transfer
      (assoc transfer (:data-key data-map) (:data data-map)))))

(defn- ensure-general-handler-is-set [handlers user organization]
  (let [general-id (org/general-handler-id-for-organization organization)]
    (if (or (nil? general-id) (util/find-by-key :roleId general-id handlers))
      handlers
      (conj handlers (user/create-handler nil general-id user)))))

(defn- ensure-bulletin-op-description-is-set-if-needed
  [{{permit-type           :permitType municipality :municipality
     primaryOperation      :primaryOperation
     bulletinOpDescription :bulletinOpDescription
     :as                   application} :application
    organization                        :organization}]
  (when-not (or (nil? application)
                (dm-cfg/enabled-for-app? organization application)
                (contains? #{"suunnittelijan-nimeaminen" "kiinteistonmuodostus"}
                           (:name primaryOperation))
                (foreman-util/foreman-app? application)
                (= permit-type "YA")
                (ss/not-blank? bulletinOpDescription))
    (let [{:keys [descriptions-from-backend-system
                  enabled]} (org/bulletin-settings-for-scope (force organization)
                                                             permit-type municipality)]
      (when (and enabled
                 (not descriptions-from-backend-system))
        (fail :error.invalid-value)))))

(defquery kuntagml-226-required
  {:description "Pseudo query that is available only if the KuntaGML version 2.2.6 is
  required by the application and the requirement is not met by the organization."
   :permissions [{:required [:application/approve]}]
   :pre-checks  [(action/not-pre-check app-utils/check-kuntagml-226-requirement)]
   :states      #{:submitted :complementNeeded :verdictGiven :foremanVerdictGiven
                  :constructionStarted :ready :acknowledged}}
  [_]
  (ok))

(defn- application-command-fail [^Exception e lang command-name]
  (error e (str "Exception in " command-name))
  (fail :error.integration.create-message
        :details (or (when-let [ed (ex-data e)]
                       (or (:details ed)
                           (and (:sade.core/type ed)
                                (some->> ed :text (i18n/localize lang)))))
                     (.getMessage e))))

(defcommand approve-application
  {:parameters       [id lang]
   :pre-checks       [temporary-approve-prechecks
                      notices-util/is-not-notice
                      ensure-bulletin-op-description-is-set-if-needed
                      app-utils/check-kuntagml-226-requirement]
   :input-validators [(partial action/non-blank-parameters [:id :lang])]
   :permissions      [{:required [:application/approve]}]
   :notified         true
   :on-success       [(action/notify :application-state-change)]
   :states           #{:submitted :complementNeeded}}
  [{:keys [application created user organization] :as command}]
  (try
    (let [application (planning-permit/clear-planning-permit-fields! command)
          next-state (if (yax/ya-extension-app? application)
                       (sm/verdict-given-state application)
                       (sm/next-state application))
          _           (assert (sm/valid-state? application next-state))

          handlers    (ensure-general-handler-is-set (:handlers application) user @organization)
          application (-> application
                          (assoc :handlers handlers)
                          (mapping-to-krysp/post-process-app-for-krysp organization))]
      (when (attachment/comments-saved-as-attachment? application next-state)
        (attachment/save-comments-as-attachment command {:state next-state}))
     ;; Description is required to be an attachment for encumbrance permits in KRYSP
      (attachment/save-description-as-attachment command)
      (or (app/validate-link-permits application)             ; If validation failure is non-nil, just return it.
          (ryhti-validation/validate-on-approve command)
          (let [submitted-application (mongo/by-id :submitted-applications id)
                [integration-available sent-file-ids]
                (bs/approve-application! (assoc command :application application) submitted-application lang)
                all-attachments       (:attachments (domain/get-application-no-access-checking id [:attachments]))
                attachments-updates   (or (attachment/create-sent-timestamp-update-statements all-attachments sent-file-ids
                                                                                              created)
                                          {})
                transfer              (get-transfer-item :exported-to-backing-system {:created created :user user})]
            (update-application (assoc command :application application)
                                {:state {$in ["submitted" "complementNeeded"]}}
                                (util/deep-merge
                                 {$push {:transfers transfer}
                                  $set  (util/deep-merge (when handlers {:handlers handlers})
                                                         attachments-updates
                                                         (app/mark-indicators-seen-updates command))}
                                 (app-state/state-transition-update next-state created application user)))
            (ok :integrationAvailable integration-available))))
    (catch Exception e
      (application-command-fail e lang command))))

(defcommand approve-application-after-verdict
  {:parameters       [id lang]
   :permissions      [{:required [:application/approve]}]
   :pre-checks       [temporary-approve-prechecks
                      notices-util/is-not-notice
                      pate-enabled
                      blocking-transfers-not-done
                      app-utils/check-kuntagml-226-requirement]
   :input-validators [(partial action/non-blank-parameters [:id :lang])]
   :states           #{:verdictGiven :foremanVerdictGiven :constructionStarted :ready :acknowledged}}
  [{:keys [application created user organization] :as command}]
  (try
    (let [application (-> application
                          (mapping-to-krysp/post-process-app-for-krysp organization))
          state       (:state application)]
      (when (attachment/comments-saved-as-attachment? application state)
        (attachment/save-comments-as-attachment command {:state state}))
      (attachment/save-description-as-attachment command)
      (or (app/validate-link-permits application)  ;; If validation failure is non-nil, just return it.
          (let [submitted-application (mongo/by-id :submitted-applications id)
                approve-application   (bs/approve-application! (assoc command :application application)
                                                               submitted-application lang)
                [integration-available
                 sent-file-ids]       approve-application
                all-attachments       (:attachments (domain/get-application-no-access-checking id [:attachments]))
                attachments-updates   (attachment/create-sent-timestamp-update-statements all-attachments
                                                                                          sent-file-ids
                                                                                          created)
                attachments-updates   (or attachments-updates {})
                transfer              (get-transfer-item :exported-to-backing-system {:created created :user user})]
            (update-application (assoc command :application application)
                                (util/deep-merge
                                  {$push {:transfers transfer}
                                   $set  (util/deep-merge attachments-updates
                                                          (app/mark-indicators-seen-updates command))}))
            (ok :integrationAvailable integration-available))))
    (catch Exception e
      (application-command-fail e lang command))))

(defn- has-exported-to-backing-system-transfer
  [{application :application}]
  (when-not (some (comp #{:exported-to-backing-system} keyword :type) (:transfers application))
    (fail :error.cannot-transfer-application-not-exported-yet)))

(defcommand transfer-application
  {:parameters       [id lang]
   :pre-checks       [dmcity/check-dmcity-enabled
                      permit/is-ymp-permit-type
                      has-exported-to-backing-system-transfer
                      temporary-approve-prechecks
                      notices-util/is-not-notice
                      ensure-bulletin-op-description-is-set-if-needed
                      app-utils/check-kuntagml-226-requirement]
   :input-validators [(partial action/non-blank-parameters [:id :lang])]
   :permissions      [{:required [:application/approve]}]
   :notified         true
   :states           states/post-sent-states}
  [{:keys [application created user organization] :as command}]
  (try
    (let [application (planning-permit/clear-planning-permit-fields! command)
          handlers    (ensure-general-handler-is-set (:handlers application) user @organization)
          application (-> application
                          (assoc :handlers handlers)
                          (mapping-to-krysp/post-process-app-for-krysp organization))]
      (or (app/validate-link-permits application)             ; If validation failure is non-nil, just return it.
          (let [submitted-application                 (mongo/by-id :submitted-applications id)
                [integration-available sent-file-ids] (bs/approve-application! (assoc command :application application) submitted-application lang)]
            (ok :integrationAvailable integration-available))))
    (catch Exception e
      (application-command-fail e lang command))))

(defn- application-already-exported [type]
  (fn [{application :application}]
    (when-not (= "aiemmalla-luvalla-hakeminen" (get-in application [:primaryOperation :name]))
      (let [export-ops #{:exported-to-backing-system :exported-to-asianhallinta}
            filtered-transfers (filter (comp export-ops keyword :type) (:transfers application))]
        (when-not (= (keyword (:type (last filtered-transfers))) type)
          (fail :error.application-not-exported))))))

(defcommand move-attachments-to-backing-system
  {:request-schema      {:id                              sssc/ApplicationId
                         :lang                            sssc/NonBlankStr
                         (sc/optional-key :attachmentIds) [sc/Str]
                         (sc/optional-key :documentIds)   [sc/Str]}
   :user-roles       #{:authority}
   :pre-checks       [(fn [{:keys [application] :as command}]
                        (if application
                          ;; Must either have SFTP KRYSP support or ALLU support
                          (when-not (allu/allu-application? (:organization application) (permit/permit-type application))
                            (or ((permit/validate-permit-type-is permit/R) command)
                                (dmcity/check-dmcity-disabled command)))
                          (fail :error.invalid-application-parameter)))
                      (application-already-exported :exported-to-backing-system)
                      doc-export/has-sendable-attachments]
   :states           (conj states/post-verdict-states :sent)
   :description      "Sends selected attachments and/or documents to backing system via SFTP.
                      Generated documents are included as liitetieto entries in the same KuntaGML
                      message as regular attachments."}
  [{:keys [created application user organization system data] :as command}]
  (let [{:keys [lang]}     data
        attachment-ids     (seq (:attachmentIds data))
        document-ids       (seq (:documentIds data))
        document-id-set    (set document-ids)
        ;; Filter out all archiveOnly attachments, since they are not relevant for the backing systems
        all-attachments    (remove :archiveOnly (:attachments application))
        attachment-id-set  (set attachment-ids)
        selected-attachments (filterv (comp attachment-id-set :id) all-attachments)
        ;; Find linked files (BIM models) - IDs not found in attachments are assumed to be linked file IDs
        regular-att-ids    (set (mapv :id selected-attachments))
        linked-file-ids    (set/difference attachment-id-set regular-att-ids)
        find-linked-file   (fn [file-id]
                             (linked-files/find-one-linked-file-with-metadata
                               {:file-id file-id :application-id (:id application)}))
        linked-file-atts   (into [] (comp (keep find-linked-file)
                                          (map (fn [linked-file]
                                                 (let [pseudo-att (doc-export/linked-file->pseudo-attachment linked-file)]
                                                   (if (:applicationState pseudo-att)
                                                     ;; BIM model file: model-phase was mapped to applicationState
                                                     pseudo-att
                                                     ;; Child file (no model-phase): look up parent's model-phase
                                                     (if-let [parent-phase
                                                              (some-> (get-in linked-file [:target-entity :parent-file-id])
                                                                      find-linked-file
                                                                      (get-in [:metadata :model-phase]))]
                                                       (assoc pseudo-att :applicationState
                                                              (doc-export/model-phase->application-state parent-phase))
                                                       pseudo-att))))))
                                   linked-file-ids)
        all-to-send        (into selected-attachments linked-file-atts)
        ;; Build generated document descriptors (canonical + content-fn)
        generated-docs     (when document-ids
                             (doc-export/build-generated-documents command document-id-set lang))
        ;; Send attachments + documents in a single KuntaGML message
        export-result      (if (seq generated-docs)
                             ;; Combined path: attachments + documents in one KuntaGML
                             (mapping-to-krysp/save-attachments-and-documents-as-kuntagml
                               system user @organization application all-to-send lang generated-docs)
                             ;; Attachment-only path (backward compatible)
                             (when (seq all-to-send)
                               {:sent-file-ids (bs/send-attachments! command all-to-send lang)}))
        sent-file-ids      (:sent-file-ids export-result)
        sent-file-id-set   (set sent-file-ids)
        sent-doc-ids       (seq (:sent-document-ids export-result))
        failed-doc-ids     (seq (:failed-document-ids export-result))
        ;; Update sent timestamps for regular attachments
        data-argument      (when (seq all-to-send)
                             (attachment/create-sent-timestamp-update-statements
                               all-attachments sent-file-ids created))
        ;; Transfer record with attachment IDs and document info
        transfer-data      {:data-key :attachments
                            :data     (concat (map :id selected-attachments)
                                              (map :id linked-file-atts))}
        transfer           (cond-> (get-transfer-item :exported-to-backing-system command transfer-data)
                             (seq sent-doc-ids)
                             (assoc :documents (mapv (fn [doc-id]
                                                      {:id      doc-id
                                                       :version (inc (or (get-in application
                                                                                 [(if (ss/ends-with? doc-id "-application")
                                                                                    :metadata :processMetadata)
                                                                                  :export-version])
                                                                         0))})
                                                    sent-doc-ids)))
        ;; MongoDB updates for document export state
        doc-updates        (when (seq sent-doc-ids)
                             (doc-export/build-document-export-updates (:id application)
                                                           sent-doc-ids
                                                           created))]
    ;; Mark sent linked files as archived
    (doseq [linked-file (->> linked-file-atts
                              (filter #(sent-file-id-set (get-in % [:latestVersion :fileId])))
                              (map :linked-file))]
      (linked-files/update-metadata-field (:target-entity linked-file) "tila" "arkistoitu"))
    (when (or (seq all-to-send) (seq sent-doc-ids))
      (update-application
        command
        (util/deep-merge
          {$push {:transfers transfer}}
          (when (seq data-argument)
            {$set data-argument})
          doc-updates)))
    (cond-> (ok)
      (seq sent-doc-ids)   (assoc :documents-sent sent-doc-ids)
      (seq failed-doc-ids) (assoc :documents-failed failed-doc-ids))))

(defquery sftp-export-enabled
  {:description "Pseudo query. Checks if SFTP export mode is enabled for the application's organization.
                 Used by archival-summary to show export button instead of archive button."
   :parameters  [id]
   :user-roles  #{:authority}
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks  [(fn [{:keys [organization]}]
                   (when (and organization (not (:replace-archive-with-sftp (force organization))))
                     (fail :error.unauthorized)))]}
  [_]
  (ok))

(defcommand parties-as-krysp
  {:description "Sends new designers to backing system after verdict"
   :parameters [id lang]
   :input-validators [(partial action/non-blank-parameters [:id :lang])]
   :user-roles #{:authority}
   :pre-checks [(partial permit/valid-permit-types {:R []})
                (application-already-exported :exported-to-backing-system)
                dmcity/check-dmcity-disabled]
   :states     states/post-verdict-states}
  [command]
  (let [sent-document-ids (or (mapping-to-krysp/save-parties-as-krysp command lang) [])
        transfer-item     (get-transfer-item :parties-to-backing-system command {:data-key :party-documents
                                                                                 :data sent-document-ids})]
    (update-application command {$push {:transfers transfer-item}})
    (ok :sentDocuments sent-document-ids)))

;;
;; krysp enrichment
;;


(defn add-value-metadata [m meta-data]
  (reduce (fn [r [k v]] (assoc r k (if (map? v) (add-value-metadata v meta-data) (assoc meta-data :value v)))) {} m))

(defn- merge-details-state-guard
  "Pre-check for `merge-details-from-krysp`. The command is allowed in
  the post-verdict states only if the user is authority AND Pate is
  enabled."
  [{:keys [application] :as cmd}]
  (when (some->> application :state keyword states/post-verdict-states)
    (or (auth/application-authority-pre-check cmd)
        (pate-enabled cmd))))

(defcommand merge-details-from-krysp
  {:description      "Update building information from KuntaGML"
   :parameters       [id documentId buildingId overwrite]
   :input-validators [(partial action/non-blank-parameters [:id :documentId])
                      (partial action/boolean-parameters [:overwrite])]
   :user-roles       #{:applicant :authority}
   :states           (states/all-application-states-but (conj states/terminal-states :sent))
   :pre-checks       [app/validate-authority-in-drafts
                      merge-details-state-guard]}
  [{created :created {:keys [propertyId] :as application} :application :as command}]
  (doc-building/update-document-building! command documentId buildingId overwrite))

;;
;; Building info
;;

(defquery get-building-info-from-wfs
  {:parameters [id]
   :user-roles #{:applicant :authority}
   :org-authz-roles roles/all-org-authz-roles
   :user-authz-roles roles/all-authz-roles
   :states     states/all-application-states
   :pre-checks [app/validate-authority-in-drafts]}
  [{{:keys [propertyId] :as application} :application}]
  (if-let [{url :url credentials :credentials} (org/get-building-wfs application)]
    (ok :data (building-reader/building-info-list url credentials propertyId))
    (ok)))


(defn- building-extinction-db-updates [{:keys [application created]} operation-id extinct-ts]
  (let [operation-type (cond
                         (= operation-id (-> application :primaryOperation :id))
                         :primary-op
                         (some #(= operation-id (:id %)) (:secondaryOperations application))
                         :secondary-operation
                         :else (fail! :error.invalid-request))
        set-operator (if (some? extinct-ts) $set $unset)]
    (util/deep-merge
      {$set {:modified created}}
      {set-operator (if (= operation-type :primary-op)
                      {:primaryOperation.extinct (or extinct-ts true)}   ;; true for the $unset case
                      (mongo/generate-array-updates
                        :secondaryOperations
                        (:secondaryOperations application)
                        #(= operation-id (:id %))
                        :extinct (or extinct-ts true)))})))  ;; true for the $unset case

(def maybe-nat-checker (sc/checker (sc/maybe sssc/Nat)))

(def- validate-extinct-value (fn [{{:keys [extinct]} :data}]
                               (when (maybe-nat-checker extinct)
                                 (fail :error.invalid-request))))

(defn- validate-buildings-extinct-enabled [{app-org :organization app :application}]
  (when-not (and (:organization app)
                 (:buildings-extinct-enabled @app-org))
    unauthorized))

(defcommand set-building-extinct
  {:description "Sends a KuntaGML message with extinction info of a building to backing system.
  Extinction can be cancelled with an nil-valued 'extinct' parameter."
   :user-roles #{:authority}
   :parameters [id lang operationId extinct]
   :input-validators [(partial action/non-blank-parameters [:id :lang :operationId])
                      validate-extinct-value]
   :pre-checks [(partial permit/valid-permit-types {:R []}) ;; R applications with buildings do not have operation subtypes
                validate-buildings-extinct-enabled]
   :states states/post-verdict-states}
  [{:keys [application] :as command}]
  (let [updates (building-extinction-db-updates command operationId extinct)]
    (action/update-application command updates))
  (let [updated-app (domain/get-application-no-access-checking (:id application))
        updated-command (assoc command :application updated-app)]
    (ok :data (mapping-to-krysp/save-building-extinction-as-krysp updated-command lang operationId))))

;;
;; Asianhallinta
;;

(defn- update-kuntalupatunnus [application]
  (if-let [kuntalupatunnus (some-> application
                                   app/get-link-permit-apps
                                   first
                                   vif/published-kuntalupatunnus)]
    (update-in application
               [:linkPermitData]
               conj {:id kuntalupatunnus
                     :type "kuntalupatunnus"})
    application))

(defcommand application-to-asianhallinta
  {:parameters [id lang]
   :input-validators [(partial action/non-blank-parameters [:id :lang])]
   :user-roles #{:authority}
   :notified   true
   :on-success (action/notify :application-state-change)
   :pre-checks [has-asianhallinta-operation
                asianhallinta-enabled]
   :states     #{:submitted :complementNeeded}}
  [{orig-app :application created :created user :user org :organization :as command}]
  (let [application (-> (meta-fields/enrich-with-link-permit-data orig-app)
                        update-kuntalupatunnus)
        submitted-application (mongo/by-id :submitted-applications id)
        all-attachments (:attachments (domain/get-application-no-access-checking id [:attachments]))
        app-updates {:modified created,
                     :handlers (ensure-general-handler-is-set (:handlers application) user @org)}
        indicator-updates (app/mark-indicators-seen-updates command)
        file-ids (ah/save-as-asianhallinta application lang submitted-application @org) ; Writes to disk
        attachments-updates (or (attachment/create-sent-timestamp-update-statements all-attachments file-ids created) {})
        transfer (get-transfer-item :exported-to-asianhallinta command)]
    (update-application command
                        (util/deep-merge
                          (app-state/state-transition-update (sm/next-state application) created orig-app user)
                          {$push {:transfers transfer}
                           $set (util/deep-merge app-updates attachments-updates indicator-updates)}))
    (ok)))

(defcommand attachments-to-asianhallinta
  {:description      "Sends such selected attachments to asianhallinta that are not yet sent."
   :parameters       [id lang attachmentIds]
   :input-validators [(partial action/non-blank-parameters [:id :lang])
                      (partial action/vector-parameter-of :attachmentIds string?)]
   :user-roles       #{:authority}
   :pre-checks       [has-asianhallinta-operation
                      asianhallinta-enabled
                      (application-already-exported :exported-to-asianhallinta)
                      doc-export/has-sendable-attachments]
   :states           (conj states/post-verdict-states :sent)}
  [{:keys [application created] :as command}]
  (let [all-attachments (:attachments application)]
    (when-let [selected-attachments (some-> (set attachmentIds)
                                            (comp :id)
                                            (filter all-attachments)
                                            seq)]
      (let [application   (meta-fields/enrich-with-link-permit-data application)
            application   (update-kuntalupatunnus application)
            sent-file-ids (ah/save-as-asianhallinta-asian-taydennys application
                                                                    selected-attachments
                                                                    lang)
            data-argument (attachment/create-sent-timestamp-update-statements all-attachments
                                                                              sent-file-ids
                                                                              created)
            transfer-data {:data-key :attachments
                           :data     (map :id selected-attachments)}
            transfer      (get-transfer-item :exported-to-asianhallinta command transfer-data)]
        (update-application command {$push {:transfers transfer}
                                     $set  data-argument})))
    (ok)))

(defquery external-api-enabled
  {:description "Dummy query to check if external API use is configured. Organization from application or user orgs."
   :parameters [id]
   :user-roles #{:authority}
   :states     states/all-states
   :pre-checks [(fn [{{ip :client-ip} :web user :user {:keys [organization]} :application}]
                  (if organization
                    (when-not (autologin/allowed-ip? ip organization)
                      (fail :error.ip-not-allowed))
                    (when-not (some
                                (partial autologin/allowed-ip? ip)
                                (user/organization-ids user))
                      (fail :error.ip-not-allowed))))]})


(def integration-messages-states #{:sent :complementNeeded :verdictGiven :foremanVerdictGiven :ready})

(defquery integration-messages
  {:parameters      [id]
   :user-roles      #{:authority}
   :org-authz-roles #{:approver}
   :pre-checks      [dmcity/check-dmcity-disabled
                     (fn [{app :application organization :organization}]
                       (when-let [org (and organization @organization)]
                         (when-not (or (org/krysp-write-integration? org (permit/permit-type app))
                                       (ah/asianhallinta-enabled?
                                         (org/resolve-organization-scope (:municipality app) (permit/permit-type app) org)))
                           (fail :error.sftp.user-not-set))))]
   :states          integration-messages-states}
  [command]
  (ok :messages (sftp/integration-messages command)))

(defn transferred-file-response [{:keys [^InputStream stream name content-type]}]
  (with-open [stream stream]
    (-<>> (slurp stream)
          (ss/replace <> (re-pattern validators/finnish-hetu-str) "******x****")
          (ss/replace <> #"(?<=<yht:ulkomainenHenkilotunnus>)(.*[^ ].*)(?=</yht:ulkomainenHenkilotunnus>)" "*******")
          (resp/content-type content-type)
          (resp/set-headers (assoc http/no-cache-headers "Content-Disposition" (format "filename=\"%s\"" name)))
          (resp/status 200))))

(defn validate-integration-message-filename [{{:keys [id filename]} :data}]
  ; Action pipeline checks that the curren user has access to application.
  ; Check that the file is related to that application
  ; and that a directory traversal is not attempted.
  (when (or (not (re-matches #"^([\w_\-\.]+)\.(txt|xml)$" filename))
            (not (ss/starts-with? filename id)))
    (fail :error.invalid-filename)))

(def integration-message-file-type-checker (sc/checker sftp/IntegrationMessageFileType))

(defraw integration-message
  {:parameters       [id fileType filename]
   :user-roles       #{:authority}
   :org-authz-roles  #{:approver}
   :input-validators [(fn [{data :data}]
                        (when (integration-message-file-type-checker (:fileType data))
                          (fail :error.unknown-type)))
                      (fn [{data :data}]
                        (when-not (validators/application-id? (:id data))
                          (fail :error.invalid-key)))
                      validate-integration-message-filename]
   :states           integration-messages-states}
  [command]
  (try
    (let [{:keys [name] :as f} (sftp/integration-message-stream command)]
      (assert (ss/starts-with? name id)) ; Can't be too paranoid...
      (transferred-file-response f))
    (catch Exception e
      (errorf "File %s could not be opened: %s %s" filename (ex-message e) (ex-data e))
      (resp/status 404 "File Not Found"))))

(defquery current-configuration
  {:description "Returns current configuration values for specified keys"
   :user-roles #{:anonymous}}
  [_]
  (ok (config/current-configuration)))

;;
;;  RH data modifications to documents
;;

(defcommand update-post-verdict-doc
  {:parameters       [:id doc updates]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :doc])
                      (partial action/vector-parameters [:updates])]
   :states           states/post-verdict-states
   :permissions      [{:context  {:application {:state states/post-verdict-states}}
                       :required [:application/read :document/edit-post-verdict]}]
   :pre-checks       [pate-enabled
                      (doc/editable-by-state? states/post-verdict-but-terminal)
                      doc/doc-disabled-validator
                      doc/validate-created-after-verdict]}
  [command]
  (let [[path _]    (first updates)
        path-prefix (first (ss/split path #"\."))]
    (if (= "rakennuksenOmistajat" path-prefix)
      (fail :error.document-not-editable-in-current-state)
      (do
        (doc-persistence/set-edited-timestamp command doc)
        (doc-persistence/update! command doc updates "documents")))))

(defcommand send-doc-updates
  {:description      "Send 'RH-tietojen muutos' KuntaGML message."
   :parameters       [:id docId]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :docId])]
   :permissions      [{:context  {:application {:state states/post-verdict-states}}
                       :required [:application/read :document/edit-post-verdict]}]
   :pre-checks       [pate-kuntagml-integration-configured
                      (doc/editable-by-state? states/post-verdict-but-terminal)
                      doc/doc-disabled-validator
                      doc/validate-created-after-verdict]}
  [command]
  (mapping-to-krysp/save-rh-tietojen-muutos-as-krysp command)
  (doc-persistence/set-sent-timestamp command docId)
  (ok))
