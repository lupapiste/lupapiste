(ns lupapalvelu.user-itest
  (:require [lupapalvelu.user :refer :all]
            [lupapalvelu.security :as security]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

;;
;; ==============================================================================
;; Creating API keys:
;; ==============================================================================
;;

(facts create-apikey
  (apply-remote-minimal)
  
  (fact (-> (find-user :email "tampere-ya") :private :apikey) => nil)
  (fact (create-apikey "tampere-ya") =not=> nil)
  (fact (-> (find-user :email "tampere-ya") :private :apikey) =not=> nil)
  
  (fact (create-apikey "does.not@exist.at.all") => (throws Exception #"unknown-user")))

;;
;; ==============================================================================
;; Change password:
;; ==============================================================================
;;

(facts change-password
  (apply-remote-minimal)
  
  (fact (change-password "veikko.viranomainen@tampere.fi" "passu") => nil
     (provided (security/get-hash "passu" anything) => "hash"))
  (fact (-> (find-user :email "veikko.viranomainen@tampere.fi") :private :password) => "hash")
  
  (fact (change-password "does.not@exist.at.all" "anything") => (throws Exception #"unknown-user")))

;;
;; ==============================================================================
;; Creating users:
;; ==============================================================================
;;

(facts create-new-user
  (apply-remote-minimal)
  (fact (create-new-user {:email "foo@bar.com"}) => (contains {:email "foo@bar.com" :role "dummy"}))
  (fact (create-new-user {:email "foo@bar.com" :role "dorka"}) => (contains {:email "foo@bar.com" :role "dorka"}))
  (fact (create-new-user {:email "foo@bar.com"}) => (throws Exception #"username"))
  
  (fact (create-new-user {:email "foo2@bar.com" :personId "123"}) => truthy)
  (fact (create-new-user {:email "foo3@bar.com" :personId "123"}) => (throws Exception #"personId")))
