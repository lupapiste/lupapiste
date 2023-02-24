(ns lupapalvelu.user-test
  (:require [clojure.test.check :refer [quick-check]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lupapalvelu.generators.user]
            [lupapalvelu.itest-util :refer [unauthorized?
                                            missing-parameters?
                                            organization-not-found?
                                            expected-failure?]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.security :as security]
            [lupapalvelu.test-util :refer [passing-quick-check]]
            [lupapalvelu.user :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.schema-generators :as ssg]
            [sade.schema-utils :as ssu]))

;;
;; ==============================================================================
;; Utils:
;; ==============================================================================
;;

(facts non-private
  (fact "strips away private keys from map"
    (non-private {:name "tommi" :private {:secret "1234"}}) => {:name "tommi"})
  (fact ".. but not non-recursively"
    (non-private {:name "tommi" :child {:private {:secret "1234"}}}) => {:name "tommi" :child {:private {:secret "1234"}}}))

(facts summary
  (fact (summary nil) => nil)
  (let [user {:id        "1"
              :firstName "Simo"
              :username  "simo@salminen.com"
              :lastName  "Salminen"
              :role      "comedian"
              :private   "SECRET"}]
    (fact (summary user) => (just (dissoc user :private)))))

(facts session-summary
  (fact (session-summary nil) => nil)
  (let [user {:id        "1"
              :firstName "Simo"
              :username  "simo@salminen.com"
              :lastName  "Salminen"
              :role      "comedian"
              :private   "SECRET"
              :orgAuthz  {:753-R ["authority" "approver"]}
              :company   {:id     "Firma Oy"
                          :role   "admin"
                          :submit true}}]
    (fact (:expires (session-summary user)) => number?)
    (fact (-> (session-summary user) :orgAuthz :753-R) => set?)
    (fact (= (summary user) (summary (session-summary user))) => truthy)))

(fact "virtual-user?"
  (virtual-user? {:role "authority"}) => false
  (virtual-user? {:role :authorityAdmin}) => false
  (virtual-user? {:role "oirAuthority"}) => true
  (virtual-user? {:role :oirAuthority}) => true
  (virtual-user? {:role "applicant"}) => false
  (virtual-user? {:role "admin"}) => false
  (virtual-user? {:role "authorityAdmin"}) => false
  (virtual-user? {}) => false
  (virtual-user? nil) => false)

(fact authority?
  (authority? {:role "authority"}) => truthy
  (authority? {:role :authority}) => truthy
  (authority? {:role :oirAuthority}) => falsey
  (authority? {:role "applicant"}) => falsey
  (authority? {}) => falsey
  (authority? nil) => falsey)

(fact applicant?
  (applicant? {:role "applicant"}) => truthy
  (applicant? {:role :applicant}) => truthy
  (applicant? {:role "authority"}) => falsey
  (applicant? {}) => falsey
  (applicant? nil) => falsey)

(fact financial-authority?
  (financial-authority? {:role "financialAuthority"}) => truthy
  (financial-authority? {:role :financialAuthority}) => truthy
  (financial-authority? {:role "authority"}) => falsey
  (financial-authority? {:role :authority}) => falsey)

(fact user-is-pure-digitizer?
  (user-is-pure-digitizer? {:role     "authority"
                            :orgAuthz {:123-FOO ["digitizer"]}})
  => truthy
  (user-is-pure-digitizer? {:role     "authority"
                            :orgAuthz {:123-FOO ["digitizer" "digitizer"]
                                       :456-BAR []
                                       :789-BAZ [:digitizer :digitizer]}})
  => truthy
  (user-is-pure-digitizer? {:role     "authority"
                            :orgAuthz {:123-FOO ["digitizer" "reader"]}})
  => falsey
  (user-is-pure-digitizer? {:role     "applicant"
                            :orgAuthz {:123-FOO ["digitizer"]}})
  => falsey
  (user-is-pure-digitizer? {:role     "authority"
                            :orgAuthz {}})
  => falsey
  (user-is-pure-digitizer? {:role     "authority"
                            :orgAuthz {:123-FOO ["authority"]
                                       :789-BAZ [:digitizer]}})
  => falsey)

(fact same-user?
  (same-user? {:id "foo"} {:id "foo"}) => truthy
  (same-user? {:id "foo"} {:id "bar"}) => falsey)

