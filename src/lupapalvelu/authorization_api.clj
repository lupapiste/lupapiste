(ns lupapalvelu.authorization-api
  "API for manipulating application.auth"
  (:require [taoensso.timbre :refer [debug error errorf]]
            [clojure.string :refer [blank? trim split]]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.core :refer [ok fail fail! unauthorized]]
            [sade.util :as util :refer [=as-kw]]
            [lupapalvelu.action :refer [defquery defcommand defraw update-application notify] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.authorization-messages]            ; notification definitions
            [lupapalvelu.document.document :as doc]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.permissions :refer [defcontext] :as permissions]
            [lupapalvelu.restrictions :as restrictions]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.company :as company]
            [lupapalvelu.organization :as org]))

;;
;; Invites
;;

(defn- invites-with-application [application]
  (->>
    (domain/invites application)
    (map #(assoc % :application (select-keys application [:id :address :primaryOperation :municipality])))))

(defn- select-user-auths
  "Select auths for user by user-id and company-id."
  [{user-id :id :as user} application]
  (let [company-auths  (auth/get-company-auths application user)
        personal-auths (auth/get-auths application user-id)]
    (assoc application :auth (remove nil? (concat company-auths personal-auths)))))

(defquery invites
  {:user-roles #{:applicant :authority :oirAuthority :financialAuthority}}
  [{{user-id :id {company-id :id company-role :role} :company :as user} :user}]
  (let [query (if (nil? company-id)                         ; TODO: use company permissions/restrictions to optimize query
                {:auth.invite.user.id user-id}
                {$or [{:auth.invite.user.id user-id}
                      {:auth {$elemMatch {:invite.user.id company-id :company-role company-role}}}]})]
    (->> (mongo/select :applications
                       (merge query {:state {$ne :canceled}})
                       [:auth :primaryOperation :address :municipality])
         (map (partial select-user-auths user))
         (mapcat invites-with-application)
         (ok :invites))))

(def settable-roles #{:writer :foreman :financialAuthority})
(def changeable-roles #{:writer :foreman})

(defn- valid-role [role]
  (settable-roles (keyword role)))

(defn send-invite! [{{:keys [email text documentName documentId path role notification]} :data
                     timestamp :created
                     inviter :user
                     application :application
                     :as command}]
  (let [email (ss/canonize-email email)
        existing-auth (auth/get-auth application (:id (user/get-user-by-email email)))
        existing-role (keyword (get-in existing-auth [:invite :role] (:role existing-auth)))
        denied-by-company (->> (get-in (user/get-user-by-email email) [:company :id])
                               (company/company-denies-invitations? application))]
    (cond
      (#{:reader :guest} existing-role)
      (fail :invite.already-has-reader-auth :existing-role existing-role)

      existing-auth
      (fail :invite.already-has-auth)

      denied-by-company
      (fail :invite.company-denies-invitation)

      :else
      (let [invited (user/get-or-create-user-by-email email inviter)
            auth    (auth/create-invite-auth inviter invited (:id application) role timestamp text documentName documentId path)
            email-template (case notification
                             "invite-to-prev-permit" :invite-to-prev-permit
                             "invite-financial-authority" :invite-financial-authority
                             :invite)]
        (update-application command
          {:auth {$not {$elemMatch {:invite.user.username (:email invited)}}}}
          {$push {:auth     auth}
           $set  {:modified timestamp}})
        (notifications/notify! email-template (assoc command :recipients [invited]))
        (ok)))))

(defn- role-validator [{{role :role} :data}]
  (when-not (valid-role role)
    (fail! :error.illegal-role :parameters role)))

(defcommand invite-with-role
  {:parameters [:id :email :text :documentName :documentId :path :role]
   :categories #{:documents}
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator
                      role-validator]
   :states     (states/all-application-states-but [:canceled])
   :permissions [{:context  {:application {:state #{:draft}}}
                  :required [:application/edit-draft :application/invite]}
                 {:required [:application/invite]}]
   :pre-checks  [permit/is-not-archiving-project]
   :notified   true}
  [command]
  (send-invite! command))

(defn authorized-to-apply-submit-restriction [{{:keys [invite-type apply-submit-restriction]} :data :as command}]
  (when apply-submit-restriction
    (case (keyword invite-type)
      :company (company/authorized-to-apply-submit-restriction-to-other-auths command)
      (fail :error.not-allowed-to-apply-submit-restriction))))

(defn submitRestrictor-company-authorized [{{:keys [invite-type]} :data
                                            company :company
                                            :as command}]
  (when (and invite-type (not (=as-kw invite-type :company)))
    (when-let [company (and company @company)]
      (when (:submitRestrictor company)
        (company/check-company-authorized command)))))

(defcommand approve-invite
  {:parameters [id]
   :optional-parameters [invite-type apply-submit-restriction]
   :user-roles #{:applicant}
   :user-authz-roles roles/default-authz-reader-roles
   :states     states/all-application-states
   :pre-checks [authorized-to-apply-submit-restriction
                submitRestrictor-company-authorized]}
  [{created :created  {user-id :id {company-id :id company-role :role} :company :as user} :user application :application :as command}]
  (let [auth-id       (cond (not (util/=as-kw invite-type :company)) user-id
                            (util/=as-kw company-role :admin)        company-id)
        auth          (->> (auth/get-auths application auth-id) (util/find-first :invite))
        approved-auth (auth/approve-invite-auth auth user created)]
    (when approved-auth
      (update-application command
        {:auth {$elemMatch {:invite.user.id auth-id}}}
        (util/deep-merge {$set {:modified created
                                :auth.$   approved-auth}}
                         (when apply-submit-restriction
                           (restrictions/mongo-updates-for-restrict-other-auths auth :application/submit))))
      (when-let [document-id (not-empty (get-in auth [:invite :documentId]))]
        (let [application (domain/get-application-as id user :include-canceled-apps? true)]
          ; Document can be undefined (invite's documentId is an empty string) in invite or removed by the time invite is approved.
          ; It's not possible to combine Mongo writes here, because only the last $elemMatch counts.
          (doc-persistence/do-set-user-to-document application document-id (:id user) (get-in auth [:invite :path]) created user false)))
      (ok))))

(defn do-remove-auth [{:keys [application] :as command} username]
  (let [username (ss/canonize-email username)
        last-invite-permission? (->> (auth/get-auths-by-permissions application [:application/invite])
                                     (every? (comp #{username} :username)))
        inviter-roles (permissions/roles-in-scope-with-permissions :application [:application/invite])
        user-pred (if last-invite-permission?
                    (fn [auth-entry] (and (= username (:username auth-entry))
                                          (not (contains? inviter-roles (keyword (:role auth-entry))))))
                    (fn [auth-entry] (= username (:username auth-entry))))]
    (when (some user-pred (:auth application))
      (let [updated-app (update-in application [:auth] (fn [a] (remove user-pred a)))
            doc-updates (doc/generate-remove-invalid-user-from-docs-updates updated-app)]
        (update-application command
          (util/deep-merge
            {$pull {:auth (if last-invite-permission?
                            {$and [{:username username}, {:type {$nin inviter-roles}}]}
                            {:username username})}}
            (restrictions/mongo-updates-for-remove-all-user-restrictions (util/find-first user-pred (:auth application)))
            (when (seq doc-updates) {$unset doc-updates})))))))

(defcommand decline-invitation
  {:parameters [:id]
   :user-roles #{:applicant :authority}
   :user-authz-roles roles/default-authz-reader-roles
   :states     states/all-application-states}
  [command]
  (do-remove-auth command (get-in command [:user :username])))

;;
;; Authorizations
;;

(defn no-company-users-in-auths-when-company-denies-invitations
  "Precheck for company auth removal for companies that have set :invitationDenied
  flag on. To remove company, all company users have to be removed first."
  [{{auth-id :id type :type} :auth-entry {:keys [auth]} :application}]
  (when (and (util/=as-kw :company type)
             (:invitationDenied (company/find-company-by-id auth-id)))

    (let [company-users-ids  (->> (mongo/select :users {:company.id auth-id} [:_id])
                                  (map :id))
          company-user-auths (filter (comp (set company-users-ids) :id) auth)]

      (when (not-empty company-user-auths)
        (fail :error.company-users-have-to-be-removed-before-company
              :users (map #(select-keys % [:firstName :lastName]) company-user-auths))))))

(defcontext auth-entry-context [{{auth :auth auth-restrictions :authRestrictions} :application
                                 {username :username} :data
                                 {user-id :id {company-id :id} :company} :user}]
  ;; Finds requested auth-entry and authRestrictions that are applied by
  ;; corresponding user. auth-entry and restrictions are injected into
  ;; command. Permissions for :auth-owner in :authorization scope are
  ;; added into command if command caller matches requesteed auth-entry.
  (let [auth-entry (util/find-first (comp #{username} :username) auth)
        restrictions (filter (comp #{(:id auth-entry)} :id :user) auth-restrictions)]
    {:context-scope :authorization
     :context-role  (when (and user-id (#{user-id company-id} (:id auth-entry))) :auth-owner)
     :auth-entry    (assoc auth-entry :restrictions restrictions)}))

(defcommand remove-auth
  {:parameters [:id username]
   :input-validators [(partial action/non-blank-parameters [:username])]
   :contexts   [auth-entry-context]
   :permissions [{:context  {:application {:state #{:draft}} :auth-entry {:restrictions not-empty}}
                  :required [:application/edit-draft :application/edit-restricting-auth]}

                 {:context  {:application {:state #{:draft}}}
                  :required [:application/edit-draft :application/edit-auth]}

                 {:context  {:auth-entry {:restrictions not-empty}} ; trying to remove auth that has applied authRestriciton
                  :required [:application/edit-restricting-auth]}

                 {:required [:application/edit-auth]}]
   :states     (states/all-application-states-but [:canceled])
   :pre-checks [no-company-users-in-auths-when-company-denies-invitations]}
  [command]
  (do-remove-auth command username))

(defcommand change-auth
  {:parameters [:id userId role]
   :input-validators [(partial action/non-blank-parameters [:userId])
                      role-validator
                      (fn [{:keys [data user]}]
                        (when (= (:id user) (-> data :userId ss/trim))
                          (fail :error.unauthorized :cause "Own role can not be changes")))
                      ]
   :user-roles #{:applicant :authority}
   :states     (states/all-application-states-but [:canceled])
   :pre-checks [application/validate-authority-in-drafts
                (fn [command]
                  (when-let [user-id (get-in command [:data :userId])]
                    (if-let [auths (seq (auth/get-auths (:application command) user-id))]
                      (when-not (some changeable-roles (map (comp keyword :role) auths))
                        (fail :error.invalid-role :cause (map :role auths)))
                      (fail :error.user-not-found))))]}
  [{:keys [application] :as command}]
  (let [user-id (ss/trim userId)
        auths (auth/get-auths application user-id)
        roles (map :role auths)]

    (when (> (count auths) 1)
      (errorf "More than one authorization for user %s %s, will change first that is changeable"
             (-> auths first :username), roles))

    (update-application command
      {:auth {$elemMatch {:id userId, :role {$in changeable-roles}}}}
      {$set {:auth.$.role role}})))

(defcommand toggle-submit-restriction-for-other-auths
  {:description "Sets submit restriction on the application for all other users in
   the application auth. When the restriction is set, other authorized users are not
   allowed to submit the application. Restriction applies for personal and company
   authorizations but not for organization authorizations (authorities)."
   :parameters  [id apply-submit-restriction]
   :permissions [{:required [:application/edit]}]
   :pre-checks  [company/authorized-to-apply-submit-restriction-to-other-auths
                 company/check-company-authorized]}
  [{{user-id :id {company-id :id} :company} :user application :application :as command}]
  (let [auth (or (auth/get-auth application company-id)
                 (auth/get-auth application user-id))]
    (->> (if apply-submit-restriction
           (restrictions/mongo-updates-for-restrict-other-auths auth :application/submit)
           (restrictions/mongo-updates-for-remove-other-user-restrictions auth :application/submit))
         (action/update-application command))))

(defquery submit-restriction-enabled-for-other-auths
  {:parameters  [id]
   :permissions [{:required [:application/edit]}]
   :pre-checks  [(partial restrictions/check-auth-restriction-is-enabled-by-user :others :application/submit)]}
  [_])

(defn- auth-company-admin? [{:keys [type id]} {:keys [company]}]
  (and (util/=as-kw  type :company)
       (= id (:id company))
       (util/=as-kw :admin (:role company))))

(defn- manage-unsubscription [{application :application user :user :as command} unsubscribe?]
  (let [username (get-in command [:data :username])]
    (if (or (= username (:username user))
            (some (partial = (:organization application)) (user/organization-ids-by-roles user #{:authority}))
            (auth-company-admin? (util/find-by-key :username username (:auth application))
                                 user))
      (update-application command
        {:auth {$elemMatch {:username username}}}
        {$set {:auth.$.unsubscribed unsubscribe?}})
      unauthorized)))

(defn- pate-enabled
  [{:keys [organization application]}]
  (when (and organization
             (not (-> (org/resolve-organization-scope (:municipality application) (:permitType application) @organization)
                      :pate-enabled)))
    (fail :error.pate-disabled)))

(defcommand unsubscribe-notifications
  {:parameters [:id :username]
   :input-validators [(partial action/non-blank-parameters [:id :username])]
   :user-roles #{:applicant :authority}
   :user-authz-roles roles/default-authz-reader-roles
   :states states/all-application-states
   :pre-checks [application/validate-authority-in-drafts]}
  [command]
  (manage-unsubscription command true))

(defcommand subscribe-notifications
  {:parameters [:id :username]
   :input-validators [(partial action/non-blank-parameters [:id :username])]
   :user-roles #{:applicant :authority}
   :user-authz-roles roles/default-authz-reader-roles
   :states states/all-application-states
   :pre-checks [application/validate-authority-in-drafts]}
  [command]
  (manage-unsubscription command false))

(defquery pate-enabled-basic
  {:description "Pre-checker that fails if Pate is not enabled in the application organization."
   :user-roles  #{:applicant :authority}
   :pre-checks  [pate-enabled]}
  [_])
