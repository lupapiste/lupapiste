(ns lupapalvelu.assignment-api
  (:require [lupapalvelu.action :refer [defquery]]
            [lupapalvelu.assignment :as assignment]
            [sade.core :refer :all]))

(defquery assignments
  {:description "Return the entire collection"
   :user-roles #{:authority}}
  (ok :assignments (assignment/get-assignments)))
