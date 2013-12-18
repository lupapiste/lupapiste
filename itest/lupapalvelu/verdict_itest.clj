(ns lupapalvelu.verdict-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet  :refer :all]))

(fact* "Authority is able to add an attachment to an application after verdict has been given for it"
  (doseq [user [sonja pena]]
    (last-email) ; Inbox zero

    (let [application-id  (create-app-id user :municipality sonja-muni :address "Paatoskuja 9")
          resp            (command user :submit-application :id application-id) => ok?
          application     (query-application user application-id)
          email           (last-email)]
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

(fact "Applicant receives email after verdict has been fetched from KRYPS backend"
  (last-email) ; Inbox zero

  (let [application (create-and-submit-application mikko :municipality sonja-muni :address "Paatoskuja 17")
        application-id (:id application)]
    (:organization application) => "753-R"
    (command sonja :check-for-verdict :id application-id) => ok?
    (let [email (last-email)]
      (:to email) => (email-for-key mikko)
      (:subject email) => "Lupapiste.fi: Paatoskuja 17 - p\u00e4\u00e4t\u00f6s"
      email => (partial contains-application-link-with-tab? application-id "verdict"))))

(facts "Rakennus & rakennelma"
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
