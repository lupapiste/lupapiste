(ns lupapalvelu.guest-api
  "Guests and guest authorities are users that have been granted read
  access for an individual application.
  Guest authorities are defined for an organization by the authority
  admin and authority can invite them (grant access) to application.
  Guests are invited by the applicant (or similar role)."
  (:require [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.guest :as guest]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]
            [sade.util :as util]))

(defquery resolve-guest-authority-candidate
  {:parameters       [organizationId email]
   :input-validators [(partial action/non-blank-parameters [:email])]
   :permissions      [{:required [:organization/admin]}]
   :description      "Checks if the given email already maps to a user in
   the system. If so, the response contains name information and
   whether the user already has access rights to every application for
   the organization. The latter is needed in order to avoid
   unnecessary authority additions."}
  [_]
  (ok :user (guest/resolve-guest-authority-candidate organizationId email)))

(defcommand update-guest-authority-organization
  {:parameters       [organizationId email firstName lastName description]
   :input-validators [(partial action/non-blank-parameters [:email :firstName :lastName])
                      action/email-validator]
   :permissions      [{:required [:organization/admin]}]
   :description      "Add or update organization's guest authority."}
  [{admin :user}]
  (guest/update-guest-authority-organization organizationId admin email firstName lastName description))

(defquery guest-authorities-organization
  {:parameters  [organizationId]
   :permissions [{:required [:organization/admin]}]
   :description "List of guest authorities for the authority admin's organization."}
  [{:keys [user-organizations]}]
  (ok :guestAuthorities (:guestAuthorities (util/find-by-id organizationId user-organizations))))

(defcommand remove-guest-authority-organization
  {:description      "Removes guestAuthority from organisation and from every (applicable) application within the
                     organization."
   :parameters       [organizationId email]
   :input-validators [(partial action/non-blank-parameters [:email])]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (ok :applications (guest/remove-guest-authority-organization organizationId email)))

(defcommand invite-guest
  {:description         "Sends invitation email and grants guest (or
  guestAuthority) access. Guest 'authorization' does not need to be
  explicitly acknowledged by the invitee."
   :user-roles          #{:applicant :authority}
   :parameters          [:id :email :role]
   :optional-parameters [:text]
   :pre-checks          [guest/no-duplicate-guests
                         guest/known-guest-authority]
   :input-validators    [(partial action/non-blank-parameters [:email])
                         action/email-validator
                         guest/valid-guest-role]
   :states              (states/all-application-states-but [:canceled])
   :notified            true}
  [command]
  (guest/invite-guest command))

(defquery application-guests
  {:description      "List of application guests and guest authorities."
   :user-roles       #{:applicant :authority}
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/reader-org-authz-roles
   :parameters       [:id]
   :states           states/all-application-states}
  [command]
  (ok :guests (guest/application-guests command)))

(defcommand toggle-guest-subscription
  {:description      "Un/subscribes notifications for guests. Corresponding
  command in authorization_api is not feasible, since the rights are
  different for guests."
   :user-roles       #{:applicant :authority}
   :user-authz-roles #{:guest :guestAuthority :writer :foreman}
   :parameters       [:id :username :unsubscribe]
   :input-validators [(partial action/non-blank-parameters [:username])]
   :pre-checks       [foreman/allow-foreman-only-in-foreman-app]
   :states           states/all-application-states}
  [command]
  (guest/toggle-guest-subscription command))

(defcommand delete-guest-application
  {:description      "Cancels the guest access from application."
   :user-roles       #{:applicant :authority}
   :user-authz-roles roles/writer-roles-with-foreman
   :parameters       [:id :username]
   :input-validators [(partial action/non-blank-parameters [:username])]
   :pre-checks       [guest/auth-modification-check
                      foreman/allow-foreman-only-in-foreman-app]
   :states           states/all-application-states}
  [command]
  (guest/delete-guest-application command))

(defquery guest-authorities-application-organization
  {:description "Guest authorities that are defined for this
  application's organization."
   :parameters  [:id]
   :user-roles  #{:authority}
   :states      states/all-application-states}
  [command]
  (ok :guestAuthorities (guest/guest-authorities-application-organization command)))
