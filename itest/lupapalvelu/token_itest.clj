(ns lupapalvelu.token-itest
  (:require [lupapalvelu.itest-util :refer [with-test-system]]
            [lupapalvelu.token :refer :all]
            [midje.sweet :refer :all]))

(set! *warn-on-reflection* true)

(defmethod handle-token :fofo [token params _system]
  {:works true :foo (get-in token [:data :foo]) :bar (:bar params)})

(with-test-system {:fixture "minimal"}
  (facts
      (fact "Create token without ttl"
        (let [id (make-token :fofo {} {:foo "foo"})]
          (get-usable-token id) => (contains {:token-type :fofo :data {:foo "foo"}})
          (get-usable-token id) => nil))

    (fact "Create token with ttl"
      (let [id (make-token :fofo {} {:foo "foo"} :ttl 150)]
        (get-usable-token id) => (contains {:token-type :fofo :data {:foo "foo"}})))

    (fact "Create token with ttl and let it expire"
      (let [id (make-token :fofo {} {:foo "foo"} :ttl 150)]
        (Thread/sleep 200)
        (get-usable-token id) => nil))

    (fact "Consume persistent token"
      (let [id (make-token :fofo {} {:foo "foo"} :auto-consume false)]
        (get-usable-token id) => (contains {:token-type :fofo :data {:foo "foo"}})
        (get-usable-token id :consume true) => (contains {:token-type :fofo :data {:foo "foo"}})
        (get-usable-token id) => nil)))

  (facts "not-found test"
    (consume-token {} (make-token :fofo {} {:foo "foo"}) {:bar "bar"})
    => [:usable {:works true :foo "foo" :bar "bar"}]
    (consume-token {} (make-token :no-such-type {} {:foo "bar"}) {}) => [:usable nil]
    (consume-token {} "no-such-token" {}) => [:not-found]))
