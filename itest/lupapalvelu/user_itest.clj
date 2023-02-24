(ns lupapalvelu.user-itest
  (:require [lupapalvelu.fixture.core :refer [apply-fixture]]
            [lupapalvelu.user :as user]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :as itu :refer [apply-remote-minimal pena upload-user-attachment]]
            [sade.core :refer [now]]
            [midje.sweet :refer :all]
            [mount.core :as mount]))

(def db-name (str "test_user-itest_" (now)))

;;
;; ==============================================================================
;; Change password:
;; ==============================================================================
;;


(facts change-password
  (mount/start #'mongo/connection)
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
      (user/change-password "does.not@exist.at.all" "anything") => (throws Exception #"user-not-found"))))

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
        (Thread/sleep 1111)                                 ;; Should be >> value of throttle expires
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



(facts "Rest api creation"
  (mongo/with-db db-name
    (let [user {:username     "foobar"
                :organization "123-A"
                :email        "foobar@example.com"
                :firstName    "Testi"
                :lastName     "Testaaja"}]
      (against-background
        [(lupapalvelu.security/random-password) => "salainen"
         (lupapalvelu.security/get-hash "salainen") => "tosisalainen"
         (lupapalvelu.mongo/create-id) => "123456"
         (lupapalvelu.organization/known-organizations? [:123-A]) => true]
        (fact "id, username and password is returned"
          (let [saved-user (user/create-rest-user {:role "admin"} user)]
            saved-user => (just {:username "foobar"
                                 :password "salainen"} :in-any-order))))
      (fact "User is saved as rest api user"
        (:role (user/get-user-by-email (:email user))) => "rest-api"))))

;;
;; ==============================================================================
;; Erase user information
;; ==============================================================================
;;

(facts erase-user
  (mongo/with-db db-name
    (apply-remote-minimal)
    (apply-fixture "minimal")
    (itu/with-local-actions
      (upload-user-attachment pena "osapuolet.cv" true)
      (upload-user-attachment pena "osapuolet.tutkintotodistus" true)

      (let [id "777777777777777777000020"                   ; pena
            obfuscated-username "poistunut_777777777777777777000020@example.com"
            attachments (:attachments (user/get-user-by-id id {:attachments 1}))]
        (doseq [{:keys [attachment-id]} attachments]
          (storage/user-attachment-exists? id attachment-id) => true)

        (user/erase-user id) => nil
        (user/get-user-by-id id) => {:id        id
                                     :firstName "Poistunut"
                                     :lastName  "K\u00e4ytt\u00e4j\u00e4"
                                     :role      "applicant"
                                     :email     obfuscated-username
                                     :username  obfuscated-username
                                     :enabled   false
                                     :state     "erased"}
        (doseq [{:keys [attachment-id]} attachments]
          (storage/user-attachment-exists? id attachment-id) => false)))))
