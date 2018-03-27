(ns lupapalvelu.smoketest.application-smoke-tests
  (:require [lupapiste.mongocheck.core :refer [mongocheck]]
            [lupapiste.mongocheck.checks :as checks]
            [lupapalvelu.server]                            ; ensure all namespaces are loaded
            [lupapalvelu.application :as app]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]
            [lupapalvelu.restrictions :as restrictions]
            [lupapalvelu.rest.applications-data :as rest-application-data]
            [lupapalvelu.rest.schemas :refer [HakemusTiedot]]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [clojure.set :refer [difference]]
            [sade.strings :as ss]
            [sade.schemas :as ssc]
            [schema.core :as sc]
            [sade.shared-util :as util])
  (:import [schema.utils.ErrorContainer]))

(defn- validate-doc [ignored-errors application {:keys [id schema-info data] :as doc}]
  (cond
    (not (and (:name schema-info) (:version schema-info)))
    {:document-id id :schema-info schema-info :results "Schema name or version missing"}

    (not (map? data))
    {:document-id id :schema-info schema-info :results (str "Data is not a map but " (class data))}

    :else
    (let [ignored (set ignored-errors)
          schema (model/get-document-schema doc)
          info (model/document-info doc schema)
          results (filter
                    (fn [{result :result}]
                      (and (= :err (first result)) (not (ignored (second result)))))
                    (flatten (model/validate-fields application info nil data [])))]
      (when (seq results)
        {:document-id id :schema-info (select-keys schema-info [:name :type :subtype]) :results results}))))

(defn- validate-documents [ignored-errors {documents :documents :as application}]
  (let [results (filter seq (map (partial validate-doc ignored-errors application) documents))]
    (when (seq results)
      results)))

(defn- validate-tasks [ignored-errors {tasks :tasks :as application}]
  (let [doc-results (map (partial validate-doc ignored-errors application) tasks)
        created-results (map (fn [t] (when-not (:created t) {:task-id (:id t) :results "No created timestamp"})) tasks)
        results (filter seq (concat doc-results created-results))]
    (when (seq results)
      results)))

(defn- validate-state [{state :state :as application}]
  (when (or (ss/blank? state) (not (sm/valid-state? application state)))
    {:result (format "Missing or invalid state (%s)" state)}))

;; Every document is valid.
(mongocheck :applications (partial validate-documents []) :documents :state :auth)

(mongocheck :submitted-applications (partial validate-documents ["illegal-hetu"]) :documents :state :auth)

;; Tasks are valid
(mongocheck :applications (partial validate-tasks []) :tasks :state :auth)

;; All applications have state
(mongocheck :applications validate-state :state :infoRequest :primaryOperation :permitSubtype :permitType)

(def coerce-auth (ssc/json-coercer auth/Auth))

(defn validate-auth-against-schema [{id :id :as auth}]
  (let [coercion-result (coerce-auth auth)]
    (when (instance? schema.utils.ErrorContainer coercion-result)
      {:auth-id id
       :error "Not valid auth"
       :coercion-result coercion-result})))


