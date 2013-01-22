(ns lupapalvelu.common-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]]))

(fact "minimal fixture has 3 municipalities"
  (apply-remote-minimal)
  (let [resp    (query pena :municipalities)]
    (count (:municipalities resp)) => 3))
