(ns lupapalvelu.autom_check_verdicts_itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [sade.dummy-email-server :as dummy-email-server]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.application :as application]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.batchrun :as batchrun]))

(mongo/connect!)
(mongo/remove-many :organizations {})

(let [krysp-url (str (server-address) "/dev/krysp")
      organizations (map (fn [org] (update-in org [:krysp] #(assoc-in % [:R :url] krysp-url))) minimal/organizations)]
  (dorun (map (partial mongo/insert :organizations) organizations)))

(facts* "Automatic checking for verdicts"


 (let [application-submitted         (create-and-submit-local-application sonja :municipality sonja-muni :address "Paatoskuja 17") => truthy
       application-id-submitted      (:id application-submitted)
       application-sent              (create-and-submit-local-application sonja :municipality sonja-muni :address "Paatoskuja 18") => truthy
       application-id-sent           (:id application-sent)
       application-verdict-given     (create-and-submit-local-application sonja :municipality sonja-muni :address "Paatoskuja 19") => truthy
       application-id-verdict-given  (:id application-verdict-given)]

   (local-command sonja :approve-application :id application-id-sent :lang "fi") => ok?
   (local-command sonja :approve-application :id application-id-verdict-given :lang "fi") => ok?
   (give-local-verdict sonja application-id-verdict-given :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?

   (let [application-submitted (query-application local-query sonja application-id-submitted) => truthy
         application-sent (query-application local-query sonja application-id-sent) => truthy
         application-verdict-given (query-application local-query sonja application-id-verdict-given) => truthy]
     (:state application-submitted) => "submitted"
     (:state application-sent) => "sent"
     (:state application-verdict-given) => "verdictGiven")

   (dummy-email-server/messages :reset true) ; Inbox zero

   (fact "checking verdicts and sending emails to the authorities related to the applications"
     (batchrun/fetch-verdics))

   (fact "Verifying the sent emails"
     ;; dummy-email-server/messages sometimes returned nil for the email
     ;; (because the email sending is asynchronous). Thus applying sleep here.
     (Thread/sleep 100)

     (let [emails (dummy-email-server/messages :reset true)]
       (fact "email count" (count emails) => 1)
       (let [email (last emails)]
         (fact "email check"
           (:to email) => (contains (email-for-key sonja))
           (:subject email) => "Lupapiste.fi: Paatoskuja 18 - p\u00e4\u00e4t\u00f6s"
           email => (partial contains-application-link-with-tab? application-id-sent "verdict" "authority")
           (get-in email [:body :plain]) => (contains "P\u00e4\u00e4t\u00f6s annettu hakemukseen")))))

   (let [application-submitted (query-application local-query sonja application-id-submitted) => truthy
         application-sent (query-application local-query sonja application-id-sent) => truthy
         application-verdict-given (query-application local-query sonja application-id-verdict-given) => truthy]
     (:state application-submitted) => "submitted"
     (:state application-sent) => "verdictGiven"
     (:state application-verdict-given) => "verdictGiven")))
