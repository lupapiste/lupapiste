(ns lupapalvelu.exports-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.set :refer [difference]]
            [lupapalvelu.exports :refer [price-classes-for-operation]]
            [lupapalvelu.operations :as ops]))

(def keyset (comp set keys))

(fact "Every operation has price class definition"
  (difference (keyset ops/operations) (keyset price-classes-for-operation) ) => #{})
