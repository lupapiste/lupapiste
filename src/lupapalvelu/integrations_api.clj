(ns lupapalvelu.integrations-api
  "API for commands/functions working with integrations (ie. KRYSP, Asianhallinta)"
  (:require [taoensso.timbre :as timbre :refer [infof info error errorf]]
            [clojure.java.io :as io]
            [noir.response :as resp]
            [monger.operators :refer [$in $set $unset $push $each $elemMatch]]
            [lupapalvelu.action :refer [defcommand defquery defraw update-application] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.autologin :as autologin]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.user :as user]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.xml.krysp.building-reader :as building-reader]
            [lupapalvelu.xml.asianhallinta.core :as ah]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.http :as http]
            [sade.util :as util]
            [sade.validators :as validators]))

;;
;; Application approval
;;

; TODO needs refactoring
(defn- update-link-permit-data-with-kuntalupatunnus-from-verdict [application]
  (let [link-permit-app-id (-> application :linkPermitData first :id)
        link-permit-app (domain/get-application-no-access-checking link-permit-app-id)
        kuntalupatunnus (-> link-permit-app :verdicts first :kuntalupatunnus)]
    ; TODO why we check only link permit data on index 0?
    (if kuntalupatunnus
      (-> application
         (assoc-in [:linkPermitData 0 :lupapisteId] link-permit-app-id)
         (assoc-in [:linkPermitData 0 :id] kuntalupatunnus)
         (assoc-in [:linkPermitData 0 :type] "kuntalupatunnus"))
      (if (and (foreman/foreman-app? application) (some #{(keyword (:state link-permit-app))} states/post-submitted-states))
        application
        (do
          (info "Not able to get a kuntalupatunnus for the application  " (:id application) " from it's link permit's (" link-permit-app-id ") verdict."
                 " Associated Link-permit data: " (:linkPermitData application))
          (if (foreman/foreman-app? application)
            (fail! :error.link-permit-app-not-in-post-sent-state)
            (fail! :error.kuntalupatunnus-not-available-from-verdict)))))))

(defn get-transfer-item [type {:keys [created user]} & [attachments]]
  (let [transfer {:type type
                  :user (select-keys user [:id :role :firstName :lastName])
                  :timestamp created}]
    (if (empty? attachments)
      transfer
      (assoc transfer :attachments (map :id attachments)))))

(defn- do-approve [application created id lang jatkoaika-app? do-rest-fn user]
  (let [organization (organization/get-organization (:organization application))]
    (if (organization/krysp-integration? organization (permit/permit-type application))
      (or
        (application/validate-link-permits application)
        (let [all-attachments (:attachments (domain/get-application-no-access-checking (:id application) [:attachments]))
              sent-file-ids   (if jatkoaika-app?
                                (mapping-to-krysp/save-jatkoaika-as-krysp application lang organization)
                                (let [submitted-application (mongo/by-id :submitted-applications id)]
                                  (mapping-to-krysp/save-application-as-krysp application lang submitted-application organization)))
              attachments-updates (or (attachment/create-sent-timestamp-update-statements all-attachments sent-file-ids created) {})]
          (do-rest-fn attachments-updates)))
      ;; Integration details not defined for the organization -> let the approve command pass
      (do-rest-fn nil))))

(defcommand approve-application
  {:parameters       [id lang]
   :input-validators [(partial action/non-blank-parameters [:id :lang])]
   :user-roles       #{:authority}
   :notified         true
   :on-success       (action/notify :application-state-change)
   :states           #{:submitted :complementNeeded}
   :org-authz-roles  #{:approver}}
  [{:keys [application created user] :as command}]
  (let [jatkoaika-app? (= :ya-jatkoaika (-> application :primaryOperation :name keyword))
        next-state   (if jatkoaika-app?
                       :closed ; FIXME create a state machine for :ya-jatkoaika
                       (sm/next-state application))
        _           (assert next-state)

        timestamps  (zipmap (conj #{:modified :sent} next-state) (repeat created))
        _           (assert (every? (partial contains? domain/application-skeleton) (keys timestamps)))

        history-entries (map #(application/history-entry % created user) (set [:sent next-state]))

        app-updates (merge
                      {:state next-state
                       :authority (if (domain/assigned? application) (:authority application) (user/summary user))} ; LUPA-1450
                      timestamps)
        application (-> application
                      meta-fields/enrich-with-link-permit-data
                      (#(if (= "lupapistetunnus" (-> % :linkPermitData first :type))
                         (update-link-permit-data-with-kuntalupatunnus-from-verdict %)
                         %))
                      (merge app-updates))
        mongo-query {:state {$in ["submitted" "complementNeeded"]}}
        indicator-updates (application/mark-indicators-seen-updates application user created)
        transfer (get-transfer-item :exported-to-backing-system {:created created :user user})
        do-update (fn [attachments-updates]
                    (update-application command
                      mongo-query
                      {$push {:transfers transfer
                              :history {$each history-entries}}
                       $set (util/deep-merge app-updates attachments-updates indicator-updates)})
                    (ok :integrationAvailable (not (nil? attachments-updates))))]

    (do-approve application created id lang jatkoaika-app? do-update user)))

(defn- application-already-exported [type]
  (fn [_ application]
    (when-not (= "aiemmalla-luvalla-hakeminen" (get-in application [:primaryOperation :name]))
      (let [export-ops #{:exported-to-backing-system :exported-to-asianhallinta}
            filtered-transfers (filter (comp export-ops keyword :type) (:transfers application))]
        (when-not (= (keyword (:type (last filtered-transfers))) type)
          (fail :error.application-not-exported))))))

(defcommand move-attachments-to-backing-system
  {:parameters [id lang attachmentIds]
   :input-validators [(partial action/non-blank-parameters [:id :lang])
                      (partial action/vector-parameter-of :attachmentIds string?)]
   :user-roles #{:authority}
   :pre-checks [(permit/validate-permit-type-is permit/R)
                (application-already-exported :exported-to-backing-system)]
   :states     (conj states/post-verdict-states :sent)
   :description "Sends such selected attachments to backing system that are not yet sent."}
  [{:keys [created application user] :as command}]

  (let [all-attachments (:attachments (domain/get-application-no-access-checking id [:attachments]))
        attachments-wo-sent-timestamp (filter
                                        #(and
                                          (-> % :versions count pos?)
                                          (or
                                            (not (:sent %))
                                            (> (-> % :versions last :created) (:sent %)))
                                          (not (#{"verdict" "statement"} (-> % :target :type)))
                                          (some #{(:id %)} attachmentIds))
                                        all-attachments)]
    (if (pos? (count attachments-wo-sent-timestamp))
      (let [organization  (organization/get-organization (:organization application))
            sent-file-ids (mapping-to-krysp/save-unsent-attachments-as-krysp (assoc application :attachments attachments-wo-sent-timestamp) lang organization)
            data-argument (attachment/create-sent-timestamp-update-statements all-attachments sent-file-ids created)
            transfer      (get-transfer-item :exported-to-backing-system command attachments-wo-sent-timestamp)]
        (update-application command {$push {:transfers transfer}
                                     $set data-argument})
        (ok))
      (fail :error.sending-unsent-attachments-failed))))

;;
;; krysp enrichment
;;

(def krysp-enrichment-states (states/all-application-states-but (conj states/terminal-states :sent :verdictGiven :constructionStarted)))

(defn add-value-metadata [m meta-data]
  (reduce (fn [r [k v]] (assoc r k (if (map? v) (add-value-metadata v meta-data) (assoc meta-data :value v)))) {} m))

(defn- load-building-data [url credentials property-id building-id overwrite-all?]
  (let [all-data (building-reader/->rakennuksen-tiedot (building-reader/building-xml url credentials property-id) building-id)]
    (if overwrite-all?
      all-data
      (select-keys all-data (keys building-reader/empty-building-ids)))))

(defcommand merge-details-from-krysp
  {:parameters [id documentId path buildingId overwrite collection]
   :input-validators [doc-persistence/validate-collection
                      (partial action/non-blank-parameters [:documentId :path])
                      (partial action/boolean-parameters [:overwrite])]
   :user-roles #{:applicant :authority}
   :states     krysp-enrichment-states
   :pre-checks [application/validate-authority-in-drafts]}
  [{created :created {:keys [organization propertyId] :as application} :application :as command}]
  (let [{url :url credentials :credentials} (organization/get-krysp-wfs application)
        clear-ids?   (or (ss/blank? buildingId) (= "other" buildingId))]
    (if (or clear-ids? url)
      (let [document     (doc-persistence/by-id application collection documentId)
            schema       (schemas/get-schema (:schema-info document))
            converted-doc (when overwrite ; don't clean data if user doesn't wish to override
                            (model/convert-document-data ; remove old krysp data
                                     (fn [_ value] ; pred
                                       (= "krysp" (:source value)))
                                     (fn [schema value] ; emitter sets default values
                                       (-> value
                                         (dissoc :source :sourceValue :modified)
                                         (assoc :value (tools/default-values schema))))
                                     document
                                     nil))
            cleared-data (dissoc (:data converted-doc) :buildingId) ; buildingId is set below explicitly

            buildingId-updates (doc-persistence/->model-updates [[path buildingId]])
            buildingId-update-map (doc-persistence/validated-model-updates application collection document buildingId-updates created :source nil)

            clearing-updates (tools/path-vals (tools/unwrapped cleared-data))
            clearing-update-map (when-not (util/empty-or-nil? clearing-updates) ; create updates only when there is data
                                  (doc-persistence/validated-model-updates application collection document clearing-updates created :source nil))

            krysp-updates (filter
                            (fn [[path _]] (model/find-by-name (:body schema) path))
                            (tools/path-vals
                              (if clear-ids?
                                building-reader/empty-building-ids
                                (load-building-data url credentials propertyId buildingId overwrite))))
            krysp-update-map (doc-persistence/validated-model-updates application collection document krysp-updates created :source "krysp")

            {:keys [mongo-query mongo-updates]} (util/deep-merge
                                                  clearing-update-map
                                                  buildingId-update-map
                                                  krysp-update-map)]
        (update-application command mongo-query mongo-updates)
        (ok))
      (fail :error.no-legacy-available))))

;;
;; Building info
;;

(defquery get-building-info-from-wfs
  {:parameters [id]
   :user-roles #{:applicant :authority}
   :org-authz-roles auth/all-org-authz-roles
   :user-authz-roles auth/all-authz-roles
   :states     states/all-application-states
   :pre-checks [application/validate-authority-in-drafts]}
  [{{:keys [organization municipality propertyId] :as application} :application}]
  (if-let [{url :url credentials :credentials} (organization/get-krysp-wfs application)]
    (try
      (let [kryspxml    (building-reader/building-xml url credentials propertyId)
            buildings   (building-reader/->buildings-summary kryspxml)]
        (ok :data buildings))
      (catch java.io.IOException e
        (errorf "Unable to get building info from %s backend: %s" (i18n/loc "municipality" municipality) (.getMessage e))
        (fail :error.unknown)))
    (ok)))

;;
;; Asianhallinta
;;

(defn- fetch-linked-kuntalupatunnus
  "Fetch kuntalupatunnus from application's link permit's verdicts"
  [application]
  (when-let [link-permit-app (application/get-link-permit-app application)]
    (-> link-permit-app :verdicts first :kuntalupatunnus)))

(defn- has-asianhallinta-operation [_ {:keys [primaryOperation]}]
  (when-not (operations/get-operation-metadata (:name primaryOperation) :asianhallinta)
    (fail :error.operations.asianhallinta-disabled)))

(defcommand application-to-asianhallinta
  {:parameters [id lang]
   :input-validators [(partial action/non-blank-parameters [:id :lang])]
   :user-roles #{:authority}
   :notified   true
   :on-success (action/notify :application-state-change)
   :pre-checks [has-asianhallinta-operation]
   :states     #{:submitted :complementNeeded}}
  [{:keys [application created user]:as command}]
  (let [application (meta-fields/enrich-with-link-permit-data application)
        application (if-let [kuntalupatunnus (fetch-linked-kuntalupatunnus application)]
                      (update-in application
                                 [:linkPermitData]
                                 conj {:id kuntalupatunnus
                                       :type "kuntalupatunnus"})
                      application)
        submitted-application (mongo/by-id :submitted-applications id)
        all-attachments (:attachments (domain/get-application-no-access-checking id [:attachments]))

        app-updates {:modified created, :authority (if (domain/assigned? application) (:authority application) (user/summary user))}
        organization (organization/get-organization (:organization application))
        indicator-updates (application/mark-indicators-seen-updates application user created)
        file-ids (ah/save-as-asianhallinta application lang submitted-application organization) ; Writes to disk
        attachments-updates (or (attachment/create-sent-timestamp-update-statements all-attachments file-ids created) {})
        transfer (get-transfer-item :exported-to-asianhallinta command)]
    (update-application command
                        (util/deep-merge
                          (application/state-transition-update (sm/next-state application) created user)
                          {$push {:transfers transfer}
                           $set (util/deep-merge app-updates attachments-updates indicator-updates)}))
    (ok)))

(defn- update-kuntalupatunnus [application]
  (if-let [kuntalupatunnus (fetch-linked-kuntalupatunnus application)]
    (update-in application
               [:linkPermitData]
               conj {:id kuntalupatunnus
                     :type "kuntalupatunnus"})
    application))

(defn- application-already-in-asianhallinta [_ application]
  (let [filtered-transfers (filter #(some #{(:type %)} "to-backing-system to-asianhallinta" ) (:transfers application))]
    (when-not (= (:type (last filtered-transfers)) "to-asianhallinta")
      (fail :error.application.not-in-asianhallinta))))

(defcommand attachments-to-asianhallinta
  {:parameters [id lang attachmentIds]
   :input-validators [(partial action/non-blank-parameters [:id :lang])
                      (partial action/vector-parameter-of :attachmentIds string?)]
   :user-roles #{:authority}
   :pre-checks [has-asianhallinta-operation (application-already-exported :exported-to-asianhallinta)]
   :states     (conj states/post-verdict-states :sent)
   :description "Sends such selected attachments to backing system that are not yet sent."}
  [{:keys [created application user] :as command}]

  (let [all-attachments (:attachments (domain/get-application-no-access-checking (:id application) [:attachments]))
        attachments-wo-sent-timestamp (filter
                                        #(and
                                          (-> % :versions count pos?)
                                          (or
                                            (not (:sent %))
                                            (> (-> % :versions last :created) (:sent %)))
                                          (not (#{"verdict" "statement"} (-> % :target :type)))
                                          (some #{(:id %)} attachmentIds))
                                        all-attachments)
        transfer (get-transfer-item :exported-to-asianhallinta command attachments-wo-sent-timestamp)]
    (if (pos? (count attachments-wo-sent-timestamp))
      (let [application (meta-fields/enrich-with-link-permit-data application)
            application (update-kuntalupatunnus application)
            sent-file-ids (ah/save-as-asianhallinta-asian-taydennys application attachments-wo-sent-timestamp lang)
            data-argument (attachment/create-sent-timestamp-update-statements all-attachments sent-file-ids created)]
        (update-application command {$push {:transfers transfer}
                                     $set data-argument})
        (ok))
      (fail :error.sending-unsent-attachments-failed))))

(defquery external-api-enabled
  {:description "Dummy query to check if external API use is configured. Organization from application or user orgs."
   :parameters [id]
   :user-roles #{:authority}
   :states     states/all-states
   :pre-checks [(fn [{{ip :client-ip} :web user :user} {:keys [organization]}]
                  (if organization
                    (when-not (autologin/allowed-ip? ip organization)
                      (fail :error.ip-not-allowed))
                    (when-not (some
                                (partial autologin/allowed-ip? ip)
                                (user/organization-ids user))
                      (fail :error.ip-not-allowed))))]})

(defn- list-dir [path pattern]
  (->>
    path
    io/file
    (.listFiles)
    (filter (fn [^java.io.File f] (re-matches pattern (.getName f))))
    (map (fn [^java.io.File f] {:name (.getName f), :modified (.lastModified f)}))))

(defn- list-integration-dirs [output-dir id]
  (if output-dir
    (let [xml-pattern (re-pattern (str "^" id "_.*\\.xml"))
          id-pattern  (re-pattern (str "^" id "_.*"))]
      {:waiting (list-dir output-dir xml-pattern)
       :ok (list-dir (str output-dir env/file-separator "archive") xml-pattern)
       :error (list-dir (str output-dir env/file-separator "error") id-pattern)})
    {:waiting []
     :ok []
     :error []}))

(defquery transfers
  {:parameters [id]
   :user-roles #{:authority}
   :org-authz-roles  #{:approver}
   :states #{:sent :complementNeeded}}
  [{{org-id :organization municipality :municipality permit-type :permitType} :application}]
  (let [organization (organization/get-organization org-id)
        krysp-output-dir (mapping-to-krysp/resolve-output-directory organization permit-type)
        ah-scope         (organization/resolve-organization-scope municipality permit-type organization)
        ah-output-dir    (when (ah/asianhallinta-enabled? ah-scope) (ah/resolve-output-directory ah-scope))]
    (ok :krysp (list-integration-dirs krysp-output-dir id)
        :ah    (list-integration-dirs ah-output-dir id))))

(defraw transfer
  {:parameters [id transferType fileType filename]
   :user-roles #{:authority}
   :org-authz-roles  #{:approver}
   :input-validators [(fn [{data :data}]
                        (when-not (#{"ok" "error"} (:fileType data))
                          (fail :error.unknown-type)))
                      (fn [{data :data}]
                        (when-not (#{"krysp" "ah"} (:transferType data))
                          (fail :error.unknown-type)))
                      (fn [{data :data}]
                        (when-not (re-matches #"^([\w_\-\.]+)\.(txt|xml)$" (:filename data))
                          (fail :error.invalid-filename)))]
   :states #{:sent :complementNeeded}}
  [{{org-id :organization municipality :municipality permit-type :permitType} :application}]
  (let [organization (organization/get-organization org-id)
        dir (case transferType
              "krysp" (mapping-to-krysp/resolve-output-directory organization permit-type)
              "ah" (ah/resolve-output-directory
                     (organization/resolve-organization-scope municipality permit-type organization)))
        sanitized (mime/sanitize-filename filename) ; input validator doesn't allow slashes, but sanitize anyway
        path (case fileType
               "ok" (str env/file-separator "archive" env/file-separator)
               "error" (str env/file-separator "error" env/file-separator))
        f (io/file (str dir path sanitized))]
    (if (.exists f)
      (let [masked-content (ss/replace (slurp f) (re-pattern validators/finnish-hetu-str) "******x****")]
        (->>
          (resp/content-type (mime/mime-type sanitized) masked-content)
          (resp/set-headers http/no-cache-headers)
          (resp/status 200)))
      (resp/status 404 "File Not Found"))))
