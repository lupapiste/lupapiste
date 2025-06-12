(ns lupapalvelu.conversion.section-schemas-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.section-schemas :as sec-sc]))

(def name-string? #'sec-sc/name-string?)

(deftest name-string?-test
  (testing "good"
    (is (every? name-string?
                ["a b" "älä-mölö" "1234567890 .,_=()[]{}"])))
  (testing "bad"
    (is (not-any? name-string?
                  [nil "" " " " hello " "ABC" "Älämölö" "1 X 2"]))))

(def safe-keyword? #'sec-sc/safe-keyword?)

(deftest safe-keyword?-test
  (testing "good"
    (is (every? safe-keyword? [:a :- :12345 :HeLlo])))
  (testing "bad"
    (is (not-any? safe-keyword?
                  [nil "" "hello" (keyword " ") :älämölö :mo$ey]))))
