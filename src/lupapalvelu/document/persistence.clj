(ns lupapalvelu.document.persistence
  (:require [taoensso.timbre :refer [trace debug debugf info infof warn error fatal]]
            [clj-time.format :as timeformat]
            [clojure.string :refer [replace-first]]
            [clojure.string :as s]
            [monger.operators :refer :all]
            [sade.util :as util :refer [fn->]]
            [sade.core :refer [ok fail fail! unauthorized! now]]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [update-application application->command]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.util :as att-util]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [lupapalvelu.company :as company]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]))

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


(defn transform
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

(defn validate-whitelist-properties
  [value-mapping [k v]]
  {:pre [(every? keyword? v)]}
  (some #{(keyword (get value-mapping k))} v))

(defn validate-against-whitelist! [document update-paths user-role {permitType :permitType}]
  (let [doc-schema (model/get-document-schema document)]
    (doseq [path update-paths
            :let [{whitelist :whitelist} (model/find-by-name (:body doc-schema) path)
                  whitelist-key-mapping {:roles (keyword user-role)
                                         :permitType (keyword permitType)}]]
      (when-not (every? (partial validate-whitelist-properties whitelist-key-mapping) (dissoc whitelist :otherwise))
        (unauthorized!)))))

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
        exception-cases         #{"pysyvaHuoneistotunnus"}
        validate-all-subschemas (fn validate-all-subschemas [{:keys [readonly readonly-after-sent body name]}]
                                  (or (and readonly (not (exception-cases name)))
                                      (and (sent? document) readonly-after-sent)
                                      (some validate-all-subschemas body)))]
    (doseq [path remove-paths]
      (let [subschema (model/find-by-name (:body doc-schema) path)]
        (when (validate-all-subschemas subschema)
          (fail! :error-trying-to-remove-readonly-field))))))

(defn validate-pseudo-input-updates!
  "Pseudo inputs cannot be updated."
  [document update-paths]
  (let [doc-schema (model/get-document-schema document)]
    (doseq [path update-paths]
      (when (:pseudo? (model/find-by-name (:body doc-schema) path))
        (fail! :error-trying-to-update-pseudo-input-field)))))

(defn validate-flagged-input-updates!
  "Flag excluded inputs cannot be updated"
  [command document update-paths]
  (let [flags      (tools/resolve-schema-flags command)
        doc-schema (model/get-document-schema document)]
    (doseq [path update-paths]
      (when (tools/exclude-schema? flags (model/find-by-name (:body doc-schema) path))
        (fail! :error-trying-to-update-excluded-input-field)))))

(defn update! [{application :application timestamp :created {role :role} :user
                :as command} doc-id updates collection]
  (let [document      (tools/by-id application collection doc-id)
        model-updates (->model-updates updates)
        update-paths  (map first model-updates)]
    (when-not document (fail! :error.document-not-found :doc-id doc-id))
    (validate-against-whitelist! document update-paths role application)
    (validate-readonly-updates! document update-paths)
    (validate-pseudo-input-updates! document update-paths)
    (validate-flagged-input-updates! command document update-paths)
    (persist-model-updates application collection document model-updates timestamp)))

(defn- empty-op-attachments-ids
  "Returns attachment ids, which don't have versions and have op-id as operation id. Returns
  nil when none found."
  [attachments op-id]
  (when (and op-id attachments)
    (seq (map :id (filter
                    (fn [{versions :versions :as attachment}]
                      (and ((set (att-util/get-operation-ids attachment)) op-id)
                           (-> attachment :op (count) (< 2))
                           (empty? versions)))
                    attachments)))))

