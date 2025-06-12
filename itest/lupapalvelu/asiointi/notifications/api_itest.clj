(ns lupapalvelu.asiointi.notifications.api-itest
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.asiointi.test-utils :as asiointi-test]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :as itest]
            [lupapalvelu.mongo :as mongo]
            [matcher-combinators.test :refer [match?]]
            [sade.util :as util]))

(use-fixtures :each (itest/local-fixture-factory (-> {:db-name itest/test-db-name}
                                                     test-system/with-remote-components)))

(defn- find-all!
  [{:keys [username password]}]
  (asiointi-test/submit! {:method   :get
                          :url      "/asiointi/api/753-R/notification-registrations"
                          :username username
                          :password password}))

(defn- find-one!
  [{:keys [username password id]}]
  (asiointi-test/submit! {:method   :get
                          :url      (str "/asiointi/api/753-R/notification-registrations/" id)
                          :username username
                          :password password}))

(defn- create!
  [{:keys [username password body]}]
  (asiointi-test/submit! {:method   :post
                          :url      "/asiointi/api/753-R/notification-registrations"
                          :username username
                          :password password
                          :body     body}))

(defn- modify!
  [{:keys [username password id body]}]
  (asiointi-test/submit! {:method   :post
                          :url      (str "/asiointi/api/753-R/notification-registrations/" id)
                          :username username
                          :password password
                          :body     body}))

(defn- remove!
  [{:keys [username password id]}]
  (asiointi-test/submit! {:method   :delete
                          :url      (str "/asiointi/api/753-R/notification-registrations/" id)
                          :username username
                          :password password}))

(deftest notification-api-test
  (let [username    (str (random-uuid))
        create-resp (itest/command itest/admin
                                   :create-asiointi-api-user
                                   {:username username
                                    :orgAuthz {:753-R ["api-reader"]}})
        password    (-> create-resp :user :password)
        request     (util/mmap username password)]
    (is (:ok create-resp))

    (testing "initially no registrations"
      (let [{:keys [status
                    body]} (find-all! request)]
        (is (= 200 status))
        (is (= {:registrations []}
               body)))
      (is (= 404 (:status (find-one! (assoc request :id (mongo/create-id)))))))

    (testing "create a new notification registration"
      (let [target                        {:url "https://localhost"}
            {:keys [status body]}         (create! (assoc request :body {:registration {:target target}}))
            {:keys [id] :as registration} body]
        (is (= 200 status))
        (is (match? {:id         string?
                     :created-at string?
                     :target     target}
                    registration))

        (testing "incorrect create calls"
          (is (= 400 (:status (create! (assoc request :body {})))))
          (is (= 400 (:status (create! (assoc request :body {:registration "kissa"})))))
          (is (= 400 (:status (create! (assoc request :body {:registration {:target {}}})))))
          (is (= 400 (:status (create! (assoc request :body {:registration {:target {:url "https://localhost/2"
                                                                                     :foo :bar}}}))))))

        (testing "verify created notification is returned"
          (testing "from all"
            (let [{:keys [status body]} (find-all! request)]
              (is (= 200 status))
              (is (= {:registrations [registration]}
                     body))))
          (testing "individually"
            (let [{:keys [status body]} (find-one! (assoc request :id id))]
              (is (= 200 status))
              (is (= registration
                     body)))))

        (testing "modify a registration"
          (let [{:keys [status body]} (modify! (assoc request
                                                      :id id
                                                      :body {:registration {:target {:url "https://localhost/2"}}}))]
            (is (= 200 status))
            (is (= (assoc registration :target {:url "https://localhost/2"})
                   body))))

        (testing "remove a registration"
          (let [{:keys [status body]} (remove! (assoc request :id id))]
            (is (= 204 status))
            (is (nil? body)))

          (testing "it's gone"
            (let [{:keys [status]} (find-one! (assoc request :id id))]
              (is (= 404 status)))))))))

(deftest notification-api-error-cases-test
  (let [username    (str (random-uuid))
        create-resp (itest/command itest/admin
                                   :create-asiointi-api-user
                                   {:username username
                                    :orgAuthz {:753-R ["api-reader"]}})
        password    (-> create-resp :user :password)
        request     (util/mmap username password)]
    (is (:ok create-resp))

    (testing "forbidden host"
      (let [target                {:url "https://example.com"}
            {:keys [status body]} (create! (assoc request :body {:registration {:target target}}))]
        (is (= 400 status))
        (is (= {:errors [{:pointer "/registration/target/url"
                          :detail  "The host name within the target URL is not allowed."}]
                :status 400
                :title  "Bad request"}
               body))))

    (testing "invalid URL"
      (let [target                {:url "foobar"}
            {:keys [status body]} (create! (assoc request :body {:registration {:target target}}))]
        (is (= 400 status))
        (is (= {:errors [{:pointer "/registration/target/url"
                          :detail  "The target URL is not a valid URI."}]
                :status 400
                :title  "Bad request"}
               body))))

    (testing "invalid URL"
      (let [target                {:url "asdf://localhost"}
            {:keys [status body]} (create! (assoc request :body {:registration {:target target}}))]
        (is (= 400 status))
        (is (= {:errors [{:pointer "/registration/target/url"
                          :detail  "The target URL is not a valid URL."}]
                :status 400
                :title  "Bad request"}
               body))))

    (testing "create a new notification registration"
      (let [target                {:url "https://localhost"}
            {:keys [status body]} (create! (assoc request :body {:registration {:target target}}))
            registration          body]
        (is (= 200 status))
        (is (match? {:id         string?
                     :created-at string?
                     :target     target}
                    registration))

        (testing "cannot re-register for the same org + url"
          (let [{:keys [status body]} (create! (assoc request :body {:registration {:target target}}))]
            (is (= 400 status))
            (is (= {:errors [{:pointer "/registration/target/url",
                              :detail  "Duplicate registration on the given target URL."}]
                    :status 400
                    :title  "Bad request"}
                   body))))))))
