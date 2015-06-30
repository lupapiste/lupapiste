(ns lupapalvelu.statement-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.statement]))

(testable-privates lupapalvelu.statement version-is-greater-or-equal possible-statement-statuses)

(facts "version-is-greater-or-equal"
  (fact "source (evaluated) version"
    (version-is-greater-or-equal "2.1"   {:major 2 :minor 1 :micro 5}) => false
    (version-is-greater-or-equal "2.1.4" {:major 2 :minor 1 :micro 5}) => false
    (version-is-greater-or-equal "2.1.5" {:major 2 :minor 1 :micro 5}) => true
    (version-is-greater-or-equal "2.1.6" {:major 2 :minor 1 :micro 5}) => true)
  (fact "target version"
    (version-is-greater-or-equal 2.1     {:major 2 :minor 1 :micro 5}) => (throws AssertionError)
    (version-is-greater-or-equal "2.1.4" "2.1.5")                      => (throws AssertionError)
    (version-is-greater-or-equal "2.1.4" {:major 2 :minor 1})          => (throws AssertionError)))


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

