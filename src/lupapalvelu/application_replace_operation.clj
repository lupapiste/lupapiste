(ns lupapalvelu.application-replace-operation
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.operations :as op]
            [monger.operators :refer :all]
            [sade.core :refer :all]))

(defn- get-operation-schemas [organization op-name]
  (let [op-metadata (->> op-name (keyword) (get op/operations))
        org-required-schema-fn (:org-required op-metadata)]
    (concat (:required op-metadata)
            (when org-required-schema-fn ((apply juxt org-required-schema-fn) organization))
            [(:applicant-doc-schema op-metadata)])))

(defn- get-required-document-schema-names-for-operations [organization new-ops]
  (->> new-ops
       (map #(get-operation-schemas organization (:name %)))
       (apply concat)
       (set)))

(defn- replace-old-operation-doc-with-new [documents op-id new-docs]
  (let [match-doc-with-op-id (fn [doc] (#{op-id} (-> doc :schema-info :op :id)))
        new-operation-doc    (->> new-docs (filter match-doc-with-op-id) (first))]
    (->> documents
         (filter #(some? (-> % :schema-info :op :name)))
         (remove match-doc-with-op-id)
         (cons new-operation-doc))))

(defn- pick-old-documents-that-new-op-needs [new-op-docs documents doc-name]
  (let [existing-docs (filter #(= doc-name (-> % :schema-info :name)) documents)]
    (if (seq existing-docs)
      existing-docs
      (filter #(= doc-name (-> % :schema-info :name)) new-op-docs))))

(defn- copy-rakennuspaikka-data [old-docs new-docs]
  (let [rakennuspaikka-filter   #(and (-> % :schema-info :type (= :location))
                                      (-> % :schema-info :repeating (not)))
        old-rakennuspaikka      (->> old-docs (filter rakennuspaikka-filter) (first))
        new-rakennuspaikka      (->> new-docs (filter rakennuspaikka-filter) (first))
        new-data-keys           (-> new-rakennuspaikka :data (keys))
        rakennuspaikka-data     (when (and old-rakennuspaikka new-rakennuspaikka)
                                  (-> old-rakennuspaikka :data (select-keys new-data-keys)))
        updated-doc             (if rakennuspaikka-data
                                  (assoc new-rakennuspaikka :data (merge (:data new-rakennuspaikka) rakennuspaikka-data))
                                  new-rakennuspaikka)]
    (->> new-docs
         (remove rakennuspaikka-filter)
         (cons updated-doc))))

(defn- copy-docs-from-old-op-to-new
  "Copies the old documents to the new operation, when the old documents are of those types that the new operation
  requires. Changes the 'operation document' to that of the new operation. Takes into account that there might be
  several operations, and keeps track which documents are required by all the operations."
  [{:keys [documents]} organization op-id new-ops new-op-docs]
  (let [docs-for-new-ops (get-required-document-schema-names-for-operations organization new-ops)
        operation-docs   (replace-old-operation-doc-with-new documents op-id new-op-docs)]
    (->> docs-for-new-ops
         (map (partial pick-old-documents-that-new-op-needs new-op-docs documents))
         (apply concat)
         (copy-rakennuspaikka-data documents)
         (concat operation-docs)
         (remove nil?))))

(defn- copy-attachments-from-old-op-to-new [{:keys [attachments]} new-op new-attachments]
  (let [op-id (:id new-op)
        id-matcher (fn [op] (= (:id op) op-id))
        updated-old-attachments (for [att attachments]
                                  (let [attachment-operations (:op att)]
                                    (if (->> attachment-operations (filter id-matcher) (empty?))
                                      att
                                      (->> (remove id-matcher attachment-operations)
                                           (cons {:id op-id :name (:name new-op)})
                                           (assoc att :op)))))]
    (concat updated-old-attachments new-attachments)))

(defn- get-operation [application primary? op-id]
  (if primary?
    (:primaryOperation application)
    (->> application
         :secondaryOperations
         (filter #(= op-id (:id %)))
         (first))))

(defn- get-new-operations [application old-op-id new-op]
  (->> application
       (app/get-operations)
       (remove #(= old-op-id (:id %)))
       (cons new-op)))

(defn- build-replace-operation-query [application primary-op? new-op new-docs new-attachments]
  (let [base-query {:attachments new-attachments
                    :documents   new-docs}]
    (if primary-op?
      (assoc base-query :primaryOperation new-op)
      (assoc base-query :secondaryOperations (->> application
                                                  :secondaryOperations
                                                  (remove #(= (:id new-op) (:id %)))
                                                  (cons new-op))))))

(defn replace-operation
  "Replaces the old operation's name to the one requested. Then generates the necessary new documents and attachments
   and deletes the documents that aren't needed any more but keeps all the attachments."
  [{{app-state :state tos-function :tosFunction :as application} :application
    organization                                                 :organization
    created                                                      :created :as command} op-id operation]
  (let [primary-op?            (= op-id (-> application :primaryOperation :id))
        old-op                 (get-operation application primary-op? op-id)
        new-op                 (assoc old-op :name operation)
        new-operations         (get-new-operations application op-id new-op)
        docs-for-new-op        (app/make-documents nil created @organization new-op (dissoc application :documents))
        updated-docs           (copy-docs-from-old-op-to-new application organization op-id new-operations docs-for-new-op)
        old-attachment-types   (->> application :attachments (map :type))
        attachments-for-new-op (app/make-attachments created new-op @organization app-state tos-function :existing-attachments-types old-attachment-types)
        updated-attachments    (copy-attachments-from-old-op-to-new application new-op attachments-for-new-op)
        update-query           (build-replace-operation-query application
                                                              primary-op?
                                                              new-op
                                                              updated-docs
                                                              updated-attachments)]
    (action/update-application command {$set update-query})
    (ok)))
