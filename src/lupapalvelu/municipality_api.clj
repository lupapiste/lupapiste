(ns lupapalvelu.municipality-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]
            [sade.core :refer :all]
            [lupapalvelu.organization :as o]
            [lupapalvelu.action :refer [defquery]]))

(defquery municipality-borders
  {:user-roles #{:anonymous}
   :description "Get municipality borders in GeoJSON format."}
  [command]
  (ok :data {}))

(defn municipality-name [lang id]
  (lupapalvelu.i18n/localize lang :municipality id))

(defn active-municipalities-internal [organizations]
  (for [[muni-id scopes]
        (group-by :municipality (flatten (map (partial :scope) organizations)))
        ]
    {:id muni-id
     :nameFi (municipality-name "fi" muni-id) :nameSv (municipality-name "sv" muni-id)
     :applications (->> scopes (filter :new-application-enabled) (map :permitType))
     :infoRequests (->> scopes (filter :inforequest-enabled) (map :permitType))
     :opening (->> scopes (filter :opening) (map #(select-keys % [:permitType :opening])))}
    ))

(defquery active-municipalities
  {:user-roles #{:anonymous}
   :description "Return applications, info requests, and openings by
municipality"}
  (ok :municipalities (active-municipalities-internal (o/get-organizations))))
