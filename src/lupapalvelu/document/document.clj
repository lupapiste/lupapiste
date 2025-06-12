(ns lupapalvelu.document.document
  (:require [lupapalvelu.application-state :as app-state]
            [lupapalvelu.application.foreman :as foreman]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.document.approval :as approval]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.logging :refer [errorf]]
            [lupapalvelu.operations :as op]
            [lupapalvelu.permissions :refer [defcontext]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [monger.operators :refer :all]
            [sade.core :refer [fail fail!]]
            [sade.strings :as ss]
            [sade.util :as util :refer [fn->]]
            [swiss.arrows :refer [-<>>]]))

;;
;; Validators
;;

(defcontext document-context [{:keys [application user data]}]
  (when-let [document-id (and application (some data [:docId :doc :documentId]))]
    (if-let [doc (domain/get-document-by-id application document-id)]
      (let [schema (model/get-document-schema doc)
            ; At least foreman schema allows access with :foreman role, this resolves if the users application authz
            ; match the one required by the schema, i.e. foreman won't get any role for a document requiring writer
            required-roles (set (get-in schema [:info :user-authz-roles] roles/default-authz-writer-roles))
            actual-role (->> [(auth/user-or-company-authz? required-roles application user)]
                             (filter some?)
                             set)]
        {:context-scope :document
         :context-roles actual-role
         :document      doc})
      (fail! :error.document-not-found))))

(defn state-valid-by-schema?
  "Is a `schema`'s document `schema-states-key` (e.g. :addable-in-states, :editable-in-states) in `state`.
  Uses `default-states` if that information is missing from `schema`."
  [schema schema-states-key default-states state]
  (let [valid-states (or (get-in schema [:info (keyword schema-states-key)]) default-states)]
    (contains? valid-states (keyword state))))

