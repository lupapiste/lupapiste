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
   :pre-checks       [app/validate-authority-in-drafts
                      (partial permit/valid-permit-types {:YA :all})
                      yax/no-ya-backend
                      yax/has-extension-link-permits]}
  [{application :application}]
  (ok :extensions (yax/extensions-details application)))
