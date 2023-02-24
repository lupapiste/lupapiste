(ns lupapalvelu.building-site-api
  (:require [lupapalvelu.action :refer [defquery disallow-impersonation]]
            [lupapalvelu.building-site :as bsite]
            [lupapalvelu.action :as action]
            [sade.core :refer [ok]]))

(defquery building-site-information
  {:description      "Fetches the building site (property) information from the MML KTJ (KiinteistöTietoJärjestelmä).
                      The same info is indirectly available to all users by creating an application on the property"
   :parameters       [propertyId]
   :input-validators [(partial action/property-id-parameters [:propertyId])]
   :permissions      [{:required [:application/edit]}]}
  [_]
  (ok :data (-> propertyId
                (bsite/fetch-ktj-tiedot)
                (select-keys [:nimi :rekisterointipvm :maapintaala :vesipintaala]))))
