(ns lupapalvelu.application
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warnf error fatal]]
            [clj-time.core :refer [year]]
            [clj-time.local :refer [local-now]]
            [clojure.set :refer [difference]]
            [clojure.walk :refer [keywordize-keys]]
            [monger.operators :refer :all]
            [lupapalvelu.action :as action]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.application-utils :refer [location->object]]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.building :as building]
            [lupapalvelu.company :as com]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.document.document :as doc]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.link-permit :as link-permit]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.operations :as op]
            [lupapalvelu.permissions :refer [defcontext] :as permissions]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.tiedonohjaus :as tos]
            [lupapalvelu.user :as usr]
            [lupapalvelu.states :as states]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.xml.krysp.building-reader :as building-reader]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.property :as prop]
            [sade.util :as util :refer [merge-in]]
            [sade.coordinate :as coord]
            [sade.strings :as ss]
            [lupapalvelu.application-utils :as app-utils]))

(defcontext canceled-app-context [{{user-id :id} :user application :application}]
  (let [last-history-entry (app-state/last-history-item application)]
    (when (and (= user-id (get-in last-history-entry [:user :id]))
               (-> last-history-entry :state keyword (= :canceled)))
      {:context-scope :canceled-app
       :context-roles [:canceler]})))

(defn get-operations [application]
  (remove nil? (conj (seq (:secondaryOperations application)) (:primaryOperation application))))

(defn get-sorted-operation-documents [{docs :documents primary-op :primaryOperation secondary-ops :secondaryOperations}]
  (let [operations (cons primary-op (sort-by :created secondary-ops))]
    (->> (filter (comp (set (map :id operations)) :id :op :schema-info) docs)
         (sort-by (util/fn-> :schema-info :op :id (util/position-by-id operations))))))

(defn resolve-valid-subtypes
  "Returns a set of valid permit and operation subtypes for the application."
  [{permit-type :permitType op :primaryOperation}]
  (let [op-subtypes (op/get-primary-operation-metadata {:primaryOperation op} :subtypes)
        permit-subtypes (permit/permit-subtypes permit-type)]
    (distinct (concat op-subtypes permit-subtypes))))


(defn tos-history-entry [tos-function timestamp user & [correction-reason]]
  {:pre [(map? tos-function)]}
  {:tosFunction tos-function
   :ts timestamp
   :user (usr/summary user)
   :correction correction-reason})

(defn handler-history-entry [handler timestamp user]
  {:handler handler
   :ts timestamp
   :user (usr/summary user)})

;;
;; Validators
;;

(defn- count-link-permits [application]
  (count (or (:linkPermitData application)
             (:linkPermitData (meta-fields/enrich-with-link-permit-data application)))))

(defn- count-required-link-permits [application]
  (let [muutoslupa? (= :muutoslupa (keyword (:permitSubtype application)))]
    (->> (get-operations application)
         (map :name)
         (map op/required-link-permits)
         (reduce + (if muutoslupa? 1 0)))))

(defn extra-link-permits? [application]
  (< (count-required-link-permits application) (count-link-permits application)))

(defn validate-link-permits [application]
  (when (> (count-required-link-permits application) (count-link-permits application))
    (fail :error.permit-must-have-link-permit)))

(defn validate-authority-in-drafts
  "Validator: Restrict authority access in draft application.
   To be used in commands' :pre-checks vector."
  [{user :user application :application}]
  (when (and (= :draft (keyword (:state application)))
             (usr/authority? user)
             (not (domain/write-access? application (:id user))))
    (fail :error.unauthorized :source ::validate-authority-in-drafts)))

(defn validate-has-subtypes [{application :application}]
  (when (empty? (resolve-valid-subtypes application))
    (fail :error.permit-has-no-subtypes)))

(defn pre-check-permit-subtype [{data :data application :application}]
  (when-let [subtype (:permitSubtype data)]
    (when-not (util/contains-value? (resolve-valid-subtypes application) (keyword subtype))
      (fail :error.permit-has-no-such-subtype))))

(defn submitted? [{:keys [state]}]
  (boolean (states/post-submitted-states (keyword state))))

(defn verdict-given? [{:keys [state]}]
  (boolean (states/post-verdict-states (keyword state))))

(defn designer-app? [application]
  (= :suunnittelijan-nimeaminen (-> application :primaryOperation :name keyword)))

