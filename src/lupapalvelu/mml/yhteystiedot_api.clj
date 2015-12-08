(ns lupapalvelu.mml.yhteystiedot-api
  (:require [lupapalvelu.mml.yhteystiedot :as yht]
            [sade.core :refer [ok now]]
            [sade.validators :as v]
            [lupapalvelu.action :refer [defquery disallow-impersonation] :as action]))

(defn- owners-of [property-id]
  (->> (yht/get-owners property-id)
       (map (fn [owner] (assoc owner :propertyId property-id)))))

(defquery owners
  {:parameters [propertyIds]
   :input-validators [(partial action/vector-parameter-of :propertyIds v/kiinteistotunnus?)]
   :pre-checks [disallow-impersonation]
   :user-roles #{:authority}}
  [_]
  (ok :owners (flatten (map owners-of propertyIds)))) ; pmap?
