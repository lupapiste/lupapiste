(ns lupapalvelu.user-api-test
  (:require [lupapalvelu.api-common :refer [execute-command]]
            [lupapalvelu.lockdown :as lockdown]
            [lupapalvelu.organization :as org]
            [lupapalvelu.password-reset :as pw-reset]
            [lupapalvelu.states :as states]
            [lupapalvelu.test-util :refer [expected-failure?]]
            [lupapalvelu.token :as token]
            [lupapalvelu.user :as user]
            [lupapalvelu.user-api :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer [ok?]]
            [sade.dns :as dns]))

;;
;; ==============================================================================
;; Updating user data:
;; ==============================================================================
;;

(testable-privates lupapalvelu.user-api allowed-state?)

(fact "filter-storage-key and default-filter-storage-key keys match"
  (set (keys filter-storage-key)) => (set (keys default-filter-storage-key)))

(def admin-data {:role "admin" :email "admin"})


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
                          :zip "92620"
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
    (add-user-attachment-allowed? authority-user-data) => truthy)
  (fact "dummy"
    (add-user-attachment-allowed? {:role "dummy"}) => falsey)
  (fact "docstore-user"
    (add-user-attachment-allowed? {:role "docstore-api"}) => falsey))

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
  (against-background [(lockdown/lockdown?) => false]
    (facts "successful"
      (fact "enabled user"
        (execute-command "reset-password" {:email "foo@example.com"} {:scheme "http" :system {}}) => ok?
        (provided
          (token/count-password-reset-tokens-by-email anything anything) => 0
          (lupapalvelu.password-reset/reset-password anything anything) => nil :times 1
          (lupapalvelu.user/get-user-by-email "foo@example.com") => {:enabled true :role "foo"}
          (sade.dns/email-and-domain-valid? "foo@example.com") => true))
      (fact "enabled user, but too many reset attempts"
        (execute-command "reset-password" {:email "foo@example.com"} {:scheme "http" :system {}}) => ok?
        (provided
          (token/count-password-reset-tokens-by-email anything anything) => 5
          (lupapalvelu.password-reset/reset-password anything anything) => nil :times 0
          (lupapalvelu.user/get-user-by-email "foo@example.com") => {:enabled true :role "foo"}
          (sade.dns/email-and-domain-valid? "foo@example.com") => true))
      (fact "as long as enabled (or not dummy)"
        (execute-command "reset-password" {:email "foo@example.com"} {:scheme "http" :system {}}) => ok?
        (provided
          (token/count-password-reset-tokens-by-email anything anything) => 0
          (lupapalvelu.password-reset/reset-password anything anything) => nil :times 1
          (lupapalvelu.user/get-user-by-email "foo@example.com") => {:enabled true}
          (sade.dns/email-and-domain-valid? "foo@example.com") => true
          )))
    (facts "unsuccessful"
      (fact "unknown user"
        (execute-command "reset-password" {:email "foo@example.com"} {:scheme "http" :system {}}) => ok?
        (provided
          (pw-reset/reset-password anything anything) => nil :times 0
          (user/get-user-by-email "foo@example.com") => nil
          (dns/email-and-domain-valid? "foo@example.com") => true))
      (fact "disabled user"
        (execute-command "reset-password" {:email "foo@example.com"} {:scheme "http" :system {}}) => ok?
        (provided
          (pw-reset/reset-password anything anything) => nil :times 0
          (user/get-user-by-email "foo@example.com") => {:enabled false :role "foo"}
          (dns/email-and-domain-valid? "foo@example.com") => true))
      (fact "dummy user"
        (execute-command "reset-password" {:email "foo@example.com"} {:scheme "http" :system {}}) => ok?
        (provided
          (pw-reset/reset-password anything anything) => nil :times 0
          (user/get-user-by-email "foo@example.com") => {:enabled false :role "dummy"}
          (dns/email-and-domain-valid? "foo@example.com") => true))
      (fact "dummy user enabled"
        (execute-command "reset-password" {:email "foo@example.com"} {:scheme "http" :system {}}) => ok?
        (provided
          (pw-reset/reset-password anything anything) => nil :times 0
          (user/get-user-by-email "foo@example.com") => {:enabled true :role "dummy"}
          (dns/email-and-domain-valid? "foo@example.com") => true)))
    (facts "admin"
      (fact "can reset also disabled"
        (execute-command "admin-reset-password"
                         {:email "foo@example.com"}
                         {:scheme "http"
                          :user   {:role "admin"}
                          :system {:config {:host "https://localhost"}}}) => ok?
        (provided
          (pw-reset/reset-password anything anything {:token-only? true}) => nil :times 1
          (user/get-user-by-email "foo@example.com") => {:enabled false :role "foo"}))
      (fact "but not dummy"
        (execute-command "admin-reset-password" {:email "foo@example.com"} {:scheme "http" :user {:role "admin"} :system {}}) => not-found
        (provided
          (pw-reset/reset-password anything anything {:token-only? true}) => nil :times 0
          (user/get-user-by-email "foo@example.com") => {:enabled true :role "dummy"}))
      (fact "nor 'nothing'"
        (execute-command "admin-reset-password" {:email "foo@example.com"} {:scheme "http" :user {:role "admin"} :system {}}) => not-found
        (provided
          (pw-reset/reset-password anything anything {:token-only? true}) => nil :times 0
          (user/get-user-by-email "foo@example.com") => nil)))))

