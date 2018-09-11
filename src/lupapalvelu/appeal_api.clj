(ns lupapalvelu.appeal-api
  (:require [clojure.set :refer [difference rename-keys]]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.appeal :as appeal]
            [lupapalvelu.appeal-common :as appeal-common]
            [lupapalvelu.appeal-verdict :as appeal-verdict]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.appeal :as att-appeal]
            [lupapalvelu.attachment.bind :as bind]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.pate.verdict :as pate-verdict]
            [lupapalvelu.states :as states]
            [monger.operators :refer [$push $pull $elemMatch $set]]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema.core :as sc]))

(defn- verdict-exists
  "Pre-check to validate that for selected verdictId a verdict exists"
  [{{verdictId :verdictId} :data {:keys [verdicts]} :application}]
  (when verdictId
    (when-not (util/find-first #(= verdictId (:id %)) verdicts)
      (fail :error.verdict-not-found))))

(defn- appeal-exists
  "Pre-check to validate that at least one appeal exists before appeal verdict can be created"
  [{{:keys [verdictId]} :data {:keys [appeals]} :application}]
  (when verdictId
    (when (zero? (count (filter #(= verdictId (:target-verdict %)) appeals)))
     (fail :error.appeals-not-found))))

(defn- appeal-id-exists
  "Pre-check to validate that given ID exists in application"
  [{{appeal-id :appealId} :data {:keys [appeals]} :application}]
  (when appeal-id ; optional parameter, could be nil in command
    (when-not (util/find-by-id appeal-id appeals)
      (fail :error.unknown-appeal))))

(defn- appeal-verdict-id-exists
  "Pre-check to validate that id from parameters exist in :appealVerdicts"
  [{{appeal-id :appealId} :data {:keys [appealVerdicts]} :application}]
  (when appeal-id
    (when-not (util/find-by-id appeal-id appealVerdicts)
      (fail :error.unknown-appeal-verdict))))

(defn- deny-type-change
  "Pre-check: when appeal is updated, type of appeal can't be changed"
  [{{appeal-id :appealId type :type} :data {:keys [appeals]} :application}]
  (when appeal-id
    (when-some [appeal (util/find-by-id appeal-id appeals)]
      (when-not (= type (:type appeal))
        (fail :error.appeal-type-change-denied)))))

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
        latest-appeal? (latest-for-verdict? appeal-item appeals)]
    (if (= (keyword (:type appeal-item)) :appealVerdict)
      (and latest-verdict? latest-appeal?)
      latest-verdict?)))

(defn- appeal-editable?
  "Pre-check to check that appeal can be edited."
  [{{appeal-id :appealId} :data {:keys [appeals appealVerdicts] :as application} :application}]
  (when (and appeal-id appealVerdicts)
    (if-let [appeal (util/find-by-id appeal-id appeals)]
      (when-not (appeal-item-editable? application appeal)
        (fail :error.appeal-verdict-already-exists))
      (fail :error.unknown-appeal))))

(defn- appeal-verdict-editable?
  "Pre-check to check that appeal-verdict can be edited."
  [{{appeal-id :appealId} :data {:keys [appealVerdicts] :as application} :application}]
  (when appeal-id
    (if-let [appeal-verdict (util/find-by-id appeal-id appealVerdicts)]
      (when-not (appeal-item-editable? application (assoc appeal-verdict :type :appealVerdict))
        (fail :error.appeal-already-exists))
      (fail :error.unknown-appeal-verdict))))


(defn new-appeal-data-mongo-updates
  "Returns $push mongo update map of data to given 'collection' property.
   Does not validate appeal-data, validation must be taken care elsewhere."
  [collection data]
  {:mongo-updates {$push {(keyword collection) data}}})

(defn update-appeal-data-mongo-updates
  "Generates query and updates for given update-data into given collection.
   Query is $elemMatch to provided matching-id as 'id'.
   Map with mongo query and updates is returned.
   Does not validate appeal-data, validation must be taken care elsewhere."
  [collection matching-id update-data]
  {:mongo-query   {(keyword collection) {$elemMatch {:id matching-id}}}
   :mongo-updates {$set (zipmap
                          (map #(str (name collection) ".$." (name %)) (keys update-data))
                          (vals update-data))}})

(defn- appeal-item-collection [appeal-type]
  (case (keyword appeal-type)
    :appealVerdict           :appealVerdicts
    (:appeal :rectification) :appeals))

(defn- attachment-updates
  [{{:keys [attachments]} :application :as command} appeal-id appeal-type file-ids]
  (when appeal-id
    (let [appeal-attachments  (filter
                                (fn [{{target-id :id} :target}]
                                  (= target-id appeal-id))
                                attachments)
          ;; Note, new files might be converted to PDF/A. After conversion initial file id is saved to originalFileId.
          ;; Converted file is given new fileId. After frontend has upserted appeal, next time in editing it will use
          ;; fileIds of the converted versions. Thus we are interested only in fileIds here. OriginalFileId is saved
          ;; in attachment's version data.
          appeal-file-ids         (map (util/fn-> :latestVersion :fileId) appeal-attachments)
          new-file-ids            (difference (set file-ids) (set appeal-file-ids))
          new-attachment-updates  (att-appeal/new-appeal-attachment-updates! command appeal-id appeal-type new-file-ids)
          removable-file-ids      (difference (set appeal-file-ids) (set file-ids))
          removable-attachments   (filter
                                    (fn [{versions :versions}] (some removable-file-ids (map :fileId versions)))
                                    appeal-attachments)]
      {:new-updates new-attachment-updates
       :new-file-ids new-file-ids
       :removable-attachment-ids (remove nil? (map :id removable-attachments))})))

(defn- appeal-item-update!
  "Runs appeal-item updates to application. Appeal-item is either appeal or appeal verdict.
   Parameters:
     command
     appealId - appealId parameter from command (nil if creation of new appeal, else ID of the update subject)
     appeal-item - the data of the appeal item to be updated (schema: Appeal or AppealVerdict)
     appeal-type - appeal/rectification/appealVerdict
     fileIds - the fileIds associated with appeal/appeal verdict"
  [{app :application :as command} appealId appeal-item appeal-type fileIds]
  (let [collection (appeal-item-collection appeal-type)
        updates (if appealId
                  (update-appeal-data-mongo-updates collection appealId appeal-item)
                  (new-appeal-data-mongo-updates collection appeal-item))
        {:keys [new-updates
                removable-attachment-ids]} (attachment-updates command (or (:id appeal-item) appealId) appeal-type fileIds)]
    (action/update-application
      command
      (:mongo-query updates)
      (util/deep-merge
        (:mongo-updates updates)
        new-updates))
    (when (seq removable-attachment-ids)
      (att/delete-attachments! (domain/get-application-no-access-checking (:id app)) removable-attachment-ids))
    (ok)))

(defcommand upsert-appeal
  {:description "Creates new appeal if appealId is not given. Updates appeal with given parameters if appealId is given"
   :parameters          [id verdictId type appellant datestamp fileIds]
   :optional-parameters [text appealId]
   :user-roles          #{:authority}
   :states              states/post-verdict-states
   :input-validators    [appeal/input-validator
                         (partial action/number-parameters [:datestamp])]
   :pre-checks          [verdict-exists
                         appeal-id-exists
                         appeal-editable?
                         deny-type-change]}
  [command]
  (let [appeal-data (appeal/appeal-data-for-upsert verdictId type appellant datestamp text appealId)]
    (if appeal-data ; if data is valid
      (appeal-item-update! command appealId appeal-data (:type appeal-data) fileIds)
      (fail :error.invalid-appeal))))

(defcommand upsert-appeal-verdict
  {:parameters          [id verdictId giver datestamp fileIds]
   :optional-parameters [text appealId]
   :user-roles          #{:authority}
   :states              states/post-verdict-states
   :input-validators    [appeal-verdict/input-validator
                         (partial action/number-parameters [:datestamp])]
   :pre-checks          [verdict-exists
                         appeal-exists
                         appeal-verdict-id-exists
                         appeal-verdict-editable?]}
  [command]
  (let [verdict-data (appeal-verdict/appeal-verdict-data-for-upsert verdictId giver datestamp text appealId)]
    (if verdict-data
      (appeal-item-update! command appealId verdict-data :appealVerdict fileIds)
      (fail :error.invalid-appeal-verdict))))

(defcommand delete-appeal
  {:parameters          [id verdictId appealId]
   :user-roles          #{:authority}
   :input-validators    [(partial action/string-parameters [:appealId])]
   :states              states/post-verdict-states
   :pre-checks          [verdict-exists
                         appeal-id-exists
                         appeal-editable?]}
  [command]
  (appeal-common/delete-by-id command appealId))

(defcommand delete-appeal-verdict
  {:parameters          [id verdictId appealId]
   :user-roles          #{:authority}
   :input-validators    [(partial action/string-parameters [:appealId])]
   :states              states/post-verdict-states
   :pre-checks          [verdict-exists
                         appeal-verdict-id-exists
                         appeal-verdict-editable?]}
  [command]
  (appeal-common/delete-by-id command appealId))

(defn- process-appeal
  "Process appeal for frontend"
  [application appeal-item]
  (assoc appeal-item :editable (appeal-item-editable? application appeal-item)))

(defn- add-attachments [{:keys [attachments]} {id :id :as appeal}]
  (->> (filter #(= id (get-in % [:target :id])) attachments)
       (map :latestVersion)
       (map #(select-keys % [:fileId :filename :contentType :size]))
       (assoc appeal :files)))

(defn- validate-output-format
  "Validate output data for frontend. Logs as ERROR in action pipeline."
  [_ response]
  (assert (contains? response :data))
  (let [data (:data response)]
    (assert (or (empty? data) (not-every? #(sc/check ssc/ObjectIdStr %) (keys data))) "Verdict IDs as ObjectID strings")
    (doseq [appeal (flatten (vals data))] ; Validate appeals/appeal-verdicts against
      (case (keyword (:type appeal))
        :appealVerdict           (sc/validate appeal-verdict/FrontendAppealVerdict appeal)
        (:appeal :rectification) (sc/validate appeal/FrontendAppeal appeal)))))

(defquery appeals
  {:description "Query for frontend, that returns all appeals/appeal verdicts of application in pre-processed form."
   :parameters [id]
   :user-roles #{:authority :applicant}
   :states     states/post-verdict-states
   :on-success validate-output-format}
  [{application :application}]
  (let [appeal-verdicts (map #(assoc % :type "appealVerdict") (:appealVerdicts application))
        all-appeals     (concat (:appeals application) appeal-verdicts)
        processed-appeals (->> all-appeals
                               (map (comp (partial add-attachments application)
                                          (partial process-appeal application)))
                               (sort-by :datestamp))]
    (ok :data (group-by :target-verdict processed-appeals))))

;; ------------------------
;; Pate appeals
;; ------------------------

#_(defn- common-input-validator [{data :data}]
  (let [data (rename-keys data {:verdict-id :verdictId
                                :file-ids   :fileIds})]
    (if (util/=as-kw (:type data) :appealVerdict)
      (appeal-verdict/input-validator {:data (rename-keys data {:author :giver})})
      (appeal/input-validator {:data (rename-keys data {:author :appellant})}))))



(defn- common-sanity-checks
  "If appeal-id is given it must exist. The id can denote appeal verdict
  as well. For appealverdicts, at least one appeal must exist for the
  same verdict. If upsert? is true then the checks are used for upsert
  operation otherwise for delete."
  [upsert?]
  (fn [{{:keys [type appeal-id verdict-id]} :data
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
  [{app :application :as command} appeal-id appeal-item appeal-type filedatas]
  (let [collection (appeal-item-collection appeal-type)
        updates    (if appeal-id
                     (update-appeal-data-mongo-updates collection appeal-id appeal-item)
                     (new-appeal-data-mongo-updates collection appeal-item))]
    (action/update-application
     command
     (:mongo-query updates)
     (:mongo-updates updates))
    (when (seq filedatas)
      (bind/make-bind-job command
                          (map (fn [fd]
                                 (assoc fd :target {:id   (or appeal-id (:id appeal-item))
                                                    :type appeal-type}))
                               filedatas)))))

(defcommand upsert-pate-appeal
  {:description         "Upserts appeal or appealVerdict."
   :feature             :pate
   :categories          #{:pate-verdicts}
   :parameters          [id verdict-id type author datestamp filedatas]
   :optional-parameters [text appeal-id deleted-file-ids]
   :user-roles          #{:authority}
   :states              states/post-verdict-states
   :input-validators    [;;common-input-validator
                         (partial action/number-parameters [:datestamp])]
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
   :feature          :pate
   :categories       #{:pate-verdicts}
   :parameters       [id verdict-id appeal-id]
   :user-roles       #{:authority}
   :input-validators [(partial action/non-blank-parameters [:appeal-id])]
   :states           states/post-verdict-states
   :pre-checks       [(action/some-pre-check (pate-verdict/verdict-exists :published? :not-replaced?)
                                             pate-verdict/backing-system-verdict)
                      (common-sanity-checks false)]}
  [command]
  (appeal-common/delete-by-id command appeal-id))
