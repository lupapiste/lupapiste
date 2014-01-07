(ns lupapalvelu.mml.yhteystiedot-api
  (:require [lupapalvelu.mml.yhteystiedot :refer :all]
            [lupapalvelu.core :refer :all]
            [lupapalvelu.action :refer [defquery]]))

(defquery owners
  {:parameters [propertyId]
   :roles      [:authority]}
  (ok :owners (get-owners propertyId)))
