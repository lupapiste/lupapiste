(ns lupapalvelu.municipality-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]
            [sade.core :refer :all]
            [lupapalvelu.organization :as org]
            [lupapalvelu.action :refer [defquery non-blank-parameters]]))

(defquery municipality-borders
  {:user-roles #{:anonymous}
   :description "Get municipality borders in GeoJSON format."}
  [command]
  (ok :data {}))

(defn municipality-name [lang id]
  (lupapalvelu.i18n/localize lang :municipality id))

(defn active-municipalities-from-organizations [organizations]
  (for [[muni-id scopes] (group-by :municipality (flatten (map :scope organizations)))]
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
  (ok :municipalities (active-municipalities-from-organizations (org/get-organizations))))

(defquery municipality-active
  {:parameters [municipality]
   :input-validators [(partial non-blank-parameters [:municipality])]
   :user-roles #{:anonymous}}
  [_]
  (let [organizations (org/get-organizations {:scope.municipality municipality})
        active-map (first (active-municipalities-from-organizations organizations))]
      (ok (select-keys active-map [:applications :infoRequests :opening]))))
