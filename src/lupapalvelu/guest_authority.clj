(ns lupapalvelu.guest-authority
  (:require [monger.operators :refer :all]
            [sade.core :refer [fail]]
            [lupapalvelu.user :as usr]  ;; usr works better with code completion.
            [lupapalvelu.organization :as org]
            [lupapalvelu.authorization :as auth]))

(defn resolve-candidate [admin email]
  (let [candidate          (-> email
                               usr/canonize-email
                               usr/get-user-by-email
                               usr/with-org-auth)
        admin-org-id       (usr/authority-admins-organization-id admin)
        reader-roles       (set (remove #{:guest} org/authority-roles))
        candidate-org-ids  (usr/organization-ids-by-roles candidate reader-roles)
        already-has-access (->> candidate-org-ids
                                (filter #(= admin-org-id %))
                                not-empty
                                boolean)]
    (assoc (select-keys candidate [:firstName :lastName])
           :hasAccess already-has-access)))

(defn- guest-list
  ([org-id & [ignore-email]]
   (->> org-id
        org/get-organization
        :guestAuthorities
        (filter #(not= ignore-email (:email %))))))

(defn update-guest
  "Either updates existing guest authority or adds new one."
  [admin email name role]
  (let [email (usr/canonize-email email)
        org-id (usr/authority-admins-organization-id admin)
        guests (concat (guest-list org-id email)
                       [{:email email
                         :name name
                         :role role}])]
    (org/update-organization org-id {$set {:guestAuthorities guests}})))

(defn guests [admin]
  (guest-list (usr/authority-admins-organization-id admin)))

(defn remove-guest
  [admin email]
  (let [email (usr/canonize-email email)
        org-id (usr/authority-admins-organization-id admin)]
    (org/update-organization org-id {$set {:guestAuthorities (guest-list org-id email)}})))

(defn no-duplicate-guests
  "Pre check for avoiding duplicate guests or unnecessary access.
  Note: only application-defined access is checked."
  [{{email :email} :data} application]
  (let [guest (usr/get-user-by-email email)]
    (when (auth/user-authz? auth/all-authz-roles application guest)
      (fail "error.already-has-access"))))
