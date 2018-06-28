(ns lupapalvelu.action-test
  (:require [taoensso.timbre :as timbre]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [schema.core :as sc]
            [sade.env :as env]
            [sade.core :refer :all]
            [lupapalvelu.action :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.server]
            [lupapalvelu.states :as states])
  (:import [org.apache.commons.io.output NullWriter]))

(defn returns [])
(def ok-status {:ok true})
(def fail-status {:ok false})
(defn localization-error [params]
  {:ok false :text "error.illegal-localization-value" :parameters params})


(testable-privates lupapalvelu.action has-required-user-role and-2-pre-check run)

(defcommand "test-command" {:description "jabba" :states states/all-states :user-roles #{:anonymous}} [command] (returns))

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
                                     :user-roles #{:authority}}})

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
  (fact
    (execute {:action "test-command"})
    => {:ok false :text "too busy"}
    (provided (returns) => {:ok false :text "too busy"}))
  (fact
    (binding [*err* (NullWriter.)]
      (execute {:action "test-command"}))
    => {:ok false :text "error.unknown"}
    (provided (returns) =throws=> (Exception. "This was expected.")))
  (fact
    (execute {:action "test-command"})
    => {:ok true}
    (provided (returns) => nil)))

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
    (domain/get-application-as "123"
                               {:id            "user123"
                                :orgAuthz      {:ankkalinna #{:authority}}
                                :role          :authority}
                               :include-canceled-apps? true)
    => {:state "submitted" :organization "ankkalinna"}
    (domain/get-application-as "123"
                               {:id            "user123"
                                :orgAuthz      {:hanhivaara #{:authority}}
                                :role          :authority}
                               :include-canceled-apps? true)
    => nil)

  (fact "regular user is not authority"
    (execute {:action "test-command-auth"
              :user   {:id "user123"}
              :data   {:id "123"}})
    => unauthorized)

  (fact "with correct authority command is executed"
    (execute {:action "test-command-auth"
              :user   {:id            "user123"
                       :orgAuthz      {:ankkalinna #{:authority}}
                       :role          :authority}
              :data   {:id "123"}})
    => ok?)

  (fact "with incorrect authority error is returned"
    (execute {:action "test-command-auth"
              :user   {:id            "user123"
                       :orgAuthz      {:hanhivaara #{:authority}}
                       :role          :authority}
              :data   {:id "123"}})
    => not-accessible))

(fact "Authority with no org and incorrect role has no access"
  (execute {:action "test-command-auth"
            :user   {:id            "user234"
                     :role          :authority}
            :data   {:id "123"}})
  => unauthorized
  (provided
    (get-actions) => {:test-command-auth {:parameters      [:id]
                                          :user-roles      #{:authority}
                                          :org-authz-roles #{:authority}
                                          :permissions     [{:required []}]
                                          :states          states/all-states}}
    (domain/get-application-as "123" {:id            "user234"
                                      :role          :authority}
                               :include-canceled-apps? true)
    => {:organization "999-R"
        :state        "submitted"
        :auth         [{:id "user234" :role "otherRole"}]}))

(fact "Authority from same org has access"
  (execute {:action "test-command"
            :user   {:id            "user123"
                     :role          :authority}
            :data   {:id "123"}})
  => ok?
  (provided
    (get-actions) => {:test-command {:parameters       [:id]
                                     :user-roles       #{:authority}
                                     :org-authz-roles  #{:authority}
                                     :permissions      [{:required []}]
                                     :states           states/all-states
                                     :user-authz-roles #{:someRole}}}
    (domain/get-application-as "123"
                               {:id            "user123"
                                :role          :authority}
                               :include-canceled-apps? true)
    => {:organization "999-R"
        :state        "submitted"
        :auth         [{:id "user123" :role "someRole"}]}))

(fact "Non-authority from same org has no access"
  (execute {:action "test-command"
            :user   {:id            "some1"
                     :orgAuthz      {:999-R #{:authority}}
                     :role          :applicant}
            :data   {:id "app-id"}})
  => unauthorized
  (provided
    (get-actions) => {:test-command {:parameters      [:id]
                                     :user-roles      #{:authority}
                                     :org-authz-roles #{:authority}
                                     :permissions     [{:required []}]}}))

(fact "Authority with no org but correct role has access"
  (execute {:action "test-command"
            :user   {:id            "user123"
                     :role          :authority}
            :data   {:id "123"}})
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
                               {:id            "user123"
                                :role          :authority}
                               :include-canceled-apps? true)
    => {:organization "999-R"
        :state        "submitted"
        :auth         [{:id "user123" :role "someRole"}]}))

(fact "Authority with no org and writer role in auth array has access"
  (execute {:action "with-default-roles"
            :user   {:id            "user345"
                     :role          :authority}
            :data   {:id "123"}})
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
                               {:id            "user345"
                                :role          :authority}
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
    (domain/get-application-as "123" {:id            "user456"
                                      :role          :authority}
                               :include-canceled-apps? true)
    => {:organization "999-R"
        :state        "submitted"
        :auth         [{:id "user456" :role "3rdRole"}]})

  (execute {:action "with-default-roles"
            :user   {:id            "user456"
                     :role          :authority}
            :data   {:id "123"}})
  => unauthorized)

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
        :state        "submitted"})

  (fact "test-command1 - has required permissions"
    (execute {:action "test-command1"
              :user   {:id       "user123"
                       :orgAuthz {:ankkalinna #{:authority}}
                       :role     :authority}
              :data   {:id "123"}})
    => ok?
    (provided (lupapalvelu.permissions/get-permissions-by-role :global :authority) => #{})
    (provided (lupapalvelu.permissions/get-permissions-by-role :organization :authority) => #{:test/do}))

  (fact "test-command2 - missing required permission"
    (execute {:action "test-command2"
              :user   {:id       "user123"
                       :orgAuthz {:ankkalinna #{:authority}}
                       :role     :authority}
              :data   {:id "123"}})
    => {:ok   false
        :text "error.unauthorized"}
    (provided (lupapalvelu.permissions/get-permissions-by-role :global :authority) => #{})
    (provided (lupapalvelu.permissions/get-permissions-by-role :organization :authority) => #{:test/do}))

  (fact "test-command3 - no required permissions for command"
    (execute {:action "test-command3"
              :user   {:id       "user123"
                       :orgAuthz {:ankkalinna #{:authority}}
                       :role     :authority}
              :data   {:id "123"}})
    => ok?
    (provided (lupapalvelu.permissions/get-permissions-by-role :global :authority) => #{})
    (provided (lupapalvelu.permissions/get-permissions-by-role :organization :authority) => #{:test/do})))

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
    (domain/get-application-as "123" {:id            "user123"
                                      :orgAuthz      {:ankkalinna #{:authority}}
                                      :role          :authority}
                               :include-canceled-apps? true)
    => {:organization "ankkalinna"
        :state        "submitted"})

  (fact
    (execute {:action "test-command1"
              :user   {:id            "user123"
                       :orgAuthz      {:ankkalinna #{:authority}}
                       :role          :authority}
              :data   {:id "123"}})
    => {:ok   false
        :text "FAIL"})
  (fact
    (execute {:action "test-command2"
              :user   {:id            "user123"
                       :orgAuthz      {:ankkalinna #{:authority}}
                       :role          :authority}
              :data   {:id "123"}})
    => ok?)
  (fact
    (execute {:action "test-command3"
              :user   {:id            "user123"
                       :orgAuthz      {:ankkalinna #{:authority}}
                       :role          :authority}
              :data   {:id "123"}})
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
                               {:id            "user123"
                                :orgAuthz      {:ankkalinna #{:authority}}
                                :role          :authority}
                               :include-canceled-apps? true)
    => {:organization "ankkalinna"
        :state        "submitted"})

  (fact
    (execute {:action "test-command1"
              :user   {:id            "user123"
                       :orgAuthz      {:ankkalinna #{:authority}}
                       :role          :authority}
              :data   {:id "123"}})
    => {:ok false :text "FAIL"})
  (fact
    (execute {:action "test-command2"
              :user   {:id            "user123"
                       :orgAuthz      {:ankkalinna #{:authority}}
                       :role          :authority}
              :data   {:id "123"}})
    => ok?)
  (fact
    (execute {:action "test-command3"
              :user   {:id            "user123"
                       :orgAuthz      {:ankkalinna #{:authority}}
                       :role          :authority}
              :data   {:id "123"}})
    => {:ok false :text "FAIL"}))

(facts "Input-validator is not run during auth check"
  (against-background
    (get-actions) => {:test-command1 {:input-validators [(constantly (fail "FAIL"))]
                                      :user-roles       #{:authority}
                                      :org-authz-roles  #{:authority}
                                      :permissions      [{:required []}]}}
    (domain/get-application-as "123"
                               {:id            "user123"
                                :orgAuthz      {:ankkalinna #{:authority}}
                                :role          :authority}
                               :include-canceled-apps? true)
    => {:organization "ankkalinna"
        :state        "submitted"})
  (validate {:action "test-command1"
             :user   {:id            "user123"
                      :orgAuthz      {:ankkalinna #{:authority}}
                      :role          :authority}
             :data   {:id "123"}})
  => ok?)

(facts "Defined querys work only in query pipelines"
  (against-background (get-actions) => {:test-command {:type        :query
                                                       :user-roles  #{:anonymous}
                                                       :permissions [{:required []}]}})
  (fact (execute {:action "test-command"}) => ok?)
  (fact (execute {:action "test-command" :type :query}) => ok?)
  (fact (execute {:action "test-command" :type :command}) => {:ok   false
                                                              :text "error.invalid-type"})
  (fact (execute {:action "test-command" :type :raw}) => {:ok   false
                                                          :text "error.invalid-type"}))

(facts "Defined commands work only in command pipelines"
  (against-background (get-actions) => {:test-command {:type        :command
                                                       :user-roles  #{:anonymous}
                                                       :permissions [{:required []}]}})
  (fact (execute {:action "test-command"}) => ok?)
  (fact (execute {:action "test-command" :type :query}) => {:ok   false
                                                            :text "error.invalid-type"})
  (fact (execute {:action "test-command" :type :command}) => ok?)
  (fact (execute {:action "test-command" :type :raw}) => {:ok   false
                                                          :text "error.invalid-type"}))

(facts "Defined raws work only in raw pipelines"
  (against-background (get-actions) => {:test-command {:type        :raw
                                                       :user-roles  #{:anonymous}
                                                       :permissions [{:required []}]}})
  (fact (execute {:action "test-command"}) => ok?)
  (fact (execute {:action "test-command" :type :query}) => {:ok   false
                                                            :text "error.invalid-type"})
  (fact (execute {:action "test-command" :type :command}) => {:ok   false
                                                              :text "error.invalid-type"})
  (fact (execute {:action "test-command" :type :raw}) => ok?))

(fact "fail! stops the press"
  (against-background (get-actions) => {:failing {:handler     (fn [_] (fail! "kosh"))
                                                  :user-roles  #{:anonymous}
                                                  :permissions [{:required []}]}})
  (execute {:action "failing"}) => {:ok false :text "kosh"})

(fact "exception details are not returned"
  (against-background (get-actions) => {:failing {:handler     (fn [_] (throw (RuntimeException. "kosh")))
                                                  :user-roles  #{:anonymous}
                                                  :permissions [{:required []}]}})
  (execute {:action "failing"}) => {:ok false :text "error.unknown"})

(fact "schema exception"
  (against-background (get-actions) => {:failing {:handler     (fn [_] (sc/validate sc/Str 1))
                                                  :user-roles  #{:anonymous}
                                                  :permissions [{:required []}]}})
  (execute {:action "failing"}) => {:ok false :text "error.illegal-value:schema-validation"})

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
  (vector-parameters-with-map-items-with-required-keys [:foo] [:x] {:data {:foo [nil]}}) => {:ok         false :text "error.vector-parameters-with-items-missing-required-keys"
                                                                                             :parameters [:foo] :required-keys [:x]}
  (vector-parameters-with-map-items-with-required-keys [:foo] [:x] {:data {:foo [{:y "aa"}]}}) => {:ok         false :text "error.vector-parameters-with-items-missing-required-keys"
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

(facts "feature requirements"
  (against-background
    (get-actions) => {:test-command1 {:feature :abba :user-roles #{:anonymous} :permissions [{:required []}]}})

  (fact "without correct feature error is given"
    (execute {:action "test-command1"}) => {:ok   false
                                            :text "error.missing-feature"}
    (provided (env/feature? :abba) => false))

  (fact "with correct feature command is executed"
    (execute {:action "test-command1"}) => ok?
    (provided (env/feature? :abba) => true)))

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
        {}) => {:ok false :error "error1"})))

(facts "not-pre-check"
  ((not-pre-check (constantly {:ok false :error "error"})) {}) => nil?
  ((not-pre-check (constantly nil)) {}) => fail?)

(facts "only users with :organization/admin permission can execute restricted actions"
  (against-background
    (get-actions)
    => {:test-command {:type        :command
                       :permissions [{:required [:organization/admin]}]}}

    (lupapalvelu.permissions/get-permissions-by-role :global :authority)
    => #{})

  (fact
    (validate {:action "test-command"
               :user   {:id            "authority"
                        :orgAuthz      {:ankkalinna #{:authority}}
                        :role          :authority}
               :data   {}})
    => {:ok false, :text "error.unauthorized"}
    (provided (lupapalvelu.permissions/get-permissions-by-role :organization :authority) => #{}))

  (fact
    (validate {:action "test-command"
               :user   {:id            "authority-admin"
                        :orgAuthz      {:ankkalinna #{:authorityAdmin}}
                        :role          :authority}
               :data   {}})
    => {:ok true}
    (provided (lupapalvelu.permissions/get-permissions-by-role :organization :authorityAdmin) => #{:organization/admin})))

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
  (-> command :data))

(facts "input-validator can be a function or schema"
  (fact "validator returns nil, data matches schema => command is successful"
    (run {:action              :input-validator-test-command
          :data                {:answer 42}
          ::validator-response nil}
         [input-validators-fail]
         true)
    => {:answer 42})

  (fact "fn validator returns non-nil => command is fails"
    (run {:action              :input-validator-test-command
          :data                {:answer 42}
          ::validator-response ::validator-fail}
         [input-validators-fail]
         true)
    => ::validator-fail)

  (fact "schema validation fails => command is fails"
    (run {:action              :input-validator-test-command
          :data                {:answer "foo"}
          ::validator-response nil}
         [input-validators-fail]
         true)
    => {:ok   false
        :text "error.illegal-value:schema-validation"}))
