(ns lupapalvelu.system.util
  (:require [schema.core :as sc]
            [integrant.core :as ig]))

(defn config-validator!
  [schema]
  (let [checker (sc/checker schema)]
    (fn [config]
      (let [errors (checker config)]
        (if errors
          ;; Print problems but not the whole config map, as it might contain
          ;; credentials etc which we don't necessarily want to print out.
          (throw (ex-info "Config does not match schema!" {:schema schema
                                                           :errors errors}))
          config)))))

(defn missing-components
  "Return all `components` which are missing from `blueprint`."
  [blueprint components]
  {:pre [(map? blueprint) (coll? components)]}
  (reduce (fn [acc k]
            (if (seq (ig/find-derived blueprint k))
              acc
              (conj acc k)))
          #{}
          components))

(defn assert-components!
  "Assert that `blueprint` contains at least every `components`."
  [blueprint components]
  (when-let [missing (not-empty (missing-components blueprint components))]
    (throw (ex-info "Blueprint is missing required components"
                    {:missing   missing
                     :available (into [] (keys blueprint))})))
  blueprint)
