(ns lupapalvelu.ryhti.api-itest
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.itest-util :refer :all]))

(use-fixtures :once (local-fixture-factory :db-name "test_ryhti-api-itest"))

(deftest ryhti-api-configuration-test
  (testing "Should be able to set configuration"
    (let [config [{:municipality "186" :client-id "42" :client-secret "6*9"}]]
      (is  (= {:ok true :configuration []}
              (query admin :ryhti-api-configuration :organizationId "753-R")))
      (is (= {:ok true :configuration config}
             (command admin :set-ryhti-api-configuration
                      :organizationId "753-R" :ryhtiConfig config)))
      (is (= {:ok true :configuration config}
             (query admin :ryhti-api-configuration :organizationId "753-R"))))))