(facts "updated oauth-roles"
  (against-background
    [(#'lupapalvelu.user-api/org-id->allowed-oauth-roles anything) => ["director" "reporter"]
     (lockdown/lockdown?) => false])

  (fact "allowed roles can be added"
    (#'lupapalvelu.user-api/updated-oauth-roles {:oauth-roles []} "753-R" ["director" "reporter"])
    => [{:organization "753-R", :client-type "bi-app", :role "director"}
        {:organization "753-R", :client-type "bi-app", :role "reporter"}])

  (fact "allowed roles can be removed"
    (#'lupapalvelu.user-api/updated-oauth-roles
      {:oauth-roles [{:organization "753-R", :client-type "bi-app", :role "director"}
                     {:organization "753-R", :client-type "bi-app", :role "reporter"}]}
      "753-R"
      ["reporter"])
    => [{:organization "753-R", :client-type "bi-app", :role "reporter"}])

  (fact "not allowed roles cannot be added"
    (#'lupapalvelu.user-api/updated-oauth-roles {:oauth-roles []} "753-R" ["departmental"]) => [])

  (fact "not allowed roles will not be removed"
    (#'lupapalvelu.user-api/updated-oauth-roles
      {:oauth-roles [{:organization "753-R", :client-type "docdepartmental", :role "departmental"}
                     {:organization "753-R", :client-type "bi-app", :role "reporter"}]}
      "753-R"
      ["director"])
    => [{:organization "753-R", :client-type "docdepartmental", :role "departmental"}
        {:organization "753-R", :client-type "bi-app", :role "director"}])

  (fact "roles in other organizations stay untouched whether allowed or not"
    (#'lupapalvelu.user-api/updated-oauth-roles
      {:oauth-roles [{:organization "092-R", :client-type "docdepartmental", :role "departmental"}
                     {:organization "092-R", :client-type "bi-app", :role "reporter"}
                     {:organization "753-R", :client-type "bi-app", :role "reporter"}]}
      "753-R"
      ["director"])
    => [{:organization "092-R", :client-type "docdepartmental", :role "departmental"}
        {:organization "092-R", :client-type "bi-app", :role "reporter"}
        {:organization "753-R", :client-type "bi-app", :role "director"}]))

(facts "Reporing link in header"
  (against-background
    [(org/get-organization "bi-app-org" [:reporting-system]) => {:reporting-system "bi-app"}
     (org/get-organization "bi-app2-org" [:reporting-system]) => {:reporting-system "bi-app2"}
     (org/get-organization "no-bi-app-org" [:reporting-system]) => {:reporting-system nil}])

  (fact "is shown to authority in organization with bi-app2"
    (show-reporting-link? {:orgAuthz {:bi-app2-org ["authority"]}}) => true)

  (fact "is shown to reader or commenter in organization with bi-app2"
    (show-reporting-link? {:orgAuthz {:bi-app2-org ["reader"]}}) => true
    (show-reporting-link? {:orgAuthz {:bi-app2-org ["commenter"]}}) => true)

  (fact "is NOT shown to authorityAdmin or biller in organization with bi-app2"
    (show-reporting-link? {:orgAuthz {:bi-app2-org ["authorityAdmin"]}}) => false
    (show-reporting-link? {:orgAuthz {:bi-app2-org ["biller"]}}) => false)

  (fact "is NOT shown to anyone in organizations with old bi-app or no bi-app"
    (show-reporting-link? {:orgAuthz {:bi-app-org ["authority"]}}) => false
    (show-reporting-link? {:orgAuthz {:no-bi-app-org ["authority"]}}) => false

    (show-reporting-link? {:orgAuthz {:bi-app-org ["reader"]}}) => false
    (show-reporting-link? {:orgAuthz {:no-bi-app-org ["reader"]}}) => false

    (show-reporting-link? {:orgAuthz {:bi-app-org ["commenter"]}}) => false
    (show-reporting-link? {:orgAuthz {:no-bi-app-org ["commenter"]}}) => false

    (show-reporting-link? {:orgAuthz {:bi-app-org ["biller"]}}) => false
    (show-reporting-link? {:orgAuthz {:no-bi-app-org ["biller"]}}) => false)

  (fact "is shown to users with reporting oauth-roles in organizations with bi-app2"
    (show-reporting-link? {:oauth-roles [{:organization "bi-app2-org" :client-type "bi-app" :role "reporter"}]}) => true
    (show-reporting-link? {:oauth-roles [{:organization "bi-app2-org" :client-type "bi-app" :role "director"}]}) => true)

  (fact "is NOT shown to users with other oauth-roles in organizations with bi-app2"
    (show-reporting-link? {:oauth-roles [{:organization "bi-app2-org" :client-type "bi-app" :role "departmental"}]}) => false)

  (fact "is NOT shown to users without reporting oauth-roles in organizations with bi-app2"
    (show-reporting-link? {:oauth-roles [{:organization "bi-app-org" :client-type "bi-app" :role "departmental"}]}) => false
    (show-reporting-link? {:oauth-roles [{:organization "bi-app-org" :client-type "bi-app" :role "reporter"}]}) => false
    (show-reporting-link? {:oauth-roles [{:organization "bi-app-org" :client-type "bi-app" :role "director"}]}) => false

    (show-reporting-link? {:oauth-roles [{:organization "no-bi-app-org" :client-type "bi-app" :role "departmental"}]}) => false
    (show-reporting-link? {:oauth-roles [{:organization "no-bi-app-org" :client-type "bi-app" :role "reporter"}]}) => false
    (show-reporting-link? {:oauth-roles [{:organization "no-bi-app-org" :client-type "bi-app" :role "director"}]}) => false))
