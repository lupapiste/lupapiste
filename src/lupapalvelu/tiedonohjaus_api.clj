(ns lupapalvelu.tiedonohjaus-api
  (:require [lupapalvelu.action :refer [defquery defcommand non-blank-parameters]]
            [sade.core :refer [ok fail]]
            [lupapalvelu.tiedonohjaus :as t]
            [lupapalvelu.organization :as o]
            [lupapalvelu.organization-api :as oa]
            [monger.operators :refer :all]))

(defquery available-tos-functions
          {:user-roles #{:anonymous}
           :parameters [organizationId]
           :input-validators [(partial non-blank-parameters [:organizationId])]}
          (let [functions (t/get-functions-from-toj organizationId)]
            (ok :functions functions)))

(defcommand set-tos-function-for-operation
            {:parameters [operation functionCode]
             :user-roles #{:authorityAdmin}
             :input-validators [(partial non-blank-parameters [:functionCode :operation])]}
            [{user :user}]
            (let [orgId (oa/authority-admins-organization-id user)
                  organization (o/get-organization orgId)
                  operation-valid? (some #{operation} (:selected-operations organization))
                  code-valid? (some #{functionCode} (map :code (t/get-functions-from-toj orgId)))]
              (if (and operation-valid? code-valid?)
                (do (o/update-organization orgId {$set {(str "operations-tos-functions." operation) functionCode}})
                    (ok))
                (fail "Invalid organization or operation"))))
