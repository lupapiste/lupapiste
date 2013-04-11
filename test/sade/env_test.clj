(ns sade.env-test
  (:use sade.env
        midje.sweet))

(def parse-target-env #'sade.env/parse-target-env)

(facts "target environment ID is parsed from build tag"
  (fact (parse-target-env "lupapiste - PROD - Build") => "PROD")
  (fact (parse-target-env "lupapiste - TEST - Build") => "TEST")
  (fact (parse-target-env "lupapiste - DEV - Build") => "DEV")
  (fact (parse-target-env "local") => "local")
  (fact (parse-target-env "") => "local")
  (fact (parse-target-env nil) => "local"))

(fact "Password is decrypted and can be found in a sub map"
  (read-config "lupapalvelu/nested.properties" "testpassword")
  =>
  {:mongodb {:uri "mongodb://127.0.0.1/lupapiste"
             :credentials {:username "lupapiste"
                           :password "lupapassword"}}
   :a {:b {:c {:d {:e {:f {:g 1 :h 2 :i true :j false :k "str"}}}}}}})
