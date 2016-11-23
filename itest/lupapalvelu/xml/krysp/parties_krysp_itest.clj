(ns lupapalvelu.xml.krysp.parties-krysp-itest
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [clojure.data.xml :refer [parse]]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.xml :as xml]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.parties-canonical :as parties]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.validator :refer [validate]]))

(apply-remote-minimal)

(defn- final-xml-validation [application]
  (let [permit-type (keyword (permit/permit-type application))
        organization (organization-from-minimal-by-id (:organization application))
        sftp-user (get-in organization [:krysp permit-type :ftpUser])
        krysp-version (get-in organization [:krysp permit-type :version])
        permit-type-dir (permit/get-sftp-directory permit-type)
        output-dir (str "target/" sftp-user permit-type-dir "/")
        sftp-server (subs (env/value :fileserver-address) 7)
        target-file-name (str "target/Downloaded-" (:id application) "-" (now) ".xml")
        filename-starts-with (:id application)
        xml-file (if get-files-from-sftp-server?
                   (io/file (get-file-from-server
                              sftp-user
                              sftp-server
                              filename-starts-with
                              target-file-name
                              (str permit-type-dir "/")))
                   (io/file (get-local-filename output-dir filename-starts-with)))
        xml-as-string (slurp xml-file)
        xml (parse (io/reader xml-file))]

    (fact "Correctly named xml file is created" (.exists xml-file) => true)

    (fact "XML file is valid"
      (validate xml-as-string (:permitType application) krysp-version))

    (let [id (or
               (xml/get-text xml [:LupaTunnus :tunnus]) ; Rakval, poik, ymp.
               (xml/get-text xml [:Kasittelytieto :asiatunnus]))] ; YA
      (fact "Application ID" id => (:id application)))

    (fact "kasittelija"
      (let [kasittelytieto (or
                             (xml/select1 xml [:kasittelynTilatieto])
                             (xml/select1 xml [:Kasittelytieto])
                             (xml/select1 xml [:KasittelyTieto]))
            etunimi (xml/get-text kasittelytieto [:kasittelija :etunimi])
            sukunimi (xml/get-text kasittelytieto [:kasittelija :sukunimi])]
        etunimi => (just #"(Sonja|Rakennustarkastaja)")
        sukunimi => (just #"(Sibbo|J\u00e4rvenp\u00e4\u00e4)")))))

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
    ; TODO check that KRYSP has one (1) suunnittelija
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
        (let [krysp-resp (command sonja :parties-as-krysp :id application-id :lang "fi")]
          krysp-resp => ok?
          (:sentDocuments krysp-resp) => (just [(:doc post-verdict-suunnittelija1)]))))
    ))
