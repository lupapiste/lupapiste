(ns lupapalvelu.common-itest
  (:require [midje.sweet  :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(fact "minimal fixture has at least 4 municipalities with organization"
  (apply-remote-minimal)
  (let [resp (query pena :municipalities-with-organization)]
    (count (:municipalities resp)) => (partial <= 4)))
