(ns lupapalvelu.company-api
  (:require [sade.core :refer [ok fail fail! unauthorized unauthorized!]]
            [lupapalvelu.action :refer [defquery defcommand some-pre-check notify] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.company :as com]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.schemas :as ssc]
            [schema.core :as sc]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.authorization :as auth]))

(defquery company
  {:user-roles #{:applicant :authority}
   :input-validators [(some-pre-check com/validate-is-admin
                                      com/validate-belongs-to-company)]
   :parameters [company]}
  [{{:keys [users]} :data}]
  (ok :company     (com/find-company! {:id company})
      :users       (and users (com/find-company-users company))
      :invitations (and users (com/find-user-invitations company))))

(defquery company-users-for-person-selector
  {:description "Fetch and return company users that can be bound to parties documents using the
         set-user-to-document command. Basically this includes all users in companies that have been
         authorised to the application."
   :user-roles  #{:applicant :authority}
   :pre-checks  [(some-pre-check domain/validate-write-access
                                 auth/application-authority-pre-check)]
   :parameters  [id]}
  [{{auth :auth} :application}]
  (let [authorised-companies (map #(select-keys % [:id :name])
                                  (filter #(and (= (:type %) "company")
                                           (not (= (:role %) "reader"))) auth))]
    (ok :users
        (mapcat (fn [{:keys [id name]}]
                  (->> (com/find-company-users id)
                       (map #(assoc-in % [:company :name] name)))) authorised-companies))))

(defquery company-tags
  {:user-roles #{:applicant :authority :admin}
   :pre-checks [(com/validate-has-company-role :any)]
   :parameters []}
  [{{{company-id :id} :company} :user}]
  (ok :company (com/find-company! {:id company-id} [:tags :name])))

(defquery companies
  {:user-roles #{:applicant :authority :admin}}
  [{user :user created :created}]
  (if (usr/admin? user)
    (let [admins (->> (usr/find-users {"company.role" "admin"})
                      (map #(assoc (usr/summary %) :company (:company %)))
                      (group-by (comp :id :company)))]
      (ok :companies (->> (com/find-companies)
                          (map (fn [company]
                                 (assoc company :admins (->> (get admins (:id company) [])
                                                             (map #(dissoc % :company)))))))))
    (ok :companies (->> (com/find-companies {:locked {$not {$lt created}}} [:name :y :address1 :zip :po :contactAddress :contactZip :contactPo])
                        (map com/company-info)))))

(defcommand company-update
  {:parameters [company updates]
   :input-validators [(partial action/non-blank-parameters [:company])
                      (partial action/map-parameters [:updates])
                      (some-pre-check com/validate-is-admin
                                      com/validate-belongs-to-company)]
   :user-roles #{:applicant :admin}
   :pre-checks [com/company-not-locked
                (some-pre-check com/validate-is-admin
                                (com/validate-has-company-role :admin))]}
  [{caller :user}]
  (ok :company (com/update-company! company (dissoc updates :tags) caller)))

(defcommand company-lock
  {:description      "Set/unset company lock timestamp. If timestamp is not positive
  long, the locked property is removed from the company."
   :user-roles       #{:admin}
   :parameters       [company timestamp]
   :input-validators [(partial action/non-blank-parameters [:company])]}
  [{user :user}]
  (let [ts (or (util/->long timestamp) 0)]
    (ok :company (com/update-company! company
                                      {:locked (if (pos? ts) ts 0)}
                                      user))))

(defcommand company-user-update
  {:parameters [user-id role submit]
   :input-validators [(partial action/non-blank-parameters [:user-id])
                      (partial action/boolean-parameters [:submit])
                      (partial action/select-parameters [:role] #{"user" "admin"})]
   :pre-checks [com/company-not-locked
                com/company-user-edit-allowed]
   :user-roles #{:applicant :admin}}
  [_]
  (com/update-user! user-id role submit))

(defcommand company-user-delete
  {:parameters [user-id]
   :input-validators [(partial action/non-blank-parameters [:user-id])]
   :user-roles #{:applicant :admin}
   :pre-checks [com/company-user-edit-allowed]
   :on-success (notify :company-user-delete)}
  [_]
  (let [user (usr/get-user-by-id user-id)]
    (ok :user (select-keys (com/delete-user! user) [:id :firstName :lastName :email :role :language])
        :company (com/find-company {:id (get-in user [:company :id])} [:id :name]))))

(defcommand company-user-delete-all
  {:description "Nuclear option for deleting every company user when
  the company is locked. Also cancels every pending invite."
   :user-roles #{:applicant}
   :pre-checks [(com/validate-has-company-role :admin)
                com/user-company-is-locked]}
  [{user :user created :created}]
  (let [company-id (-> user :company :id)]
    (com/delete-every-user! company-id)
    (mongo/update-by-query
                   :token
                   {:token-type #"(new|invite)-company-user"
                    :data.company.id company-id
                    :used {$type "null"}}
                   {$set {:used created}})
    (ok)))

(defquery user-company-locked
  {:description "Pseudo-query that succeeds if the user's company is
  locked."
   :user-roles #{:applicant}
   :pre-checks [com/user-company-is-locked]}
  [_])

(defn- user-limit-not-exceeded [command]
  (let [company (com/find-company-by-id (get-in command [:user :company :id]))
        company-users (com/company-users-count (:id company))
        invitations (com/find-user-invitations (:id company))
        users (+ (count invitations) company-users)]
    (when-not (:accountType company)
      (fail! :error.account-type-not-defined-for-company))
    (let [user-limit (or (:customAccountLimit company) (com/user-limit-for-account-type (keyword (:accountType company))))]
      (when-not (< users user-limit)
        (fail :error.company-user-limit-exceeded)))))


(defquery company-search-user
  {:parameters [email]
   :user-roles #{:applicant}
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :pre-checks [(some-pre-check com/validate-is-admin
                                (com/validate-has-company-role :admin))]}
  [{caller :user}]
  (com/search-result caller email (fn [user]
                                    (ok (assoc (select-keys user [:firstName :lastName :role]) :result :found)))))

(defcommand company-invite-user
  {:parameters [email admin submit]
   :optional-parameters [firstName lastName]
   :user-roles #{:applicant}
   :input-validators [(partial action/non-blank-parameters [:email])
                      (partial action/boolean-parameters [:admin :submit])
                      action/email-validator]
   :notified   true
   :pre-checks [com/company-not-locked
                (some-pre-check com/validate-is-admin
                                (com/validate-has-company-role :admin))
                user-limit-not-exceeded]}
  [{caller :user}]
  (com/search-result caller email (fn [_]
                                  (com/invite-user! caller
                                                  email
                                                  (-> caller :company :id)
                                                  (if admin :admin :user)
                                                  submit
                                                  firstName
                                                  lastName)
                                  (ok :result :invited))))

(defcommand company-add-user
  {:user-roles #{:applicant}
   :parameters [firstName lastName email admin submit]
   :input-validators [(partial action/non-blank-parameters [:email])
                      (partial action/boolean-parameters [:admin :submit])
                      action/email-validator]
   :notified   true
   :pre-checks [com/company-not-locked
                (some-pre-check com/validate-is-admin
                                (com/validate-has-company-role :admin))
                user-limit-not-exceeded]}
  [{user :user}]
  (com/add-user! {:firstName firstName :lastName lastName :email email}
               (assoc (com/find-company-by-id (-> user :company :id)) :admin user)
               (if admin :admin :user)
               submit)
  (ok))

(defcommand company-invite
  {:parameters [id company-id]
   :input-validators [(partial action/non-blank-parameters [:id :company-id])]
   :states (states/all-application-states-but states/terminal-states)
   :user-roles #{:applicant :authority}
   :pre-checks [com/company-not-locked
                application/validate-authority-in-drafts
                com/company-not-already-invited]
   :notified   true}
  [{caller :user application :application}]
  (com/company-invite caller application company-id)
  (ok))

(defcommand company-cancel-invite
  {:parameters [tokenId]
   :input-validators [(partial action/non-blank-parameters [:tokenId])]
   :user-roles #{:applicant}
   :pre-checks [(some-pre-check com/validate-is-admin
                                (com/validate-has-company-role :admin))]}
  [{:keys [created user application] :as command}]
  (let [token (mongo/by-id :token tokenId)
        token-company-id (get-in token [:data :company :id])
        user-company-id (get-in user [:company :id])]
    (if-not (= token-company-id user-company-id)
      (fail! :forbidden)))
  (mongo/update-by-id :token tokenId {$set {:used created}})
  (ok))

(defcommand save-company-tags
  {:parameters [tags]
   :input-validators [(partial action/parameters-matching-schema
                               [:tags]
                               [{(sc/optional-key :id) (sc/maybe ssc/ObjectIdStr)
                                 :label                sc/Str}])]
   :user-roles #{:applicant}
   :pre-checks [(com/validate-has-company-role :any)]}
  [{{:keys [company] :as user} :user :as command}]
  (com/update-company! (:id company) {:tags (map mongo/ensure-id tags)} user)
  (ok))

(defquery remove-company-tag-ok
  {:parameters [tagId]
   :input-validators [(partial action/non-blank-parameters [:tagId])]
   :user-roles #{:applicant :authority}}
  [{{{company-id :id} :company} :user}]
  (if-let [tag-applications (seq (mongo/select :applications
                                               {:company-notes {$elemMatch {:companyId company-id
                                                                            :tags      tagId}}}
                                               [:_id]))]
    (fail :warning.tags.removing-from-applications :applications tag-applications)
    (ok)))

(defquery company-notes
  {:parameters [id]
   :user-roles #{:applicant :authority}
   :pre-checks [com/validate-company-is-authorized]}
  [{{notes :company-notes} :application {{company-id :id} :company} :user}]
  (ok :notes (util/find-by-key :companyId company-id notes)))

(defcommand update-application-company-notes
  {:parameters [id]
   :optional-parameters [tags note]
   :states states/all-application-states
   :pre-checks [(com/validate-has-company-role :any)
                com/validate-tag-ids]
   :user-roles #{:authority :applicant}}
  [{{notes :company-notes} :application {{company-id :id} :company} :user}]
  (if (util/find-by-key :companyId company-id notes)
    (mongo/update :applications
                  {:_id id :company-notes {$elemMatch {:companyId company-id}}}
                  {$set (util/assoc-when {} :company-notes.$.tags tags :company-notes.$.note note)})
    (mongo/update :applications
                  {:_id id}
                  {$push {:company-notes (util/assoc-when {:companyId company-id} :tags tags :note note)}}))
  (ok))

(defquery enable-company-search
  {:user-roles #{:applicant}
   :pre-checks [(com/validate-has-company-role :any)]}
  [_])

(defquery authorized-to-apply-submit-restriction-to-other-auths
  {:permissions [{:required []}]
   :pre-checks  [com/authorized-to-apply-submit-restriction-to-other-auths
                 com/check-invitation-accepted]}
  [_])
