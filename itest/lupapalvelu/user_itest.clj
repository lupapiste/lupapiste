(ns lupapalvelu.user-itest
  (:require [lupapalvelu.user :as user]
            [lupapalvelu.security :as security]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

;;
;; ==============================================================================
;; Change password:
;; ==============================================================================
;;

(facts change-password
  (apply-remote-minimal)
  
  (fact (user/change-password "veikko.viranomainen@tampere.fi" "passu") => nil
     (provided (security/get-hash "passu" anything) => "hash"))
  
  (fact (-> (user/find-user {:email "veikko.viranomainen@tampere.fi"}) :private :password) => "hash")
  
  (fact (user/change-password "does.not@exist.at.all" "anything") => (throws Exception #"unknown-user")))
