(ns lupapalvelu.guest-api
  "Guests and guest authorities are users that have been granted read
  access for an individual application.
  Guest authorities are defined for an organization by the authority
  admin and authority can invite them (grant access) to application.
  Guests are invited by the applicant (or similar role)."
  (:require [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.guest :as guest]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]))

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
  {:parameters [email firstName lastName description]
   :input-validators [(partial action/non-blank-parameters [:email :firstName :lastName])]
   :user-roles #{:authorityAdmin}
   :description "Add or update organization's guest authority."}
  [{admin :user}]
  (guest/update-guest-authority-organization admin email firstName lastName description))

(defquery guest-authorities-organization
  {:user-roles #{:authorityAdmin}
   :description "List of guest authorities for the authority admin's
   organization."}
  [{admin :user}]
  (ok :guestAuthorities (guest/organization-guest-authorities (usr/authority-admins-organization-id admin))))

(defcommand remove-guest-authority-organization {:description "Removes
  guestAuthority from organisation and from every (applicable)
  application within the organization."
   :parameters [email]
   :input-validators [(partial action/non-blank-parameters [:email])]
   :user-roles #{:authorityAdmin}}
  [{admin :user}]
  (ok :applications (guest/remove-guest-authority-organization admin email)))

(defcommand invite-guest
  {:description         "Sends invitation email and grants guest (or
  guestAuthority) access. Guest 'authorization' does not need to be
  explicitly acknowledged by the invitee."
   :user-roles          #{:applicant :authority}
   :user-authz-roles    (conj auth/default-authz-writer-roles :foreman)
   :parameters          [:id :email :role]
   :optional-parameters [:text]
   :pre-checks          [foreman/allow-foreman-only-in-foreman-app
                         guest/no-duplicate-guests
                         guest/known-guest-authority]
   :input-validators    [(partial action/non-blank-parameters [:email])
                         action/email-validator
                         guest/valid-guest-role]
   :states              (states/all-application-states-but [:canceled])
   :notified            true}
  [command]
  (guest/invite-guest command))

(defquery application-guests
  {:description "List of application guests and guest authorities."
   :user-roles #{:applicant :authority}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles auth/reader-org-authz-roles
   :parameters [:id]
   :states states/all-application-states}
  [command]
  (ok :guests (guest/application-guests command)))

(defcommand toggle-guest-subscription
  {:description      "Un/subscribes notifications for guests. Corresponding
  command in authorization_api is not feasible, since the rights are
  different for guests."
   :user-roles       #{:applicant :authority}
   :user-authz-roles #{:guest :guestAuthority :writer :owner :foreman}
   :parameters       [:id :username :unsubscribe]
   :input-validators [(partial action/non-blank-parameters [:username])]
   :pre-checks       [foreman/allow-foreman-only-in-foreman-app]
   :states           states/all-application-states}
  [command]
  (guest/toggle-guest-subscription command))

(defcommand delete-guest-application
  {:description "Cancels the guest access from application."
   :user-roles #{:applicant :authority}
   :user-authz-roles (conj auth/default-authz-writer-roles :foreman)
   :parameters [:id :username]
   :input-validators [(partial action/non-blank-parameters [:username])]
   :pre-checks [guest/auth-modification-check
                foreman/allow-foreman-only-in-foreman-app]
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
