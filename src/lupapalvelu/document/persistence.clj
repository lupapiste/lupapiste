(ns lupapalvelu.document.persistence
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error fatal]]
            [clojure.string :refer [replace-first]]
            [clojure.string :as s]
            [monger.operators :refer :all]
            [sade.util :as util]
            [sade.core :refer [ok fail fail! unauthorized!]]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [update-application application->command] :as action]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.company :as company]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]))

(defn by-id [application collection id]
  (let [docs ((keyword collection) application)]
    (some #(when (= (:id %) id) %) docs)))

(defn ->model-updates
  "Creates model-updates from ui-format."
  [updates]
  (for [[k v] updates]
    (let [keys (mapv util/->keyword (if (coll? k) k (s/split k #"\.")))]
      [keys v])))

(defn data-model->model-updates
  "Creates model updates from data returned by mongo query"
  [path data-model]
  (if (contains? data-model :value)
    [[path (:value data-model)]]
    (->> (filter (comp map? val) data-model)
         (mapcat (fn [[k m]] (data-model->model-updates (conj path (keyword k)) m))))))

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

(defn get-after-update-trigger-fn [document]
  (let [schema (schemas/get-schema (:schema-info document))
        trigger-ref (get-in schema [:info :after-update])]
    (if trigger-ref
      (resolve trigger-ref)
      (constantly nil))))

(defn after-update-triggered-updates [application collection original-doc updated-doc]
  (->> (update application (keyword collection) (partial util/replace-by-id updated-doc))
       ((get-after-update-trigger-fn original-doc))))

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
       :mongo-updates (util/deep-merge
                       {$set (assoc
                              (->mongo-updates (str (name collection) ".$.data") model-updates (apply hash-map meta-data))
                              :modified timestamp)}
                       (after-update-triggered-updates application collection document updated-doc))
       :post-results  post-results})))

(defmulti transform-trimmed-value (fn [transform _] (keyword transform)))

(defmethod transform-trimmed-value :default [_ value] value)

(defmethod transform-trimmed-value :upper-case
  [_ value]
  (ss/upper-case value))

(defmethod transform-trimmed-value :lower-case
  [_ value]
  (ss/lower-case value))

(defmethod transform-trimmed-value :zero-pad-4
  [_ value]
  (if (and value (re-matches #"[\s0-9]+" value))
    (try
      (->> value ss/trim Long/parseLong (format "%04d"))
      (catch Exception _ value))
    value))

(defn- trim-value [value]
  (if (string? value)
    (ss/trim value)
    value))

(defn transform-value [transform value]
  (transform-trimmed-value transform (trim-value value)))


(defn- transform
  "Processes model updates with the schema-defined transforms if defined."
  [document updates]
  (let [doc-schema (model/get-document-schema document)]
    (for [[path value] updates
          :let [{transform :transform} (model/find-by-name (:body doc-schema) path)]]
      [path (transform-value transform value)])))

(defn persist-model-updates [application collection document model-updates timestamp & meta-data]
  (let [command (application->command application)
        {:keys [mongo-query mongo-updates post-results]} (apply validated-model-updates application collection document
                                                                (transform document model-updates) timestamp meta-data)]
    (update-application command mongo-query mongo-updates)
    (ok :results post-results)))

(defn validate-collection [{{collection :collection} :data}]
  (when-not (#{"documents" "tasks"} collection)
    (fail :error.unknown-type)))

(defn validate-against-whitelist! [document update-paths user-role]
  (let [doc-schema (model/get-document-schema document)]
    (doseq [path update-paths]
      (let [{whitelist :whitelist} (model/find-by-name (:body doc-schema) path)]
        (when-not (or (empty? whitelist)
                      (some #{(keyword user-role)} (:roles whitelist)))
          (unauthorized!))))))

(defn- sent? [{state :state}]
  (and state (= "sent" (name state))))

(defn validate-readonly-updates! [document update-paths]
  (let [doc-schema (model/get-document-schema document)]
    (doseq [path update-paths]
      (let [{:keys [readonly readonly-after-sent]} (model/find-by-name (:body doc-schema) path)]
        (when (or readonly (and (sent? document) readonly-after-sent))
          (fail! :error-trying-to-update-readonly-field))))))

(defn- validate-readonly-removes! [document remove-paths]
  (let [doc-schema              (model/get-document-schema document)
        validate-all-subschemas (fn validate-all-subschemas [{:keys [readonly readonly-after-sent body]}]
                                  (or readonly
                                      (and (sent? document) readonly-after-sent)
                                      (some validate-all-subschemas body)))]
    (doseq [path remove-paths]
      (let [{:keys [readonly readonly-after-sent] :as subschema} (model/find-by-name (:body doc-schema) path)]
        (when (validate-all-subschemas subschema)
          (fail! :error-trying-to-remove-readonly-field))))))

(defn update! [{application :application timestamp :created {role :role} :user} doc-id updates collection]
  (let [document      (by-id application collection doc-id)
        model-updates (->model-updates updates)
        update-paths  (map first model-updates)]
    (when-not document (fail! :error.document-not-found))
    (validate-against-whitelist! document update-paths role)
    (validate-readonly-updates! document update-paths)
    (persist-model-updates application collection document model-updates timestamp)))

(defn- empty-op-attachments-ids
  "Returns attachment ids, which don't have verions and have op-id as operation id. Returns nil when none found"
  [attachments op-id]
  (when (and op-id attachments)
    (seq (map :id (filter
                    (fn [{op :op versions :versions}]
                      (and (= (:id op) op-id)
                           (empty? versions)))
                    attachments)))))

(defn remove! [{application :application timestamp :created :as command} doc-id collection]
  (let [document      (by-id application collection doc-id)
        updated-app   (update-in application [:documents] (fn [c] (filter #(not= (:id %) doc-id) c)))
        trigger-fn    (get-after-update-trigger-fn document)
        extra-updates (trigger-fn updated-app)
        op-id         (get-in document [:schema-info :op :id])
        removable-attachment-ids (when op-id
                                   (empty-op-attachments-ids (:attachments application) op-id))
        all-attachments (:attachments (domain/get-application-no-access-checking (:id application) [:attachments]))]
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
                     all-attachments
                     #(= (:id (:op %)) op-id)
                     :op nil
                     :groupType nil)))}))
    (when (seq removable-attachment-ids)
      (att/delete-attachments! application removable-attachment-ids))))

(defn removing-updates-by-path [collection doc-id paths]
  (letfn [(build-path [path] (->> (map name path)
                                  (ss/join ".")
                                  ((juxt (partial str (name collection) ".$.data.")
                                         (partial str (name collection) ".$.meta.")))))]
    (if-let [paths (not-empty (remove empty? paths))]
      {:mongo-query   {(keyword collection) {$elemMatch {:id doc-id}}}
       :mongo-updates {$unset (-> (mapcat build-path paths)
                                  (zipmap (repeat "")))}}
      {})))

(defn remove-document-data [{application :application {role :role} :user :as command} doc-id paths collection]
  (let [document (by-id application collection doc-id)
        paths (map (partial map util/->keyword) paths)]
    (when-not document (fail! :error.document-not-found))
    (validate-against-whitelist! document paths role)
    (validate-readonly-removes! document paths)
    (->> (removing-updates-by-path collection doc-id paths)
         ((juxt :mongo-query :mongo-updates))
         (apply update-application command))))

(defn new-doc
  ([application schema created] (new-doc application schema created []))
  ([application schema created updates]
   (let [empty-document (model/new-document schema created)
         document       (model/apply-updates empty-document (->model-updates updates))
         post-results   (model/validate application document schema)]
     (when (model/has-errors? post-results) (fail! :document-would-be-in-error-after-update :results post-results))
     document)))

(defn- can-add-schema? [{info :info :as schema} application]
  (let [schema-name         (:name info)
        applicant-schema    (operations/get-applicant-doc-schema-name application)

        all-operation-names (->> (conj (:secondaryOperations application) (:primaryOperation application)) (map :name) set)
        all-meta            (map operations/get-operation-metadata all-operation-names)
        allowed-schemas     (-> (map (fn [m] (concat (:required m) (:optional m))) all-meta) flatten set (conj applicant-schema))

        has-same-document (seq (filter (util/fn-> :schema-info :name (= schema-name)) (:documents application)))]
    (and
      (allowed-schemas schema-name) ; Must be defined in required or optional documents for some of the operations
      (or
        (= applicant-schema schema-name) ; Can always add more applicants
        (:repeating info) ; Can add more repeating docs
        (not has-same-document))))) ; Schema not repeating but document is valid and missing from the application

(defn do-create-doc! [{created :created {:keys [schema-version] :as application} :application :as command} schema-name & [updates]]
  (let [schema (schemas/get-schema schema-version schema-name)]

    (when-not (can-add-schema? schema application) (fail! :error.non-repeating-schema))

    (let [document (new-doc application schema created updates)]
      (update-application command {$push {:documents document}
                                   $set  {:modified created}})
      document)))

(defn update-key-in-schema? [schema [update-key _]]
  (model/find-by-name schema update-key))

(defn- subject->updates [subject path-arr schema set-empty-values?]
  (when-not (map? schema) (fail! :error.schema-not-found))

  (let [with-hetu (model/has-hetu? (:body schema) path-arr)
        person    (tools/unwrapped (case (first path-arr)
                                     "henkilo" (model/->henkilo subject :with-hetu with-hetu :with-empty-defaults? set-empty-values?)
                                     "yritys" (model/->yritys subject :with-empty-defaults? set-empty-values?)
                                     (model/->henkilo subject :with-hetu with-hetu :with-empty-defaults? set-empty-values?)))
        model     (if (seq path-arr)
                    (assoc-in {:_selected (first path-arr)} (map keyword path-arr) person)
                    person)

        include-update (fn [path-val]
                         (let [v (second path-val)]
                           (and
                             ; Path must exist in schema!
                             (update-key-in-schema? (:body schema) path-val)
                             (or (ss/other-than-string? v)
                               ; Optionally skip empty values
                               set-empty-values?  (not (ss/blank? v))))))]
    (filterv include-update (tools/path-vals model))))

(defn set-subject-to-document
  ([application document subject path timestamp]
    (set-subject-to-document application document subject path timestamp true))
  ([application document subject path timestamp set-empty-values?]
    {:pre [(map? document) (map? subject) (util/boolean? set-empty-values?)]}
    (when (seq subject)
      (let [path-arr (if-not (ss/blank? path) (ss/split path #"\.") [])
            schema   (schemas/get-schema (:schema-info document))
            updates  (subject->updates subject path-arr schema set-empty-values?)]
        (debugf "merging user %s with best effort into %s %s: %s" (:email subject) (get-in document [:schema-info :name]) (:id document) updates)
        (persist-model-updates application "documents" document updates timestamp)))))

(defn do-set-user-to-document
  ([application document-id user-id path timestamp]
    (do-set-user-to-document application document-id user-id path timestamp true))
  ([application document-id user-id path timestamp set-empty-values?]
    {:pre [application document-id timestamp]}
    (if-let [document (domain/get-document-by-id application document-id)]
      (when-not (ss/blank? user-id)
        (if-let [subject (user/get-user-by-id user-id)]
          (set-subject-to-document application document subject path timestamp set-empty-values?)
          (fail! :error.user-not-found)))
      (fail :error.document-not-found))))

(defn do-set-company-to-document [application document company-id path user timestamp]
  {:pre [document]}
  (when-not (ss/blank? company-id)
    (let [path-arr (if-not (ss/blank? path) (s/split path #"\.") [])
          schema (schemas/get-schema (:schema-info document))
          subject (company/find-company-by-id company-id)
          ;; Company contact person fields are filled with the current user's data.
          company (tools/unwrapped (model/->yritys (merge subject (dissoc user :zip)) :with-empty-defaults true))
          model (if (seq path-arr)
                  (assoc-in {} (map keyword path-arr) company)
                  company)
          updates (->> (tools/path-vals model)
                       (filter (partial update-key-in-schema? (:body schema))))]
      (when-not schema (fail! :error.schema-not-found))
      (when-not company (fail! :error.company-not-found))
      (debugf "merging company %s into %s %s with db %s" model (get-in document [:schema-info :name]) (:id document) mongo/*db-name*)
      (persist-model-updates application "documents" document updates timestamp))))

;;
;; Disabling or de-activating
;;

(defn set-disabled-status [command doc-id value]
  (update-application command
                      {:documents {$elemMatch {:id doc-id}}}
                      {$set {:modified (:created command)
                             :documents.$.disabled (= "disabled" value)}}))
