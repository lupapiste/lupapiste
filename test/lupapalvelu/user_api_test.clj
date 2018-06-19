(ns lupapalvelu.user-api-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer [ok?]]
            [lupapalvelu.api-common :refer [execute-command]]
            [lupapalvelu.user-api :refer :all]
            [lupapalvelu.itest-util :refer [expected-failure? unauthorized?]]
            [lupapalvelu.states :as states]))


;;
;; ==============================================================================
;; Updating user data:
;; ==============================================================================
;;

(testable-privates lupapalvelu.user-api validate-update-user! allowed-state?)

(fact "filter-storage-key and default-filter-storage-key keys match"
  (set (keys filter-storage-key)) => (set (keys default-filter-storage-key)))

(def admin-data {:role "admin" :email "admin"})

(facts "validate-update-user!"
  (facts "admin can change only others data"
    (fact (validate-update-user! admin-data {:email "admin"}) => unauthorized?)
    (fact (validate-update-user! admin-data {:email "foo"})   => truthy))
  (fact "non-admin users can change only their own data"
    (fact (validate-update-user! {:role ..anything.. :email "foo"} {:email "foo"}) => truthy)
    (fact (validate-update-user! {:role ..anything.. :email "foo"} {:email "bar"}) => unauthorized?)))

;;
;; ==============================================================================
;; Adding user attachments:
;; ==============================================================================
;;

(testable-privates lupapalvelu.user-api add-user-attachment-allowed?)

(def applicant-user-data {:role "applicant"
                          :id "777777777777777777000020"
                          :username "pena"
                          :email "pena@example.com"
                          :firstName "Pena"
                          :lastName "Panaani"
                          :personId "010203-040A"
                          :attachments []
                          :phone "0102030405"
                          :city "Piippola"
                          :street "Paapankuja 12"
                          :zip "10203"
                          :enabled true})

(def authority-user-data {:role "authority"
                          :id "777777777777777777000023"
                          :username "sonja"
                          :email "sonja.sibbo@sipoo.fi"
                          :firstName "Sonja"
                          :lastName "Sibbo"
                          :orgAuthz {:753-R #{:authority}
                                     :753-YA #{:authority}
                                     :998-R-TESTI-2 #{:authority}}
                          :enabled true})

(facts "add-user-attachment-allowed?"
  (fact "applicant"
    (add-user-attachment-allowed? applicant-user-data) => truthy)
  (fact "authority"
    (add-user-attachment-allowed? authority-user-data) => falsey))

(facts "allowed-state?"
  (facts "pre-sent states"
    (doseq [state states/pre-sent-application-states]
      (fact {:midje/description state}
        (allowed-state? state {:applicationState irrelevant}) => true)))
  (facts "post-verdict states"
    (doseq [state states/post-verdict-states]
      (fact {:midje/description state}
        (allowed-state? state {:applicationState :draft}) => false
        (allowed-state? state {:applicationState :sent}) => false
        (allowed-state? state {:applicationState state}) => true)))
  (fact "sent"
    (allowed-state? :sent {:applicationState irrelevant}) => true))

(def not-found (partial expected-failure? :error.email-not-found))

(facts "reset-password"
  (facts "successful"
    (fact "enabled user"
      (execute-command "reset-password" {:email "foo@example.com"} {:scheme "http"}) => ok?
      (provided
        (lupapalvelu.password-reset/reset-password anything) => nil :times 1
        (lupapalvelu.user/get-user-by-email "foo@example.com") => {:enabled true :role "foo"}
        (sade.dns/email-and-domain-valid? "foo@example.com") => true))
    (fact "as long as enabled (or not dummy)"
      (execute-command "reset-password" {:email "foo@example.com"} {:scheme "http"}) => ok?
      (provided
        (lupapalvelu.password-reset/reset-password anything) => nil :times 1
        (lupapalvelu.user/get-user-by-email "foo@example.com") => {:enabled true}
        (sade.dns/email-and-domain-valid? "foo@example.com") => true)))
  (facts "unsuccessful"
    (fact "unknown user"
      (execute-command "reset-password" {:email "foo@example.com"} {:scheme "http"}) => not-found
      (provided
        (lupapalvelu.password-reset/reset-password anything) => nil :times 0
        (lupapalvelu.user/get-user-by-email "foo@example.com") => nil
        (sade.dns/email-and-domain-valid? "foo@example.com") => true))
    (fact "disabled user"
      (execute-command "reset-password" {:email "foo@example.com"} {:scheme "http"}) => not-found
      (provided
        (lupapalvelu.password-reset/reset-password anything) => nil :times 0
        (lupapalvelu.user/get-user-by-email "foo@example.com") => {:enabled false :role "foo"}
        (sade.dns/email-and-domain-valid? "foo@example.com") => true))
    (fact "dummy user"
      (execute-command "reset-password" {:email "foo@example.com"} {:scheme "http"}) => not-found
      (provided
        (lupapalvelu.password-reset/reset-password anything) => nil :times 0
        (lupapalvelu.user/get-user-by-email "foo@example.com") => {:enabled false :role "dummy"}
        (sade.dns/email-and-domain-valid? "foo@example.com") => true))
    (fact "dummy user enabled"
      (execute-command "reset-password" {:email "foo@example.com"} {:scheme "http"}) => not-found
      (provided
        (lupapalvelu.password-reset/reset-password anything) => nil :times 0
        (lupapalvelu.user/get-user-by-email "foo@example.com") => {:enabled true :role "dummy"}
        (sade.dns/email-and-domain-valid? "foo@example.com") => true))
    (fact "dummy user enabled"
      (execute-command "reset-password" {:email "foo@example.com"} {:scheme "http"}) => not-found
      (provided
        (lupapalvelu.password-reset/reset-password anything) => nil :times 0
        (lupapalvelu.user/get-user-by-email "foo@example.com") => {:enabled true :role "dummy"}
        (sade.dns/email-and-domain-valid? "foo@example.com") => true)))
  (facts "admin"
    (fact "can reset also disabled"
      (execute-command "admin-reset-password" {:email "foo@example.com"} {:scheme "http" :user {:role "admin"}}) => ok?
      (provided
        (lupapalvelu.password-reset/reset-password anything) => nil :times 1
        (lupapalvelu.user/get-user-by-email "foo@example.com") => {:enabled false :role "foo"}
        (sade.dns/email-and-domain-valid? "foo@example.com") => true))
    (fact "but not dummy"
      (execute-command "admin-reset-password" {:email "foo@example.com"} {:scheme "http" :user {:role "admin"}}) => not-found
      (provided
        (lupapalvelu.password-reset/reset-password anything) => nil :times 0
        (lupapalvelu.user/get-user-by-email "foo@example.com") => {:enabled true :role "dummy"}
        (sade.dns/email-and-domain-valid? "foo@example.com") => true))
    (fact "nor 'nothing'"
      (execute-command "admin-reset-password" {:email "foo@example.com"} {:scheme "http" :user {:role "admin"}}) => not-found
      (provided
        (lupapalvelu.password-reset/reset-password anything) => nil :times 0
        (lupapalvelu.user/get-user-by-email "foo@example.com") => nil
        (sade.dns/email-and-domain-valid? "foo@example.com") => true))))

