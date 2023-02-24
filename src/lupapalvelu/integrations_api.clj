(ns lupapalvelu.integrations-api
  "API for commands/functions working with integrations (ie. KRYSP, Asianhallinta)"
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defcommand defquery defraw update-application] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.autologin :as autologin]
            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.backing-system.asianhallinta.core :as ah]
            [lupapalvelu.backing-system.core :as bs]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.backing-system.krysp.building-reader :as building-reader]
            [lupapalvelu.building :as building]
            [lupapalvelu.document.document :as doc]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.verdict :refer [pate-enabled]]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.rest.config :as config]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.sftp.core :as sftp]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]
            [lupapalvelu.ya-extension :as yax]
            [monger.operators :refer [$in $set $unset $push]]
            [noir.response :as resp]
            [sade.core :refer :all]
            [sade.http :as http]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as validators]
            [schema.core :as sc]
            [swiss.arrows :refer [-<>>]]
            [taoensso.timbre :refer [error errorf]]))

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

(defn- transfers-not-done
  [command]
  (when (some? (:application command))
    (when-not (-> command :application :transfers empty?)
      (fail :error.unknown))))                                   ;;TODO error.integration.has.been.done

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
  [{{organizationId :organization permit-type :permitType municipality :municipality primaryOperation :primaryOperation
     bulletinOpDescription :bulletinOpDescription :as application} :application}]
  (when organizationId
    (let [{:keys [enabled descriptions-from-backend-system]} (org/bulletin-settings-for-scope (org/get-organization organizationId) permit-type municipality)]
      (when (and enabled
                 (not descriptions-from-backend-system)
                 (not (foreman/foreman-app? application))
                 (not (= (:name primaryOperation) "suunnittelijan-nimeaminen"))
                 (ss/blank? bulletinOpDescription))
        (fail :error.invalid-value)))))

