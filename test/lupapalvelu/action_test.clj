(ns lupapalvelu.action-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [lupapalvelu.action :as action :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.lockdown :as lockdown]
            [lupapalvelu.permissions :as permissions]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.server]
            [lupapalvelu.states :as states]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [schema.core :as sc])
  (:import [org.apache.commons.io.output NullWriter]))

(set! *warn-on-reflection* true)

(defn returns []
  (ok))
(def ok-status {:ok true})
(def fail-status {:ok false})
(defn localization-error [params]
  {:ok false :text "error.illegal-localization-value" :parameters params})

(testable-privates lupapalvelu.action has-required-user-role and-2-pre-check run)

(defcommand "test-command"
  {:description "jabba"
   :states      states/all-states
   :user-roles  #{:anonymous}}
  [_]
  (returns))

(defcommand "test-authority-admin-command"
  {:description "vader"
   :parameters  [:organizationId]
   :permissions [{:required [:organization/admin]}]}
  [_]
  (returns))

(facts "get-meta"
  (get-meta "test-command") => (contains {:description "jabba"}))

(fact "masked"
  (fact "returns normal data as-is"
    (masked {:data {:a 1}}) => {:data {:a 1}})
  (fact "masks passwords"
    (masked {:data {:password    "salainen"
                    :newPassword "salainen"
                    :oldPassword "salainen"}})
    => {:data {:password    "*****"
               :oldPassword "*****"
               :newPassword "*****"}}))

(facts "Test has-required-user-role"
  (fact
    (has-required-user-role {:user {:role "foo"}}
                            {:user-roles #{:foo :bar}})
    => truthy)
  (fact
    (has-required-user-role {:user {:role "bar"}}
                            {:user-roles #{:foo :bar}})
    => truthy)
  (fact
    (has-required-user-role {:user {:role "boz"}}
                            {:user-roles #{:foo :bar}})
    => falsey))

(fact "Financial authority have applicant user role when command takes id as parameter"
  (fact
    (has-required-user-role {:user {:role "financialAuthority"}}
                            {:user-roles #{:applicant}
                             :parameters [:id]})
    => truthy)
  (fact
    (has-required-user-role {:user {:role "financialAuthority"}}
                            {:user-roles #{:applicant}
                             :parameters ["id"]})
    => truthy)
  (fact
    (has-required-user-role {:user {:role "financialAuthority"}}
                            {:user-roles #{:applicant}
                             :parameters [:some-other]})
    => falsey)
  (fact
    (has-required-user-role {:user {:role "notFinancialAuthority"}}
                            {:user-roles #{:applicant}
                             :parameters [:id]})
    => falsey)
  (fact
    (has-required-user-role {:user {:role "financialAuthority"}}
                            {:user-roles #{:authority}
                             :parameters [:id]})
    => falsey))

(facts "Test missing-fields"
  (fact
    (missing-fields {:data {:foo "Foo" :bar "Bar"}}
                    {:parameters [:foo :bar]})
    => empty?)
  (fact
    (missing-fields {:data {:foo "Foo" :bozo "Bozo"}}
                    {:parameters [:foo :bar]})
    => (contains "bar"))
  (fact
    (missing-fields {:data {}}
                    {:parameters [:foo :bar]})
    => (just ["foo" "bar"] :in-any-order))
  (fact
    (missing-fields {:data {:foo "Foo"}}
                    {})
    => empty?)
  (fact
    (missing-fields {:data {:foo "Foo" "_" "Bar"}}
                    {:parameters [:foo]})
    => empty?))

(facts "Test general validation in command execution"
  (against-background
    (get-actions) => {:test-command {:parameters [:id]
                                     :states     states/all-states
                                     :user-roles #{:authority}}}
    (lockdown/lockdown?) => false)

  (fact
    (execute {})
    => (contains {:ok false}))

  (fact
    (execute {:action "foobar"})
    => (contains {:ok false}))

  (fact
    (execute {:action "test-command"
              :user   {:role :applicant}})
    => unauthorized)

  (fact
    (execute {:action "test-command"
              :user   {:role :authority}})
    => (contains {:ok   false
                  :text "error.missing-parameters"})))

(facts "Test general validation in command execution"
  (against-background [(lockdown/lockdown?) => false]
    (fact
        (execute {:action "test-command"
                  :system {}})
      => {:ok false :text "too busy"}
      (provided (returns) => {:ok false :text "too busy"}))
    (fact
        (binding [*err* (NullWriter.)]
          (execute {:action "test-command"}))
      => {:ok false :text "error.unknown"}
      (provided (returns) =throws=> (Exception. "This was expected.")))
    (fact "actions may not return `nil`."
      (execute {:action "test-command"
                :system {}})
      => {:ok   false
          :text "error.unknown"}
      (provided (returns) => nil))))

(facts "Test valid-states"
  (against-background
    (get-actions)
    => {:test-command {:parameters [:id]
                       :states     #{:open}}})
  (fact "invalid state"
    (invalid-state-in-application {:action "test-command"
                                   :data   {:id "123"}}
                                  {:state "closed"})
    => (contains {:ok false, :text "error.command-illegal-state"}))

  (fact "valid state"
    (invalid-state-in-application {:action "test-command"
                                   :data   {:id "123"}}
                                  {:state "open"})
    => nil))

(facts "Test authority"
  (against-background
    (get-actions) => {:test-command-auth {:parameters      [:id]
                                          :user-roles      #{:authority}
                                          :org-authz-roles #{:authority}
                                          :permissions     [{:required []}]
                                          :states          states/all-states}}
    (lockdown/lockdown?) => false
    (domain/get-application-as "123"
                               {:id       "user123"
                                :orgAuthz {:ankkalinna #{:authority}}
                                :role     :authority}
                               :include-canceled-apps? true)
    => {:state "submitted" :organization "ankkalinna"}
    (domain/get-application-as "123"
                               {:id       "user123"
                                :orgAuthz {:hanhivaara #{:authority}}
                                :role     :authority}
                               :include-canceled-apps? true)
    => nil)

  (fact "regular user is not authority"
    (execute {:action "test-command-auth"
              :user   {:id "user123"}
              :data   {:id "123"}
              :system {}})
    => unauthorized)

  (fact "with correct authority command is executed"
    (execute {:action "test-command-auth"
              :user   {:id       "user123"
                       :orgAuthz {:ankkalinna #{:authority}}
                       :role     :authority}
              :data   {:id "123"}
              :system {}})
    => ok?)

  (fact "with incorrect authority error is returned"
    (execute {:action "test-command-auth"
              :user   {:id       "user123"
                       :orgAuthz {:hanhivaara #{:authority}}
                       :role     :authority}
              :data   {:id "123"}
              :system {}})
    => not-accessible))

(fact "Authority with no org and incorrect role has no access"
  (against-background (lockdown/lockdown?) => false)
  (execute {:action "test-command-auth"
            :user   {:id   "user234"
                     :role :authority}
            :data   {:id "123"}
            :system {}})
  => unauthorized
  (provided
    (get-actions) => {:test-command-auth {:parameters      [:id]
                                          :user-roles      #{:authority}
                                          :org-authz-roles #{:authority}
                                          :permissions     [{:required []}]
                                          :states          states/all-states}}
    (domain/get-application-as "123" {:id   "user234"
                                      :role :authority}
                               :include-canceled-apps? true)
    => {:organization "999-R"
        :state        "submitted"
        :auth         [{:id "user234" :role "otherRole"}]}))

(fact "Authority from same org has access"
  (against-background (lockdown/lockdown?) => false)
  (execute {:action "test-command"
            :user   {:id   "user123"
                     :role :authority}
            :data   {:id "123"}
            :system {}})
  => ok?
  (provided
    (get-actions) => {:test-command {:parameters       [:id]
                                     :user-roles       #{:authority}
                                     :org-authz-roles  #{:authority}
                                     :permissions      [{:required []}]
                                     :states           states/all-states
                                     :user-authz-roles #{:someRole}}}
    (domain/get-application-as "123"
                               {:id   "user123"
                                :role :authority}
                               :include-canceled-apps? true)
    => {:organization "999-R"
        :state        "submitted"
        :auth         [{:id "user123" :role "someRole"}]}))

(fact "Non-authority from same org has no access"
  (against-background (lockdown/lockdown?) => false)
  (execute {:action "test-command"
            :user   {:id       "some1"
                     :orgAuthz {:999-R #{:authority}}
                     :role     :applicant}
            :data   {:id "app-id"}
            :system {}})
  => unauthorized
  (provided
    (get-actions) => {:test-command {:parameters      [:id]
                                     :user-roles      #{:authority}
                                     :org-authz-roles #{:authority}
                                     :permissions     [{:required []}]}}))

(fact "Authority with no org but correct role has access"
  (against-background (lockdown/lockdown?) => false)
  (execute {:action "test-command"
            :user   {:id   "user123"
                     :role :authority}
            :data   {:id "123"}
            :system {}})
  => ok?
  (provided
    (get-actions)
    => {:test-command {:parameters       [:id]
                       :user-roles       #{:authority}
                       :org-authz-roles  #{:authority}
                       :permissions      [{:required []}]
                       :states           states/all-states
                       :user-authz-roles #{:someRole}}}
    (domain/get-application-as "123"
                               {:id   "user123"
                                :role :authority}
                               :include-canceled-apps? true)
    => {:organization "999-R"
        :state        "submitted"
        :auth         [{:id "user123" :role "someRole"}]}))

(fact "Authority with no org and writer role in auth array has access"
  (against-background (lockdown/lockdown?) => false)
  (execute {:action "with-default-roles"
            :user   {:id   "user345"
                     :role :authority}
            :data   {:id "123"}
            :system {}})
  => ok?
  (provided
    (get-actions)
    => {:with-default-roles {:parameters       [:id]
                             :user-roles       #{:authority}
                             :org-authz-roles  #{:authority}
                             :permissions      [{:required []}]
                             :states           states/all-states
                             :user-authz-roles roles/default-authz-writer-roles}}
    (domain/get-application-as "123"
                               {:id   "user345"
                                :role :authority}
                               :include-canceled-apps? true)
    => {:organization "999-R"
        :state        "submitted"
        :auth         [{:id "user345" :role "writer"}]}))

(fact "Authority with no org and non-writer role in auth array has no access"
  (against-background
    (get-actions)
    => {:with-default-roles {:parameters       [:id]
                             :user-roles       #{:authority}
                             :org-authz-roles  #{:authority}
                             :permissions      [{:required []}]
                             :states           states/all-states
                             :user-authz-roles roles/default-authz-writer-roles}}
    (domain/get-application-as "123" {:id   "user456"
                                      :role :authority}
                               :include-canceled-apps? true)
    => {:organization "999-R"
        :state        "submitted"
        :auth         [{:id "user456" :role "3rdRole"}]}
    (lockdown/lockdown?) => false)

  (execute {:action "with-default-roles"
            :user   {:id   "user456"
                     :role :authority}
            :data   {:id "123"}})
  => unauthorized)

(fact "authorityAdmin action permissions"
  (against-background [(lockdown/lockdown?) => false]
    (fact "applicant can not access"
      (execute {:action "test-authority-admin-command"
                :user   {:id   "user123"
                         :role :applicant}
                :data   {:organizationId "753-R"}
                :system {}}) => unauthorized)

    (fact "authority from same org can not access"
      (execute {:action "test-authority-admin-command"
                :user   {:id       "user123"
                         :role     :authority
                         :orgAuthz {:753-R #{:authority}}}
                :data   {:organizationId "753-R"}
                :system {}}) => unauthorized)

    (fact "authorityAdmin from different org can not access"
      (execute {:action "test-authority-admin-command"
                :user   {:id       "user123"
                         :role     :authority
                         :orgAuthz {:753-YA #{:authorityAdmin}}}
                :data   {:organizationId "753-R"}
                :system {}}) => unauthorized)

    (fact "authorityAdmin from same org"
      (fact "can access with :organizationId parameter"
        (execute {:action "test-authority-admin-command"
                  :user   {:id       "user123"
                           :role     :authority
                           :orgAuthz {:753-R #{:authorityAdmin}}}
                  :data   {:organizationId "753-R"}
                  :system {}}) => ok?)

      (fact "can not access without :organizationId parameter"
        (execute {:action "test-authority-admin-command"
                  :user   {:id       "user123"
                           :role     :authority
                           :orgAuthz {:753-R #{:authorityAdmin}}}
                  :data   {}
                  :system {}}) => {:ok false, :text "error.missing-parameters", :parameters ["organizationId"]}))))

(facts access-denied-by-insufficient-permissions
  (fact "no required permissions - default case when :permissions is not defined for action"
    (access-denied-by-insufficient-permissions {:action      :test-action
                                                :permissions #{}})
    => falsey
    (provided (meta-data {:action      :test-action
                          :permissions #{}})
              => {:permissions [{:required #{}}]}))

  (fact "insufficient permissions"
    (access-denied-by-insufficient-permissions {:action      :test-action
                                                :permissions #{}})
    => unauthorized
    (provided (meta-data {:action      :test-action
                          :permissions #{}})
              => {:permissions [{:required [:global/test]}]}))

  (fact "has required permissions"
    (access-denied-by-insufficient-permissions {:action      :test-action
                                                :permissions #{:global/test}})
    => falsey
    (provided (meta-data {:action      :test-action
                          :permissions #{:global/test}})
              => {:permissions [{:required [:global/test]}]})))

(facts "Parameter validation"
  (against-background (get-actions) => {:test-command {:parameters [:id]}})
  (fact
    (missing-parameters {:action "test-command"
                         :data   {}})
    => {:ok         false
        :parameters ["id"]
        :text       "error.missing-parameters"})
  (fact
    (missing-parameters {:action "test-command"
                         :data   {:id 1}})
    => nil)
  (fact
    (missing-parameters {:action "test-command"
                         :data   {:id 1 :_ 2}})
    => nil))

(facts "Permissions check is run"
  (against-background
    (get-actions) => {:test-command1 {:pre-checks      [(constantly nil)]
                                      :user-roles      #{:authority}
                                      :org-authz-roles #{:authority}
                                      :permissions     [{:required [:test/do]}]}

                      :test-command2 {:pre-checks      [(constantly nil)]
                                      :user-roles      #{:authority}
                                      :org-authz-roles #{:authority}
                                      :permissions     [{:required [:test/fail :test/do]}]}

                      :test-command3 {:pre-checks      [(constantly nil)]
                                      :user-roles      #{:authority}
                                      :org-authz-roles #{:authority}
                                      :permissions     [{:required []}]}}
    (domain/get-application-as "123"
                               {:id       "user123"
                                :orgAuthz {:ankkalinna #{:authority}}
                                :role     :authority}
                               :include-canceled-apps? true)
    => {:organization "ankkalinna"
        :state        "submitted"}
    (lockdown/lockdown?) => false)

  (fact "test-command1 - has required permissions"
    (execute {:action "test-command1"
              :user   {:id       "user123"
                       :orgAuthz {:ankkalinna #{:authority}}
                       :role     :authority}
              :data   {:id "123"}
              :system {}})
    => ok?
    (provided (permissions/get-permissions-by-role :global :authority) => #{})
    (provided (permissions/get-permissions-by-role :organization :authority) => #{:test/do}))

  (fact "test-command2 - missing required permission"
    (execute {:action "test-command2"
              :user   {:id       "user123"
                       :orgAuthz {:ankkalinna #{:authority}}
                       :role     :authority}
              :data   {:id "123"}
              :system {}})
    => {:ok   false
        :text "error.unauthorized"}
    (provided (permissions/get-permissions-by-role :global :authority) => #{})
    (provided (permissions/get-permissions-by-role :organization :authority) => #{:test/do}))

  (fact "test-command3 - no required permissions for command"
    (execute {:action "test-command3"
              :user   {:id       "user123"
                       :orgAuthz {:ankkalinna #{:authority}}
                       :role     :authority}
              :data   {:id "123"}
              :system {}})
    => ok?
    (provided (permissions/get-permissions-by-role :global :authority) => #{})
    (provided (permissions/get-permissions-by-role :organization :authority) => #{:test/do})))

(facts "Custom pre-check is run"
  (against-background
    (get-actions) => {:test-command1 {:pre-checks      [(constantly (fail "FAIL"))]
                                      :user-roles      #{:authority}
                                      :org-authz-roles #{:authority}
                                      :permissions     [{:required []}]}
                      :test-command2 {:pre-checks      [(constantly nil)]
                                      :user-roles      #{:authority}
                                      :org-authz-roles #{:authority}
                                      :permissions     [{:required []}]}
                      :test-command3 {:pre-checks      [(constantly nil) (constantly nil) (constantly (fail "FAIL"))]
                                      :user-roles      #{:authority}
                                      :org-authz-roles #{:authority}
                                      :permissions     [{:required []}]}}
    (domain/get-application-as "123" {:id       "user123"
                                      :orgAuthz {:ankkalinna #{:authority}}
                                      :role     :authority}
                               :include-canceled-apps? true)
    => {:organization "ankkalinna"
        :state        "submitted"}
    (lockdown/lockdown?) => false)

  (fact
    (execute {:action "test-command1"
              :user   {:id       "user123"
                       :orgAuthz {:ankkalinna #{:authority}}
                       :role     :authority}
                :data   {:id "123"}
                :system {}})
    => {:ok   false
        :text "FAIL"})
  (fact
    (execute {:action "test-command2"
              :user   {:id       "user123"
                       :orgAuthz {:ankkalinna #{:authority}}
                       :role     :authority}
                :data   {:id "123"}
                :system {}})
    => ok?)
  (fact
    (execute {:action "test-command3"
              :user   {:id       "user123"
                       :orgAuthz {:ankkalinna #{:authority}}
                       :role     :authority}
                :data   {:id "123"}
                :system {}})
    => {:ok   false
        :text "FAIL"}))

(facts "Custom input-validator is run"
  (against-background
    (get-actions) => {:test-command1 {:input-validators [(constantly (fail "FAIL"))]
                                      :user-roles       #{:authority}
                                      :org-authz-roles  #{:authority}
                                      :permissions      [{:required []}]}
                      :test-command2 {:input-validators [(constantly nil)]
                                      :user-roles       #{:authority}
                                      :org-authz-roles  #{:authority}
                                      :permissions      [{:required []}]}
                      :test-command3 {:input-validators [(constantly nil) (constantly nil) (constantly (fail "FAIL"))]
                                      :user-roles       #{:authority}
                                      :org-authz-roles  #{:authority}
                                      :permissions      [{:required []}]}}
    (domain/get-application-as "123"
                               {:id       "user123"
                                :orgAuthz {:ankkalinna #{:authority}}
                                :role     :authority}
                               :include-canceled-apps? true)
    => {:organization "ankkalinna"
        :state        "submitted"}
    (lockdown/lockdown?) => false)

  (fact
    (execute {:action "test-command1"
              :user   {:id       "user123"
                       :orgAuthz {:ankkalinna #{:authority}}
                       :role     :authority}
                :data   {:id "123"}
                :system {}})
    => {:ok false :text "FAIL"})
  (fact
    (execute {:action "test-command2"
              :user   {:id       "user123"
                       :orgAuthz {:ankkalinna #{:authority}}
                       :role     :authority}
                :data   {:id "123"}
                :system {}})
    => ok?)
  (fact
    (execute {:action "test-command3"
              :user   {:id       "user123"
                       :orgAuthz {:ankkalinna #{:authority}}
                       :role     :authority}
                :data   {:id "123"}
                :system {}})
    => {:ok false :text "FAIL"}))

(facts "Input-validator is not run during auth check"
  (against-background
    (get-actions) => {:test-command1 {:input-validators [(constantly (fail "FAIL"))]
                                      :user-roles       #{:authority}
                                      :org-authz-roles  #{:authority}
                                      :permissions      [{:required []}]}}
    (domain/get-application-as "123"
                               {:id       "user123"
                                :orgAuthz {:ankkalinna #{:authority}}
                                :role     :authority}
                               :include-canceled-apps? true)
    => {:organization "ankkalinna"
        :state        "submitted"}
    (lockdown/lockdown?) => false)
  (validate {:action "test-command1"
             :user   {:id       "user123"
                      :orgAuthz {:ankkalinna #{:authority}}
                      :role     :authority}
             :data   {:id "123"}
             :system {}})
  => ok?)

(facts "Defined querys work only in query pipelines"
  (against-background (get-actions) => {:test-command {:type        :query
                                                       :user-roles  #{:anonymous}
                                                       :permissions [{:required []}]}}
    (lockdown/lockdown?) => false)
  (fact (execute {:action "test-command" :system {}}) => ok?)
  (fact (execute {:action "test-command" :system {} :type :query}) => ok?)
  (fact (execute {:action "test-command" :system {} :type :command}) => {:ok   false
                                                              :text "error.invalid-type"})
  (fact (execute {:action "test-command" :system {} :type :raw}) => {:ok   false
                                                          :text "error.invalid-type"}))

(facts "Defined commands work only in command pipelines"
  (against-background (get-actions) => {:test-command {:type        :command
                                                       :user-roles  #{:anonymous}
                                                       :permissions [{:required []}]}}
    (lockdown/lockdown?) => false)
  (fact (execute {:action "test-command" :system {}}) => ok?)
  (fact (execute {:action "test-command" :system {} :type :query}) => {:ok   false
                                                            :text "error.invalid-type"})
  (fact (execute {:action "test-command" :system {} :type :command}) => ok?)
  (fact (execute {:action "test-command" :system {} :type :raw}) => {:ok   false
                                                          :text "error.invalid-type"}))

(facts "Defined raws work only in raw pipelines"
  (against-background (get-actions) => {:test-command {:type        :raw
                                                       :user-roles  #{:anonymous}
                                                       :permissions [{:required []}]}}
    (lockdown/lockdown?) => false)
  (fact (execute {:action "test-command" :system {}}) => ok?)
  (fact (execute {:action "test-command" :system {} :type :query}) => {:ok   false
                                                            :text "error.invalid-type"})
  (fact (execute {:action "test-command" :system {} :type :command}) => {:ok   false
                                                              :text "error.invalid-type"})
  (fact (execute {:action "test-command" :system {} :type :raw}) => ok?))

(fact "fail! stops the press"
  (against-background (get-actions) => {:failing {:handler     (fn [_] (fail! "kosh"))
                                                  :user-roles  #{:anonymous}
                                                  :permissions [{:required []}]}}
    (lockdown/lockdown?) => false)
  (execute {:action "failing" :system {}}) => {:ok false :text "kosh"})

(fact "exception details are not returned"
  (against-background (get-actions) => {:failing {:handler     (fn [_] (throw (RuntimeException. "kosh")))
                                                  :user-roles  #{:anonymous}
                                                  :permissions [{:required []}]}}
    (lockdown/lockdown?) => false)
  (execute {:action "failing" :system {}}) => {:ok false :text "error.unknown"})

(fact "schema exception"
  (against-background (get-actions) => {:failing {:handler     (fn [_] (sc/validate sc/Str 1))
                                                  :user-roles  #{:anonymous}
                                                  :permissions [{:required []}]}}
    (lockdown/lockdown?) => false)
  (execute {:action "failing" :system {}}) => {:ok false :text "error.illegal-value:schema-validation"})

(facts "non-blank-parameters"
  (non-blank-parameters nil {}) => nil
  (non-blank-parameters [] {}) => nil
  (non-blank-parameters [:foo] {:data {:foo ""}}) => (contains {:parameters [:foo]})
  (non-blank-parameters [:foo] {:data {:foo " "}}) => (contains {:parameters [:foo]})
  (non-blank-parameters [:foo] {:data {:foo " x"}}) => nil
  (non-blank-parameters [:foo :bar] {:data {:foo nil}}) => (contains {:parameters [:foo :bar]})
  (non-blank-parameters [:foo :bar] {:data {:foo "" :bar " "}}) => (contains {:parameters [:foo :bar]})
  (non-blank-parameters [:foo :bar] {:data {:foo " " :bar "x"}}) => (contains {:parameters [:foo]}))

(facts "vector-parameters-with-non-blank-items"
  (vector-parameters-with-non-blank-items [:foo] {:data {:foo ["aa"]}}) => nil
  (vector-parameters-with-non-blank-items [:foo] {:data {:foo ["aa" nil]}}) => {:ok false :text "error.vector-parameters-with-blank-items" :parameters [:foo]}
  (vector-parameters-with-non-blank-items [:foo] {:data {:foo ["aa" ""]}}) => {:ok false :text "error.vector-parameters-with-blank-items" :parameters [:foo]}
  (vector-parameters-with-non-blank-items [:foo :bar] {:data {:foo [nil] :bar [" "]}}) => {:ok false :text "error.vector-parameters-with-blank-items" :parameters [:foo :bar]}
  (vector-parameters-with-non-blank-items [:foo :bar] {:data {:foo nil :bar " "}}) => {:ok false :text "error.non-vector-parameters" :parameters [:foo :bar]})

(facts "vector-parameters-with-map-items-with-required-keys"
  (vector-parameters-with-map-items-with-required-keys [:foo] [:x :y] {:data {:foo [{:x "aa" :y nil}]}}) => nil
  (vector-parameters-with-map-items-with-required-keys [:foo] [:x] {:data {:foo nil}}) => {:ok false :text "error.non-vector-parameters" :parameters [:foo]}
  (vector-parameters-with-map-items-with-required-keys [:foo] [:x] {:data {:foo [nil]}}) => {:ok         false  :text          "error.vector-parameters-with-items-missing-required-keys"
                                                                                             :parameters [:foo] :required-keys [:x]}
  (vector-parameters-with-map-items-with-required-keys [:foo] [:x] {:data {:foo [{:y "aa"}]}}) => {:ok         false  :text          "error.vector-parameters-with-items-missing-required-keys"
                                                                                                   :parameters [:foo] :required-keys [:x]})

(fact "map-parameters-with-required-keys"
  (map-parameters-with-required-keys [:foo] [:x :y] {:data {:foo {:x "aa" :y nil}}}) => nil
  (map-parameters-with-required-keys [:foo] [:x] {:data {}}) => {:ok false :text "error.unknown-type" :parameters [:foo]}
  (map-parameters-with-required-keys [:foo] [:x] {:data {:foo nil}}) => {:ok false :text "error.unknown-type" :parameters [:foo]}
  (map-parameters-with-required-keys [:foo] [:x] {:data {:foo {:y "aa"}}}) => {:ok false :text "error.map-parameters-with-required-keys" :parameters [:foo] :required-keys [:x]})

(fact "non-empty-map-parameters"
  (non-empty-map-parameters [:foo] {:data {:foo {:hii 88}}}) => nil
  (non-empty-map-parameters [:foo] {:data {:foo {}}})
  => {:ok false :text "error.empty-map-parameters" :parameters [:foo]})

(fact "localization-parameters"
  (localization-parameters [:name] {:data {:name (i18n/supported-langs-map str)}}) => nil
  (localization-parameters [:name] {:data {:name (dissoc (i18n/supported-langs-map str) :fi)}}) => (localization-error [:name])
  (localization-parameters [:name] {:data {:name ""}}) => (localization-error [:name])
  (localization-parameters [:name] {:data {:name (assoc (i18n/supported-langs-map str) :esperanto "")}}) => (localization-error [:name]))

(fact "partial-localization-parameters"
  (partial-localization-parameters [:name] {:data {:name (i18n/supported-langs-map str)}}) => nil
  (partial-localization-parameters [:name] {:data {:name (dissoc (i18n/supported-langs-map str) :fi)}}) => nil
  (localization-parameters [:name] {:data {:name ""}}) => (localization-error [:name])
  (localization-parameters [:name] {:data {:name (assoc (i18n/supported-langs-map str) :esperanto "")}}) => (localization-error [:name]))

(fact "supported-localization-parameters"
  (supported-localization-parameters [:name] {:data {:name (i18n/supported-langs-map str)}}) => nil
  (supported-localization-parameters [:name] {:data {:name (assoc (i18n/supported-langs-map str) :esperanto "")}}) => nil
  (supported-localization-parameters [:name] {:data {:name (dissoc (i18n/supported-langs-map str) :fi)}}) => (localization-error [:name])
  (supported-localization-parameters [:name] {:data {:name (dissoc (assoc (i18n/supported-langs-map str) :esperanto "") :fi)}}) => (localization-error [:name]))

(fact "coordinate-parameters"
  (coordinate-parameters :x :y {:data {:x "10001" :y "7779999.0"}}) => nil
  (coordinate-parameters :x-coord :y-coord {:data {:x-coord "10000" :y "7779999.0"}})  => {:ok false :parameters [:x-coord] :text "error.illegal-coordinates"}
  (coordinate-parameters :x-coord :y-coord {:data {:x-coord "10001" :y "7789999.0"}})  => {:ok false :parameters [:y-coord] :text "error.illegal-coordinates"}
  (coordinate-parameters :x :y {:data {}})  => {:ok false :parameters [:x] :text "error.illegal-coordinates"}
  (coordinate-parameters :x :y {:data {:x "10001" :y "NaN"}})  => {:ok false :parameters [:y] :text "error.illegal-coordinates"})

(facts "feature requirements"
  (against-background
    (lockdown/lockdown?) => false
    (get-actions) => {:test-command1 {:feature :abba :user-roles #{:anonymous} :permissions [{:required []}]}})

  (fact "without correct feature error is given"
    (execute {:action "test-command1" :system {}}) => {:ok   false
                                            :text "error.missing-feature"}
    (provided (env/feature? :abba) => false))

  (fact "with correct feature command is executed"
    (execute {:action "test-command1" :system {}}) => ok?
    (provided (env/feature? :abba) => true)
    (provided (env/feature? :dev-mode-supported) => irrelevant)))

(facts get-post-fns
  (get-post-fns ok-status {})
  => []
  (get-post-fns fail-status {})
  => []
  (get-post-fns ok-status {:on-complete ...c...})
  => [...c...]
  (get-post-fns ok-status {:on-complete [...c1... ...c2...]})
  => [...c1... ...c2...]
  (get-post-fns ok-status {:on-complete [...c1... ...c2...] :on-success ...s...})
  => [...c1... ...c2... ...s...]
  (get-post-fns ok-status {:on-complete [...c1... ...c2...] :on-success [...s1... ...s2...]})
  => [...c1... ...c2... ...s1... ...s2...]
  (get-post-fns fail-status {:on-complete [...c1... ...c2...] :on-success [...s1... ...s2...] :on-fail ...f...})
  => [...c1... ...c2... ...f...]
  (get-post-fns fail-status {:on-complete [...c1... ...c2...] :on-success [...s1... ...s2...] :on-fail [...f1... ...f2...]})
  => [...c1... ...c2... ...f1... ...f2...])

(facts "some-pre-check"
  ((some-pre-check (constantly {:ok false :error "ok"}) (constantly nil)) {}) => nil?
  ((some-pre-check (constantly nil) (constantly {:ok false :error "ok"})) {}) => nil?
  ((some-pre-check (constantly {:ok false :error "ok"})
                   (constantly nil)
                   (constantly {:ok false :error "ok"})) {}) => nil?
  ((some-pre-check (constantly {:ok false :error "fail1"}) (constantly {:ok false :error "fail2"})) {}) => (contains {:error "fail2"})
  ((some-pre-check (constantly nil) (constantly nil)) {}) => nil?)

(facts "and-pre-check"
  (let [allways-fail (constantly {:ok false :error "error"})
        allways-pass (constantly nil)]
    (facts "and-2-pre-check"
      ((and-2-pre-check allways-pass allways-pass) {}) => nil?
      ((and-2-pre-check allways-fail allways-pass) {}) => fail?
      ((and-2-pre-check allways-pass allways-fail) {}) => fail?
      ((and-2-pre-check allways-fail allways-fail) {}) => fail?)

    (facts "and-pre-check"
      ((and-pre-check allways-pass allways-pass allways-pass) {}) => nil?
      ((and-pre-check allways-fail allways-pass allways-pass) {}) => fail?
      ((and-pre-check allways-pass allways-fail allways-pass) {}) => fail?
      ((and-pre-check allways-fail allways-fail allways-pass) {}) => fail?
      ((and-pre-check allways-pass allways-pass allways-fail) {}) => fail?
      ((and-pre-check allways-fail allways-pass allways-fail) {}) => fail?
      ((and-pre-check allways-pass allways-fail allways-fail) {}) => fail?
      ((and-pre-check allways-fail allways-fail allways-fail) {}) => fail?)

    (fact "and-pre-check failure propagation"
      ((and-pre-check (constantly nil)
                      (constantly {:ok false :error "error1"})
                      (constantly {:ok false :error "error2"}))
       {}) => {:ok                                              false :error "error1"})))

(facts "not-pre-check"
  ((not-pre-check (constantly {:ok false :error "error"})) {}) => nil?
  ((not-pre-check (constantly nil)) {}) => fail?)

(facts "only users with :organization/admin permission can execute restricted actions"
  (against-background
    (lockdown/lockdown?) => false
    (get-actions)
    => {:test-command {:type        :command
                       :permissions [{:required [:organization/admin]}]}}

    (permissions/get-permissions-by-role :global :authority)
    => #{})

  (fact
    (validate {:action "test-command"
               :user   {:id       "authority"
                        :orgAuthz {:ankkalinna #{:authority}}
                        :role     :authority}
               :data   {}})
    => {:ok false, :text "error.unauthorized"}
    (provided (permissions/get-permissions-by-role :organization :authority) => #{}))

  (fact
    (validate {:action "test-command"
               :user   {:id       "authority-admin"
                        :orgAuthz {:ankkalinna #{:authorityAdmin}}
                        :role     :authority}
               :data   {:organizationId "ankkalinna"}})
    => {:ok true}
    (provided (permissions/get-permissions-by-role :organization :authorityAdmin) => #{:organization/admin})))

(facts "input-validators-fail test"
  (against-background
    (get-actions) => {:success {:input-validators [(constantly nil)
                                                   (constantly nil)
                                                   (constantly nil)]}
                      :failing {:input-validators [(constantly nil)
                                                   (constantly {:oh "no"})
                                                   (constantly {:not "this"})]}})
  (fact
    (input-validators-fail {:action :success}) => nil)
  (fact
    (input-validators-fail {:action :failing}) => {:oh "no"}))

;;
;; Test that input-validator can be a function or schema.
;;

; Test command that has two validators, first is a function that
; returns what the command has under ::validator-response, second
; uses schema `InputValidatorTestSchema` as validator. On success
; command returns `:data` of command.

(sc/defschema InputValidatorTestSchema {:answer sc/Int})

(defcommand "input-validator-test-command"
  {:input-validators [(fn [command] (-> command ::validator-response))
                      InputValidatorTestSchema]
   :user-roles       #{:anonymous}}
  [command]
  (ok :data (-> command :data)))

(facts "input-validator can be a function or schema"
  (fact "validator returns nil, data matches schema => command is successful"
    (run {:action              :input-validator-test-command
          :data                {:answer 42}
          :system              {}
          ::validator-response nil}
      [input-validators-fail]
      true)
    => {:ok   true
        :data {:answer 42}})

  (fact "fn validator returns non-nil => command is fails"
    (run {:action              :input-validator-test-command
          :data                {:answer 42}
          :system              {}
          ::validator-response ::validator-fail}
      [input-validators-fail]
      true)
    => ::validator-fail)

  (fact "schema validation fails => command is fails"
    (run {:action              :input-validator-test-command
          :data                {:answer "foo"}
          :system              {}
          ::validator-response nil}
      [input-validators-fail]
      true)
    => {:ok   false
        :text "error.illegal-value:schema-validation"}))

(def bad-date {:ok false :text "error.invalid-date"})

(facts "date-parameter"
  (date-parameter :date nil) => bad-date
  (date-parameter :date {:data {}})  => bad-date
  (date-parameter :date {:data {:date ""}})  => bad-date
  (date-parameter :date {:data {:date "  "}})  => bad-date
  (date-parameter :date {:data {:date "foo bar"}})  => bad-date
  (date-parameter :date {:data {:date "32.6.2020"}})  => bad-date
  (date-parameter :date {:data {:date "foo12.6.2020"}})  => bad-date
  (date-parameter :date {:data {:date "12.6.2020bar"}})  => bad-date
  (date-parameter :date {:data {:date " 12.6.2020"}})  => nil
  (date-parameter :date {:data {:date "12.6.2020 "}})  => nil
  ((date-parameter :date) {:data {:date "12.6.2020 "}})  => nil
  (date-parameter :date {:data {:date "12.6.2020"}})  => nil
  ((date-parameter :date) {:data {:date "12.6.2020"}})  => nil)

(deftest request-schema-test
  (let [action-type        :command
        action-name        "test-command"
        line               1
        ns-name            "some.test.namespace-api"
        register-with-meta (fn [meta-data]
                             (action/register-action action-type
                                                     (name action-name)
                                                     meta-data
                                                     line
                                                     ns-name
                                                     identity))]
    (testing "overlapping features assert"
      (is (thrown-with-msg? AssertionError
                            #"Assert failed: Cannot define both `:\(optional-\)parameters` and request coercion for actions - test-command"
                            (register-with-meta {:permissions    [{:required []}]
                                                 :parameters     [:foo]
                                                 :request-schema {:foo sc/Str}})))
      (is (thrown-with-msg? AssertionError
                            #"Assert failed: Cannot define both `:\(optional-\)parameters` and request coercion for actions - test-command"
                            (register-with-meta {:permissions         [{:required []}]
                                                 :optional-parameters [:foo]
                                                 :request-schema      {:foo sc/Str}})))
      (is (thrown-with-msg? AssertionError
                            #"Assert failed: Cannot define both `:input-validators` schemas and request coercion for actions - test-command"
                            (register-with-meta {:permissions      [{:required []}]
                                                 :input-validators [{:foo sc/Str}]
                                                 :request-schema   {:foo sc/Str}})))
      (testing "non-schema input-validators are fine"
        (is (some? (register-with-meta {:permissions      [{:required []}]
                                        :input-validators [(constantly true)]
                                        :request-schema   {:foo sc/Str}})))))
    (testing "plumatic schema"
      (testing "id is detected in schema"
        (is (thrown-with-msg? AssertionError
                              #"Assert failed: You must define :permissions, :states or :pre-checks meta data for test-command if action has the :id parameter"
                              (register-with-meta {:user-roles     #{:admin}
                                                   :request-schema {:id sc/Str}})))
        (is (thrown-with-msg? AssertionError
                              #"Assert failed: You must define :permissions, :states or :pre-checks meta data for test-command if action has the :id parameter"
                              (register-with-meta {:user-roles     #{:admin}
                                                   :request-schema {(sc/optional-key :id) sc/Str}})))
        ;; `schema-tools.core/required-keys` will crash on non-map schemas
        (is (thrown-with-msg? AssertionError
                              #"Action 'test-command' has invalid meta data:"
                              (register-with-meta {:user-roles     #{:admin}
                                                   :request-schema (sc/conditional
                                                                     true? {:id sc/Str})})))))
    (testing "malli"
      (testing "id is detected in schema"
        (is (thrown-with-msg? AssertionError
                              #"Assert failed: You must define :permissions, :states or :pre-checks meta data for test-command if action has the :id parameter"
                              (register-with-meta {:user-roles    #{:admin}
                                                   :request-malli [:map [:id :string]]})))
        (is (thrown-with-msg? AssertionError
                              #"Assert failed: You must define :permissions, :states or :pre-checks meta data for test-command if action has the :id parameter"
                              (register-with-meta {:user-roles    #{:admin}
                                                   :request-malli [:map [:id {:optional true} :string]]})))
        ;; `malli.util/keys` can only read `:map` schemas
        (is (thrown-with-msg? AssertionError
                              #"Action 'test-command' has invalid meta data:"
                              (register-with-meta {:user-roles    #{:admin}
                                                   :request-malli [:and
                                                                   [:map [:id :string]]
                                                                   [:fn (constantly true)]]})))))
    (testing "coercion"
      (let [run-with-data (fn [data]
                            (run  {:action (keyword action-name)
                                   :data   data}
                                  [identity]
                                  true))]
        (testing "plumatic"
          (is (register-with-meta {:permissions    [{:required []}]
                                   :request-schema {:bool    sc/Bool
                                                    :string  sc/Str
                                                    :keyword sc/Keyword
                                                    :int     sc/Int
                                                    :vec     [sc/Keyword]
                                                    :set     #{sc/Keyword}
                                                    :s-map   {sc/Str sc/Str}
                                                    :k-map   {sc/Keyword sc/Keyword}}}))
          (is (= {:action :test-command, :data {:bool    true
                                                :string  "hello"
                                                :keyword :world
                                                :int     1337
                                                :vec     [:a]
                                                :set     #{:b}
                                                :s-map   {"hello" "world"}
                                                :k-map   {:hello :world}}}
                 (run-with-data {:bool    "true"
                                 :string  "hello"
                                 :keyword "world"
                                 :int     "1337"
                                 :vec     ["a"]
                                 :set     ["b"]
                                 :s-map   {"hello" "world"}
                                 :k-map   {"hello" "world"}})))
          (testing "unknown keys"
            (is (register-with-meta {:permissions    [{:required []}]
                                     :request-schema {:string sc/Str}}))
            (is (= {:text "error.illegal-value:schema-validation"
                    :ok   false}
                   (run-with-data {:string "hello"
                                   :foo    "false"})))))
        (testing "malli"
          (is (register-with-meta {:permissions   [{:required []}]
                                   :request-malli [:map
                                                   [:bool :boolean]
                                                   [:string :string]
                                                   [:keyword :keyword]
                                                   [:int :int]
                                                   [:vec [:sequential :keyword]]
                                                   [:set [:set :keyword]]
                                                   [:s-map [:map-of :string :string]]
                                                   [:k-map [:map-of :keyword :keyword]]]}))
          (is (= {:action :test-command, :data {:bool    true
                                                :string  "hello"
                                                :keyword :world
                                                :int     1337
                                                :vec     [:a]
                                                :set     #{:b}
                                                :s-map   {"hello" "world"}
                                                :k-map   {:hello :world}}}
                 (run-with-data {:bool    "true"
                                 :string  "hello"
                                 :keyword "world"
                                 :int     "1337"
                                 :vec     ["a"]
                                 :set     ["b"]
                                 :s-map   {"hello" "world"}
                                 :k-map   {"hello" "world"}})))
          (testing "unknown keys"
            (is (register-with-meta {:permissions   [{:required []}]
                                     :request-malli [:map [:string :string]]}))
            (is (= {:text "error.illegal-value:schema-validation"
                    :ok   false}
                   (run-with-data {:string "hello"
                                   :foo    "false"})))))))

    (swap! action/actions dissoc (keyword action-name))))

(deftest action-permissions-test
  (let [params-per-action    (reduce (fn [acc [action-name {:keys [ns] :as meta-data}]]
                                       ;; try to ignore actions which look like they were defined within a test
                                       ;; or only apply in specific modes (ie. dev)
                                       (if (or (ss/includes-i? ns "test")
                                               (#{:last-email :sent-emails
                                                  :dummy-cancel-sign-document
                                                  :dummy-find-sign-process
                                                  :dummy-sign-document}
                                                action-name))
                                         acc
                                         (assoc acc action-name (action/parameters meta-data))))
                                     {}
                                     (action/get-actions))
        has-param?           (fn [param-pred]
                               (->> params-per-action
                                    (filter (fn [[_ parameters]] (param-pred parameters)))
                                    (map first)
                                    (into #{})))
        application-actions  (has-param? (fn [param] (contains? param :id)))
        organization-actions (has-param? (fn [params] (contains? params :organizationId)))
        general-actions      (has-param? (fn [params] (and (not (contains? params :organizationId))
                                                           (not (contains? params :id)))))]
    (testing "actions targeting an organization"
      (let [expected #{:ad-login-enabled
                       :ad-login-role-mapping
                       :add-organization-automatic-email-template
                       :add-organization-link
                       :admin-test-ryhti-api
                       :all-operations-for-organization
                       :applications-between-xlsx
                       :arcgis-config
                       :archival-report
                       :authorities-report
                       :automatic-assignment-authorities
                       :available-tos-functions
                       :biller-invoices
                       :biller-transfer-batches
                       :bim-checking-plans-for-org
                       :buildings
                       :bulletin-report
                       :change-handler-applications
                       :close-transfer-batch
                       :configure-invoicing-backend-id
                       :convert-to-normal-inforequests
                       :copy-verdict-template
                       :create-application-from-previous-permit
                       :create-application-submit-assurance-template
                       :create-archiving-project
                       :create-inspection-summary-template
                       :create-review-officer
                       :create-statement-giver
                       :custom-organization-phrase-categories
                       :default-operation-verdict-templates
                       :delete-automatic-assignment-filter
                       :delete-dmcity-configuration
                       :delete-inspection-summary-template
                       :delete-phrase
                       :delete-phrase-category
                       :delete-price-catalogue
                       :delete-review-officer
                       :delete-stamp-template
                       :delete-statement-giver
                       :delete-transfer-batch
                       :dmcity-configuration
                       :dmcity-configuration-flags
                       :docdepartmental-enabled
                       :docstore-enabled
                       :docterminal-attachment-types
                       :docterminal-enabled
                       :document-request-info
                       :download-organization-attachments-export-file
                       :download-transfer-batch-excel
                       :download-transfer-batch-xml
                       :edit-review-officer
                       :edit-statement-giver
                       :evaluate-password
                       :get-import-config
                       :get-map-layers-data
                       :get-oauth-users
                       :get-organization-application-states
                       :get-organization-automatic-email-templates
                       :get-organization-suomifi-attachments
                       :get-organization-tags
                       :get-organizations-review-officers
                       :get-organizations-statement-givers
                       :get-organizations-suomifi-messages-enabled
                       :get-permanent-building-permit-id
                       :guest-authorities-organization
                       :impersonate-authority
                       :integration-config
                       :invoices-between-xlsx
                       :invoicing-config
                       :kopiolaitos-config
                       :krysp-config
                       :login-required-for-previews
                       :modify-inspection-summary-template
                       :move-price-catalogue-row
                       :new-price-catalogue-draft
                       :new-transfer-batch
                       :new-verdict-template
                       :onkalo-applications-xlsx
                       :open-applications-xlsx
                       :open-transfer-batch
                       :organization-area
                       :organization-attachment-types
                       :organization-authorities
                       :organization-by-id
                       :organization-by-user
                       :organization-import-configs
                       :organization-inspection-summary-settings
                       :organization-jobs
                       :organization-name-by-user
                       :organization-operations-attachments
                       :organization-phrases
                       :organization-price-catalogue
                       :organization-price-catalogues
                       :parties-between-xlsx
                       :pate-enabled
                       :permanent-archive-enabled
                       :post-verdict-xlsx
                       :process-transfer-batch-integration
                       :pseudo-organization-area
                       :publish-price-catalogue
                       :publish-verdict-template
                       :remove-application-submit-assurance-template
                       :remove-building
                       :remove-guest-authority-organization
                       :remove-organization-automatic-email-template
                       :remove-organization-link
                       :remove-organization-local-bulletins-caption
                       :remove-tag-ok
                       :remove-tos-function-from-operation
                       :remove-user-organization
                       :rename-transfer-batch
                       :reset-bulletin-text-settings
                       :resolve-guest-authority-candidate
                       :revert-price-catalogue-to-draft
                       :reviews-period-report
                       :ryhti-api-configuration
                       :save-application-submit-assurance-template
                       :save-arcgis-config
                       :save-integration-config
                       :save-no-billing-periods
                       :save-organization-automatic-email-template-field
                       :save-organization-tags
                       :save-phrase-category
                       :save-vendor-backend-redirect-config
                       :save-verdict-template-draft-value
                       :save-verdict-template-settings-value
                       :section-toggle-enabled
                       :section-toggle-operation
                       :selectable-verdict-templates
                       :send-messages-to-dmcity
                       :set-3d-map-enabled
                       :set-automatic-construction-started
                       :set-automatic-ok-for-attachments
                       :set-default-digitalization-location
                       :set-default-operation-verdict-template
                       :set-dmcity-configuration-flags
                       :set-dmcity-url
                       :set-docdepartmental-attachment-type
                       :set-docterminal-attachment-type
                       :set-document-request-info
                       :set-foreman-submit-setting
                       :set-kopiolaitos-info
                       :set-krysp-endpoint
                       :set-only-use-inspection-from-backend
                       :set-organization-app-required-fields-filling-obligatory
                       :set-organization-assignments
                       :set-organization-boolean-attribute
                       :set-organization-boolean-path
                       :set-organization-earliest-allowed-archiving-date
                       :set-organization-extended-construction-waste-report
                       :set-organization-force-kuntagml-226-for-pre-2025
                       :set-organization-foreman-termination-request-enabled
                       :set-organization-funding-enabled-notification-email
                       :set-organization-handling-time
                       :set-organization-inforequest-notification-email
                       :set-organization-inspection-summaries
                       :set-organization-jpeg-archiving-enabled
                       :set-organization-login-required-for-previews
                       :set-organization-multiple-operations-support
                       :set-organization-neighbor-order-email
                       :set-organization-no-comment-neighbor-attachment-enabled
                       :set-organization-operations-allowed-attachments
                       :set-organization-permanent-archive-start-date
                       :set-organization-plan-info-disabled
                       :set-organization-ram-message
                       :set-organization-rectification-verdicts-enabled
                       :set-organization-remove-handlers-from-converted-application
                       :set-organization-remove-handlers-from-reverted-draft
                       :set-organization-review-fetch-enabled
                       :set-organization-review-officers-list-enabled
                       :set-organization-ryhti-data-2026-start-ts
                       :set-organization-selected-operations
                       :set-organization-submit-notification-email
                       :set-organization-suomifi-attachments
                       :set-organization-suomifi-message
                       :set-organization-tiff-archiving-enabled
                       :set-organization-use-attachment-links-integration
                       :set-organization-validate-verdict-bulletin-date
                       :set-organization-validate-verdict-given-date
                       :set-ryhti-api-configuration
                       :set-tos-function-for-operation
                       :set-verdict-template-name
                       :sftp-organization-configuration
                       :stamp-templates
                       :store-application-submit-assurance-order
                       :store-billing-report
                       :store-downloads-report
                       :suti-admin-details
                       :suti-operations
                       :suti-toggle-enabled
                       :suti-toggle-operation
                       :suti-www
                       :toggle-deactivation
                       :toggle-default-attachments-mandatory-operation
                       :toggle-delete-verdict-template
                       :toggle-dmcity-configuration-target
                       :toggle-handler-role
                       :toggle-organization-ram
                       :toggle-organization-suomifi-messages
                       :update-3d-map-server-details
                       :update-ad-login-role-mapping
                       :update-and-open-verdict-template
                       :update-auth-info
                       :update-bim-checking-plan-for-operation
                       :update-building
                       :update-buildings-in-archive
                       :update-guest-authority-organization
                       :update-map-server-details
                       :update-organization-bulletin-scope
                       :update-organization-link
                       :update-organization-name
                       :update-review-pdf-configuration
                       :update-sftp-organization-configuration
                       :update-suti-server-details
                       :update-user-layers
                       :update-user-oauth-roles
                       :update-user-roles
                       :upload-building-data
                       :upload-import-excel
                       :upsert-automatic-assignment-filter
                       :upsert-dmcity-configuration
                       :upsert-handler-role
                       :upsert-organization-local-bulletins-text
                       :upsert-organization-suomifi-message-attachments
                       :upsert-organization-user
                       :upsert-phrase
                       :upsert-stamp-template
                       :user-for-edit-authority
                       :user-organization-bulletin-settings
                       :users-for-datatables
                       :vendor-backend-redirect-config
                       :verdict-template
                       :verdict-template-categories
                       :verdict-template-settings
                       :verdict-templates
                       :waste-report}]
        (is (= #{} (set/difference expected organization-actions)))
        (is (= #{} (set/difference organization-actions expected)))))
    (testing "actions targeting an application"
      (let [expected #{:acknowledge-foreman
                       :add-allu-drawing
                       :add-application-tags
                       :add-authority-notice
                       :add-building-product-catalog-reused
                       :add-comment
                       :add-execution-permit
                       :add-invoice-operation
                       :add-invoice-row
                       :add-link-permit
                       :add-manual-bulletin-comment
                       :add-operation
                       :add-target-to-inspection-summary
                       :addable-operations
                       :all-operations-for-schema
                       :allu-sites
                       :answer-processing-entry
                       :answer-reply
                       :app-bulletin-op-description-required
                       :app-matches-for-link-permits
                       :appeals
                       :application
                       :application-authorities
                       :application-commenters
                       :application-copyable
                       :application-gis-data
                       :application-guests
                       :application-handlers
                       :application-has-foreman
                       :application-info-tab-visible
                       :application-invoices
                       :application-invoicing-configuration
                       :application-kinds
                       :application-operation-buildings
                       :application-organization-archive-enabled
                       :application-organization-handler-roles
                       :application-organization-tags
                       :application-phrases
                       :application-price-catalogues
                       :application-property-owners
                       :application-statement-tab-visible
                       :application-submit-assurance-templates
                       :application-submittable
                       :application-summary-tab-visible
                       :application-to-asianhallinta
                       :application-verdict-templates
                       :apply-ifc-model-data
                       :approve-application
                       :approve-application-after-verdict
                       :approve-attachment
                       :approve-doc
                       :approve-foreman
                       :approve-invite
                       :approve-notice-form
                       :approve-task
                       :approve-ya-extension
                       :arcgis-feature-by-point
                       :arcgis-feature-geometry
                       :arcgis-layer-tile
                       :archive-documents
                       :assignment-targets
                       :assignments-for-application
                       :attachment
                       :attachment-groups
                       :attachment-operations
                       :attachment-print-order-history
                       :attachment-types
                       :attachments
                       :attachments-filters
                       :attachments-for-printing-order
                       :attachments-tag-groups
                       :attachments-to-asianhallinta
                       :authority-notice
                       :bind-attachment
                       :bind-attachments
                       :bind-draft-attachments
                       :bind-filebank-files
                       :bulletin-comments
                       :bulletin-for-application-verdict-enabled
                       :bulletin-report-pdf
                       :bulletin-versions
                       :bulletin-will-be-published-for-verdict
                       :can-send-via-suomifi
                       :can-target-comment-to-authority
                       :cancel-application
                       :cancel-foreman-termination-request
                       :cancel-foreman-termination-request-v3
                       :cancel-inforequest
                       :cancel-review-request
                       :case-file-data
                       :change-address
                       :change-application-state
                       :change-bulletin-process
                       :change-foreman-doc-type
                       :change-location
                       :change-permit-sub-type
                       :change-primary-operation
                       :change-urgency
                       :change-warranty-end-date
                       :change-warranty-start-date
                       :check-for-verdict
                       :check-for-verdict-fix
                       :check-model
                       :check-verdict-for-document
                       :cleanup-krysp
                       :climate-reports-valid
                       :close-processing-entry
                       :comments
                       :common-area-application
                       :company-invite
                       :company-notes
                       :company-users-for-person-selector
                       :confirm-foreman-termination
                       :confirm-foreman-termination-v3
                       :conversion-message
                       :conversion-sections
                       :convert-to-application
                       :convert-to-pdfa
                       :copy-pate-verdict-draft
                       :copy-user-attachments-to-application
                       :create-and-invite-foreman-by-email
                       :create-and-invite-foreman-by-id
                       :create-assignment
                       :create-attachments
                       :create-change-permit
                       :create-continuation-period-permit
                       :create-digging-permit
                       :create-doc
                       :create-encumbrance-permit
                       :create-foreman-application
                       :create-inspection-summary
                       :create-manual-statement
                       :create-property-formation-app
                       :create-ram-attachment
                       :create-reply
                       :create-supplement-request
                       :create-task
                       :custom-application-phrase-categories
                       :custom-stamps
                       :declare-foreman
                       :decline-invitation
                       :delete-attachment
                       :delete-attachment-version
                       :delete-draft-notice-resolution
                       :delete-filebank-file
                       :delete-guest-application
                       :delete-inspection-summary
                       :delete-invoice
                       :delete-invoice-operation
                       :delete-invoice-row
                       :delete-linked-file
                       :delete-notice-form
                       :delete-pate-appeal
                       :delete-pate-verdict
                       :delete-published-notice-resolution
                       :delete-reply
                       :delete-statement
                       :delete-supplement-request
                       :delete-task
                       :delete-verdict
                       :demolition-notice-to-application
                       :designer-applications
                       :designer-consent
                       :designer-consent-given
                       :document
                       :document-by-name
                       :document-states
                       :download-all-attachments
                       :download-attachment
                       :download-attachments
                       :download-conversation-pdf
                       :download-filebank-file
                       :download-filebank-files
                       :download-xkt-file
                       :edit-conversion-section
                       :edit-inspection-summary-target
                       :edit-pate-verdict
                       :ely-statement-request
                       :ely-statement-types
                       :external-api-enabled
                       :external-states
                       :fetch-ifc-doc
                       :fetch-linked-file-bim-results
                       :fetch-linked-file-metadata
                       :fetch-linked-file-metadatas-for-application
                       :fetch-recipient-suomifi-data
                       :fetch-validation-errors
                       :filter-allu-drawings
                       :force-fix-tos-function-for-application
                       :foreman-applications
                       :foreman-consent
                       :foreman-consent-given
                       :foreman-documents
                       :foreman-history
                       :foreman-responsibility-timestamps
                       :foreman-verdict-templates
                       :generate-xkt-file
                       :get-assurances-for-application
                       :get-building-info-from-wfs
                       :get-filebank-files
                       :get-foreman-submit-settings
                       :get-possible-statement-statuses
                       :get-reply-target-issues
                       :get-statement-givers
                       :get-supplement-requests
                       :get-verdict-recipient
                       :give-rectification-verdict
                       :give-statement
                       :guest-authorities-application-organization
                       :ifc-data-file-version-ids
                       :info-construction-status
                       :info-link-delete
                       :info-link-reorder
                       :info-link-upsert
                       :info-links
                       :inforequest-markers
                       :inform-construction-ready
                       :inform-construction-started
                       :init-resumable-upload-for-application
                       :inspection-summaries-for-application
                       :integration-message
                       :integration-messages
                       :invite-financial-handler
                       :invite-guest
                       :invite-with-role
                       :invoice-pdf
                       :invoices-tab
                       :invoicing-backend-id-codes
                       :is-end-review
                       :is-faulty-review
                       :is-other-review
                       :issue-reply-state
                       :job-status
                       :link-foreman-task
                       :link-permit-required
                       :link-resumable-upload-for-application
                       :location-operations
                       :low-carbon-assessment
                       :mark-entries-seen
                       :mark-everything-seen
                       :mark-fully-archived
                       :mark-pre-verdict-phase-archived
                       :mark-review-faulty
                       :mark-seen
                       :mark-seen-organization-links
                       :mark-supplement-request-done
                       :merge-details-from-krysp
                       :move-attachments-to-backing-system
                       :move-to-final
                       :move-to-proclaimed
                       :move-to-verdict-given
                       :nearby-application-markers
                       :neighbor-add
                       :neighbor-add-owners
                       :neighbor-mark-done
                       :neighbor-remove
                       :neighbor-send-invite
                       :neighbor-update
                       :neighbors-replies-enabled
                       :neighbors-statement-enabled
                       :new-construction-notice-form
                       :new-invoice-draft
                       :new-legacy-foreman-verdict-draft
                       :new-legacy-verdict-draft
                       :new-location-notice-form
                       :new-notice-data
                       :new-pate-foreman-verdict-draft
                       :new-pate-verdict-draft
                       :new-processing-delay
                       :new-processing-request
                       :new-terrain-notice-form
                       :notice-forms
                       :notice-resolution
                       :notify-organizations-housing-office
                       :order-verdict-attachment-prints
                       :organization-links
                       :parties-as-krysp
                       :party-document-names
                       :pate-contract-tab
                       :pate-kuntagml-226-required
                       :pate-verdict
                       :pate-verdict-tab
                       :pate-verdicts
                       :pdf-export
                       :pdf-foreman
                       :pdf-foreman-list
                       :pdfa-casefile
                       :plan-document-infos
                       :poll-notice-resolution
                       :preview-pate-verdict
                       :preview-pate-verdict-data
                       :processing-days
                       :processing-entries
                       :processing-entries-tab
                       :processing-log-pdf
                       :processing-logdata
                       :processing-requests-status
                       :property-formation-app-invite-candidates
                       :property-info
                       :proposal-pdf
                       :publish-legacy-verdict
                       :publish-notice-resolution
                       :publish-pate-verdict
                       :publish-verdict-proposal
                       :published-pate-verdict
                       :published-verdict-attachment-ids
                       :ram-disabled-message
                       :ram-linked-attachments
                       :read-model-data
                       :read-model-data-job
                       :redirect-to-3d-map
                       :redirect-to-dmcity
                       :redirect-to-vendor-backend
                       :refresh-ktj
                       :reject-attachment
                       :reject-attachment-note
                       :reject-doc
                       :reject-doc-note
                       :reject-notice-form
                       :reject-task
                       :removable-attachment-versions
                       :remove-application-drawing
                       :remove-application-handler
                       :remove-auth
                       :remove-buildings
                       :remove-doc
                       :remove-document-data
                       :remove-financial-handler-invitation
                       :remove-foreman-from-list
                       :remove-link-permit-by-app-id
                       :remove-target-from-inspection-summary
                       :replace-operation
                       :replace-pate-verdict
                       :replies
                       :replies-connected-to-issue
                       :reply-supplement-request
                       :request-for-complement
                       :request-for-statement
                       :request-foreman-termination
                       :request-foreman-termination-v3
                       :request-review
                       :resend-review-to-backing-system
                       :reset-attachment
                       :resolve-multi-attachment-updates
                       :return-to-draft
                       :revert-verdict-proposal
                       :review-can-be-marked-done
                       :review-done
                       :rollback-scheduled-publish
                       :rotate-pdf
                       :save-application-drawings
                       :save-proclaimed-bulletin
                       :save-statement-as-draft
                       :save-statement-due-date
                       :save-verdict-given-bulletin
                       :scheduled-verdict-publish
                       :send-doc-updates
                       :send-foreman-message
                       :send-signature-request
                       :send-suomifi-verdict
                       :send-supplement-request
                       :send-verdict-messages
                       :set-archival-project-permit-date
                       :set-attachment-as-bulletin-doc
                       :set-attachment-as-construction-time
                       :set-attachment-meta
                       :set-attachment-not-needed
                       :set-attachment-target
                       :set-attachment-type
                       :set-attachment-visibility
                       :set-attachments-as-verdict-attachment
                       :set-biller-invoice-field
                       :set-building-extinct
                       :set-company-to-document
                       :set-current-user-to-document
                       :set-doc-status
                       :set-foreman-start-date
                       :set-foreman-start-date-v3
                       :set-inspection-date
                       :set-invoice-field
                       :set-invoice-row-field
                       :set-linked-application-file-metadata-field
                       :set-municipality-hears-neighbors
                       :set-myyntipalvelu-for-attachment
                       :set-operation-location
                       :set-processing-complexity
                       :set-submit-data-source
                       :set-target-status
                       :set-tos-function-for-application
                       :set-user-to-document
                       :sftp-export-enabled
                       :sign-allu-contract
                       :sign-attachments
                       :sign-pate-contract
                       :signature-request-parties
                       :stamp-attachments
                       :statement-attachment-allowed
                       :statement-replies-enabled
                       :statements-after-approve-allowed
                       :store-archival-project-backend-ids
                       :store-tos-metadata-for-application
                       :store-tos-metadata-for-attachment
                       :store-tos-metadata-for-foreman-list
                       :store-tos-metadata-for-linked-file
                       :store-tos-metadata-for-process
                       :submit-application
                       :submit-archiving-project
                       :submit-foreman
                       :submit-printing-order
                       :submit-restriction-enabled-for-other-auths
                       :submitted-application-pdf-export
                       :suti-application-data
                       :suti-application-products
                       :suti-pre-sent-state
                       :suti-update-added
                       :suti-update-id
                       :task-order
                       :task-types-for-application
                       :tasks-tab-visible
                       :terminate-foreman
                       :terminate-foreman-v3
                       :toggle-entry-seen
                       :toggle-guest-subscription
                       :toggle-inspection-summary-locking
                       :toggle-notification-subscription
                       :toggle-submit-restriction-for-other-auths
                       :tos-operations-enabled
                       :transfer-application
                       :undo-cancellation
                       :update-app-bulletin-op-description
                       :update-application-company-notes
                       :update-arcgis-features
                       :update-assignment
                       :update-building-product-catalog
                       :update-building-product-catalog-operation
                       :update-climate-report
                       :update-doc
                       :update-doc-identifier
                       :update-filebank-keywords
                       :update-foreman-other-applications
                       :update-invoice-worktime
                       :update-linked-file-version-metadata
                       :update-op-description
                       :update-post-verdict-doc
                       :update-processing-entry
                       :update-supplement-request
                       :update-task
                       :upload-application-shapefile
                       :upload-attachment
                       :upload-file-authenticated
                       :upload-premises-data
                       :upsert-application-handler
                       :upsert-pate-appeal
                       :user-contact-infos
                       :verdict-bulletins
                       :verdict-pdf
                       :withdraw-supplement-request
                       :xkt-file-info
                       :ya-application
                       :ya-extensions
                       :ymp-publish-bulletin-enabled}]
        (is (= #{} (set/difference application-actions expected)))
        (is (= #{} (set/difference expected application-actions)))))
    (testing "actions targeting neither org nor application"
      (let [expected #{:active-municipalities
                       :ad-logged-in
                       :ad-logins
                       :add-application-import-row
                       :add-bulletin-appeal
                       :add-bulletin-comment
                       :add-scope
                       :add-screenmessage
                       :add-single-sign-on-key
                       :add-user-attachment-allowed
                       :admin-change-user-email
                       :admin-download-application-data
                       :admin-download-application-data-with-kuntalupatunnus
                       :admin-download-authority-usernames
                       :admin-download-building-info
                       :admin-download-foreman-v3-data
                       :admin-download-signed-company-contract
                       :admin-reset-password
                       :admin-screenmessages
                       :all-operations-in
                       :allowed-action
                       :allowed-actions
                       :allowed-actions-for-category
                       :allowed-autologin-ips-for-organization
                       :applicant-to-authority
                       :application-bulletin-categories
                       :application-bulletin-municipalities
                       :application-bulletin-states
                       :application-bulletins
                       :application-copyable-to-location
                       :application-date-within-time-limit
                       :application-imports
                       :application-in-final-archiving-state
                       :application-map-markers-search
                       :application-map-markers-search-es
                       :applications
                       :applications-period-report
                       :applications-search
                       :applications-search-default
                       :applications-search-es
                       :applications-search-total-count
                       :dashboard-query
                       :apply-fixture
                       :archivable-applications-report
                       :archiving-operations-enabled
                       :asiointi-api-organizations
                       :assignment
                       :assignment-count
                       :assignments
                       :assignments-search
                       :authorized-to-apply-submit-restriction-to-other-auths
                       :available-reporting-systems
                       :biller-organizations
                       :bim-checking-plans
                       :bind-attachments-job
                       :bind-filebank-job
                       :building-site-information
                       :bulletin-pdf-export
                       :can-mark-answered
                       :cancel-sign
                       :change-application-state-targets
                       :change-email
                       :change-email-init
                       :change-email-init-suomifi
                       :change-email-simple
                       :change-password
                       :change-password-suomifi
                       :change-permit-premises-note
                       :check-model-webhook
                       :check-password
                       :companies
                       :company
                       :company-add-subscriber
                       :company-add-user
                       :company-cancel-invite
                       :company-invite-user
                       :company-lock
                       :company-remove-subscriber
                       :company-report
                       :company-search-user
                       :company-subscribers
                       :company-tags
                       :company-unsubscribed-emails
                       :company-update
                       :company-user-delete
                       :company-user-delete-all
                       :company-user-update
                       :complete-assignment
                       :connect-sso
                       :copy-application
                       :copy-application-invite-candidates
                       :create-ad-login
                       :create-application
                       :create-asiointi-api-user
                       :create-bim-checking-plan
                       :create-financial-handler
                       :create-foreign-user
                       :create-organization
                       :create-rest-api-user
                       :create-system-user
                       :create-user
                       :current-configuration
                       :dashboard-search-settings
                       :delete-attachments-export-file
                       :delete-bim-checking-plan
                       :delete-import-config
                       :demote-authority
                       :digitizer-applications-report-all-municipalities
                       :digitizer-report
                       :digitizing-enabled
                       :disconnect-sso
                       :dmcity-enabled
                       :document-search-access-allowed
                       :download-attachments-export-file
                       :download-bim-checking-plan
                       :download-bulletin-attachment
                       :download-bulletin-comment-attachment
                       :download-bulletin-doc
                       :download-contract
                       :download-premises-template
                       :download-signed-company-contract
                       :download-user-attachment
                       :edit-application-import-row
                       :edit-import-row
                       :edit-price-catalogue-draft
                       :email-in-use
                       :enable-accordions
                       :enable-company-search
                       :enable-foreman-search
                       :erase-user
                       :es-index-cleanup
                       :event-search
                       :execute-import-config
                       :export-application-for-reporting-db
                       :export-application-ids-for-reporting-db
                       :export-applications
                       :export-applications-for-reporting-db
                       :export-archive-api-usage
                       :export-migration-application-ids-for-reporting-db
                       :export-organizations
                       :export-reporting-applications
                       :features
                       :fetch-response-headers
                       :filebank-enabled
                       :foreman-search-es
                       :full-es-index-update
                       :get-application-operations
                       :get-es-config
                       :get-es-document-by-id
                       :get-es-index-info
                       :get-lp-id-from-previous-permit
                       :get-organization-areas
                       :get-organization-names
                       :get-single-sign-on-keys
                       :import-config-previews
                       :import-config-row-ids
                       :import-config-rows
                       :import-export-export-items
                       :import-export-fetch-items
                       :import-export-fetch-organizations
                       :import-export-import-items
                       :init-sign
                       :invites
                       :is-signed
                       :kuntagml-226-required
                       :latest-attachment-version
                       :linked-file-bim-result-bcf
                       :linked-file-version
                       :local-application-bulletins
                       :local-bulletins-page-settings
                       :local-kuulutus-page-settings
                       :login
                       :map-config
                       :map-marker-infos
                       :mark-application-import-row-deleted
                       :mark-import-config-deleted
                       :mark-import-config-row-deleted
                       :mass-download
                       :modify-asiointi-api-user
                       :municipalities
                       :municipalities-with-organization
                       :municipality-active
                       :municipality-borders
                       :municipality-for-property
                       :municipality-hears-neighbors-visible
                       :my-printing-orders
                       :neighbor-application
                       :neighbor-download-attachment
                       :neighbor-response
                       :newest-version
                       :notice-forms-supported
                       :notifications-update
                       :openinforequest
                       :operation-areas-report
                       :organization-details
                       :organization-krysp-settings
                       :organization-names-by-user
                       :organizations
                       :owners
                       :passwordless-login-accounts
                       :pate-enabled-basic
                       :permit-types
                       :pre-login
                       :printing-order-pricing
                       :processing-dashboard-tab
                       :property-borders
                       :pseudo-update-organization-name
                       :public-bulletin
                       :rami-access
                       :rami-logins-report
                       :redirect-after-login
                       :redirect-neighbor-to-3d-map
                       :register-user
                       :remove-ad-login
                       :remove-application-filter
                       :remove-company-tag-ok
                       :remove-screenmessage
                       :remove-single-sign-on-key
                       :remove-uploaded-file
                       :remove-user-attachment
                       :remove-user-notification
                       :reorder-import-config-columns
                       :reset-asiointi-api-user-password
                       :reset-password
                       :salesforce-export
                       :save-application-filter
                       :save-company-tags
                       :save-notice-resolution
                       :saved-application-filters
                       :schemas
                       :screenmessages
                       :selected-digging-operations-for-organization
                       :selected-operations-for-municipality
                       :set-attachment-group-enabled
                       :set-es-index-aliases-to-current-version
                       :set-es-index-refresh-interval
                       :set-feature
                       :set-organization-notice-form-text
                       :set-organization-permit-type-krysp-version
                       :set-organization-reporting-system
                       :set-organization-scope-pate-value
                       :set-user-enabled
                       :show-application-search-elastic-checkbox
                       :show-automatic-construction-started
                       :show-new-foreman-elastic-checkbox
                       :sign-contract
                       :signing-possible
                       :source-application
                       :stamp-attachments-job
                       :start-polling
                       :store-billing-report-all-municipalities
                       :store-downloads-report-all-municipalities
                       :test-invoicing-sftp
                       :toggle-dashboard-search-setting
                       :toggle-digitization-user-status
                       :toggle-organization-notice-form
                       :toggle-organization-notice-form-integration
                       :toj-access-allowed
                       :tos-metadata-schema
                       :update-ad-login-settings
                       :update-allowed-autologin-ips
                       :update-bim-checking-plan
                       :update-bim-checking-plan-file
                       :update-default-application-filter
                       :update-docstore-info
                       :update-es-document-by-mongo-id
                       :update-import-config
                       :update-invoicing-config
                       :update-organization
                       :update-rest-api-user
                       :update-single-sign-on-key
                       :update-suomifi-settings
                       :update-user
                       :upload-file
                       :upload-file-admin
                       :upsert-company-unsubscribed
                       :usage-purposes
                       :user
                       :user-attachments
                       :user-by-email
                       :user-company-locked
                       :user-is-pure-digitizer
                       :user-is-pure-ymp-org-user
                       :user-organizations-for-archiving-project
                       :user-organizations-for-permit-type
                       :user-report
                       :users
                       :users-in-same-organizations
                       :users-organizations-handling-times
                       :verdicts-contracts-report
                       :vetuma-bulletin
                       :view-file}]
        (is (= #{} (set/difference expected general-actions)))
        (is (= #{} (set/difference general-actions expected)))))))
