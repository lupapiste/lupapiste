(ns lupapalvelu.application-replace-operation
  (:require [clojure.set :as set]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.document.document-api :as doc-api]
            [lupapalvelu.document.waste-schemas :as waste-schemas]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]
            [lupapalvelu.organization :as org]
            [lupapalvelu.document.schemas :as schemas]
            [monger.operators :refer :all]
            [sade.core :refer :all]))

;;
;; Attachments
;;

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
        attachments               (:attachments updated-application)
        attachments-to-be-removed (concat (new-attachment-duplicates attachments new-op)
                                          (not-needed-templates @organization updated-application old-op))]
    (when (seq attachments-to-be-removed)
      (action/update-application command {$pull {:attachments {:id {$in attachments-to-be-removed}}}}))))

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

;;
;; Documents
;;


(defn get-document-schema-names-for-operation [organization operation]
  (let [op-info (op/operations (keyword (:name operation)))]
    (->> (when (not-empty (:org-required op-info)) ((apply juxt (:org-required op-info)) organization))
         (concat (:required op-info)))))

(defn- required-document-schema-names-by-op [organization application]
  (->> (cons (:primaryOperation application) (:secondaryOperations application))
       (reduce #(assoc %1 (keyword (:name %2)) (set (get-document-schema-names-for-operation organization %2))) {})))

(defn- copy-old-document-data [old-doc new-doc]
  (let [new-data-keys       (-> new-doc :data (keys))
        document-data (-> old-doc :data (select-keys new-data-keys))]
    (update-in new-doc [:data] merge document-data)))

(defn- create-document [doc-name application created & [old-document]]
  (let [op-name (:name (get-operation-by-key application :created created))
        document-schema (schemas/get-schema (:schema-version application) doc-name)
        new-document (app/make-document op-name created nil document-schema)]
    (if old-document
      (copy-old-document-data old-document new-document)
      new-document)))

(defn- update-documents
  "Because replace-operation uses existing add-operation/change-primary-operation/do-remove-doc! functionalities
  it is necessary to ensure that the application has the correct documents after replacing operation.
  This function checks that after replacement, there is no waste document if it isn't needed, and it
  replaces the location document with one of the correct type and preserves its data. However, it does not delete
  Paasuunnittelija-document because it is possible that the user has added it even though it isn't needed."
  [{created :created :as command} organization app-id]
  (let [application                             (domain/get-application-no-access-checking app-id)
        required-document-names-by-op           (required-document-schema-names-by-op organization application)
        required-document-names                 (apply set/union (vals required-document-names-by-op))

        existing-rakennusjatesuunnitelma        (or (domain/get-document-by-name application waste-schemas/basic-construction-waste-plan-name)
                                                    (domain/get-document-by-name application waste-schemas/extended-construction-waste-report-name))
        should-contain-rakennusjatesuunnitelma? (or (required-document-names waste-schemas/basic-construction-waste-plan-name)
                                                    (required-document-names waste-schemas/extended-construction-waste-report-name))

        location-document                       (->> (domain/get-documents-by-type application "location")
                                                     (remove #(-> % :schema-info :repeating))
                                                     (first))
        required-rakennuspaikka                 (or (required-document-names "rakennuspaikka")
                                                    (required-document-names "rakennuspaikka-ilman-ilmoitusta"))
        correct-rakennuspaikka-document?        (= required-rakennuspaikka (-> location-document :schema-info :name))

        new-rakennuspaikka-document             (when (and (not correct-rakennuspaikka-document?)
                                                           required-rakennuspaikka)
                                                  (create-document required-rakennuspaikka application created location-document))

        documents-to-be-removed                 (remove nil?
                                                        [(when (and new-rakennuspaikka-document
                                                                    (-> location-document :schema-info :repeating (not)))
                                                           (:id location-document))
                                                         (when (and existing-rakennusjatesuunnitelma
                                                                    (not should-contain-rakennusjatesuunnitelma?))
                                                           (:id existing-rakennusjatesuunnitelma))])]
    (when (seq documents-to-be-removed)
      (action/update-application command {$pull {:documents {:id {$in documents-to-be-removed}}}}))
    (when new-rakennuspaikka-document
      (action/update-application command {$push {:documents new-rakennuspaikka-document }}))))

;;
;;
;;

(defn replace-operation
  [{{app-id :id :as application} :application org :organization created :created :as command} op-id operation]
  (app/add-operation command app-id operation)
  (let [organization    @org
        primary-op?     (= op-id (-> application :primaryOperation :id))
        updated-app     (domain/get-application-no-access-checking app-id)
        new-op          (get-operation-by-key updated-app :created created)
        old-op          (get-operation-by-key updated-app :id op-id)
        old-op-doc      (domain/get-document-by-operation updated-app old-op)
        updated-command (assoc command :application updated-app)
        new-ops         (when primary-op? (app/change-primary-operation updated-command (:id new-op)))
        updated-app     (merge (:application updated-command) new-ops)
        updated-command (assoc updated-command :application updated-app)]
    (move-attachments-from-old-op-to-new updated-command new-op old-op)
    (remove-not-needed-attachment-templates updated-command new-op old-op)
    (doc-api/do-remove-doc! updated-command old-op-doc app-id (:id old-op-doc))
    (update-documents updated-command organization app-id)))
