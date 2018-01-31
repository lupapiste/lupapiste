(ns lupapalvelu.smoketest-test
  (:require [midje.sweet :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [sade.schema-generators :as ssg]
            [lupapalvelu.test-util :refer [passing-quick-check]]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.smoketest.application-smoke-tests :refer [validate-auth-array]]
            [lupapalvelu.user :as usr]))


(fact :qc "auths are valid"
  (tc/quick-check
    150
    (prop/for-all [auths (gen/vector-distinct-by :id (ssg/generator auth/Auth) {:min-elements 1 :max-elements 10})]
      (validate-auth-array {:auth auths}) => nil)
    :max-size 30) => passing-quick-check)

(def duplicate-auth-id-prop
  (prop/for-all [auths (-> (ssg/generator auth/Auth {usr/Id (gen/return "123")})
                           (gen/vector 2 10))]
    (let [errors (validate-auth-array {:auth auths})]
      (fact "has errors"
        errors =not=> empty?
        errors => (has every? #(= "123" (:auth-id %)))))))

#_(fact :ac "auth with duplicate id's are not valid"        ; FIXME enable after LPK-3564
  (tc/quick-check
    150
    duplicate-auth-id-prop
    :max-size 30) => passing-quick-check)
