(ns lupapalvelu.mml.yhteystiedot-api
  (:require [lupapalvelu.mml.yhteystiedot :refer :all]
            [sade.core :refer [ok]]
            [lupapalvelu.action :refer [defquery disallow-impersonation]]))

(defquery owners
  {:parameters [propertyId]
   :pre-checks [disallow-impersonation]
   :roles      [:authority]}
  [_]
  (ok :owners (get-owners propertyId)))
