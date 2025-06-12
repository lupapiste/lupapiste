(ns lupapalvelu.asiointi.api-itest
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [jsonista.core :as jsonista]
            [lupapalvelu.asiointi.test-utils :as asiointi-test]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :as itest]
            [lupapalvelu.json :as json]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]))

(set! *warn-on-reflection* true)

(use-fixtures :each (itest/local-fixture-factory
                      (test-system/with-remote-components {})))

(defn- get-openapi-json!
  [api-type]
  (let [url                   (format "%s/%s"
                                      (itest/server-address)
                                      (case api-type
                                        :asiointiapi   "asiointi/openapi.json"
                                        :notifications "asiointi-notifications/openapi.json"))
        {:keys [status body]} (itest/http-get url {:throw-exceptions false
                                                   :content-type     :json})]
    (is (= 200 status))
    (json/decode body)))

(def pretty-mapper (jsonista/object-mapper {:pretty        true
                                            :order-by-keys true}))

(defn write-openapi-json!
  "When the `openapi.json` becomes stale and you want to update it"
  [api-type]
  (let [current (get-openapi-json! api-type)
        js      (jsonista/write-value-as-string current pretty-mapper)
        f       (io/file (case api-type
                           :asiointiapi   "dev-resources/asiointiapi/openapi.json"
                           :notifications "dev-resources/asiointiapi/notifications-openapi.json"))]
    (spit f js)))

(comment
  ;; NOTE: If the `asiointi-api-validate-openapi-json-test` test fails,
  ;; do NOT blindly update the `openapi.json`! Make sure you understand _why_
  ;; the API changed and whether or not if affects the existing API consumers.
  (itest/with-test-system (-> {}
                              test-system/with-remote-components)
    (write-openapi-json! :asiointiapi)
    (write-openapi-json! :notifications)))

(deftest asiointi-api-validate-openapi-json-test
  (testing "AsiointiAPI `openapi.json` is up-to-date"
    (let [expected (-> (io/resource "asiointiapi/openapi.json")
                       slurp
                       json/decode)
          actual   (get-openapi-json! :asiointiapi)]
      (is (match? (m/nested-equals expected)
                  actual))))
  (testing "AsiointiAPI notifications `openapi.json` is up-to-date"
    (let [expected (-> (io/resource "asiointiapi/notifications-openapi.json")
                       slurp
                       json/decode)
          actual   (get-openapi-json! :notifications)]
      (is (match? (m/nested-equals expected)
                  actual)))))

(deftest asiointi-api-authorization-test
  (testing "Can read openapi.json without authentication"
    (let [{:keys [status
                  body]} (asiointi-test/submit! {:method :get
                                                 :url    "/asiointi/openapi.json"})]
      (is (= 200 status))
      (is (= "asiointi-api"
             (-> body :info :title)))))
  (testing "Cannot read org without credentials"
    (let [{:keys [status
                  body]} (asiointi-test/submit! {:method :get
                                                 :url    "/asiointi/api/753-R/hello"})]
      (is (= 401 status))
      (is (= "Unauthorized"
             body))))
  (testing "Cannot read org with wrong credentials"
    (let [{:keys [status
                  body]} (asiointi-test/submit! {:method   :get
                                                 :url      "/asiointi/api/753-R/hello"
                                                 :username "hackerman"
                                                 :password "hunter2"})]
      (is (= 401 status))
      (is (= "Unauthorized"
             body))))
  (testing "Cannot read org with non-integration credentials"
    (testing "authority admin"
      (let [{:keys [status
                    body]} (asiointi-test/submit! {:method   :get
                                                   :url      "/asiointi/api/753-R/hello"
                                                   :username "sipoo"
                                                   :password "sipoo"})]
        (is (= 401 status))
        (is (= "Unauthorized"
               body))))
    (testing "authority"
      (let [{:keys [status
                    body]} (asiointi-test/submit! {:method   :get
                                                   :url      "/asiointi/api/753-R/hello"
                                                   :username "sonja"
                                                   :password "sonja"})]
        (is (= 401 status))
        (is (= "Unauthorized"
               body)))))
  (let [username "sipoo_asiointiapi"
        password "sipoo"]
    (testing "User can access organization"
      (testing "with writer rights"
        (let [{:keys [status
                      body]} (asiointi-test/submit! {:method   :get
                                                     :url      "/asiointi/api/753-R/hello"
                                                     :username username
                                                     :password password})]
          (is (= 200 status))
          (is (= {:content "Hello from 753-R"}
                 body))))
      (testing "with reader rights"
        (let [{:keys [status
                      body]} (asiointi-test/submit! {:method   :get
                                                     :url      "/asiointi/api/753-YA/hello"
                                                     :username username
                                                     :password password})]
          (is (= 200 status))
          (is (= {:content "Hello from 753-YA"}
                 body))))
      (testing "but not with no rights"
        (let [{:keys [status
                      body]} (asiointi-test/submit! {:method   :get
                                                     :url      "/asiointi/api/091-R/hello"
                                                     :username username
                                                     :password password})]
          (is (= 403 status))
          (is (= "Forbidden"
                 body)))))))

