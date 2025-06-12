(ns lupapalvelu.asiointi.notifications.registration-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.asiointi.notifications.registration :as registration]
            [matcher-combinators.test :refer [match?]])
  (:import [org.bson.types ObjectId]))

(set! *warn-on-reflection* true)

(defn object-id? [v] (instance? ObjectId v))

(deftest registration-test
  (binding [registration/*do-insert* (constantly nil)]
    (testing "registration validation"
      (let [ctx {:url-host-whitelist     #{"localhost"}
                 :url-protocol-whitelist #{"https"}}]
        (testing "create"
          (let [registration {:created         1767261600000
                              :organization-id "753-R"
                              :user            {:username "admin"}
                              :ignored         [:application-state-change]
                              :target          {:url "https://localhost:8080/api"}}]
            (testing "happy path"
              (is (match? {:ok           true
                           :registration {:id              object-id?
                                          :created-at      #inst "2026-01-01T10:00:00.000-00:00"
                                          :created-by      "admin"
                                          :organization-id "753-R"
                                          :ignored         #{:application-state-change}
                                          :target          {:url "https://localhost:8080/api"}}}
                          (registration/create! registration ctx))))
            (testing "checker catches obvious problems"
              (doseq [k [:created :organization-id :user :target]]
                (is (= {:ok   false
                        :text "error.invalid-registration"}
                       (-> registration
                           (dissoc k)
                           (registration/create! ctx))))))
            (testing "URL checking"
              (is (:ok (registration/create! registration ctx)))
              (testing "suspicious URLs"
                (testing "illegal host"
                  (let [url "https://example.com"]
                    (is (= {:ok   false
                            :text "error.forbidden-host"
                            :url  url}
                           (-> registration
                               (assoc-in [:target :url] url)
                               (registration/create! ctx))))))
                (testing "illegal protocol"
                  (let [url "http://localhost"]
                    (is (= {:ok   false
                            :text "error.forbidden-protocol"
                            :url  url}
                           (-> registration
                               (assoc-in [:target :url] url)
                               (registration/create! ctx))))))
                (testing "invalid URI"
                  (doseq [url ["asdasd" "123"]]
                    (is (= {:ok   false
                            :text "error.invalid-uri"
                            :url  url}
                           (-> registration
                               (assoc-in [:target :url] url)
                               (registration/create! ctx))))))
                (testing "invalid URL"
                  (let [url "sftp://localhost"]
                    (is (= {:ok   false
                            :text "error.invalid-url"
                            :url  url}
                           (-> registration
                               (assoc-in [:target :url] url)
                               (registration/create! ctx))))))
                (testing "empty URL"
                  (let [url ""]
                    (is (= {:ok   false
                            :text "error.invalid-registration"}
                           (-> registration
                               (assoc-in [:target :url] url)
                               (registration/create! ctx))))))))))
        (testing "update"
          (let [registration-id (ObjectId.)
                organization-id "753-R"
                changes         {:target {:url "https://localhost:8080/api/2"}}
                registration    {:id              registration-id
                                 :created-at      #inst "2026-01-01T10:00:00.000-00:00"
                                 :organization-id "753-R"
                                 :created-by      "admin"
                                 :ignored         [:application-state-change]
                                 :target          {:url "https://localhost:8080/api"}}]
            (testing "not found"
              (binding [registration/*do-update* (constantly nil)]
                (is (= {:ok   false
                        :text "error.not-found"}
                       (registration/update! registration-id
                                             organization-id
                                             changes
                                             ctx)))))
            (testing "found"
              (binding [registration/*do-update* (constantly registration)]
                (testing "URL checking"
                  (is (= {:ok           true
                          :registration (-> registration
                                            ;; the API should also change, but we mock the return value
                                            (update :ignored set))}
                         (registration/update! registration-id
                                               organization-id
                                               changes
                                               ctx)))
                  (testing "suspicious URLs"
                    (testing "illegal host"
                      (let [url "https://example.com"]
                        (is (= {:ok   false
                                :text "error.forbidden-host"
                                :url  url}
                               (registration/update! registration-id
                                                     organization-id
                                                     {:target {:url url}}
                                                     ctx)))))
                    (testing "illegal protocol"
                      (let [url "http://localhost"]
                        (is (= {:ok   false
                                :text "error.forbidden-protocol"
                                :url  url}
                               (registration/update! registration-id
                                                     organization-id
                                                     {:target {:url url}}
                                                     ctx)))))
                    (testing "invalid URI"
                      (doseq [url ["asdasd"
                                   "123"]]
                        (is (= {:ok   false
                                :text "error.invalid-uri"
                                :url  url}
                               (registration/update! registration-id
                                                     organization-id
                                                     {:target {:url url}}
                                                     ctx)))))
                    (testing "invalid URL"
                      (let [url "sftp://localhost"]
                        (is (= {:ok   false
                                :text "error.invalid-url"
                                :url  url}
                               (registration/update! registration-id
                                                     organization-id
                                                     {:target {:url url}}
                                                     ctx)))))
                    (testing "empty URL"
                      (let [url ""]
                        (is (= {:ok   false
                                :text "error.invalid-registration"}
                               (registration/update! registration-id
                                                     organization-id
                                                     {:target {:url url}}
                                                     ctx)))))))))))))))
