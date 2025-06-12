(ns lupapalvelu.asiointi.logging-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.asiointi.logging :as api-logging]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]])
  (:import [java.util Date]
           [org.bson.types ObjectId]))

(set! *warn-on-reflection* true)

(defn object-id? [v] (instance? ObjectId v))
(defn date? [v] (instance? Date v))

(deftest middleware-test
  (let [response      {:status 200}
        inner-handler (fn [{::api-logging/keys [logger]}]
                        (logger {:foo :bar})
                        response)
        *entries      (atom [])
        commit        (fn [entry] (swap! *entries conj entry))
        wrap          (:wrap api-logging/middleware)
        handler       (wrap inner-handler commit)
        request       {:uri         "/foo/bar"
                       :remote-addr "127.0.0.1"}]
    (testing "simple request"
      (is (= response (handler request)))
      (is (match? (m/nested-equals
                    [{:id          object-id?
                      :received-at date?
                      :request     request
                      :context     {:foo :bar}
                      :response    response}])
                  (first (reset-vals! *entries [])))))
    (testing "empty or nil request params discarded"
      (let [request (assoc request :query-string nil :form-params {})]
        (is (= response (handler request)))
        (is (match? (m/nested-equals
                      [{:id          object-id?
                        :received-at date?
                        :request     (dissoc request :query-string :form-params)
                        :context     {:foo :bar}
                        :response    response}])
                    (first (reset-vals! *entries []))))))
    (testing "unexpected request keys"
      (let [request (assoc request :quuz :bar)]
        (is (= response (handler request)))
        (is (match? (m/nested-equals
                      [{:id          object-id?
                        :received-at date?
                        :request     (dissoc request :quuz)
                        :context     {:foo :bar}
                        :response    response}])
                    (first (reset-vals! *entries []))))))
    (testing "headers"
      (let [request (assoc request :headers {"host"          "localhost:8000"
                                             "Authorization" "Basic 1312345234"})]
        (is (= response (handler request)))
        (is (match? (m/nested-equals
                      [{:id          object-id?
                        :received-at date?
                        :request     {:uri         "/foo/bar"
                                      :remote-addr "127.0.0.1"
                                      :headers     {"host" "localhost:8000"}}
                        :context     {:foo :bar}
                        :response    response}])
                    (first (reset-vals! *entries []))))))))

(deftest exception-test
  (let [inner-handler (fn [{::api-logging/keys [logger]}]
                        (logger {:foo :bar})
                        (throw (ex-info "oh noes" {:intentional? true})))
        *entries      (atom [])
        commit        (fn [entry] (swap! *entries conj entry))
        wrap          (:wrap api-logging/middleware)
        handler       (wrap inner-handler commit)
        request       {:uri         "/foo/bar"
                       :remote-addr "127.0.0.1"}]
    (testing "simple request throws exception"
      (is (thrown-with-msg? Exception
                            #"oh noes"
                            (handler request)))
      (is (match? (m/nested-equals
                    [{:id          object-id?
                      :received-at date?
                      :request     request
                      :context     {:foo           :bar
                                    :error-message "oh noes"}}])
                  (first (reset-vals! *entries [])))))))
