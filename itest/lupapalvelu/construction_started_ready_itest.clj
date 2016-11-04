(ns lupapalvelu.construction-started-ready-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(apply-remote-minimal)

(fact* "Application can be set to Started state after verdict has been given, and after that to Closed state."
  (let [initial-application (create-and-submit-application sonja
                              :operation "ya-katulupa-vesi-ja-viemarityot"
                              :propertyId sipoo-property-id
                              :address "Paatoskuja 11") => truthy
        application-id (:id initial-application)
        _              (generate-documents initial-application sonja)
        _              (command sonja :approve-application :id application-id :lang "fi") => ok?
        documents-after-approve (:documents (query-application sonja application-id)) => seq
        _              (command sonja :inform-construction-started :id application-id
                                :startedTimestampStr "31.12.2013" :lang "fi")
        => (partial expected-failure? "error.command-illegal-state")
        _              (give-verdict sonja application-id) => ok?
        application    (query-application sonja application-id) => truthy]

    (facts "Documents got an indicator reset timestamp"
      (fact "had not before"
        (:documents initial-application) => seq
        (:documents initial-application) => (has every? #(nil? (get-in % [:meta :_indicator_reset :timestamp]))))
      (fact "have now"
        documents-after-approve => (has every? #(pos? (get-in % [:meta :_indicator_reset :timestamp])))))

    (:state application) => "verdictGiven"
    sonja => (allowed? :create-continuation-period-permit :id application-id)
    (command sonja :inform-construction-ready :id application-id :readyTimestampStr "31.12.2013"
             :lang "fi") => (partial expected-failure? "error.command-illegal-state")
    (command sonja :inform-construction-started :id application-id :startedTimestampStr "31.12.2013"
             :lang "fi") => ok?

    ;; Started application
    (let [application (query-application sonja application-id) => truthy
          email       (last-email) => truthy]
      (fact "state is constructionStarted"
        (:state application) => "constructionStarted"
        (-> application :history last :state) => "constructionStarted")
      (:to email) => (contains (email-for-key sonja))
      (:subject email) => "Lupapiste: Paatoskuja 11 - hakemuksen tila muuttunut"
      (get-in email [:body :plain]) => (contains "Rakennusty\u00f6t aloitettu")
      email => (partial contains-application-link? application-id "authority")

      (fact "Verdicts can be fetched even in construction started state, state doesn't change"
        (command sonja :check-for-verdict :id application-id) => ok?
        (:state (query-application sonja application-id)) => "constructionStarted"
        (-> application :history last :state) => "constructionStarted")

      (command sonja :inform-construction-ready :id application-id :readyTimestampStr "31.12.2013" :lang "fi") => ok?

      ;; Closed application
      (let [application (query-application sonja application-id) => truthy
            email       (last-email) => truthy]
        (fact "state is closed"
          (:state application) => "closed"
          (-> application :history last :state) => "closed")

        sonja =not=> (allowed? :inform-construction-started :id application-id)
        sonja =not=> (allowed? :create-continuation-period-permit :id application-id)

        (:to email) => (contains (email-for-key sonja))
        (:subject email) => "Lupapiste: Paatoskuja 11 - hakemuksen tila muuttunut"
        (get-in email [:body :plain]) => (contains "Valmistunut")
        email => (partial contains-application-link? application-id "authority")))))

(fact* "Application cannot be set to Started state if it is not an YA type of application."
  (let [application    (create-and-submit-application sonja :propertyId sipoo-property-id :address "Paatoskuja 11") => truthy
        application-id (:id application)
        _              (command sonja :approve-application :id application-id :lang "fi") => ok?
        _              (give-verdict sonja application-id) => ok?
        application    (query-application sonja application-id) => truthy
        _              (:state application) => "verdictGiven"]
    (command sonja :inform-construction-started :id application-id :startedTimestampStr "31.12.2013"
             :lang "fi") => (partial expected-failure? "error.invalid-permit-type")))
