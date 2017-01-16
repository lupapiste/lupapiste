(ns lupapalvelu.smoketest.organization-smoke-tests
  (:require [schema.core :as sc]
            [lupapiste.mongocheck.core :refer [mongocheck]]
            [lupapalvelu.smoketest.core :refer [defmonster]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
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
  #(when-let [res (sc/check org/Organization (mongo/with-id %))]
     (assoc (select-keys % [:id]) :errors res))
  organization-keys)
