(ns lupapalvelu.construction-started-ready-itest
  (:require [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer [err]]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.date :as date]))

(testable-privates lupapalvelu.construction-api
                   dates-sanity-check)

(apply-remote-minimal)

(fact "Add continuation permit support to the organization"
        (command sipoo-ya :set-organization-selected-operations :organizationId "753-YA"
                 :operations [:ya-katulupa-vesi-ja-viemarityot
                              :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen
                              :ya-kayttolupa-mainostus-ja-viitoitus
                              :ya-kayttolupa-terassit
                              :ya-kayttolupa-vaihtolavat
                              :ya-kayttolupa-nostotyot
                              :ya-jatkoaika]) => ok?)

(fact* "Application can be set to Started state after verdict has been given, and after that to Closed state."
  (let [initial-application (create-and-submit-application pena
                              :operation "ya-katulupa-vesi-ja-viemarityot"
                              :propertyId sipoo-property-id
                              :address "Paatoskuja 11") => truthy
        application-id (:id initial-application)
        _              (generate-documents! initial-application sonja)
        _              (command sonja :approve-application :id application-id :lang "fi") => ok?
        documents-after-approve (:documents (query-application sonja application-id)) => seq
        _              (command pena :inform-construction-started :id application-id
                                :startedTimestampStr "31.12.2013" :lang "fi")
        => (partial expected-failure? "error.command-illegal-state")
        _              (give-legacy-verdict sonja application-id)
        application    (query-application sonja application-id) => truthy]

    (facts "Documents got an indicator reset timestamp"
      (fact "had not before"
        (:documents initial-application) => seq
        (:documents initial-application) => (has every? #(nil? (get-in % [:meta :_indicator_reset :timestamp]))))
      (fact "have now"
        documents-after-approve => (has every? #(pos? (get-in % [:meta :_indicator_reset :timestamp])))))

    (:state application) => "verdictGiven"
    sonja => (allowed? :create-continuation-period-permit :id application-id)
    (fact "Cannot be marked ready if not marked started"
      (command sonja :inform-construction-ready :id application-id :readyTimestampStr "31.12.2013"
               :lang "fi") => unauthorized?)
    (fact "Started date must be valid"
      (command pena :inform-construction-started :id application-id :startedTimestampStr "32.12.3013"
               :lang "fi") => (partial expected-failure? "error.invalid-date"))
    (fact "Started date cannot be in the future"
      (command pena :inform-construction-started :id application-id :startedTimestampStr "31.12.3013"
               :lang "fi") => (partial expected-failure? "error.started-in-the-future"))
    (fact "Set started"
      sonja => (allowed? :inform-construction-started :id application-id)
      (command pena :inform-construction-started :id application-id :startedTimestampStr "31.12.2013"
               :lang "fi") => ok?)

    ;; Started application
    (let [application (query-application sonja application-id) => truthy
          email       (last-email) => truthy]
      (fact "state is constructionStarted"
        (:state application) => "constructionStarted"
        (-> application :history last :state) => "constructionStarted")
      (:to email) => (contains (email-for-key pena))
      (:subject email) => "Lupapiste: Paatoskuja 11, Sipoo - hankkeen tila on nyt Rakennusty\u00f6t aloitettu"
      (get-in email [:body :plain]) => (contains "Rakennusty\u00f6t aloitettu")
      email => (partial contains-application-link? application-id "applicant")

      (fact "Verdicts can be fetched even in construction started state, state doesn't change"
        (command sonja :check-for-verdict :id application-id) => ok?
        (:state (query-application sonja application-id)) => "constructionStarted"
        (-> application :history last :state) => "constructionStarted")

      (fact "Clear emails"
        (last-email) => truthy)

      (fact "Pena cannot edit started date"
        (command pena :inform-construction-started :id application-id :startedTimestampStr "1.1.2014"
                 :lang "fi") => unauthorized?)
      (fact "... but Sonja can"
        (command sonja :inform-construction-started :id application-id :startedTimestampStr "1.1.2014"
                 :lang "fi") => ok?)

      (fact "Editing does not send email, since the application state is already constructionStarted"
        (last-email) => nil)

      (fact "Pena cannot mark ready"
        (command pena :inform-construction-ready :id application-id :readyTimestampStr "1.2.2014" :lang "fi") => unauthorized?)
      (fact "Sonja can, but the ready date cannot be before started"
        (command sonja :inform-construction-ready :id application-id :readyTimestampStr "31.12.2013" :lang "fi") => (partial expected-failure? "error.started-after-ready"))
      (fact "... nor in the future"
        (command sonja :inform-construction-ready :id application-id :readyTimestampStr "31.12.3013" :lang "fi") => (partial expected-failure? "error.ready-in-the-future"))
      (fact "... nor invalid"
        (command sonja :inform-construction-ready :id application-id :readyTimestampStr "foobar" :lang "fi") => (partial expected-failure? "error.invalid-date"))
      (fact "Sonja marks ready"
        (command sonja :inform-construction-ready :id application-id :readyTimestampStr "1.2.2014" :lang "fi") => ok?)
      (fact "Ready cannot be edited"
        (command sonja :inform-construction-ready :id application-id :readyTimestampStr "1.3.2014" :lang "fi") => fail?)

      ;; Closed application
      (let [application (query-application sonja application-id) => truthy
            email       (last-email) => truthy]
        (fact "state is closed"
          (:state application) => "closed"
          (-> application :history last :state) => "closed")

        sonja =not=> (allowed? :inform-construction-started :id application-id)
        sonja =not=> (allowed? :create-continuation-period-permit :id application-id)

        (:to email) => (contains (email-for-key pena))
        (:subject email) => "Lupapiste: Paatoskuja 11, Sipoo - hankkeen tila on nyt Valmistunut"
        (get-in email [:body :plain]) => (contains "Valmistunut")
        email => (partial contains-application-link? application-id "applicant")))))

(fact* "Application cannot be set to Started state if it is not an YA type of application."
  (let [application    (create-and-submit-application sonja :propertyId sipoo-property-id :address "Paatoskuja 11") => truthy
        application-id (:id application)
        _              (command sonja :update-app-bulletin-op-description :id application-id :description "otsikko julkipanoon") => ok?
        _              (command sonja :approve-application :id application-id :lang "fi") => ok?
        _              (give-legacy-verdict sonja application-id)
        application    (query-application sonja application-id) => truthy
        _              (:state application) => "verdictGiven"]
    (command sonja :inform-construction-started :id application-id :startedTimestampStr "31.12.2013"
             :lang "fi") => (partial expected-failure? "error.invalid-permit-type")))

(let [n               (date/now)
      created         (date/timestamp n)
      yesterday-ts    (date/timestamp (date/minus n :day))
      yesterday       (date/finnish-date yesterday-ts)
      today           (date/finnish-date n)
      tomorrow-ts     (date/timestamp (date/plus n :day))
      tomorrow        (date/finnish-date tomorrow-ts)
      started-future? (err :error.started-in-the-future)
      started-after?  (err :error.started-after-ready)
      ready-future?   (err :error.ready-in-the-future)]
  (facts dates-sanity-check
    (fact "Not enough timestamps: success"
      (dates-sanity-check {:created created}) => nil
      (dates-sanity-check {:application {:closed created}
                           :created     created}) => nil
      (dates-sanity-check {:data    {:readyTimestampStr tomorrow}
                           :created created}) => nil)
    (fact "Started in the future"
      (dates-sanity-check {:application {:started tomorrow-ts}
                           :created     created})
      => started-future?
      (dates-sanity-check {:application {:started yesterday-ts}
                           :data        {:startedTimestampStr tomorrow}
                           :created     created})
      => started-future?)
    (fact "Ready in the future"
      (dates-sanity-check {:application {:closed tomorrow-ts}
                           :created     created})
      => ready-future?
      (dates-sanity-check {:application {:closed yesterday-ts}
                           :data        {:readyTimestampStr tomorrow}
                           :created     created})
      => ready-future?)
    (fact "Started after ready"
      (dates-sanity-check {:application {:started created
                                         :closed  yesterday-ts}
                           :created     created})
      => started-after?
      (dates-sanity-check {:application {:started created}
                           :data        {:readyTimestampStr yesterday}
                           :created     created})
      => started-after?
      (dates-sanity-check {:application {:closed yesterday-ts}
                           :data        {:startedTimestampStr created}
                           :created     created})
      => started-after?
      (dates-sanity-check {:application {:started yesterday-ts
                                         :closed  created}
                           :data        {:startedTimestampStr today
                                         :readyTimestampStr   yesterday}
                           :created     created})
      => started-after?)
    (fact "Good dates"
      (dates-sanity-check {:application {:started yesterday-ts
                                         :closed  created}
                           :created     created})
      => nil
      (dates-sanity-check {:application {:started tomorrow-ts
                                         :closed  yesterday-ts}
                           :data        {:startedTimestampStr yesterday
                                         :readyTimestampStr   today}
                           :created     created})
      => nil
      (dates-sanity-check {:application {:started created}
                           :data        {:readyTimestampStr today}
                           :created     created})
      => nil
      (dates-sanity-check {:data    {:startedTimestampStr today
                                     :readyTimestampStr   today}
                           :created created})
      => nil
      (dates-sanity-check {:data    {:startedTimestampStr yesterday
                                     :readyTimestampStr   yesterday}
                           :created yesterday-ts})
      => nil)))
