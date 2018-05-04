(ns lupapalvelu.application-replace-operation
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.document.document-api :as doc-api]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [lupapalvelu.domain :as domain]))

(defn get-operation-by-key [application key value]
  (->> application
       (app/get-operations)
       (filter #(-> % key (= value)))
       (first)))

(defn- op-id-matcher [op-id op]
  (= op-id (:id op)))

(defn- replace-op-in-attachment [attachment old-op new-op]
  (->> (:op attachment)
       (remove (partial op-id-matcher (:id old-op)))
       (cons {:id (:id new-op) :name (:name new-op)})
       (assoc attachment :op)))

(defn- single-operation-attachment? [op-to-match attachment]
  (let [ops (:op attachment)]
    (and (op-id-matcher (:id op-to-match) (first ops))
         (not (att-type/multioperation? (:type attachment))))))

(defn- get-existing-operation-attachment-types [attachments operation]
  (->> attachments
       (filter (partial single-operation-attachment? operation))
       (map :type)
       (set)))

(defn- non-empty-attachment? [att]
  (not (-> att :versions (empty?))))

(defn- coll-contains-type? [coll att]
  (some #(= (:type %) (:type att)) coll))

(defn- required-attachments-for-operations [operations organization]
  (->> operations
       (map (partial org/get-organization-attachments-for-operation organization))
       (apply concat)
       (map (fn [[type-group type-id]] {:type-group type-group :type-id type-id}))
       (set)))

(defn- new-attachment-duplicates [attachments new-op]
  (let [new-op-attachments   (filter (partial single-operation-attachment? new-op) attachments)
        uploaded-attachments (filter non-empty-attachment? new-op-attachments)
        empty-attachments    (remove non-empty-attachment? new-op-attachments)
        empty-duplicates     (filter (partial coll-contains-type? uploaded-attachments) empty-attachments)]
    (map :id empty-duplicates)))

(defn- not-needed-templates [organization application {old-op-id :id}]
  (let [all-operations       (->> (cons (:primaryOperation application) (:secondaryOperations application))
                                  (remove (partial op-id-matcher old-op-id)))
        attachments          (:attachments application)
        required-attachments (required-attachments-for-operations all-operations organization)
        attachment-needed?   (fn [att] (or (non-empty-attachment? att)
                                           (required-attachments (:type att))))]
    (->> attachments
         (remove attachment-needed?)
         (map :id))))

(defn- remove-not-needed-attachment-templates [{application :application organization :organization :as command} new-op old-op]
  (let [updated-application       (domain/get-application-no-access-checking (:id application))
        updated-command           (assoc command :application updated-application)
        attachments               (:attachments updated-application)
        attachments-to-be-removed (concat (new-attachment-duplicates attachments new-op)
                                          (not-needed-templates @organization updated-application old-op))]
    (when (seq attachments-to-be-removed)
      (action/update-application updated-command {$pull {:attachments {:id {$in attachments-to-be-removed}}}}))))

(defn- replace-operation-in-attachment? [new-op-att-types old-op attachment]
  (and (single-operation-attachment? old-op attachment)
       (some? (new-op-att-types (:type attachment)))))

(defn- replace-old-op-with-new-updates [attachments old-op new-op]
  (let [new-op-att-types (get-existing-operation-attachment-types attachments new-op)]
    (mongo/generate-array-updates :attachments
                                  attachments
                                  (partial replace-operation-in-attachment? new-op-att-types old-op)
                                  :op [{:id (:id new-op) :name (:name new-op)}])))

(defn- move-attachments-from-old-op-to-new [{application :application :as command} new-op old-op]
  (let [replace-updates (replace-old-op-with-new-updates (:attachments application) old-op new-op)]
    (when (seq replace-updates) (action/update-application command {$set replace-updates}))))

(defn replace-operation
  [{{app-id :id :as application} :application created :created :as command} op-id operation]
  (app/add-operation command app-id operation)
  (let [primary-op?     (= op-id (-> application :primaryOperation :id))
        updated-app     (domain/get-application-no-access-checking app-id)
        new-op          (get-operation-by-key updated-app :created created)
        old-op          (get-operation-by-key updated-app :id op-id)
        old-op-doc      (domain/get-document-by-operation updated-app old-op)
        updated-command (assoc command :application updated-app)
        new-ops         (when primary-op? (app/change-primary-operation updated-command app-id (:id new-op)))
        updated-app     (merge (:application updated-command) new-ops)
        updated-command (assoc updated-command :application updated-app)]
    (move-attachments-from-old-op-to-new updated-command new-op old-op)
    (remove-not-needed-attachment-templates updated-command new-op old-op)
    (doc-api/do-remove-doc! updated-command old-op-doc app-id (:id old-op-doc))))
