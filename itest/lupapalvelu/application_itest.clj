(ns lupapalvelu.application-itest
  (:require [midje.sweet :refer :all]
            [clojure.string :refer [join]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet  :refer :all]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]))

(apply-remote-minimal)

#_(fact "can't inject js in 'x' or 'y' params"
   (create-app pena :x ";alert(\"foo\");" :y "what ever") =not=> ok?
   (create-app pena :x "0.1x" :y "1.0")                   =not=> ok?
   (create-app pena :x "1x2" :y "1.0")                    =not=> ok?
   (create-app pena :x "2" :y "1.0")                      =not=> ok?
   (create-app pena :x "410000.1" :y "6610000.1")         => ok?)

(fact "creating application without message"
  (let [id    (create-app-id pena)
        app   (query-application pena id)]
    app => (contains {:id id
                      :state "draft"
                      :location {:x 444444.0 :y 6666666.0}
                      :organization "753-R"})
    (count (:comments app)) => 0
    (first (:auth app)) => (contains
                             {:firstName "Pena"
                              :lastName "Panaani"
                              :type "owner"
                              :role "owner"})
    (fact "has applicant" (:applicant app) => "Pena Panaani")
    (fact "has allowedAttachmentTypes" (:allowedAttachmentTypes app) => seq)))

(fact "creating application with message"
  (let [application-id  (create-app-id pena :messages ["hello"])
        application     (query-application pena application-id)
        hakija          (domain/get-document-by-name application "hakija")]
    (:state application) => "draft"
    (:opened application) => nil
    (count (:comments application)) => 1
    (-> (:comments application) first :text) => "hello"))

(fact "application created to Sipoo belongs to organization Sipoon Rakennusvalvonta"
  (let [application-id  (create-app-id pena :municipality sonja-muni)
        application     (query-application pena application-id)
        hakija (domain/get-document-by-name application "hakija")]
    (:organization application) => "753-R"))