(defn remove! [{application :application timestamp :created :as command} document]
  (let [updated-app   (update-in application [:documents] (fn [c] (filter #(not= (:id %) (:id document)) c)))
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
                 {:documents {:id (:id document)}}
                 (when op-id
                   {:secondaryOperations {:id op-id}}))
         $set  (merge
                 {:modified timestamp}
                 (when op-id
                   (->> all-attachments
                        (keep-indexed (fn [ind att]
                                        (some->> (att/remove-operation-updates att op-id)
                                                 (util/map-keys #(ss/join "." [ind (name %)])))))
                        (apply concat)
                        (util/map-keys (partial util/kw-path :attachments)))))}))
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

(defn- apply-removals [document paths]
  (reduce (fn [document path]
            (->> (vec (flatten [:data path]))
                 (map (fn [key]
                        (cond
                          (number? key) key
                          (keyword? key) key
                          :else (keyword key))))
                 (util/dissoc-in document)))
          document paths))

(defn remove-document-data [{application :application {role :role} :user :as command} doc-id paths collection]
  (let [document (tools/by-id application collection doc-id)
        updated-doc (apply-removals document paths)
        post-results (model/validate application updated-doc)
        paths (map (partial map util/->keyword) paths)]
    (when-not document (fail! :error.document-not-found))
    (validate-against-whitelist! document paths role application)
    (validate-readonly-removes! document paths)
    (->> (removing-updates-by-path collection doc-id paths)
         ((juxt :mongo-query :mongo-updates))
         (apply update-application command))
    {:ok true :results post-results}))

(defn new-doc
  ([application schema created] (new-doc application schema created []))
  ([application schema created updates]
   (let [empty-document (model/new-document schema created)
         document       (model/apply-updates empty-document (->model-updates updates))
         post-results   (model/validate application document schema)]
     (when (model/has-errors? post-results) (fail! :document-would-be-in-error-after-update :results post-results))
     document)))

(defn- can-add-schema? [{:keys [info]} application]
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

  (let [with-hetu (model/has-hetu? (:body schema) path-arr) ;;Schema has hetu <=> schema has ulkomainen hetu
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
                               set-empty-values? (ss/not-blank? v)))))]
    (filterv include-update (tools/path-vals model))))

(defn document-subject-updates
  ([document subject path]
   (document-subject-updates document subject path true))
  ([document subject path set-empty-values?]
   (let [path-arr (if-not (ss/blank? path) (ss/split path #"\.") [])
         schema   (schemas/get-schema (:schema-info document))
         {:keys [ulkomainenHenkilotunnus not-finnish-hetu]} (get-in document [:data :henkilo :henkilotiedot])
         ;;We only want to modify values of the fields ulkomainenHenkilotunnus and not-finnish-hetu only if it is necessary.
         ;;Because modified ts is used to control should we show ulkomainenHenkilotunnus or not.
         ;;See lupapalvelu.document.persistence/validate-input-enabled-for-organization! and lupapalvelu.document.model/evaluate-if-field-is-not-enabled-for-organization.
         set-empty-value-for-ulkomainen-hetu? (or (ss/not-blank? (:value ulkomainenHenkilotunnus)) (:value not-finnish-hetu))
         {:keys [personId non-finnish-personId]} subject
         ulkomainen-hetu-value (cond
                                 (ss/not-blank? non-finnish-personId)                         non-finnish-personId
                                 (and set-empty-values? set-empty-value-for-ulkomainen-hetu?) ""
                                 :else                                                        nil)
         not-finnish-hetu-value     (cond (and (ss/blank? personId)
                                               (ss/not-blank? non-finnish-personId))                     true
                                          (or non-finnish-personId set-empty-value-for-ulkomainen-hetu?) false
                                          :else                                                          nil)
         modified-subject (util/assoc-when subject some? subject :non-finnish-personId ulkomainen-hetu-value :not-finnish-hetu not-finnish-hetu-value)]
     (subject->updates modified-subject path-arr schema set-empty-values?))))

(defn set-subject-to-document
  ([application document subject path timestamp]
    (set-subject-to-document application document subject path timestamp true))
  ([application document subject path timestamp set-empty-values?]
    {:pre [(map? document) (map? subject) (boolean? set-empty-values?)]}
    (when (seq subject)
      (let [updates (document-subject-updates document subject path set-empty-values?)]
        (debugf "merging user %s with best effort into %s %s: %s" (:email subject) (get-in document [:schema-info :name]) (:id document) updates)
        (persist-model-updates application "documents" document updates timestamp)))))

(defn do-set-user-to-document
  ([application document-id user-id path timestamp current-user]
    (do-set-user-to-document application document-id user-id path timestamp current-user true))
  ([application document-id user-id path timestamp current-user set-empty-values?]
    {:pre [application document-id timestamp]}
   (if-let [{data :data :as document} (domain/get-document-by-id application
                                                                 document-id)]
      (if (ss/blank? user-id)
        (let [user-id-path (cond->> [:userId]
                             (:henkilo data) (cons :henkilo))]
          (persist-model-updates application
                                "documents"
                                document
                                [[user-id-path ""]]
                                timestamp))
        (let [company-auth (auth/auth-via-company application user-id)
              company      (when company-auth
                             (company/find-company-by-id (:id company-auth)))
              personal-auth (auth/get-auth application (:id current-user))]
          (if-let [subject (util/assoc-when (user/get-user-by-id user-id)
                                            :companyId (:y company)
                                            :companyName (:name company))]
            (do (set-subject-to-document application document subject path timestamp set-empty-values?)
                (when (and (= user-id (:id current-user))
                           company
                           (= :suunnittelija (get-in document [:schema-info :subtype]))
                           (nil? personal-auth))
                  ; If current user is a company member and without personal auth, and is setting
                  ; herself as a designer, create auth because of the implicit consent of manipulating
                  ; your own data.
                  (update-application
                    (application->command application)
                    {:auth.id {$ne user-id}}
                    {$push {:auth     (auth/make-user-auth current-user false)}
                     $set  {:modified timestamp}})))
            (fail! :error.user-not-found))))
      (fail :error.document-not-found))))

(defn- company-fields [{auth :auth} company-id user]
  (merge (company/find-company-by-id company-id)
         (dissoc (if (= company-id (some-> user :company :id))
                   user
                   (let [auth-ids (set (map :id auth))]
                     (util/find-first #(contains? auth-ids (:id %))
                                      (company/find-company-users company-id))))
                 :zip)))

(defn company-address-type [schema path-arr]
  (let [path-document-model (model/find-by-name (:body schema) [(first path-arr)])
        address-model (if (> (count path-arr) 1)
                        (model/find-by-name (:body (model/find-by-name (:body path-document-model) [(last path-arr)])) [:osoite])
                        (model/find-by-name (:body path-document-model) [:osoite]))]
    (:address-type address-model)))

(defn do-set-company-to-document [application document company-id path user timestamp]
  {:pre [document]}
  (when-not (ss/blank? company-id)
    (let [path-arr (if-not (ss/blank? path) (s/split path #"\.") [])
          schema (schemas/get-schema (:schema-info document))
          company (tools/unwrapped (model/->yritys (company-fields application
                                                                   company-id user)
                                                   :with-empty-defaults? true
                                                   :contact-address? (= :contact (company-address-type schema path-arr))))
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

(defn set-edited-timestamp [{:keys [user] :as command} doc-id]
  (update-application command
                      {:documents {$elemMatch {:id doc-id}}}
                      {$set {:modified (:created command)
                             :documents.$.meta._post_verdict_edit.timestamp (now)
                             :documents.$.meta._post_verdict_edit.user {:id (:id user)
                                                                        :firstName (:firstName user)
                                                                        :lastName (:lastName user)}}}))

(defn set-sent-timestamp [{:keys [user] :as command} doc-id]
  (update-application command
                      {:documents {$elemMatch {:id doc-id}}}
                      {$set {:modified (:created command)
                             :documents.$.meta._post_verdict_sent.timestamp (now)
                             :documents.$.meta._post_verdict_sent.user {:id (:id user)
                                                                        :firstName (:firstName user)
                                                                        :lastName (:lastName user)}}}))
