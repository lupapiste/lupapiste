(ns lupapalvelu.document.persistence
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error fatal]]
            [clojure.string :refer [replace-first]]
            [clojure.string :as s]
            [monger.operators :refer :all]
            [sade.util :as util]
            [sade.core :refer [ok fail fail! unauthorized!]]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [update-application application->command] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.company :as company]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.user :as user]))

(defn- find-repeating-document [schema-name documents]
  (some #(and (= schema-name (get-in % [:schema-info :name]))
              (get-in % [:schema-info :repeating]))
        documents))

(defn by-id [application collection id]
  (let [docs ((keyword collection) application)]
    (some #(when (= (:id %) id) %) docs)))

(defn ->model-updates
  "Creates model-updates from ui-format."
  [updates] (for [[k v] updates] [(-> k (s/split #"\.") (->> (map keyword)) vec) v]))

(defn ->mongo-updates
  "Creates full paths to document update values to be $set.
   To be used within model/with-timestamp."
  [prefix updates meta]
  (reduce
    (fn [m [ks v]] (let [field (fn [x] (s/join "." (flatten [prefix (map name ks) x]))) ]
                     (-> m
                       (#(reduce (fn [m [k v]] (assoc m (field (name k)) v)) % meta))
                       (assoc (field "value") v)
                       (#(if (contains? meta :source) (assoc % (field "sourceValue") v) %))
                       (assoc (field "modified") (model/current-timestamp)))))
    {} updates))

(defn validated-model-updates
  "Returns a map with keys: :mongo-query, :mongo-updates, :post-results.
   Throws fail! if validation fails."
  [application collection document model-updates timestamp & meta-data]
  (model/with-timestamp timestamp
    (let  [pre-results  (model/validate application document)
           updated-doc  (model/apply-updates document model-updates)
           post-results (model/validate application updated-doc)]
      (when-not document (fail! :unknown-document))
      (when (model/has-errors? pre-results) (fail! :document-in-error-before-update :results pre-results))
      (when (model/has-errors? post-results) (fail! :document-would-be-in-error-after-update :results post-results))

      {:mongo-query   {collection {$elemMatch {:id (:id document)}}}
       :mongo-updates {$set (assoc
                              (->mongo-updates (str (name collection) ".$.data") model-updates (apply hash-map meta-data))
                              :modified timestamp)}
       :post-results  post-results
       :updated-doc   updated-doc})))

(defn get-after-update-trigger-fn [document]
  (let [schema (schemas/get-schema (:schema-info document))
        trigger-ref (get-in schema [:info :after-update])]
    (if trigger-ref
      (resolve trigger-ref)
      (constantly nil))))

(defn persist-model-updates [application collection document model-updates timestamp & meta-data]
  (let [command (application->command application)
        {:keys [mongo-query mongo-updates post-results updated-doc]} (apply validated-model-updates application collection document model-updates timestamp meta-data)
        updated-app (update-in application [(keyword collection)] (fn [c] (map #(if (= (:id %) (:id updated-doc)) updated-doc %) c)))
        trigger-fn (get-after-update-trigger-fn document)
        extra-updates (trigger-fn updated-app)]
    (update-application command mongo-query (util/deep-merge mongo-updates extra-updates))
    (ok :results post-results)))

(defn validate-collection [{{collection :collection} :data}]
  (when-not (#{"documents" "tasks"} collection)
    (fail :error.unknown-type)))

(defn validate-against-whitelist! [document model-updates user-role]
  (let [doc-schema            (model/get-document-schema document)
        schema-paths          (map first model-updates)
        get-subschema-by-name (fn [schema sub-schema-name]
                                (some
                                  (fn [schema-body]
                                    (when (= (:name schema-body) (name sub-schema-name))
                                      schema-body))
                                  (:body schema)))]
    (doseq [path schema-paths]
      (let [path          (remove (fn [item] (ss/numeric? (name item))) path)
            [_ whitelist] (reduce (fn [[subschema whitelist] path]
                                    (let [subschema (get-subschema-by-name subschema path)
                                          whitelist (or (:whitelist subschema) whitelist)]
                                      [subschema whitelist]))
                                  [doc-schema nil]
                                  path)]
        (when-not (or
                    (empty? whitelist)
                    (some #{(keyword user-role)} (:roles whitelist)))
          (unauthorized!))))))

(defn update! [{application :application timestamp :created {role :role} :user} doc-id updates collection]
  (let [document      (by-id application collection doc-id)
        model-updates (->model-updates updates)]
    (when-not document (fail! :error.document-not-found))
    (validate-against-whitelist! document model-updates role)
    (persist-model-updates application collection document model-updates timestamp)))

(defn remove! [{application :application timestamp :created :as command} doc-id collection]
  (let [document      (by-id application collection doc-id)
        updated-app (update-in application [:documents] (fn [c] (filter #(not= (:id %) doc-id) c)))
          trigger-fn ( get-after-update-trigger-fn document)
          extra-updates (trigger-fn updated-app)
          op-id (get-in document [:schema-info :op :id])]
    (when-not document (fail! :error.document-not-found))
    (update-application command
      (util/deep-merge
        extra-updates
        {$pull (merge
                 {:documents {:id doc-id}}
                 (when op-id
                   {:secondaryOperations {:id op-id}}))
         $set  (merge
                 {:modified timestamp}
                 (when op-id
                   (mongo/generate-array-updates
                     :attachments
                     (:attachments application)
                     #(= (:id (:op %)) op-id)
                     :op nil)))})))
  )

(defn do-create-doc [{{:keys [id schemaName]} :data created :created application :application :as command}]
  (let [schema (schemas/get-schema (:schema-version application) schemaName)]
    (when-not (:repeating (:info schema)) (fail! :illegal-schema))
    (let [document (model/new-document schema created)]
      (update-application command
        {$push {:documents document}
         $set {:modified created}})
      document)))

(defn- update-key-in-schema? [schema [update-key _]]
  (model/find-by-name schema update-key))

(defn set-subject-to-document [application document subject path timestamp]
  {:pre [(map? document) (map? subject)]}
  (when (seq subject)
    (let [path-arr     (if-not (ss/blank? path) (ss/split path #"\.") [])
          schema       (schemas/get-schema (:schema-info document))
          with-hetu    (model/has-hetu? (:body schema) path-arr)
          person       (tools/unwrapped (case (first path-arr)
                                          "henkilo" (model/->henkilo subject :with-hetu with-hetu :with-empty-defaults? true)
                                          "yritys" (model/->yritys subject :with-empty-defaults? true)
                                          (model/->henkilo subject :with-hetu with-hetu :with-empty-defaults? true)))
          model        (if (seq path-arr)
                         (assoc-in {:_selected (first path-arr)} (map keyword path-arr) person)
                         person)
          updates      (->> (tools/path-vals model)
                            ; Path should exist in schema!
                            (filter (partial update-key-in-schema? (:body schema))))]
      (when-not schema (fail! :error.schema-not-found))
      (debugf "merging user %s with best effort into %s %s with db %s" model (get-in document [:schema-info :name]) (:id document) mongo/*db-name*)
      (persist-model-updates application "documents" document updates timestamp))))

(defn do-set-user-to-document [application document-id user-id path timestamp]
  {:pre [application document-id timestamp]}
  (if-let [document (domain/get-document-by-id application document-id)]
    (when-not (ss/blank? user-id)
      (if-let [subject (user/get-user-by-id user-id)]
        (set-subject-to-document application document subject path timestamp)
        (fail! :error.user-not-found)))
    (fail :error.document-not-found)))

(defn do-set-company-to-document [application document company-id path user timestamp]
  {:pre [document]}
  (when-not (ss/blank? company-id)
    (let [path-arr (if-not (ss/blank? path) (s/split path #"\.") [])
          schema (schemas/get-schema (:schema-info document))
          subject (company/find-company-by-id company-id)
          company (tools/unwrapped (model/->yritys (merge subject user) :with-empty-defaults true))
          model (if (seq path-arr)
                  (assoc-in {} (map keyword path-arr) company)
                  company)
          updates (->> (tools/path-vals model)
                       (filter (partial update-key-in-schema? (:body schema))))]
      (when-not schema (fail! :error.schema-not-found))
      (when-not company (fail! :error.company-not-found))
      (when-not (and (domain/has-auth? application company-id) (domain/no-pending-invites? application company-id))
        (fail! :error.application-does-not-have-given-auth))
      (debugf "merging company %s into %s %s with db %s" model (get-in document [:schema-info :name]) (:id document) mongo/*db-name*)
      (persist-model-updates application "documents" document updates timestamp))))


