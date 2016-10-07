(ns lupapalvelu.ya-extension-api
  "YA extension (jatkoaika) application related."
  (:require [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.property :as prop]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [lupapalvelu.application :as app]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.ya-extension :as yax]))

(defquery ya-extensions
  {:description      "Extension periods for the given main YA application. "
   :parameters       [id]
   :user-roles       #{:applicant :authority}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles
   :states           states/post-verdict-states
   :pre-checks       [(partial permit/valid-permit-types {:YA :all})
                      yax/has-extension-info-or-link-permits]}
  [{application :application}]
  (ok :extensions (yax/extensions-details application)))

(defquery approve-ya-extension
  {:description     "Pseudo query for checking whether an :ya-jatkoaika
  application can be approved."
   :parameters      [id]
   :user-roles      #{:authority}
   :org-authz-roles #{:approver}
   :states          #{:submitted :complementNeeded}
   :pre-checks      [(app/allow-primary-operations #{:ya-jatkoaika})]}
  [_])
