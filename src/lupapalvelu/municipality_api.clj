(ns lupapalvelu.municipality-api
  (:require [lupapalvelu.action :refer [defquery non-blank-parameters]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.organization :as org]
            [sade.core :refer :all]
            [sade.util :as util]))

(defquery municipality-borders
  {:user-roles #{:anonymous}
   :description "Get municipality borders in GeoJSON format."}
  [_]
  (ok :data {}))

(defn municipality-name [lang id]
  (i18n/localize lang :municipality id))

(defn active-municipalities-from-organizations [organizations timestamp]
  (for [[muni-id scopes] (group-by :municipality (flatten (map :scope organizations)))
        :let             [applications (->> scopes (filter :new-application-enabled) (map :permitType))]]
    {:id           muni-id
     :nameFi       (municipality-name "fi" muni-id) :nameSv (municipality-name "sv" muni-id)
     :applications applications
     :infoRequests (->> scopes
                        (filter #(or (:inforequest-enabled %) (:open-inforequest %)))
                        (mapv :permitType))
     :opening      (->> scopes
                        (map #(select-keys % [:permitType :opening]))
                        (remove (fn [{:keys [opening permitType]}]
                                  (let [will-open? (> (or opening 0) timestamp)
                                        is-open?   (contains? (set applications) permitType)]
                                    (or is-open? (not will-open?)))))
                        (into []))}))

(defquery active-municipalities
  {:user-roles #{:anonymous}}
  [{:keys [created]}]
  (ok :municipalities (into [] (active-municipalities-from-organizations (org/get-organizations)
                                                                         created))))

(defquery municipality-active
  {:parameters       [municipality]
   :input-validators [(partial non-blank-parameters [:municipality])]
   :user-roles       #{:anonymous}}
  [{:keys [created]}]
  (let [organizations (org/get-organizations {:scope.municipality municipality})
        active-map    (util/find-by-id municipality (active-municipalities-from-organizations organizations
                                                                                              created))]
      (ok (select-keys active-map [:applications :infoRequests :opening]))))
