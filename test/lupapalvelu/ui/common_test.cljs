(ns lupapalvelu.ui.common-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.ui.common :as common]))

(deftest make-url-test
  (is (= "http://localhost/foo"
         (common/make-url "http://localhost/foo" {})))
  (is (= "http://localhost/foo?bar=1"
         (common/make-url "http://localhost/foo" {:bar 1})))
  (is (= "http://localhost/foo?bar=1&baz=lol"
         (common/make-url "http://localhost/foo" {:bar 1
                                                  :baz "lol"})))
  (is (= "http://localhost/foo?bar=1&quuz=!%23%C2%A4%25%26%2F()"
         (common/make-url "http://localhost/foo" {:bar 1
                                                  :quuz "!#¤%&/()"})))
  (is (= "http://localhost/foo?coll=1%2C2%2C3"
         (common/make-url "http://localhost/foo" {:coll [1 2 3]})
         (common/make-url "http://localhost/foo" {:coll ["1" "2" "3"]})
         (common/make-url "http://localhost/foo" {:coll (sorted-set "1" "2" "3")})
         (common/make-url "http://localhost/foo" {:coll (list "1" "2" "3")}))))

(def buildings [{:description "Rakennus 1"
                 :index "1"
                 :nationalId "1234"
                 :propertyId "75342300130015"
                 :operationId "6908a059a3eac02fbaf52866"}
                {:description "Rakennus 2"
                 :index "2"
                 :nationalId "4567"
                 :propertyId "75342300130015"
                 :operationId "6908a9c2a3eac02fbaf52875"}
                {:description "Rakennus 3"
                 :index "3"
                 :nationalId "987"
                 :propertyId "75342300130015"
                 :operationId "692423bdbb1f107279b082d2"}])

(deftest find-matching-building-test
  (testing "Building list is empty"
    (is (= nil
           (common/find-matching-building [] "123" "10"))))
  (testing "Building national ID and index are nil"
    (is (= nil
           (common/find-matching-building buildings nil nil))))
  (testing "National ID and index both match"
    (is (= {:description "Rakennus 1"
            :index "1"
            :nationalId "1234"
            :propertyId "75342300130015"
            :operationId "6908a059a3eac02fbaf52866"}
           (common/find-matching-building buildings "1234" "1"))))
  (testing "National ID matches"
    (is (= {:description "Rakennus 2"
            :index "2"
            :nationalId "4567"
            :propertyId "75342300130015"
            :operationId "6908a9c2a3eac02fbaf52875"}
           (common/find-matching-building buildings "4567" nil))))
  (testing "Index matches"
    (is (= {:description "Rakennus 2"
            :index "2"
            :nationalId "4567"
            :propertyId "75342300130015"
            :operationId "6908a9c2a3eac02fbaf52875"}
           (common/find-matching-building buildings nil "2")))))

(deftest percentage-over-limit-test
  (testing "returns nil when value is not over limit"
    (is (nil? (common/->percentage-over-limit 10 20)))
    (is (nil? (common/->percentage-over-limit 20 20))))

  (testing "returns nil when limit is zero"
    (is (nil? (common/->percentage-over-limit 10 0))))

  (testing "returns nil when value or limit is nil"
    (is (nil? (common/->percentage-over-limit nil 10)))
    (is (nil? (common/->percentage-over-limit 10 nil))))

  (testing "calculates percentage over limit correctly"
    (is (= "0.13" (common/->percentage-over-limit 801 800))
        "801 is 0.125% over 800, rounded to 0.13")
    (is (= "25.00" (common/->percentage-over-limit 25 20))
        "25 is 25% over 20")
    (is (= "100.00" (common/->percentage-over-limit 32 16))
        "32 is 100% over 16")))
