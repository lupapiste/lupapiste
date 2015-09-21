(ns lupapalvelu.mml.yhteystiedot-api
  (:require [lupapalvelu.mml.yhteystiedot :refer :all]
            [sade.core :refer [ok now]]
            [lupapalvelu.action :refer [defquery defraw disallow-impersonation]]))

(defquery owners
  {:parameters [propertyId]
   :pre-checks [disallow-impersonation]
   :user-roles #{:authority}}
  [_]
  (ok :owners (get-owners propertyId)))
