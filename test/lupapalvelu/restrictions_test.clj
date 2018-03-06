(ns lupapalvelu.restrictions-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.restrictions :refer :all]
            [sade.util :as util]))

(testable-privates lupapalvelu.restrictions restrict)

(facts restrict
  (fact "both nils"
    (restrict nil nil) => #{})

  (fact "restrictions is nil"
    (restrict #{:test/test} nil) => #{:test/test})

  (fact "permissions is nil"
    (restrict nil #{:test/test}) => #{})

  (fact "one permissions - one matching restriction"
    (restrict #{:test/test} [:test/test]) => #{})

  (fact "one permission - no restrictions"
    (restrict #{:test/test} []) => #{:test/test})

  (fact "one permission - one matching restriction"
    (restrict #{:test/test} [:test/test]) => #{})

  (fact "two permissions - one matching restriction"
    (restrict #{:test/test :test/do} [:test/test]) => #{:test/do})

  (fact "multiple permissions - multiple matching restrictions"
    (restrict #{:test/test :test/do :test/fail :test/bar} [:test/test :test/fail :test/foo]) => #{:test/do :test/bar}))


(facts apply-auth-restriction
  (facts "others"
    (fact "restrictions are applied to other users"
      (apply-auth-restriction {:user {:id "user1"}} #{:test/do :test/fail} {:restrictions [:test/fail]
                                                                            :user {:id "user2"}
                                                                            :target {:type "others"}})
      => #{:test/do})

    (fact "restriction is not applied to self"
      (apply-auth-restriction {:user {:id "user1"}} #{:test/do :test/fail} {:restrictions [:test/fail]
                                                                            :user {:id "user1"}
                                                                            :target {:type "others"}})
      => #{:test/do :test/fail})

    (fact "restriction is not applied company user in same company"
      (apply-auth-restriction {:user {:id "user1"
                                      :company {:id "company1"}}}
                              #{:test/do :test/fail}
                              {:restrictions [:test/fail]
                               :user {:id "company1"}
                               :target {:type "others"}})
      => #{:test/do :test/fail})))

(facts apply-auth-restrictions
  (facts "others"
    (fact "single restriction"
      (apply-auth-restrictions {:user {:id "user1"}
                                :application {:authRestrictions [{:restrictions [:test/fail]
                                                                  :user {:id "user2"}
                                                                  :target {:type "others"}}]}}
                               #{:test/do :test/fail})
      => #{:test/do})

    (fact "single restriction - update command"
      (apply-auth-restrictions {:user {:id "user1"}
                                :application {:authRestrictions [{:restrictions [:test/fail]
                                                                  :user {:id "user2"}
                                                                  :target {:type "others"}}]}
                                :permissions #{:test/do :test/fail}})
      => {:user {:id "user1"}
          :application {:authRestrictions [{:restrictions [:test/fail]
                                            :user {:id "user2"}
                                            :target {:type "others"}}]}
          :permissions #{:test/do}})

    (fact "multiple restrictions"
      (apply-auth-restrictions {:user {:id "user1"}
                                :application {:authRestrictions [{:restrictions [:test/fail]
                                                                  :user {:id "user2"}
                                                                  :target {:type "others"}}
                                                                 {:restrictions [:test/do]
                                                                  :user {:id "user1"}
                                                                  :target {:type "others"}}
                                                                 {:restrictions [:test/test :test/foo :test/bar]
                                                                  :user {:id "user3"}
                                                                  :target {:type "others"}}]}}
                               #{:test/do :test/fail :test/test :test/foo})
      => #{:test/do})))
