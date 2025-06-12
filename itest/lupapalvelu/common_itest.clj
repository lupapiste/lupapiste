(ns lupapalvelu.common-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet  :refer :all]))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (fact "minimal fixture has at least 4 municipalities with organization"
    (let [resp (query pena :municipalities-with-organization)]
      (count (:municipalities resp)) => (partial <= 4))))
