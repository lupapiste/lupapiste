(ns lupapalvelu.permissions-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [lupapalvelu.permissions :as permissions :refer [defcontext]]))

(deftest restriction-test
  (testing "restricted permission not in common permissions"
    (is (thrown? java.lang.AssertionError (permissions/restriction :test/unknown-permission))))
  (testing "single restriction"
    (is (= #{:test/do}
           ((permissions/restriction :application/submit) #{:application/submit :test/do}))))
  (testing "multiple restrictions"
    (is (= #{:test/do}
           ((permissions/restriction :application/submit :comment/set-target) #{:application/submit :comment/set-target :test/do}))))
  (testing "multiple restrictions - nothing left"
    (is (= #{}
           ((permissions/restriction :application/submit :comment/set-target) #{:application/submit :comment/set-target}))))
  (testing "two restrictions - other not in permissions"
    (is (= #{}
           ((permissions/restriction :application/submit :comment/set-target) #{:application/submit})))))

(deftest permissions-test
  (testing "single required permission - command threaded"
    (is (-> {:permissions #{:application/submit :test/do}}
            (permissions/permissions? [:application/submit]))))
  (testing "single required permission"
    (is (permissions/permissions? {:permissions #{:application/submit :test/do}} [:application/submit])))
  (testing "multiple required permissions"
    (is (permissions/permissions? {:permissions #{:application/submit :comment/set-target :test/do}} [:application/submit :comment/set-target])))
  (testing "permission denied"
    (is (not (permissions/permissions? {:permissions #{:test/do}} [:application/submit]))))
  (testing "multiple required permissions - permission denied"
    (is (not (permissions/permissions? {:permissions #{:application/submit :test/do}} [:application/submit :comment/set-target]))))
  (testing "no permissions in command"
    (is (not (permissions/permissions? {} [:application/submit :comment/set-target])))))

(deftest roles-in-scope-with-permissions-test
  (testing "Keyword scope"
    (is (= #{:applicant :authority}
           (permissions/roles-in-scope-with-permissions :global [:application/create]))))
  (testing "String scope"
    (is (= #{:applicant :authority}
           (permissions/roles-in-scope-with-permissions "global" [:application/create]))))
  (testing "Multiple permissions"
    (is (= #{:oirAuthority}
           (permissions/roles-in-scope-with-permissions :global [:application/cancel :application/read]))))
  (testing "Too many permissions"
    (is (= #{}
           (permissions/roles-in-scope-with-permissions :global [:application/cancel :application/read :application/create]))))
  (testing "Unknown scope"
    (is (= #{}
           (permissions/roles-in-scope-with-permissions :foobar [:application/read]))))
  (testing "Unknown permission"
    (is (thrown? java.lang.AssertionError
                 (permissions/roles-in-scope-with-permissions :global [:test/unknown]))))
  (testing "Nil scope"
    (is (= #{}
           (permissions/roles-in-scope-with-permissions nil [:application/read]))))
  (testing "Nil permissions"
    (is (= #{:admin :applicant :authority :financialAuthority :oirAuthority}
           (permissions/roles-in-scope-with-permissions :global nil))))
  (testing "Empty permissions"
    (is (= #{:admin :applicant :authority :financialAuthority :oirAuthority}
           (permissions/roles-in-scope-with-permissions :global [])))))

(deftest get-permissions-by-role-test
  (testing "Existing scope and role"
    (is (= #{:application/create
             :global/map-config
             :global/property-info
             :users/printing-order
             :users/update-own-data}
           (permissions/get-permissions-by-role :global "applicant"))))
  (testing "Missing scope"
    (is (= #{} (permissions/get-permissions-by-role :foobar "applicant"))))
  (testing "Missing role"
    (is (= #{} (permissions/get-permissions-by-role :global "some-role"))))
  (testing "Nil role"
    (is (= #{} (permissions/get-permissions-by-role :global nil))))
  (testing "Nil scope"
    (is (= #{} (permissions/get-permissions-by-role nil "applicant")))))

(deftest get-global-permissions-test
  (is (= #{:application/create
           :global/map-config
           :global/property-info
           :users/printing-order
           :users/update-own-data}
         (permissions/get-global-permissions {:user {:role "applicant"}}))))

(deftest get-application-permissions-test
  (let [statementPerms #{:application/accordions-open
                         :application/read
                         :comment/add
                         :comment/read
                         :processing/read
                         :processing/mark-seen}
        guestPerms     #{:application/accordions-open
                         :application/printing-order
                         :application/read
                         :organization/check-permanent-archive-enabled
                         :processing/read}]
    (testing "existing role"
      (is (= statementPerms
             (permissions/get-application-permissions {:user {:id 1} :application {:auth [{:id 1 :role "statementGiver"}]}})))
      (is (= guestPerms
             (permissions/get-application-permissions {:user {:id 1} :application {:auth [{:id 1 :role "guest"}]}}))))
    (testing "no role in auth"
      (is (= #{}
             (permissions/get-application-permissions {:user {:id 1} :application {:auth []}}))))
    (testing "no application in command"
      (is (= #{}
             (permissions/get-application-permissions {:user {:id 1}}))))
    (testing "existing role - multiple roles in auth"
      (is (= guestPerms
             (permissions/get-application-permissions {:user {:id 1} :application {:auth [{:id 2 :role "statementGiver"}
                                                                                          {:id 1 :role "guest"}]}}))))
    (testing "same user with multiple roles in auth"
      (is (= (set/union statementPerms guestPerms)
             (permissions/get-application-permissions {:user {:id 1} :application {:auth [{:id 2 :role "foreman"}
                                                                                          {:id 1 :role "statementGiver"}
                                                                                          {:id 1 :role "guest"}]}}))))
    (testing "same user with multiple roles in auth plus restrictions - :comment/add permission is removed"
      (is (= (-> (set/union statementPerms guestPerms)
                 (disj :comment/add))
             (permissions/get-application-permissions {:user        {:id 1}
                                                       :application {:auth             [{:id 2 :role "foreman"}
                                                                                        {:id 1 :role "statementGiver"}
                                                                                        {:id 1 :role "guest"}]
                                                                     :authRestrictions [{:restriction :comment/add
                                                                                         :user        {:id 2}
                                                                                         :target      {:type "others"}}]}}))))))

(deftest get-organization-permissions-test
  (let [digitizerPerms #{:application/mark-everything-seen
                         :application/show-authorities
                         :application/tos-operations
                         :organization/digitize}
        statementPerms #{:application/organization-tags
                         :application/read-authority-notice
                         :application/set-authority-notice
                         :application/set-urgency
                         :processing/read
                         :processing/mark-seen}]
    (testing "with application"
      (testing "existing role"
        (is (= digitizerPerms
               (permissions/get-organization-permissions {:application {:organization "123-T"}
                                                          :user        {:orgAuthz {:123-T #{:digitizer}}}}))))
      (testing "existing multiple roles"
        (is (= (set/union digitizerPerms statementPerms)
               (permissions/get-organization-permissions {:application {:organization "123-T"}
                                                          :user        {:orgAuthz {:123-T #{:digitizer
                                                                                            :statementGiver}}}}))))
      (testing "existing role - multiple organizations"
        (is (= digitizerPerms
               (permissions/get-organization-permissions {:application {:organization "123-T"}
                                                          :user        {:orgAuthz {:123-T #{:foobar
                                                                                            :digitizer}
                                                                                   :100-T #{:statementGiver}}}}))))
      (testing "without application and with organizationId in params"
        (testing "existing role - multiple organizations"
          (is (= (set/union digitizerPerms statementPerms)
                 (permissions/get-organization-permissions {:data {:organizationId "123-T"}
                                                            :user {:orgAuthz {:123-T #{:digitizer
                                                                                       :statementGiver}
                                                                              :100-T #{:biller}}}}))))))
    (testing "without application and without organizationId in params"
      (testing "user having one organization"
        (is (= digitizerPerms
               (permissions/get-organization-permissions {:user {:orgAuthz {:123-T #{:digitizer}}}}))))
      (testing "user having two organizations"
        ;; NOTE: No app nor org? => User gets ALL the permissions of the orgs!
        (is (= (set/union digitizerPerms statementPerms)
               (permissions/get-organization-permissions {:user {:orgAuthz {:123-T #{:digitizer}
                                                                            :321-T #{:statementGiver}}}}))))
      (testing "user having no organizations"
        (is (= #{}
               (permissions/get-organization-permissions {:user {:orgAuthz {}}})))))))

(deftest apply-company-restrictions-test
  (testing "submitter"
    (is (= #{:application/submit :test/do}
           (permissions/apply-company-restrictions {:submit true} #{:application/submit :test/do}))))
  (testing "non-submitter"
    (is (= #{:test/do}
           (permissions/apply-company-restrictions {:submit false} #{:application/submit :test/do}))))
  (testing "no submit field implies non-submitter"
    (is (= #{:test/do}
           (permissions/apply-company-restrictions {} #{:application/submit :test/do}))))
  (testing "submitter but no submit permissions present"
    (is (= #{:test/do}
           (permissions/apply-company-restrictions {:submit true} #{:test/do})))))

(deftest get-company-permissions-test
  (let [statementPerms #{:application/accordions-open
                         :application/read
                         :comment/add
                         :comment/read
                         :processing/read
                         :processing/mark-seen}
        guestPerms     #{:application/accordions-open
                         :application/printing-order
                         :application/read
                         :organization/check-permanent-archive-enabled
                         :processing/read}]
    (testing "Company has an existing role in auth"
      (is (= statementPerms
             (permissions/get-company-permissions {:user        {:company {:id 1 :role "user"}}
                                                   :application {:auth [{:id 1 :role "statementGiver"}]}}))))
    (is (= guestPerms
           (permissions/get-company-permissions {:user        {:company {:id 1 :role "user"}}
                                                 :application {:auth [{:id 1 :role "guest"}]}})))
    (testing "Company has an existing role in auth - with a matching company role"
      (is (= statementPerms
             (permissions/get-company-permissions {:user        {:company {:id 1 :role "user"}}
                                                   :application {:auth [{:id 1 :role "statementGiver" :company-role "user"}]}}))))
    (testing "Company has an existing role in auth - the company role does not match"
      (is (= #{}
             (permissions/get-company-permissions {:user        {:company {:id 1 :role "user"}}
                                                   :application {:auth [{:id 1 :role "statementGiver" :company-role "admin"}]}}))))
    (testing "Company has an existing role in auth - with submit rights"
      (is (contains? (permissions/get-company-permissions {:user        {:company {:id 1 :role "user" :submit true}}
                                                           :application {:auth [{:id 1 :role "writer"}]}})
                     :application/submit)))
    (testing "Company has an existing role in auth - no submit rights"
      (is (not (contains? (permissions/get-company-permissions {:user        {:company {:id 1 :role "user" :submit false}}
                                                                :application {:auth [{:id 1 :role "writer"}]}})
                          :application/submit))))
    (testing "Company has an existing role in auth - with an auth restriction"
      (is (= (disj statementPerms :comment/add)
             (permissions/get-company-permissions {:user        {:company {:id 1 :role "user" :submit true}}
                                                   :application {:auth             [{:id 1 :role "statementGiver"}]
                                                                 :authRestrictions [{:restriction :comment/add
                                                                                     :user        {:id 2}
                                                                                     :target      {:type "others"}}]}}))))
    (testing "No role in auth"
      (is (= #{}
             (permissions/get-company-permissions {:user {:company {:id 1 :role "user"}} :application {:auth []}}))))
    (testing "No application in the command"
      (is (= #{}
             (permissions/get-company-permissions {:user {:company {:id 1 :role "user"}}}))))
    (testing "Company has multiple roles in auth"
      (is (= statementPerms
             (permissions/get-company-permissions {:user        {:company {:id 1 :role "user"}}
                                                   :application {:auth [{:id 2 :role "guest"}
                                                                        {:id 1 :role "statementGiver"}
                                                                        {:id 3 :role "writer"}]}}))))
    (testing "Same company with multiple roles in auth"
      (is (= (set/union statementPerms guestPerms)
             (permissions/get-company-permissions {:user        {:company {:id 1 :role "user"}}
                                                   :application {:auth [{:id 2 :role "writer"}
                                                                        {:id 1 :role "statementGiver"}
                                                                        {:id 1 :role "guest"}]}}))))))

(defcontext test-context [{{id :test-id} :test-user coll :test-coll}]
  (let [things (filter (comp #{id} :id) coll)]
    {:context-scope :application
     :context-roles (map :role things)
     :thing         (first things)}))

(deftest defcontext-test
  (let [guestPerms #{:application/accordions-open
                     :application/printing-order
                     :application/read
                     :organization/check-permanent-archive-enabled
                     :processing/read}]
    (testing "is a function"
      (is (fn? test-context)))
    (testing "context extended with thing"
      (is (= {:test-user   {:test-id 1 :test-field "something"}
              :test-coll   [{:id 1 :role "guest"}]
              :thing       {:id 1 :role "guest"}
              :permissions guestPerms}
             (test-context {:test-user {:test-id 1 :test-field "something"}
                            :test-coll [{:id 1 :role "guest"}]}))))
    (testing "context extended with thing - permissions extended"
      (is (= {:test-user   {:test-id 1 :test-field "something"}
              :test-coll   [{:id 1 :role "guest"}]
              :thing       {:id 1 :role "guest"}
              :permissions (set/union guestPerms #{:application/do})}
             (test-context {:test-user   {:test-id 1 :test-field "something"}
                            :test-coll   [{:id 1 :role "guest"}]
                            :permissions #{:application/do}}))))
    (testing "context extended with thing - multiple roles"
      (is (= {:test-user   {:test-id 1 :test-field "something"}
              :test-coll   [{:id 1 :role "guest"}
                            {:id 2 :role "guest"}
                            {:id 1 :role "another guest"}]
              :thing       {:id 1 :role "guest"}
              :permissions guestPerms}
             (test-context {:test-user {:test-id 1 :test-field "something"}
                            :test-coll [{:id 1 :role "guest"}
                                        {:id 2 :role "guest"}
                                        {:id 1 :role "another guest"}]}))))
    (testing "thing not found - no permissions"
      (is (= {:test-user   {:test-id 1 :test-field "something"}
              :test-coll   [{:id 2 :role "test-role"}]
              :thing       nil
              :permissions nil}
             (test-context {:test-user {:test-id 1 :test-field "something"}
                            :test-coll [{:id 2 :role "test-role"}]}))))
    (testing "thing not found - permissions unchanged"
      (is (= {:test-user   {:test-id 1 :test-field "something"}
              :test-coll   [{:id 2 :role "test-role"}]
              :thing       nil
              :permissions #{:application/do}}
             (test-context {:test-user   {:test-id 1 :test-field "something"}
                            :test-coll   [{:id 2 :role "test-role"}]
                            :permissions #{:application/do}}))))))

(deftest matching-context?-test
  (testing "Set matcher with match"
    (is (permissions/matching-context? #{:sent :submitted}
                                       :sent)))
  (testing "Set matcher without match"
    (is (not (permissions/matching-context? #{:sent :submitted}
                                            :draft))))
  (testing "Function matcher with match"
    (is (permissions/matching-context? keyword?
                                       :sent)))
  (testing "Simple map matcher with match"
    (is (permissions/matching-context? {:state #{:sent :submitted}}
                                       {:state :sent})))
  (testing "Simple map matcher without match"
    (is (not (permissions/matching-context? {:state #{:sent :submitted}}
                                            {:state :draft}))))
  (testing "Empty map matcher"
    (is (permissions/matching-context? {}
                                       {:any {:command :context}})))
  (testing "Context matcher is nil"
    (is (permissions/matching-context? nil
                                       {:any {:command :context}})))
  (testing "Simple map/function matcher with match"
    (is (permissions/matching-context? {:state keyword?}
                                       {:state :sent})))
  (testing "Simple map/function matcher without match"
    (is (not (permissions/matching-context? {:state string?}
                                            {:state :draft}))))
  (testing "Two-stage map matcher with match"
    (is (permissions/matching-context? {:application {:state #{:sent :submitted}}}
                                       {:application {:state :submitted}})))
  (testing "Two-stage map matcher without match"
    (is (not (permissions/matching-context? {:application {:state #{:sent :submitted}}}
                                            {:application {:state :draft}}))))
  (testing "Two-stage map matcher with match and wider context"
    (is (permissions/matching-context? {:application {:permitType #{:R :P}}}
                                       {:application {:state :submitted :permitType :R}})))
  (testing "Two-stage map wide matcher with match"
    (is (permissions/matching-context? {:application {:permitType #{:R :P} :state #{:sent :submitted}}}
                                       {:application {:state :submitted :permitType :R}})))
  (testing "Two-stage map even wider matcher with match"
    (is (permissions/matching-context? {:application {:permitType #{:R :P} :state #{:sent :submitted}} :attachment {:applicationState #{:sent}}}
                                       {:application {:state :submitted :permitType :R} :attachment {:applicationState :sent}})))
  (testing "Two-stage map even wider matcher with non-matching state in application context"
    (is (not (permissions/matching-context? {:application {:permitType #{:R :P} :state #{:sent :submitted}} :attachment {:applicationState #{:sent}}}
                                            {:application {:state :verdictGiven :permitType :R} :attachment {:applicationState :sent}})))))

(deftest get-required-permissions-test
  (testing "one simple matcher"
    (is (= {:context  {:application {:permitType #{:YA}}}
            :required [:application/modify :attachment/ya-delete]}
           (permissions/get-required-permissions {:permissions [{:context  {:application {:permitType #{:YA}}}
                                                                 :required [:application/modify :attachment/ya-delete]}]}
                                                 {:application {:permitType :YA}}))))
  (testing "one simple matcher without a match"
    (is (= {:required [:global/not-allowed]}
           (permissions/get-required-permissions {:permissions [{:context  {:application {:permitType #{:YA}}}
                                                                 :required [:application/modify :attachment/ya-delete]}]}
                                                 {:application {:permitType "R"}}))))
  (testing "multiple matchers - first match is returned - case YA"
    (is (= {:context  {:application {:permitType #{:YA}}}
            :required [:application/modify :attachment/ya-delete]}
           (permissions/get-required-permissions {:permissions [{:context  {:application {:state #{:canceled}}}
                                                                 :required [:application/modify]}
                                                                {:context  {:application {:permitType #{:YA}}}
                                                                 :required [:application/modify :attachment/ya-delete]}
                                                                {:context  {:application {:state #{:sent :submitted}}}
                                                                 :required [:application/modify :attachment/delete]}
                                                                {:context  {:application {:state #{:verdictGiven}}
                                                                            :attachment  {:applicationState #{:verdictGiven}}}
                                                                 :required [:attachment/delete]}]}
                                                 {:application {:permitType :YA :state :sent}}))))
  (testing "multiple matchers - first match is returned - case R"
    (is (= {:context  {:application {:state #{:sent :submitted}}}
            :required [:application/modify :attachment/delete]}
           (permissions/get-required-permissions {:permissions [{:context  {:application {:state #{:canceled}}}
                                                                 :required [:application/modify]}
                                                                {:context  {:application {:permitType #{:YA}}}
                                                                 :required [:application/modify :attachment/ya-delete]}
                                                                {:context  {:application {:state #{:sent :submitted}}}
                                                                 :required [:application/modify :attachment/delete]}
                                                                {:context  {:application {:state #{:verdictGiven}}
                                                                            :attachment  {:applicationState #{:verdictGiven}}}
                                                                 :required [:attachment/delete]}]}
                                                 {:application {:permitType :R :state :sent}}))))
  (testing "multiple matchers - first match is returned - case R / verdictGiven"
    (is (= {:context  {:application {:state #{:verdictGiven}}
                       :attachment  {:applicationState #{:verdictGiven}}}
            :required [:attachment/delete]}
           (permissions/get-required-permissions {:permissions [{:context  {:application {:state #{:canceled}}}
                                                                 :required [:application/modify]}
                                                                {:context  {:application {:permitType #{:YA}}}
                                                                 :required [:application/modify :attachment/ya-delete]}
                                                                {:context  {:application {:state #{:sent :submitted}}}
                                                                 :required [:application/modify :attachment/delete]}
                                                                {:context  {:application {:state #{:verdictGiven}}
                                                                            :attachment  {:applicationState #{:verdictGiven}}}
                                                                 :required [:attachment/delete]}]}
                                                 {:application {:permitType :R :state :verdictGiven}
                                                  :attachment  {:applicationState :verdictGiven}})))))

(deftest asiointiapi-permissions-test
  (let [reader-permissions (permissions/get-permissions-by-role :integration :api-reader)
        writer-permissions (permissions/get-permissions-by-role :integration :api-writer)]
    (testing "writers include all permissions of readers"
      (is (set/superset? writer-permissions
                         reader-permissions)))))
