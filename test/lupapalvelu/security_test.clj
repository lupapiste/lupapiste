(ns lupapalvelu.security-test
  (:use clojure.test
        lupapalvelu.security))

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
