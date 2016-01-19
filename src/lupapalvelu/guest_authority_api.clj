(ns lupapalvelu.guest-authority-api
  (:require [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.guest-authority :as guest]))

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
  (ok :user (guest/resolve-candidate admin email)))

(defcommand update-guest-authority-organization
  {:parameters [email name role]
   :input-validators [(partial action/non-blank-parameters [:email :name])]
   :user-roles #{:authorityAdmin}
   :description "Add or update organization's guest authority."}
  [{admin :user}]
  (guest/update-guest admin email name role))

(defquery guest-authorities-organization
  {:user-roles #{:authorityAdmin}
   :description "List of guest authorities for the authority admin's
   organization."}
  [{admin :user}]
  (ok :guestAuthorities (guest/guests admin)))

(defcommand remove-guest-authority-organization
  {:parameters [email]
   :input-validators [(partial action/non-blank-parameters [:email])]
   :user-roles #{:authorityAdmin}}
  [{admin :user}]
  (guest/remove-guest admin email))

(defquery add-guest-pseudo-query
  {:description "Different checks before invite-with-role"
   :parameters [email id]
   :user-roles #{:applicant :authority}
   :pre-checks [guest/no-duplicate-guests]
   :input-validators [(partial action/non-blank-parameters [:email])]})
