(ns lupapalvelu.archiving-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

; Jarvenpaa R has archiving enabled in minimal, ie Sipoo doesn't
(apply-remote-minimal)

(facts "permanent-archive-enabled"
  (facts "query without parameters"
    (facts "normal authority"
      (fact "enabled"
        (query raktark-jarvenpaa :permanent-archive-enabled) => ok?)
      (fact "disabled"
        (query sonja :permanent-archive-enabled) => unauthorized?))
    (facts "authorityAdmin"
      (fact "enabled"
        (query jarvenpaa :permanent-archive-enabled) => ok?)
      (fact "disabled"
        (query sipoo :permanent-archive-enabled) => unauthorized?))
    (facts "applicant"
      (fact "disabled"
        (query pena :permanent-archive-enabled) => unauthorized?))
    (fact "tos-editor and tos-publisher, but not authority in organization"
      (query torsti :permanent-archive-enabled) => ok?))
  (facts "with application - archive enabled in municipality"
    (let [app-id (create-app-id pena :propertyId jarvenpaa-property-id :address "Arkistotie 13")]
      (facts "normal authority"
        (fact "enabled"
          (query raktark-jarvenpaa :permanent-archive-enabled :id app-id) => ok?)
        (fact "wrong municipality"
          (query sonja :permanent-archive-enabled :id app-id) => not-accessible?))
      (facts "authorityAdmin"
        (fact "not allowed an application query"
          (query jarvenpaa :permanent-archive-enabled :id app-id) => not-accessible?)
        (fact "application not accessible"
          (query sipoo :permanent-archive-enabled :id app-id) => not-accessible?))
      (facts "applicant"
        (fact "enabled"
          (query pena :permanent-archive-enabled :id app-id) => ok?))))
  (facts "with application - archive not enabled in municipality"
    (let [app-id (create-app-id pena :propertyId sipoo-property-id :address "Arkistotie 13")]
      (facts "normal authority"
        (fact "wrong municipality"
          (query raktark-jarvenpaa :permanent-archive-enabled :id app-id) => not-accessible?)
        (fact "unauthorized"
          (query sonja :permanent-archive-enabled :id app-id) => unauthorized?))
      (facts "authorityAdmin"
        (fact "application not accessible"
          (query jarvenpaa :permanent-archive-enabled :id app-id) => not-accessible?)
        (fact "application not accessible"
          (query sipoo :permanent-archive-enabled :id app-id) => not-accessible?))
      (facts "applicant"
        (fact "not enabled"
          (query pena :permanent-archive-enabled :id app-id) => unauthorized?)))))
