(ns lupapalvelu.migration.core-test
  (:require [lupapalvelu.migration.core :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.migration.core ->assertion)

(facts "about ->assertion"
  ((eval (->assertion ['(= 1 1) '(= 2 2)]))) => nil
  ((eval (->assertion ['(= 1 1) '(= 2 3)]))) => (throws AssertionError #"= 2 3"))