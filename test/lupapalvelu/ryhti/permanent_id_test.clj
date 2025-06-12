(ns lupapalvelu.ryhti.permanent-id-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.organization :as org]
            [lupapalvelu.ryhti.permanent-id :as id]
            [schema.core :as sc]))

(def disabled-ctx {::id/fetch-ryhti-plt?     false
                   ::id/dev-mode?            false
                   ::id/generate-random-plt? false})
(def prod-ctx     {::id/fetch-ryhti-plt?     true
                   ::id/dev-mode?            false
                   ::id/generate-random-plt? false})
(def qa-ctx       {::id/fetch-ryhti-plt?     true
                   ::id/dev-mode?            false
                   ::id/generate-random-plt? true})
(def dev-ctx      {::id/fetch-ryhti-plt?     true
                   ::id/dev-mode?            true
                   ::id/generate-random-plt? true})

(deftest permanent-id-required-test
  (let [organization {:ryhti-api-configuration [{:client-id     "user"
                                                 :client-secret "hunter2"
                                                 :municipality  "753"}
                                                {:client-id     "user"
                                                 :client-secret "hunter2"
                                                 :municipality  "091"}]}
        application  {:permitType       "R"
                      :ryhti-data-2026  true
                      :municipality     "753"
                      :primaryOperation {:name "purkaminen"}}]
    (is (sc/validate [org/RyhtiApiConfiguration]
                     (:ryhti-api-configuration organization)))
    (testing "id required"
      (testing "control case through operation"
        (doseq [ctx [prod-ctx qa-ctx dev-ctx]]
          (is (id/permanent-id-required? ctx
                                         organization
                                         application)))
        (is (not (id/permanent-id-required? disabled-ctx
                                            organization
                                            application))))
      (testing "control case through permit type"
        (let [application (-> application
                              (assoc-in [:primaryOperation :name] "asuinrakennus"))]
          (doseq [ctx [prod-ctx qa-ctx dev-ctx]]
            (is (id/permanent-id-required? ctx
                                           organization
                                           application)))
          (is (not (id/permanent-id-required? disabled-ctx
                                              organization
                                              application))))))
    (testing "id not required"
      (testing "due to application"
        (testing "already has one"
          (doseq [ctx [disabled-ctx prod-ctx qa-ctx dev-ctx]]
            (is (not (id/permanent-id-required? ctx
                                                organization
                                                (-> application
                                                    (assoc-in [:ryhti :permanentPermitIdentifier] "123")))))))
        (testing "not following Ryhti 2026"
          (doseq [ctx [disabled-ctx prod-ctx qa-ctx dev-ctx]]
            (is (not (id/permanent-id-required? ctx
                                                organization
                                                (-> application
                                                    (assoc :ryhti-data-2026 false)))))))
        (testing "wrong permit type"
          (doseq [ctx [disabled-ctx prod-ctx qa-ctx dev-ctx]]
            (is (not (id/permanent-id-required? ctx
                                                organization
                                                (-> application
                                                    (assoc :permitType "YA")))))))
        (testing "municipality does not match config"
          (doseq [ctx [disabled-ctx prod-ctx]]
            (is (not (id/permanent-id-required? ctx
                                                organization
                                                (-> application
                                                    (assoc :municipality "092"))))))
          (testing "unless we are generating UUIDs"
            (doseq [ctx [qa-ctx dev-ctx]]
              (is (id/permanent-id-required? ctx
                                             organization
                                             (-> application
                                                 (assoc :municipality "092"))))))))
      (testing "due to org"
        (testing "no configs"
          (doseq [ctx [disabled-ctx prod-ctx]]
            (is (not (id/permanent-id-required? ctx
                                                (-> organization
                                                    (assoc :ryhti-api-configuration []))
                                                application))))
          (testing "unless we are generating UUIDs"
            (doseq [ctx [qa-ctx dev-ctx]]
              (is (id/permanent-id-required? ctx
                                             organization
                                             (-> application
                                                 (assoc :municipality "092")))))))))))

(deftest id-strategy-test
  (let [organization {:ryhti-api-configuration [{:client-id     "user"
                                                 :client-secret "hunter2"
                                                 :municipality  "753"}
                                                {:client-id     "user"
                                                 :client-secret "hunter2"
                                                 :municipality  "091"}]}]
    (is (sc/validate [org/RyhtiApiConfiguration]
                     (:ryhti-api-configuration organization)))
    (testing "dev-mode always generates"
      (is (= ::id/random (id/id-strategy dev-ctx nil nil)))
      (is (= ::id/random (id/id-strategy dev-ctx organization nil)))
      (is (= ::id/random (id/id-strategy dev-ctx organization "753")))
      (is (= ::id/random (id/id-strategy dev-ctx organization "123"))))
    (testing "qa generates if no config found"
      (is (= ::id/random (id/id-strategy qa-ctx nil nil)))
      (is (= ::id/random (id/id-strategy qa-ctx organization nil)))
      (is (= ::id/fetch (id/id-strategy qa-ctx organization "753")))
      (is (= ::id/fetch (id/id-strategy qa-ctx organization "091")))
      (is (= ::id/random (id/id-strategy qa-ctx organization "123"))))
    (testing "prod always fetches if possible"
      (is (nil? (id/id-strategy prod-ctx nil nil)))
      (is (nil? (id/id-strategy prod-ctx organization nil)))
      (is (= ::id/fetch (id/id-strategy prod-ctx organization "753")))
      (is (= ::id/fetch (id/id-strategy prod-ctx organization "091")))
      (is (nil? (id/id-strategy prod-ctx organization "123"))))
    (testing "disabled always does nothing"
      (is (nil? (id/id-strategy disabled-ctx nil nil)))
      (is (nil? (id/id-strategy disabled-ctx organization nil)))
      (is (nil? (id/id-strategy disabled-ctx organization "753")))
      (is (nil? (id/id-strategy disabled-ctx organization "091")))
      (is (nil? (id/id-strategy disabled-ctx organization "123"))))))