(defn doc-state-editable-by-schema?
  "Is a `schema`'s document `:editable-in-doc-states` in `state`.
  Determines if a document in a certain state can be edited."
  [schema state]
  (state-valid-by-schema? schema :editable-in-doc-states #{} state))

(defn- schema-addable-in-state? [state schema]
  (state-valid-by-schema? schema :addable-in-states states/create-doc-states state))

(defn- allowed-doc-schemas
  "The schemas of documents allowed on `application`."
  [{:keys [schema-version] :as application}]
  (let [op-meta (op/get-primary-operation-metadata application)]
    (->> (concat (:required op-meta) (:optional op-meta))
         (map (partial schemas/get-schema schema-version)))))

(defn- current-doc-schema-names
  "The names of schemas currently on application."
  [{:keys [documents]}]
  (into #{} (map (fn-> :schema-info :name name)) documents))

(defn- addable-schemas [{:keys [permitType schema-version state] :as application}]
  (let [all-allowed-schemas        (allowed-doc-schemas application)
        allowed-repeatable-schemas (filter (fn-> :info :repeating) all-allowed-schemas)
        current-schema-names       (current-doc-schema-names application)
        ;; The schemas of party documents that are currently not on this application but would be allowed:
        missing-schemas            (into #{} (remove (fn-> :info :name current-schema-names)) all-allowed-schemas)
        addable-at-some-time       (if (permit/multiple-parties-allowed? permitType)
                                     (let [applicant-schema-name (op/get-applicant-doc-schema-name application)]
                                       (cond-> (into missing-schemas allowed-repeatable-schemas)
                                         ;; dunno why this is forced here:
                                         applicant-schema-name (conj (schemas/get-schema schema-version applicant-schema-name))))
                                     missing-schemas)
        addable-in-current-state   (into #{} (filter (partial schema-addable-in-state? state) addable-at-some-time))]
    addable-in-current-state))

(defn addable-schema-names
  "Get the set of schema names that could currently be added to `application`."
  [application]
  (into #{} (map (fn-> :info :name)) (addable-schemas application)))

(defn- addable-party-schemas [application]
  (into #{} (filter #(= (-> % :info :type) :party)) (addable-schemas application)))

(defn addable-party-schema-names
  "Get the set of party schema names that could currently be added to `application`."
  [application]
  (into #{} (map (fn-> :info :name)) (addable-party-schemas application)))

(defn- after-verdict? [timestamp application]
  (let [verdict-state        (sm/verdict-given-state application)
        verdict-history-item (->> (app-state/state-history-entries (:history application))
                                  (util/filter-by-key :state (name verdict-state))
                                  (apply util/max-key :ts))]
    (when-not verdict-history-item
      (errorf "Application in %s, but doesn't have %s state in history" (:state application) verdict-state))
    (some->> (:ts verdict-history-item) (> timestamp))))

(defn created-after-verdict? [document application]
  (if (contains? states/post-verdict-states (keyword (:state application)))
    (after-verdict? (:created document) application)
    false))

(defn approved-after-verdict? [document application]
  (if-let [approval (approval/get-approval document)]
    (and (approval/approved? document) (after-verdict? (:timestamp approval) application))
    false))

(defn user-can-be-set? [user-id application]
  (and (auth/has-auth? application user-id) (domain/no-pending-invites? application user-id)))

(defn create-doc-validator
  "Pre-check that ensures that a new document can be added. Depending on
  whether `schemaName` parameter is given, the check is either
  general (some document can be added) or specific (document matching
  `schemaName` can be added). The former is for `allowed-actions`
  command's benefit."
  [{:keys [application], {:keys [schemaName]} :data}]
  (when-let [addable (addable-schema-names application)]
    (when (or (empty? addable)
              (and schemaName (not (contains? addable schemaName))))
     (fail :error.create-doc-not-allowed))))

(defn user-can-be-set-validator [{{user-id :userId} :data application :application}]
  (when-not (or (ss/blank? user-id) (user-can-be-set? user-id application)
                (auth/has-auth-via-company? application user-id))
    (fail :error.application-does-not-have-given-auth)))

(defn- deny-remove-of-non-removable-doc [{{:keys [removable-by]} :schema-info} application user]
  (not (#{:all (auth/application-role application user)} removable-by)))

(defn- deny-remove-of-primary-operation [document application]
  (= (get-in document [:schema-info :op :id]) (get-in application [:primaryOperation :id])))

(defn- deny-remove-of-last-document [{{:keys [last-removable-by name]} :schema-info} {documents :documents :as app} user]
  (and last-removable-by
       (not (#{:all (auth/application-role app user)} last-removable-by))
       (<= (count (domain/get-documents-by-name documents name)) 1)))

(defn- deny-remove-of-non-post-verdict-document [document {state :state :as application}]
  (and (contains? states/post-verdict-states (keyword state)) (not (created-after-verdict? document application))))

(defn- deny-remove-of-approved-post-verdict-document [document application]
  (and (created-after-verdict? document application) (approval/approved? document)))

(defn- permit-removal-of-empty-foreman-doc?
  "Allow removing sufficiently empty foreman documents."
  [document]
  (and (:tj-document document)
       (ss/blank? (foreman/tj-document->email document))
       (ss/blank? (foreman/tj-document->fullname document))
       (not (foreman/tj-document->foreman-consent document))))

(defn remove-doc-validator [{:keys [document user application]}]
  (when document
    (cond
      (deny-remove-of-non-removable-doc document application user)         (fail :error.not-allowed-to-remove-document)
      (deny-remove-of-last-document document application user)             (fail :error.removal-of-last-document-denied)
      (deny-remove-of-primary-operation document application)              (fail :error.removal-of-primary-document-denied)
      (permit-removal-of-empty-foreman-doc? document)                      nil
      (deny-remove-of-non-post-verdict-document document application)      (fail :error.document.post-verdict-deletion)
      (deny-remove-of-approved-post-verdict-document document application) (fail :error.document.post-verdict-deletion))))

(defn- can-edit-converted-application?
  "True if the target application is converted and the user has
  `:application/edit-converted-application?` permission."
  [{:keys [permissions application]}]
  (and (:facta-imported application)
       (contains? permissions :application/edit-converted-application)))

(defn validate-post-verdict-not-approved-or-locked
  "In post verdict states, validates that given document is not approved or locked.
   Approval 'locks' documents in post-verdict state. However, if the application is
  converted and the user is the application authority, it can still be edited."
  [{:keys [application document] :as command}]
  (when (and document
             application
             (not (can-edit-converted-application? command))
             (contains? states/post-verdict-states (keyword (:state application)))
             (or (approval/approved? document)
                 (approval/locked? document)))
    (fail :error.document.approved)))

(defn validate-created-after-verdict
  "In post-verdict state, validates that document is post-verdict-party and it's not created-after-verdict.
   This is special case for post-verdict-parties. Also waste schemas can be edited in post-verdict states, though
   they have been created before verdict. Thus we are only interested in 'post-verdict-party' documents here."
  [{:keys [application document]}]
  (when (and document
             application
             (contains? states/post-verdict-states (keyword (:state application)))
             (get-in document [:schema-info :post-verdict-party])
             (not (created-after-verdict? document application)))
    (fail :error.document.pre-verdict-document)))

(defn doc-disabled-validator
  "Deny action if document is marked as disabled"
  [{:keys [document]}]
  (when (:disabled document)
    (fail :error.document.disabled)))

(defn document-disableable-precheck
  "Checks if document can be disabled from document's schema"
  [{:keys [document]}]
  (when document
    (when-not (get-in document [:schema-info :disableable])
      (fail :error.document.not-disableable))))

(defn validate-document-is-pre-verdict-or-approved
  "Pre-check for document disabling. If document is added after verdict, it needs to be approved."
  [{:keys [application document]}]
  (when document
    (when-not (or (not (created-after-verdict? document application)) (approval/approved? document))
      (fail :error.document-not-approved))))

(defn is-identifier
  "Precheck that fails if the identifier parameter does not refer to an
  identifier schema."
  [{:keys [document data]}]
  (when-let [identifier (:identifier data)]
    (when-not (some->> document
                       model/get-document-schema
                       :body
                       (util/find-by-key :name identifier)
                       :identifier)
      (fail :error.not-identifier))))

;;
;; Document updates
;;

(defn generate-remove-invalid-user-from-docs-updates [{docs :documents :as application}]
  (-<>> docs
        (map-indexed
          (fn [i doc]
            (->> (model/validate application doc)
                 (filter #(= (:result %) [:warn "application-does-not-have-given-auth"]))
                 (map (comp (partial map name) :path))
                 (map (comp (partial ss/join ".") (partial concat ["documents" i "data"]))))))
        flatten
        (zipmap <> (repeat ""))))


;;
;; Assignments
;;

(defn- document-assignment-info
  "Return document info as assignment target"
  [operations {{name :name doc-op :op} :schema-info id :id :as doc}]
  (let [accordion-datas (schemas/resolve-accordion-field-values doc)
        op-description (:description (util/find-by-id (:id doc-op) operations))]
    (util/assoc-when-pred {:id id :type-key (ss/join "." [name "_group_label"])} ss/not-blank?
                          :description (or op-description (ss/join " " accordion-datas)))))

(defn- describe-parties-assignment-targets [application]
  (->> (domain/get-documents-by-type application :party)
       (remove :disabled)
       (sort-by tools/document-ordering-fn)
       (mapv (partial document-assignment-info nil))))

(defn- describe-non-party-document-assignment-targets [{:keys [documents primaryOperation secondaryOperations] :as application}]
  (let [party-doc-ids (set (map :id (domain/get-documents-by-type application :party)))
        operations    (cons primaryOperation secondaryOperations)]
    (->> (remove (comp party-doc-ids :id) documents)
         (remove :disabled)
         (sort-by tools/document-ordering-fn)
         (mapv (partial document-assignment-info operations)))))

(assignment/register-assignment-target! :parties describe-parties-assignment-targets)

(assignment/register-assignment-target! :documents describe-non-party-document-assignment-targets)

(defn get-schema-name [document]
  (-> document :schema-info :name))

(defn- other-doc-of-same-name-active? [document documents]
  (->> documents
       (filter #(and (not= (:id %) (:id document))
                     (= (get-schema-name %)
                        (get-schema-name document))))
       (not-every? :disabled)))

(defn editable-by-state?
  "Pre-check to determine if documents are editable in abnormal states"
  [default-states]
  (fn [{:keys [document] {:keys [state documents]} :application :as command}]
    (let [doc-state     (:state document)
          can-edit?     (or (nil? document)
                            (can-edit-converted-application? command)
                            (some-> document
                                    (model/get-document-schema)
                                    (state-valid-by-schema? :editable-in-states default-states state))
                            (some-> document
                                    (model/get-document-schema)
                                    (doc-state-editable-by-schema? doc-state)))
          other-active? (and can-edit?
                             (-> document :schema-info (contains? :only-one-active))
                             (document :disabled)
                             (other-doc-of-same-name-active? document documents))]
      (cond
        (not can-edit?) (fail :error.document-not-editable-in-current-state)
        other-active?   (fail :error.only-one-active)))))

(defn designer-document? [document]
  (= :suunnittelija (tools/doc-subtype document)))

(defn- designer-wrap-for-post-verdict-checks
  "The rejected designer documents can always be edited. Otherwise, given pre-checkers are
  run."
  [& pre-checks]
  (let [rejected-designer-doc? (every-pred designer-document? approval/rejected?)
        check                  (apply some-fn pre-checks)]
    (fn [{:keys [document] :as command}]
      (when document
        (when-not (rejected-designer-doc? document)
          (check command))))))

(def update-doc-pre-checks
  "Pre-checks that verify the document can be edited."
  [(editable-by-state? states/update-doc-states)
   doc-disabled-validator
   (designer-wrap-for-post-verdict-checks validate-created-after-verdict
                                          validate-post-verdict-not-approved-or-locked)])
