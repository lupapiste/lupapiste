(ns lupapalvelu.token-itest
  (:require [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [lupapalvelu.user :as user]
            [lupapalvelu.token :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :as fixture]))

(def db-name (str "test_token-itest_" (now)))

(mongo/connect!)
(mongo/with-db db-name (fixture/apply-fixture "minimal"))

(facts
  (fact "Create token without ttl"
    (mongo/with-db db-name
      (let [id (make-token :fofo {} {:foo "foo"})]
        (get-usable-token id) => (contains {:token-type :fofo :data {:foo "foo"}})
        (get-usable-token id) => nil)))

  (fact "Create token with ttl"
    (mongo/with-db db-name
      (let [id (make-token :fofo {} {:foo "foo"} :ttl 150)]
        (get-usable-token id) => (contains {:token-type :fofo :data {:foo "foo"}}))))

  (fact "Create token with ttl and let it expire"
    (mongo/with-db db-name
      (let [id (make-token :fofo {} {:foo "foo"} :ttl 150)]
        (Thread/sleep 200)
        (get-usable-token id) => nil)))

  (fact "Consume persistent token"
    (mongo/with-db db-name
      (let [id (make-token :fofo {} {:foo "foo"} :auto-consume false)]
        (get-usable-token id) => (contains {:token-type :fofo :data {:foo "foo"}})
        (get-usable-token id :consume true) => (contains {:token-type :fofo :data {:foo "foo"}})
        (get-usable-token id) => nil))))

(defmethod handle-token :fofo [token params]
  {:works true :foo (get-in token [:data :foo]) :bar (:bar params)})

(facts
    (mongo/with-db db-name
      (consume-token (make-token :fofo {} {:foo "foo"}) {:bar "bar"}) => {:works true :foo "foo" :bar "bar"}
      (consume-token (make-token :no-such-type {} {:foo "bar"}) {}) => nil
      (consume-token "no-such-token" {}) => nil))
