(ns lupapalvelu.user-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :as itu :refer [pena upload-user-attachment with-test-system]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.security :as security]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.user :as user]
            [midje.sweet :refer :all]
            [sade.env :as env]))

(set! *warn-on-reflection* true)

;;
;; ==============================================================================
;; Change password:
;; ==============================================================================
;;

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts "change-password"
    (fact "setup"
      (let [user (user/find-user {:email "veikko.viranomainen@tampere.fi"})]
        user => some?
        (-> user :password-set-at) => nil?))

    (fact "too short password throws"
      (user/change-password "veikko.viranomainen@tampere.fi" "passu") => (throws Exception #"minlength"))

    (fact "too simple password throws"
      (user/change-password "veikko.viranomainen@tampere.fi" "passupassupassu") => (throws Exception #"minscore"))

    (fact "happy path"
      (user/change-password "veikko.viranomainen@tampere.fi" "Pa24234SDdfekGVD") => nil
      (provided (security/get-hash "Pa24234SDdfekGVD" anything) => "hash"))

    (fact "user can be found"
      (let [user (user/find-user {:email "veikko.viranomainen@tampere.fi"})]
        (-> user :private :password) => "hash"
        (-> user :password-set-at) => int?))

    (fact "unknown user"
      (user/change-password "does.not@exist.at.all" "anything") => (throws Exception #"user-not-found")))

  ;;
  ;; ==============================================================================
  ;; Login Throttle:
  ;; ==============================================================================
  ;;


  (facts "login-trottle"
    (against-background
        [(env/value :login :allowed-failures) => 2
         (env/value :login :throttle-expires) => 1]
      (fact "First failure doesn't lock username"
        (user/throttle-login? "foo") => false
        (user/login-failed "foo") => nil
        (user/throttle-login? "foo") => false)
      (fact "Second failure locks username"
        (user/login-failed "foo") => nil
        (user/throttle-login? "foo") => true)
      (fact "Lock expires after timeout"
        (Thread/sleep 1111)                                 ;; Should be >> value of throttle expires
        (user/throttle-login? "foo") => false)))

  (facts "clear-login-trottle"
    (against-background
        [(env/value :login :allowed-failures) => 1
         (env/value :login :throttle-expires) => 10]
      (fact (user/throttle-login? "bar") => false
            (user/login-failed "bar") => nil
            (user/throttle-login? "bar") => true
            (user/clear-logins "bar") => true
            (user/throttle-login? "bar") => false)))

  (facts "Rest api creation"
    (let [user {:username     "foobar"
                :organization "123-A"
                :email        "foobar@example.com"
                :firstName    "Testi"
                :lastName     "Testaaja"}]
      (against-background
          [(lupapalvelu.security/random-password) => "salainen"
           (lupapalvelu.security/get-hash "salainen") => "tosisalainen"
           (lupapalvelu.mongo/create-id) => "123456"
           (org/known-organizations? [:123-A]) => true]
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

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts "erase-user"
    (itu/with-local-actions
      (upload-user-attachment pena "osapuolet.cv" true)
      (upload-user-attachment pena "osapuolet.tutkintotodistus" true)

      (let [id                  "777777777777777777000020"                   ; pena
            obfuscated-username "poistunut_777777777777777777000020@example.com"
            attachments         (:attachments (user/get-user-by-id id {:attachments 1}))]
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
