(ns lupapalvelu.user-itest
  (:require [lupapalvelu.user :as user]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.fixture.core :as fixture]
            [sade.core :refer [now]]
            [midje.sweet :refer :all]))

(def db-name (str "test_user-itest_" (now)))

;;
;; ==============================================================================
;; Change password:
;; ==============================================================================
;;


(facts change-password
  (mongo/connect!)
  (mongo/with-db db-name (fixture/apply-fixture "minimal"))

  (fact
      (mongo/with-db db-name
        (user/change-password "veikko.viranomainen@tampere.fi" "passu") => nil
        (provided (security/get-hash "passu" anything) => "hash")))

  (fact
      (mongo/with-db db-name
        (-> (user/find-user {:email "veikko.viranomainen@tampere.fi"}) :private :password) => "hash"))

  (fact 
      (mongo/with-db db-name
        (user/change-password "does.not@exist.at.all" "anything") => (throws Exception #"unknown-user"))))

;;
;; ==============================================================================
;; Login Throttle:
;; ==============================================================================
;;


(facts login-trottle
  (mongo/with-db db-name
    (against-background 
     [(sade.env/value :login :allowed-failures) => 2
      (sade.env/value :login :throttle-expires) => 1] 
     (fact "First failure doesn't lock username"
       (user/throttle-login? "foo") => false
       (user/login-failed "foo") => nil
       (user/throttle-login? "foo") => false)
     (fact "Second failure locks username"
       (user/login-failed "foo") => nil
       (user/throttle-login? "foo") => true)
     (fact "Lock expires after timeout"
       (Thread/sleep 1001)
       (user/throttle-login? "foo") => false))))

(facts clear-login-trottle
  (mongo/with-db db-name
    (against-background 
     [(sade.env/value :login :allowed-failures) => 1
      (sade.env/value :login :throttle-expires) => 10] 
     (fact (user/throttle-login? "bar") => false
           (user/login-failed "bar") => nil
           (user/throttle-login? "bar") => true
           (user/clear-logins "bar") => true
           (user/throttle-login? "bar") => false))))
