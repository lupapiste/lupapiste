(ns lupapalvelu.integrations-api
  "API for commands/functions working with integrations (ie. KRYSP, Asianhallinta)"
  (:require [taoensso.timbre :as timbre :refer [infof info error]]
            [monger.operators :refer [$in $set $unset $push $elemMatch]]
            [lupapalvelu.action :refer [defcommand update-application notify] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.document.commands :as commands]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.user :as user]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]
            [lupapalvelu.xml.asianhallinta.core :as ah]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

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
      (if (and (foreman/foreman-app? application) (some #{(keyword (:state link-permit-app))} action/post-submitted-states))
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
    (if (organization/has-ftp-user? organization (permit/permit-type application))
      (or
        (application/validate-link-permits application)
        (let [sent-file-ids (if jatkoaika-app?
                              (mapping-to-krysp/save-jatkoaika-as-krysp application lang organization)
                              (let [submitted-application (mongo/by-id :submitted-applications id)]
                                (mapping-to-krysp/save-application-as-krysp application lang submitted-application organization)))
              attachments-updates (or (attachment/create-sent-timestamp-update-statements (:attachments application) sent-file-ids created) {})]
          (do-rest-fn attachments-updates)))
      ;; SFTP user not defined for the organization -> let the approve command pass
      (do-rest-fn nil))))

(defcommand approve-application
  {:parameters [id lang]
   :user-roles #{:authority}
   :notified   true
   :on-success (notify :application-state-change)
   :states     [:submitted :complement-needed]}
  [{:keys [application created user] :as command}]
  (let [jatkoaika-app? (= :ya-jatkoaika (-> application :primaryOperation :name keyword))
        foreman-notice? (foreman/notice? application)
        app-updates (merge
                      {:modified created
                       :sent created
                       :authority (if (domain/assigned? application) (:authority application) (user/summary user))} ; LUPA-1450
                      (if (or jatkoaika-app? foreman-notice?)
                        {:state :closed :closed created}
                        {:state :sent}))
        application (-> application
                      meta-fields/enrich-with-link-permit-data
                      (#(if (= "lupapistetunnus" (-> % :linkPermitData first :type))
                         (update-link-permit-data-with-kuntalupatunnus-from-verdict %)
                         %))
                      (merge app-updates))
        mongo-query (if (or jatkoaika-app? foreman-notice?)
                      {:state {$in ["submitted" "complement-needed"]}}
                      {})
        indicator-updates (application/mark-indicators-seen-updates application user created)
        transfer (get-transfer-item :exported-to-backing-system {:created created :user user})
        do-update (fn [attachments-updates]
                    (update-application command
                      mongo-query
                      {$push {:transfers transfer}
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
   :user-roles #{:authority}
   :pre-checks [(permit/validate-permit-type-is permit/R)
                (application-already-exported :exported-to-backing-system)]
   :states     [:verdictGiven :constructionStarted]
   :description "Sends such selected attachments to backing system that are not yet sent."}
  [{:keys [created application user] :as command}]

  (let [attachments-wo-sent-timestamp (filter
                                        #(and
                                          (-> % :versions count pos?)
                                          (or
                                            (not (:sent %))
                                            (> (-> % :versions last :created) (:sent %)))
                                          (not (#{"verdict" "statement"} (-> % :target :type)))
                                          (some #{(:id %)} attachmentIds))
                                        (:attachments application))]
    (if (pos? (count attachments-wo-sent-timestamp))
      (let [organization  (organization/get-organization (:organization application))
            sent-file-ids (mapping-to-krysp/save-unsent-attachments-as-krysp (assoc application :attachments attachments-wo-sent-timestamp) lang organization)
            data-argument (attachment/create-sent-timestamp-update-statements (:attachments application) sent-file-ids created)
            transfer      (get-transfer-item :exported-to-backing-system command attachments-wo-sent-timestamp)]
        (update-application command {$push {:transfers transfer}
                                     $set data-argument})
        (ok))
      (fail :error.sending-unsent-attachments-failed))))

;;
;; krysp enrichment
;;

(defn add-value-metadata [m meta-data]
  (reduce (fn [r [k v]] (assoc r k (if (map? v) (add-value-metadata v meta-data) (assoc meta-data :value v)))) {} m))

(defn- load-building-data [url property-id building-id overwrite-all?]
  (let [all-data (krysp-reader/->rakennuksen-tiedot (krysp-reader/building-xml url property-id) building-id)]
    (if overwrite-all?
      all-data
      (select-keys all-data (keys krysp-reader/empty-building-ids)))))

(defcommand merge-details-from-krysp
  {:parameters [id documentId path buildingId overwrite collection]
   :input-validators [commands/validate-collection
                      (partial action/non-blank-parameters [:documentId :path])
                      (partial action/boolean-parameters [:overwrite])]
   :user-roles #{:applicant :authority}
   :states     (action/all-application-states-but [:sent :verdictGiven :constructionStarted :closed :canceled])
   :pre-checks [application/validate-authority-in-drafts]}
  [{created :created {:keys [organization propertyId] :as application} :application :as command}]
  (if-let [{url :url} (organization/get-krysp-wfs application)]
    (let [document     (commands/by-id application collection documentId)
          schema       (schemas/get-schema (:schema-info document))
          clear-ids?   (or (ss/blank? buildingId) (= "other" buildingId))
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

          buildingId-updates (commands/->model-updates [[path buildingId]])
          buildingId-update-map (commands/validated-model-updates application collection document buildingId-updates created :source nil)

          clearing-updates (tools/path-vals (tools/unwrapped cleared-data))
          clearing-update-map (when-not (util/empty-or-nil? clearing-updates) ; create updates only when there is data
                                (commands/validated-model-updates application collection document clearing-updates created :source nil))

          krysp-updates (filter
                          (fn [[path _]] (model/find-by-name (:body schema) path))
                          (tools/path-vals
                            (if clear-ids?
                              krysp-reader/empty-building-ids
                              (load-building-data url propertyId buildingId overwrite))))
          krysp-update-map (commands/validated-model-updates application collection document krysp-updates created :source "krysp")

          {:keys [mongo-query mongo-updates]} (util/deep-merge
                                                clearing-update-map
                                                buildingId-update-map
                                                krysp-update-map)]
      (infof "merging data into %s %s" (get-in document [:schema-info :name]) (:id document))
      (update-application command mongo-query mongo-updates)
      (ok))
    (fail :error.no-legacy-available)))

;;
;; Building info
;;

(defcommand get-building-info-from-wfs
  {:parameters [id]
   :user-roles #{:applicant :authority}
   :states     (action/all-application-states-but [:sent :verdictGiven :constructionStarted :closed :canceled])
   :pre-checks [application/validate-authority-in-drafts]}
  [{{:keys [organization propertyId] :as application} :application}]
  (if-let [{url :url} (organization/get-krysp-wfs application)]
    (let [kryspxml  (krysp-reader/building-xml url propertyId)
          buildings (krysp-reader/->buildings-summary kryspxml)]
      (ok :data buildings))
    (ok)))

;;
;; Asianhallinta
;;

(defn- fetch-linked-kuntalupatunnus [application]
  "Fetch kuntalupatunnus from application's link permit's verdicts"
  (when-let [link-permit-app (application/get-link-permit-app application)]
    (-> link-permit-app :verdicts first :kuntalupatunnus)))

(defn- has-asianhallinta-operation [_ {:keys [primaryOperation]}]
  (when-not (operations/get-operation-metadata (:name primaryOperation) :asianhallinta)
    (fail :error.operations.asianhallinta-disabled)))

(defcommand application-to-asianhallinta
  {:parameters [id lang]
   :user-roles #{:authority}
   :notified   true
   :on-success (notify :application-state-change)
   :pre-checks [has-asianhallinta-operation]
   :states     [:submitted :complement-needed]}
  [{:keys [application created user]:as command}]
  (let [application (meta-fields/enrich-with-link-permit-data application)
        application (if-let [kuntalupatunnus (fetch-linked-kuntalupatunnus application)]
                      (update-in application
                                 [:linkPermitData]
                                 conj {:id kuntalupatunnus
                                       :type "kuntalupatunnus"})
                      application)
        submitted-application (mongo/by-id :submitted-applications id)
        app-updates {:modified created
                     :sent created
                     :authority (if (domain/assigned? application) (:authority application) (user/summary user))
                     :state :sent}
        organization (organization/get-organization (:organization application))
        indicator-updates (application/mark-indicators-seen-updates application user created)
        file-ids (ah/save-as-asianhallinta application lang submitted-application organization) ; Writes to disk
        attachments-updates (or (attachment/create-sent-timestamp-update-statements (:attachments application) file-ids created) {})
        transfer (get-transfer-item :exported-to-asianhallinta command)]
    (update-application command
                        {$push {:transfers transfer}
                         $set (util/deep-merge app-updates attachments-updates indicator-updates)})
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
    (when-not (= (:type (last filtered-transfers)) "to-asianhallinta"))
    (fail :error.application.not-in-asianhallinta)))

(defcommand attachments-to-asianhallinta
  {:parameters [id lang attachmentIds]
   :user-roles #{:authority}
   :pre-checks [has-asianhallinta-operation (application-already-exported :exported-to-asianhallinta)]
   :states     [:verdictGiven :sent]
   :description "Sends such selected attachments to backing system that are not yet sent."}
  [{:keys [created application user] :as command}]

  (let [attachments-wo-sent-timestamp (filter
                                        #(and
                                          (-> % :versions count pos?)
                                          (or
                                            (not (:sent %))
                                            (> (-> % :versions last :created) (:sent %)))
                                          (not (#{"verdict" "statement"} (-> % :target :type)))
                                          (some #{(:id %)} attachmentIds))
                                        (:attachments application))
        transfer (get-transfer-item :exported-to-asianhallinta command attachments-wo-sent-timestamp)]
    (if (pos? (count attachments-wo-sent-timestamp))
      (let [application (meta-fields/enrich-with-link-permit-data application)
            application (update-kuntalupatunnus application)
            sent-file-ids (ah/save-as-asianhallinta-asian-taydennys application attachments-wo-sent-timestamp lang)
            data-argument (attachment/create-sent-timestamp-update-statements (:attachments application) sent-file-ids created)]
        (update-application command {$push {:transfers transfer}
                                     $set data-argument})
        (ok))
      (fail :error.sending-unsent-attachments-failed))))
