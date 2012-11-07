(ns lupapalvelu.mongo-test
  (:use clojure.test
        midje.sweet
        lupapalvelu.mongo)
  (:require [monger.collection :as mc]))

(def valid-id "502770568de2282ae6fbb0be")
(def invalid-id "123")

(facts "Facts about create-id"
  (fact (create-id) => string?))

(facts "Facts about with-_id"
  (fact (with-_id nil) => nil)
  (fact (with-_id {:data "data"}) => {:data "data"})
  (fact (with-_id {:id "foo" :data "data"}) => {:_id "foo" :data "data"}))

(facts "Facts about with-id"
  (fact (with-id nil) => nil)
  (fact (with-id {:data "data"}) => {:data "data"})
  (fact (with-id {:_id "foo" :data "data"}) => {:id "foo" :data "data"}))

(facts "Facts about insert"
  (fact (insert "c" {:id "foo" :data "data"}) => nil
        (provided
          (mc/insert "c" {:_id "foo" :data "data"}) => nil))
  (fact (insert "c" {:data "data"}) => nil
        (provided
          (mc/insert "c" {:data "data"}) => nil)))
