(ns lupapalvelu.document.document-api-itest
  (:require [midje.sweet :refer :all]
            [sade.util :refer [fn->] :as util]
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
      (command pena :create-doc :id application-id :schemaName "paasuunnittelija") => fail?
      (fact "application for puun-kaataminen does not contain 'suunnittelija' document"
        (let [application (query-application pena application-id)]
          (domain/get-document-by-name (:documents application)
                                       "suunnittelija") => falsey)))))

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
    party-document-names => ["hakija-r" "hakijan-asiamies" "maksaja" "suunnittelija"]))

(facts* "approve and reject document"
        (let [application    (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
              application-id (:id application)
              hakija         (domain/get-applicant-document (:documents application))
              uusi-rakennus  (domain/get-document-by-name application "uusiRakennus")]

          (doseq [[cmd status] [[:reject-doc "rejected"] [:approve-doc "approved"]]]
      (command pena  cmd :id application-id :doc (:id hakija) :path nil :collection "documents") => unauthorized?
      (command sonja cmd :id application-id :doc (:id hakija) :path nil :collection "documents") => ok?
      (command sonja cmd :id application-id :doc (:id uusi-rakennus) :path nil :collection "documents") => ok?
      (let [{old-modified :modified} (query-application pena application-id)]
        old-modified => truthy
        (when (= cmd :reject-doc)
          (command sonja :reject-doc-note :id application-id :doc (:id uusi-rakennus)
                   :path nil :collection "documents" :note (format "%s: Bu hao!" (name cmd))) => ok?)
        (let [approved-app          (query-application pena application-id)
              modified               (:modified approved-app)
              approved-uusi-rakennus (domain/get-document-by-name approved-app "uusiRakennus")]
         modified => truthy
         (>= modified old-modified) => true
         (get-in approved-uusi-rakennus [:meta :_approved :value]) => status
         (get-in approved-uusi-rakennus [:meta :_approved :timestamp]) => old-modified
         (get-in approved-uusi-rakennus [:meta :_approved :user :id]) => sonja-id
         (get-in approved-uusi-rakennus [:meta :_approved :note]) => "reject-doc: Bu hao!")))))

(facts "remove document"
  (let [application-id (:id (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id))
        _              (command pena :add-operation :id application-id :operation "vapaa-ajan-asuinrakennus")
        _              (command pena :add-operation :id application-id :operation "aloitusoikeus")
        application    (query-application pena application-id)
        hakija         (domain/get-applicant-document (:documents application))
        paasuunnittelija (domain/get-document-by-name application "paasuunnittelija")
        uusi-rakennus  (domain/get-document-by-name application "uusiRakennus")
        sauna          (domain/get-document-by-name application "uusi-rakennus-ei-huoneistoa")
        aloitusoikeus  (domain/get-document-by-name application "aloitusoikeus")
        primary-op     (:primaryOperation application)
        sec-operations (:secondaryOperations application)]


    (fact "new application has a primary operation and two secondary operations"
      (:name primary-op) => "kerrostalo-rivitalo"
      (count sec-operations) => 2
      (:name (first sec-operations)) => "vapaa-ajan-asuinrakennus")

    (fact "application has attachments with primary operation" (count (attachment/get-attachments-by-operation application (:id primary-op))) => pos?)

    (fact "application has attachments with secondary operation" (count (attachment/get-attachments-by-operation application (:id (first sec-operations)))) => pos?)

    (facts "not removable document cannot be removed"
      (get-in aloitusoikeus [:schema-info :removable]) => false

      (fact "by applicant"
        (command pena  :remove-doc :id application-id :docId (:id aloitusoikeus)) => (partial expected-failure? "error.not-allowed-to-remove-document"))

      (fact "by authority"
        (command sonja :remove-doc :id application-id :docId (:id aloitusoikeus)) => (partial expected-failure? "error.not-allowed-to-remove-document")))

    (fact "only authority can remove removable-only-by-authority document"
      (get-in paasuunnittelija [:schema-info :removable]) => true
      (get-in paasuunnittelija [:schema-info :removable-only-by-authority]) => true
      (command pena :remove-doc :id application-id :docId (:id paasuunnittelija)) => (partial expected-failure? "error.action-allowed-only-for-authority")
      (command sonja :remove-doc :id application-id :docId (:id paasuunnittelija)) => ok?)

    (fact "last hakija doc cannot be removed due :deny-removing-last-document flag"
      (get-in hakija [:schema-info :deny-removing-last-document]) => true
      (command pena :remove-doc :id application-id :docId (:id hakija)) => fail?)

    (fact "hakija doc is removed if there is more than one hakija-doc"
      (let [app-before (query-application pena application-id)
            create-resp (command pena :create-doc :id application-id :schemaName (get-in hakija [:schema-info :name]))
            application (query-application pena application-id)
            modified1   (:modified application)]
        create-resp => ok?
        (-> application :documents domain/get-applicant-documents count) => 2

        (command pena :remove-doc :id application-id :docId (:id hakija)) => ok?
        (let [updated-app (query-application pena application-id)]
          (fact "Modified changed on remove-doc"
            (< modified1 (:modified updated-app)) => true)
          (-> (:documents updated-app) domain/get-applicant-documents count) => 1
          (facts "every other doc and operation remains untouched"
            (fact "docs"
              (count (:documents updated-app)) => (count (:documents app-before)))
            (fact "secondary operations"
              (count (:secondaryOperations updated-app)) => (count (:secondaryOperations app-before)))
            (fact "primary operation"
              (:primaryOperation updated-app) => (:primaryOperation app-before))))))

    (fact "primary operation cannot be removed"
      (command pena :remove-doc :id application-id :docId (:id uusi-rakennus)) => fail?)


    (fact "sauna doc and operation are removed"

      (let [sauna-attachment (first (attachment/get-attachments-by-operation application (:id (first sec-operations))))]
        (fact "Upload sauna-attachment"
            sauna-attachment => truthy
            (upload-attachment pena application-id sauna-attachment true))

        (:id sauna) => truthy
        (command pena :remove-doc :id application-id :docId (:id sauna)) => ok?

        (let [updated-app (query-application pena application-id)]

          (domain/get-document-by-name updated-app "uusi-rakennus-ei-huoneistoa") => nil
          (:primaryOperation updated-app) =not=> nil?
          (count (:secondaryOperations updated-app)) => 1
          (fact "attachments belonging to operation don't exist anymore"
            (count (attachment/get-attachments-by-operation updated-app (:id (first sec-operations)))) => 0)
          (let [attachment (util/find-by-id (:id sauna-attachment) (:attachments updated-app))]
            (fact "old sauna attachment still exists after remove, because it had attachment versions uploaded"
              attachment)
            (fact "attachment op is unset"
              (:op attachment) => nil)
            (fact "attachment groupType is unset"
              (:groupType attachment) => nil)))))))

(facts* "post-verdict document modifications"
  (let [application    (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
        application-id (:id application)
        documents      (:documents application)
        applicant-doc  (domain/get-applicant-document documents)
        pre-verdict-suunnittelija (domain/get-document-by-name documents "suunnittelija")
        _ (command pena :update-doc :id application-id :doc (:id applicant-doc) :updates [["henkilo.henkilotiedot.etunimi" "test1"]]) => ok?
        _ (command pena :update-doc :id application-id :doc (:id pre-verdict-suunnittelija) :updates [["henkilotiedot.etunimi" "DeSigner"]]) => ok?
        _ (give-verdict sonja application-id) => ok?
        suunnittelija-resp (command pena :create-doc :id application-id :schemaName "suunnittelija")]

    (facts "disabling document - can set status for only documents with 'disableable'"
      (fact "hakija fail"
        (command pena :set-doc-status :id application-id :docId (:id applicant-doc) :value "disabled") => fail?
        (command sonja :set-doc-status :id application-id :docId (:id applicant-doc) :value "disabled") => fail?)
      (fact "can't set to disabled, when document is not approved"
        (command pena :set-doc-status
                 :id application-id
                 :docId (:doc suunnittelija-resp)
                 :value "disabled") => (partial expected-failure? :error.document-not-approved)
        ; set following documents to approved
        (command sonja :approve-doc :id application-id :doc (:doc suunnittelija-resp) :path nil :collection "documents") => ok?
        (command sonja :approve-doc :id application-id :doc (:id pre-verdict-suunnittelija) :path nil :collection "documents") => ok?)
      (fact "pre-verdict suunnittelija ok"
        (command pena :set-doc-status :id application-id :docId (:id pre-verdict-suunnittelija) :value "disabled") => ok?
        (fact "sonja can re-enable"
          (command sonja :set-doc-status :id application-id :docId (:id pre-verdict-suunnittelija) :value "enabled") => ok?))
      (fact "new suunnittelija ok"
        (command pena :set-doc-status :id application-id :docId (:doc suunnittelija-resp) :value "disabled") => ok?
        (fact "sonja can re-enable"
          (command sonja :set-doc-status :id application-id :docId (:doc suunnittelija-resp) :value "enabled") => ok?))
      (fact "actions allowed before document is disabled"
        (command sonja :reject-doc :id application-id :doc (:doc suunnittelija-resp) :path nil :collection "documents") => ok?
        pena => (allowed? :update-doc :id application-id :doc (:doc suunnittelija-resp))
        pena => (allowed? :remove-doc :id application-id :docId (:doc suunnittelija-resp))
        sonja => (allowed? :approve-doc :id application-id :doc (:doc suunnittelija-resp))
        sonja => (allowed? :reject-doc :id application-id :doc (:doc suunnittelija-resp)))
      (fact "actions denied when document approved and disabled"
        (command pena :set-doc-status :id application-id :docId (:doc suunnittelija-resp) :value "disabled") => (partial expected-failure? :error.document-not-approved)
        (command sonja :approve-doc :id application-id :doc (:doc suunnittelija-resp) :path nil :collection "documents") => ok?
        pena =not=> (allowed? :update-doc :id application-id :doc (:doc suunnittelija-resp))
        pena =not=> (allowed? :remove-doc :id application-id :docId (:doc suunnittelija-resp))
        (command pena :set-doc-status :id application-id :docId (:doc suunnittelija-resp) :value "disabled") => ok?
        pena =not=> (allowed? :update-doc :id application-id :doc (:doc suunnittelija-resp))
        pena =not=> (allowed? :remove-doc :id application-id :docId (:doc suunnittelija-resp))
        sonja =not=> (allowed? :approve-doc :id application-id :doc (:doc suunnittelija-resp))
        sonja =not=> (allowed? :reject-doc :id application-id :doc (:doc suunnittelija-resp)))
      ; Enable for rest of tests
      (command pena :set-doc-status :id application-id :docId (:doc suunnittelija-resp) :value "enabled") => ok?
      (command sonja :reject-doc :id application-id :doc (:doc suunnittelija-resp) :path nil :collection "documents"))


    (facts "create-doc"
      (command pena :create-doc :id application-id :schemaName "hakija-r") => (partial expected-failure? :error.document.post-verdict-addition)
      (fact "Suunnittelija can be added after verdict (defined in schema)"
        suunnittelija-resp => ok?)
      (fact "can't add another paasuunnittelija"
        (command pena :create-doc :id application-id :schemaName "paasuunnittelija") => fail?))

    (facts "update-doc"
      (fact "can't update applicant doc"
        (command pena :update-doc :id application-id :doc (:id applicant-doc) :updates [["henkilo.henkilotiedot.etunimi" "test"]])  => fail?)
      (fact "can't update pre-verdict designer doc"
        (command pena :update-doc :id application-id :doc (:id pre-verdict-suunnittelija) :updates [["henkilo.henkilotiedot.etunimi" "test"]])  => fail?)
      (fact "new suunnittelija can be updated"
        (command pena :update-doc :id application-id :doc (:doc suunnittelija-resp) :updates [["henkilotiedot.etunimi" "test"]]) => ok?))

    (facts "approve-doc"
      (fact "can't approve applicant doc"
        (command sonja :approve-doc :id application-id :doc (:id applicant-doc) :path nil :collection "documents") => fail?)
      (fact "pre-verdict suunnittelija doc approval"
        (command sonja :approve-doc :id application-id :doc (:id pre-verdict-suunnittelija) :path nil :collection "documents") => ok?)
      (fact "new suunnittelija doc approval"
        (command sonja :approve-doc :id application-id :doc (:doc suunnittelija-resp) :path nil :collection "documents") => ok?)
      (fact "after approval, Pena nor Sonja can't edit"
        (command pena :update-doc :id application-id
                 :doc (:doc suunnittelija-resp)
                 :updates [["henkilotiedot.etunimi" "Herkko"]]) => (partial expected-failure? :error.document.approved)
        (command sonja :update-doc :id application-id
                 :doc (:doc suunnittelija-resp)
                 :updates [["henkilotiedot.etunimi" "Herkko"]]) => (partial expected-failure? :error.document.approved))
      (fact "can't delete if approved"
        (command pena :remove-doc :id application-id :docId (:doc suunnittelija-resp)) => (partial expected-failure? :error.document.post-verdict-deletion)))

    (facts "reject-doc"
      (fact "can't update applicant doc"
        (command sonja :reject-doc :id application-id :doc (:id applicant-doc) :path nil :collection "documents") => fail?)
      (fact "new suunnittelija doc approval"
        (command sonja :reject-doc :id application-id :doc (:doc suunnittelija-resp) :path nil :collection "documents") => ok?
        (fact "Pena can edit again"
          (command pena :update-doc :id application-id :doc (:doc suunnittelija-resp) :updates [["henkilotiedot.etunimi" "Herkko"]]) => ok?))
      (fact "pre-verdict suunnittelija doc approval"
        (command sonja :reject-doc :id application-id :doc (:id pre-verdict-suunnittelija) :path nil :collection "documents") => ok?))

    (facts "remove-doc"
      (fact "can't remove applicant doc"
        (command pena :remove-doc :id application-id :docId (:id applicant-doc))) => fail?
      (fact "added suunnittelija doc can be removed"
        ; Here document is also 'rejected'
        (command pena :remove-doc :id application-id :docId (:doc suunnittelija-resp)) => ok?)
      (fact "pre-verdit suunnittelija doc can NOT be removed"
        (command pena :remove-doc :id application-id :docId (:id pre-verdict-suunnittelija)) => (partial expected-failure? :error.document.post-verdict-deletion)))))
