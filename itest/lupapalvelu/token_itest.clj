(ns lupapalvelu.token-itest
  (:use [midje.sweet]
        [midje.util :only [testable-privates]]
        [lupapalvelu.token])
  (:require [lupapalvelu.mongo :as mongo]))

(facts
  
  (let [id (save-token {:foo "foo"})]
    (fact (consume-token id) => (contains {:data {:foo "foo"}}))
    (fact (consume-token id) => nil))
  
  (let [id (save-token {:foo "foo"} :ttl 100)]
    (fact (consume-token id) => (contains {:data {:foo "foo"}})))
  
  (let [id (save-token {:foo "foo"} :ttl 100)]
    (Thread/sleep 150)
    (fact (consume-token id) => nil)))
