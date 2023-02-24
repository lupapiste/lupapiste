(ns lupapalvelu.application
  (:require [clj-time.core :refer [year]]
            [clj-time.local :refer [local-now]]
            [clojure.set :refer [intersection]]
            [clojure.walk :refer [keywordize-keys]]
            [lupapalvelu.action :as action]
            [lupapalvelu.allu.allu-application :refer [allu-application?]]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.application-utils :as app-utils :refer [location->object
                                                                 get-operations]]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.building-attributes :as ba]
            [lupapalvelu.building-site :as bsite]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.company :as com]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.rakennuslupa-canonical :refer [fix-legacy-apartments]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.link-permit :as link-permit]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.permissions :refer [defcontext] :as permissions]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.property :as prop]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [lupapalvelu.task-util :as task-util]
            [lupapalvelu.tiedonohjaus :as tos]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.coordinate :as coord]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util :refer [merge-in]]
            [sade.validators :as validators]
            [schema.core :as sc]
            [slingshot.slingshot :refer [try+]]
            [taoensso.timbre :refer [warn warnf errorf]])
  (:import [com.mongodb DuplicateKeyException])
  (:import (java.net SocketTimeoutException)))

(defcontext canceled-app-context [{{user-id :id} :user application :application}]
  (let [last-history-entry (app-state/last-history-item application)]
    (when (and (= user-id (get-in last-history-entry [:user :id]))
               (-> last-history-entry :state keyword (= :canceled)))
      {:context-scope :canceled-app
       :context-roles [:canceler]})))

