(ns lupapalvelu.organizations-integrations-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.itest-util :refer [local-fixture-factory]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization-integrations :as integrations]
            [sade.core :as sade]))

(def org-id (str (random-uuid)))
(def org {:id org-id})
(def now (sade/now))

(use-fixtures :each
  (local-fixture-factory :db-name "test_organizations-integrations-test")
  (fn [test-fn]
    (mongo/insert :organizations org)
    (test-fn)))

(defn- get-org
  []
  (mongo/by-id :organizations org-id))

(deftest roundtrip-test
  (testing "Initially has no integrations"
    (is (empty? (integrations/find-by-type (get-org) :foo))))

  (let [config {:integration-type :foo
                :targets          #{"A" "B"}
                :enabled?         false
                :options          {:foo "bar"
                                   :kek 1}}]

    (integrations/insert!
      {:config          config
       :organization-id org-id
       :now             now})

    (testing "inserted integration is returned"
      (let [results (integrations/find-by-type (get-org) :foo)]
        (is (= 1 (count results)))
        (let [integration (first results)]
          (is (= now (:created-at integration)))
          (is (string? (:id integration)))
          (testing "config survives round trip"
            (is (= config
                   (dissoc integration :id :created-at))))

          (testing "targets are reported"
            (is (= #{"A" "B"}
                   (integrations/current-targets-by-type (get-org) :foo))))

          (testing "partial update"
            (integrations/update!
              {:config          (assoc integration :options {:kek 2})
               :organization-id org-id
               :now             now})

            (testing "updated integration is returned"
              (let [results (integrations/find-by-type (get-org) :foo)]
                (is (= 1 (count results)))
                (let [updated-integration (first results)]
                  (is (= now (:created-at updated-integration)))
                  (is (= (:id integration)
                         (:id updated-integration)))
                  (testing "only a single key was altered"
                    (is (= (assoc config :options {:foo "bar"
                                                   :kek 2})
                           (dissoc updated-integration :id :created-at)))))))))))

    (testing "insert more integrations"
      (integrations/insert!
        {:config          (assoc config :targets #{"B" "C"})
         :organization-id org-id
         :now             now})
      (integrations/insert!
        {:config          (assoc config :integration-type :bar :targets #{"X" "Y"})
         :organization-id org-id
         :now             now})

      (testing "union of both target sets are reported for type foo"
        (is (= #{"A" "B" "C"}
               (integrations/current-targets-by-type (get-org) :foo)))))))
