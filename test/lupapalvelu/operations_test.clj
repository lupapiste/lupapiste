(ns lupapalvelu.operations-test
  (:require [lupapalvelu.operations :refer :all]
            [midje.sweet :refer :all]
            [lupapalvelu.document.schemas :as schemas]))

(facts "check that every operation refers to existing schema"
  (doseq [[op {:keys [schema required]}] operations
          schema (cons schema required)]
    (schemas/get-schema (schemas/get-latest-schema-version) schema) => truthy))
