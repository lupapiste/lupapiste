(ns lupapalvelu.permissions-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.permissions :refer :all]
            [sade.util :as util]))

(testable-privates lupapalvelu.permissions defpermissions)

(defpermissions :test
  {:test-scope   {:test-role         #{:test/test
                                       :test/fail}
                  :another-test-role #{:test/fail}}
   :test-scope-b {:tester            #{:test/test
                                       :test/do}}})

(defpermissions :test-more
  {:test-scope   {:test-role         #{:test-more/test}}})

(facts restriction
  (fact "restircted permission not in common-permissions"
    (restriction :test/unknown-permission) => (throws java.lang.AssertionError))

  (fact "single restriction"
    ((restriction :application/submit) #{:application/submit :test/do}) => #{:test/do})

  (fact "multiple restictions"
    ((restriction :application/submit :comment/set-target) #{:application/submit :comment/set-target :test/do}) => #{:test/do})

  (fact "multiple restrictions - nothing left"
    ((restriction :application/submit :comment/set-target) #{:application/submit :comment/set-target}) => #{})

  (fact "two restrictions - other not in permission"
    ((restriction :application/submit :comment/set-target) #{:application/submit}) => #{}))

(facts permissions?

  (fact "single required permission - command threaded"
    (-> {:permissions #{:application/submit :test/do}}
        (permissions? [:application/submit])) => true)

  (fact "single required permission"
    (permissions? {:permissions #{:application/submit :test/do}} [:application/submit]) => true)

  (fact "multiple required permissions"
    (permissions? {:permissions #{:application/submit :comment/set-target :test/do}} [:application/submit :comment/set-target]) => true)

  (fact "permission denied"
    (permissions? {:permissions #{:test/do}} [:application/submit]) => false)

  (fact "multiple reuired permissions permission denied"
    (permissions? {:permissions #{:application/submit :test/do}} [:application/submit :comment/set-target]) => false)

  (fact "no permissions in command"
    (permissions? {} [:application/submit :comment/set-target]) => false))

(facts roles-in-scope-with-permissions
  (fact "test/test"
    (roles-in-scope-with-permissions :test-scope [:test/test]) => #{:test-role})

  (fact "test/test - string scope"
    (roles-in-scope-with-permissions "test-scope" [:test/test]) => #{:test-role})

  (fact "test/fail"
    (roles-in-scope-with-permissions :test-scope [:test/fail]) => #{:test-role :another-test-role})

  (fact "test/test + test/fail"
    (roles-in-scope-with-permissions :test-scope [:test/test :test/fail]) => #{:test-role})

  (fact "test/test + test/fail + test/do"
    (roles-in-scope-with-permissions :test-scope [:test/test :test/fail :test/do]) => #{})

  (fact "unknown scope"
    (roles-in-scope-with-permissions :unknown [:test/fail]) => #{})

  (fact "unknown permission"
    (roles-in-scope-with-permissions :test-scope [:test/unknown]) => (throws java.lang.AssertionError))

  (fact "nil scope"
    (roles-in-scope-with-permissions nil [:test/fail]) => #{})

  (fact "nil permissions"
    (roles-in-scope-with-permissions :test-scope nil) => #{:test-role :another-test-role})

  (fact "empty permissions"
    (roles-in-scope-with-permissions :test-scope []) => #{:test-role :another-test-role}))

(facts get-permissions-by-role
  (fact "existing scope and role"
    (get-permissions-by-role :test-scope "test-role") => #{:test/test :test/fail :test-more/test})

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
    (get-application-permissions {:user {:id 1} :application {:auth [{:id 1 :role "app-tester"}]}}) => #{:test/do}

    (provided (get-permissions-by-role :application "app-tester") => #{:test/do}))

  (fact "no role in auth"
    (get-application-permissions {:user {:id 1} :application {:auth []}}) => irrelevant

    (provided (get-permissions-by-role :application nil) => irrelevant :times 0))

  (fact "no application in command"
    (get-application-permissions {:user {:id 1}}) => #{}

    (provided (get-permissions-by-role :application nil) => irrelevant :times 0))

  (fact "existing role - multiple roles in auth"
    (get-application-permissions {:user {:id 1} :application {:auth [{:id 2 :role "some-role"}
                                                                     {:id 1 :role "app-tester"}
                                                                     {:id 3 :role "another-role"}]}})
    => #{:test/do}

    (provided (get-permissions-by-role :application "app-tester") => #{:test/do}))

  (fact "same user with multiple roles in auth"
    (get-application-permissions {:user {:id 1} :application {:auth [{:id 2 :role "some-role"}
                                                                     {:id 1 :role "app-tester"}
                                                                     {:id 1 :role "another-role"}]}})
    => #{:test/do :test/test :test/fail}

    (provided (get-permissions-by-role :application "app-tester") => #{:test/do :test/fail})
    (provided (get-permissions-by-role :application "another-role") => #{:test/do :test/test}))

  (fact "same user with multiple roles in auth plus restrictions"
    (get-application-permissions {:user {:id 1} :application {:auth [{:id 2 :role "some-role"}
                                                                     {:id 1 :role "app-tester"}
                                                                     {:id 1 :role "another-role"}]
                                                              :authRestrictions [{:restriction :test/fail
                                                                                  :user {:id 2}
                                                                                  :target {:type "others"}}]}})
    => #{:test/do :test/test}

    (provided (get-permissions-by-role :application "app-tester") => #{:test/do :test/fail})
    (provided (get-permissions-by-role :application "another-role") => #{:test/do :test/test})))

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
    (provided (get-permissions-by-role :organization "org-nocando") => #{}))

  (fact "without application"
    (get-organization-permissions {:user {:orgAuthz {:123-T ["org-tester"]}}})
    => #{:test/do}

    (provided (get-permissions-by-role :organization "org-tester") => #{:test/do}))

  (fact "without application - two orgs"
    (get-organization-permissions {:user {:orgAuthz {:123-T ["org-tester"] :321-T ["archiver"]}}})
    => #{:test/do :test/archive}

    (provided (get-permissions-by-role :organization "org-tester") => #{:test/do}
              (get-permissions-by-role :organization "archiver") => #{:test/archive}))

  (fact "without application - no orgs"
    (get-organization-permissions {:user {:orgAuthz {}}})
    => #{}

    (provided (get-permissions-by-role :organization irrelevant) => irrelevant :times 0)))

(testable-privates lupapalvelu.permissions apply-company-restrictions)

(fact apply-company-restrictions
  (fact "submitter"
    (apply-company-restrictions {:submit true} #{:application/submit :test/do}) => #{:application/submit :test/do})

  (fact "non-submitter"
    (apply-company-restrictions {:submit false} #{:application/submit :test/do}) => #{:test/do})

  (fact "no submit field -> non-submitter"
    (apply-company-restrictions {} #{:application/submit :test/do}) => #{:test/do})

  (fact "is submitter but no submit permissions in"
    (apply-company-restrictions {:submit true} #{:test/do}) => #{:test/do}))

(facts get-company-permissions
  (fact "company has existing role in auth"
    (get-company-permissions {:user {:company {:id 1 :role "user"}}
                              :application {:auth [{:id 1 :role "app-tester"}]}}) => #{:test/do}

    (provided (get-permissions-by-role :application "app-tester") => #{:test/do}))

  (fact "company has existing role in auth - with matching company role"
    (get-company-permissions {:user {:company {:id 1 :role "user"}}
                              :application {:auth [{:id 1 :role "app-tester" :company-role "user"}]}}) => #{:test/do}

    (provided (get-permissions-by-role :application "app-tester") => #{:test/do}))

  (fact "company has existing role in auth - company role does not match"
    (get-company-permissions {:user {:company {:id 1 :role "user"}}
                              :application {:auth [{:id 1 :role "app-tester" :company-role "admin"}]}}) => #{}

    (provided (get-permissions-by-role :application irrelevant) => irrelevant :times 0))

  (fact "company has existing role in auth - with submit rights"
    (get-company-permissions {:user {:company {:id 1 :role "user" :submit true}}
                              :application {:auth [{:id 1 :role "app-tester"}]}}) => #{:test/do :application/submit}

    (provided (get-permissions-by-role :application "app-tester") => #{:test/do :application/submit}))

  (fact "company has existing role in auth - no submit rights"
    (get-company-permissions {:user {:company {:id 1 :role "user" :submit false}}
                              :application {:auth [{:id 1 :role "app-tester"}]}}) => #{:test/do}

    (provided (get-permissions-by-role :application "app-tester") => #{:test/do :application/submit}))

  (fact "company has existing role in auth - with auth restriction"
    (get-company-permissions {:user {:company {:id 1 :role "user" :submit true}}
                              :application {:auth [{:id 1 :role "app-tester"}]
                                            :authRestrictions [{:restriction :test/fail
                                                                :user {:id 2}
                                                                :target {:type "others"}}]}})
    => #{:test/do}

    (provided (get-permissions-by-role :application "app-tester") => #{:test/do :test/fail}))

  (fact "no role in auth"
    (get-company-permissions {:user {:company {:id 1 :role "user"}} :application {:auth []}}) => #{}

    (provided (get-permissions-by-role :application nil) => irrelevant :times 0))

  (fact "no application in command"
    (get-company-permissions {:user {:company {:id 1 :role "user"}}}) => #{}

    (provided (get-permissions-by-role :application nil) => irrelevant :times 0))

  (fact "company has multiple roles in auth"
    (get-company-permissions {:user {:company {:id 1 :role "user"}}
                              :application {:auth [{:id 2 :role "some-role"}
                                                   {:id 1 :role "app-tester"}
                                                   {:id 3 :role "another-role"}]}})
    => #{:test/do}

    (provided (get-permissions-by-role :application "app-tester") => #{:test/do}))

  (fact "same company with multiple roles in auth"
    (get-company-permissions {:user {:company {:id 1 :role "user"}}
                              :application {:auth [{:id 2 :role "some-role"}
                                                   {:id 1 :role "app-tester"}
                                                   {:id 1 :role "another-role"}]}})
    => #{:test/do :test/test :test/fail}

    (provided (get-permissions-by-role :application "app-tester") => #{:test/do :test/fail})
    (provided (get-permissions-by-role :application "another-role") => #{:test/do :test/test})))

(defcontext test-context [{{id :test-id field :test-field} :test-user coll :test-coll}]
  (let [things (filter (comp #{id} :id) coll)]
    {:context-scope  :test-scope
     :context-roles  (map :role things)
     :thing          (first things)}))

(facts "defcontext"
  (facts test-context
    (fact "is function"
      test-context => fn?)

    (fact "context extended with thing"
      (test-context {:test-user {:test-id 1 :test-field "something"} :test-coll [{:id 1 :role "test-role"}]})

      => {:test-user   {:test-id 1 :test-field "something"}
          :test-coll   [{:id 1 :role "test-role"}]
          :thing       {:id 1 :role "test-role"}
          :permissions #{:test/test :test/fail :test-more/test}})

    (fact "context extended with thing - permissions extended"
      (test-context {:test-user {:test-id 1 :test-field "something"} :test-coll [{:id 1 :role "test-role"}] :permissions #{:application/do}})

      => {:test-user   {:test-id 1 :test-field "something"}
          :test-coll   [{:id 1 :role "test-role"}]
          :thing       {:id 1 :role "test-role"}
          :permissions #{:test/test :test/fail :test-more/test :application/do}})

    (fact "context extended with thing - multiple roles"
      (test-context {:test-user {:test-id 1 :test-field "something"}
                     :test-coll [{:id 1 :role "test-role"}
                                 {:id 2 :role "test-role"}
                                 {:id 1 :role "another test-role"}]})

      => {:test-user   {:test-id 1 :test-field "something"}
          :test-coll   [{:id 1 :role "test-role"}
                        {:id 2 :role "test-role"}
                        {:id 1 :role "another test-role"}]
          :thing       {:id 1 :role "test-role"}
          :permissions #{:test/test :test/fail :test-more/test}})

    (fact "thing not found - no permissions"
      (test-context {:test-user {:test-id 1 :test-field "something"} :test-coll [{:id 2 :role "test-role"}]})

      => {:test-user   {:test-id 1 :test-field "something"}
          :test-coll   [{:id 2 :role "test-role"}]
          :thing       nil
          :permissions nil})

    (fact "thing not found - permissions unchanged"
      (test-context {:test-user {:test-id 1 :test-field "something"} :test-coll [{:id 2 :role "test-role"}] :permissions #{:application/do}})

      => {:test-user   {:test-id 1 :test-field "something"}
          :test-coll   [{:id 2 :role "test-role"}]
          :thing       nil
          :permissions #{:application/do}})))

(testable-privates lupapalvelu.permissions matching-context?)

(facts matching-context?
  (fact "set matcher with match"
    (matching-context? #{:sent :submitted}
                       :sent) => truthy)

  (fact "set matcher without match"
    (matching-context? #{:sent :submitted}
                       :draft) => falsey)

  (fact "function matcher with match"
    (matching-context? keyword?
                       :sent) => truthy)

  (fact "simple map matcher with match"
    (matching-context? {:state #{:sent :submitted}}
                       {:state :sent}) => truthy)

  (fact "simple map matcher without match"
    (matching-context? {:state #{:sent :submitted}}
                       {:state :draft}) => falsey)

  (fact "empty map matcher"
    (matching-context? {}
                       {:any {:command :context}}) => truthy)

  (fact "context matcher is nil"
    (matching-context? nil
                       {:any {:command :context}}) => truthy)

  (fact "simple map/function matcher with match"
    (matching-context? {:state keyword?}
                       {:state :sent}) => truthy)

  (fact "simple map/function matcher without match"
    (matching-context? {:state string?}
                       {:state :draft}) => falsey)

  (fact "two stage map matcher with match"
    (matching-context? {:application {:state #{:sent :submitted}}}
                       {:application {:state :submitted}}) => truthy)

  (fact "two stage map matcher without match"
    (matching-context? {:application {:state #{:sent :submitted}}}
                       {:application {:state :draft}}) => falsey)

  (fact "two stage map matcher with match and wider context"
    (matching-context? {:application {:permitType #{:R :P}}}
                       {:application {:state :submitted :permitType :R}}) => truthy)

  (fact "two stage map wide matcher with match"
    (matching-context? {:application {:permitType #{:R :P} :state #{:sent :submitted}}}
                       {:application {:state :submitted :permitType :R}}) => truthy)

  (fact "two stage map even wider matcher with match"
    (matching-context? {:application {:permitType #{:R :P} :state #{:sent :submitted}} :attachment {:applicationState #{:sent}}}
                       {:application {:state :submitted :permitType :R} :attachment {:applicationState :sent}}) => truthy)

  (fact "two stage map even wider matcher with non matching state in application context"
    (matching-context? {:application {:permitType #{:R :P} :state #{:sent :submitted}} :attachment {:applicationState #{:sent}}}
                       {:application {:state :verdictGiven :permitType :R} :attachment {:applicationState :sent}}) => falsey))

(facts get-required-permissions
  (fact "one simple matcher"
    (get-required-permissions {:permissions [{:context  {:application {:permitType #{:YA}}}
                                              :required [:application/modify :attachment/ya-delete]}]}
                              {:application {:permitType :YA}})
    => {:context  {:application {:permitType #{:YA}}}
        :required [:application/modify :attachment/ya-delete]})

  (fact "one simple matcher without match"
    (get-required-permissions {:permissions [{:context  {:application {:permitType #{:YA}}}
                                              :required [:application/modify :attachment/ya-delete]}]}
                              {:application {:permitType "R"}})
    => {:required [:global/not-allowed]})

  (fact "multiple matchers - first match is returned - case YA"
    (get-required-permissions {:permissions [{:context  {:application {:state #{:canceled}}}
                                              :required [:application/modify]}
                                             {:context  {:application {:permitType #{:YA}}}
                                              :required [:application/modify :attachment/ya-delete]}
                                             {:context  {:application {:state #{:sent :submitted}}}
                                              :required [:application/modify :attachment/delete]}
                                             {:context  {:application {:state #{:verdictGiven}}
                                                         :attachment  {:applicationState #{:verdictGiven}}}
                                              :required [:attachment/delete]}]}
                              {:application {:permitType :YA :state :sent}})
    => {:context  {:application {:permitType #{:YA}}}
        :required [:application/modify :attachment/ya-delete]})

  (fact "multiple matchers - first match is returned - case R"
    (get-required-permissions {:permissions [{:context  {:application {:state #{:canceled}}}
                                              :required [:application/modify]}
                                             {:context  {:application {:permitType #{:YA}}}
                                              :required [:application/modify :attachment/ya-delete]}
                                             {:context  {:application {:state #{:sent :submitted}}}
                                              :required [:application/modify :attachment/delete]}
                                             {:context  {:application {:state #{:verdictGiven}}
                                                         :attachment  {:applicationState #{:verdictGiven}}}
                                              :required [:attachment/delete]}]}
                              {:application {:permitType :R :state :sent}})
    => {:context  {:application {:state #{:sent :submitted}}}
        :required [:application/modify :attachment/delete]})

  (fact "multiple matchers - first match is returned - case R / verdictGiven"
    (get-required-permissions {:permissions [{:context  {:application {:state #{:canceled}}}
                                              :required [:application/modify]}
                                             {:context  {:application {:permitType #{:YA}}}
                                              :required [:application/modify :attachment/ya-delete]}
                                             {:context  {:application {:state #{:sent :submitted}}}
                                              :required [:application/modify :attachment/delete]}
                                             {:context  {:application {:state #{:verdictGiven}}
                                                         :attachment  {:applicationState #{:verdictGiven}}}
                                              :required [:attachment/delete]}]}
                              {:application {:permitType :R :state :verdictGiven} :attachment {:applicationState :verdictGiven}})
    => {:context  {:application {:state #{:verdictGiven}}
                   :attachment  {:applicationState #{:verdictGiven}}}
        :required [:attachment/delete]}))
