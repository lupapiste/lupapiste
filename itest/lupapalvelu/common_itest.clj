(ns lupapalvelu.common-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]]))

(fact "minimal fixture has at least 4 municipalities with organization"
  (apply-remote-minimal)
  (let [resp (query pena :municipalities-with-organization)]
    (count (:municipalities resp)) => (partial <= 4)))
