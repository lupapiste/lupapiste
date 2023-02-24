(ns lupapalvelu.backing-system.krysp.parties-krysp-itest
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]))

(apply-remote-minimal)

(defn get-doc-id-filename-pred
  [doc-id]
  (fn [file]
    (let [filename (file-name-accessor file)]
      (when (and (ss/contains? filename doc-id)
                 (ss/ends-with filename ".xml"))
        filename))))

(defn- check-pre-verdict-parties [application xml]
  (let [id (xml/get-text xml [:LupaTunnus :tunnus])]
    (fact "Application ID" id => (:id application)))

  (facts "Designers"
    (let [asia-path [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia]
          designers-path (conj asia-path :osapuolettieto :Osapuolet :suunnittelijatieto)
          designer-element (xml/select xml designers-path)]
      (fact "Two pre-verdict designers"
        (count designer-element) => 2
        (xml/get-text (first designer-element) [:Suunnittelija :henkilo :etunimi]) => "Pre2"
        (xml/get-text (second designer-element) [:Suunnittelija :henkilo :etunimi]) => "Pre1"))))

(facts* "Post verdict parties KRYSP message"
  (let [application-id (create-app-id pena :propertyId "75341600550007" :operation "kerrostalo-rivitalo" :address "Ryspitie 289")
        application (query-application pena application-id)
        pre-verdict-suunnittelija1 (domain/get-document-by-name application "suunnittelija")
        pre-verdict-suunnittelija2 (command pena :create-doc :id application-id :schemaName "suunnittelija") => ok?
        pre-verdict-suunnittelija3 (command pena :create-doc :id application-id :schemaName "suunnittelija") => ok?
        pre-verdict-non-approved (command pena :create-doc :id application-id :schemaName "suunnittelija") => ok?
        _ (command pena :update-doc :id application-id :doc (:id pre-verdict-suunnittelija1) :updates [["henkilotiedot.etunimi" "Pre1"]
                                                                                                       ["henkilotiedot.sukunimi" "Suunnittelija"]]) => ok?
        _ (command pena :update-doc :id application-id :doc (:doc pre-verdict-suunnittelija2) :updates [["henkilotiedot.etunimi" "Pre2"]
                                                                                                        ["henkilotiedot.sukunimi" "Suunnittelija"]]) => ok?
        _ (command pena :update-doc :id application-id :doc (:doc pre-verdict-suunnittelija3) :updates [["henkilotiedot.etunimi" "Pre3"]
                                                                                                        ["henkilotiedot.sukunimi" "Suunnittelija"]]) => ok?
        _ (command pena :update-doc :id application-id :doc (:doc pre-verdict-non-approved) :updates [["henkilotiedot.etunimi" "Non-Approved"]
                                                                                                      ["henkilotiedot.sukunimi" "Suunnittelija"]]) => ok?
        ]

    (command pena :submit-application :id application-id) => ok?
    (command sonja :approve-doc :id application-id :doc (:id pre-verdict-suunnittelija1) :path nil :collection "documents") => ok?
    (command sonja :approve-doc :id application-id :doc (:doc pre-verdict-suunnittelija2) :path nil :collection "documents") => ok?
    (command sonja :update-app-bulletin-op-description :id application-id :description "otsikko julkipanoon") => ok?
    (command sonja :approve-application :id application-id :lang "fi") => ok?

    (let [application (query-application sonja application-id)
          xml (get-valid-krysp-xml application (:id application))]
      (check-pre-verdict-parties application xml))

    (give-legacy-verdict sonja application-id)

    (let [post-verdict-suunnittelija1 (command pena :create-doc :id application-id :schemaName "suunnittelija") => ok?
          post-verdict-suunnittelija2 (command pena :create-doc :id application-id :schemaName "suunnittelija") => ok?
          post-verdict-suunnittelija3 (command pena :create-doc :id application-id :schemaName "suunnittelija") => ok?
          _  (command pena :remove-doc :id application-id :docId (:doc post-verdict-suunnittelija3)) => ok? ; can be removed
          post-verdict-suunnittelija3 (command pena :create-doc :id application-id :schemaName "suunnittelija") => ok?
          post-verdict-suunnittelija4 (command pena :create-doc :id application-id :schemaName "suunnittelija") => ok?]
      (fact "updating post-verdict documents"
        (command pena :update-doc :id application-id :doc (:doc post-verdict-suunnittelija1) :updates [["henkilotiedot.etunimi" "Post1"]
                                                                                                       ["henkilotiedot.sukunimi" "Suunnittelija"]]) => ok?
        (command pena :update-doc :id application-id :doc (:doc post-verdict-suunnittelija2) :updates [["henkilotiedot.etunimi" "Post2"]
                                                                                                       ["henkilotiedot.sukunimi" "Suunnittelija"]]) => ok?
        (command pena :update-doc :id application-id :doc (:doc post-verdict-suunnittelija3) :updates [["henkilotiedot.etunimi" "Post3"]
                                                                                                       ["henkilotiedot.sukunimi" "Suunnittelija"]]) => ok?
        (command pena :update-doc :id application-id :doc (:doc post-verdict-suunnittelija4) :updates [["henkilotiedot.etunimi" "Post4"]
                                                                                                       ["henkilotiedot.sukunimi" "Suunnittelija"]]) => ok?)
      (fact "Sonja approves/rejects docs"
        (command sonja :approve-doc :id application-id :doc (:doc post-verdict-suunnittelija1) :path nil :collection "documents") => ok?
        (command sonja :approve-doc :id application-id :doc (:doc post-verdict-suunnittelija2) :path nil :collection "documents") => ok?
        (command sonja :reject-doc :id application-id :doc (:doc post-verdict-suunnittelija3) :path nil :collection "documents") => ok?)
      (fact "Pena couldn't remove approved"
        pena =not=> (allowed? :remove-doc :id application-id :docId (:doc post-verdict-suunnittelija1))
        pena =not=> (allowed? :remove-doc :id application-id :docId (:doc post-verdict-suunnittelija2))
        (fact "but could remove rejected and untouched"
          pena => (allowed? :remove-doc :id application-id :docId (:doc post-verdict-suunnittelija3))
          pena => (allowed? :remove-doc :id application-id :docId (:doc post-verdict-suunnittelija4))))
      (fact "Pena disables designers pre2 and post2"
        (command pena :set-doc-status :id application-id :docId (:doc pre-verdict-suunnittelija2) :value "disabled") => ok?
        (command pena :set-doc-status :id application-id :docId (:doc post-verdict-suunnittelija2) :value "disabled") => ok?
        ; by accident clicks it back to enabled
        (command pena :set-doc-status :id application-id :docId (:doc post-verdict-suunnittelija2) :value "enabled") => ok?
        (command pena :set-doc-status :id application-id :docId (:doc post-verdict-suunnittelija2) :value "disabled") => ok?
        (fact "can't disable documents that are not approved"
          (command pena :set-doc-status :id application-id :docId (:doc post-verdict-suunnittelija3) :value "disabled") => fail?))

      (facts "Sonja sends parties KRYSP message"
        (let [krysp-resp (command sonja :parties-as-krysp :id application-id :lang "fi")
              _ (count (:sentDocuments krysp-resp)) => 1
              application (query-application sonja application-id)
              xml (get-valid-krysp-xml application (get-doc-id-filename-pred (first (:sentDocuments krysp-resp))))
              asia-path [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia]
              parties-path (conj asia-path :osapuolettieto :Osapuolet)
              designers-path (conj parties-path :suunnittelijatieto)
              designer-elements (xml/select xml designers-path)]
          krysp-resp => ok?
          (:sentDocuments krysp-resp) => (just [(:doc post-verdict-suunnittelija1)])

          (fact "Parties transfer item"
            (count (:transfers application)) => 2
            (count (filter #(= :parties-to-backing-system (keyword (:type %))) (:transfers application))) => 1
            (let [transfer (util/find-first #(= :parties-to-backing-system (keyword (:type %))) (:transfers application))]
              (:party-documents transfer) => (just (:sentDocuments krysp-resp))))

          (fact "Only designer parties"
            (map :tag (xml/children (xml/select1 xml parties-path))) => (just :suunnittelijatieto))
          (fact "Only Post1 is sent to KRYSP"
            ; ... because:
            ; 1) pre-verdict designers are not included
            ; 2) post-verdict2 is disabled
            ; 3) post-verdict3 is rejected
            ; 4) post-verdict4 is not approved
            (count designer-elements) => 1
            (xml/get-text designer-elements [:henkilo :etunimi]) => "Post1")
          (fact "kayttotapaus is set"
            (xml/get-text xml (conj asia-path :kayttotapaus)) => "Uuden suunnittelijan nime\u00e4minen"))

        (fact "Sonja approves Post3 and Non-Approved and sends designers again"
          (command sonja :approve-doc :id application-id :doc (:doc post-verdict-suunnittelija3) :path nil :collection "documents") => ok?
          (command sonja :approve-doc :id application-id :doc (:doc pre-verdict-non-approved) :path nil :collection "documents") => ok?
          (let [second-resp (command sonja :parties-as-krysp :id application-id :lang "fi")
                application (query-application sonja application-id)
                first-xml (get-valid-krysp-xml application (get-doc-id-filename-pred (:doc post-verdict-suunnittelija1)))
                second-xml (get-valid-krysp-xml application (get-doc-id-filename-pred (:doc post-verdict-suunnittelija3)))
                pre-verdict-designer-xml (get-valid-krysp-xml application (get-doc-id-filename-pred (:doc pre-verdict-non-approved)))
                designers-path [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :osapuolettieto :Osapuolet :suunnittelijatieto]
                first-designer-elements (xml/select first-xml designers-path)
                second-designer-elements (xml/select second-xml designers-path)
                third-designer-elements (xml/select pre-verdict-designer-xml designers-path)]
            second-resp => ok?
            (count (:transfers application)) => 3
            (fact "three documents have been sent"
              (:sentDocuments second-resp) => (just [(:doc pre-verdict-non-approved)
                                                     (:doc post-verdict-suunnittelija1)
                                                     (:doc post-verdict-suunnittelija3)]))
            (fact "Post1 in KRYSP message"
              (count first-designer-elements) => 1
              (xml/get-text (first first-designer-elements) [:henkilo :etunimi]) => "Post1")
            (fact "Post3 in KRYSP message"
              (count second-designer-elements) => 1
              (xml/get-text (first second-designer-elements) [:henkilo :etunimi]) => "Post3")
            (fact "Non-Approved designer also in KRYSP"
              (count third-designer-elements) => 1
              (xml/get-text (first third-designer-elements) [:henkilo :etunimi]) => "Non-Approved"))
          (fact "HTTP enabled -> can't send"
            (command admin :set-kuntagml-http-endpoint :partner "matti"
                     :url (str (server-address) "/dev/krysp/receiver") :organization "753-R" :permitType "R"
                     :username "kuntagml" :password "kryspi") => ok?
            (command admin :set-organization-boolean-path
                     :path "krysp.R.http.enabled" :value true
                     :organizationId "753-R") => ok?
            (command sonja :parties-as-krysp :id application-id :lang "fi") => (partial expected-failure? :error.integration.krysp-http)
            (command admin :delete-kuntagml-http-endpoint :organization "753-R" :permitType "R") => ok?))))))
