(ns lupapalvelu.appeal-api
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.appeal :as appeal]
            [lupapalvelu.appeal-common :as appeal-common]
            [lupapalvelu.appeal-schema :as appeal-schema]
            [lupapalvelu.appeal-verdict :as appeal-verdict]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.appeal :as att-appeal]
            [lupapalvelu.attachment.bind :as bind]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.verdict :as pate-verdict]
            [lupapalvelu.states :as states]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.vetuma :as vetuma]
            [monger.operators :refer [$elemMatch $push $set]]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema.core :as sc]))

(defn- latest-for-verdict?
  "True if the appeal-item (the first param) is later than any of the
  appeal-items. The check is limited to the same verdict."
  [{:keys [datestamp target-verdict id]} appeal-items]
  (let [filtered (filter #(and (not= id (:id %))
                               (= target-verdict (:target-verdict %))) appeal-items)]
    (util/is-latest-of? datestamp (map :datestamp filtered))))

(defn- appeal-item-editable?
  "Appeal-item can be either appeal or appealVerdict. Appeal is
  editable if it is later than the latest appealVerdict within the
  same verdict. AppealVerdict is only editable if it is latest
  appeal-item within the verdict."
  [{:keys [appealVerdicts appeals]} appeal-item]
  (let [latest-verdict? (latest-for-verdict? appeal-item appealVerdicts)
        latest-appeal?  (latest-for-verdict? appeal-item appeals)]
    (if (= (keyword (:type appeal-item)) :appealVerdict)
      (and latest-verdict? latest-appeal?)
      latest-verdict?)))

(defn new-appeal-data-mongo-updates
  "Returns $push mongo update map of data to given 'collection' property.
  Trusts that `data` is validated by the caller.
  For rectifications an id is generated according to a running number sequence."
  [application collection {:keys [type] :as data}]
  (let [rect-id (when (= "rectification" type)
                  (appeal/next-rectification-id application))
        data    (cond-> data
                  rect-id (assoc :rectification-id rect-id))]
    {:mongo-updates {$push {(keyword collection) data}}}))

