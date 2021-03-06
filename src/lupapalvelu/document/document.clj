(ns lupapalvelu.document.document
  (:require [taoensso.timbre :refer [trace debug debugf info infof warn warnf error]]
            [monger.operators :refer :all]
            [swiss.arrows :refer [-<>>]]
            [sade.core :refer [ok fail fail! unauthorized! now]]
            [sade.strings :as ss]
            [sade.util :as util :refer [=as-kw]]
            [lupapalvelu.action :refer [update-application]]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.approval :as approval]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.permissions :refer [defcontext]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]))

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
         :document doc})
      (fail! :error.document-not-found))))

(defn state-valid-by-schema? [schema schema-states-key default-states state]
  (-> (get-in schema [:info (keyword schema-states-key)])
      (or default-states)
      (contains? (keyword state))))

(defn created-after-verdict? [document application]
  (if (contains? states/post-verdict-states (keyword (:state application)))
    (let [verdict-state        (sm/verdict-given-state application)
          verdict-history-item (->> (app-state/state-history-entries (:history application))
                                    (filter #(= (:state %) (name verdict-state)))
                                    (sort-by :ts)
                                    last)]
      (when-not verdict-history-item
        (error "Application in post-verdict, but doesnt have verdictGiven state in history"))
      (> (:created document) (:ts verdict-history-item)))
    false))

(defn approved-after-verdict? [document application]
  (if-let [approval (approval/get-approval document)]
    (let [verdict-state (sm/verdict-given-state application)
          verdict-history-item (->> (app-state/state-history-entries (:history application))
                                    (filter #(= (:state %) (name verdict-state)))
                                    (sort-by :ts)
                                    last)]
      (when-not verdict-history-item
        (error "Application in post-verdict, but doesnt have verdictGiven state in history"))
      (and (approval/approved? document) (> (:timestamp approval) (:ts verdict-history-item))))
    false))

(defn user-can-be-set? [user-id application]
  (and (auth/has-auth? application user-id) (domain/no-pending-invites? application user-id)))

(defn create-doc-validator [{{documents :documents permit-type :permitType} :application}]
  ;; Hide the "Lisaa osapuoli" button when application contains "party" type documents and more can not be added.
  (when (and
          (not (permit/multiple-parties-allowed? permit-type))
          (some (comp (partial =as-kw :party) :type :schema-info) documents))
    (fail :error.create-doc-not-allowed)))

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

(defn remove-doc-validator [{:keys [document user application]}]
  (cond
    (deny-remove-of-non-removable-doc document application user) (fail :error.not-allowed-to-remove-document)
    (deny-remove-of-last-document document application user) (fail :error.removal-of-last-document-denied)
    (deny-remove-of-primary-operation document application) (fail :error.removal-of-primary-document-denied)
    (deny-remove-of-non-post-verdict-document document application) (fail :error.document.post-verdict-deletion)
    (deny-remove-of-approved-post-verdict-document document application) (fail :error.document.post-verdict-deletion)))

(defn validate-post-verdict-not-approved
  "In post verdict states, validates that given document is not approved.
   Approval 'locks' documents in post-verdict state."
  [{:keys [application document]}]
  (when (and document
             application
             (contains? states/post-verdict-states (keyword (:state application)))
             (approval/approved? document))
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
  (when-not (get-in document [:schema-info :disableable])
    (fail :error.document.not-disableable)))

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
                 (filter #(= (:result %) [:err "application-does-not-have-given-auth"]))
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
        op-description  (:description (util/find-by-id (:id doc-op) operations))]
    (util/assoc-when-pred {:id id :type-key (ss/join "." [name "_group_label"])} ss/not-blank?
                          :description (or op-description (ss/join " " accordion-datas)))))

(defn- describe-parties-assignment-targets [application]
  (->> (domain/get-documents-by-type application :party)
       (remove :disabled)
       (sort-by tools/document-ordering-fn)
       (map (partial document-assignment-info nil))))

(defn- describe-non-party-document-assignment-targets [{:keys [documents primaryOperation secondaryOperations] :as application}]
  (let [party-doc-ids (set (map :id (domain/get-documents-by-type application :party)))
        operations (cons primaryOperation secondaryOperations)]
    (->> (remove (comp party-doc-ids :id) documents)
         (remove :disabled)
         (sort-by tools/document-ordering-fn)
         (map (partial document-assignment-info operations)))))

(assignment/register-assignment-target! :parties describe-parties-assignment-targets)

(assignment/register-assignment-target! :documents describe-non-party-document-assignment-targets)
