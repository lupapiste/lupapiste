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

#_(facts
  (against-background
    (session/get :user) => :session-user
    (security/login-with-apikey "123") => :apikey-user)
  (current-user {}) => :session-user
  (current-user {:apikey "123"}) => :apikey-user (provided (session/get :user) => nil))


#_(deftest login-tests
  (testing "can't login with bad credentials"
           (is nil? (login "tommi" "<<hacked>>")))
  (testing "can login with right credentials"
           (is not (nil? (login "tommi" "abba")))))
  
#_(deftest apikey-tests
  (testing "can't login with bad apikey"
           (is nil? (login-with-apikey "<<hacked>>")))
  (testing "can login with right apikey"
           (is not (nil? (login-with-apikey "502cb9e58426c613c8b85abc")))))
