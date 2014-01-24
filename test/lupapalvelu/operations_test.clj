(ns lupapalvelu.operations-test
  (:require [lupapalvelu.operations :refer :all]
            [midje.sweet :refer :all]
            [lupapalvelu.document.schemas :as schemas]))

(facts "check that every operation refers to existing schema"
  (doseq [[op {:keys [schema required]}] operations
          schema (cons schema required)]
    (schemas/get-schema (schemas/get-latest-schema-version) schema) => truthy))

(facts "verify that every operation has link-permit-required set"
       (for [[op propeties] operations]
         (fact (contains? propeties :link-permit-required) => truthy)) => truthy)

(facts "check that correct operations requires linkPermit"
       (fact (:ya-jatkoaika link-permit-required-operations) => truthy)
       (fact (:tyonjohtajan-nimeaminen link-permit-required-operations) => truthy)
       (fact (:suunnittelijan-nimeaminen link-permit-required-operations) => truthy)
       (fact (:jatkoaika link-permit-required-operations) => truthy)
       (fact (:aloitusoikeus link-permit-required-operations) => truthy)
       (fact (count link-permit-required-operations) => 5))
