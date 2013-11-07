(ns lupapalvelu.user-itest
  (:require [lupapalvelu.user :as user]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.fixture :as fixture]
            [midje.sweet :refer :all]))

;;
;; ==============================================================================
;; Change password:
;; ==============================================================================
;;

(facts change-password
  (fixture/apply-fixture "minimal")

  (fact (user/change-password "veikko.viranomainen@tampere.fi" "passu") => nil
     (provided (security/get-hash "passu" anything) => "hash"))

  (fact (-> (user/find-user {:email "veikko.viranomainen@tampere.fi"}) :private :password) => "hash")

  (fact (user/change-password "does.not@exist.at.all" "anything") => (throws Exception #"unknown-user")))
