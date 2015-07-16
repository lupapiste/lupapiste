(ns lupapalvelu.verdict-test
  (require [midje.sweet :refer :all]
           [midje.util :refer [testable-privates]]
           [lupapalvelu.verdict :refer :all]
           [lupapalvelu.permit :as permit]
           [sade.core :refer [now]]))

(testable-privates lupapalvelu.verdict get-verdicts-with-attachments)

(facts "Verdicts parsing"
  (let [xml (sade.xml/parse (slurp "dev-resources/krysp/no-verdicts.xml"))]
    (fact "No verdicts found in the attachment parsing phase"
      (count (get-verdicts-with-attachments {:permitType "R"} {} (now) xml (permit/get-verdict-reader "R"))) => 0
      )))
