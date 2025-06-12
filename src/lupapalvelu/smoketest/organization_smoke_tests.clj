(ns lupapalvelu.smoketest.organization-smoke-tests
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mongocheck.core :refer [mongocheck]]
            [lupapalvelu.organization :as org]
            [lupapalvelu.smoketest.core :refer [defmonster]]
            [monger.operators :refer :all]))

(def organization-keys [])

(defmonster permit-type-only-in-single-municipality-scope
  (let [results (->> (mongo/select :organizations {} [:scope])
                     (mapcat :scope)
                     (group-by :municipality)
                     (map (fn [[muni scopes]] [muni (map :permitType scopes)]))
                     (remove #(apply distinct? (second %)))
                     (into {}))]
    (if (seq results)
      {:ok false :results (str results)}
      {:ok true})))

(mongocheck :organizations
  #(when-let [res (org/organization-checker (mongo/with-id %))]
     (assoc (select-keys % [:id]) :errors res))
  organization-keys)
