(ns lupapalvelu.smoketest.organization-smoke-tests
  (:require [lupapalvelu.smoketest.core :refer [defmonster]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]))

(def organization-keys [:scope])

(def organizations (delay (mongo/select :organizations {} organization-keys)))

(defmonster permit-type-only-in-single-municipality-scope
  (let [results (->> @organizations
                     (mapcat :scope)
                     (group-by :municipality)
                     (map (fn [[muni scopes]] [muni (map :permitType scopes)]))
                     (remove #(apply distinct? (second %)))
                     (into {}))]
    (if (seq results)
      {:ok false :results (str results)}
      {:ok true})))