(defn update-appeal-data-mongo-updates
  "Generates query and updates for given update-data into given collection.
   Query is $elemMatch to provided matching-id as 'id'.
   Map with mongo query and updates is returned.
   Trusts that `data` is validated by the caller."
  [collection matching-id update-data]
  {:mongo-query   {(keyword collection) {$elemMatch {:id matching-id}}}
   :mongo-updates {$set (zipmap
                          (map #(str (name collection) ".$." (name %)) (keys update-data))
                          (vals update-data))}})

(defn- appeal-item-attribute [appeal-type]
  (case (keyword appeal-type)
    :appealVerdict :appealVerdicts
    (:appeal :rectification) :appeals))

(defn- process-appeal
  "Process appeal for frontend"
  [application appeal-item]
  (assoc appeal-item :editable (appeal-item-editable? application appeal-item)))

(defn- add-attachments [{:keys [attachments]} {id :id :as appeal}]
  (->> (filter #(= id (get-in % [:target :id])) attachments)
       (map :latestVersion)
       (map #(select-keys % [:fileId :filename :contentType :size]))
       (into [])
       (assoc appeal :files)))

(def ^:private valid-frontend-appeal-verdict! (sc/validator appeal-verdict/FrontendAppealVerdict))
(def ^:private valid-frontend-appeal! (sc/validator appeal-schema/FrontendAppeal))

(def object-id-str-checker (sc/checker ssc/ObjectIdStr))

(defn- validate-output-format
  "Validate output data for frontend. Logs as ERROR in action pipeline."
  [_ response]
  (assert (contains? response :data))
  (let [data (:data response)]
    (assert (or (empty? data) (not-every? object-id-str-checker (keys data))) "Verdict IDs as ObjectID strings")
    (doseq [appeal (flatten (vals data))] ; Validate appeals/appeal-verdicts against
      (case (keyword (:type appeal))
        :appealVerdict (valid-frontend-appeal-verdict! appeal)
        (:appeal :rectification) (valid-frontend-appeal! appeal)))))

(defquery appeals
  {:description "Query for frontend, that returns all appeals/appeal verdicts of application in pre-processed form."
   :parameters  [id]
   :user-roles  #{:authority :applicant}
   :states      states/post-verdict-states
   :on-success  validate-output-format}
  [{application :application}]
  (let [appeal-verdicts   (map #(assoc % :type "appealVerdict") (:appealVerdicts application))
        all-appeals       (concat (:appeals application) appeal-verdicts)
        processed-appeals (->> all-appeals
                               (map (comp (partial add-attachments application)
                                          (partial process-appeal application)))
                               (sort-by :datestamp))]
    (ok :data (group-by :target-verdict processed-appeals))))

;; ------------------------
;; Pate appeals
;; ------------------------

(defn- common-sanity-checks
  "If appeal-id is given it must exist. The id can denote appeal verdict
  as well. For appealverdicts, at least one appeal must exist for the
  same verdict. If upsert? is true then the checks are used for upsert
  operation otherwise for delete."
  [upsert?]
  (fn [{{:keys [type appeal-id verdict-id]}              :data
        {:keys [appeals appealVerdicts] :as application} :application}]
    (let [type (or type
                   (when (util/find-by-id appeal-id appeals)
                     :appeal)
                   (when (util/find-by-id appeal-id appealVerdicts)
                     :appealVerdict))]
      (if (util/=as-kw type :appealVerdict)
        (let [appeal-verdict (util/find-by-id appeal-id appealVerdicts)]
          (or
            (when-not (or (nil? appeal-id) appeal-verdict)
              (fail :error.unknown-appeal-verdict))
            (when-not (or (not upsert?)
                          (util/find-by-key :target-verdict verdict-id appeals))
              (fail :error.appeals-not-found))
            (when-not (or (nil? appeal-id)
                          (appeal-item-editable? application
                                                 (assoc appeal-verdict :type type)))
              (fail :error.appeal-already-exists))))
        (let [appeal (util/find-by-id appeal-id appeals)]
          (or
            (when-not (or (nil? appeal-id) appeal)
              (fail :error.unknown-appeal))
            (when-not (or (nil? appeal-id)
                          (appeal-item-editable? application
                                                 (assoc appeal :type type)))
              (fail :error.appeal-verdict-already-exists))
            (when-not (or (not upsert?)
                          (nil? appeal-id)
                          (util/=as-kw type (:type appeal)))
              (fail :error.appeal-type-change-denied))))))))

(defn- attachments-for-file-ids [{:keys [attachments]} file-ids]
  (filter #(some->> % :latestVersion :fileId
                    (util/includes-as-kw? file-ids))
          attachments))

(defn- check-deleted-file-ids [{:keys [application data]}]
  (let [delete-ids (some-> data :deleted-file-ids seq)
        appeal-id  (:appeal-id data)]
    (when delete-ids
      (let [attachments (attachments-for-file-ids application
                                                  delete-ids)]
        (when (or (not= (count attachments)
                        (count delete-ids))
                  (nil? appeal-id)
                  (some (fn [{:keys [target]}]
                          (not= (:id target) appeal-id))
                        attachments))
          (fail :error.file-cannot-be-deleted))))))

(defn- pate-appeal-item-update!
  "Runs appeal-item updates to application. Appeal-item is either appeal or appeal verdict.
   Parameters:
     command
     appeal-id - appeal-id parameter from command (nil if creation of new appeal, else ID of the update subject)
     appeal-item - the data of the appeal item to be updated (schema: Appeal or AppealVerdict)
     appeal-type - appeal/rectification/appealVerdict
     filedatas - Attachment filedatas
  Note: attachment updates are handled separately."
  [command appeal-id appeal-item appeal-type filedatas]
  (let [collection (appeal-item-attribute appeal-type)
        updates    (if appeal-id
                     (update-appeal-data-mongo-updates collection appeal-id appeal-item)
                     (new-appeal-data-mongo-updates (:application command) collection appeal-item))]
    (action/update-application
      command
      (:mongo-query updates)
      (:mongo-updates updates))
    (when (seq filedatas)
      (bind/make-bind-job command :attachment
                          (map (fn [fd]
                                 (assoc fd :target {:id   (or appeal-id (:id appeal-item))
                                                    :type appeal-type}))
                               filedatas)))))

(defcommand upsert-pate-appeal
  {:description         "Upserts appeal or appealVerdict. Returns bind job state in :job"
   :categories          #{:pate-verdicts}
   :parameters          [id verdict-id type author datestamp filedatas]
   :optional-parameters [text appeal-id deleted-file-ids]
   :user-roles          #{:authority}
   :states              states/post-verdict-states
   :input-validators    [(partial action/number-parameters [:datestamp])]
   :pre-checks          [(action/some-pre-check pate-verdict/backing-system-verdict
                                                (pate-verdict/verdict-exists :published? :not-replaced?))
                         (common-sanity-checks true)
                         check-deleted-file-ids]}
  [{:keys [application] :as command}]
  ;; Update appeals/appealVerdicts
  (let [job (if (util/=as-kw type :appealVerdict)
              (if-let [verdict-data (appeal-verdict/appeal-verdict-data-for-upsert verdict-id author
                                                                                   datestamp text appeal-id)]
                (pate-appeal-item-update! command appeal-id verdict-data :appealVerdict filedatas)
                (fail! :error.invalid-appeal-verdict))
              (if-let [appeal-data (appeal/appeal-data-for-upsert verdict-id type
                                                                  author datestamp text appeal-id)]
                (pate-appeal-item-update! command appeal-id appeal-data (:type appeal-data) filedatas)
                (fail! :error.invalid-appeal)))]
    ;; Delete attachments if needed
    (when (and (seq deleted-file-ids) appeal-id)
      (att/delete-attachments! application
                               (map :id (attachments-for-file-ids application deleted-file-ids))))
    (ok :job job)))

(defcommand delete-pate-appeal
  {:description      "Deletes appeal or appeal verdict."
   :categories       #{:pate-verdicts}
   :parameters       [id verdict-id appeal-id]
   :user-roles       #{:authority}
   :input-validators [(partial action/non-blank-parameters [:appeal-id])]
   :states           states/post-verdict-states
   :pre-checks       [(action/some-pre-check (pate-verdict/verdict-exists :published? :not-replaced?)
                                             pate-verdict/backing-system-verdict)
                      (common-sanity-checks false)]}
  [command]
  (appeal-common/delete-by-id command appeal-id)
  (ok))

(defn- referenced-file-can-be-attached
  [{{files :files} :data}]
  (when-not (->> files
                 (map #(storage/unlinked-file-exists? (vetuma/session-id) (:fileId %)))
                 (every? true?))
    (fail :error.invalid-files-attached-to-appeal)))

(notifications/defemail :verdict-appeal
                        {:recipients-fn notifications/from-user
                         :subject-key   "verdict-appeal"
                         :model-fn      notifications/create-app-model})

(defn- bulletin-appeal-enabled
  "Fails if either bulletin is not found or bulletin appeals are _explicitly_ disallowed."
  [{data :data}]
  (if-let [bid (:bulletin-id data)]
    (when-not (some-> (bulletins/get-bulletin bid [:versions])
                      :versions first
                      bulletins/bulletin-appeal-enabled?)
      (fail :error.appeal-not-enabled))
    (fail :error.bulletin.not-found)))

(defcommand add-bulletin-appeal
  {:description      "Add new appeal thru a bulletin."
   :parameters       [bulletin-id text]
   :user-roles       #{:anonymous}
   :input-validators [(partial action/non-blank-parameters [:bulletin-id :text])
                      action/email-validator]
   :pre-checks       [vetuma/session-pre-check
                      referenced-file-can-be-attached
                      bulletin-appeal-enabled]
   :notified         true}
  [{{:keys [files email]} :data
    :keys                 [created system]
    :as                   command}]
  (if-let [bulletin-version (bulletins/bulletin-version-for-user command (bulletins/get-bulletin bulletin-id))]
    (let [verdict-id                   (-> bulletin-version :verdicts first :id)
          application                  (domain/get-application-no-access-checking (:application-id bulletin-version))
          _                            (when-not (states/post-verdict-states (-> application :state keyword))
                                         (fail! :error.command-illegal-state :state (:state application)))
          appellant                    (-> (vetuma/vetuma-session)
                                           :user
                                           (assoc :email email))
          command                      (-> (action/application->command application (assoc appellant :id (vetuma/session-id)))
                                           (assoc :created created))
          appeal-type                  :rectification
          appeal-data                  (appeal/create-bulletin-appeal application verdict-id appeal-type appellant created text email)
          appeal-key                   (appeal-item-attribute appeal-type)
          appeal-updates               {appeal-key appeal-data}
          att-updates                  (att-appeal/new-appeal-attachment-updates! command (:id appeal-data) appeal-type (map :fileId files))
          organization                 (org/get-application-organization application)
          {:keys [notification-email]} (org/bulletin-settings-for-scope organization
                                                                        (:permitType application)
                                                                        (:municipality application))]
      (if (appeal-schema/appeal-checker appeal-data)
        (fail :error.validator)
        (do (action/update-application
              command
              (util/deep-merge (app-state/state-transition-update :appealed created application appellant)
                               {$push (merge appeal-updates att-updates)}))
            (when notification-email
              (notifications/notify! :verdict-appeal {:application application
                                                      :system      system
                                                      :user        {:email notification-email
                                                                    :role  "authority"}}))))
      (ok :id appeal-data))
    (fail :error.not-found)))
