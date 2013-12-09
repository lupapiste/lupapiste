(ns lupapalvelu.mml.yhteystiedot-api
  (:require [lupapalvelu.mml.yhteystiedot :refer :all]
            [lupapalvelu.core :refer :all]
            [lupapalvelu.action :refer [defquery]]))

(defquery owners
  {:parameters [id]
   :verified   true
   :roles      [:authority]}
  (ok (get-owners id)))
