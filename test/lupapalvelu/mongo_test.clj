(ns lupapalvelu.mongo-test
  (:use clojure.test
        midje.sweet
        lupapalvelu.mongo)
  (:require [monger.collection :as mc]))

(def valid-id "502770568de2282ae6fbb0be")
(def invalid-id "123")

(deftest a-test
  (testing "invalid id returns nil"
           (is (= nil (string-to-objectid invalid-id))))
  (testing "string id can be converted to objectid and back"
           (is (= valid-id (-> valid-id string-to-objectid objectid-to-string))))
  (testing "make-objectid returns string"
           (is (string? (make-objectid)))))

(facts "Facts about with-objectid"
  (against-background
    (string-to-objectid "foo") => ...id...)
  (fact (with-objectid nil) => nil)
  (fact (with-objectid {:data "data"}) => {:data "data"})
  (fact (with-objectid {:id "foo" :data "data"}) => {:_id ...id... :data "data"}))

(facts "Facts about with-id"
  (against-background
    (objectid-to-string "foo") => ...id...)
  (fact (with-id nil) => nil)
  (fact (with-id {:data "data"}) => {:data "data"})
  (fact (with-id {:_id "foo" :data "data"}) => {:id ...id... :data "data"}))

(facts "Facts about insert"
  (fact (insert "c" {:id "foo" :data "data"}) => nil
        (provided
          (string-to-objectid "foo") => ...id...
          (mc/insert "c" {:_id ...id... :data "data"}) => nil))
  (fact (insert "c" {:data "data"}) => nil
        (provided
          (mc/insert "c" {:data "data"}) => nil)))
