(ns lupapalvelu.xml.krysp.krysp-http-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [sade.strings :as ss]))

(apply-remote-minimal)

(facts "Sending KuntaGML via HTTP"                          ; Tampere is configured to use HTTP krysp
  (let [application-id (create-app-id pena
                                      :x 329072
                                      :y 6823200
                                      :propertyId "83712103620001"
                                      :address "Pub Harald")
        application (query-application pena application-id)]
    (generate-documents application pena)
    (command pena :submit-application :id application-id) => ok?

    (let [resp (command veikko :approve-application :id application-id :lang "fi")]
      (fact "Veikko moves to backing system via HTTP"
        resp => ok?
        (:integrationAvailable resp) => true))

    (let [msgs (integration-messages application-id :test-db-name test-db-name)
          krysp-message (first msgs)]
      (fact "message is saved to integration-messages"
        (count msgs) => 1
        (:messageType krysp-message) => "KuntaGML"
        (ss/starts-with (:data krysp-message) "<?xml") => truthy
        (ss/contains? (:data krysp-message) "Pub Harald") => true))

    (fact "Sending fails without proper http endpoint"
      (command admin :set-kuntagml-http-endpoint :url "http://invalid" :organization "837-R" :permitType "R") => ok?

      (command veikko :request-for-complement :id application-id) => ok?

      (command veikko :approve-application :id application-id :lang "fi") => fail?)))