(deftest new-user-test
  ;; simple happy path testing
  (let [username    (str (random-uuid))
        create-resp (itest/command itest/admin
                                   :create-asiointi-api-user
                                   {:username username
                                    :orgAuthz {:753-R ["api-reader"]}})
        password    (-> create-resp :user :password)]
    (is (:ok create-resp))
    (is (string? password))

    (testing "with reader rights"
      (let [{:keys [status
                    body]} (asiointi-test/submit! {:method   :get
                                                   :url      "/asiointi/api/753-R/hello"
                                                   :username username
                                                   :password password})]
        (is (= 200 status))
        (is (= {:content "Hello from 753-R"}
               body))))
    (testing "cannot access without rights"
      (let [{:keys [status
                    body]} (asiointi-test/submit! {:method   :get
                                                   :url      "/asiointi/api/091-R/hello"
                                                   :username username
                                                   :password password})]
        (is (= 403 status))
        (is (= "Forbidden"
               body))))

    (let [modify-resp (itest/command itest/admin
                                     :modify-asiointi-api-user
                                     {:username username
                                      :orgAuthz {:091-R ["api-writer"]}})]
      (is (:ok modify-resp))

      (testing "with writer rights"
        (let [{:keys [status
                      body]} (asiointi-test/submit! {:method   :get
                                                     :url      "/asiointi/api/091-R/hello"
                                                     :username username
                                                     :password password})]
          (is (= 200 status))
          (is (= {:content "Hello from 091-R"}
                 body)))))
    (let [{new-pw :password :as reset-pw-resp} (itest/command itest/admin
                                                              :reset-asiointi-api-user-password
                                                              {:username username})]
      (is (:ok reset-pw-resp))
      (is (string? new-pw))
      (is (not= password new-pw))

      (testing "with old password"
        (let [{:keys [status
                      body]} (asiointi-test/submit! {:method   :get
                                                     :url      "/asiointi/api/091-R/hello"
                                                     :username username
                                                     :password password})]
          (is (= 401 status))
          (is (= "Unauthorized"
                 body))))

      (testing "with new password"
        (let [{:keys [status
                      body]} (asiointi-test/submit! {:method   :get
                                                     :url      "/asiointi/api/091-R/hello"
                                                     :username username
                                                     :password new-pw})]
          (is (= 200 status))
          (is (= {:content "Hello from 091-R"}
                 body))))

      (testing "access after being disabled"
        (let [disable-resp (itest/command itest/admin
                                          :set-user-enabled
                                          {:email   (str username "@example.com")
                                           :enabled false})]
          (is (:ok disable-resp))
          (testing "with new password"
            (let [{:keys [status
                          body]} (asiointi-test/submit! {:method   :get
                                                         :url      "/asiointi/api/091-R/hello"
                                                         :username username
                                                         :password new-pw})]
              (is (= 401 status))
              (is (= "Unauthorized"
                     body)))))))))

(deftest asiointi-api-authorization-failure-test
  (testing "create-asiointi-api-user failures"
    (testing "incorrect role given"
      (is (=  {:ok   false
               :text "error.forbidden-orgAuthz-roles"}
              (itest/command itest/admin
                             :create-asiointi-api-user
                             {:username "foo"
                              :orgAuthz {:753-R ["api-reader" "authority"]}}))))
    (testing "existing user"
      (is (=  {:ok   false
               :text "email-in-use"}
              (itest/command itest/admin
                             :create-asiointi-api-user
                             {:username "admin"
                              :orgAuthz {:753-R ["api-reader"]}})))))
  (testing "modify-asiointi-api-user failures"
    (testing "incorrect role given"
      (is (=  {:ok   false
               :text "error.forbidden-orgAuthz-roles"}
              (itest/command itest/admin
                             :modify-asiointi-api-user
                             {:username "foo"
                              :orgAuthz {:753-R ["api-reader" "authority"]}}))))
    (testing "non-asiointiapi user"
      (is (=  {:ok   false
               :text "not-found"}
              (itest/command itest/admin
                             :modify-asiointi-api-user
                             {:username "admin"
                              :orgAuthz {:753-R ["api-reader"]}})))))
  (testing "reset-asiointi-api-user-password failures"
    (testing "non-asiointiapi user"
      (is (=  {:ok   false
               :text "not-found"}
              (itest/command itest/admin
                             :reset-asiointi-api-user-password
                             {:username "admin"}))))))
