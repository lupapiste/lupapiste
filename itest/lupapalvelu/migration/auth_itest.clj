(ns lupapalvelu.migration.auth-itest
  (:require [clojure.test :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.migration.auth :as mig-auth]
            [lupapalvelu.mongo :as mongo]))

(use-fixtures :once (local-fixture-factory :db-name "test_auth-migration-test"))

(deftest transfer-auths-test
  (let [{app-id :id :as app} (create-and-submit-local-application pena :address "Transfer Town")
        get-auth             (fn [{:keys [auth]}]
                               (-> auth first (select-keys [:id :username])))
        pena-auth            {:id pena-id :username "pena"}
        pena-email           "pena@example.com"]
    (is (= pena-auth (get-auth app)))
    (testing "No transfer"
      (let [{:keys [apply-when execute]} (mig-auth/transfer-auths "No such user"
                                                                  "bad@bad.example.net"
                                                                  pena-email)]
        (is (false? (apply-when)))
        (is (nil? (execute))))
      (let [{:keys [apply-when execute]} (mig-auth/transfer-auths "No such user"
                                                                  pena-email
                                                                  "bad@bad.example.net")]
        (is (false? (apply-when)))
        (is (nil? (execute))))
      (is (thrown? AssertionError (mig-auth/transfer-auths "Same user"
                                                           pena-email
                                                           pena-email)))
      (let [{:keys [apply-when execute]} (mig-auth/transfer-auths "No applications"
                                                                  "mikko@example.com"
                                                                  pena-email)]
        (is (false? (apply-when)))
        (is (zero? (execute)))))
    (testing "Successful transfer"
      (let [sven-email                   "sven@example.com"
            {:keys [apply-when execute]} (mig-auth/transfer-auths "Howdy"
                                                                  pena-email
                                                                  sven-email)
            app                          #(mongo/by-id :applications app-id)]
        (is (true? (apply-when)))
        (is (= 1 (execute)))
        (is (= {:id sven-id :username sven-email}
               (get-auth (app))))
        (is (= (format "Howdy Migration: Auth change pena (%s) -> sven@example.com (%s)."
                       pena-id sven-id)
               (-> (app) :_sheriff-notes last :note)))))))