(facts "full-name"
  (full-name nil) => ""
  (full-name "") => ""
  (full-name {:firstName "Hello"}) => "Hello"
  (full-name {:lastName "World"}) => "World"
  (full-name {:firstName "  Hello  "}) => "Hello"
  (full-name {:lastName "  World  "}) => "World"
  (full-name {:firstName "  Hello  "
              :lastName  "  World  "}) => "Hello World"
  (full-name {:firstName "    "
              :lastName  "    "}) => "")

(def role-set-generator
  (let [role-keyword-generator (gen/fmap keyword (ssg/generator StrOrgAuthzRoles))]
    (gen/set role-keyword-generator
             {:max-elements (count roles/all-org-authz-roles)
              :min-elements 0})))

(def organization-ids-by-roles-prop
  (prop/for-all [user (ssg/generator User)
                 roles role-set-generator]
                (let [user   (with-org-auth user)
                      result (organization-ids-by-roles user roles)
                      valid? (fn [[org org-roles]]
                               (let [in-both (clojure.set/intersection roles (set org-roles))]
                                 (= (boolean (not-empty in-both))
                                    (contains? result (name org)))))]
                  (every? valid? (:orgAuthz user)))))

(fact :qc organization-ids-by-roles-spec
  (quick-check 100 organization-ids-by-roles-prop :max-size 50)
  => passing-quick-check)

(facts coerce-person-id
  (let [user-mock {:personId "131052-308T"
                   :personIdSource "identification-service"}]
    (fact "already a person id"
      (coerce-person-id "131052-308T") => "131052-308T")
    (fact "not a string"
      (coerce-person-id nil) => nil)
    (fact "user found, no person id"
      (coerce-person-id "user-id") => nil
      (provided (get-user-by-id "user-id") => (dissoc user-mock :personId :personIdSource))
    (fact "user with person id found"
      (coerce-person-id "user-id") => "131052-308T"
      (provided (get-user-by-id "user-id") => user-mock)))))

(def user-and-organization-id-generator
  (gen/let [org-ids (gen/vector-distinct (ssg/generator OrgId)
                                         {:min-elements 1})
            user (-> (ssu/select-keys User [:id :role :username :email :enabled :orgAuthz])
                     (ssg/generator {OrgId (gen/elements org-ids)}))]
           (let [user (with-org-auth user)]
             {:user            user
              :organization-id (rand-nth org-ids)})))

(def user-is-authority-in-organization-prop
  (prop/for-all [{:keys [user organization-id]} user-and-organization-id-generator]
                (let [users-roles-in-organization (set (get-in user [:orgAuthz organization-id]))
                      organization-id             (name organization-id)
                      is-authority-in-org?        (user-is-authority-in-organization? user organization-id)]
                  (= is-authority-in-org?
                     (contains? users-roles-in-organization :authority)))))

(fact :qc user-is-authority-in-organization?-spec
          (quick-check 150 user-is-authority-in-organization-prop :max-size 50)
          => passing-quick-check)

(facts municipality-name->system-user-email
  (fact "srting with caps"
    (municipality-name->system-user-email "Kunta") => "jarjestelmatunnus.kunta@lupapiste.fi")

  (fact "string with whitespace"
    (municipality-name->system-user-email "muu kunta nimi") => "jarjestelmatunnus.muukuntanimi@lupapiste.fi")

  (fact "string with scandics"
    (municipality-name->system-user-email "p\u00e4\u00e4p\u00e5kk\u00f6l\u00e4") => "jarjestelmatunnus.paapakkola@lupapiste.fi"
    (municipality-name->system-user-email "p\u00e4\u00e4pakk\u00f6\u00f6\u00f6\u00f6l\u00e4") => "jarjestelmatunnus.paapakkoooola@lupapiste.fi")

  (fact "string with invalid chars"
    (municipality-name->system-user-email "a0b+c*") => "jarjestelmatunnus.a_b_c_@lupapiste.fi"))

