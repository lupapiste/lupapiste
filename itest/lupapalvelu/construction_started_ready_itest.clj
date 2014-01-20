(ns lupapalvelu.construction-started-ready-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(fact* "Application can be set to Started state after verdict has been given, and after that to Closed state."
  (let [application-id         (create-app-id sonja
                                 :operation "ya-katulupa-vesi-ja-viemarityot"
                                 :municipality sonja-muni
                                 :address "Paatoskuja 11")
        application            (query-application sonja application-id) => truthy
        _                      (generate-documents application sonja)
        application            (query-application sonja application-id) => truthy
        submit-resp            (command sonja :submit-application :id application-id) => ok?
        approve-resp           (command sonja :approve-application :id application-id :lang "fi") => ok?
        started-resp-fail      (command sonja :inform-construction-started :id application-id :startedTimestampStr "31.12.2013") => (partial expected-failure? "error.command-illegal-state")
        verdict-resp           (command sonja :give-verdict :id application-id :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
        application            (query-application sonja application-id) => truthy
        app-state              (:state application) => "verdictGiven"
        closed-resp-fail       (command sonja :inform-construction-ready :id application-id :readyTimestampStr "31.12.2013" :lang "fi") => (partial expected-failure? "error.command-illegal-state")

        started-resp           (command sonja :inform-construction-started :id application-id :startedTimestampStr "31.12.2013") => ok?
        application            (query-application sonja application-id) => truthy
        email                  (last-email) => truthy]

    sonja => (allowed? :create-continuation-period-permit :id application-id)

    (:state application) => "constructionStarted"
    (:to email) => (email-for-key sonja)
    (:subject email) => "Lupapiste.fi: Paatoskuja 11 - hakemuksen tila muuttunut"
    (get-in email [:body :plain]) => (contains "Rakennusty\u00f6t aloitettu")
    email => (partial contains-application-link? application-id)

    (let [closed-resp            (command sonja :inform-construction-ready :id application-id :readyTimestampStr "31.12.2013" :lang "fi") => ok?
          application            (query-application sonja application-id) => truthy
          email                  (last-email)]

      (:state application) => "closed"
      sonja =not=> (allowed? :inform-construction-started :id application-id)
      sonja =not=> (allowed? :create-continuation-period-permit :id application-id)

      (:to email) => (email-for-key sonja)
      (:subject email) => "Lupapiste.fi: Paatoskuja 11 - hakemuksen tila muuttunut"
      (get-in email [:body :plain]) => (contains "Valmistunut")
      email => (partial contains-application-link? application-id))))

(fact* "Application cannot be set to Started state if it is not an YA type of application."
  (let [application            (create-and-submit-application sonja :municipality sonja-muni :address "Paatoskuja 11") => truthy
        application-id         (:id application)
        approve-resp           (command sonja :approve-application :id application-id :lang "fi") => ok?
        verdict-resp           (command sonja :give-verdict :id application-id :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
        application            (query-application sonja application-id) => truthy
        app-state              (:state application) => "verdictGiven"]
    (command sonja :inform-construction-started :id application-id :startedTimestampStr "31.12.2013") => (partial expected-failure? "error.invalid-permit-type")))
