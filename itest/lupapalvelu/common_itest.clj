(ns lupapalvelu.common-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]]))

(fact "minimal fixture has at least 4 municipalities"
  (apply-remote-minimal)
  (let [resp (query pena :municipalities-for-new-application)]
    (count (:municipalities resp)) => (partial <= 4)))
