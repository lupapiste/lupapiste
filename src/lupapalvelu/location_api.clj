(ns lupapalvelu.location-api
  (:require [lupapalvelu.action :refer [defquery defcommand non-blank-parameters]]
            [lupapalvelu.location :as location]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]))

(defcommand set-operation-location
  {:description      "Set coordinates for the operation structure/building."
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/edit-draft]}
                      {:context  {:application {:state states/all-but-terminal}}
                       :required [:application/edit]}]
   :parameters       [:id]
   :input-validators [location/LocationParams]
   :pre-checks       [location/has-location-operations
                      location/valid-operation]}
  [command]
  (location/set-operation-location command)
  (ok))

(defquery location-operations
  {:description      "Information about location operations and their structures/buildings."
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/edit-draft]}
                      {:required [:application/read]}]
   :parameters       [:id]
   :input-validators [(partial non-blank-parameters [:id])]
   :pre-checks       [location/has-location-operations]}
  [{:keys [application]}]
  (ok :operations (location/location-operation-list application)))