(facts create-system-user
  (fact "no organization-ids"
    (create-system-user {:role "admin"} "Kunta" "tunnus@email.org" []) => {:username "tunnus@email.org"}
    (provided (mongo/create-id) => ..id..)
    (provided (mongo/insert :users {:id        ..id..
                                    :username  "tunnus@email.org"
                                    :email     "tunnus@email.org"
                                    :firstName "J\u00e4rjestelm\u00e4tunnus"
                                    :lastName  "Kunta"
                                    :role      "authority"
                                    :enabled   true
                                    :orgAuthz  {}
                                    :language  "fi"}) => irrelevant))

  (fact "with organization-ids"
    (create-system-user {:role "admin"} "Kunta" "tunnus@email.org" ["123-R" "789-YA"]) => {:username "tunnus@email.org"}
    (provided
      (org/known-organizations? [:123-R :789-YA]) => true
      (mongo/create-id) => ..id..
      (mongo/insert :users {:id        ..id..
                            :username  "tunnus@email.org"
                            :email     "tunnus@email.org"
                            :firstName "J\u00e4rjestelm\u00e4tunnus"
                            :lastName  "Kunta"
                            :role      "authority"
                            :enabled   true
                            :orgAuthz  {:123-R ["reader"]
                                        :789-YA ["reader"]}
                            :language  "fi"}) => irrelevant)))

;;
;; ==============================================================================
;; create-new-user-entity
;; ==============================================================================
;;

(testable-privates lupapalvelu.user create-new-user-entity)

(facts "create-new-user-entity"

  (facts "emails are converted to lowercase"
    (fact (create-new-user-entity {:email "foo"}) => (contains {:email "foo"}))
    (fact (create-new-user-entity {:email "Foo@Bar.COM"}) => (contains {:email "foo@bar.com"})))

  (facts "default values"
    (fact (create-new-user-entity {:email "Foo@Foo.Foo"}) => (contains {:email     "foo@foo.foo"
                                                                        :username  "foo@foo.foo"
                                                                        :firstName ""
                                                                        :lastName  "foo@foo.foo"
                                                                        :enabled   false}))
    (fact (create-new-user-entity {:email "Foo" :username "bar"}) => (contains {:email     "foo"
                                                                                :username  "bar"
                                                                                :firstName ""
                                                                                :lastName  "foo"
                                                                                :enabled   false})))

  (fact "password (if provided) is put under :private"
    (fact (create-new-user-entity {:email "email"})
      => (contains {:private {}}))
    (fact (create-new-user-entity {:email "email" :password "foo"})
      => (contains {:private {:password "bar"}})
      (provided (security/get-hash "foo") => "bar")))

  (fact "does not contain extra fields"
    (-> (create-new-user-entity {:email "email" :foo "bar"}) :foo) => nil)

  (facts "apikey is not created"
    (fact (-> (create-new-user-entity {:email "..anything.." :apikey "true"}) :private :apikey) => nil?)
    (fact (-> (create-new-user-entity {:email "..anything.." :apikey "false"}) :private) => {})
    (fact (-> (create-new-user-entity {:email "..anything.." :apikey "foo"}) :private :apikey) => nil?)))

;;
;; ==============================================================================
;; validate-create-new-user!
;; ==============================================================================
;;

(testable-privates lupapalvelu.user new-user-error validate-create-new-user!)

