(ns lupapalvelu.token-itest
  (:use [midje.sweet]
        [midje.util :only [testable-privates]]
        [lupapalvelu.token]))

;; FIXME MongoDB not available on lupaci!
(comment
  (facts
    (let [id (make-token :fofo {:foo "foo"})]
      (get-and-consume-token id) => (contains {:token-type :fofo :data {:foo "foo"}})
      (get-and-consume-token id) => nil)

    (let [id (make-token :fofo {:foo "foo"} :ttl 100)]
      (get-and-consume-token id) => (contains {:token-type :fofo :data {:foo "foo"}}))

    (let [id (make-token :fofo {:foo "foo"} :ttl 100)]
      (Thread/sleep 150)
      (get-and-consume-token id) => nil))

  (defmethod handle-token :fofo [token params]
    {:works true :foo (get-in token [:data :foo]) :bar (:bar params)})

  (facts
    (consume-token (make-token :fofo {:foo "foo"}) {:bar "bar"}) => {:works true :foo "foo" :bar "bar"}
    (consume-token (make-token :no-such-type {:foo "bar"}) {}) => nil
    (consume-token "no-such-token" {}) => nil))
