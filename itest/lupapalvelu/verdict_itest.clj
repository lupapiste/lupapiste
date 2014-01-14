(ns lupapalvelu.verdict-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet  :refer :all]
            [lupapalvelu.domain :as domain]))

(fact* "Authority is able to add an attachment to an application after verdict has been given for it"
  (doseq [user [sonja pena]]
    (last-email) ; Inbox zero

    (let [application-id  (create-app-id user :municipality sonja-muni :address "Paatoskuja 9")
          resp            (command user :submit-application :id application-id) => ok?
          application     (query-application user application-id)
          email           (last-email) => truthy]
      (:state application) => "submitted"
      (:to email) => (email-for-key user)
      (:subject email) => "Lupapiste.fi: Paatoskuja 9 - hakemuksen tila muuttunut"
      (get-in email [:body :plain]) => (contains "Vireill\u00e4")
      email => (partial contains-application-link? application-id)

      (let [resp        (command sonja :give-verdict :id application-id :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
            application (query-application sonja application-id)
            verdict     (first (:verdicts application))
            paatos      (first (:paatokset verdict))
            poytakirja  (first (:poytakirjat paatos))
            email       (last-email)]
        (:state application) => "verdictGiven"
        (count (:verdicts application)) => 1
        (count (:paatokset verdict)) => 1
        (count (:poytakirjat paatos)) => 1

        (:kuntalupatunnus verdict) => "aaa"
        (:status poytakirja) => 42
        (:paatoksentekija poytakirja) => "Paatoksen antaja"
        (get-in paatos [:paivamaarat :anto]) => 123
        (get-in paatos [:paivamaarat :lainvoimainen]) => 124

        (let [first-attachment (get-in application [:attachments 0])]
          (upload-attachment sonja (:id application) first-attachment true)
          (upload-attachment pena (:id application) first-attachment false))

        (:to email) => (email-for-key user)
        (:subject email) => "Lupapiste.fi: Paatoskuja 9 - p\u00e4\u00e4t\u00f6s"
        email => (partial contains-application-link-with-tab? application-id "verdict")))))

(fact "Applicant receives email after verdict has been fetched from KRYSP backend"
  (last-email) ; Inbox zero

  (let [application (create-and-submit-application mikko :municipality sonja-muni :address "Paatoskuja 17")
        application-id (:id application)]
    (:organization application) => "753-R"
    (command sonja :check-for-verdict :id application-id) => ok?
    (let [email (last-email)]
      (:to email) => (email-for-key mikko)
      (:subject email) => "Lupapiste.fi: Paatoskuja 17 - p\u00e4\u00e4t\u00f6s"
      email => (partial contains-application-link-with-tab? application-id "verdict"))))

(fact "Rakennus & rakennelma"
  (let [application (create-and-submit-application mikko :municipality sonja-muni :address "Paatoskuja 17")
        application-id (:id application)
        _ (command sonja :check-for-verdict :id application-id) => ok?
        application (query-application mikko application-id)
        buildings   (:buildings application)
        {:as building1}   (first buildings)
        {:as building2}   (second buildings)
        {:as building3}   (last buildings)]

    (count buildings) => 3

    (:buildingId building1) => "101"
    (:propertyId building1) => "18601234567890"
    (:index building1) => "1"
    (:usage building1) => "893 turkistarhat"
    (:area building1) => "501"
    (:created building1) => "2013"

    (:buildingId building2) => "102"
    (:propertyId building2) => "18601234567891"
    (:index building2) => "2"
    (:usage building2) => "891 viljankuivaamot ja viljan s\u00e4ilytysrakennukset"
    (:area building2) => "602"
    (:created building2) => "2013"

    (:buildingId building3) => "103"
    (:propertyId building3) => "18601234567892"
    (:index building3) => "3"
    (:usage building3) => nil
    (:area building3) => "22"
    (:created building3) => "2013"))

;; Construction start / ready

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


;; Change permit

(fact* "A change permit can be created based on current R application after verdict has been given."
  (let [apikey                 sonja
        application-id         (create-app-id apikey
                                 :municipality sonja-muni
                                 :address "Paatoskuja 12")
        application            (query-application apikey application-id) => truthy]
    (generate-documents application apikey)
    (command apikey :submit-application :id application-id) => ok?
    (command apikey :approve-application :id application-id :lang "fi") => ok?
    (command apikey :create-change-permit :id application-id) => (partial expected-failure? "error.command-illegal-state")
    (command apikey :give-verdict :id application-id :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?

    (let [application (query-application apikey application-id)]
      (:state application) => "verdictGiven")
;     (command apikey :create-change-permit :id application-id) => ok?
;     (query-application apikey application-id) => truthy
    apikey => (allowed? :create-change-permit :id application-id)))

(fact* "Change permit can only be applied for an R type of application."
  (let [apikey                 sonja
        municipality           sonja-muni
        application            (create-and-submit-application apikey
                                 :municipality municipality
                                 :address "Paatoskuja 13"
                                 :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        application-id         (:id application)]
    (generate-documents application apikey)
    (command apikey :approve-application :id application-id :lang "fi") => ok?
    (command apikey :give-verdict :id application-id :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
    (let [application (query-application apikey application-id) => truthy]
      (:state application) => "verdictGiven")
    (command apikey :create-change-permit :id application-id) => (partial expected-failure? "error.invalid-permit-type")))


;; Link permit

(fact* "Link permit creation and removal"
  (apply-remote-minimal)

  (let [apikey                 sonja
        municipality           sonja-muni

        ;; App 1 - with same permit type, approved
        application-1            (create-and-submit-application apikey
                                   :municipality municipality
                                   :address "Paatoskuja 13"
                                   :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        application-id-1         (:id application-1)
        _                        (generate-documents application-1 apikey)
        _                        (command apikey :approve-application :id application-id-1 :lang "fi") => ok?

        ;; App 2 - with same permit type, verdict given
        application-2            (create-and-submit-application apikey
                                   :municipality municipality
                                   :address "Paatoskuja 13"
                                   :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        application-id-2         (:id application-2)
        _                        (generate-documents application-2 apikey)
        _                        (command apikey :approve-application :id application-id-2 :lang "fi") => ok?
        _                        (command apikey :give-verdict :id application-id-2 :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?

        ;; App 3 - with same permit type, verdict given, of operation "ya-jatkoaika"
        application-id-resp      (command apikey :create-continuation-period-permit :id application-id-2) => ok?
        application-id-3         (:id application-id-resp)
        application-3            (query-application apikey application-id-3) => truthy
        _                        (command apikey :submit-application :id application-id-3) => ok?
        _                        (generate-documents application-3 apikey)
        _                        (command apikey :approve-application :id application-id-3 :lang "fi") => ok?

        ;; App that gets a link permit attached to it
        application-4             (create-and-submit-application apikey
                                   :municipality municipality
                                   :address "Paatoskuja 13"
                                   :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        application-id-4         (:id application-4)]


    ;; The dropdown selection component in the link-permit dialog is allowed to have only these kind of applications:
    ;;    - different id than current application has, but same permit-type
    ;;    - is in "verdictGiven"- or "constructionStarted" state
    ;;    - whose operation is not of type "ya-jatkoaika"
    ;;
    (let [matches-resp (query apikey :app-matches-for-link-permits :id application-id-4) => ok?
          matches (:app-links matches-resp)]
      (count matches) => 1
      (-> matches first :id)   => application-id-2)

    (command apikey :add-link-permit :id application-id-4 :linkPermitId application-id-2) => ok?
    (generate-documents application-4 apikey)
    (command apikey :approve-application :id application-id-4 :lang "fi") => ok?
    (command apikey :add-link-permit :id application-id-4 :linkPermitId application-id-2) => (partial expected-failure? "error.command-illegal-state")

    (let [app (query-application apikey application-id-4) => truthy]
      (-> app first :appsLinkingToUs) => nil?
      (count (:linkPermitData app)) => 1
      (let [link-permit-data (-> app :linkPermitData first)]
        (:id link-permit-data) => "LP-753-2014-00002"
        (:type link-permit-data) => "lupapistetunnus"
        (:operation link-permit-data) => "ya-katulupa-vesi-ja-viemarityot"
        ))))