(defn- contains-primary-operation? [application op-set]
  {:pre [(set? op-set)]}
  (contains? op-set (-> application :primaryOperation :name keyword)))

(defn allow-primary-operations
  "Prechecker (factory, no partial needed) that fails if the current
  primary operation is not contained in the operation set (keywords)"
  [operation-set]
  (fn [{application :application}]
    (when-not (contains-primary-operation? application operation-set)
      (fail :error.unsupported-primary-operation))))

(defn reject-primary-operations
  "Prechecker (factory, no partial needed) that fails if the current
  primary operation is contained in the operation set (keywords)"
  [operation-set]
  (fn [{application :application}]
    (when (contains-primary-operation? application operation-set)
      (fail :error.unsupported-primary-operation))))

(defn allow-roles-only-in-operations
  "Prechecker (factory, no partial needed) that fails if the user has
  one of the given roles in the application but the current primary
  operation is not in the given operations"
  [roles operations]
  (fn [{:keys [user application]}]
    (when (and (auth/has-some-auth-role? application (:id user) roles)
               (not (contains-primary-operation? application (set operations))))
      (fail :error.unauthorized :source ::allow-roles-in-operations))))

;;
;; Helpers
;;

(defn party-document? [doc]
  (= :party (tools/doc-type doc)))

(defn user-role
  "User role within the application."
  [user {:keys [organization]}]
  (if (usr/user-is-authority-in-organization? user organization)
    :authority
    :applicant))

(defn insert-application [application]
  {:pre [(every? (partial contains? application)  (keys domain/application-skeleton))]}
  (mongo/insert :applications (merge application (meta-fields/applicant-index application))))

(defn filter-party-docs [schema-version schema-names repeating-only?]
  (filter (fn [schema-name]
            (let [schema-info (:info (schemas/get-schema schema-version schema-name))]
              (and (= (:type schema-info) :party) (or (:repeating schema-info) (not repeating-only?)) )))
          schema-names))

