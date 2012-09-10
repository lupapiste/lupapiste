(ns lupapalvelu.mongo-test
  (:use clojure.test
        lupapalvelu.mongo))

(def valid-id "502770568de2282ae6fbb0be")
(def invalid-id "123")

(deftest a-test
  (testing "invalid id returns nil"
           (is (= nil (string-to-objectid invalid-id))))
  (testing "string id can be converted to objectid and back"
           (is (= valid-id (-> valid-id string-to-objectid objectid-to-string))))
  (testing "make-objectid returns string"
           (is (string? (make-objectid)))))