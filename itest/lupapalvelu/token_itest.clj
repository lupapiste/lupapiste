(ns lupapalvelu.token-itest
  (:use [midje.sweet]
        [midje.util :only [testable-privates]]
        [lupapalvelu.token])
  (:require [lupapalvelu.mongo :as mongo]))

(facts
  
  (let [id (make-token :fofo {:foo "foo"})]
    (get-and-consume-token id) => (contains {:token-type :fofo :data {:foo "foo"}})
    (get-and-consume-token id) => nil)
  
  (let [id (make-token :fofo {:foo "foo"} :ttl 100)]
    (get-and-consume-token id) => (contains {:token-type :fofo :data {:foo "foo"}}))
  
  (let [id (make-token :fofo {:foo "foo"} :ttl 100)]
    (Thread/sleep 150)
    (get-and-consume-token id) => nil))

(defmethod handle-token :fofo [token]
  {:works true :bar (:foo (:data token))})

(facts
  (consume-token (make-token :fofo {:foo "bar"})) => {:works true :bar "bar"}
  (consume-token (make-token :no-such-type {:foo "bar"})) => nil 
  (consume-token "no-such-token") => nil)
