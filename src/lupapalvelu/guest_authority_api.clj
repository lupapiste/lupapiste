(ns lupapalvelu.guest-authority-api
  (:require [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [lupapalvelu.guest-authority :as guest]
            [lupapalvelu.authorization :as auth]))

(defquery resolve-guest-authority-candidate
  {:parameters [email]
   :input-validators [(partial action/non-blank-parameters [:email])]
   :user-roles #{:authorityAdmin}
   :description "Checks if the given email already maps to a user in
   the system. If so, the response contains name information and
   whether the user already has access rights to every application for
   the organization. The latter is needed in order to avoid
   unnecessary authority additions."}
  [{admin :user}]
  (ok :user (guest/resolve-guest-authority-candidate admin email)))

(defcommand update-guest-authority-organization
  {:parameters [email name role]
   :input-validators [(partial action/non-blank-parameters [:email :name])]
   :user-roles #{:authorityAdmin}
   :description "Add or update organization's guest authority."}
  [{admin :user}]
  (guest/update-guest-authority-organization admin email name role))

(defquery guest-authorities-organization
  {:user-roles #{:authorityAdmin}
   :description "List of guest authorities for the authority admin's
   organization."}
  [{admin :user}]
  (ok :guestAuthorities (guest/organization-guest-authorities (usr/authority-admins-organization-id admin))))

(defcommand remove-guest-authority-organization
  {:parameters [email]
   :input-validators [(partial action/non-blank-parameters [:email])]
   :user-roles #{:authorityAdmin}}
  [{admin :user}]
  (guest/remove-guest-authority-organization admin email))

(defcommand invite-guest
  {:description         "Sends invitation email and grants guest (or
  guestAuthority) access. Guest 'authorization' does not need to be
  explicitly acknowledged by the invitee."
   :user-roles          #{:applicant :authority}
   :parameters          [:id :email :role]
   :optional-parameters [:text]
   :pre-checks          [guest/no-duplicate-guests]
   :input-validators    [(partial action/non-blank-parameters [:email])
                         action/email-validator
                         guest/valid-guest-role]
   :states              states/all-application-states
   :notified            true}
  [command]
  (guest/invite-guest command))

(defquery application-guests
  {:description "List of application guests and guest authorities."
   :user-roles #{:applicant :authority}
   :user-authz-roles auth/default-authz-reader-roles
   :parameters [:id]
   :states states/all-application-states}
  [command]
  (ok :guests (guest/application-guests command)))

(defcommand toggle-guest-subscription
  {:description      "Un/subscribes notifications for guests. Corresponding
  command in authorization_api is not feasible, since the rights are
  different for guests."
   :user-roles       #{:applicant :authority}
   :user-authz-roles #{:guest :guestAuthority :writer}
   :parameters       [:id :username :unsubscribe]
   :input-validators [(partial action/non-blank-parameters [:username])]
   :states           states/all-application-states}
  [command]
  (guest/toggle-guest-subscription command))

(defcommand delete-guest-application
  {:description "Cancels the guest access from application."
   :user-roles #{:applicant :authority}
   :parameters [:id :username]
   :input-validators [(partial action/non-blank-parameters [:username])]
   :pre-checks [guest/auth-modification-check]
   :states states/all-application-states}
  [command]
  (guest/delete-guest-application command))

(defquery guest-authorities-application-organization
  {:description "Guest authorities that are defined for this
  application's organization."
   :parameters [:id]
   :user-roles #{:authority}
   :states states/all-application-states}
  [command]
  (ok :guestAuthorities (guest/guest-authorities-application-organization command)))
