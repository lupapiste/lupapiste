(ns lupapalvelu.company-api
  (:require [sade.core :refer [ok fail fail! unauthorized unauthorized!]]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.company :as c]
            [lupapalvelu.user :as u]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [sade.strings :as ss]))

;;
;; Company API:
;;

; Validator: check is user is either :admin or user belongs to requested company

(defn validate-user-is-admin-or-company-member [{{:keys [role company]} :user {requested-company :company} :data}]
  (when-not (or (= role "admin")
                (= (:id company) requested-company))
    unauthorized))

(defn validate-user-is-admin-or-company-admin [{user :user} _]
  (when-not (or (= (:role user) "admin")
                (= (get-in user [:company :role]) "admin"))
    unauthorized))

;;
;; Basic API:
;;

(defquery company
  {:user-roles #{:applicant :authority}
   :input-validators [validate-user-is-admin-or-company-member]
   :parameters [company]}
  [{{:keys [users]} :data}]
  (ok :company     (c/find-company! {:id company})
      :users       (and users (c/find-company-users company))
      :invitations (and users (c/find-user-invitations company))))

(defquery companies
  {:user-roles #{:applicant :authority :admin}}
  [_]
  (ok :companies (c/find-companies)))

(defcommand company-update
  {:user-roles #{:applicant}
   :input-validators [validate-user-is-admin-or-company-member]
   :parameters [company updates]}
  (ok :company (c/update-company! company updates)))

(defcommand company-user-update
  {:user-roles #{:applicant :admin}
   :parameters [user-id op value]}
  [{caller :user}]
  (let [target-user (u/get-user-by-id! user-id)]
    (if-not (or (= (:role caller) "admin")
                (and (= (get-in caller [:company :role])
                        "admin")
                     (= (get-in caller [:company :id])
                        (get-in target-user [:company :id]))))
      (unauthorized!))
    (c/update-user! user-id (keyword op) value)
    (ok)))

(defn- user-limit-not-exceeded [command _]
  (let [company (c/find-company-by-id (get-in command [:user :company :id]))
        company-users (c/find-company-users (:id company))
        invitations (c/find-user-invitations (:id company))
        users (+ (count invitations) (count company-users))]
    (when-not (:accountType company)
      (fail! :error.account-type-not-defined-for-company))
    (let [user-limit (c/user-limit-for-account-type (keyword (:accountType company)))]
      (when-not (< users user-limit)
        (fail :error.company-user-limit-exceeded)))))


(defquery company-invite-user
  {:user-roles #{:applicant}
   :pre-checks [validate-user-is-admin-or-company-admin user-limit-not-exceeded]
   :parameters [email]}
  [{caller :user}]
  (let [user (u/find-user {:email email})
        tokens (c/find-user-invitations (-> caller :company :id))]
    (cond
      (some #(= email (:email %)) tokens)
      (ok :result :already-invited)

      (nil? user)
      (ok :result :not-found)

      (get-in user [:company :id])
      (ok :result :already-in-company)

      :else
      (do
        (c/invite-user! email (-> caller :company :id))
        (ok :result :invited)))))

(defcommand company-add-user
  {:user-roles #{:applicant}
   :parameters [firstName lastName email]
   :pre-checks [validate-user-is-admin-or-company-admin user-limit-not-exceeded]}
  [{user :user, {:keys [admin]} :data}]
  (c/add-user! {:firstName firstName :lastName lastName :email email}
               (c/find-company-by-id (-> user :company :id))
               (if admin :admin :user))
  (ok))

(defcommand company-invite
  {:parameters [id company-id]
   :states (action/all-application-states-but [:closed :canceled])
   :user-roles #{:applicant :authority}
   :pre-checks [application/validate-authority-in-drafts]}
  [{caller :user application :application}]
  (c/company-invite caller application company-id)
  (ok))

(defcommand create-company
  {:parameters [:name :y :address1 :po :zip email]
   :input-validators [action/email-validator
                      (partial action/non-blank-parameters [:name :y])]
   :user-roles #{:admin}}
  [{data :data}]
  (if-let [user (u/find-user {:email email, :role :applicant, :company.id {"$exists" false}})]
    (let [company (c/create-company (select-keys data [:name :y :address1 :po :zip]))]
      (u/update-user-by-email email {:company  {:id (:id company), :role :admin}})
      (ok))
    (fail :error.user-not-found)))

(defcommand company-cancel-invite
  {:parameters [tokenId]
   :user-roles #{:applicant}
   :pre-checks [validate-user-is-admin-or-company-admin]}
  [{:keys [created user application] :as command}]
  (let [token (mongo/by-id :token tokenId)
        token-company-id (get-in token [:data :company :id])
        user-company-id (get-in user [:company :id])]
    (if-not (= token-company-id user-company-id)
      (fail! :forbidden)))
  (mongo/update-by-id :token tokenId {$set {:used created}})
  (ok))
