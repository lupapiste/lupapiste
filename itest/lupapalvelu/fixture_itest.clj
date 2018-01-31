(ns lupapalvelu.fixture-itest
  (:require [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.smoketest.lupamonster :as monster]))

(def db-name (str "test_fixture_" (now)))

(mongo/connect!)

(fact "apply minimal fixture"
  (mongo/with-db db-name
    (fixture/apply-fixture "minimal")) => [])

(fact "minimal fixture smokes"
  (mongo/with-db db-name
    (monster/mongochecks)) => ok?)

(fact "apply company-application fixture"
  (mongo/with-db db-name
    (fixture/apply-fixture "company-application")) => [])

(fact "company-application fixture smokes"
  (mongo/with-db db-name
    (monster/mongochecks)) => ok?)
