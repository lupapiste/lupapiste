(ns lupapalvelu.migration.remove-duplicate-auths-test
  (:require [lupapalvelu.migration.migrations :refer [remove-owners-double-auth-updates]]
            [monger.operators :refer [$pull]]
            [midje.sweet :refer :all]))

(def test-auths [{:role "owner"
                  :id "test1"}
                 {:role "writer"
                  :id "test2"}
                 {:role "writer"
                  :id "test1"}])

(def no-action-auth [{:role "owner"
                      :id "test1"}
                     {:role "writer"
                      :id "test2"}])

(def reader-and-writer-auth [{:role "owner"
                              :id "test1"}
                             {:role "writer"
                              :id "test2"}
                             {:role "writer"
                              :id "test1"}
                             {:role "reader"
                              :id "test1"}])

(fact "Owner's writer auth is pulled"
  (remove-owners-double-auth-updates test-auths) => [{$pull {:auth {:role "writer" :id "test1"}}}])

(fact "If no duplicates, nil is returned"
  (remove-owners-double-auth-updates no-action-auth) => nil)

(fact "Several roles pulled if needed (but not owner)"
  (remove-owners-double-auth-updates reader-and-writer-auth) => [{$pull {:auth {:role "writer" :id "test1"}}}
                                                                 {$pull {:auth {:role "reader" :id "test1"}}}])

(fact (remove-owners-double-auth-updates []) => nil)
(fact (remove-owners-double-auth-updates [{:bobobo 123}]) => nil)

