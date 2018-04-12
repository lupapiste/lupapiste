(ns lupapalvelu.application-replace-operation
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.document.document-api :as doc-api]
            [lupapalvelu.organization :as org]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [lupapalvelu.domain :as domain]))

(defn- get-operation-by-key [application key value]
  (->> application
       (app/get-operations)
       (filter #(-> % key (= value)))
       (first)))

(defn- change-primary-operation-if-needed [command primary-op? app-id new-op-id]
  (if primary-op?
    (do (app/change-primary-operation command app-id new-op-id)
        (assoc command :application (domain/get-application-no-access-checking app-id)))
    command))

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

(defn- coll-contains-not-type? [coll att]
  (not (some #(= (:type %) (:type att)) coll)))

(defn- updated-new-op-attachments [empty-attachments uploaded-attachments]
  (loop [atts empty-attachments
         acc  uploaded-attachments]
    (let [att (first atts)]
      (cond
        (empty? atts) acc
        (coll-contains-not-type? acc att) (recur (rest atts) (cons att acc))
        :else (recur (rest atts) acc)))))

(defn- remove-new-attachment-duplicates [new-op attachments]
  (let [new-op-attachments         (filter (partial single-operation-attachment? new-op) attachments)
        rest-attachments           (remove (partial single-operation-attachment? new-op) attachments)
        uploaded-attachments       (filter non-empty-attachment? new-op-attachments)
        empty-attachments          (remove non-empty-attachment? new-op-attachments)
        updated-new-op-attachments (updated-new-op-attachments empty-attachments uploaded-attachments)]
    (concat rest-attachments updated-new-op-attachments)))

(defn- required-attachments-for-operations [operations organization]
  (->> operations
       (map (partial org/get-organization-attachments-for-operation organization))
       (apply concat)
       (map (fn [[type-group type-id]] {:type-group type-group :type-id type-id}))
       (set)))

(defn- remove-not-needed-attachment-templates [application attachments]
  (let [all-operations       (cons (:primaryOperation application) (:secondaryOperations application))
        organization         (org/get-organization (:organization application))
        required-attachments (required-attachments-for-operations all-operations organization)
        attachment-needed?   (fn [att] (or (non-empty-attachment? att)
                                           (required-attachments (:type att))))]
    (filter attachment-needed? attachments)))

(defn- move-attachments-from-old-op-to-new [{application :application :as command} app-id new-op old-op]
  (let [attachments             (:attachments application)
        new-op-att-types        (get-existing-operation-attachment-types attachments new-op)
        replace-old-op-with-new (fn [att] (if (and (single-operation-attachment? old-op att)
                                                   (some? (new-op-att-types (:type att))))
                                            (replace-op-in-attachment att old-op new-op)
                                            att))
        updated-attachments     (->> attachments
                                     (map replace-old-op-with-new)
                                     (remove-new-attachment-duplicates new-op)
                                     (remove-not-needed-attachment-templates application))]
    (action/update-application command {$set {:attachments updated-attachments}})
    (assoc command :application (domain/get-application-no-access-checking app-id))))

(defn replace-operation
  [{{app-id :id :as application} :application created :created :as command} op-id operation]
  (app/add-operation command app-id operation)
  (let [primary-op? (= op-id (-> application :primaryOperation :id))
        updated-app (domain/get-application-no-access-checking app-id)
        new-op      (get-operation-by-key updated-app :created created)
        old-op      (get-operation-by-key updated-app :id op-id)
        old-op-doc  (domain/get-document-by-operation updated-app old-op)]
    (-> (assoc command :application updated-app)
        (change-primary-operation-if-needed primary-op? app-id (:id new-op))
        (move-attachments-from-old-op-to-new app-id new-op old-op)
        (doc-api/do-remove-doc! old-op-doc app-id (:id old-op-doc)))))
