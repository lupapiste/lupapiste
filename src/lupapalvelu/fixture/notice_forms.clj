(ns lupapalvelu.fixture.notice-forms
  (:require [lupapalvelu.automatic-assignment.schemas :as schemas]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.system.util-components :as components]
            [sade.core :refer :all]
            [sade.util :as util]))

(defn make-filter [form-type  name target]
  (schemas/valid-filter! (util/strip-nils {:id       (mongo/create-id)
                                           :rank     0
                                           :modified (now)
                                           :name     name
                                           :criteria {:notice-forms [form-type]}
                                           :target   target})))

(def avi-role-id "5c9cd661a054b7391c46b2a1")

(defn update-sipoo [{org-id :id :as organization}]
  (if (= org-id "753-R")
    (org/valid-organization!
      (-> organization
          (update :handler-roles conj {:id   avi-role-id
                                       :name {:fi "AVI-Käsittelijä"
                                              :sv "AVI-Handläggare"
                                              :en "AVI Handler"}})
          (assoc :notice-forms {:construction {:enabled     true
                                               :text        {:fi "AVI-ohje"
                                                             :sv "AVI-hjälp"
                                                             :en "AVI help"}
                                               :integration true}
                                :terrain      {:enabled true
                                               :text    {:fi "Maasto-ohje"
                                                         :sv "Terränghjälp"
                                                         :en "Terrain help"}}
                                :location     {:enabled true
                                               :text    {:fi "Sijaintiohje"
                                                         :sv "Platshjälp"
                                                         :en "Location help"}}}
                 :automatic-assignment-filters [(make-filter "construction"
                                                             "Rakentamistehtävä"
                                                             {:handler-role-id avi-role-id})
                                                (make-filter "terrain"
                                                             "Maastopuuhaa"
                                                             ;; Ronja
                                                             {:user-id "777777777777777777000024"})
                                                (make-filter "location"
                                                             "Sijainti?"
                                                             nil)])))
    organization))


(defmethod fixture/fixture :notice-forms
  [_ system]
  (mongo/clear!)
  (mongo/insert-batch :users minimal/users)
  (mongo/insert-batch :companies minimal/companies)
  (mongo/insert-batch :organizations (mapv update-sipoo (minimal/organizations {:http-port (components/system->http-port system)}))))
