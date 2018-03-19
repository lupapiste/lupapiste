(ns lupapalvelu.copy-application
  (:require [clojure.set :as set]
            [taoensso.timbre :refer [error]]
            [lupapalvelu.application :as app]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.company :as company]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.waste-schemas :as waste-schemas]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notif]
            [lupapalvelu.operations :as op]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.property :as prop]
            [sade.util :refer [merge-in find-first pathwalk]]))

;;;
;;; Obtaining the parties to invite for the copied app
;;;

(defn- party-info-from-document [document]
  {:document-id (:id document)
   :document-name (tools/doc-name document)
   :id (tools/party-doc-selected-id document)
   :role (tools/party-doc->user-role document)})

(def ^:private non-copyable-roles #{:tyonjohtaja :statementGiver})

(defn- party-infos-from-documents [documents]
  (->> documents
       (filter #(= :party (tools/doc-type %)))
       (map party-info-from-document)
       (remove #(nil? (:id %)))
       (map (comp (partial apply hash-map)
                  (juxt :id vector)))
       (apply merge-with concat)))

(defn auth-id [auth-entry]
  (or (not-empty (:id auth-entry))
      (-> auth-entry :invite :user :id)))

(defn- auth-is-user? [auth user]
  (= (auth-id auth) (:id user)))

(defn- auth-is-company-of-user? [auth user]
  (= (auth-id auth) (-> user :company :id)))

(defn- auth-is-invite? [auth]
  (boolean (:invite auth)))

(defn- auth-role [auth-entry]
  (or (keyword (-> auth-entry :invite :role))
      (keyword (-> auth-entry :role))))

(defn non-copyable-auth? [auth user]
  (or (contains? non-copyable-roles (auth-role auth))
      (auth-is-user? auth user)
      (auth-is-company-of-user? auth user)))

(defn not-in-auth [auth]
  (let [auth-id-set (set (map auth-id auth))]
    (fn [id]
      (not (get auth-id-set id)))))

(defn- invite-candidate-info [party-infos auth-entry]
  (let [party-info (first (get party-infos (:id auth-entry)))]
    {:id (auth-id auth-entry)
     :firstName (:firstName auth-entry)
     :lastName (:lastName auth-entry)
     :email (-> auth-entry :invite :email) ; for cases where invitee is not a registered user and name is not known
     :role (or (:role party-info)      ; prefer role dictated by party document
               (auth-role auth-entry)) ; but fall back to role in auth
     :roleSource (if (:role party-info)
                   :document :auth)}))

(defn- get-invite-candidates [auth documents user]
  (->> auth
       (map (partial invite-candidate-info (party-infos-from-documents documents)))
       (remove #(non-copyable-auth? % user))))

(defn copy-application-invite-candidates [user source-application-id]
  (if-let [source-application (domain/get-application-as source-application-id user :include-canceled-apps? true)]
    (get-invite-candidates (:auth source-application)
                           (:documents source-application)
                           user)
    (fail! :error.application-not-found :id source-application-id)))

;;; Copying source application keys

(defn- copied-keys
  "Copy keys from source application. The options must include either a
  whitelist of keys to copy, or a blacklist of keys to NOT copy"
  [source-application copy-options]
  {:pre [(or (contains? copy-options :whitelist)
             (contains? copy-options :blacklist))]}
  (if (contains? copy-options :whitelist)
    (select-keys source-application (:whitelist copy-options))
    (apply dissoc source-application (:blacklist copy-options))))

;;;
;;; Updating documents
;;;

(defn- primary-op-name [application]
  (-> application :primaryOperation :name))

(defn- operation-id-map
  "Creates a map from operation ids of source application to newly
  generated mongo ids. This is used to have e.g. copied documents refer
  to the operations of the copied application instead of the source one."
  [source-application]
  (into {}
        (map #(vector (:id %) (mongo/create-id))
             (conj (:secondaryOperations source-application)
                   (:primaryOperation source-application)))))

(defn new-building-application? [application]
  (-> application primary-op-name keyword
      op/operations :schema
      #{"uusiRakennus" "uusi-rakennus-ei-huoneistoa"}
      boolean))

(defn- empty-document-copy
  "Returns an empty copy of the given document"
  [document {:keys [created primaryOperation schema-version] :as application} & [manual-schema-datas]]
  (let [schema (schemas/get-schema schema-version (-> document :schema-info :name))]
    (app/make-document application (:name primaryOperation) created manual-schema-datas schema)))

(defn- user-id-from-personal-information [v]
  (some->> v :userId :value not-empty))

(defn- company-id-from-personal-information [v]
  (some->> v :companyId :value not-empty))

(defn id-from-personal-information [v]
  (or (user-id-from-personal-information v)
      (company-id-from-personal-information v)))

(defn- personal-information-element? [v]
  (and (map? v)
       (or (contains? v :userId)
           (contains? v :companyId))))

(defn- building-selector-element? [v]
  (and (map? v)
       (contains? v :buildingId)))

(defn- document-element-sould-be-cleared? [element not-in-auth?]
  (or (and (personal-information-element? element)
           (or (empty? (id-from-personal-information element))
               (not-in-auth? (id-from-personal-information element))))
      (building-selector-element? element)))

(defn- clear-user-and-company-ids [element auth]
  (if (and (map? element)
           (auth-is-invite? (find-first #(= (:id %)
                                            (id-from-personal-information element))
                                        auth)))
    (cond (contains? element :userId)    (assoc-in element [:userId :value] "")
          (contains? element :companyId) (assoc-in element [:companyId :value] "")
          :else element)
    element))

(defn- clear-personal-information
  "Clears personal information from documents if
   - it is possible to enter the information using user or company id and
   - user/company id is missing or the provided id is not authorized for the new
     application"
  [document application & [manual-schema-datas]]
  (let [empty-copy (empty-document-copy document application manual-schema-datas)
        not-in-auth? (not-in-auth (:auth application))]
    (pathwalk (fn [path v]
                (if (document-element-sould-be-cleared? v not-in-auth?)
                  (get-in empty-copy path)

                  ; clear user and company id's in any case because
                  ; document is invalid if an unauthorized id is found, and
                  ; invites are not considered authorizations
                  (clear-user-and-company-ids v (:auth application))))
              document)))

(defn- construction-waste-plan? [doc]
  ;; This is as intended, waste-schemas/construction-waste-plan-for-organization
  ;; chooses between these two instead of the basic and extended reports
  (#{waste-schemas/basic-construction-waste-plan-name
     waste-schemas/extended-construction-waste-report-name}
   (-> doc :schema-info :name)))

(defn construction-waste-plan
  [application organization manual-schema-datas]
  (let [plan-name (waste-schemas/construction-waste-plan-for-organization organization)]
    (app/make-document application
                       (primary-op-name application)
                       (:created application)
                       manual-schema-datas
                       (schemas/get-schema (:schema-version application)
                                           plan-name))))

(defn- document-disabled? [document]
  (boolean (-> document :disabled)))

(defn- handle-copy-action
  "Handle the copy action specified by the document schema"
  [document application organization manual-schema-datas]
  (let [schema-info (-> document :schema-info
                        (schemas/get-schema) :info)
        copy-action (:copy-action schema-info)]
    (cond (= copy-action :clear)
          (empty-document-copy document application manual-schema-datas)

          (or (= copy-action :copy) (nil? copy-action))
          document

          :else document)))

(defn- handle-special-cases
  "Handle special cases not covered by :copy-action in the document
  schema. If you notice that a subset of the cases could be removed by
  adding a meaningful :copy-action value, please do."
  [document application organization manual-schema-datas]
  (cond (construction-waste-plan? document)
        (construction-waste-plan application organization manual-schema-datas)

        (document-disabled? document)
        (empty-document-copy document application manual-schema-datas)

        :else document))

(defn- update-doc-operation-reference
  "Update the document so that it refers to an operation in the copied
  application, not the source one"
  [document op-id-mapping]
  (if (-> document :schema-info :op)
    (update-in document [:schema-info :op :id] op-id-mapping)
    document))

(defn preprocess-document
  "Preprocess document taken from another application so that it is valid for the target application"
  [document application organization manual-schema-datas]
  (-> document
      (handle-copy-action   application organization manual-schema-datas)
      (handle-special-cases application organization manual-schema-datas)
      (assoc :id      (mongo/create-id)
             :created (:created application))
      (dissoc :meta)
      (clear-personal-information application manual-schema-datas)))

(defn- update-attachment-operation-references
  "Update the attachment so that it refers to an operation in the copied
  application, not the source one"
  [attachment op-id-mapping]
  (if (:op attachment)
    (update attachment :op (partial map #(update % :id op-id-mapping)))
    attachment))

(defn- updated-operation-and-document-ids
  [application source-application organization & [manual-schema-datas]]
  (let [op-id-mapping (operation-id-map source-application)]
    {:primaryOperation (update (:primaryOperation application) :id
                               op-id-mapping)
     :secondaryOperations (mapv #(assoc % :id (op-id-mapping (:id %)))
                                (:secondaryOperations application))
     :documents (mapv (comp #(update-doc-operation-reference % op-id-mapping)
                            #(preprocess-document %
                                                  application
                                                  organization
                                                  manual-schema-datas))
                      (:documents application))
     :attachments (mapv #(update-attachment-operation-references % op-id-mapping)
                        (:attachments application))}))

;;; Handling noncopied and nonoverridden keys similarly to creating new application

(defn- tos-function [organization-id operation-name]
  (app/tos-function (org/get-organization organization-id) operation-name))

(defn- copy-application-documents-map
  "If the application contains no documents, create new ones similarly
  to a new application"
  [{:keys [documents] :as copied-application} user organization manual-schema-datas]
  (if (empty? documents)
    (app/application-documents-map copied-application user organization manual-schema-datas)))

(defn create-company-auth [{:keys [role invite] :as old-company-auth} inviter]
  (when-let [company-id (auth-id old-company-auth)]
    (when-let [company (company/find-company-by-id company-id)]
      (assoc (company/company->auth company (get invite :role role))
             :inviter (usr/summary inviter)))))

(defn create-user-auth [old-user-auth role inviter application-id timestamp & [text document-name document-id path]]
  (when-let [user (usr/get-user-by-id (:id old-user-auth))]
    (auth/create-invite-auth inviter user application-id
                             (get-in old-user-auth [:invite :role] role)
                             timestamp
                             text document-name document-id path)))

(defn auth->invite
  "Create an invite from existing auth entry."
  [old-auth inviter application-id timestamp]
  (if (= (:type old-auth) "company")
    (create-company-auth old-auth inviter)
    (create-user-auth old-auth
                      (get-in old-auth [:invite :role] (:role old-auth))
                      inviter application-id timestamp)))

(defn- new-auth-map [{auth           :auth
                     id              :id
                     {op-name :name} :primaryOperation
                     created         :created}
                     inviter]
  {:auth (concat (app/application-auth inviter op-name)
                 (->> auth
                      (remove #(= (:id inviter) (:id %)))
                      (mapv #(auth->invite % inviter id created))))})


(def default-copy-options
  {:whitelist [:address :auth :documents :location :location-wgs84 :municipality
               :organization :permitSubtype :permitType :primaryOperation :propertyId
               :schema-version :secondaryOperations]})

(defn- new-application-overrides
  [{:keys [address auth infoRequest location municipality primaryOperation schema-version state title tosFunction] :as application}
   user organization created manual-schema-datas]
  {:pre [(not-empty primaryOperation) (not-empty location) municipality]}
  (let [org-id (:id organization)
        op-name (:name primaryOperation)]
    (-> (merge application
               {:created          created
                :id               (app/make-application-id municipality)
                :schema-version   (or schema-version    (schemas/get-latest-schema-version))
                :state            (or (not-empty state) (app/application-state user org-id infoRequest false))
                :title            (or (not-empty title) address)
                :tosFunction      (or tosFunction       (tos-function org-id op-name))}
               (app/location-map  location))
        (merge-in new-auth-map user)
        (merge-in app/application-timestamp-map)
        (merge-in app/application-history-map user)
        (merge-in app/application-attachments-map organization)
        (merge-in copy-application-documents-map user organization manual-schema-datas)
        (merge-in app/application-metadata-map))))

(defn new-application-copy [source-application user organization created copy-options & [manual-schema-datas]]
  (let [options (merge default-copy-options copy-options)]
    (-> domain/application-skeleton
        (merge (copied-keys source-application options))
        (merge-in new-application-overrides user organization created manual-schema-datas)
        (merge-in updated-operation-and-document-ids source-application
                  organization manual-schema-datas))))

(defn- check-valid-source-application
  "Fail if the application cannot be copied because of its subtype or
  primary operation type"
  [source-application]
  (cond (= (:permitSubtype source-application) "muutoslupa")
        (fail :error.application-invalid-permit-subtype
              :permitSubtype (:permitSubtype source-application))

        (not (op/get-operation-metadata (primary-op-name source-application)
                                        :copying-allowed))
        (fail :error.operations.copying-not-allowed
              :operation (primary-op-name source-application))

        :else nil))

(defn check-valid-operation-for-organization
  "Fail if the target organization does not support the primary
  operation of the source application"
  [source-application organization]
  (let [operation-name (primary-op-name source-application)]
    (when-not (find-first #(= % operation-name) (:selected-operations organization))
      (fail :error.operations.hidden :organization (:id organization)
            :operation operation-name))))

(defn- organization-for-property-id [propertyId operation-name]
  (let [municipality (prop/municipality-id-by-property-id propertyId)
        permit-type  (op/permit-type-of-operation operation-name)
        org (org/resolve-organization municipality
                                      permit-type)]
    (when-not org
      (fail! :error.organization-not-found :municipality municipality
             :permit-type permit-type :operation operation-name))
    org))

(defn check-application-copyable
  "Fails if the source application cannot be copied"
  [{{:keys [source-application-id]} :data :keys [user]}]
  (if-let [source-application (domain/get-application-as source-application-id
                                                         user :include-canceled-apps? true)]
    (check-valid-source-application source-application)
    (fail :error.application-not-found :id source-application-id)))

(defn check-application-copyable-to-organization
  "Fails if the application cannot be copied to the specific organization"
  [{{:keys [source-application-id x y address propertyId]} :data :keys [user]}]
  (if-let [source-application (domain/get-application-as source-application-id user :include-canceled-apps? true)]
    (let [operation-name (primary-op-name source-application)]
      (or (check-valid-source-application source-application)
          (check-valid-operation-for-organization source-application
                                                  (organization-for-property-id propertyId
                                                                                operation-name))))
    (fail! :error.application-not-found :id source-application-id)))

(defn- check-valid-auth-invites
  "Fails if some of the auth invites are not present on the source application"
  [source-application auth-invites user]
  (let [not-in-source-auths? (not-in-auth (remove #(non-copyable-auth? % user)
                                                  (:auth source-application)))]
    (when (some not-in-source-auths? auth-invites)
      (fail :error.nonexistent-auths :missing (filter not-in-source-auths? auth-invites)))))

(defn- select-auth-invites [source-application auth-invites]
  (filter #((set auth-invites) (auth-id %))
          (:auth source-application)))

(defn copy-application
  [{{:keys [source-application-id x y address propertyId auth-invites]} :data :keys [user created]} & [manual-schema-datas]]
  (if-let [source-application (domain/get-application-as source-application-id user :include-canceled-apps? true)]
    (let [municipality (prop/municipality-id-by-property-id propertyId)
          operation    (-> source-application :primaryOperation :name)
          organization (organization-for-property-id propertyId operation)]

      (if-let [check-failed (or (check-valid-source-application source-application)
                                (check-valid-operation-for-organization source-application organization)
                                (check-valid-auth-invites source-application auth-invites user))]
        check-failed
        {:source-application source-application
         :copy-application (new-application-copy (assoc source-application
                                                        :auth         (select-auth-invites source-application
                                                                                           auth-invites)
                                                        :state        :draft
                                                        :address      address
                                                        :propertyId   propertyId
                                                        :location     (app/->location x y)
                                                        :municipality municipality
                                                        :organization (:id organization))
                                                 user organization created
                                                 default-copy-options
                                                 manual-schema-datas)}))
    (fail! :error.application-not-found :id source-application-id)))

(defn store-source-application
  "Store the state of the source application used for copying application specified by copy-application-id"
  [source-application copy-application-id timestamp]
  (mongo/insert :source-applications {:id copy-application-id
                                      :timestamp timestamp
                                      :source-application source-application}))

(defn get-source-application [copy-application-id]
  (first (mongo/select :source-applications {:_id copy-application-id})))


;;; Sending invite notifications

(defn- invited-as-foreman? [application user]
  (->> application
       :auth
       (filter #(= (-> % :invite :role) "foreman"))
       (map :id)
       (some #(= (:id user) %))))

(defn- foreman-in-foreman-app? [application user]
  (and (invited-as-foreman? application user)
       (= :tyonjohtajan-nimeaminen-v2 (-> application :primaryOperation :name keyword))))

(defn- notify-of-invite! [app command invite-type recipients]
  (let [recipients (->> (filter :invite recipients)
                        (map (comp usr/get-user-by-email :email :invite)))]
    (notif/notify! invite-type
                   (assoc command
                          :application app
                          :recipients  recipients))))


(defn- user-invite-notifications! [application command auths]
  (let [[foremen others] ((juxt filter remove) (partial foreman-in-foreman-app?
                                                        application)
                                               auths)]
    (notify-of-invite! application command :invite-foreman foremen)
    (notify-of-invite! application command :invite others)))

(defn- invite-company! [app {user :user} auth]
  (let [company-id (get-in auth [:invite :user :id])
        token-id   (company/company-invitation-token user company-id (:id app))]
    (notif/notify! :accept-company-invitation {:admins      (company/find-company-admins company-id)
                                               :inviter     user
                                               :company     (company/find-company! {:id company-id})
                                               :token-id    token-id
                                               :application app})))


(defn send-invite-notifications! [{:keys [auth] :as application} {:keys [user] :as command}]
  (let [[users companies] ((juxt remove filter) (comp #{"company"} :type)
                                                (remove (comp #{:statementGiver} keyword :role)
                                                        auth))]
    ;; Non-company invites
    (user-invite-notifications! application command users)
    ;; Company invites
    (run! (partial invite-company! application command) companies)))