(defn resolve-valid-subtypes
  "Returns a list with no duplicates (~= set) of valid permit and operation subtypes for the application."
  [{permit-type :permitType op :primaryOperation org :organization}]
  (let [op-subtypes (op/get-primary-operation-metadata {:primaryOperation op} :subtypes)
        permit-subtypes (permit/permit-subtypes permit-type)
        all-subtypes (distinct (concat op-subtypes permit-subtypes))]
    ;; If organization is 091-YA (= Helsinki yleiset alueet) and the user is requesting sijoituslupa or sijoitussopimus,
    ;; return a list where :sijoitussopimus comes before :sijoituslupa so it acts as a default value
    (if (and (= "091-YA" org) (= (set all-subtypes) #{:sijoitussopimus :sijoituslupa}))
      (reverse all-subtypes)
      all-subtypes)))

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

(defn ymp-clarification-app? [application]
  (-> application :primaryOperation :name (= "ymparistoluvan-selventaminen")))

(defn previous-permit? [{:keys [primaryOperation secondaryOperations]}]
  (boolean (some #(= (:name %) "aiemmalla-luvalla-hakeminen")
                 (cons primaryOperation secondaryOperations))))

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

;;
;; Helpers
;;

(defn user-role
  "User role within the application."
  [user {:keys [organization]}]
  (if (usr/user-is-authority-in-organization? user organization)
    :authority
    :applicant))

(defn insert-application [application]
  {:pre [(every? (partial contains? application)  (keys domain/application-skeleton))]}
  (mongo/insert :applications (merge application (meta-fields/applicant-index application))))

(defn enrich-application-handlers [application {roles :handler-roles}]
  (update application :handlers (partial map #(merge (util/find-by-id (:roleId %) roles) %))))

(defn enrich-building [organization extra-attributes-by-national-id {:keys [nationalId] :as building}]
  (let [{:keys [visibility publicity] :as extra-attributes} (extra-attributes-by-national-id nationalId)
        metadata (cond-> {}
                   extra-attributes (assoc :myyntipalvelu (ba/->myyntipalvelu-in-onkalo-update extra-attributes))
                   publicity        (assoc :julkisuusluokka publicity)
                   visibility       (assoc :nakyvyys visibility))]
    (assoc building :metadata metadata)))

(defn- associate-by [f coll]
  (zipmap (map f coll) coll))

(defn enrich-buildings [{:keys [buildings organization] :as application}]
  (let [building-ids (map :nationalId buildings)
        extra-attributes-by-national-id (->> (ba/fetch-buildings organization {:vtjprts building-ids})
                                             (associate-by :vtjprt))
        enriched-buildings (map (partial enrich-building organization extra-attributes-by-national-id) buildings)]
    (assoc application :buildings enriched-buildings)))

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

(defn pertinent-validation-errors [{:keys [application] :as command}]
  (map (partial model/validate-pertinent command) (:documents application)))

(defn validate-fully-formed
 "If the application's organization requires fully-formed
  applications, the following checks are enforced:
  1. All the required fields are valid
  2. Every required attachment is either filled or marked not needed
  This function is called from submit-validation-errors on the api side.
  Returns nil on success and fail map if the application is not fully formed."
  [{:keys [organization application] :as command}]
  (when (and (some-> organization
                     force
                     :app-required-fields-filling-obligatory)
             (or (->> (pertinent-validation-errors command)
                      flatten
                      (remove #(-> % :result first (= :tip)))
                      (some #(-> % :element :required)))
                 (some (fn [{:keys [required notNeeded versions]}]
                         (and required (not notNeeded) (empty? versions)))
                       (:attachments application))))
    (fail :application.requiredDataDesc)))

(defn- validate [command document]
  (let [all-results   (model/validate-pertinent command document)
        ; sorting result in ascending order on severity as only the last error ends up visible in the docgen UI per field
        all-results   (sort-by #(get {:tip   0
                                      :warn  1
                                      :error 2} (get-in % [:result 0])) all-results)
        create-result (fn [document result]
                        (let [path  (vec (flatten [:data (:path result)]))
                              field (get-in document path)]
                          (when (and (some? field)
                                     (not (map? field)))
                            (errorf "Invalid document data in %s: %s" (-> command :application :id) field))
                          (assoc-in document (conj path :validationResult) (:result result))))]
    (assoc (reduce create-result document all-results) :validationErrors all-results)))

(defn populate-operation-info [operation-map {info :schema-info :as doc}]
  (if (:op info)
    (if-let [operation (get operation-map (get-in info [:op :id]))]
      (assoc-in doc [:schema-info :op] operation)
      (do
        (warnf "Couldn't find operation %s for doc %s " (get-in info [:op :id]) (:id doc))
        doc))
    doc))

(defn id-to-operation-map [application]
  (->> (get-operations application)
       (reduce (fn [opmap {:keys [id] :as op}]
                 (assoc opmap id op))
               {})))

(defn process-document-or-task [{:keys [user application] :as command} doc]
  (->> (validate command doc)
       (populate-operation-info (id-to-operation-map application))
       ((app-utils/person-id-masker-for-user user application))
       (enrich-single-doc-disabled-flag user application)
       (fix-legacy-apartments application)
       (task-util/enrich-default-task-reviewer command)))

(defn- process-documents-and-tasks [command {:keys [documents tasks] :as application}]
  (let [;; Faulty reviews only available to authorities.
        tasks  (cond->> tasks
                 (not (usr/user-has-role-in-organization? (usr/with-org-auth (:user command))
                                                          (:organization application)
                                                          roles/reader-org-authz-roles))
                 (remove task-util/faulty?))
        mapper (partial process-document-or-task (assoc command :application application))
        fdocs  (future (pmap mapper documents))
        ftasks (future (pmap mapper tasks))]
    (assoc application :documents @fdocs
                       :tasks @ftasks)))

(defn ->location [x y]
  [(util/->double x) (util/->double y)])

(defn get-link-permit-apps
  "Return associated link-permit application."
  [{:keys [linkPermitData]}]
  (when-let [links (not-empty (filter (comp #{"lupapistetunnus"} :type) linkPermitData))]
    (->> (map :id links)
         (domain/get-multiple-applications-no-access-checking))))

;;
;; Application query post process
;;

(defn- with-auth-models [{:keys [application] :as command}]
  (let [document-authz (action/allowed-actions-for-category (assoc-in command [:data :category] "documents"))]
    (update application :documents #(map (fn [doc] (assoc doc :allowedActions (get document-authz (:id doc)))) %))))

(def merge-operation-skeleton (partial merge domain/operation-skeleton))

(defn- with-allowed-attachment-types [organization application]
  (assoc application :allowedAttachmentTypes
                     (-> (att-type/organization->organization-attachment-settings (force organization))
                         (att-type/get-attachment-types-for-application application)
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

(defn- remove-draft-foreman-links [user application]
  (let [application-authority? (auth/application-authority? application user)
        application-writer? (auth/has-auth-role? application (:id user) :writer)]
    (if (and application-authority? (not application-writer?))
      (update application :appsLinkingToUs
              (util/fn->> (remove (fn [link-model]
                                    (and (util/=as-kw (:operation link-model) :tyonjohtajan-nimeaminen-v2)
                                         (= (:state link-model) "draft"))))
                          not-empty))
      application)))

(defn with-municipality-permit-ids
  "Assocs municipality permit ids found in published verdicts to
  `:municipalityVerdictIds`, or all verdicts if the application is an
  archiving project."
  [application]

  (assoc application
         :municipalityPermitIds
         (cond
           (or (permit/archiving-project? application) (:facta-imported application))
           (vif/kuntalupatunnukset application)

           (allu-application? (:organization application) (:permitType application)) ; HACK
           (->> (vif/published-municipality-permit-ids application)
                (into (or (some-> application :integrationKeys :ALLU :kuntalupatunnus vector) []))
                distinct)

           :else (vif/published-municipality-permit-ids application))))

(defn enrich-document-building [organization secret-vtjprts {:keys [vtj-prt] :as document-building}]
  (assoc document-building :secret (contains? (set secret-vtjprts) vtj-prt)))

(defn enrich-document-buildings [{:keys [document-buildings organization] :as application}]
  (let [secret-vtjprts (->> (map :vtj-prt document-buildings)
                            (ba/fetch-secret-buildings organization)
                            (map :vtjprt))
        enriched-buildings (map (partial enrich-document-building organization secret-vtjprts) document-buildings)]
    (assoc application :document-buildings enriched-buildings)))

(defn remove-attachment-map
  "Remove the `:attachments-by-id` quick access map set in lupapalvelu.domain/add-attachment-map"
  [application]
  (dissoc application :attachments-by-id))

(defn post-process-app [{:keys [user organization] :as command}]
  (->> (with-auth-models command)
       (with-allowed-attachment-types @organization)
       ensure-operations
       enrich-primary-operation-with-metadata
       att/post-process-attachments
       meta-fields/enrich-with-link-permit-data
       (remove-draft-foreman-links user)
       (meta-fields/with-meta-fields user)
       action/without-system-keys
       (process-documents-and-tasks command)
       with-municipality-permit-ids
       enrich-document-buildings
       location->object
       remove-attachment-map
       (task-util/with-review-officers command)))

(defn post-process-app-for-krysp [application organization]
  (-> application
      (domain/enrich-application-handlers organization)
      (domain/enrich-application-tags organization)
      enrich-tos-function-name
      meta-fields/enrich-with-link-permit-data
      link-permit/update-backend-ids-in-link-permit-data
      remove-attachment-map))

;;
;; Application creation
;;

(defn new-attachment-types-for-operation [organization operation existing-types]
  (->> (org/get-organization-attachments-for-operation organization operation)
       (map (partial apply att-type/attachment-type))
       (filter #(or (att-type/operation-specific? %) (not (att-type/contains? existing-types %))))))

(defn- attachment-grouping-for-type [operation {{group-type :grouping} :metadata}]
  (when-not (false? (op/get-operation-metadata (:name operation) :attachment-op-selector))
    (util/assoc-when nil :groupType group-type :operations (when (and operation (= group-type :operation)) [operation]))))

(defn make-attachments
  [created operation organization applicationState tos-function & {:keys [target existing-attachments-types]}]
  (let [types      (new-attachment-types-for-operation organization operation existing-attachments-types)
        groups     (map (partial attachment-grouping-for-type operation) types)
        metadatas  (pmap (partial tos/metadata-for-document (:id organization) tos-function) types)
        mandatory? (boolean (some-> organization
                                    :default-attachments-mandatory
                                    (util/includes-as-kw? (:name operation))))]
    (map (partial att/make-attachment created target true mandatory? false (keyword applicationState)) groups types metadatas)))

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

(defn schema-data-to-body [schema-data]
  (keywordize-keys
    (reduce
      (fn [body [data-path data-value]]
        (let [path (if (= :value (last data-path)) data-path (conj (vec data-path) :value))]
          (assoc-in body path (if (or (seq? data-value)
                                      (vector? data-value))
                                (do (warnf "Multiple values found in %s: %s" data-path (doall data-value))
                                    (first data-value))
                                data-value))))
      {} schema-data)))


(defn make-document [primary-operation-name created manual-schema-datas schema]
  (let [op-info (op/operations (keyword primary-operation-name))
        op-schema-name (:schema op-info)
        default-schema-datas (util/assoc-when-pred {} util/not-empty-or-nil?
                                                   op-schema-name
                                                   (:schema-data op-info))
        merged-schema-datas (merge-with concat default-schema-datas manual-schema-datas)
        schema-name (get-in schema [:info :name])]
    (-> schema
        (model/new-document created)
        (update :data util/deep-merge (tools/timestamped
                                        (if-let [schema-data (get-in merged-schema-datas [schema-name])]
                                          (schema-data-to-body schema-data)
                                          {})
                                        created)))))

(defn make-documents [user created org op application & [manual-schema-datas]]
  {:pre [(or (nil? manual-schema-datas) (map? manual-schema-datas))]}
  (let [op-info (op/operations (keyword (:name op)))
        op-schema-name (:schema op-info)
        schema-version (:schema-version application)

        make (partial make-document (:name op) created manual-schema-datas)

        op-doc (update (make (schemas/get-schema schema-version op-schema-name)) :schema-info assoc :op op)

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
    (or (usr/user-has-role-in-organization? user organization-id roles/reader-org-authz-roles)
        (usr/rest-user? user)
        (= "ARK" (op/permit-type-of-operation operation-name))) :open
    :else :draft))

(defn application-history-map [{:keys [created organization state tosFunction]} user]
  {:pre [(pos? created) (string? organization) (states/all-states (keyword state))]}
  (let [tos-function-map (tos/tos-function-with-name tosFunction organization)]
    {:history (cond->> [(app-state/history-entry state created user)]
                tos-function-map (concat [(tos/tos-history-entry tos-function-map created user)]))}))

(defn permit-type-and-operation-map [operation-name organization created]
  (let [op (make-op operation-name created)
        classification {:permitType       (op/permit-type-of-operation operation-name)
                        :primaryOperation op}]
    (merge classification
           {:permitSubtype (first (resolve-valid-subtypes (assoc classification :organization organization)))})))

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

(defn application-documents-map [{:keys [infoRequest created primaryOperation] :as application} user organization manual-schema-datas]
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

(sc/defschema MakeApplicationSchema
  {:id                                 ssc/ApplicationId
   :organization                       org/Organization
   :propertyId                         sc/Str
   :municipality                       sc/Str
   :address                            sc/Str
   :operation-name                     (apply sc/enum (map name (keys op/operations)))
   :location                           [(sc/one ssc/LocationX "X coordinate")
                                        (sc/one ssc/LocationY "Y coordinate")]
   (sc/optional-key :infoRequest)      sc/Bool
   (sc/optional-key :openInfoRequest)  sc/Bool
   (sc/optional-key :propertyIdSource) sc/Str})

(sc/defn ^:always-validate make-application
  [{:keys [id operation-name propertyIdSource
           openInfoRequest infoRequest
           organization] :as application-info} :- MakeApplicationSchema
   messages
   user
   created
   manual-schema-datas]
  {:pre [user created]}
  (let [application (merge domain/application-skeleton
                           (dissoc application-info :propertyIdSource)
                           (permit-type-and-operation-map operation-name (:id organization) created)
                           (location-map (:location application-info))
                           {:auth            (application-auth user operation-name)
                            :comments        (application-comments user messages openInfoRequest created)
                            :created         created
                            :creator         (usr/summary user)
                            :_creatorIndex   (usr/full-name user)
                            :id              id
                            :infoRequest     (or infoRequest false)
                            :openInfoRequest (or openInfoRequest false)
                            :municipality    (:municipality application-info)
                            :organization    (:id organization)
                            :propertyId      (:propertyId application-info)
                            :schema-version  (schemas/get-latest-schema-version)
                            :state           (application-state user (:id organization) infoRequest operation-name)
                            :title           (:address application-info)
                            :tosFunction     (tos-function organization operation-name)}
                           (when-not (#{:location-service nil} (keyword propertyIdSource))
                             {:propertyIdSource propertyIdSource}))]
    (-> application
        (merge-in application-timestamp-map)
        (merge-in application-history-map user)
        (merge-in application-attachments-map organization)
        (merge-in application-documents-map user organization manual-schema-datas)
        (merge-in application-metadata-map))))

(defn do-create-application
  [{{:keys [operation x y address propertyId propertyIdSource infoRequest messages]} :data :keys [user created]} & [manual-schema-datas]]
  (let [municipality      (prop/municipality-by-property-id propertyId)
        permit-type       (op/permit-type-of-operation operation)
        organization      (org/resolve-organization municipality permit-type)
        scope             (org/resolve-organization-scope municipality permit-type organization)
        organization-id   (:id organization)
        info-request?     (boolean infoRequest)
        open-inforequest? (and info-request?
                               (:open-inforequest scope)
                               (empty? (some-> organization :notifications
                                               :inforequest-notification-emails))
                               (validators/valid-email? (:open-inforequest-email scope)))]

    (when (ss/blank? organization-id)
      (fail! :error.missing-organization :municipality municipality :permit-type permit-type :operation operation))
    (if info-request?
      (when-not (:inforequest-enabled scope)
        (fail! :error.inforequests-disabled))
      (when-not (:new-application-enabled scope)
        (fail! :error.new-applications-disabled)))

    (let [id               (make-application-id municipality)
          application-info (util/assoc-when-pred
                             {:id              id
                              :organization    organization
                              :operation-name  operation
                              :location        (->location x y)
                              :propertyId      propertyId
                              :address         address
                              :infoRequest     info-request?
                              :openInfoRequest open-inforequest?}
                             ss/not-blank?
                             :propertyIdSource propertyIdSource
                             :municipality municipality)]
      (make-application application-info
                        messages
                        user
                        created
                        manual-schema-datas))))

;;
;; Operation
;;

(defn add-operation [{{app-state :state tos-function :tosFunction :as application} :application
                      organization :organization
                      created :created
                      :as command}
                     id
                     operation]
  (let [op                 (make-op operation created)
        new-docs           (make-documents nil created @organization op application)
        attachments        (:attachments (domain/get-application-no-access-checking id {:attachments true}))
        new-attachments    (make-attachments created op @organization app-state tos-function :existing-attachments-types (map :type attachments))
        attachment-updates (multioperation-attachment-updates op @organization attachments)]
    (action/update-application command {$push {:secondaryOperations  op
                                               :documents   {$each new-docs}
                                               :attachments {$each new-attachments}}
                                        $set  {:modified created}})
    ;; Cannot update existing array and push new items into it same time with one update
    (when (not-empty attachment-updates) (action/update-application command attachment-updates))))

(defn change-primary-operation [{:keys [application] :as command} secondaryOperationId]
  (let [old-primary-op                       (:primaryOperation application)
        old-secondary-ops                    (:secondaryOperations application)
        new-primary-op                       (util/find-first #(= secondaryOperationId (:id %)) old-secondary-ops)
        secondary-ops-without-new-primary-op (remove #(= (:id new-primary-op) (:id %)) old-secondary-ops)
        new-secondary-ops                    (conj secondary-ops-without-new-primary-op old-primary-op)]
    (when-not (= (:id old-primary-op) secondaryOperationId)
      (when-not new-primary-op
        (fail! :error.unknown-operation))
      ;; TODO update also :app-links apptype if application is linked to other apps (loose WriteConcern ok?)
      (action/update-application command {$set {:primaryOperation    new-primary-op
                                                :secondaryOperations new-secondary-ops}})
      {:primaryOperation    new-primary-op
       :secondaryOperations new-secondary-ops})))

;;
;; Link permit
;;

(defn make-mongo-id-for-link-permit [app-id link-permit-id]
  (if (<= (compare app-id link-permit-id) 0)
    (str app-id "|" link-permit-id)
    (str link-permit-id "|" app-id)))

(defn are-linked? [app-id link-permit-id]
  (pos? (count (mongo/select :app-links {:_id (make-mongo-id-for-link-permit app-id link-permit-id)}))))

(defn do-add-link-permit
  "Note the terminology: here 'application' = FROM and link-permit-id = TO. For example, if we're linking foreman-application
  to building application, the foreman comes first here."
  [{:keys [id propertyId primaryOperation] :as application} link-permit-id]
  {:pre [(mongo/valid-key? link-permit-id)
         (not= id link-permit-id)]}
  (let [db-id (make-mongo-id-for-link-permit id link-permit-id)
        link-application (some-> link-permit-id
                           domain/get-application-no-access-checking
                           meta-fields/enrich-with-link-permit-data)
        max-incoming-link-permits (op/get-primary-operation-metadata link-application :max-incoming-link-permits)
        allowed-link-permit-types (op/get-primary-operation-metadata application :allowed-link-permit-types)]

    (if link-application
      (do
        (if (and max-incoming-link-permits (>= (count (:appsLinkingToUs link-application)) max-incoming-link-permits))
          (fail! :error.max-incoming-link-permits))

        (if (and allowed-link-permit-types (not (allowed-link-permit-types (permit/permit-type link-application))))
          (fail! :error.link-permit-wrong-type)))
      (when (= "KT" (:permitType application))
        (fail! :error.link-permit-not-allowed-to-use-kuntalupatunnus)))

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

(defn get-lp-ids-by-kuntalupatunnus [organization-id kuntalupatunnus]
  (map :id (mongo/select :applications
                         {:organization organization-id
                          $or [{:verdicts.kuntalupatunnus kuntalupatunnus}       ;; Backing system
                               {:pate-verdicts.kuntalupatunnus kuntalupatunnus}  ;; Legacy published
                               {:pate-verdicts.kuntalupatunnus._value kuntalupatunnus}]}  ;; Legacy draft
                         {:_id 1})))

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
                                              (dissoc :id :attachments-by-id)
                                              (assoc :_id (:id application))))
    (catch DuplicateKeyException _
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
  (let [state         (keyword state)
        graph         (sm/state-graph application)
        verdict-state (sm/verdict-given-state application)
        target        (cond
                        (= state :appealed) :appealed
                        (= state :archived) :open
                        :else               (or verdict-state state))
        states        (case state
                        :underReview [:archived]
                        :ready       [:appealed :extinct]
                        (remove #{:canceled} (target graph)))]
    (some->> (seq states) (cons state) set)))

(defn valid-new-state
  "Pre-check for change-application-state command."
  [{{new-state :state} :data application :application}]
  (when-not (or (nil? new-state)
                ((change-application-state-targets application) (keyword new-state)))
    (fail :error.illegal-state)))

(defn application-org-authz-users [{org-id :organization} org-authz]
  (->> (usr/find-authorized-users-in-org org-id org-authz [:id :firstName :lastName :orgAuthz])
       (map (fn [user]
              (cond-> (dissoc user :orgAuthz)
                (> (count org-authz) 1)
                (assoc :roles (util/intersection-as-kw org-authz
                                                       (get-in user [:orgAuthz (keyword org-id)]))))))))

(defn add-continuation-period [application link-permit-id handler period-end]
  (let [period {:handler               handler
                :continuationAppId     link-permit-id
                :continuationPeriodEnd period-end}]
    (action/update-application (action/application->command application) {$push {:continuationPeriods period}})))

;; Cancellation

(defn- remove-app-links [id]
  (mongo/remove-many :app-links {:link {$in [id]}}))

(defn cancel-inforequest [{:keys [created user application] :as command}]
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

(defn handler-upsert-updates [handler handlers timestamp user]
  (let [ind (util/position-by-id (:id handler) handlers)]
    {$set  {(util/kw-path :handlers (or ind (count handlers))) handler
            :modified timestamp}
     $push {:history (handler-history-entry (util/assoc-when handler :new-entry (nil? ind)) timestamp user)}}))

(defn autofill-rakennuspaikka [application time & [force?]]
  (when (and (not= "Y" (:permitType application)) (not (:infoRequest application)))
    (let [rakennuspaikka-docs (domain/get-documents-by-type application :location)]
      (doseq [rakennuspaikka rakennuspaikka-docs
              :when (seq rakennuspaikka)]
        (let [property-id (or (and force? (:propertyId application))
                              (get-in rakennuspaikka [:data :kiinteisto :kiinteistoTunnus :value])
                              (:propertyId application))]
          (bsite/fetch-and-persist-ktj-tiedot application rakennuspaikka property-id time))))))

(defn try-autofill-rakennuspaikka [application timestamp]
  (try+
    (autofill-rakennuspaikka application timestamp true)
    (catch [:sade.core/type :sade.core/fail] {:keys [cause text] :as exp}
      (warnf "Could not get KTJ data for the new application, cause: %s, text: %s. From %s:%s"
             cause
             text
             (:sade.core/file exp)
             (:sade.core/line exp)))
    (catch SocketTimeoutException _
      (warn "Socket timeout from KTJ when creating application"))))

(defn- anonymize-values [updates]
  (for [[k v] updates
        :let [keyset (set k)
              new-val (cond
                        (keyset :etunimi) "Pena"
                        (keyset :sukunimi) "Panaani"
                        (seq (intersection keyset #{:katu :osoitenimi :nimi})) "Paapankuja 1 A 1"
                        (keyset :hetu) "131052-308T"
                        (seq (intersection keyset #{:email :sahkopostiosoite})) "pena@example.com"
                        (keyset :liikeJaYhteisoTunnus) "123123980"
                        (keyset :puhelin) "012-3456789"
                        (keyset :yritysnimi) "Penan Panaanitarha")]
        :when (and (string? v)
                   (pos? (count v))
                   new-val)]
    [k new-val]))

(defn anonymize-parties
 "Takes a document from an application and sets Pena Panaani as the party in question."
 [document]
  (loop [updates (->> document (model/map2updates []) anonymize-values)
         doc document]
    (if (empty? updates)
      doc
      (let [[path data] (first updates)]
        (recur (rest updates)
               (assoc-in doc path data))))))

(defn anonymize-application [{:keys [documents] :as app}]
  (assoc app :documents (map anonymize-parties documents)
         :applicant "Pena Panaani"))

(defn anonymize-application-by-id!
  "Takes an LP id and anonymizes the said application in the database.
  Note that only the parties are anonymized while other application
  data is left intact."
  [id]
  (let [updated-app (->> id (mongo/by-id :applications) anonymize-application)
        applicant-index (meta-fields/applicant-index updated-app)]
    (mongo/update-by-id :applications id {$set (merge applicant-index
                                                {:documents (:documents updated-app)})})))

(defn sanitize-document-datas
  "This cleans document datas of all the key-value pairs that are not found in the
  given schema. Failure to do this results in smoke-tests breaking, plus the data
  wouldn't end up to the created application anyway."
  [schema document-datas]
  (filter (fn [[k _v]]
              (model/find-by-name (:body schema) k))
          document-datas))

(defn document-data->op-document
  "If no operation name is provided, this defaults to \"archiving-project\""
  ([application data]
   (document-data->op-document application data "archiving-project"))
  ([{:keys [schema-version] :as application} data operation-name]
    (let [schema-name (-> operation-name op/get-operation-metadata :schema)
          schema (schemas/get-schema schema-version schema-name)
          op (make-op operation-name (now))
          doc (doc-persistence/new-doc application schema (now))
          doc (assoc-in doc [:schema-info :op] op)
          doc-updates (sanitize-document-datas schema (if (map? data)               ;; The incoming data should already be an update vector
                                                        (model/map2updates [] data) ;; sequence, but we'll perform an extra
                                                        data))]                     ;; validation here to be 100 % sure.
      (lupapalvelu.document.model/apply-updates doc doc-updates))))

(def jatkolupa-operations
  "Note that legacy permit/R applications may also have the operation `jatkoaika`
  but it is not included here since `raktyo-aloit-loppuunsaat` has superceded it (PATE-48 / LPK-4680)."
  {permit/R   :raktyo-aloit-loppuunsaat
   permit/YA  :ya-jatkoaika
   permit/MAL :maa-aineslupa-jatkoaika})

(defn jatkoaika-application? [application]
  (let [primary-operation (get-in application [:primaryOperation :name])]
    (or (= primary-operation "raktyo-aloit-loppuunsaat")
        (= primary-operation "jatkoaika")
        (= primary-operation "ya-jatkoaika")
        (= primary-operation "maa-aineslupa-jatkoaika"))))


;;
;; Kuntalupatunnus from ALLU
;;
(defn set-kuntalupatunnus
  "Store kuntalupatunnus here where it's read when creating a verdict. When creating a verdict kuntalupatunnus is
  placed with other verdict data where UI can find it (see lupapalvelu.backing-system.allu.contract/new-allu-contract)."
  [app-id kuntalupatunnus]
  (mongo/update-by-id :applications app-id {$set {:integrationKeys.ALLU.kuntalupatunnus kuntalupatunnus}}))

;;
;; Integration keys
;;

(defn set-integration-key [app-id system-name key-data]
  (mongo/update-by-id :applications app-id {$set {(str "integrationKeys." (name system-name)) key-data}}))

(defn load-integration-key [app-id system-name]
  (get-in (mongo/by-id :applications app-id {:integrationKeys true}) [:integrationKeys system-name]))
