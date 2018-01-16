(ns lupapalvelu.permissions-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.permissions :refer :all]))

(defpermission test
  {:test-scope   {:test-role         #{:test/test
                                       :test/fail}
                  :another-test-role #{:test/fail}}
   :test-scope-b {:tester            #{:test/test
                                       :test/do}}})

(facts get-permissions-by-role
  (fact "existing scope and role"
    (get-permissions-by-role :test-scope "test-role") => #{:test/test :test/fail})

  (fact "existing scope and another role"
    (get-permissions-by-role :test-scope :another-test-role) => #{:test/fail})

  (fact "another existing scope"
    (get-permissions-by-role :test-scope-b :tester) => #{:test/test :test/do})

  (fact "missing scope"
    (get-permissions-by-role :test-scope-b "test-role") => #{})

  (fact "missing role"
    (get-permissions-by-role :test-scope "some-role") => #{})

  (fact "nil role"
    (get-permissions-by-role :test-scope nil) => #{})

  (fact "nil scope"
    (get-permissions-by-role nil "test-role") => #{}))

(facts get-global-permissions
  (fact "permissions resolved"
    (get-global-permissions {:user {:role "tester"}}) => ..permissions..

    (provided (get-permissions-by-role :global :tester) => ..permissions..)))

(facts get-application-permissions
  (fact "existing role"
    (get-application-permissions {:user {:id 1} :application {:auth [{:id 1 :role "app-tester"}]}}) => ..permissions..

    (provided (get-permissions-by-role :application "app-tester") => ..permissions..))

  (fact "no role in auth"
    (get-application-permissions {:user {:id 1} :application {:auth []}}) => irrelevant

    (provided (get-permissions-by-role :application nil) => irrelevant :times 1))

  (fact "no application in command"
    (get-application-permissions {:user {:id 1}}) => irrelevant

    (provided (get-permissions-by-role :application nil) => irrelevant :times 1))

  (fact "existing role - multiple roles in auth"
    (get-application-permissions {:user {:id 1} :application {:auth [{:id 2 :role "some-role"}
                                                                     {:id 1 :role "app-tester"}
                                                                     {:id 3 :role "another-role"}]}})
    => ..permissions..

    (provided (get-permissions-by-role :application "app-tester") => ..permissions..)))

(facts get-organization-permissions
  (fact "existing role"
    (get-organization-permissions {:application {:organization "123-T"} :user {:orgAuthz {:123-T ["org-tester"]}}})
    => #{:test/do}

    (provided (get-permissions-by-role :organization "org-tester") => #{:test/do}))

  (fact "existing multiple roles"
    (get-organization-permissions {:application {:organization "123-T"} :user {:orgAuthz {:123-T ["org-tester"
                                                                                                  "org-mighty"]}}})
    => #{:test/do :test/test :test/do-anything}

    (provided (get-permissions-by-role :organization "org-tester") => #{:test/do :test/test})
    (provided (get-permissions-by-role :organization "org-mighty") => #{:test/do-anything :test/test}))

  (fact "existing role - multiple organizations"
    (get-organization-permissions {:application {:organization "123-T"} :user {:orgAuthz {:123-T ["org-nocando"
                                                                                                  "org-mighty"]
                                                                                          :100-T ["org-tester"]}}})
    => #{:test/test :test/do-anything}

    (provided (get-permissions-by-role :organization "org-mighty")  => #{:test/do-anything :test/test})
    (provided (get-permissions-by-role :organization "org-nocando") => #{})))
