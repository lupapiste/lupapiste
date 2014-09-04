(ns lupapalvelu.exports-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.set :refer [difference]]
            [lupapalvelu.exports :refer [kayttotarkoitus-hinnasto price-classes-for-operation]]
            [lupapalvelu.application :as app]
            [lupapalvelu.operations :as ops]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]))

(testable-privates lupapalvelu.exports resolve-price-class)

(def keyset (comp set keys))

(fact "Every operation has price class definition"
  (difference (keyset ops/operations) (keyset price-classes-for-operation) ) => empty?)

(fact "Every kayttotarkoitus has price class"
  (let [every-kayttotarkoitus (remove (partial = "ei tiedossa") (map :name schemas/rakennuksen-kayttotarkoitus))]
    (difference (set every-kayttotarkoitus) (keyset @kayttotarkoitus-hinnasto))) => empty?)

(fact "Uusi kerrostalo"
  (let [application (app/make-application "LP-123" "asuinrakennus" 0 0 "address" "01234567891234" "753" "753-R" false false [] {} 123)
        op (resolve-price-class application (first (:operations application)))]
    (fact "Default value '011 yhden asunnon talot' = C"
      (:priceClass op) => "C")))