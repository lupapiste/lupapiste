(ns lupapalvelu.token-itest
  (:use [midje.sweet]
        [midje.util :only [testable-privates]]
        [lupapalvelu.token])
  (:require [lupapalvelu.mongo :as mongo]))

(def get-and-use-token #'lupapalvelu.token/get-and-use-token)

(facts
  
  (let [id (save-token :fofo {:foo "foo"})]
    (get-and-use-token id) => (contains {:token-type :fofo :data {:foo "foo"}})
    (get-and-use-token id) => nil)
  
  (let [id (save-token :fofo {:foo "foo"} :ttl 100)]
    (get-and-use-token id) => (contains {:token-type :fofo :data {:foo "foo"}}))
  
  (let [id (save-token :fofo {:foo "foo"} :ttl 100)]
    (Thread/sleep 150)
    (get-and-use-token id) => nil))

(defmethod handle-token :fofo [token]
  {:works true :bar (:foo (:data token))})

(facts
  (consume-token (save-token :fofo {:foo "bar"})) => {:works true :bar "bar"})
