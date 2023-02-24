(ns lupapalvelu.fixture.notice-forms
  (:require [lupapalvelu.automatic-assignment.schemas :refer [Filter]]
            [lupapalvelu.fixture.core :refer [deffixture]]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :refer [Organization]]
            [sade.core :refer :all]
            [sade.util :as util]
            [schema.core :as sc]))

(defn make-filter [form-type  name target]
  (sc/validate Filter (util/strip-nils {:id       (mongo/create-id)
                                        :rank     0
                                        :modified (now)
                                        :name     name
                                        :criteria {:notice-forms [form-type]}
                                        :target   target})))

(def avi-role-id "5c9cd661a054b7391c46b2a1")

(defn update-sipoo [{org-id :id :as organization}]
  (if (= org-id "753-R")
    (sc/validate Organization
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


(deffixture "notice-forms" {}
  (mongo/clear!)
  (mongo/insert-batch :users minimal/users)
  (mongo/insert-batch :companies minimal/companies)
  (mongo/insert-batch :organizations (map update-sipoo minimal/organizations)))
