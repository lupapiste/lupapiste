(ns lupapalvelu.system.system-itest
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.itest-util :refer [with-test-system]]
            [lupapalvelu.itest-system :as test-system]))

(set! *warn-on-reflection* true)

(deftest no-duplicate-components

  (testing "system starts up"
    (with-test-system {}
      (is (= 1 1))))

  (testing "duplicate laundry clients"
    (let [ex  (is (thrown?
                    Exception
                    (with-test-system (-> {}
                                          test-system/with-real-laundry
                                          test-system/with-mock-laundry)
                      (is (= 1 1)))))
          ex2 (ex-cause ex)]
      (is (= "Error on key :lupapalvelu.system.util-components/common when building system"
             (ex-message ex)))
      (is (= "Found multiple components, expected only one!"
             (ex-message ex2)))
      (is (= :laundry (-> ex2 ex-data :k)))))

  (testing "unknown keys"
    (let [ex (is (thrown?
                   Exception
                   (with-test-system (-> {}
                                         (test-system/with-extra-system-keys ::foobar))
                     (is (= 1 1)))))]
      (is (= "Attempting to start unknown components"
             (ex-message ex)))
      (is (= #{::foobar} (-> ex ex-data :unknown))))))
