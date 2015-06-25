(ns lupapalvelu.statement-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.statement]))

(testable-privates lupapalvelu.statement version-is-greater-or-equal)

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