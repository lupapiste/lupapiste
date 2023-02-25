(ns lupapalvelu.document.document-api-itest
  (:require [lupapalvelu.attachment :as attachment]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.core :as core]
            [sade.strings :as ss]
            [sade.util :refer [fn->] :as util]
            [sade.xml :as xml]))

(apply-remote-minimal)

(facts* "facts about update-doc and validate-doc commands"
  (let [application-id   (create-app-id pena)
        application0     (query-application pena application-id) => truthy
        hakija-doc-id    (:id (domain/get-applicant-document (:documents application0)))
        _                (command pena :update-doc :id application-id :doc hakija-doc-id :updates [["henkilo.henkilotiedot.etunimi" "foo"]["henkilo.henkilotiedot.sukunimi" "bar"]]) => ok?
        modified1        (:modified (query-application pena application-id))
        rakennus-doc-id  (:id (domain/get-document-by-name application0 "rakennuspaikka")) => truthy
        _                (command pena :update-doc :id application-id :doc rakennus-doc-id :updates [["kiinteisto.maaraalaTunnus" "maaraalaTunnus"]]) => ok?
        application2     (query-application pena application-id)
        modified2        (:modified application2)
        hakija-doc       (domain/get-document-by-id application2 hakija-doc-id)
        rakennus-doc     (domain/get-document-by-id application2 rakennus-doc-id)
        failing-updates  [["rakennuksenOmistajat.henkilo.henkilotiedot.etunimi" "P0wnr"]]
        failing-result   (command pena :update-doc :id application-id :doc rakennus-doc-id :updates failing-updates)
        readonly-updates [["kiinteisto.tilanNimi" "tilannimi"]]
        readonly-result  (command pena :update-doc :id application-id :doc rakennus-doc-id :updates readonly-updates)]

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

(facts "validate-input-enabled-for-org"
  (let [application-id             (create-app-id pena)
        application                (query-application pena application-id) => truthy
        uusi-rakennus-doc-id       (:id (domain/get-document-by-name application "uusiRakennus"))
        update-org (fn [krysp-version]
                     (fact "update krysp-version for org"
                       (command sipoo :set-krysp-endpoint :organizationId "753-R" :url "http://localhost:8000/dev/krysp"
                                :username "" :password "" :version krysp-version :permitType "R")
                       => ok?))
        update-doc (fn [updates res & [app-id doc-id]]
                     (let [application-id (or app-id application-id)
                           uusi-rakennus-doc-id (or doc-id uusi-rakennus-doc-id)]
                       (fact {:midje/description (str "Updates: " updates " Expected result: " res)}
                         (command pena :update-doc :id application-id :doc uusi-rakennus-doc-id
                                  :updates updates :collection "documents") => res)))
        update-rakennusluokat (fn [enabled]
                                (fact "update rakennusluokat for org"
                                  (command admin :set-organization-boolean-attribute
                                           :enabled enabled
                                           :organizationId "753-R"
                                           :attribute :rakennusluokat-enabled) => ok?))
        fail-excluded? (err :error-trying-to-update-excluded-input-field)]

    (facts "2.2.2"
      (update-doc [["kaytto.tilapainenRakennusvoimassaPvm", "17.1.2020"]] fail-excluded?)
      (update-doc [["kaytto.rakennusluokka", "0110 omakotitalot"]] fail-excluded?)
      (update-doc [["kaytto.rakentajaTyyppi", "muu"]] ok?)
      (update-doc [["kaytto.tilapainenRakennusKytkin", true]] fail-excluded?))

    (facts "2.2.3"
      (update-org "2.2.3")
      (update-doc [["kaytto.tilapainenRakennusvoimassaPvm", "17.1.2020"]] fail-excluded?)
      (update-doc [["kaytto.rakennusluokka", "0110 omakotitalot"]] fail-excluded?)
      (update-doc [["kaytto.rakentajaTyyppi", "muu"]] ok?)
      (update-doc [["kaytto.tilapainenRakennusKytkin", true]] ok?))

    (facts "2.2.4, rakennusluokat disabled"
      (update-org "2.2.4")
      (update-doc [["kaytto.tilapainenRakennusKytkin", false]] ok?)
      (update-doc [["kaytto.tilapainenRakennusvoimassaPvm", "17.1.2020"]] ok?)
      (update-doc [["kaytto.rakennusluokka", "0110 omakotitalot"]] fail?)
      (update-doc [["kaytto.kayttotarkoitus", "032 luhtitalot"]] ok?))

    (facts "2.2.4 rakennusluokat enabled"
      (update-rakennusluokat true)
      (update-doc [["kaytto.tilapainenRakennusKytkin", true]] ok?)
      (update-doc [["kaytto.tilapainenRakennusvoimassaPvm", "17.1.2020"]] ok?)
      (update-doc [["kaytto.rakennusluokka", "0110 omakotitalot"]] ok?)
      (update-doc [["kaytto.kayttotarkoitus", "022 ketjutalot"]] ok?))))

(facts "invited user access"
  (let [application-id   (create-app-id pena)
        rakennus-doc-id  (-> (query-application pena application-id) (domain/get-document-by-name "rakennuspaikka") :id) => truthy]
    (fact "pena is allowed to update application"
      (command pena :update-doc :id application-id :doc rakennus-doc-id :updates [["kiinteisto.maaraalaTunnus" "mtunnus"]]) => ok?)

    (fact "pena is cannot update unexisting doc"
      (command pena :update-doc :id application-id :doc "invalid-doc-id" :updates [["kiinteisto.maaraalaTunnus" "mtunnus"]]) => (partial expected-failure? :error.document-not-found))

    (fact "teppo is not allowed to update document"
      (command teppo :update-doc :id application-id :doc rakennus-doc-id :updates [["kiinteisto.maaraalaTunnus" "mtunnus"]]) => (partial expected-failure? :error.application-not-accessible))

    (fact "teppo is invited to application as foreman"
      (command pena :invite-with-role :id application-id :email (email-for-key teppo) :role "foreman" :text "wilkommen" :documentName "" :documentId "" :path "") => ok?)

    (fact "teppo approves invite"
      (command teppo :approve-invite :id application-id) => ok?)

    (fact "teppo is allowed to update document after approving invite"
      (command teppo :update-doc :id application-id :doc rakennus-doc-id :updates [["kiinteisto.maaraalaTunnus" "tilannimi"]]) => ok?)

    (fact "mikko is invited to applition as writer"
      (command pena :invite-with-role :id application-id :email (email-for-key mikko) :role "writer" :text "wilkommen" :documentName "" :documentId "" :path "") => ok?)

    (fact "mikko approves invite"
      (command mikko :approve-invite :id application-id) => ok?)

    (fact "mikko is allowed to update document after approving invite"
      (command mikko :update-doc :id application-id :doc rakennus-doc-id :updates [["kiinteisto.maaraalaTunnus" "tilannimi"]]) => ok?)

    (fact "solita is invited to application"
      (invite-company-and-accept-invitation pena application-id "solita" kaino))

    (fact "kaino is allowed to update document"
      (command kaino :update-doc :id application-id :doc rakennus-doc-id :updates [["kiinteisto.maaraalaTunnus" "tilannimi"]]) => ok?)))

(facts "create and query document"
  (let [application-id   (create-app-id pena)
        application0     (query-application pena application-id)
        ok-result        (command pena :create-doc :id application-id :schemaName "hakija-r")
        doc-id           (:doc ok-result)
        no-schema-result        (command pena :create-doc :id application-id :schemaName "foo")
        repeating-schema-result (command pena :create-doc :id application-id :schemaName "hakija-r")
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
        _                          (command pena :update-doc :id application-id :doc uusi-rakennus-doc-id :updates [["huoneistot.0.porras" "A"]
                                                                                                                    ["huoneistot.1.porras" "B"]
                                                                                                                    ["huoneistot.2.porras" "C"]])
        data-of                    (fn [app] (->> app :documents (filter (fn-> :id (= uusi-rakennus-doc-id))) first :data))
        app-with-three-apartments  (query-application pena application-id)
        three-apartments           (data-of app-with-three-apartments)
        resp                       (command pena :remove-document-data :id application-id :doc uusi-rakennus-doc-id :path ["huoneistot" "0"])
        app-with-two-apartments    (query-application pena application-id)
        two-apartments             (data-of app-with-two-apartments)]
    resp => ok?
    (fact (-> three-apartments :huoneistot keys) => (just #{:2 :1 :0}))
    (fact (-> two-apartments :huoneistot keys) => (just #{:2 :1}))))

(fact "building data"
  (let [application-id             (create-app-id pena)
        application                (query-application pena application-id)
        uusi-rakennus-doc-id       (:id (domain/get-document-by-name application "uusiRakennus"))]
    (fact "'lyhyt rakennustunnus' cannot exceed maximum length"
      (command pena :update-doc :id application-id :doc uusi-rakennus-doc-id :updates [["tunnus" "123456"]]) => ok?
      (command pena :update-doc :id application-id :doc uusi-rakennus-doc-id :updates [["tunnus" "1234567"]]) => fail?)
    (command pena :submit-application :id application-id) => ok?
    (fact "valtakunnallinenNumero can't be edited"
      (command sonja :update-doc :id application-id :doc uusi-rakennus-doc-id :updates [["valtakunnallinenNumero" "1234567"]]) => unauthorized?
      (command pena :update-doc :id application-id :doc uusi-rakennus-doc-id :updates [["valtakunnallinenNumero" "1234567"]]) => unauthorized?)))

(facts "facts about party-document-names query"
  (let [application-id        (create-app-id pena)
        party-document-names  (:partyDocumentNames (query pena :party-document-names :id application-id))]
    (fact "pre-verdict"
      party-document-names => ["hakija-r" "hakijan-asiamies" "suunnittelija"])

    (command pena :submit-application :id application-id) => ok?
    (give-legacy-verdict sonja application-id)

    (fact "post-verdict"
      (:partyDocumentNames (query pena :party-document-names :id application-id)) => (just ["suunnittelija"]))))

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
      (get-in aloitusoikeus [:schema-info :removable-by]) => "none"

      (fact "by applicant"
        (command pena  :remove-doc :id application-id :docId (:id aloitusoikeus)) => (partial expected-failure? "error.not-allowed-to-remove-document"))

      (fact "by authority"
        (command sonja :remove-doc :id application-id :docId (:id aloitusoikeus)) => (partial expected-failure? "error.not-allowed-to-remove-document")))

    (fact "only authority can remove removable-only-by-authority document"
      (get-in paasuunnittelija [:schema-info :removable-by]) => "authority"
      (command pena :remove-doc :id application-id :docId (:id paasuunnittelija)) => (partial expected-failure? "error.not-allowed-to-remove-document")
      (command sonja :remove-doc :id application-id :docId (:id paasuunnittelija)) => ok?)

    (fact "last hakija doc cannot be removed due :deny-removing-last-document flag"
      (get-in hakija [:schema-info :last-removable-by]) => "none"
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
        _ (give-legacy-verdict sonja application-id)
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

(facts "Corner case: authority can remove disabled document in pre-verdict state"
  (let [{app-id :id
         docs   :documents} (create-and-submit-application pena
                                                         :operation "pientalo"
                                                         :propertyId sipoo-property-id)
        {doc-id :id}      (util/find-first #(= (get-in % [:schema-info :name]) "suunnittelija")
                                           docs)
        verdict-id        (give-legacy-verdict sonja app-id)]
    (fact "Disable document"
      (command sonja :set-doc-status :id app-id
               :docId doc-id
               :value "disabled") => ok?)
    (fact "The document cannot be removed"
      (command sonja :remove-doc :id app-id
               :docId doc-id) => fail?)
    (fact "Delete the only verdict"
      (command sonja :delete-pate-verdict :id app-id
               :verdict-id verdict-id) => ok?)
    (fact "Application state is rewound"
      (query-application sonja app-id)
      => (contains {:state "submitted"}))
    (fact "Document cannot be enabled"
      (command sonja :set-doc-status :id app-id
               :docId doc-id
               :value "enabled") => fail?)
    (fact "Pena cannot remove the disabled document"
      (command pena :remove-doc :id app-id
               :docId doc-id) => fail?)
    (fact ".. but Sonja can"
      (command sonja :remove-doc :id app-id
               :docId doc-id) => ok?)))

(facts "PATE - post-verdict document modifications"
  (apply-remote-fixture "pate-verdict")
  (let [application (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
        application-id (:id application)
        {link-app-id :id} (create-and-submit-application pena :operation "pientalo" :propertyId sipoo-property-id)
        documents (:documents application)
        building-doc (domain/get-document-by-name documents "uusiRakennus")
        building-doc-id (:id building-doc)
        _ (command pena :update-doc :id application-id :doc (:id building-doc) :updates [["mitat.tilavuus" "1000"]
                                                                                         ["mitat.kerrosala" "300"]
                                                                                         ["mitat.kokonaisala" "500"]
                                                                                         ["mitat.kerrosluku" "2"]]) => ok?
        _ (command sonja :update-app-bulletin-op-description :id application-id :description "Bulletin description") => ok?
        _ (command sonja :approve-application :id application-id :lang "fi") => ok?]

    (fact "Documents cant be modified before verdict is given"
      (command sonja :update-post-verdict-doc :id application-id :doc (:id building-doc) :updates [["mitat.tilavuus" "2000"]])
        => (partial expected-failure? :error.command-illegal-state))

    (facts "Publish verdict for application"
      (let [{verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                              :id application-id
                                              :template-id (-> pate-fixture/verdict-templates-setting-r
                                                               :templates
                                                               first
                                                               :id))]

        (fact "Set automatic calculation of other dates"
          (command sonja :edit-pate-verdict :id application-id :verdict-id verdict-id
                   :path [:automatic-verdict-dates] :value true) => no-errors?)
        (fact "Verdict date"
          (command sonja :edit-pate-verdict :id application-id :verdict-id verdict-id
                   :path [:verdict-date] :value (core/now)) => no-errors?)
        (fact "Verdict code"
          (command sonja :edit-pate-verdict :id application-id :verdict-id verdict-id
                   :path [:verdict-code] :value "hyvaksytty") => no-errors?)
        (fact "Publish verdict"
          (command sonja :publish-pate-verdict :id application-id :verdict-id verdict-id) => no-errors?)
        (verdict-pdf-queue-test sonja {:app-id application-id :verdict-id verdict-id})))

    (fact "Now document can be modified"
      (command sonja :update-post-verdict-doc :id application-id :doc building-doc-id :updates [["mitat.tilavuus" "2000"]
                                                                                                ["mitat.kerrosala" "600"]
                                                                                                ["mitat.kokonaisala" "1000"]
                                                                                                ["mitat.kerrosluku" "3"]]) => ok?)

    (fact "Applicant still cannot modify documents"
      (command pena :update-post-verdict-doc :id application-id :doc building-doc-id :updates [["mitat.tilavuus" "5000"]])
        => (partial expected-failure? :error.unauthorized))

    (fact "But no owner details can be modified"
      (command sonja :update-post-verdict-doc :id application-id :doc building-doc-id :updates [["rakennuksenOmistajat.henkilo.henkilotiedot.etunimi" "Herkko"]])
        => (partial expected-failure? :error.document-not-editable-in-current-state))

    (fact "Document is modified"
      (let [updated-app (query-application sonja application-id)
            updated-docs (:documents updated-app)
            updated-building-doc (domain/get-document-by-name updated-docs "uusiRakennus")]

        (get-in updated-building-doc [:data :mitat :tilavuus :value]) => "2000"
        (get-in updated-building-doc [:data :mitat :kerrosala :value]) => "600"
        (get-in updated-building-doc [:data :mitat :kokonaisala :value]) => "1000"
        (get-in updated-building-doc [:data :mitat :kerrosluku :value]) => "3"

        (fact "There is also meta data"
          (get-in updated-building-doc [:meta :_post_verdict_edit :timestamp]) => some?
          (get-in updated-building-doc [:meta :_post_verdict_edit :user :firstName]) => "Sonja")))

    (fact "Applicant cannot send modified document to backend"
      (command pena :send-doc-updates :id application-id :docId building-doc-id)
      => (partial expected-failure? :error.unauthorized))

    (fact "Applicant can add link permit"
      (command pena :add-link-permit :id application-id :linkPermitId link-app-id) => ok?)

    (fact "Sending doc updates fails when sftp-transfer is disabled"
      (command sonja :send-doc-updates :id application-id :docId building-doc-id)
      => {:ok false :text "error.sftp-disabled"}
      (command admin :update-organization
               :pateSftp true
               :municipality "753"
               :permitType "R")
      => ok?
      (fact "now allowed"
        sonja => (allowed? :send-doc-updates :id application-id :docId building-doc-id)))

    (fact "Sending modified document to backend"
      (command sonja :send-doc-updates :id application-id :docId building-doc-id) => ok?)

    (let [final-app (query-application sonja application-id)
          final-building-doc (domain/get-document-by-name (:documents final-app) "uusiRakennus")]

      (fact "And now there is also sent meta data"
        (get-in final-building-doc [:meta :_post_verdict_sent :timestamp]) => some?
        (get-in final-building-doc [:meta :_post_verdict_sent :user :firstName]) => "Sonja")

      (fact "KuntaGML xml contains modified fields"
        (let [xml (get-valid-krysp-xml final-app (fn [file]
                                                   (let [filename (file-name-accessor file)]
                                                     (when (and (ss/contains? filename (:id application))
                                                                (.endsWith filename "_RH.xml"))
                                                       filename))))
              building-path [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto :Toimenpide :rakennustieto :Rakennus :rakennuksenTiedot]
              asia-path [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia]
              building-element (xml/select xml building-path)
              asia-element (xml/select xml asia-path)]
          (xml/get-text xml [:viitelupatieto :tunnus]) => link-app-id
          (xml/get-text (first building-element) [:tilavuus]) => "2000"
          (xml/get-text (first building-element) [:kerrosala]) => "600"
          (xml/get-text (first building-element) [:kokonaisala]) => "1000"
          (xml/get-text (first building-element) [:kerrosluku]) => "3"
          (xml/get-text (first asia-element) [:kayttotapaus]) => "RH-tietojen muutos")))

    (fact "Disable integration"
      (command sipoo :set-krysp-endpoint
               :organizationId "753-R"
               :url ""
               :permitType "R"
               :version ""
               :password ""
               :username "") => ok?
      (command sonja :send-doc-updates :id application-id :docId building-doc-id)
      => (err :error.krysp-integration))))

(facts set-current-user-to-document
  (let [{app-id :id
         :as    app} (create-and-open-application pena :address "My information"
                                                  :propertyId sipoo-property-id
                                                  :operation "pientalo")
        {doc-id :id} (domain/get-document-by-name app "paatoksen-toimitus-rakval")]
    (fact "Not allowed for authority"
      (command sonja :set-current-user-to-document :id app-id
               :documentId doc-id
               :path "") => unauthorized?)
    (fact "Allowed for applicant"
      (command pena :set-current-user-to-document :id app-id
               :documentId doc-id
               :path "") => ok?)
    (fact "Information has been filled"
      (-> (query-application pena app-id)
          (domain/get-document-by-name "paatoksen-toimitus-rakval")
          :data
          tools/unwrapped
          :henkilotiedot) => (contains {:etunimi  "Pena"
                                        :sukunimi "Panaani"}))))
