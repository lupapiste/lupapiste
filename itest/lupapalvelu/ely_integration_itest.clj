(ns lupapalvelu.ely-integration-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.integrations.ely :as ely]
            [lupapalvelu.domain :as domain]))

(apply-remote-minimal)

(facts "ELY statement-request"
  (let [app (create-and-submit-application mikko :propertyId sipoo-property-id :operation "poikkeamis")
        statement-subtype "Lausuntopyynt\u00f6 poikkeamishakemuksesta"]
    (generate-documents app mikko)

    (fact "request XML is created"
      (command sonja :ely-statement-request :id (:id app) :subtype statement-subtype :saateText "moro") => ok?
      (let [statements (:statements (query-application mikko (:id app)))
            ely-statement (first statements)]
        (count statements) => 1
        (fact "ELY person" (get ely-statement :person) => (ely/ely-statement-giver statement-subtype))
        (fact "state ok" (get ely-statement :state) => "requested")
        (fact "external config ok"
          (get ely-statement :external) => (just {:partner "ely"
                                                  :messageId string?
                                                  :subtype statement-subtype}))))))
