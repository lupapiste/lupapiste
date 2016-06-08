(ns lupapalvelu.document.document-api-itest
  (:require [midje.sweet :refer :all]
            [sade.util :refer [fn->]]
            [lupapalvelu.application :refer [get-operations]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.attachment :as attachment]))

(apply-remote-minimal)

(facts* "facts about update-doc and validate-doc commands"
  (let [application-id   (create-app-id pena)
        application0     (query-application pena application-id) => truthy
        hakija-doc-id    (:id (domain/get-applicant-document (:documents application0)))
        resp             (command pena :update-doc :id application-id :doc hakija-doc-id  :collection "documents" :updates [["henkilo.henkilotiedot.etunimi" "foo"]["henkilo.henkilotiedot.sukunimi" "bar"]]) => ok?
        modified1        (:modified (query-application pena application-id))
        rakennus-doc-id  (:id (domain/get-document-by-name application0 "rakennuspaikka")) => truthy
        resp             (command pena :update-doc :id application-id :doc rakennus-doc-id  :collection "documents" :updates [["kiinteisto.maaraalaTunnus" "maaraalaTunnus"]]) => ok?
        application2     (query-application pena application-id)
        modified2        (:modified application2)
        hakija-doc       (domain/get-document-by-id application2 hakija-doc-id)
        rakennus-doc     (domain/get-document-by-id application2 rakennus-doc-id)
        failing-updates  [["rakennuksenOmistajat.henkilo.henkilotiedot.etunimi" "P0wnr"]]
        failing-result   (command pena :update-doc :id application-id :doc rakennus-doc-id :updates failing-updates)
        readonly-updates [["kiinteisto.tilanNimi" "tilannimi"]]
        readonly-result  (command pena :update-doc :id application-id :doc rakennus-doc-id :updates readonly-updates)]

    (fact "hakija is valid, but missing some fieds"
      (let [resp (query pena :validate-doc :id application-id :doc hakija-doc-id :collection "documents") => ok?
            results-by-doc (group-by #(get-in % [:document :id]) (:results resp))
            results (get results-by-doc hakija-doc-id)]
        (count results-by-doc) => 1
        (first (keys results-by-doc)) => hakija-doc-id
        (count results) => pos?
        (every? (fn [{:keys [element result]}] (and (:required element) (= result ["tip" "illegal-value:required"]))) results) => true))

    (fact "rakennus can not be validated as a task"
      (query pena :validate-doc :id application-id :doc rakennus-doc-id :collection "tasks") => fail?)

    modified1 => truthy
    modified2 => truthy
    modified2 => (partial < modified1)
    (fact "hakija-doc"
      (get-in hakija-doc   [:data :henkilo :henkilotiedot :etunimi :value]) => "foo"
      (get-in hakija-doc   [:data :henkilo :henkilotiedot :etunimi :modified]) => modified1
      (get-in hakija-doc   [:data :henkilo :henkilotiedot :sukunimi :value]) => "bar"
      (get-in hakija-doc   [:data :henkilo :henkilotiedot :sukunimi :modified]) => modified1)
    (fact "rakennus-doc"
      (get-in rakennus-doc [:data :kiinteisto :maaraalaTunnus :value]) => "maaraalaTunnus"
      (get-in rakennus-doc [:data :kiinteisto :maaraalaTunnus :modified]) => modified2)
    (fact (:ok failing-result) => false)
    (fact (:text failing-result) => "document-would-be-in-error-after-update")

    (fact "illegal key path is not echoed"
      (let [errors (filter (fn [{r :result}] (= "err" (first r))) (:results failing-result))]
        (count errors) => 1
        (:path (first errors)) => empty?))

    (fact (:ok readonly-result) => false)
    (fact (:text readonly-result) => "error-trying-to-update-readonly-field")))


(facts "create and query document"
  (let [application-id   (create-app-id pena)
        application0     (query-application pena application-id)
        ok-result        (command pena :create-doc :id application-id :schemaName "hakija-r")
        doc-id           (:doc ok-result)
        no-schema-result        (command pena :create-doc :id application-id :schemaName "foo")
        repeating-schema-result (command pena :create-doc :id application-id :schemaName "maksaja")
        non-repeating-result    (command pena :create-doc :id application-id :schemaName "paasuunnittelija")
        application1     (query-application pena application-id)]
    (fact ok-result => ok?)
    (fact (:text ok-result) => nil)
    (fact doc-id => truthy)
    (fact no-schema-result => fail?)
    (fact repeating-schema-result => ok?)
    (fact "paasuunnittelija can't be added" non-repeating-result => fail?)
    (fact "2 docs were added" (count (:documents application1)) => (inc (inc (count (:documents application0)))))

    (fact "The new document is returned with validation results"
      (let [{document :document :as resp} (query pena :document :id application-id :doc doc-id :collection "documents")]
        resp => ok?
        document => map?
        (:validationErrors document) => seq)))

  (facts "paasuunnittelija can be added exactly once"
    (let [application-id (create-app-id pena :operation :puun-kaataminen)]
      (command pena :create-doc :id application-id :schemaName "paasuunnittelija") => ok?
      (command pena :create-doc :id application-id :schemaName "paasuunnittelija") => fail?
      (command pena :create-doc :id application-id :schemaName "paasuunnittelija") => fail?)))

(facts "facts about remove-document-data command"
  (let [application-id             (create-app-id pena)
        application                (query-application pena application-id)
        uusi-rakennus-doc-id       (:id (domain/get-document-by-name application "uusiRakennus"))
        _                          (command pena :update-doc :id application-id :doc uusi-rakennus-doc-id  :collection "documents" :updates [["huoneistot.0.porras" "A"]
                                                                                                                                             ["huoneistot.1.porras" "B"]
                                                                                                                                             ["huoneistot.2.porras" "C"]])
        data-of                    (fn [app] (->> app :documents (filter (fn-> :id (= uusi-rakennus-doc-id))) first :data))
        app-with-three-apartments  (query-application pena application-id)
        three-apartments           (data-of app-with-three-apartments)
        resp                       (command pena :remove-document-data :id application-id :doc uusi-rakennus-doc-id  :collection "documents" :path ["huoneistot" "0"])
        app-with-two-apartments    (query-application pena application-id)
        two-apartments             (data-of app-with-two-apartments)]
    resp => ok?
    (fact (-> three-apartments :huoneistot keys) => (just #{:2 :1 :0}))
    (fact (-> two-apartments :huoneistot keys) => (just #{:2 :1 :validationResult}))))

(fact "'lyhyt rakennustunnus' cannot exceed maximum length"
  (let [application-id             (create-app-id pena)
        application                (query-application pena application-id)
        uusi-rakennus-doc-id       (:id (domain/get-document-by-name application "uusiRakennus"))]
    (fact (command pena :update-doc :id application-id :doc uusi-rakennus-doc-id
                   :collection "documents" :updates [["tunnus" "123456"]]) => ok?
          (command pena :update-doc :id application-id :doc uusi-rakennus-doc-id
                   :collection "documents" :updates [["tunnus" "1234567"]]) => fail?)))

(facts "facts about party-document-names query"
  (let [application-id        (create-app-id pena)
        application0          (query-application pena application-id)
        party-document-names  (:partyDocumentNames (query pena :party-document-names :id application-id))]
    party-document-names => ["hakija-r" "maksaja" "suunnittelija"]))

(facts* "approve and reject document"
  (let [application    (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
        application-id (:id application)
        hakija         (domain/get-applicant-document (:documents application))
        uusi-rakennus  (domain/get-document-by-name application "uusiRakennus")]

    (doseq [[cmd status] [[:approve-doc "approved"] [:reject-doc "rejected"]]]
      (command pena  cmd :id application-id :doc (:id hakija) :path nil :collection "documents") => unauthorized?
      (command sonja cmd :id application-id :doc (:id hakija) :path nil :collection "documents") => ok?
      (command sonja cmd :id application-id :doc (:id uusi-rakennus) :path nil :collection "documents") => ok?
      (let [approved-app           (query-application pena application-id)
            modified               (:modified approved-app)
            approved-uusi-rakennus (domain/get-document-by-name approved-app "uusiRakennus")]
        modified => truthy
        (get-in approved-uusi-rakennus [:meta :_approved :value]) => status
        (get-in approved-uusi-rakennus [:meta :_approved :timestamp]) => modified
        (get-in approved-uusi-rakennus [:meta :_approved :user :id]) => sonja-id))))

(facts* "remove document"
  (let [application-id (create-app-id pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
        _              (command pena :add-operation :id application-id :operation "vapaa-ajan-asuinrakennus") => truthy
        application    (query-application pena application-id)
        hakija         (domain/get-applicant-document (:documents application)) => truthy
        uusi-rakennus  (domain/get-document-by-name application "uusiRakennus") => truthy
        sauna          (domain/get-document-by-name application "uusi-rakennus-ei-huoneistoa") => truthy
        primary-op     (:primaryOperation application)
        sec-operations (:secondaryOperations application)]

    (fact "new application has a primary operation and a secondary operation"
      (:name primary-op) => "kerrostalo-rivitalo"
      (count sec-operations) => 1
      (:name (first sec-operations)) => "vapaa-ajan-asuinrakennus")

    (fact "application has attachments with primary operation" (count (attachment/get-attachments-by-operation application (:id primary-op))) => pos?)

    (fact "application has attachments with secondary operation" (count (attachment/get-attachments-by-operation application (:id (first sec-operations)))) => pos?)

    (fact "last hakija doc cannot be removed due :deny-removing-last-document flag"
      (get-in hakija [:schema-info :deny-removing-last-document]) => true
      (command pena :remove-doc :id application-id :docId (:id hakija)) => fail?)

    (fact "hakija doc is removed if there is more than one hakija-doc"
      (command pena :create-doc :id application-id :schemaName (get-in hakija [:schema-info :name])) => ok?
      (-> (query-application pena application-id) :documents domain/get-applicant-documents count) => 2

      (command pena :remove-doc :id application-id :docId (:id hakija)) => ok?
      (let [updated-app (query-application pena application-id)]
        (-> (:documents updated-app) domain/get-applicant-documents count) => 1
        (fact "every other doc nad operation remains untouched"
          (count (:documents updated-app)) => (count (:documents application))
          (count (:secondaryOperations updated-app)) => (count (:secondaryOperations application))
          (:primaryOperation updated-app) => (:primaryOperation application))))

    (fact "primary operation cannot be removed"
      (command pena :remove-doc :id application-id :docId (:id uusi-rakennus)) => fail?)


    (fact* "sauna doc and operation are removed"
      (let [sauna-attachment (first (attachment/get-attachments-by-operation application (:id (first sec-operations))))
            _ (upload-attachment pena application-id sauna-attachment true)
            _ (command pena :remove-doc :id application-id :docId (:id sauna)) => ok?
            updated-app (query-application pena application-id)]
        (domain/get-document-by-name updated-app "uusi-rakennus-ei-huoneistoa") => nil
        (:primaryOperation updated-app) =not=> nil?
        (count (:secondaryOperations updated-app)) => 0
        (fact "attachments belonging to operation don't exist anymore"
          (count (attachment/get-attachments-by-operation updated-app (:id (first sec-operations)))) => 0)
        (fact "old sauna attachment still exists after remove, because it had attachment versions uploaded"
          (some (hash-set (:id sauna-attachment)) (map :id (:attachments updated-app))))))))
