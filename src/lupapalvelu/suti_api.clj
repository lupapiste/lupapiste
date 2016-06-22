(ns lupapalvelu.suti-api
  (:require [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.organization :as org]
            [lupapalvelu.operations :as op]
            [lupapalvelu.user :as usr]
            [lupapalvelu.suti :as suti]))

(defcommand suti-toggle-operation
  {:description "Toggles operation either requiring Suti or not."
   :parameters [operationId flag]
   :input-validators [(partial action/non-blank-parameters [:operationId])
                      (partial action/boolean-parameters [:flag])]
   :user-roles #{:authorityAdmin}
   :feature :suti-integration}
  [{user :user}]
  (let [fun (if flag suti/add-operation suti/remove-operation)]
    (fun (suti/admin-org user) operationId)))
