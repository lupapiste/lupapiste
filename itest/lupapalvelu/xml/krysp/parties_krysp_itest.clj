(ns lupapalvelu.xml.krysp.parties-krysp-itest
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [clojure.data.xml :refer [parse]]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.util :as util]
            [sade.xml :as xml]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.validator :refer [validate]]))

(apply-remote-minimal)

(defn- get-valid-krysp-xml [application]
  (let [permit-type      (keyword (permit/permit-type application))
        organization     (organization-from-minimal-by-id (:organization application))
        sftp-user        (get-in organization [:krysp permit-type :ftpUser])
        krysp-version    (get-in organization [:krysp permit-type :version])
        permit-type-dir  (permit/get-sftp-directory permit-type)
        output-dir       (str "target/" sftp-user permit-type-dir "/")
        sftp-server      (subs (env/value :fileserver-address) 7)
        target-file-name (str "target/Downloaded-" (:id application) "-" (now) ".xml")
        file-starts-with (:id application)
        xml-file (if get-files-from-sftp-server?
                   (io/file (get-file-from-server
                              sftp-user
                              sftp-server
                              file-starts-with
                              target-file-name
                              (str permit-type-dir "/")))
                   (io/file (get-local-filename output-dir file-starts-with)))
        xml-as-string (slurp xml-file)
        xml (parse xml-file)]

    (fact "Correctly named xml file is created" (.exists xml-file) => true)
    (fact "XML file is valid"
      (validate xml-as-string (:permitType application) krysp-version))

    xml))

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
        _ (command pena :update-doc :id application-id :doc (:id pre-verdict-suunnittelija1) :updates [["henkilotiedot.etunimi" "Pre1"]
                                                                                                       ["henkilotiedot.sukunimi" "Suunnittelija"]]) => ok?
        _ (command pena :update-doc :id application-id :doc (:doc pre-verdict-suunnittelija2) :updates [["henkilotiedot.etunimi" "Pre2"]
                                                                                                        ["henkilotiedot.sukunimi" "Suunnittelija"]]) => ok?
        _ (command pena :update-doc :id application-id :doc (:doc pre-verdict-suunnittelija3) :updates [["henkilotiedot.etunimi" "Pre3"]
                                                                                                        ["henkilotiedot.sukunimi" "Suunnittelija"]]) => ok?
        ]

    (command pena :submit-application :id application-id) => ok?
    (command sonja :approve-doc :id application-id :doc (:id pre-verdict-suunnittelija1) :path nil :collection "documents") => ok?
    (command sonja :approve-doc :id application-id :doc (:doc pre-verdict-suunnittelija2) :path nil :collection "documents") => ok?
    (command sonja :approve-application :id application-id :lang "fi") => ok?

    (let [application (query-application sonja application-id)
          xml (get-valid-krysp-xml application)]
      (check-pre-verdict-parties application xml))

    (give-verdict sonja application-id) => ok?

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
              application (query-application sonja application-id)
              xml (get-valid-krysp-xml application)
              asia-path [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia]
              parties-path (conj asia-path :osapuolettieto :Osapuolet)
              designers-path (conj parties-path :suunnittelijatieto)
              designer-elements (xml/select xml designers-path)]
          krysp-resp => ok?
          (:sentDocuments krysp-resp) => (just [(:doc post-verdict-suunnittelija1)])

          (fact "Parties transfer item"
            (count (:transfers application)) => 2
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

        (fact "Sonja approves Post3 and sends designers again"
          (command sonja :approve-doc :id application-id :doc (:doc post-verdict-suunnittelija3) :path nil :collection "documents") => ok?
          (let [second-resp (command sonja :parties-as-krysp :id application-id :lang "fi")
                application (query-application sonja application-id)
                second-xml (get-valid-krysp-xml application)
                designers-path [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :osapuolettieto :Osapuolet :suunnittelijatieto]
                designer-elements (xml/select second-xml designers-path)]
            second-resp => ok?
            (:sentDocuments second-resp) => (just [(:doc post-verdict-suunnittelija1)
                                                   (:doc post-verdict-suunnittelija3)])
            (fact "Post1 and Post3 in KRSYP message"
              (count designer-elements) => 2
              (xml/get-text (first designer-elements) [:henkilo :etunimi]) => "Post3"
              (xml/get-text (second designer-elements) [:henkilo :etunimi]) => "Post1")))))))
