(ns lupapalvelu.xml.krysp.krysp-http-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [sade.core :refer [now]]
            [sade.strings :as ss]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :as fixture]
            [sade.env :as env]))

(if (env/feature? :jms)
  (do                                                       ; testing with JMS
    (mongo/connect!)
    (fixture/apply-fixture "minimal")

    (facts "Sending KuntaGML via JMS and HTTP"                          ; Tampere is configured to use HTTP krysp in minimal
      (let [{application-id :id} (create-local-app pena
                                                   :x 329072
                                                   :y 6823200
                                                   :propertyId "83712103620001"
                                                   :address "Pub Harald")
            application (query-application local-query pena application-id)
            ts (now)]
        (generate-documents application pena true)
        (local-command pena :submit-application :id application-id) => ok?

        (let [resp (local-command veikko :approve-application :id application-id :lang "fi")]
          (fact "Veikko moves to backing system via HTTP"
            resp => ok?
            (:integrationAvailable resp) => true))

        (Thread/sleep 2000)
        (let [msgs (mongo/select :integration-messages {:application.id application-id})
              sent-message (first msgs)
              received-message (second msgs)]
          (facts "integration-messages"
            (count msgs) => 2
            (fact "sent message is saved"
              (:messageType sent-message) => "KuntaGML application"
              (:direction sent-message) => "out"
              (fact "after success, message is acknowledged"
                (> (or (:acknowledged sent-message) 0) ts) => true
                (:status sent-message) => "done"))
            (fact "received message is saved (dummy receiver)"
              ; 'hakemus-path' below is deduced in web.clj as :path, which is actually defined in organization in minimal fixture
              (:messageType received-message) => "KuntaGML hakemus-path"
              (:direction received-message) => "in"
              (ss/starts-with (:data received-message) "<?xml") => truthy
              (ss/contains? (:data received-message) "Pub Harald") => true)))

        (fact "Sending fails without proper http endpoint (message not acknowledged)"
          (local-command veikko :request-for-complement :id application-id) => ok?
          (local-command admin :set-kuntagml-http-endpoint :url "http://invalid" :organization "837-R" :permitType "R") => ok?
          (local-command veikko :approve-application :id application-id :lang "fi") => ok?
          (local-command veikko :request-for-complement :id application-id) => ok?
          (local-command admin :set-kuntagml-http-endpoint :partner "matti"
                         :url (str (server-address) "/dev/krysp/receiver") :organization "837-R" :permitType "R"
                         :username "kuntagml" :password "invalid") => ok?
          ; in HTTP version this failed, but as JMS is async, this returns OK
          (local-command veikko :approve-application :id application-id :lang "fi") => ok?
          (local-command veikko :request-for-complement :id application-id) => ok?)
        (fact "updating correct creds makes integration work again"
          (local-command admin :set-kuntagml-http-endpoint
                         :url (str (server-address) "/dev/krysp/receiver") :organization "837-R" :permitType "R"
                         :username "kuntagml" :password "kryspi" :partner "matti") => ok?
          (local-command veikko :approve-application :id application-id :lang "fi") => ok?))))
  ; else, testing without JMS with pure HTTP
  (do
    (apply-remote-minimal)
    (facts "Sending KuntaGML via HTTP"                    ; Tampere is configured to use HTTP krysp in minimal
        (let [application-id (create-app-id pena
                                            :x 329072
                                            :y 6823200
                                            :propertyId "83712103620001"
                                            :address "Pub Harald")
              application (query-application pena application-id)
              ts (now)]
          (generate-documents application pena)
          (command pena :submit-application :id application-id) => ok?

          (let [resp (command veikko :approve-application :id application-id :lang "fi")]
            (fact "Veikko moves to backing system via HTTP"
              resp => ok?
              (:integrationAvailable resp) => true))

          (let [msgs (integration-messages application-id :test-db-name test-db-name)
                sent-message (first msgs)
                received-message (second msgs)]
            (facts "integration-messages"
              (count msgs) => 2
              (fact "sent message is saved"
                (:messageType sent-message) => "KuntaGML application"
                (:direction sent-message) => "out"
                (fact "after success, message is acknowledged"
                  (> (or (:acknowledged sent-message) 0) ts) => true
                  (:status sent-message) => "done"))
              (fact "received message is saved (dummy receiver)"
                ; 'hakemus-path' below is deduced in web.clj as :path, which is actually defined in organization in minimal fixture
                (:messageType received-message) => "KuntaGML hakemus-path"
                (:direction received-message) => "in"
                (ss/starts-with (:data received-message) "<?xml") => truthy
                (ss/contains? (:data received-message) "Pub Harald") => true)))

          (fact "Sending fails without proper http endpoint"
            (command veikko :request-for-complement :id application-id) => ok?
            (command admin :set-kuntagml-http-endpoint :url "http://invalid" :organization "837-R" :permitType "R") => ok?
            (command veikko :approve-application :id application-id :lang "fi") => fail?
            (command admin :set-kuntagml-http-endpoint :partner "matti"
                     :url (str (server-address) "/dev/krysp/receiver") :organization "837-R" :permitType "R"
                     :username "kuntagml" :password "invalid") => ok?
            (command veikko :approve-application :id application-id :lang "fi") => fail?)
          (fact "updating correct creds makes integration work again"
            (command admin :set-kuntagml-http-endpoint
                     :url (str (server-address) "/dev/krysp/receiver") :organization "837-R" :permitType "R"
                     :username "kuntagml" :password "kryspi" :partner "matti") => ok?
            (command veikko :approve-application :id application-id :lang "fi") => ok?)))))
