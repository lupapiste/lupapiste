(ns lupapalvelu.token-itest
  (:use [midje.sweet]
        [lupapalvelu.security :as security]
        [lupapalvelu.token]))

;; FIXME MongoDB not available on lupaci!
(comment
(facts
  (against-background (security/current-user) => {:username "fozzaa"})
  
  (let [id (make-token :fofo {:foo "foo"})]
    (get-token id) => (contains {:token-type :fofo :data {:foo "foo"}})
    (get-token id) => nil)
  
  (let [id (make-token :fofo {:foo "foo"} :ttl 100)]
    (get-token id) => (contains {:token-type :fofo :data {:foo "foo"}}))
  
  (let [id (make-token :fofo {:foo "foo"} :ttl 100)]
    (Thread/sleep 150)
    (get-token id) => nil)
  
  (let [id (make-token :fofo {:foo "foo"} :auto-consume false)]
    (get-token id) => (contains {:token-type :fofo :data {:foo "foo"}})
    (get-token id :consume true) => (contains {:token-type :fofo :data {:foo "foo"}})
    (get-token id) => nil))

(defmethod handle-token :fofo [token params]
  {:works true :foo (get-in token [:data :foo]) :bar (:bar params)})

(facts
  (against-background (security/current-user) => {:username "fozzaa"})
  (consume-token (make-token :fofo {:foo "foo"}) {:bar "bar"}) => {:works true :foo "foo" :bar "bar"}
  (consume-token (make-token :no-such-type {:foo "bar"}) {}) => nil
  (consume-token "no-such-token" {}) => nil))

