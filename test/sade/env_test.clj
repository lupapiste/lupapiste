(ns sade.env-test
  (:require [sade.env :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.java.io :as io]))

(testable-privates sade.env parse-target-env)

(facts "target environment ID is parsed from build tag"
  (fact (parse-target-env "lupapiste - PROD - Build") => "PROD")
  (fact (parse-target-env "lupapiste - TEST - Build") => "TEST")
  (fact (parse-target-env "lupapiste - DEV - Build") => "DEV")
  (fact (parse-target-env "lupapiste-prod-build") => "prod")
  (fact (parse-target-env "lupapiste-qa-build") => "qa")
  (fact (parse-target-env "lupapiste-test-build") => "test")
  (fact (parse-target-env "lupapiste-dev-build") => "dev")
  (fact (parse-target-env "local") => "local")
  (fact (parse-target-env "") => "local")
  (fact (parse-target-env nil) => "local"))

(testable-privates sade.env read-config)

(fact "Password is decrypted and can be found in a sub map"
  (read-config "testpassword" (io/input-stream (io/resource "lupapalvelu/nested.properties")) )
  =>
  {:mongodb {:uri "mongodb://127.0.0.1/lupapiste"
             :credentials {:username "lupapiste"
                           :password "lupapassword"}}
   :a {:b {:c {:d {:e {:f {:g 1 :h 2 :i true :j false :k "str"}}}}}}})

(facts "feature flags"

  (fact "feature is on"

    (feature? :a) => true
    (provided (get-config) => {:feature {:a true}})

    (feature? :a) => true
    (provided (get-config) => {:feature {:a "true"}}))

  (fact "feature is not on"

    (feature? :a) => false
    (provided (get-config) => {:feature {:a false}})

    (feature? :a) => false
    (provided (get-config) => {:feature {:a "totta"}})

    (feature? :a) => false
    (provided (get-config) => {:feature false})

    (feature? :a) => false
    (provided (get-config) => {}))

  (fact "enabling & disabling features"
    (feature?         :k :i :k :k :a) => false
    (enable-feature!  :k :i :k :k :a)
    (feature?         :k :i :k :k :a) => true
    (disable-feature! :k :i :k :k :a)
    (feature?         :k :i :k :k :a) => false))

(facts "features"
  (features) => {:a true
                 :b false
                 :c {:c1 true
                     :c2 true
                     :c3 false}}
  (provided
    (get-config) => {:feature {:a "true"
                               :b false
                               :c {:c1 "true"
                                   :c2 true
                                   :c3 nil}}}))

