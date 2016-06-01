(ns lupapalvelu.action-test
  (:require [taoensso.timbre :as timbre]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.core :refer :all]
            [lupapalvelu.server]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.user :as user]
            [lupapalvelu.action :refer :all]
            [lupapalvelu.states :as states]
            [lupapalvelu.authorization :as auth])
  (:import [org.apache.commons.io.output NullWriter]))

(defn returns [])
(def ok-status   {:ok true})
(def fail-status {:ok false})

(testable-privates lupapalvelu.action has-required-user-role)

(timbre/with-level :fatal

  (defcommand "test-command" {:description "jabba" :states states/all-states :user-roles #{:anonymous}} [command] (returns))

  (facts "get-meta"
    (get-meta "test-command") => (contains {:description "jabba"}))

  (fact "masked"
    (fact "returns normal data as-is"
      (masked {:data {:a 1}}) => {:data {:a 1}})
    (fact "masks passwords"
      (masked {:data {:password    "salainen"
                      :newPassword "salainen"
                      :oldPassword "salainen"}}) => {:data {:password    "*****"
                                                            :oldPassword "*****"
                                                            :newPassword "*****"}}))


  (facts "Test has-required-user-role"
    (fact (has-required-user-role {:user {:role "foo"}} {:user-roles #{:foo :bar}}) => truthy)
    (fact (has-required-user-role {:user {:role "bar"}} {:user-roles #{:foo :bar}}) => truthy)
    (fact (has-required-user-role {:user {:role "boz"}} {:user-roles #{:foo :bar}}) => falsey))



  (facts "Test missing-fields"
    (fact (missing-fields {:data {:foo "Foo" :bar "Bar"}} {:parameters [:foo :bar]}) => empty?)
    (fact (missing-fields {:data {:foo "Foo" :bozo "Bozo"}} {:parameters [:foo :bar]}) => (contains "bar"))
    (fact (missing-fields {:data {}} {:parameters [:foo :bar]}) => (just ["foo" "bar"] :in-any-order))
    (fact (missing-fields {:data {:foo "Foo"}} {}) => empty?)
    (fact (missing-fields {:data {:foo "Foo" "_" "Bar"}} {:parameters [:foo]}) => empty?))

  (facts "Test general validation in command execution"
    (against-background
      (get-actions) => {:test-command {:parameters [:id]
                                       :states     states/all-states
                                       :user-roles #{:authority}}})

    (fact (execute {})
          => (contains {:ok false}))

    (fact (execute {:action "foobar"})
          => (contains {:ok false}))

    (fact (execute {:action "test-command" :user {:role :applicant}}) => unauthorized)

    (fact (execute {:action "test-command" :user {:role :authority}})
          => (contains {:ok false :text "error.missing-parameters"})))

  (facts "Test general validation in command execution"
    (fact (execute {:action "test-command"}) =>        {:ok false :text "too busy"}
          (provided (returns)                =>        {:ok false :text "too busy"}))
    (fact (binding [*err* (NullWriter.)]
            (execute {:action "test-command"})) =>        {:ok false :text "error.unknown"}
          (provided (returns)                =throws=> (Exception. "This was expected.")))
    (fact (execute {:action "test-command"}) =>        {:ok true}
          (provided (returns)                =>        nil)))

  (facts "Test valid-states"
    (against-background
      (get-actions) => {:test-command {:parameters [:id]
                                       :states     [:open]}})
    (fact "invalid state"
          (invalid-state-in-application {:action "test-command" :data {:id "123"}} {:state "closed"})
          => (contains {:ok false, :text "error.command-illegal-state"}))

    (fact "valid state"
          (invalid-state-in-application {:action "test-command" :data {:id "123"}} {:state "open"})
          => nil))

  (facts "Test authority"
    (against-background
      (get-actions) => {:test-command-auth {:parameters [:id]
                                            :user-roles #{:authority}
                                            :org-authz-roles #{:authority}
                                            :states     states/all-states}}
      (domain/get-application-as "123" {:id "user123" :organizations ["ankkalinna"] :orgAuthz {:ankkalinna #{:authority}} :role :authority} :include-canceled-apps? true) =>  {:state "submitted" :organization "ankkalinna"}
      (domain/get-application-as "123" {:id "user123" :organizations ["hanhivaara"] :orgAuthz {:hanhivaara #{:authority}} :role :authority} :include-canceled-apps? true) =>  nil)

    (fact "regular user is not authority"
      (execute {:action "test-command-auth" :user {:id "user123"} :data {:id "123"}}) => unauthorized)

    (fact "with correct authority command is executed"
      (execute {:action "test-command-auth" :user {:id "user123" :organizations ["ankkalinna"] :orgAuthz {:ankkalinna #{:authority}} :role :authority} :data {:id "123"}}) => ok?)

    (fact "with incorrect authority error is returned"
      (execute {:action "test-command-auth" :user {:id "user123" :organizations ["hanhivaara"] :orgAuthz {:hanhivaara #{:authority}} :role :authority} :data {:id "123"}}) => not-accessible))

  (facts "Access based on user-authz-roles"
    (against-background
      (get-actions) => {:test-command-auth  {:parameters       [:id]
                                             :user-roles       #{:authority}
                                             :org-authz-roles  #{:authority}
                                             :states           states/all-states
                                             :user-authz-roles #{:someRole}}
                        :with-default-roles {:parameters       [:id]
                                             :user-roles       #{:authority}
                                             :org-authz-roles  #{:authority}
                                             :states           states/all-states
                                             :user-authz-roles auth/default-authz-writer-roles}}
      (domain/get-application-as "123" {:id "some1" :organizations ["999-R"] :orgAuthz {:999-R #{:authority}} :role :authority} :include-canceled-apps? true) => {:organization "999-R"
                                                                                                                                                                  :state "submitted"
                                                                                                                                                                  :auth [{:id "user123" :role "someRole"}]}

      (domain/get-application-as "123" {:id "some1" :organizations ["999-R"] :orgAuthz {:999-R #{:authority}} :role :applicant} :include-canceled-apps? true) => {:organization "999-R" :state "submitted" :auth []}

      (domain/get-application-as "123" {:id "user123" :organizations [] :role :authority} :include-canceled-apps? true) => {:organization "999-R"
                                                                                                                             :state "submitted"
                                                                                                                             :auth [{:id "user123" :role "someRole"}]}

      (domain/get-application-as "123" {:id "user234" :organizations [] :role :authority} :include-canceled-apps? true) => {:organization "999-R"
                                                                                                                             :state "submitted"
                                                                                                                             :auth [{:id "user234" :role "otherRole"}]}

      (domain/get-application-as "123" {:id "user345" :organizations [] :role :authority} :include-canceled-apps? true) => {:organization "999-R"
                                                                                                                             :state "submitted"
                                                                                                                             :auth [{:id "user345" :role "writer"}]}

      (domain/get-application-as "123" {:id "user456" :organizations [] :role :authority} :include-canceled-apps? true) => {:organization "999-R"
                                                                                                                             :state "submitted"
                                                                                                                             :auth [{:id "user456" :role "3rdRole"}]}
      )

    (fact "Authority from same org has access"
      (execute {:action "test-command-auth" :user {:id "some1" :organizations ["999-R"] :orgAuthz {:999-R #{:authority}} :role :authority} :data {:id "123"}}) => ok?)

    (fact "Non-authority from same org has no access"
      (execute {:action "test-command-auth" :user {:id "some1" :organizations ["999-R"] :orgAuthz {:999-R #{:authority}} :role :applicant} :data {:id "123"}}) => unauthorized)

    (fact "Authority with no org but correct role has access"
      (execute {:action "test-command-auth" :user {:id "user123" :organizations [] :role :authority} :data {:id "123"}}) => ok?)

    (fact "Authority with no org and incorrect role has no access"
      (execute {:action "test-command-auth" :user {:id "user234" :organizations [] :role :authority} :data {:id "123"}}) => unauthorized)

    (fact "Authority with no org and writer role in auth array has access"
      (execute {:action "with-default-roles" :user {:id "user345" :organizations [] :role :authority} :data {:id "123"}}) => ok?)

    (fact "Authority with no org and non-writer role in auth array has no access"
      (execute {:action "with-default-roles" :user {:id "user456" :organizations [] :role :authority} :data {:id "123"}}) => unauthorized)

    )

  (facts "Parameter validation"
    (against-background (get-actions) => {:test-command {:parameters [:id]}})
    (fact  (missing-parameters {:action "test-command" :data {}})           => { :ok false :parameters ["id"] :text "error.missing-parameters"})
    (fact  (missing-parameters {:action "test-command" :data {:id 1}})      => nil)
    (fact  (missing-parameters {:action "test-command" :data {:id 1 :_ 2}}) => nil))

  (facts "Custom pre-check is run"
    (against-background
      (get-actions) => {:test-command1 {:pre-checks [(constantly (fail "FAIL"))], :user-roles #{:authority}, :org-authz-roles #{:authority}}
                        :test-command2 {:pre-checks [(constantly nil)], :user-roles #{:authority}, :org-authz-roles #{:authority}}
                        :test-command3 {:pre-checks [(constantly nil) (constantly nil) (constantly (fail "FAIL"))], :user-roles #{:authority}, :org-authz-roles #{:authority}}}
      (domain/get-application-as "123" {:id "user123" :organizations ["ankkalinna"] :orgAuthz {:ankkalinna #{:authority}} :role :authority} :include-canceled-apps? true) =>  {:organization "ankkalinna" :state "submitted"})

    (fact (execute {:action "test-command1" :user {:id "user123" :organizations ["ankkalinna"] :orgAuthz {:ankkalinna #{:authority}} :role :authority} :data {:id "123"}}) => {:ok false :text "FAIL"})
    (fact (execute {:action "test-command2" :user {:id "user123" :organizations ["ankkalinna"] :orgAuthz {:ankkalinna #{:authority}} :role :authority} :data {:id "123"}}) => ok?)
    (fact (execute {:action "test-command3" :user {:id "user123" :organizations ["ankkalinna"] :orgAuthz {:ankkalinna #{:authority}} :role :authority} :data {:id "123"}}) => {:ok false :text "FAIL"}))

  (facts "Custom input-validator is run"
    (against-background
      (get-actions) => {:test-command1 {:input-validators [(constantly (fail "FAIL"))], :user-roles #{:authority}, :org-authz-roles #{:authority}}
                        :test-command2 {:input-validators [(constantly nil)], :user-roles #{:authority}, :org-authz-roles #{:authority}}
                        :test-command3 {:input-validators [(constantly nil) (constantly nil) (constantly (fail "FAIL"))], :user-roles #{:authority}, :org-authz-roles #{:authority}}}
      (domain/get-application-as "123" {:id "user123" :organizations ["ankkalinna"] :orgAuthz {:ankkalinna #{:authority}} :role :authority} :include-canceled-apps? true) =>  {:organization "ankkalinna" :state "submitted"})

    (fact (execute {:action "test-command1" :user {:id "user123" :organizations ["ankkalinna"] :orgAuthz {:ankkalinna #{:authority}} :role :authority} :data {:id "123"}}) => {:ok false :text "FAIL"})
    (fact (execute {:action "test-command2" :user {:id "user123" :organizations ["ankkalinna"] :orgAuthz {:ankkalinna #{:authority}} :role :authority} :data {:id "123"}}) => ok?)
    (fact (execute {:action "test-command3" :user {:id "user123" :organizations ["ankkalinna"] :orgAuthz {:ankkalinna #{:authority}} :role :authority} :data {:id "123"}}) => {:ok false :text "FAIL"}))

  (facts "Input-validator is not run during auth check"
    (against-background
      (get-actions) => {:test-command1 {:input-validators [(constantly (fail "FAIL"))], :user-roles #{:authority}, :org-authz-roles #{:authority}}}
      (domain/get-application-as "123" {:id "user123" :organizations ["ankkalinna"] :orgAuthz {:ankkalinna #{:authority}} :role :authority} :include-canceled-apps? true) =>  {:organization "ankkalinna" :state "submitted"})
    (validate {:action "test-command1" :user {:id "user123" :organizations ["ankkalinna"] :orgAuthz {:ankkalinna #{:authority}} :role :authority} :data {:id "123"}}) => ok?)

  (facts "Defined querys work only in query pipelines"
    (against-background (get-actions) => {:test-command {:type :query :user-roles #{:anonymous}}})
    (fact  (execute {:action "test-command"})                 => ok?)
    (fact  (execute {:action "test-command" :type :query})    => ok?)
    (fact  (execute {:action "test-command" :type :command})  => {:ok false, :text "error.invalid-type"})
    (fact  (execute {:action "test-command" :type :raw})      => {:ok false, :text "error.invalid-type"}))

  (facts "Defined commands work only in command pipelines"
    (against-background (get-actions) => {:test-command {:type :command :user-roles #{:anonymous}}})
    (fact  (execute {:action "test-command"})                 => ok?)
    (fact  (execute {:action "test-command" :type :query})    => {:ok false, :text "error.invalid-type"})
    (fact  (execute {:action "test-command" :type :command})  => ok?)
    (fact  (execute {:action "test-command" :type :raw})      => {:ok false, :text "error.invalid-type"}))

  (facts "Defined raws work only in raw pipelines"
    (against-background (get-actions) => {:test-command {:type :raw :user-roles #{:anonymous}}})
    (fact  (execute {:action "test-command"})                 => ok?)
    (fact  (execute {:action "test-command" :type :query})    => {:ok false, :text "error.invalid-type"})
    (fact  (execute {:action "test-command" :type :command})  => {:ok false, :text "error.invalid-type"})
    (fact  (execute {:action "test-command" :type :raw})      => ok?))

  (fact "fail! stops the press"
    (against-background (get-actions) => {:failing {:handler (fn [_] (fail! "kosh")) :user-roles #{:anonymous}}})
    (binding [*err* (NullWriter.)]
      (execute {:action "failing"})) => {:ok false :text "kosh"})

  (fact "exception details are not returned"
    (against-background (get-actions) => {:failing {:handler (fn [_] (throw (RuntimeException. "kosh"))) :user-roles #{:anonymous}}})
    (binding [*err* (NullWriter.)]
      (execute {:action "failing"})) => {:ok false :text "error.unknown"})

  (facts "non-blank-parameters"
    (non-blank-parameters nil {}) => nil
    (non-blank-parameters [] {})  => nil
    (non-blank-parameters [:foo] {:data {:foo ""}})    => (contains {:parameters [:foo]})
    (non-blank-parameters [:foo] {:data {:foo " "}})   => (contains {:parameters [:foo]})
    (non-blank-parameters [:foo] {:data {:foo " x"}})  => nil
    (non-blank-parameters [:foo :bar] {:data {:foo nil}})          => (contains {:parameters [:foo :bar]})
    (non-blank-parameters [:foo :bar] {:data {:foo "" :bar " "}})  => (contains {:parameters [:foo :bar]})
    (non-blank-parameters [:foo :bar] {:data {:foo " " :bar "x"}}) => (contains {:parameters [:foo]}))

  (facts "vector-parameters-with-non-blank-items"
    (vector-parameters-with-non-blank-items [:foo] {:data {:foo ["aa"]}})     => nil
    (vector-parameters-with-non-blank-items [:foo] {:data {:foo ["aa" nil]}}) => {:ok false :text "error.vector-parameters-with-blank-items" :parameters [:foo]}
    (vector-parameters-with-non-blank-items [:foo] {:data {:foo ["aa" ""]}})  => {:ok false :text "error.vector-parameters-with-blank-items" :parameters [:foo]}
    (vector-parameters-with-non-blank-items [:foo :bar] {:data {:foo [nil] :bar [" "]}}) => {:ok false :text "error.vector-parameters-with-blank-items" :parameters [:foo :bar]}
    (vector-parameters-with-non-blank-items [:foo :bar] {:data {:foo nil :bar " "}})     => {:ok false :text "error.non-vector-parameters"        :parameters [:foo :bar]})

  (facts "vector-parameters-with-map-items-with-required-keys"
    (vector-parameters-with-map-items-with-required-keys [:foo] [:x :y] {:data {:foo [{:x "aa" :y nil}]}}) => nil
    (vector-parameters-with-map-items-with-required-keys [:foo] [:x] {:data {:foo nil}})         => {:ok false :text "error.non-vector-parameters" :parameters [:foo]}
    (vector-parameters-with-map-items-with-required-keys [:foo] [:x] {:data {:foo [nil]}})       => {:ok false :text "error.vector-parameters-with-items-missing-required-keys"
                                                                                                     :parameters [:foo] :required-keys [:x]}
    (vector-parameters-with-map-items-with-required-keys [:foo] [:x] {:data {:foo [{:y "aa"}]}}) => {:ok false :text "error.vector-parameters-with-items-missing-required-keys"
                                                                                                     :parameters [:foo] :required-keys [:x]})

  (fact "map-parameters-with-required-keys"
    (map-parameters-with-required-keys [:foo] [:x :y] {:data {:foo {:x "aa" :y nil}}}) => nil
    (map-parameters-with-required-keys [:foo] [:x] {:data {}})                         => {:ok false :text "error.unknown-type" :parameters [:foo]}
    (map-parameters-with-required-keys [:foo] [:x] {:data {:foo nil}})                 => {:ok false :text "error.unknown-type" :parameters [:foo]}
    (map-parameters-with-required-keys [:foo] [:x] {:data {:foo {:y "aa"}}})           => {:ok false :text "error.map-parameters-with-required-keys" :parameters [:foo] :required-keys [:x]})

  (facts "feature requirements"
   (against-background
     (get-actions) => {:test-command1 {:feature :abba :user-roles #{:anonymous}}})

   (fact "without correct feature error is given"
     (execute {:action "test-command1"}) => {:ok false, :text "error.missing-feature"}
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
      => [...c1... ...c2... ...f1... ...f2...]))
