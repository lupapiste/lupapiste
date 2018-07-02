(ns lupapalvelu.web-test
  (:require [lupapalvelu.web :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.fixture.minimal :as minimal])
  (:import [org.apache.commons.io IOUtils]))

(testable-privates lupapalvelu.web ->hashbang user-has-org-role?)

(facts "facts about hashbang parsing"
  (->hashbang nil)               => nil
  (->hashbang [])                => nil
  (->hashbang [nil])             => nil
  (->hashbang "http://foo")      => nil
  (->hashbang 1234)              => nil
  (->hashbang {:foo "bar"})      => nil
  (->hashbang "foo")             => "foo"
  (->hashbang "/foo")            => "foo"
  (->hashbang "!/foo")           => "foo"
  (->hashbang "%21/foo")         => "foo"
  (->hashbang "#!/foo")          => "foo"
  (->hashbang "/../../foo")      => "foo"
  (->hashbang "/../../#!foo")    => "foo"
  (->hashbang ["#!/foo" "#!/bar"]) => "bar")

(fact {:a 1 :b 2}
  => (and (contains {:a 1})
          (contains {:b 2})))

(facts "parse-json-body"
  (parse-json-body {:body (IOUtils/toInputStream "{\"a\": true, \"b\": 42}")
                    :content-type "application/json;charset=utf-8"})
    => (contains {:params {:a true :b 42}
                  :json {:a true :b 42}}))

(facts "user-has-org-role? test"
  (let [auth  (->> minimal/users
                   (filter (comp (partial = "sonja.sibbo@sipoo.fi") :email))
                   (first))
        admin (->> minimal/users
                   (filter (comp (partial = "admin@sipoo.fi") :email))
                   (first))]
    (user-has-org-role? :authorityAdmin auth) => falsey
    (user-has-org-role? :authorityAdmin admin) => truthy)

  (let [user {:role "authority"
              :orgAuthz {:org1 #{:foo}
                         :org2 #{:bar}}}]
    (user-has-org-role? :foo user) => truthy
    (user-has-org-role? :bar user) => truthy
    (user-has-org-role? :boz user) => falsey))
