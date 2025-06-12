(ns lupapalvelu.fixture-itest
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.smoketest.lupamonster :as monster]
            [midje.sweet :refer :all]))

(with-test-system {}
  (let [system (test-system/get-common-components)]
    (facts "fixtures smoke"
      (doseq [fixture-name (fixture/fixtures)]
        (facts {:midje/description (name fixture-name)}
          (fact "apply fixture" (fixture/apply-fixture fixture-name system) => true)
          (fact "smoketest fixture" (monster/mongochecks) => ok?)))
      (fact "Unknown fixture"
        (fixture/apply-fixture "mysteryfixture" system) => false))))