(facts "new-user-error-test"

  (fact "new user must match schema"
    (-> {:caller    nil
         :user-data (create-new-user-entity {})}
        (new-user-error))
    => (contains {:desc  "user data matches User schema"
                  :error :error.missing-parameters}))

  (fact "applicants are born via registration"
    (new-user-error {:caller    {}
                     :user-data (create-new-user-entity {:email "a@b.c"
                                                         :role  "applicant"})})
    => (contains {:desc  "applicants are born via registration"
                  :error :error.unauthorized}))

  (fact "applicants may not have an organizations"
    (new-user-error {:caller    nil
                     :user-data (create-new-user-entity {:email    "a@b.c"
                                                         :role     "applicant"
                                                         :orgAuthz {:some-org ["authority"]}})})
    => (contains {:desc  "applicants may not have an organizations"
                  :error :error.unauthorized}))

  (fact "authorityAdmin can create users into his/her own organizations only"
    (fact "caller is only authority"
      (new-user-error {:caller    {:role     "authority"
                                   :orgAuthz {:org-1 #{:authority'}}}
                       :user-data (create-new-user-entity {:email    "a@b.c"
                                                           :role     "authority"
                                                           :orgAuthz {:org-1 ["authority"]}})})
      => (contains {:desc  "authorityAdmin can create users into his/her own organizations only"
                    :error :error.unauthorized}))

    (fact "caller is authorityAdmin, but in wrong org"
      (new-user-error {:caller    {:role     "authority"
                                   :orgAuthz {:org-1 #{:authority'}}}
                       :user-data (create-new-user-entity {:email    "a@b.c"
                                                           :role     "authority"
                                                           :orgAuthz {:org-2 ["authority"]}})})
      => (contains {:desc  "authorityAdmin can create users into his/her own organizations only"
                    :error :error.unauthorized}))

    (fact "caller is authorityAdmin, and in correct org"
      (new-user-error {:caller               {:role     "authority"
                                              :orgAuthz {:org-1 #{:authorityAdmin}}}
                       :user-data            (create-new-user-entity {:email    "a@b.c"
                                                                      :role     "authority"
                                                                      :orgAuthz {:org-1 ["authority"]}})
                       :known-organizations? {[:org-1] true}})
      => nil)

    (fact "caller has authorityAdmin in two orgs, created user can have roles in both"
      (new-user-error {:caller               {:role     "authority"
                                              :orgAuthz {:org-1 #{:anything1 :authorityAdmin :anything2}
                                                         :org-2 #{:anything2 :authorityAdmin :anything4}}}
                       :user-data            (create-new-user-entity {:email    "a@b.c"
                                                                      :role     "authority"
                                                                      :orgAuthz {:org-1 ["authority"]
                                                                                 :org-2 ["authority"]}})
                       :known-organizations? {[:org-1 :org-2] true}})
      => nil)

    (fact "caller has authorityAdmin in only one org, created user has roles in both, therefore this fails"
      (new-user-error {:caller               {:role     "authority"
                                              :orgAuthz {:org-1 #{:anything1 :authorityAdmin :anything2}
                                                         :org-2 #{::anything2 :anything4}}}
                       :user-data            (create-new-user-entity {:email    "a@b.c"
                                                                      :role     "authority"
                                                                      :orgAuthz {:org-1 ["authority"]
                                                                                 :org-2 ["authority"]}})
                       :known-organizations? {[:org-1 :org-2] true}})
      => (contains {:desc  "authorityAdmin can create users into his/her own organizations only"
                    :error :error.unauthorized}))

    (fact "caller has authorityAdmin in only one org, created user has role in that org"
      (new-user-error {:caller               {:role     "authority"
                                              :orgAuthz {:org-1 #{:anything1 :authorityAdmin :anything2}
                                                         :org-2 #{::anything2 :anything4}}}
                       :user-data            (create-new-user-entity {:email    "a@b.c"
                                                                      :role     "authority"
                                                                      :orgAuthz {:org-1 ["authority"]}})
                       :known-organizations? {[:org-1] true}})
      => nil)
    (fact "admin is allowed to create users with orgAuthz"
      (new-user-error {:caller               {:role "admin"}
                       :user-data            (create-new-user-entity {:email "a@b.c"
                                                                      :role  "authority"
                                                                      :orgAuthz {:123-TEST ["authority"]}})
                       :known-organizations? (constantly true)})
      => nil))

  (fact "dummy user may not have an organization roles"
    (new-user-error {:caller    {:role     "authority"
                                 :orgAuthz {:foo #{:authorityAdmin}}}
                     :user-data (create-new-user-entity {:email    "a@b.c"
                                                         :role     "dummy"
                                                         :orgAuthz {:foo ["approver"]}})})
    => (contains {:error :error.unauthorized
                  :desc  "dummy user may not have an organization roles"}))

  (facts "all organizations must be known"
    (fact "org-1 is unknown"
      (new-user-error {:caller               {:role     "authority"
                                              :orgAuthz {:org-1 #{:authorityAdmin}}}
                       :user-data            (create-new-user-entity {:email    "a@b.c"
                                                                      :role     "authority"
                                                                      :orgAuthz {:org-1 ["authority"]}})
                       :known-organizations? (constantly false)})
      => (contains {:desc  "all organizations must be known"
                    :error :error.organization-not-found}))
    (fact "org-1 is known"
      (new-user-error {:caller               {:role     "authority"
                                              :orgAuthz {:org-1 #{:authorityAdmin}}}
                       :user-data            (create-new-user-entity {:email    "a@b.c"
                                                                      :role     "authority"
                                                                      :orgAuthz {:org-1 ["authority"]}})
                       :known-organizations? {[:org-1] true}})
      => nil))

  (facts "only users in admin role can create users with apikeys"
    (fact "caller role is authority, use of apikey is not permitted"
      (new-user-error {:caller               {:role "authority"}
                       :user-data            (create-new-user-entity {:email "a@b.c"
                                                                      :role  "dummy"})
                       :known-organizations? (constantly true)
                       :apikey               "xxx"})
      => (contains {:desc  "only admin can create create users with apikey"
                    :error :error.unauthorized}))
    (fact "caller is admin, user of apikey is permitted"
      (new-user-error {:caller               {:role "admin"}
                       :user-data            (create-new-user-entity {:email "a@b.c"
                                                                      :role  "dummy"})
                       :known-organizations? (constantly true)
                       :apikey               "xxx"})
      => nil))

  ;; Happy cases:

  (fact "authority can create user with role `dummy`"
    (new-user-error {:caller               {:role     "authority"
                                            :orgAuthz {:org-1 #{:authorityAdmin}}}
                     :user-data            (create-new-user-entity {:email    "a@b.c"
                                                                    :role     "dummy"
                                                                    :orgAuthz {}})
                     :known-organizations? {nil true}})
    => nil))

(facts "validate-create-new-user!"
  (fact (validate-create-new-user! nil (create-new-user-entity nil)) => missing-parameters?)
  (fact (validate-create-new-user! nil (create-new-user-entity {})) => missing-parameters?)

  (fact "applicant can create dummy users"
    (validate-create-new-user! {:role "applicant"}
                               (create-new-user-entity {:role  "dummy"
                                                        :email "x@x.x"}))
    => (contains {:email "x@x.x"}))
  (fact "applicant can't create authority users"
    (validate-create-new-user! {:role "applicant"}
                               (create-new-user-entity {:role  "authority"
                                                        :email "x@x.x"}))
    => unauthorized?)

  (fact "applicant can't create applicant users"
    (validate-create-new-user! {:role "applicant"}
                               (create-new-user-entity {:role  "applicant"
                                                        :email "x@x.x"}))
    => unauthorized?)

  (facts "authority can create new authority, provided that she has authorityAdmin permission to target org"
    (fact "caller has authority admin, validation succeeds"
      (validate-create-new-user! {:role     "authority"
                                  :orgAuthz {:org-1 #{:authorityAdmin}
                                             :org-2 #{:authorityAdmin}}}
                                 (create-new-user-entity {:role     "authority"
                                                          :email    "x@x.x"
                                                          :orgAuthz {:org-1 ["authority"]
                                                                     :org-2 ["authority"]}}))
      => (contains {:email "x@x.x"})
      (provided
        (org/known-organizations? [:org-1 :org-2]) => true))
    (fact "caller does not have authorityAdmin to org-2 validation fails"
      (validate-create-new-user! {:role     "authority"
                                  :orgAuthz {:org-1 #{:authorityAdmin}
                                             :org-2 #{:authority}}}
                                 (create-new-user-entity {:role     "authority"
                                                          :email    "x@x.x"
                                                          :orgAuthz {:org-1 ["authority"]
                                                                     :org-2 ["authority"]}}))
      => unauthorized?)
    (fact "can't create users to org that is unknown"
      (validate-create-new-user! {:role     "authority"
                                  :orgAuthz {:org-1 #{:authorityAdmin}
                                             :org-2 #{:authorityAdmin}}}
                                 (create-new-user-entity {:role     "authority"
                                                          :email    "x@x.x"
                                                          :orgAuthz {:org-1 ["authority"]
                                                                     :org-2 ["authority"]}}))
      => organization-not-found?
      (provided
        (org/known-organizations? [:org-1 :org-2]) => false)))

  (facts "admin tests"
    (fact "admin can create user to known organization"
      (validate-create-new-user! {:role "admin"}
                                 (create-new-user-entity {:role     "authority"
                                                          :email    "x@x.x"
                                                          :orgAuthz {:org ["authority"]}}))
      => (contains {:email "x@x.x"})
      (provided
        (org/known-organizations? [:org]) => true))
    (fact "admin can't create user to unknown organization"
      (validate-create-new-user! {:role "admin"}
                                 (create-new-user-entity {:role     "authority"
                                                          :email    "x@x.x"
                                                          :orgAuthz {:org ["authority"]}}))
      => organization-not-found?
      (provided
        (org/known-organizations? [:org]) => false)))


  (fact "dummy users can't have orgs"
    (validate-create-new-user! {:role     "authority"
                                :orgAuthz {:org #{:authorityAdmin}}}
                               (create-new-user-entity {:role     "dummy"
                                                        :email    "x@x.x"
                                                        :orgAuthz {:org ["authority"]}}))
    => unauthorized?)

  (fact "not even admin can create another admin"
    (validate-create-new-user! {:role "admin"}
                               (create-new-user-entity {:role  "admin"
                                                        :email "x@x.x"}))
    => missing-parameters?)

  (fact "admin can create authority"
    (validate-create-new-user! {:role "admin"}
                               (create-new-user-entity {:role  "authority"
                                                        :email "x@x.x"}))
    => (contains {:email "x@x.x"}))

  ; TODO: figure out where invalid password should be checked
  ; note: checks have now been added to commands that try to save passwords
  ;  but lets remove this after discussions on where should we do this in future :D
  #_(fact "invalid passwords are rejected"
      (validate-create-new-user! {:role "admin"}
                                 {:password "z"
                                  :role     "dummy"
                                  :email    "x@x.x"})
      => (partial expected-failure? :error.password.minlength)
      (provided (security/valid-password? "z") => false)))

;;
;; ==============================================================================
;; Creating users:
;; ==============================================================================
;;

(facts "create-new-user"

  (fact "register new applicant user, user did not exists before"
    (create-new-user nil {:email "x@x.x" :role "applicant"}) => ..result..
    (provided
      (get-user-by-email "x@x.x") =streams=> [nil ..result..]
      (mongo/create-id) => ..id..
      (mongo/insert :users (contains {:email "x@x.x" :id ..id..})) => nil
      (mongo/update-by-id :users anything anything) => anything :times 0))

  (fact "create new applicant user, user exists before as dummy user"
    (create-new-user nil {:email "x@x.x" :role "applicant"}) => ..result..
    (provided
      (get-user-by-email "x@x.x") =streams=> [{:id ..old-id.. :role "dummy"} ..result..]
      (mongo/insert :users anything) => anything :times 0
      (mongo/update-by-id :users ..old-id.. (contains {:email "x@x.x"})) => nil))

  (fact "create new authorityAdmin user, user exists before as dummy user"
    (create-new-user {:role "admin"}
                     {:email    "x@x.x"
                      :orgAuthz {:x ["authorityAdmin"]}
                      :role     "authority"})
    => ..result..
    (provided
      (get-user-by-email "x@x.x") =streams=> [{:id ..old-id.. :role "dummy"} ..result..]
      (org/known-organizations? [:x]) => true
      (mongo/insert :users anything) => anything :times 0
      (mongo/update-by-id :users ..old-id.. (contains {:email "x@x.x"})) => nil))

  (fact "create new authorityAdmin user, user exists before, but role is not 'dummy'"
    (create-new-user {:role "admin"} {:email "x@x.x" :orgAuthz {:x ["authorityAdmin"]} :role "authority"}) => (partial expected-failure? :error.duplicate-email)
    (provided
      (get-user-by-email "x@x.x") => {:id ..old-id.. :role "authorityAdmin"} :times 1
      (org/known-organizations? [:x]) => true
      (mongo/insert :users anything) => anything :times 0
      (mongo/update-by-id :users ..old-id.. (contains {:email "x@x.x"})) => anything :times 0)))

;;
;; ==============================================================================
;; Finding user data:
;; ==============================================================================
;;

(testable-privates lupapalvelu.user user-query)

(facts user-query
  (user-query "hello") => (throws AssertionError)
  (user-query nil) => (throws AssertionError)
  (user-query {}) => {}
  (user-query {:id "x"}) => {:_id "x"}
  (user-query {:email "x"}) => {:email "x"}
  (user-query {:email "XyZq"}) => {:email "xyzq"}
  (user-query {:id "x" :username "UserName" :email "Email@AddreSS.FI" :foo "BoZo"}) => {:_id "x" :username "username" :email "email@address.fi" :foo "BoZo"}
  (user-query {:organization "x"}) => {"orgAuthz.x" {$exists true}})

;;
;; jQuery data-tables:
;;

(testable-privates lupapalvelu.user users-for-datatables-base-query)

(facts users-for-datatables-base-query
  (facts "admin"
    (users-for-datatables-base-query {:role :admin} nil {}) => {}
    (users-for-datatables-base-query {:role :admin} nil {:organizations ["a" "b"]}) => {:organizations ["a" "b"]})
  (facts
    (users-for-datatables-base-query {:orgAuthz {:a #{:authorityAdmin}, :b #{:authorityAdmin}}} "a"
                                     {:organizations ["a"]}) => (contains {:organizations ["a"]})
    (users-for-datatables-base-query {:orgAuthz {:a #{:authorityAdmin}, :b #{:authorityAdmin}}} "b"
                                     {:organizations ["b"]}) => (contains {:organizations ["b"]})
    (users-for-datatables-base-query {:orgAuthz {:a #{:authorityAdmin}, :b #{:authorityAdmin}}} "a"
                                     {:organizations ["a" "b"]}) => (contains {:organizations ["a" "b"]})
    (users-for-datatables-base-query {:orgAuthz {:a #{:authorityAdmin}, :b #{:authorityAdmin}}} "a"
                                     {:organizations ["a" "b" "c"]}) => (contains {:organizations ["a" "b"]})
    (users-for-datatables-base-query {:orgAuthz {:a #{:authorityAdmin}, :b #{:authorityAdmin}}} "a"
                                     {:organizations ["c"]})
    =not=> #(contains? % :organizations)
    (users-for-datatables-base-query {:orgAuthz {:a #{:authorityAdmin}, :b #{:authorityAdmin}}} "a"
                                     {:organizations []})
    =not=> #(contains? % :organizations)
    (users-for-datatables-base-query {:orgAuthz {:a #{:authorityAdmin}, :b #{:authorityAdmin}}} "a"
                                     {:organizations nil}) => (contains {:organizations ["a"]})
    (users-for-datatables-base-query {:orgAuthz {:a #{:authorityAdmin}, :b #{:authorityAdmin}}} "a"
                                     {}) => (contains {:organizations ["a"]})
    (users-for-datatables-base-query {} nil {}) =not=> #(contains? % :organizations)))

;;
;; ==============================================================================
;; Getting non-private user data:
;; ==============================================================================
;;

(facts get-user-by-id
  (get-user-by-id ..id..) => {:id ..id.. :email ..email..}
  (provided (mongo/select-one :users {:_id ..id..}) => {:id ..id.. :email ..email.. :private ..private..}))

(facts get-user-by-email
  (get-user-by-email "email") => {:id ..id.. :email "email"}
  (provided (mongo/select-one :users {:email "email"}) => {:id ..id.. :email "email" :private ..private..}))

(facts get-user-with-password

  (fact happy-case
    (get-user-with-password "username" "password") => {:id ..id.. :enabled true}
    (provided (mongo/select-one :users {:username "username"}) => {:id ..id.. :enabled true :private {:password "from-db"}}
              (security/check-password "password" "from-db") => true))

  (fact wrong-password
    (get-user-with-password "username" "password") => nil
    (provided (mongo/select-one :users {:username "username"}) => {:id ..id.. :enabled true :private {:password "from-db"}}
              (security/check-password "password" "from-db") => false))

  (fact disabled-user
    (get-user-with-password "username" "password") => nil
    (provided (mongo/select-one :users {:username "username"}) => {:id ..id.. :enabled false :private {:password "from-db"}}))

  (fact unknown-user
    (get-user-with-password "username" "password") => nil
    (provided (mongo/select-one :users {:username "username"}) => nil)))

(facts with-user-by-email
  (fact
    (with-user-by-email "email" user) => (contains {:id "123" :email "email"})
                                      (provided (get-user-by-email "email") => {:id "123" :email "email"}))

  (fact
    (with-user-by-email ..email..) => (throws clojure.lang.ExceptionInfo #"error\.user-not-found")
                                   (provided (get-user-by-email ..email..) => nil)))

;;
;; ==============================================================================
;; User role:
;; ==============================================================================
;;

(facts "applicationpage-for"
  (applicationpage-for {:role "applicant"}) => "applicant"
  (applicationpage-for {:role "admin"}) => "admin"
  (applicationpage-for {:role "FooBarBaz"}) => "foo-bar-baz"
  (applicationpage-for {:role "authorityAdmin"}) => "authority-admin"
  (fact "authorityAdmin is resolved from orgAuthz"
    (applicationpage-for {:role "authority"}) => "authority"
    (applicationpage-for {:role "authority" :orgAuthz {:123-TEST #{:foo}}}) => "authority"
    (applicationpage-for {:role "authority" :orgAuthz {:123-TEST #{:authority}}}) => "authority"
    (applicationpage-for {:role "authority" :orgAuthz {:123-TEST #{:authorityAdmin}}}) => "authority-admin"
    (applicationpage-for {:role "authority" :orgAuthz {:123-TEST #{:authorityAdmin :authority}}}) => "authority"))

(facts user-in-role

  (fact "role is overridden"
    (user-in-role {:id 1 :role :applicant} :reader) => {:id 1 :role :reader})

  (fact "takes optional name & value parameter pair"
    (user-in-role {:id 1 :role :applicant} :reader :age 16) => {:id 1 :role :reader :age 16})

  (fact "takes optional name & value parameter pairS"
    (user-in-role {:id 1 :role :applicant} :reader :age 16 :size :L) => {:id 1 :role :reader :age 16 :size :L})

  (fact "fails with uneven optional parameter pairs"
    (user-in-role {:id 1 :role :applicant} :reader :age) => (throws Exception)))


(facts "email-recipient?"
  (email-recipient? {}) => true
  (email-recipient? {:id 1}) => false
  (provided
    (find-user {:id 1}) => nil)
  (email-recipient? {:id 2}) => true
  (provided
    (find-user {:id 2}) => {:id 2 :dummy true})
  (email-recipient? {:id 3}) => true ; no password set
  (provided
    (find-user {:id 3}) => {:id 3 :dummy false})
  (email-recipient? {:id 4}) => true
  (provided
    (find-user {:id 4}) => {:id 4 :dummy false :enabled true})
  (email-recipient? {:id 5}) => true
  (provided
    (find-user {:id 5}) => {:id 5 :dummy false :enabled true, :private {:password "foo"}})
  (email-recipient? {:id 6}) => false ; has been enabled (has password), but now disabled
  (provided
    (find-user {:id 6}) => {:id 6 :dummy false :enabled false, :private {:password "foo"}})
  (email-recipient? {:id 7}) => true
  (provided
    (find-user {:id 7}) => {:id 7 :dummy false :enabled true, :private {:password "foo"}})
  (email-recipient? {:id 8}) => true
  (provided
    (find-user {:id 8}) => {:id 8 :dummy false :enabled true, :private {:password ""}}))

(facts without-application-context
  (fact "single org-authz role"
    (:permissions (without-application-context {:user {:role "authority" :orgAuthz {:123-T ["commenter"]}}}))
    => #{:test/do :test/comment}

    (provided (lupapalvelu.permissions/get-permissions-by-role :organization "commenter") => #{:test/do :test/comment}))

  (fact "no org-authz roles"
    (:permissions (without-application-context {:user {:role "authority" :orgAuthz {}}}))
    => empty?

    (provided (lupapalvelu.permissions/get-permissions-by-role irrelevant irrelevant) => irrelevant :times 0))

  (fact "multiple org-authz roles"
    (:permissions (without-application-context {:user {:role "authority" :orgAuthz {:123-T ["commenter" "approver"]}}}))
    => #{:test/do :test/comment :test/approve}

    (provided (lupapalvelu.permissions/get-permissions-by-role :organization "commenter") => #{:test/do :test/comment})
    (provided (lupapalvelu.permissions/get-permissions-by-role :organization "approver") => #{:test/do :test/approve}))

  (fact "multiple org-authz roles in multiple organizations"
    (:permissions (without-application-context {:user {:role "authority" :orgAuthz {:123-T ["commenter" "approver"]
                                                                                    :456-T ["commenter" "tester"]}}}))
    => #{:test/do :test/comment :test/approve :test/test}

    (provided (lupapalvelu.permissions/get-permissions-by-role :organization "commenter") => #{:test/do :test/comment})
    (provided (lupapalvelu.permissions/get-permissions-by-role :organization "approver") => #{:test/do :test/approve})
    (provided (lupapalvelu.permissions/get-permissions-by-role :organization "tester") => #{:test/do :test/test}))

  (fact "application in command"
    (:permissions (without-application-context {:user        {:role "authority" :orgAuthz {:123-T ["commenter" "approver"]
                                                                                           :456-T ["commenter" "tester"]}}
                                                :application {:organization "123-T"}}))
    => empty?

    (provided (lupapalvelu.permissions/get-permissions-by-role irrelevant irrelevant) => irrelevant :times 0)))

(fact "anonymized-user gives anonymous values for all fields that should get them"
  (every? (partial contains? (#'lupapalvelu.user/anonymized-user "foo"))
          (for [[k v] @#'lupapalvelu.user/erasure-strategy :when (= v :anonymize)] k))
  => true)
