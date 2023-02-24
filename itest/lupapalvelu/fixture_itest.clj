(ns lupapalvelu.fixture-itest
  (:require [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.smoketest.lupamonster :as monster]
            [mount.core :as mount]))

(def db-name (str "test_fixture_" (now)))

(mount/start #'mongo/connection)

(facts "fixtures smoke"
  (mongo/with-db db-name
    (doseq [fixture-name (keys @fixture/fixtures)]
      (facts {:midje/description fixture-name}
        (fact "apply fixture" (fixture/apply-fixture (name fixture-name)) => [])
        (fact "smoketest fixture" (monster/mongochecks) => ok?)))))