(defn enrich-application-handlers [application {roles :handler-roles :as organization}]
  (update application :handlers (partial map #(merge (util/find-by-id (:roleId %) roles) %))))

; Seen updates
(def collections-to-be-seen #{"comments" "statements" "verdicts" "authority-notices" "info-links"})

(defn mark-collection-seen-update [{id :id} timestamp collection]
  {:pre [(collections-to-be-seen collection) id timestamp]}
  {(str "_" collection "-seen-by." id) timestamp})

(defn mark-indicators-seen-updates [{application :application user :user timestamp :created :as command}]
  (merge
    (apply merge (map (partial mark-collection-seen-update user timestamp) collections-to-be-seen))
    (when (permissions/permissions? command [:document/approve]) (model/mark-approval-indicators-seen-update application timestamp))
    (when (permissions/permissions? command [:attachment/approve]) {:_attachment_indicator_reset timestamp})))

; whitelist-action
(defn- prefix-with [prefix coll]
  (conj (seq coll) prefix))

(defn- enrich-single-doc-disabled-flag [{user-role :role} {permitType :permitType} doc]
  (let [doc-schema (model/get-document-schema doc)
        zip-root (tools/schema-zipper doc-schema)
        whitelisted-paths (tools/whitelistify-schema zip-root)
        whitelist-validator (partial doc-persistence/validate-whitelist-properties {:roles user-role :permitType permitType})]
    (reduce (fn [new-doc [path whitelist]]
              (if-not (every? whitelist-validator (dissoc whitelist :otherwise))
                (tools/update-in-repeating new-doc (prefix-with :data path) merge {:whitelist-action (:otherwise whitelist)})
                new-doc))
            doc
            whitelisted-paths)))

; Process

(defn pertinent-validation-errors [{:keys [documents] :as  application}]
  (map (partial model/validate-pertinent application) documents))

(defn validate-fully-formed
 "If the application's organization requires fully-formed
  applications, the following checks are enforced:
  1. All the required fields are valid
  2. Every required attachment is either filled or marked not needed
  This function is called from submit-validation-errors on the api side.
  Returns nil on success and fail map if the application is not fully formed."
  [application]
  (when (and (some-> application
                     :organization
                     org/get-organization
                     :app-required-fields-filling-obligatory)
             (or (->> (pertinent-validation-errors application)
                      flatten
                      (some #(-> % :element :required)))
                 (some (fn [{:keys [required notNeeded versions]}]
                         (and required (not notNeeded) (empty? versions)))
                       (:attachments application))))
    (fail :application.requiredDataDesc)))

(defn- validate [application document]
  (let [all-results   (model/validate-pertinent application document)
        ; sorting result in ascending order on severity as only the last error ends up visible in the docgen UI per field
        all-results   (sort-by #(get {:tip 0
                                      :warn 1
                                      :error 2} (get-in % [:result 0])) all-results)
        create-result (fn [document result]
                        (assoc-in document (flatten [:data (:path result) :validationResult]) (:result result)))]
    (assoc (reduce create-result document all-results) :validationErrors all-results)))

(defn populate-operation-info [operations {info :schema-info :as doc}]
  (if (:op info)
    (if-let [operation (util/find-first #(= (:id %) (get-in info [:op :id])) operations)]
      (assoc-in doc [:schema-info :op] operation)
      (do
        (warnf "Couldn't find operation %s for doc %s " (get-in info [:op :id]) (:id doc))
        doc))
    doc))

(defn process-document-or-task [user application doc]
  (->> (validate application doc)
       (populate-operation-info (get-operations application))
       ((app-utils/person-id-masker-for-user user application))
       (enrich-single-doc-disabled-flag user application)))

(defn- process-documents-and-tasks [user application]
  (let [mapper (partial process-document-or-task user application)]
    (-> application
      (update :documents (partial map mapper))
      (update :tasks (partial map mapper)))))

(defn ->location [x y]
  [(util/->double x) (util/->double y)])

(defn get-link-permit-apps
  "Return associated link-permit application."
  [{:keys [linkPermitData]}]
  (when-let [links (not-empty (filter (comp #{"lupapistetunnus"} :type) linkPermitData))]
    (->> (map :id links)
         (domain/get-multiple-applications-no-access-checking))))

;; https://eevertti.vrk.fi/documents/2634109/3072453/VTJ-yll%C3%A4pito+Virhekoodit+Rajapinta/e7904362-6c43-43e6-8f1c-a80b24313ac9?version=1.0
;; gives some hint for valid ID, but is still pretty confusing...

(defn vrk-lupatunnus                                        ; LPK-3207
  "Below mimics other system's intrepetation of VRKLupatunnus (KuntaGML).
  Number part is fixed at 4 digits, and can't be '0000'.
  When sequence hits 10000, it would generate illegal value '0000'. To bypass this we will take first 4 in this special case.
  For the next value 10001 we would be back in line returning '0001'.
  It seems values do not need to be unique accross time."
  [{:keys [municipality created submitted id]}]
  (when (and (not-any? ss/blank? [municipality id]) (or submitted created))
    (let [orig-suffix (ss/suffix id "-")
          vrk-suffix (->> orig-suffix
                          (take-last 4)
                          (apply str))
          final-suffix (if (= "0000" vrk-suffix)            ; handle special case
                         (->> orig-suffix
                              (take 4)
                              (apply str))
                         vrk-suffix)]
      (assert (not= "0000" final-suffix) "VRKLupatunnus number can't be '0000'")
      (format "%s000%ty-%s" municipality (or submitted created) final-suffix))))

;;
;; Application query post process
;;

(defn- with-auth-models [{:keys [application] :as command}]
  (let [document-authz (action/allowed-actions-for-category (assoc-in command [:data :category] "documents"))]
    (update application :documents #(map (fn [doc] (assoc doc :allowedActions (get document-authz (:id doc)))) %))))

(def merge-operation-skeleton (partial merge domain/operation-skeleton))

(defn- with-allowed-attachment-types [application]
  (assoc application :allowedAttachmentTypes (->> (att-type/get-attachment-types-for-application application)
                                                  (att-type/->grouped-array))))
(defn ensure-operations
  "Ensure operations have all properties set."
  [app]
  (-> app
      (update :primaryOperation merge-operation-skeleton)
      (update :secondaryOperations (fn [operations] (map merge-operation-skeleton operations)))))

;; Meta fields with default values.
(def- operation-meta-fields-to-enrich {:attachment-op-selector true, :optional []})
(defn- enrich-primary-operation-with-metadata [app]
  (let [enrichable-fields (-> (op/get-primary-operation-metadata app)
                              (select-keys (keys operation-meta-fields-to-enrich)))
        fields-with-defaults (merge operation-meta-fields-to-enrich enrichable-fields)]
    (update app :primaryOperation merge fields-with-defaults)))

(defn- enrich-tos-function-name [{tos-function :tosFunction org-id :organization :as application}]
  (assoc application :tosFunctionName (:name (tos/tos-function-with-name tos-function org-id))))

(defn post-process-app [{:keys [user] :as command}]
  (->> (with-auth-models command)
       with-allowed-attachment-types
       ensure-operations
       enrich-primary-operation-with-metadata
       att/post-process-attachments
       meta-fields/enrich-with-link-permit-data
       (meta-fields/with-meta-fields user)
       action/without-system-keys
       (process-documents-and-tasks user)
       location->object))

(defn post-process-app-for-krysp [application organization]
  (-> application
      (domain/enrich-application-handlers organization)
      (domain/enrich-application-tags organization)
      enrich-tos-function-name
      meta-fields/enrich-with-link-permit-data
      link-permit/update-backend-ids-in-link-permit-data))

;;
;; Application creation
;;

(defn- new-attachment-types-for-operation [organization operation existing-types]
  (->> (org/get-organization-attachments-for-operation organization operation)
       (map (partial apply att-type/attachment-type))
       (filter #(or (att-type/operation-specific? %) (not (att-type/contains? existing-types %))))))

(defn- attachment-grouping-for-type [operation {{group-type :grouping} :metadata}]
  (when-not (false? (op/get-operation-metadata (:name operation) :attachment-op-selector))
    (util/assoc-when nil :groupType group-type :operations (when (and operation (= group-type :operation)) [operation]))))

(defn make-attachments
  [created operation organization applicationState tos-function & {:keys [target existing-attachments-types]}]
  (let [types     (new-attachment-types-for-operation organization operation existing-attachments-types)
        groups    (map (partial attachment-grouping-for-type operation) types)
        metadatas (pmap (partial tos/metadata-for-document (:id organization) tos-function) types)]
    (map (partial att/make-attachment created target true false false (keyword applicationState)) groups types metadatas)))

(defn multioperation-attachment-updates [operation organization attachments]
  (when-let [added-op (not-empty (select-keys operation [:id :name]))]
    (let [required-types (->> (org/get-organization-attachments-for-operation organization operation)
                              (map (partial apply att-type/attachment-type)))
          ops-to-update (keep-indexed (fn [ind att]
                                        (when (and (att-type/multioperation? (:type att))
                                                   (att-type/contains? required-types (:type att))
                                                   (= (keyword (:groupType att)) :operation))
                                          [(util/kw-path "attachments" ind "op") (:op att)]))
                                      attachments)]
      (util/assoc-when-pred nil not-empty
                            $set  (->> (remove (comp vector? second) ops-to-update) ; Update legacy op
                                       (util/map-values #(->> [% added-op] (remove nil?))))
                            $push (->> (filter (comp vector? second) ops-to-update) ; Update op array
                                       (util/map-values (constantly added-op)))))))

(defn- schema-data-to-body [schema-data application]
  (keywordize-keys
    (reduce
      (fn [body [data-path data-value]]
        (let [path (if (= :value (last data-path)) data-path (conj (vec data-path) :value))
              val (if (fn? data-value) (data-value application) data-value)]
          (assoc-in body path val)))
      {} schema-data)))

(defn make-document [application primary-operation-name created manual-schema-datas schema]
  (let [op-info (op/operations (keyword primary-operation-name))
        op-schema-name (:schema op-info)
        schema-version (:schema-version application)
        default-schema-datas (util/assoc-when-pred {} util/not-empty-or-nil?
                                                   op-schema-name
                                                   (:schema-data op-info))
        merged-schema-datas (merge-with conj default-schema-datas manual-schema-datas)
        schema-name (get-in schema [:info :name])]
    {:id          (mongo/create-id)
     :schema-info (:info schema) ; TODO: no need for storing doc schema into mongo (LPK-3107)
     :created     created
     :data        (util/deep-merge
                   (tools/create-document-data schema tools/default-values)
                   (tools/timestamped
                    (if-let [schema-data (get-in merged-schema-datas [schema-name])]
                      (schema-data-to-body schema-data application)
                      {})
                    created))}))

(defn make-documents [user created org op application & [manual-schema-datas]]
  {:pre [(or (nil? manual-schema-datas) (map? manual-schema-datas))]}
  (let [op-info (op/operations (keyword (:name op)))
        op-schema-name (:schema op-info)
        schema-version (:schema-version application)
        default-schema-datas (util/assoc-when-pred {} util/not-empty-or-nil?
                                                   op-schema-name (:schema-data op-info))
        merged-schema-datas (merge-with conj default-schema-datas manual-schema-datas)

        make (partial make-document application (:name op) created manual-schema-datas)

        ;; TODO: :removable is deprecated (LPK-3107), no need for storing doc schema into mongo
        ;;The merge below: If :removable is set manually in schema's info, do not override it to true.
        op-doc (update-in (make (schemas/get-schema schema-version op-schema-name)) [:schema-info] #(merge {:op op :removable true} %))

        existing-schemas-infos (map :schema-info (:documents application))
        existing-schema-names (set (map :name existing-schemas-infos))

        location-schema (util/find-first #(= (keyword (:type %)) :location) existing-schemas-infos)

        schemas (->> (when (not-empty (:org-required op-info)) ((apply juxt (:org-required op-info)) org))
                     (concat (:required op-info))
                     (map #(schemas/get-schema schema-version %)))
        new-docs (->> schemas
                      (remove (comp existing-schema-names :name :info))
                      (remove
                        (fn [{{:keys [type repeating]} :info}]
                          (and location-schema (= type :location) (not repeating))))
                      (map make)                           ;; required docs
                      (cons op-doc))]                      ;; new docs
    (if-not user
      new-docs
      (conj new-docs (make (schemas/get-schema schema-version (op/get-applicant-doc-schema-name application)))))))


(defn make-op [op-name created]
  {:id          (mongo/create-id)
   :name        op-name
   :description nil
   :created     created})

(defn make-application-id [municipality]
  (let [year (str (year (local-now)))
        sequence-name (str "applications-" municipality "-" year)
        counter (if (env/feature? :prefixed-id)
                  (format "9%04d" (mongo/get-next-sequence-value sequence-name))
                  (format "%05d"  (mongo/get-next-sequence-value sequence-name)))]
    (str "LP-" municipality "-" year "-" counter)))

(defn application-state [user organization-id info-request? operation-name]
  (cond
    info-request? :info
    (util/=as-kw "aiemmalla-luvalla-hakeminen" operation-name) :verdictGiven
    (or (usr/user-is-authority-in-organization? user organization-id)
        (usr/rest-user? user)
        (= "ARK" (op/permit-type-of-operation operation-name))) :open
    :else :draft))

(defn application-history-map [{:keys [created organization state tosFunction]} user]
  {:pre [(pos? created) (string? organization) (states/all-states (keyword state))]}
  (let [tos-function-map (tos/tos-function-with-name tosFunction organization)]
    {:history (cond->> [(app-state/history-entry state created user)]
                tos-function-map (concat [(tos-history-entry tos-function-map created user)]))}))

(defn permit-type-and-operation-map [operation-name created]
  (let [op (make-op operation-name created)
        classification {:permitType       (op/permit-type-of-operation operation-name)
                        :primaryOperation op}]
    (merge classification
           {:permitSubtype (first (resolve-valid-subtypes classification))})))

(defn application-auth [user operation-name]
  (let [user-auth    (usr/user-in-role user :writer)
        company-auth (some-> user :company :id com/find-company-by-id com/company->auth)]
    (-> (or company-auth user-auth)
        (assoc :unsubscribed (boolean (get-in op/operations [(keyword operation-name) :unsubscribe-notifications])))
        (vector))))

(defn application-comments [user messages open-inforequest? created]
  (let [comment-target (if open-inforequest? [:applicant :authority :oirAuthority] [:applicant :authority])]
    (map #(domain/->comment % {:type "application"} (:role user) user nil created comment-target) messages)))

(defn application-attachments-map [{:keys [infoRequest created primaryOperation state tosFunction]} organization]
  {:pre [(pos? created) (map? primaryOperation) (states/all-states (keyword state))]}
  {:attachments (if-not infoRequest
                  (make-attachments created primaryOperation organization state tosFunction)
                  [])})

(defn application-documents-map [{:keys [infoRequest created primaryOperation auth] :as application} user organization manual-schema-datas]
  {:pre [(pos? created) (map? primaryOperation)]}
  {:documents (if-not infoRequest
                (make-documents user created organization primaryOperation application manual-schema-datas)
                [])})

(defn application-metadata-map [{:keys [attachments organization tosFunction]}]
  {:pre [(string? organization)]}
  (let [metadata (tos/metadata-for-document organization tosFunction "hakemus")]
    {:metadata        metadata
     :processMetadata (-> (tos/metadata-for-process organization tosFunction)
                          (tos/calculate-process-metadata metadata attachments))}))

(defn application-timestamp-map [{:keys [state created]}]
  {:pre [(states/all-states (keyword state)) (pos? created)]}
  {:opened   (when (#{:open :info} state) created)
   :modified created})

(defn location-map [location]
  {:pre [(number? (first location)) (number? (second location))]}
  {:location       location
   :location-wgs84 (coord/convert "EPSG:3067" "WGS84" 5 location)})

(defn tos-function [organization operation-name]
  (get-in organization [:operations-tos-functions (keyword operation-name)]))

(defn make-application
  [id operation-name x y address property-id property-id-source municipality organization info-request? open-inforequest? messages user created manual-schema-datas]
  {:pre [id operation-name address property-id (not (nil? info-request?)) (not (nil? open-inforequest?)) user created]}
  (let [application (merge domain/application-skeleton
                           (permit-type-and-operation-map operation-name created)
                           (location-map (->location x y))
                           {:address             address
                            :auth                (application-auth user operation-name)
                            :comments            (application-comments user messages open-inforequest? created)
                            :created             created
                            :creator             (usr/summary user)
                            :id                  id
                            :infoRequest         info-request?
                            :municipality        municipality
                            :openInfoRequest     open-inforequest?
                            :organization        (:id organization)
                            :propertyId          property-id
                            :schema-version      (schemas/get-latest-schema-version)
                            :state               (application-state user (:id organization) info-request? operation-name)
                            :title               address
                            :tosFunction         (tos-function organization operation-name)}
                           (when-not (#{:location-service nil} (keyword property-id-source))
                             {:propertyIdSource property-id-source}))]
    (-> application
        (merge-in application-timestamp-map)
        (merge-in application-history-map user)
        (merge-in application-attachments-map organization)
        (merge-in application-documents-map user organization manual-schema-datas)
        (merge-in application-metadata-map))))

(defn do-create-application
  [{{:keys [operation x y address propertyId propertyIdSource infoRequest messages]} :data :keys [user created]} & [manual-schema-datas]]
  (let [municipality      (prop/municipality-id-by-property-id propertyId)
        permit-type       (op/permit-type-of-operation operation)
        organization      (org/resolve-organization municipality permit-type)
        scope             (org/resolve-organization-scope municipality permit-type organization)
        organization-id   (:id organization)
        info-request?     (boolean infoRequest)
        open-inforequest? (and info-request? (:open-inforequest scope))]

    (when (ss/blank? organization-id)
      (fail! :error.missing-organization :municipality municipality :permit-type permit-type :operation operation))
    (if info-request?
      (when-not (:inforequest-enabled scope)
        (fail! :error.inforequests-disabled))
      (when-not (:new-application-enabled scope)
        (fail! :error.new-applications-disabled)))

    (let [id (make-application-id municipality)]
      (make-application id
                        operation
                        x
                        y
                        address
                        propertyId
                        propertyIdSource
                        municipality
                        organization
                        info-request?
                        open-inforequest?
                        messages
                        user
                        created
                        manual-schema-datas))))

;;
;; Link permit
;;

(defn make-mongo-id-for-link-permit [app-id link-permit-id]
  (if (<= (compare app-id link-permit-id) 0)
    (str app-id "|" link-permit-id)
    (str link-permit-id "|" app-id)))

(defn do-add-link-permit [{:keys [id propertyId primaryOperation] :as application} link-permit-id]
  {:pre [(mongo/valid-key? link-permit-id)
         (not= id link-permit-id)]}
  (let [db-id (make-mongo-id-for-link-permit id link-permit-id)
        link-application (some-> link-permit-id
                     domain/get-application-no-access-checking
                     meta-fields/enrich-with-link-permit-data)
        max-incoming-link-permits (op/get-primary-operation-metadata link-application :max-incoming-link-permits)
        allowed-link-permit-types (op/get-primary-operation-metadata application :allowed-link-permit-types)]

    (when link-application
      (if (and max-incoming-link-permits (>= (count (:appsLinkingToUs link-application)) max-incoming-link-permits))
        (fail! :error.max-incoming-link-permits))

      (if (and allowed-link-permit-types (not (allowed-link-permit-types (permit/permit-type link-application))))
        (fail! :error.link-permit-wrong-type)))

    (mongo/update-by-id :app-links db-id
                        {:_id           db-id
                         :link          [id link-permit-id]
                         id             {:type       "application"
                                         :apptype    (:name primaryOperation)
                                         :propertyId propertyId}
                         link-permit-id {:type           "linkpermit"
                                         :linkpermittype (if link-application
                                                           "lupapistetunnus"
                                                           "kuntalupatunnus")
                                         :apptype (get-in link-application [:primaryOperation :name])}}
                        :upsert true)))

;; Submit
(defn submit [{:keys [application created user] :as command} ]
  (let [transitions (remove nil?
                            [(when-not (:opened application)
                               [:open created application user])
                             [:submitted created application user]])]
    (action/update-application command (app-state/state-transition-updates transitions)))
  (try
    (mongo/insert :submitted-applications (-> application
                                              meta-fields/enrich-with-link-permit-data
                                              (dissoc :id)
                                              (assoc :_id (:id application))))
    (catch com.mongodb.DuplicateKeyException e
      ; This is ok. Only the first submit is saved.
      )))

;;
;; Updates
;;

(def two-years-ms 63072000000)

(defn warranty-period [timestamp]
  {:warrantyStart timestamp,
   :warrantyEnd (+ timestamp two-years-ms)})

(defn change-application-state-targets
  "Namesake query implementation."
  [{:keys [state] :as application}]
  (let [state (keyword state)
        graph (sm/state-graph application)
        verdict-state (sm/verdict-given-state application)
        target (cond
                 (= state :appealed) :appealed
                 (= state :archived) :open
                 :else verdict-state)]
    (if (= state :underReview)
      (set #{state :archived})
      (set (cons state (remove #{:canceled} (target graph)))))))

(defn valid-new-state
  "Pre-check for change-application-state command."
  [{{new-state :state} :data application :application}]
  (when-not (or (nil? new-state)
                ((change-application-state-targets application) (keyword new-state)))
    (fail :error.illegal-state)))

(defn application-org-authz-users
  [{org-id :organization :as application} org-authz]
  (->> (usr/find-authorized-users-in-org org-id org-authz)
       (map #(select-keys % [:id :firstName :lastName]))))

;; Cancellation

(defn- remove-app-links [id]
  (mongo/remove-many :app-links {:link {$in [id]}}))

(defn cancel-inforequest [{:keys [created user data application] :as command}]
  {:pre [(seq (:application command))]}
  (action/update-application command (app-state/state-transition-update :canceled created application user))
  (remove-app-links (:id application))
  (assignment/cancel-assignments (:id application))
  (ok))

(defn cancel-application
  [{created :created {:keys [id state] :as application} :application {:keys [role] :as user} :user {:keys [lang text]} :data :as command}]
  (let [comment-text (str (i18n/localize lang "application.canceled.text") ". "
                          (i18n/localize lang "application.canceled.reason") ": "
                          text)]
    (->> (util/deep-merge
          (app-state/state-transition-update :canceled created application user)
          (when (seq text)
            (comment/comment-mongo-update state comment-text {:type "application"} role false user nil created)))
         (action/update-application command)))
  (remove-app-links id)
  (assignment/cancel-assignments id)
  (ok))

(defn undo-cancellation
  [{:keys [application created user] :as command}]
  (action/update-application command
                             {:state :canceled}
                             (merge
                               (app-state/state-transition-update
                                 (app-state/get-previous-app-state application)
                                 created
                                 application
                                 user)
                               {$unset {:canceled 1}}))
  (assignment/activate-assignments (:id application))
  (ok))

(defn handler-upsert-updates [handler handlers created user]
  (let [ind (util/position-by-id (:id handler) handlers)]
    {$set  {(util/kw-path :handlers (or ind (count handlers))) handler}
     $push {:history (handler-history-entry (util/assoc-when handler :new-entry (nil? ind)) created user)}}))

(defn autofill-rakennuspaikka [application time & [force?]]
  (when (and (not (= "Y" (:permitType application))) (not (:infoRequest application)))
    (let [rakennuspaikka-docs (domain/get-documents-by-type application :location)]
      (doseq [rakennuspaikka rakennuspaikka-docs
              :when (seq rakennuspaikka)]
        (let [property-id (or (and force? (:propertyId application))
                              (get-in rakennuspaikka [:data :kiinteisto :kiinteistoTunnus :value])
                              (:propertyId application))]
          (doc/fetch-and-persist-ktj-tiedot application rakennuspaikka property-id time))))))

(defn schema-datas [{:keys [rakennusvalvontaasianKuvaus]} buildings]
  (map
    (fn [{:keys [data]}]
      (remove empty? (conj [[[:valtakunnallinenNumero] (:valtakunnallinenNumero data)]
                            [[:kaytto :kayttotarkoitus] (get-in data [:kaytto :kayttotarkoitus])]]
                           (when-not (or (ss/blank? (:rakennusnro data))
                                         (= "000" (:rakennusnro data)))
                             [[:tunnus] (:rakennusnro data)])
                           (when-not (ss/blank? rakennusvalvontaasianKuvaus)
                             [[:kuvaus] rakennusvalvontaasianKuvaus]))))
    buildings))

(defn document-data->op-document [{:keys [schema-version] :as application} data]
  (let [op (make-op :archiving-project (now))
        doc (doc-persistence/new-doc application (schemas/get-schema schema-version "archiving-project") (now))
        doc (assoc-in doc [:schema-info :op] op)
        doc-updates (model/map2updates [] data)]
    (model/apply-updates doc doc-updates)))

(defn fetch-building-xml [organization permit-type property-id]
  (when (and organization permit-type property-id)
    (when-let [{url :url credentials :credentials} (org/get-krysp-wfs {:_id organization} permit-type)]
      (building-reader/building-xml url credentials property-id))))

(defn buildings-for-documents [xml]
  (->> (building-reader/->buildings xml)
       (map #(-> {:data %}))))

(defn update-buildings-array! [xml application]
  (let [doc-buildings (building/building-ids application)
        buildings (building-reader/->buildings-summary xml)
        find-op-id (fn [nid]
                     (->> (filter #(= (:national-id %) nid) doc-buildings)
                          first
                          :operation-id))
        updated-buildings (map
                            (fn [{:keys [nationalId] :as bldg}]
                              (-> (select-keys bldg [:localShortId :buildingId :localId :nationalId :location-wgs84 :location])
                                  (assoc :operationId (find-op-id nationalId))))
                            buildings)]
    (when (seq updated-buildings)
      (mongo/update-by-id :applications
                          (:id application)
                          {$set {:buildings updated-buildings}}))))


(defn fetch-buildings [{:keys [application] :as command} propertyId]
  (let [building-xml              (fetch-building-xml (:organization application) "R" propertyId)
        old-building-docs         (domain/get-documents-by-name application "archiving-project")
        buildings-and-structures  (buildings-for-documents building-xml)
        document-datas            (schema-datas nil buildings-and-structures)
        structure-descriptions    (map :description buildings-and-structures)
        building-docs             (map (partial document-data->op-document application) document-datas)
        primary-operation         (assoc (-> (first building-docs) :schema-info :op) :description (first structure-descriptions))
        secondary-ops             (mapv #(assoc (-> %1 :schema-info :op) :description %2) (rest building-docs) (rest structure-descriptions))
        application               (update-in application [:documents] concat building-docs)
        command                   (util/deep-merge command (action/application->command application))]
  (when (some? (:id primary-operation))
    (do
      (mapv #(doc-persistence/remove! command (:id %) "documents") old-building-docs)
      (action/update-application command {$set  {:primaryOperation    primary-operation
                                                 :secondaryOperations secondary-ops}
                                          $push {:documents {$each building-docs}}})
      (update-buildings-array! building-xml (mongo/by-id :applications (:id application)))))))

(defn remove-secondary-buildings [{:keys [application] :as command}]
  (let [building-docs (domain/get-documents-by-name application "archiving-project")
        primary-op-id (get-in application [:primaryOperation :id])
        secondary-building-docs (filter #(not (= (-> % :schema-info :op :id) primary-op-id)) building-docs)
        secondary-buildings (filter #(not (= (:operationId %) primary-op-id)) (:buildings application))]
    (mapv #(doc-persistence/remove! command (:id %) "documents") secondary-building-docs)
    (action/update-application command {$pull {:buildings {:buildingId {$in (map :buildingId secondary-buildings)}}}})))
