(ns lupapalvelu.migration.scope-migrations
  "Utilities for updating organization scopes with migrations"
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.util :as util]))

(defn update-org-scopes
  "Query should match organizations that we want to update.
  Filter-fn gets the scope as a parameter and returns truthy if it will be updated.
  Update-fn gets the index for the scope and the scope itself as parameters and returns a mongo update map."
  [query filter-fn update-fn]
  (doseq [{:keys [id scope]} (mongo/select :organizations query [:scope])]
    (some->> scope ; actually the scopes array, name is just misleading
             (keep-indexed (fn [idx scope]
                             (when (filter-fn scope)
                               [idx scope])))
             (reduce (fn [acc [idx scope]]
                       (util/deep-merge acc (update-fn idx scope)))
                     {})
             not-empty
             (mongo/update-by-id :organizations id))))