(fact "the ready-calculated validation errors about required document fields, included by a newly created application, are updated when those application fields are filled"
  (let [application-id  (create-app-id pena :municipality sonja-muni)
        application     (query-application pena application-id)
        hakija          (domain/get-document-by-name application "hakija")
        errs            (:validationErrors hakija)]
    (count errs) => pos?
    (some #(= "illegal-value:required" (-> % :result second)) errs)

    (generate-documents application pena)

    (let [application     (query-application pena application-id)
          hakija          (domain/get-document-by-name application "hakija")]
      (not-any? #(= "illegal-value:required" (-> % :result second)) errs))))

(fact "application created to Tampere belongs to organization Tampereen Rakennusvalvonta"
  (let [application-id  (create-app-id pena :municipality "837")
        application     (query-application pena application-id)
        hakija (domain/get-document-by-name application "hakija")]
    (:organization application) => "837-R"))

(fact "application created to Reisjarvi belongs to organization Peruspalvelukuntayhtyma Selanne"
  (let [application-id  (create-app-id pena :municipality "626")
        application     (query-application pena application-id)
        hakija (domain/get-document-by-name application "hakija")]
    (:organization application) => "069-R"))

(fact* "Assign application to an authority"
  (let [application-id (create-app-id pena :municipality sonja-muni)
        ;; add a comment to change state to open
        _ (comment-application pena application-id true) => ok?
        application (query-application sonja application-id)
        authority-before-assignation (:authority application)
        resp (command sonja :assign-application :id application-id :assigneeId ronja-id)
        assigned-app (query-application sonja application-id)
        authority-after-assignation (:authority assigned-app)]
    application-id => truthy
    application => truthy
    (success resp) => true
    (empty? authority-before-assignation) => true
    authority-after-assignation => (contains {:id ronja-id})
    (fact "Authority is not able to submit"
      sonja =not=> (allowed? sonja :submit-application :id application-id))))

(fact* "Assign application to an authority and then to no-one"
  (let [application-id (create-app-id pena :municipality sonja-muni)
        ;; add a comment change set state to open
        _ (comment-application pena application-id true) => ok?
        application (query-application sonja application-id)
        authority-before-assignation (:authority application)
        resp (command sonja :assign-application :id application-id :assigneeId sonja-id)
        resp (command sonja :assign-application :id application-id :assigneeId nil)
        assigned-app (query-application sonja application-id)
        authority-in-the-end (:authority assigned-app)]
    (empty? authority-before-assignation) => true
    (empty? authority-in-the-end) => true))

(fact "Authority is able to create an application to a municipality in own organization"
  (let [application-id  (create-app-id sonja :municipality sonja-muni)]
    (fact "Application is open"
       (let [application (query-application sonja application-id)]
         application => truthy
         (:state application) => "open"
         (:opened application) => truthy
         (:opened application) => (:created application)))
    (fact "Authority could submit her own application"
      sonja => (allowed? :submit-application :id application-id))
    (fact "Application is submitted"
      (let [resp        (command sonja :submit-application :id application-id)
            application (query-application sonja application-id)]
        resp => ok?
        (:state application) => "submitted"))))

(facts* "Application has opened when submitted from draft"
  (let [resp (create-app pena) => ok?
        id   (:id resp)
        app1 (query-application pena id) => truthy
        resp (command pena :submit-application :id id) => ok?
        app2 (query-application pena id) => truthy]
    (:opened app1) => nil
    (:opened app2) => number?))

(facts* "cancel application"
  (last-email) ; Inbox zero

  (let [application (create-and-submit-application mikko :municipality sonja-muni :address "Peruutustie 23")
        application-id (:id application)]

    (fact "Mikko sees the application" (query mikko :application :id application-id) => ok?)
    (fact "Sonja sees the application" (query sonja :application :id application-id) => ok?)
    (fact "Sonja can cancel Mikko's application" (command sonja :cancel-application :id application-id) => ok?)
    (fact "Sonja sees the canceled application" (query sonja :application :id application-id) => ok?)
    (let [email (last-email)]
      (:to email) => (email-for-key mikko)
      (:subject email) => "Lupapiste.fi: Peruutustie 23 - hakemuksen tila muuttunut"
      (get-in email [:body :plain]) => (contains "Peruutettu")
      email => (partial contains-application-link? application-id)))

  (fact "Authority can cancel own application"
    (let [application-id  (create-app-id sonja :municipality sonja-muni)]
      (fact "Sonja sees the application" (query sonja :application :id application-id) => ok?)
      (fact "Sonja can cancel the application" (command sonja :cancel-application :id application-id) => ok?))))

(fact "Authority in unable to create an application to a municipality in another organization"
  (create-app sonja :municipality veikko-muni) => unauthorized?)

(facts "Add operations"
  (let [application-id  (create-app-id mikko :municipality veikko-muni)]
    (comment-application mikko application-id true) => ok?
    (command veikko :assign-application :id application-id :assigneeId veikko-id) => ok?

    (fact "Applicant is able to add operation"
      (success (command mikko :add-operation :id application-id :operation "varasto-tms")) => true)

    (fact "Authority is able to add operation"
      (success (command veikko :add-operation :id application-id :operation "muu-uusi-rakentaminen")) => true)))



(fact "Pena cannot create app for organization that has new applications disabled"
  (let [resp  (create-app pena :municipality "997")]
    resp =not=> ok?
    (:text resp) => "error.new-applications-disabled"))

(defn in?
  "true if seq contains elm"
  [seq elm]
  (some #(= elm %) seq))

(defn- set-and-check-person [api-key application-id initial-document path]
  (fact "initially there is no person data"
       initial-document => truthy
       (get-in initial-document [:data :henkilotiedot]) => nil)

    (fact "new person is set"
      (command api-key :set-user-to-document :id application-id :documentId (:id initial-document) :userId mikko-id :path (if (seq path) (join "." path) "")) => ok?
      (let [updated-app (query-application mikko application-id)
            update-doc (domain/get-document-by-id updated-app (:id initial-document))
            schema-name  (get-in update-doc [:schema-info :name])
            person-path  (into [] (concat [:data] (map keyword path) [:henkilotiedot]))
            company-path (into [] (concat [:data] (map keyword path) [:yritys]))
            experience-path (into [] (concat [:data] (map keyword path) [:patevyys]))
            suunnittelija? (in? ["paasuunnittelija" "suunnittelija"] schema-name )]
        (get-in update-doc (into person-path [:etunimi :value])) => "Mikko"
        (get-in update-doc (into person-path [:sukunimi :value])) => "Intonen"
        (get-in update-doc (into company-path [:yritysnimi :value])) => (if suunnittelija? "Yritys Oy" nil)
        (get-in update-doc (into company-path [:liikeJaYhteisoTunnus :value])) => (if suunnittelija? "1234567-1" nil)
        (get-in update-doc (into experience-path [:koulutus :value])) => (if suunnittelija? "Tutkinto" nil)
        (get-in update-doc (into experience-path [:valmistumisvuosi :value])) => (if suunnittelija? "2000" nil)
        (get-in update-doc (into experience-path [:fise :value])) => (if suunnittelija? "f" nil))))

(facts "Set user to document"
  (let [application-id   (create-app-id mikko :municipality sonja-muni)
        application      (query-application mikko application-id)
        paasuunnittelija (domain/get-document-by-name application "paasuunnittelija")
        suunnittelija    (domain/get-document-by-name application "suunnittelija")
        hakija     (domain/get-document-by-name application "hakija")
        maksaja    (domain/get-document-by-name application "maksaja")]

    (set-and-check-person mikko application-id paasuunnittelija [])
    (set-and-check-person mikko application-id hakija ["henkilo"])
    (set-and-check-person mikko application-id maksaja ["henkilo"])

    (fact "there is no suunnittelija"
       suunnittelija => truthy
       (get-in suunnittelija [:data :henkilotiedot]) => nil)

    (let [doc-id (:id suunnittelija)
          code "RAK-rakennesuunnittelija"]

      (fact "suunnittelija kuntaroolikoodi is set"
        (command mikko :update-doc :id application-id :doc doc-id :updates [["kuntaRoolikoodi" code]]) => ok?
        (let [updated-app          (query-application mikko application-id)
              updated-suunnittelija (domain/get-document-by-id updated-app doc-id)]
          updated-suunnittelija => truthy
          (get-in updated-suunnittelija [:data :kuntaRoolikoodi :value]) => code))

      (fact "suunnittelija patevyys is set"
        (command mikko :update-doc :id application-id :doc doc-id :updates
                 [["patevyys.kokemus" "10"]
                  ["patevyys.patevyysluokka" "AA"]
                  ["patevyys.patevyys" "Patevyys"]]) => ok?
        (let [updated-app          (query-application mikko application-id)
              updated-suunnittelija (domain/get-document-by-id updated-app doc-id)]
          updated-suunnittelija => truthy
          (get-in updated-suunnittelija [:data :patevyys :patevyys :value]) => "Patevyys"
          (get-in updated-suunnittelija [:data :patevyys :patevyysluokka :value]) => "AA"
          (get-in updated-suunnittelija [:data :patevyys :kokemus :value]) => "10"))

      (fact "new suunnittelija is set"
        (command mikko :set-user-to-document :id application-id :documentId (:id suunnittelija) :userId mikko-id :path "") => ok?
        (let [updated-app           (query-application mikko application-id)
              updated-suunnittelija (domain/get-document-by-id updated-app doc-id)]
          (get-in updated-suunnittelija [:data :henkilotiedot :etunimi :value]) => "Mikko"
          (get-in updated-suunnittelija [:data :henkilotiedot :sukunimi :value]) => "Intonen"
          (get-in updated-suunnittelija [:data :yritys :yritysnimi :value]) => "Yritys Oy"
          (get-in updated-suunnittelija [:data :yritys :liikeJaYhteisoTunnus :value]) => "1234567-1"
          (get-in updated-suunnittelija [:data :patevyys :koulutus :value]) => "Tutkinto"
          (get-in updated-suunnittelija [:data :patevyys :valmistumisvuosi :value]) => "2000"
          (get-in updated-suunnittelija [:data :patevyys :fise :value]) => "f"
          (fact "suunnittelija kuntaroolikoodi is preserved (LUPA-774)"
            (get-in updated-suunnittelija [:data :kuntaRoolikoodi :value]) => code))))))

(fact* "Merging building information from KRYSP does not overwrite the rest of the document"
  (let [application-id  (create-app-id pena :municipality sonja-muni :operation "kayttotark-muutos")
        app             (query-application pena application-id)
        rakmuu-doc      (domain/get-document-by-name app "rakennuksen-muuttaminen")
        resp2           (command pena :update-doc :id application-id :doc (:id rakmuu-doc) :collection "documents" :updates [["muutostyolaji" "muut muutosty\u00f6t"]])
        updated-app     (query-application pena application-id)
        building-info   (command pena :get-building-info-from-wfs :id application-id) => ok?
        doc-before      (domain/get-document-by-name updated-app "rakennuksen-muuttaminen")
        building-id     (:buildingId (first (:data building-info)))
        resp3           (command pena :merge-details-from-krysp :id application-id :documentId (:id doc-before) :collection "documents" :buildingId building-id) => ok?
        merged-app      (query-application pena application-id)
        doc-after       (domain/get-document-by-name merged-app "rakennuksen-muuttaminen")]
        (get-in doc-before [:data :muutostyolaji :value]) => "muut muutosty\u00f6t"
        (get-in doc-after [:data :muutostyolaji :value]) => "muut muutosty\u00f6t"
        (count (get-in doc-after [:data :huoneistot])) => 21
        (get-in doc-after [:data :kaytto :kayttotarkoitus :source]) => "krysp"))

(fact* "Merging building information from KRYSP succeeds even if document schema does not have place for all the info"
  (let [application-id  (create-app-id pena :municipality sonja-muni :operation "purkaminen")
        app             (query-application pena application-id)
        doc             (domain/get-document-by-name app "purkaminen")
        building-info   (command pena :get-building-info-from-wfs :id application-id) => ok?
        building-id     (:buildingId (first (:data building-info)))
        resp            (command pena :merge-details-from-krysp :id application-id :documentId (:id doc) :collection "documents" :buildingId building-id) => ok?
        merged-app      (query-application pena application-id)
        doc-after       (domain/get-document-by-name merged-app "purkaminen")]
        (get-in doc-after [:data :mitat :kokonaisala :source]) => "krysp"
        (get-in doc-after [:data :kaytto :kayttotarkoitus :source]) => "krysp"))


(facts "Facts about update operation description"
  (let [application-id (create-app-id pena :operation "asuinrakennus" :municipality sonja-muni)
        application (query-application pena application-id)
        op (first (:operations application))
        test-desc "Testdesc"]
    (fact "operation desc is empty" (-> op :description empty?) => truthy)
    (command pena :update-op-description :id application-id :op-id (:id op) :desc test-desc :collection "operations")
    (let [updated-app (query-application pena application-id)
          updated-op (some #(when (= (:id op) (:id %)) %) (:operations updated-app))]
      (fact "description is set" (:description updated-op) => test-desc))))

