(ns lupapalvelu.ya-digging-permit-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.application :as app]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.operations :as operations]
            [sade.env :as env]
            [sade.property :as prop]))

(apply-remote-minimal)

(def ^:private pena-user (find-user-from-minimal-by-apikey pena))

(facts "creating digging permit"
  (fact "fails if source application is not sijoituslupa/sopimus"
    (let [source-app (create-and-submit-application pena :operation "kerrostalo-rivitalo")]
      (command pena :create-digging-permit :id (:id source-app)
               :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen")
      => (partial expected-failure? "error.invalid-digging-permit-source")))

  (fact "fails if operation is not digging operation"
    (let [source-app (create-and-submit-application pena :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen")]
      (give-verdict sonja (:id source-app)) => ok?
      (command pena :create-digging-permit :id (:id source-app)
               :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen")
      => (partial expected-failure? "error.not-digging-permit-operation")))

  (fact "fails if operation is not selected for source permit's organization"
    (let [source-app (create-and-submit-application pena :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen")]
      (give-verdict sonja (:id source-app)) => ok?
      (command pena :create-digging-permit :id (:id source-app)
               :operation "ya-katulupa-maalampotyot") ; not selected for Sipoo YA in minimal fixture
      => (partial expected-failure? "error.operations.hidden")))

  (fact "applicant and payer documents are copied from the source application"
    (let [source-app (create-and-submit-application pena :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen")
          maksaja-doc-id (:id (domain/get-document-by-name (:documents source-app) "yleiset-alueet-maksaja"))
          hakija-doc-id (:id (domain/get-applicant-document (:documents source-app)))]
      (command pena :update-doc :id (:id source-app) :doc hakija-doc-id :collection "documents"
               :updates [["_selected" "henkilo"]
                         ["henkilo.henkilotiedot.etunimi" (:firstName pena-user)]
                         ["henkilo.henkilotiedot.sukunimi" (:lastName pena-user)]
                         ["henkilo.userId" (:id pena-user)]])
      (command pena :update-doc :id (:id source-app) :doc maksaja-doc-id :collection "documents"
               :updates [["_selected" "henkilo"]
                         ["henkilo.henkilotiedot.etunimi" (:firstName pena-user)]
                         ["henkilo.henkilotiedot.sukunimi" (:lastName pena-user)]
                         ["henkilo.userId" (:id pena-user)]]) => ok?
      (give-verdict sonja (:id source-app)) => ok?
      (let [{digging-app-id :id} (command pena :create-digging-permit :id (:id source-app)
                                          :operation "ya-katulupa-vesi-ja-viemarityot")
            digging-app (query-application pena digging-app-id)
            source-app  (query-application pena (:id source-app))]

        (-> (domain/get-document-by-name digging-app "hakija-ya") :data)
        => (-> (domain/get-document-by-name source-app "hakija-ya") :data
               (assoc-in [:henkilo :userId :value] ""))

        (-> (domain/get-document-by-name digging-app "yleiset-alueet-maksaja") :data)
        => (-> (domain/get-document-by-name source-app "yleiset-alueet-maksaja") :data
               (assoc-in [:henkilo :userId :value] ""))))))
