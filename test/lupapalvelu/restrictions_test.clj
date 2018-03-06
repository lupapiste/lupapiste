(ns lupapalvelu.restrictions-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.restrictions :refer :all]
            [sade.util :as util]))

(testable-privates lupapalvelu.restrictions restrict apply-auth-restriction check-auth-restriction-entry)

(facts restrict
  (fact "both nils"
    (restrict nil nil) => #{})

  (fact "restrictions is nil"
    (restrict #{:test/test} nil) => #{:test/test})

  (fact "permissions is nil"
    (restrict nil :test/test) => #{})

  (fact "one permissions - one matching restriction"
    (restrict #{:test/test} :test/test) => #{})

  (fact "one permission - no restrictions"
    (restrict #{:test/test} []) => #{:test/test})

  (fact "one permission - one matching restriction"
    (restrict #{:test/test} :test/test) => #{})

  (fact "two permissions - one matching restriction"
    (restrict #{:test/test :test/do} :test/test) => #{:test/do}))


(facts apply-auth-restriction
  (facts "others"
    (fact "restrictions are applied to other users"
      (apply-auth-restriction {:user {:id "user1"}} #{:test/do :test/fail} {:restriction :test/fail
                                                                            :user {:id "user2"}
                                                                            :target {:type "others"}})
      => #{:test/do})

    (fact "restriction is not applied to self"
      (apply-auth-restriction {:user {:id "user1"}} #{:test/do :test/fail} {:restriction :test/fail
                                                                            :user {:id "user1"}
                                                                            :target {:type "others"}})
      => #{:test/do :test/fail})

    (fact "restriction is not applied company user in same company"
      (apply-auth-restriction {:user {:id "user1"
                                      :company {:id "company1"}}}
                              #{:test/do :test/fail}
                              {:restriction :test/fail
                               :user {:id "company1"}
                               :target {:type "others"}})
      => #{:test/do :test/fail})))

(facts apply-auth-restrictions
  (facts "others"
    (fact "single restriction"
      (apply-auth-restrictions {:user {:id "user1"}
                                :application {:authRestrictions [{:restriction :test/fail
                                                                  :user {:id "user2"}
                                                                  :target {:type "others"}}]}}
                               #{:test/do :test/fail})
      => #{:test/do})

    (fact "single restriction - update command"
      (apply-auth-restrictions {:user {:id "user1"}
                                :application {:authRestrictions [{:restriction :test/fail
                                                                  :user {:id "user2"}
                                                                  :target {:type "others"}}]}
                                :permissions #{:test/do :test/fail}})
      => {:user {:id "user1"}
          :application {:authRestrictions [{:restriction :test/fail
                                            :user {:id "user2"}
                                            :target {:type "others"}}]}
          :permissions #{:test/do}})

    (fact "multiple restrictions"
      (apply-auth-restrictions {:user {:id "user1"}
                                :application {:authRestrictions [{:restriction :test/fail
                                                                  :user {:id "user2"}
                                                                  :target {:type "others"}}
                                                                 {:restriction :test/do
                                                                  :user {:id "user1"}
                                                                  :target {:type "others"}}
                                                                 {:restriction :test/test
                                                                  :user {:id "user3"}
                                                                  :target {:type "others"}}
                                                                 {:restriction :test/foo
                                                                  :user {:id "user3"}
                                                                  :target {:type "others"}}]}}
                               #{:test/do :test/fail :test/test :test/foo})
      => #{:test/do})))

(facts check-auth-restriction
  (facts "others"
    (fact "single applied restriction"
      (check-auth-restriction {:user {:id "user1"}
                               :application {:authRestrictions [{:restriction :test/fail
                                                                 :user {:id "user2"}
                                                                 :target {:type "others"}}]}}
                              :test/fail)
      => {:restriction :test/fail, :ok false, :text "error.permissions-restricted-by-another-user"})

    (fact "restriction not applied to self"
      (check-auth-restriction {:user {:id "user1"}
                               :application {:authRestrictions [{:restriction :test/fail
                                                                 :user {:id "user1"}
                                                                 :target {:type "others"}}]}}
                              :test/fail)
      => nil)

    (fact "no restrictions"
      (check-auth-restriction {:user {:id "user1"}
                               :application {:authRestrictions []}}
                              :test/fail)
      => nil)

    (fact "auth-restrictions not in application"
      (check-auth-restriction {:user {:id "user1"}
                               :application {}}
                              :test/fail)
      => nil)

    (fact "no mathching restriction"
      (check-auth-restriction {:user {:id "user1"}
                               :application {:authRestrictions [{:restriction :test/do
                                                                 :user {:id "user2"}
                                                                 :target {:type "others"}}]}}
                              :test/fail)
      => nil)

    (fact "multiple restrictions with match"
      (check-auth-restriction {:user {:id "user1"}
                               :application {:authRestrictions [{:restriction :test/fail
                                                                 :user {:id "user1"}
                                                                 :target {:type "others"}}
                                                                {:restriction :test/do
                                                                 :user {:id "user2"}
                                                                 :target {:type "others"}}
                                                                {:restriction :test/fail
                                                                 :user {:id "user2"}
                                                                 :target {:type "others"}}
                                                                {:restriction :test/test
                                                                 :user {:id "user2"}
                                                                 :target {:type "others"}}]}}
                              :test/fail)
      => {:restriction :test/fail, :ok false, :text "error.permissions-restricted-by-another-user"})))
