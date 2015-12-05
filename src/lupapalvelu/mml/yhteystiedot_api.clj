(ns lupapalvelu.mml.yhteystiedot-api
  (:require [lupapalvelu.mml.yhteystiedot :refer :all]
            [sade.core :refer [ok now]]
            [lupapalvelu.action :refer [defquery disallow-impersonation] :as action]))

(defquery owners
  {:parameters [propertyId]
   :input-validators [(partial action/property-id-parameters [:propertyId])]
   :pre-checks [disallow-impersonation]
   :user-roles #{:authority}}
  [_]
  (ok :owners (get-owners propertyId)))
