(ns lupapalvelu.ryhti.api.malli-schema
  (:require [lupapalvelu.ryhti.api.malli-schema.generated :as generated]
            [malli.core :as malli]
            [malli.util :as mu]))

;; It is possible to override some of the generated schemas here, if the
;; generated schema isn't perfect for some reason.
;;
;; - def a new version of some schema, you can use malli.utils to e.g. add new
;;   map properties etc.
;; - assoc the new version of schema into the registry, with same name,
;;   as all schemas refer to each other through registry,
;;   the references will now use the modified schema.

;; Example:
;; (def InternalNetwork (mu/assoc generated/InternalNetwork :foo :string))
;; (def registry (assoc generated/registry :ryhti/InternalNetwork InternalNetwork))

(defn- update-schema
  [schema f]
  ;; Parsing the schema in isolation breaks as it `:ref`s to other
  ;; schemas in the registry -> use `::malli/allow-invalid-refs` to ignore such `:ref`s.
  (-> (malli/schema schema {::malli/allow-invalid-refs true})
      f
      ;; NOTE: For some reason we must convert back to the form
      ;; representation, otherwise stuff like `(malli/coercer schema)` fails.
      malli/form))

(def DeviationPermitIssue
  (update-schema
    generated/DeviationPermitIssue
    ;; Make decision optional so we can use the schema earlier in the permit life cycle
    (fn [schema] (mu/update schema :deviationPermitDecision (fn [x] [:maybe x])))))

(def registry
  (-> generated/registry
      (assoc :ryhti/DeviationPermitIssue DeviationPermitIssue)))

;; Top level schemas

(def BuildingPermitIssueSchema
  (malli/schema [:schema {:registry registry}
                 [:ref :ryhti/BuildingPermitIssue]]))

(def DeviationPermitIssueSchema
  (malli/schema [:schema {:registry registry}
                 [:ref :ryhti/DeviationPermitIssue]]))
