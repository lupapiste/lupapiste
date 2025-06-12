(ns lupapalvelu.building-site-api
  (:require [lupapalvelu.action :as action :refer [defquery]]
            [lupapalvelu.building-site :as bsite]
            [sade.core :refer [fail ok]]))

(defquery building-site-information
  {:description      "Fetches the building site (property) information from the MML KTJ (KiinteistöTietoJärjestelmä).
                      The same info is indirectly available to all users by creating an application on the property"
   :parameters       [propertyId]
   :input-validators [(partial action/property-id-parameters [:propertyId])]
   :permissions      [{:required [:global/property-info]}]}
  [_]
  (if-let [data (some-> propertyId
                        bsite/fetch-ktj-tiedot
                        bsite/post-process-ktj-tiedot
                        (select-keys [:nimi :rekisterointipvm :maapintaala :vesipintaala]))]
    (ok :data data)
    (fail :error.property-not-found)))
