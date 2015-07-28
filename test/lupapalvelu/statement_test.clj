(ns lupapalvelu.statement-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.statement]))

(testable-privates lupapalvelu.statement possible-statement-statuses)

(let [test-app {:municipality 753 :permitType "R"}]
  (fact "get-possible-statement-statuses, krysp yhteiset version 2.1.3"
   (possible-statement-statuses test-app) => (just ["puoltaa" "ei-puolla" "ehdoilla"] :in-any-order)
   (provided
     (organization/resolve-organization anything anything) => {:krysp {:R {:version "2.1.5"}}}))

  (fact "get-possible-statement-statuses, krysp yhteiset version 2.1.5"
    (possible-statement-statuses test-app) => (just ["puoltaa" "ei-puolla" "ehdoilla"
                                                     "ei-huomautettavaa" "ehdollinen" "puollettu"
                                                     "ei-puollettu" "ei-lausuntoa" "lausunto"
                                                     "kielteinen" "palautettu" "poydalle"] :in-any-order)
    (provided
      (organization/resolve-organization anything anything) => {:krysp {:R {:version "2.1.6"}}})))

