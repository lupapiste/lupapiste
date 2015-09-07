(ns lupapalvelu.mongo-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [monger.collection :as mc]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer :all]))

(def test-db-name (str "test_" (now)))

(mongo/connect!)

(fact get-db
  (mongo/with-db test-db-name (.getName (mongo/get-db))) => test-db-name)

