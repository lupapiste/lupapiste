(ns sade.env-test
  (:use sade.env
        midje.sweet))

(facts
  (against-background (buildinfo :build-tag) => "lupapiste - PROD - Build")
  (fact (prop-file) => "prod.properties"))
(facts
  (against-background (buildinfo :build-tag) => "lupapiste - TEST - Build")
  (fact (prop-file) => "test.properties"))
(facts
  (against-background (buildinfo :build-tag) => "lupapiste - DEV - Build")
  (fact (prop-file) => "dev.properties"))
(facts
  (against-background (buildinfo :build-tag) => "local")
  (fact (prop-file) => "local.properties"))
(facts
  (against-background (buildinfo :build-tag) => "")
  (fact (prop-file) => "local.properties"))
(facts
  (against-background (buildinfo :build-tag) => nil)
  (fact (prop-file) => "local.properties"))

(fact "Password is decrypted and can be found in a sub map"
  (read-config "lupapalvelu/nested.properties" "testpassword")
  =>
  {:mongodb {:uri "mongodb://127.0.0.1/lupapiste"
             :credentials {:username "lupapiste"
                           :password "lupapassword"}}
   :a {:b {:c {:d {:e {:f {:g 1 :h 2 :i true :j false :k "str"}}}}}}})
