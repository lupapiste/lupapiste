(ns lupapalvelu.autom_check_verdicts_itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [sade.dummy-email-server :as dummy-email-server]
            [lupapalvelu.batchrun :as batchrun]))

#_(apply-remote-minimal)

#_(facts* "Automatic checking for verdicts"

   (last-email) ; Inbox zero

   (let [application-submitted         (create-and-submit-application sonja :municipality sonja-muni :address "Paatoskuja 17") => truthy
         application-id-submitted      (:id application-submitted)
         application-sent              (create-and-submit-application sonja :municipality sonja-muni :address "Paatoskuja 18") => truthy
         application-id-sent           (:id application-sent)
         application-verdict-given     (create-and-submit-application sonja :municipality sonja-muni :address "Paatoskuja 19") => truthy
         application-id-verdict-given  (:id application-verdict-given)]

     (command sonja :approve-application :id application-id-sent :lang "fi") => ok?
     (command sonja :approve-application :id application-id-verdict-given :lang "fi") => ok?
     (give-verdict sonja application-id-verdict-given :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?

     (let [application-submitted (query-application sonja application-id-submitted) => truthy
           application-sent (query-application sonja application-id-sent) => truthy
           application-verdict-given (query-application sonja application-id-verdict-given) => truthy]
       (:state application-submitted) => "submitted"
       (:state application-sent) => "sent"
       (:state application-verdict-given) => "verdictGiven")


     (fact "checking verdicts and sending emails to the authorities related to the applications"
       (batchrun/check-for-verdicts))

     (fact "Verifying the sent emails"
       ;; dummy-email-server/messages sometimes returned nil for the email
       ;; (because the email sending is asynchronous). Thus applying sleep here.
       (Thread/sleep 100)
       (let [emails (dummy-email-server/messages :reset true)]
         (fact "email count" (count emails) => 1)
         (let [email (last emails)]
           (fact "email check"
             (:to email) => (email-for-key sonja)
             (:subject email) => "Lupapiste.fi: Paatoskuja 18 - p\u00e4\u00e4t\u00f6s"
             email => (partial contains-application-link-with-tab? application-id-sent "verdict")
             (get-in email [:body :plain]) => (contains "P\u00e4\u00e4t\u00f6s annettu hakemukseen")))))

     (let [application-submitted (query-application sonja application-id-submitted) => truthy
           application-sent (query-application sonja application-id-sent) => truthy
           application-verdict-given (query-application sonja application-id-verdict-given) => truthy]
       (:state application-submitted) => "submitted"
       (:state application-sent) => "verdictGiven"
       (:state application-verdict-given) => "verdictGiven")
     ))
