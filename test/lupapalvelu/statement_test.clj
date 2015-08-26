(ns lupapalvelu.statement-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.statement]))

(testable-privates lupapalvelu.statement possible-statement-statuses)

(let [test-app-R  {:municipality 753 :permitType "R"}
      test-app-P  {:municipality 753 :permitType "P"}
      test-app-YA {:municipality 753 :permitType "YA"}]

  ;; permit type R

  (fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.3"
   (possible-statement-statuses test-app-R) => (just ["puoltaa" "ei-puolla" "ehdoilla"] :in-any-order)
   (provided
     (organization/resolve-organization anything anything) => {:krysp {:R {:version "2.1.5"}}}))

  (fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.5"
    (possible-statement-statuses test-app-R) => (just ["puoltaa" "ei-puolla" "ehdoilla"
                                                       "ei-huomautettavaa" "ehdollinen" "puollettu"
                                                       "ei-puollettu" "ei-lausuntoa" "lausunto"
                                                       "kielteinen" "palautettu" "poydalle"] :in-any-order)
    (provided
      (organization/resolve-organization anything anything) => {:krysp {:R {:version "2.1.6"}}}))

  ;; permit type P

  (fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.3"
   (possible-statement-statuses test-app-P) => (just ["puoltaa" "ei-puolla" "ehdoilla"] :in-any-order)
   (provided
     (organization/resolve-organization anything anything) => {:krysp {:P {:version "2.1.5"}}}))

  (fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.5"
    (possible-statement-statuses test-app-P) => (just ["puoltaa" "ei-puolla" "ehdoilla"
                                                       "ei-huomautettavaa" "ehdollinen" "puollettu"
                                                       "ei-puollettu" "ei-lausuntoa" "lausunto"
                                                       "kielteinen" "palautettu" "poydalle"] :in-any-order)
    (provided
      (organization/resolve-organization anything anything) => {:krysp {:P {:version "2.2.0"}}}))

  ;; permit type YA

  (fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.3"
   (possible-statement-statuses test-app-YA) => (just ["puoltaa" "ei-puolla" "ehdoilla"] :in-any-order)
   (provided
     (organization/resolve-organization anything anything) => {:krysp {:YA {:version "2.1.3"}}}))

  (fact "get-possible-statement-statuses, permit type R, krysp yhteiset version 2.1.5"
    (possible-statement-statuses test-app-YA) => (just ["puoltaa" "ei-puolla" "ehdoilla"] :in-any-order)
    (provided
      (organization/resolve-organization anything anything) => {:krysp {:YA {:version "2.2.0"}}})))