(defcommand approve-application
  {:parameters       [id lang]
   :pre-checks       [temporary-approve-prechecks
                      ensure-bulletin-op-description-is-set-if-needed]
   :input-validators [(partial action/non-blank-parameters [:id :lang])]
   :user-roles       #{:authority}
   :notified         true
   :on-success       (action/notify :application-state-change)
   :states           #{:submitted :complementNeeded}
   :org-authz-roles  #{:approver}}
  [{:keys [application created user organization] :as command}]
  (try
    (let [next-state (if (yax/ya-extension-app? application)
                       (sm/verdict-given-state application)
                       (sm/next-state application))
          _           (assert (sm/valid-state? application next-state))

          handlers    (ensure-general-handler-is-set (:handlers application) user @organization)
          application (-> application
                          (assoc :handlers handlers)
                          (app/post-process-app-for-krysp @organization))]
     (when (attachment/comments-saved-as-attachment? application next-state)
       (attachment/save-comments-as-attachment command {:state next-state}))
     ;; Description is required to be an attachment for encumbrance permits in KRYSP
     (attachment/save-description-as-attachment command)
     (or (app/validate-link-permits application)             ; If validation failure is non-nil, just return it.
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
      (error e "Exception while approving application")
      (fail :error.integration.create-message
            :details (or (:details (ex-data e))
                         (.getMessage e))))))

(defcommand approve-application-after-verdict
  {:parameters       [id lang]
   :pre-checks       [temporary-approve-prechecks
                      pate-enabled
                      transfers-not-done]
   :input-validators [(partial action/non-blank-parameters [:id :lang])]
   :user-roles       #{:authority}
   :states           #{:verdictGiven :foremanVerdictGiven :constructionStarted}
   :org-authz-roles  #{:approver}}
  [{:keys [application created user organization] :as command}]
  (try
    (let [application (-> application
                          (app/post-process-app-for-krysp @organization))
          state       (:state application)]
      (when (attachment/comments-saved-as-attachment? application state)
        (attachment/save-comments-as-attachment command {:state state}))
      (attachment/save-description-as-attachment command)
      (or (app/validate-link-permits application)             ; If validation failure is non-nil, just return it.
          (let [submitted-application (mongo/by-id :submitted-applications id)
                approve-application   (bs/approve-application! (assoc command :application application) submitted-application lang)
                [integration-available sent-file-ids] approve-application
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
      (fail :error.integration.create-message
            :details (or (:details (ex-data e))
                         (.getMessage e))))))

(defn- application-already-exported [type]
  (fn [{application :application}]
    (when-not (= "aiemmalla-luvalla-hakeminen" (get-in application [:primaryOperation :name]))
      (let [export-ops #{:exported-to-backing-system :exported-to-asianhallinta}
            filtered-transfers (filter (comp export-ops keyword :type) (:transfers application))]
        (when-not (= (keyword (:type (last filtered-transfers))) type)
          (fail :error.application-not-exported))))))

(defn- has-sendable-attachments
  [{{attachments :attachments} :application}]
  (when-not (some attachment/transmittable-to-krysp? attachments)
    (fail :error.no-sendable-attachments)))

(defcommand move-attachments-to-backing-system
  {:parameters       [id lang attachmentIds]
   :input-validators [(partial action/non-blank-parameters [:id :lang])
                      (partial action/vector-parameter-of :attachmentIds string?)]
   :user-roles       #{:authority}
   :pre-checks       [(fn [{:keys [application] :as command}]
                        (if application
                          ;; Must either have SFTP KRYSP support or ALLU support
                          (when-not (allu/allu-application? (:organization application) (permit/permit-type application))
                            (or ((permit/validate-permit-type-is permit/R) command)
                                (mapping-to-krysp/http-not-allowed command)))
                          (fail :error.invalid-application-parameter)))
                      (application-already-exported :exported-to-backing-system)
                      has-sendable-attachments]
   :states           (conj states/post-verdict-states :sent)
   :description      "Sends such selected attachments to backing system that are not yet sent."}
  [{:keys [created application] :as command}]
  (let [all-attachments (:attachments application)]
    (when-let [selected-attachments (some-> (set attachmentIds)
                                            (comp :id)
                                            (filter all-attachments)
                                            seq)]
      (let [sent-file-ids (bs/send-attachments! command selected-attachments lang)
            data-argument (attachment/create-sent-timestamp-update-statements all-attachments
                                                                              sent-file-ids
                                                                              created)
            transfer-data {:data-key :attachments
                           :data     (map :id selected-attachments)}
            transfer      (get-transfer-item :exported-to-backing-system command transfer-data)]
        (update-application command {$push {:transfers transfer}
                                     $set  data-argument})))
    (ok)))

(defcommand parties-as-krysp
  {:description "Sends new designers to backing system after verdict"
   :parameters [id lang]
   :input-validators [(partial action/non-blank-parameters [:id :lang])]
   :user-roles #{:authority}
   :pre-checks [(partial permit/valid-permit-types {:R []})
                (application-already-exported :exported-to-backing-system)
                mapping-to-krysp/http-not-allowed]
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

(defn- load-building-data [url credentials property-id building-id overwrite-all? application]
  (let [all-data (building-reader/->rakennuksen-tiedot-by-id (building-reader/building-xml url
                                                                                           credentials
                                                                                           property-id)
                                                             building-id)]
    (when all-data
      (building/upsert-document-buildings application all-data))
    (if overwrite-all?
      all-data
      (select-keys all-data (keys building-reader/empty-building-ids)))))

(defn- merge-details-state-guard
  "Pre-check for `merge-details-from-krysp`. The command is allowed in
  the post-verdict states only if the user is authority AND Pate is
  enabled."
  [{:keys [application] :as cmd}]
  (when (some->> application :state keyword states/post-verdict-states)
    (or (auth/application-authority-pre-check cmd)
        (pate-enabled cmd))))

(defn- huoneistot-updates
  "Huoneistot are not merged but simply replaced, since otherwise could end up with ghost
  apartments. Returns map with `:mongo-updates`. The update is a 'document-aware' mongo update."
  [command doc-info {:keys [huoneistot]}]
  ;; Check that schema includes huoneistot and later we pick only schema-allowed keys from the building data.
  (when-let [apartment-keys (some->> (:schema-body doc-info)
                                     (util/find-by-key :name "huoneistot")
                                     :body
                                     (map (comp keyword :name))
                                     seq)]
    (letfn [(pack-field [v]
              {:modified    (:created command)
               :value       v
               :source      "krysp"
               :sourceValue v})
            (pack-apartment  [apartment]
              (->> (select-keys apartment apartment-keys)
                   (map (fn [[field v]]
                          [field (pack-field v)]))
                   (into {})))]
      (let [huoneistot (->> huoneistot
                            (map (fn [[k v]]
                                   [k (pack-apartment v)]))
                            (into {}))
            validation (model/validate-fields (:application command)
                                              doc-info
                                              nil
                                              {:huoneistot huoneistot}
                                              [])]
        (if (empty? validation)
          {:mongo-updates {$set {:documents.$.data.huoneistot huoneistot}}}
          (fail! :document-would-be-in-error-after-update :results validation))))))

(defcommand merge-details-from-krysp
  {:description      "Update building information from KuntaGML, mainly buildingId"
   :parameters       [id documentId buildingId overwrite]
   :input-validators [(partial action/non-blank-parameters [:id :documentId])
                      (partial action/boolean-parameters [:overwrite])]
   :user-roles       #{:applicant :authority}
   :states           (states/all-application-states-but (conj states/terminal-states :sent))
   :pre-checks       [app/validate-authority-in-drafts
                      merge-details-state-guard]}
  [{created :created {:keys [propertyId] :as application} :application :as command}]
  (let [{:keys [url credentials]} (org/get-building-wfs application)
        path                      "buildingId"
        collection                "documents"
        other?                    (= model/other-value buildingId)
        clear-ids?                (or (ss/blank? buildingId) other?)]
    (if (or clear-ids? url)
      (let [document              (tools/by-id application collection documentId)
            schema                (schemas/get-schema (:schema-info document))
            path-schema           (model/find-by-name (:body schema) [path])
            converted-doc         (when overwrite ; don't clean data if user doesn't wish to override
                                    (model/convert-document-data ; remove old krysp data
                                      (fn [_ value] ; pred
                                        (= "krysp" (:source value)))
                                      (fn [schema value] ; emitter sets default values
                                        (-> value
                                            (dissoc :source :sourceValue :modified)
                                            (assoc :value (tools/default-values schema))))
                                      document
                                      nil))
            cleared-data          (dissoc (:data converted-doc) :buildingId :huoneistot) ; buildingId is set below explicitly

            buildingId-updates    (doc-persistence/->model-updates
                                    (cond-> [[path buildingId]]
                                      ;; clear other-key, if we just selected buildingId from dropdown
                                      (and (not other?) (:other-key path-schema)) (conj [(:other-key path-schema) ""])))
            buildingId-update-map (doc-persistence/validated-model-updates application collection document buildingId-updates created :source nil)

            clearing-updates      (tools/path-vals (tools/unwrapped cleared-data))
            clearing-update-map   (when-not (util/empty-or-nil? clearing-updates) ; create updates only when there is data
                                    (doc-persistence/validated-model-updates application collection document clearing-updates created :source nil))

            building-data         (when-not clear-ids?
                                    (load-building-data url credentials propertyId buildingId overwrite application))

            krysp-updates         (filter
                                    (fn [[path _]] (model/find-by-name (:body schema) path))
                                    (tools/path-vals
                                      (if clear-ids?
                                        building-reader/empty-building-ids
                                        (dissoc building-data :huoneistot))))
            krysp-update-map      (doc-persistence/validated-model-updates application collection document krysp-updates created :source "krysp")
            huoneistot-update-map (huoneistot-updates command
                                                      (model/document-info document schema)
                                                      building-data)

            {:keys [mongo-query mongo-updates]
             }                    (util/deep-merge
                                    clearing-update-map
                                    buildingId-update-map
                                    krysp-update-map
                                    huoneistot-update-map)]
        (update-application command mongo-query mongo-updates)
        (ok))
      (fail :error.no-legacy-available))))

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

(def- validate-extinct-value (fn [{{:keys [extinct]} :data}]
                               (when (sc/check (sc/maybe sssc/Nat) extinct)
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
                      has-sendable-attachments]
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
   :pre-checks      [(fn [{app :application organization :organization}]
                       (when-let [org (and organization @organization)]
                         (when-not (or (org/krysp-write-integration? org (permit/permit-type app))
                                       (ah/asianhallinta-enabled?
                                         (org/resolve-organization-scope (:municipality app) (permit/permit-type app) org)))
                           (fail :error.sftp.user-not-set))))]
   :states          integration-messages-states}
  [command]
  (ok :messages (sftp/integration-messages command)))

(defn transferred-file-response [{:keys [stream name content-type]}]
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
            (not (ss/starts-with filename id)))
    (fail :error.invalid-filename)))

(defraw integration-message
  {:parameters       [id fileType filename]
   :user-roles       #{:authority}
   :org-authz-roles  #{:approver}
   :input-validators [(fn [{data :data}]
                        (when (sc/check sftp/IntegrationMessageFileType (:fileType data))
                          (fail :error.unknown-type)))
                      (fn [{data :data}]
                        (when-not (validators/application-id? (:id data))
                          (fail :error.invalid-key)))
                      validate-integration-message-filename]
   :states           integration-messages-states}
  [command]
  (try
    (let [{:keys [name] :as f} (sftp/integration-message-stream command)]
      (assert (ss/starts-with name id)) ; Can't be too paranoid...
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

(defn editable-by-state?
  "Pre-check to determine if documents are editable in abnormal states"
  [default-states]
  (fn [{document :document {state :state} :application}]
    (when document
      (when-not (-> document
                    (model/get-document-schema)
                    (doc/state-valid-by-schema? :editable-in-states default-states state))
        (fail :error.document-not-editable-in-current-state)))))

(defcommand update-post-verdict-doc
  {:parameters       [id doc updates]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :doc])
                      (partial action/vector-parameters [:updates])]
   :states           states/post-verdict-states
   :user-roles       #{:authority}
   :org-authz-roles  #{:approver}
   :pre-checks       [pate-enabled
                      (editable-by-state? (set/union states/update-doc-states [:verdictGiven]))
                      doc/doc-disabled-validator
                      doc/validate-created-after-verdict]}
  [command]
  (let [[path _] (first updates)
        path-prefix (first (ss/split path #"\."))]
    (if (= "rakennuksenOmistajat" path-prefix)
      (fail :error.document-not-editable-in-current-state)
      (do
        (doc-persistence/set-edited-timestamp command doc)
        (doc-persistence/update! command doc updates "documents")))))

(defn- pate-krysp-integration-configured
  "Checks that PATE supported integration is on.
  Either HTTP or PATE SFTP`."
  [{:keys [organization application]}]
  (when-let [permit-type (:permitType application)]
    (let [municipality (:municipality application)
          pate-sftp?   (-> (org/resolve-organization-scope municipality permit-type @organization)
                           (get-in [:pate :sftp]))]
      (if-not (org/krysp-write-integration? @organization permit-type)
        (fail :error.krysp-integration)
        (when-not (or pate-sftp?
                      (mapping-to-krysp/http-conf @organization permit-type))
          (fail :error.sftp-disabled))))))


(defcommand send-doc-updates
  {:description      "Send 'RH-tietojen muutos' KuntaGML message."
   :parameters       [id docId]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :docId])]
   :states           states/post-verdict-states
   :user-roles       #{:authority}
   :org-authz-roles  #{:approver}
   :pre-checks       [pate-krysp-integration-configured
                      (editable-by-state? (set/union states/update-doc-states [:verdictGiven]))
                      doc/doc-disabled-validator
                      doc/validate-created-after-verdict]}
  [command]
  (mapping-to-krysp/save-rh-tietojen-muutos-as-krysp command)
  (doc-persistence/set-sent-timestamp command docId)
  (ok))