(defn validate-duplicate-auths [auths]
  (->> auths            ; check duplicate IDs without statementGivers
       (remove #(= "statementGiver" (:role %)))
       (remove #(= "company" (:type %)))       ; remove companies, but FIXME after LPK-3564
       (map :id)
       (frequencies)
       (reduce (fn [acc [k v]]
                 (cond-> acc
                   (> v 1) (conj {:auth-id k :error "Duplicate auth id"})))
               [])))

(defn validate-auth-array [{auths :auth}]
  (->> (concat (map validate-auth-against-schema auths)
               #_(validate-duplicate-auths auths))          ; FIXME enable duplicate validation after LPK-3564
       (remove nil?)
       (seq)))

;; All auths are valid
(mongocheck :applications validate-auth-array :auth)

(def coerce-auth-restriction (ssc/json-coercer restrictions/AuthRestriction))

(defn validate-auth-restriction-against-schema [auth-restriction]
  (let [coercion-result (coerce-auth-restriction auth-restriction)]
    (when (instance? schema.utils.ErrorContainer coercion-result)
      (assoc auth-restriction
             :error "Not valid auth-restriction"
             :coercion-result coercion-result))))

(defn validate-restricting-user-in-auths [auths {{restricting-user-id :id} :user :as restriction}]
  (when-not (contains? (set (map :id auths)) restricting-user-id)
    (assoc restriction :error "Restricting user not in auths")))

(defn validate-auth-restrictions [{auth :auth auth-restrictions :authRestrictions}]
  (->> (concat (map validate-auth-restriction-against-schema auth-restrictions)
               (map (partial validate-restricting-user-in-auths auth) auth-restrictions))
       (remove nil?)
       seq))

(mongocheck :applications validate-auth-restrictions :auth :authRestrictions)

;; Latest attachment version and latestVersion match
(defn validate-latest-version [{id :id versions :versions latestVersion :latestVersion}]
  (when-not (or (empty? versions) (= latestVersion (last versions)))
    {:attachment-id id :error "latest version does not match last element of versions array"}))

(def coerce-attachment (ssc/json-coercer att/Attachment))

(defn validate-attachment-against-schema [{id :id :as attachment}]
  (let [{{{type-id :type-id type-group :type-group} :type} :error :as coercion-result} (coerce-attachment attachment)]
    (when (instance? schema.utils.ErrorContainer coercion-result)
      {:attachment-id id
       :error "Not valid attachment"
       :coercion-result  (cond-> coercion-result ; truncate long enum prints from type-group and type-id by taking only value
                           type-group (update-in [:error :type :type-group] #(list 'invalid-type-group (.value %)))
                           type-id    (update-in [:error :type :type-id]    #(list 'invalid-type-id (.value %))))})))

(defn validate-not-needed
  "If there are versions, it doesn't make sense to flag attachment as 'not needed'."
  [{:keys [id notNeeded versions]}]
  (when (and notNeeded (not-empty versions))
    {:attachment-id id
     :error "Has versions, but marked as not needed"}))

(defn validate-attachments-grouping
  "YA attachments should not have 'op' or 'groupType' fields.
   Other attachment should have valid operation IDs, if defined."
  [application-permitType op-ids {:keys [id op groupType]}]
  (when (not-empty op-ids)
    (if (= application-permitType "YA")
      (when (or (seq op) groupType)
        {:attachment-id id
         :error "Not valid YA attachment (should not have op/groupType)"
         :op op
         :groupType groupType})
      (when (and (= "operation" groupType)
                 (not-empty op)
                 (not-every? (set op-ids) (map :id op)))
        {:error "Invalid operation ids"
         :attachment-id id
         :application-ops op-ids
         :attachment-ops (map :id op)}))))


(defn validate-attachments
  [{:keys [_id attachments permitType primaryOperation secondaryOperations]}]
  (let [operation-ids (map :id (cons primaryOperation secondaryOperations))]
    (->> attachments
         (mapcat (juxt validate-attachment-against-schema
                       validate-latest-version
                       validate-not-needed
                       (partial validate-attachments-grouping permitType operation-ids)))
         (remove nil?)
         seq)))

(mongocheck :applications validate-attachments :primaryOperation :secondaryOperations :attachments :permitType)

;; Documents have operation information

(defn- application-schemas-have-ops [{documents :documents :as application}]
  (when-not (:infoRequest application)
    (let [operations (app/get-operations application)
          docs-with-op (count (filter #(get-in % [:schema-info :op]) documents))
          ops          (count operations)]
      (when-not (= docs-with-op ops)
        (format "Different number of operations and documents refering to an operation: %d != %d" docs-with-op ops)))))


(mongocheck :applications application-schemas-have-ops :documents :primaryOperation :secondaryOperations :infoRequest)

(mongocheck :submitted-applications application-schemas-have-ops :documents :primaryOperation :secondaryOperations :infoRequest)

(mongocheck :applications (checks/not-null-property :organization) :organization)

(mongocheck :applications (checks/not-null-property :propertyId) :propertyId)

(mongocheck :applications (checks/not-null-property :location) :location)

(mongocheck :applications (checks/not-null-property :municipality) :municipality)

(mongocheck :applications (checks/not-null-property :schema-version) :schema-version)

(defn some-timestamp-is-set [timestamps states]
  (fn [application]
    (when (and (states (keyword (:state application))) (not-any? #(get application %) timestamps))
      (format "One of timestamps %s is null in state %s" timestamps (:state application)))))

(mongocheck :applications (some-timestamp-is-set #{:opened} (states/all-states-but [:draft :canceled])) :state :opened)

;;
;; Skips applications with operation "aiemmalla-luvalla-hakeminen" (previous permit aka paperilupa)
;;
(mongocheck :applications
  (fn [application]
    (when (and
            ((states/all-application-states-but [:canceled :draft :open]) (keyword (:state application)))
            (when-not (some #(= "aiemmalla-luvalla-hakeminen" %) (map :name (app/get-operations application)))
              (nil? (:submitted application))))
      "Submitted timestamp is null"))
  :submitted :state :primaryOperation :secondaryOperations)

(mongocheck :applications (some-timestamp-is-set #{:sent :acknowledged} #{:sent :complementNeeded}) :state :sent :acknowledged)

(mongocheck :applications (some-timestamp-is-set #{:closed} #{:closed}) :state :closed)

(def ignore-states-set #{:closed :acknowledged})

(defn validate-verdict-history-entry [{:keys [state history verdicts] :as application}]
  (when (contains? (difference states/post-verdict-states ignore-states-set) (keyword state))
    (let [verdict-state (sm/verdict-given-state application)
          verdict-history-entries (->> (app-state/state-history-entries history)
                                       (filter #(= (:state %) (name verdict-state))))]
      (when (and (zero? (count verdict-history-entries)) (not (zero? (count verdicts))))
        (format "Application has verdict, but no verdict history entry ('%s' required, has %d verdicts)" verdict-state (count verdicts))))))

(mongocheck :applications validate-verdict-history-entry :history :verdicts :state :primaryOperation :permitType :permitSubtype)

(def excluded-operations #{:tyonjohtajan-nimeaminen :tyonjohtajan-nimeaminen-v2 :aiemmalla-luvalla-hakeminen})

(defn submitted-rest-interface-schema-check-app [application]
  (when (and (#{"R" "YA"} (:permitType application))
             (#{"submitted"} (:state application))
             (not (excluded-operations (-> application :primaryOperation :name keyword))))
    (->> (mongo/with-id application)
         (rest-application-data/process-application)
         (sc/check HakemusTiedot))))

(apply mongocheck :applications submitted-rest-interface-schema-check-app :state rest-application-data/required-fields-from-db)

(defn validate-ya-subtypes [{:keys [primaryOperation permitType permitSubtype]}]
  (when (and (= "YA" permitType) (not= "ya-jatkoaika" (:name primaryOperation)))
    (when (or (ss/blank? permitSubtype)
              (->> (get op/operations (keyword (:name primaryOperation)))
                   :subtypes
                   (some (partial = (keyword permitSubtype)))
                   nil?))
      (format "Invalid YA subtype %s for operation %s" permitSubtype (:name primaryOperation)))))

(mongocheck :applications validate-ya-subtypes :primaryOperation :permitType :permitSubtype)
