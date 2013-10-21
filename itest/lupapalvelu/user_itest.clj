(ns lupapalvelu.user-itest
  (:require [lupapalvelu.user :refer :all]
            [lupapalvelu.security :as security]
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
     (provided (security/dispense-salt) => "salt"
               (security/get-hash "passu" "salt") => "hash"))
  (fact (find-user :email "veikko.viranomainen@tampere.fi") => (contains {:private (contains {:salt "salt"
                                                                                              :password "hash"})}))
  
  (fact (change-password "does.not@exist.at.all" "anything") => (throws Exception #"unknown-user")))

