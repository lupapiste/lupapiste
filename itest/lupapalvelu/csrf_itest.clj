(ns lupapalvelu.csrf-itest
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer [->cookie-store decode-response get-anti-csrf http-get http-post http200?
                                            invalid-csrf-token? mikko ok? raw-query server-address with-anti-csrf
                                            with-test-system]]))

(use-fixtures :once (fn [f]
                      (with-test-system (-> {:fixture "minimal"}
                                            test-system/with-remote-components)
                        (with-anti-csrf
                          (f)))))

(deftest valid-apikey-bypasses-csrf-check-test
  (let [resp (raw-query mikko :allowed-actions)]
    (is (http200? resp))
    (is (ok? (:body resp)))))

(deftest csrf-check-not-bypassed-without-apikey-test
  (is (invalid-csrf-token? (raw-query nil :allowed-actions))))

(deftest non-protected-resource-returns-csrf-token-test
  (let [resp (http-get (str (server-address) "/app/fi/welcome") {})
        cookie (get-in resp [:cookies "anti-csrf-token"])]
    (is (http200? resp))
    (is (some? cookie))
    (is (= "/" (:path cookie)))))

(deftest cookie-and-header-passes-csrf-protection-test
  (is (http200? (http-get (str (server-address) "/api/query/allowed-actions")
                          {:cookies {"anti-csrf-token" {:value "my-token"}}
                           :headers {"x-anti-forgery-token" "my-token"}}))))

(deftest missing-header-fails-csrf-check-test
  (let [resp (http-get (str (server-address) "/api/query/allowed-actions")
                       {:cookies {"anti-csrf-token" {:value "my-token"}}
                        :query-params {:id 123}
                        :headers {"Referer" "http://attacker.example.com/"}
                        :redirect-strategy :none
                        :throw-exceptions false})]
    (is (invalid-csrf-token? (decode-response resp)))))

(deftest csrf-token-regenerated-on-login-test
  (let [store           (atom {})
        cookie-store    (->cookie-store store)
        ;; Initial request to get a CSRF token
        _               (http-get (str (server-address) "/app/fi/welcome")
                                  {:cookie-store cookie-store})
        initial-csrf    (get-anti-csrf store)
        ;; Login with the same cookie store
        login-resp      (-> (http-post (str (server-address) "/api/login")
                                       {:cookie-store      cookie-store
                                        :redirect-strategy :none
                                        :throw-exceptions  false
                                        :form-params       {:username "pena"
                                                            :password "pena"}})
                            decode-response)
        post-login-csrf (get-anti-csrf store)]
    (testing "Initial CSRF token exists"
      (is (some? initial-csrf)))
    (testing "Login is successful"
      (is (and (= 200 (:status login-resp))
               (-> login-resp :body :ok))))
    (testing "Post-login CSRF token exists"
      (is (some? post-login-csrf)))
    (testing "CSRF token has changed after login"
      (is (not= post-login-csrf initial-csrf)))))
