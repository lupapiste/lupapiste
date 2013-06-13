(ns lupapalvelu.security-test
  (:use clojure.test
        midje.sweet
        lupapalvelu.security)
  (:require [noir.session :as session]
            [lupapalvelu.security :as security]))


(facts "non-private"
  (fact "strips away private keys from map"
    (non-private {:name "tommi" :private {:secret "1234"}}) => {:name "tommi"})
  (fact ".. but not non-recursively"
    (non-private {:name "tommi" :child {:private {:secret "1234"}}}) => {:name "tommi" :child {:private {:secret "1234"}}}))

(let [user {:id "1"
            :firstName "Simo"
            :username  "simo@salminen.com"
            :lastName "Salminen"
            :role "comedian"
            :private "SECRET"}]
  (facts "summary"
    (fact (summary nil) => nil)
    (fact (summary user) => (just (dissoc user :private)))))

(facts "roles"
  (fact "authority"
    (authority? {:role "authority"}) => true
    (authority? {:role :authority}) => true
    (authority? {}) => false)
  (fact "applicant"
    (applicant? {:role "applicant"}) => true
    (applicant? {:role :applicant}) => true
    (applicant? {}) => false))

