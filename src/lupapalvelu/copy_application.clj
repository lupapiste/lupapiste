(ns lupapalvelu.copy-application
  (:require [clojure.set :as set]
            [taoensso.timbre :refer [error]]
            [lupapalvelu.application :as app]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.company :as company]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notif]
            [lupapalvelu.operations :as op]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.property :as prop]
            [sade.util :refer [merge-in find-first]]))

;;; Obtaining the parties to invite for the copied app

(defn- party-info-from-document [document]
  {:document-id (:id document)
   :document-name (tools/doc-name document)
   :id (tools/party-doc-user-id document)
   :role (tools/party-doc->user-role document)})

(def ^:private non-copyable-roles #{:tyonjohtaja})

(defn- party-infos-from-documents [documents]
  (->> documents
       (filter #(= :party (tools/doc-type %)))
       (map party-info-from-document)
       (remove #(nil? (:id %)))
       (map (comp (partial apply hash-map)
                  (juxt :id vector)))
       (apply merge-with concat)))

(defn- auth-id [auth-entry]
  (or (not-empty (:id auth-entry))
      (-> auth-entry :invite :user :id)))

(defn- invite-candidate-info [party-infos auth-entry]
  (let [party-info (first (get party-infos (:id auth-entry)))]
    {:id (auth-id auth-entry)
     :firstName (:firstName auth-entry)
     :lastName (:lastName auth-entry)
     :email (-> auth-entry :invite :email) ; for cases where invitee is not a registered user and name is not known
     :role (or (:role party-info)                      ; prefer role dictated by party document
               (keyword (-> auth-entry :invite :role)) ; but fall back to role in auth
               (keyword (-> auth-entry :role)))
     :roleSource (if (:role party-info)
                   :document :auth)}))

(defn- get-invite-candidates [auth documents]
  (->> auth
       (map (partial invite-candidate-info (party-infos-from-documents documents)))
       (remove (comp non-copyable-roles :role))))

(defn copy-application-invite-candidates [user source-application-id]
  (if-let [source-application (domain/get-application-as source-application-id user :include-canceled-apps? true)]
    (get-invite-candidates (:auth source-application)
                           (:documents source-application))
    (fail! :error.no-source-application :id source-application-id)))

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


;;; Updating ids

(defn- new-app-id [application]
  {:id (app/make-application-id (:municipality application))})

(defn- operation-id-map [source-application]
  (into {}
        (map #(vector (:id %) (mongo/create-id))
             (conj (:secondaryOperations source-application)
                   (:primaryOperation source-application)))))

(defn- updated-operation-and-document-ids [application source-application]
  (let [op-id-mapping (operation-id-map source-application)]
    {:primaryOperation (update (:primaryOperation application) :id
                               op-id-mapping)
     :secondaryOperations (mapv #(assoc % :id (op-id-mapping (:id %)))
                                (:secondaryOperations application))
     :documents (mapv (fn [doc]
                        (let [doc (assoc doc
                                         :id (mongo/create-id)
                                         :created (:created application))]
                          (if (-> doc :schema-info :op)
                            (update-in doc [:schema-info :op :id] op-id-mapping)
                            doc)))
                      (:documents application))}))

;;; Handling noncopied and nonoverridden keys similarly to creating new application

(defn- tos-function [organization-id operation-name]
  (app/tos-function (org/get-organization organization-id) operation-name))

(defn- copy-application-documents-map
  "If the application contains no documents, create new ones similarly
  to a new application"
  [{:keys [documents] :as copied-application} user organization manual-schema-datas]
  (if (empty? documents)
    (app/application-documents-map copied-application user organization manual-schema-datas)))

(defn- create-company-auth [old-company-auth]
  (when-let [company-id (auth-id old-company-auth)]
    (when-let [company (company/find-company-by-id company-id)]
      (assoc
       (company/company->auth company)
       :id "" ; prevents access to application before accepting invite
       :role (:role old-company-auth)
       :invite {:user {:id company-id}}))))

(defn- create-user-auth [old-user-auth inviter application-id timestamp]
  (when-let [user (usr/get-user-by-id (:id old-user-auth))]
    (auth/create-invite-auth inviter user application-id
                             (:role old-user-auth)
                             timestamp)))

(defn- new-auth [old-auth inviter application-id timestamp]
  (if (= (:type old-auth) "company")
    (create-company-auth old-auth)
    (create-user-auth old-auth inviter application-id timestamp )))

(defn- new-auth-map [{auth           :auth
                     id              :id
                     {op-name :name} :primaryOperation
                     created          :created}
                    inviter]
  {:auth (concat (app/application-auth inviter op-name)
                 (->> auth
                      (remove #(= (:id inviter) (:id %)))
                      (map #(if (= (keyword (:role %)) :owner)
                              (assoc % :role :writer)
                              %))
                      (mapv #(new-auth % inviter id created))))})


(def default-copy-options
  {:blacklist [:comments :history :statements :attachments] ; copy everything except these
   })

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
                :state            (or state             (app/application-state user org-id infoRequest))
                :title            (or (not-empty title) address)
                :tosFunction      (or tosFunction       (tos-function org-id op-name))}
               (app/location-map  location))
        (merge-in new-auth-map user)
        (merge-in app/application-timestamp-map)
        (merge-in app/application-history-map user)
        (merge-in app/application-attachments-map organization)
        (merge-in copy-application-documents-map user organization manual-schema-datas))))

(defn new-application-copy [source-application user organization created copy-options & [manual-schema-datas]]
  (let [options (merge default-copy-options copy-options)]
    (-> domain/application-skeleton
        (merge (copied-keys source-application options))
        (merge-in new-application-overrides user organization created manual-schema-datas)
        (merge-in updated-operation-and-document-ids source-application))))

(defn- not-in-auth [auth]
  (let [auth-id-set (set (map auth-id auth))]
    (fn [id]
      (not (get auth-id-set id)))))

(defn check-copy-application-possible! [organization municipality permit-type operation]
  (when-not organization
    (fail! :error.missing-organization :municipality municipality :permit-type permit-type :operation operation))
  (when-not (find-first #(= % operation) (:selected-operations organization))
    (fail! :error.operation-not-supported-by-organization :organization (:id organization) :operation operation)))

(defn application-copyable-to-location
  [{{:keys [source-application-id x y address propertyId]} :data :keys [user]}]
  (if-let [source-application (domain/get-application-as source-application-id user :include-canceled-apps? true)]
    (let [municipality (prop/municipality-id-by-property-id propertyId)
          operation    (-> source-application :primaryOperation :name)
          permit-type  (op/permit-type-of-operation operation)
          organization (org/resolve-organization municipality permit-type)]
      (check-copy-application-possible! organization municipality permit-type operation)
      true)
    (fail! :error.no-source-application :id source-application-id)))

(defn copy-application
  [{{:keys [source-application-id x y address propertyId auth-invites]} :data :keys [user created]} & [manual-schema-datas]]
  (if-let [source-application (domain/get-application-as source-application-id user :include-canceled-apps? true)]
    (let [municipality (prop/municipality-id-by-property-id propertyId)
          operation    (-> source-application :primaryOperation :name)
          permit-type  (op/permit-type-of-operation operation)
          organization (org/resolve-organization municipality permit-type)
          not-in-source-auths? (not-in-auth (:auth source-application))]
      (check-copy-application-possible! organization municipality permit-type operation)
      (when (some not-in-source-auths? auth-invites)
        (fail! :error.nonexistent-auths :missing (filter not-in-source-auths? auth-invites)))

      {:source-application source-application
       :copy-application (new-application-copy (assoc source-application
                                                      :auth         (filter #((set auth-invites) (auth-id %))
                                                                            (:auth source-application))
                                                      :state        :draft
                                                      :propertyId   propertyId
                                                      :location     (app/->location x y)
                                                      :municipality municipality
                                                      :organization (:id organization))
                                               user organization created
                                               default-copy-options
                                               manual-schema-datas)})
    (fail! :error.no-source-application :id source-application-id)))

(defn store-source-application
  "Store the state of the source application used for copying application specified by copy-application-id"
  [source-application copy-application-id timestamp]
  (mongo/insert :source-applications {:id copy-application-id
                                      :timestamp timestamp
                                      :source-application source-application}))

(defn get-source-application [copy-application-id]
  (first (mongo/select :source-applications {:id copy-application-id})))

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
  (->> (map (fn [{{:keys [email user]} :invite}] (or (usr/get-user-by-email email) (assoc user :email email))) recipients)
         (assoc command :application app :recipients)
         (notif/notify! invite-type)))


(defn- user-invite-notifications! [foreman-app command auths]
  (let [[foremen others] ((juxt filter remove) (partial foreman-in-foreman-app?
                                                        foreman-app)
                                               auths)]
    (notify-of-invite! foreman-app command :invite-foreman foremen)
    (notify-of-invite! foreman-app command :invite others)))

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
                                                (remove (comp #{:owner} keyword :role) auth))]
    ;; Non-company invites
    (user-invite-notifications! application command users)
    ;; Company invites
    (run! (partial invite-company! application command) companies)))
