(ns lupapalvelu.ya-digging-permit-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.application :as app]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.operations :as operations]
            [sade.env :as env]))

(apply-remote-minimal)

(def ^:private pena-user  (find-user-from-minimal-by-apikey pena))
(def ^:private mikko-user (find-user-from-minimal-by-apikey mikko))

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

  (facts "copied data from sijoitus application"

    ;; Setup
    (let [source-app (create-and-submit-application pena :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen")
          app-id (:id source-app)
          maksaja-doc-id (:id (domain/get-document-by-name (:documents source-app) "yleiset-alueet-maksaja"))
          hakija-doc-id (:id (domain/get-applicant-document (:documents source-app)))]

      ;; Invite Mikko
      (command pena :invite-with-role :id app-id :email (email-for-key mikko)
               :role "writer" :text "wilkommen" :documentName "" :documentId "" :path "") => ok?
      (command mikko :approve-invite :id app-id) => ok?

      ;; Add Pena's personal information to applicant document
      (command pena :update-doc :id app-id :doc hakija-doc-id :collection "documents"
               :updates [["_selected" "henkilo"]
                         ["henkilo.henkilotiedot.etunimi" (:firstName pena-user)]
                         ["henkilo.henkilotiedot.sukunimi" (:lastName pena-user)]
                         ["henkilo.userId" (:id pena-user)]])

      ;; Add Mikko's personal information to payer document
      (command pena :update-doc :id app-id :doc maksaja-doc-id :collection "documents"
               :updates [["_selected" "henkilo"]
                         ["henkilo.henkilotiedot.etunimi" (:firstName mikko-user)]
                         ["henkilo.henkilotiedot.sukunimi" (:lastName mikko-user)]
                         ["henkilo.userId" (:id mikko-user)]]) => ok?

      ;; Add drawings
      (command pena :save-application-drawings
               :id app-id
               :drawings [{:id 1,
                           :name "A",
                           :desc "A desc",
                           :category "123",
                           :geometry "POLYGON((438952 6666883.25,441420 6666039.25,441920 6667359.25,439508 6667543.25,438952 6666883.25))",
                           :area "2686992",
                           :height "1"}])

      (give-verdict sonja app-id) => ok?

      (let [{digging-app-id :id} (command pena :create-digging-permit :id (:id source-app)
                                          :operation "ya-katulupa-vesi-ja-viemarityot")
            digging-app (query-application pena digging-app-id)
            source-app  (query-application pena (:id source-app))]

        (fact "applicant and payer documents are copied"
          ;; Pena's id is present since he is the one creating the application
          (-> (domain/get-document-by-name digging-app "hakija-ya") :data)
          => (-> (domain/get-document-by-name source-app "hakija-ya") :data)

          (-> (domain/get-document-by-name digging-app "yleiset-alueet-maksaja") :data)
          => (-> (domain/get-document-by-name source-app "yleiset-alueet-maksaja") :data
                 (assoc-in [:henkilo :userId :value] "")))

        (fact "applicant and payer parties are invited"
          (count (:auth digging-app)) => 2
          (let [mikko-auth (-> digging-app :auth second)
                digging-maksaja-doc-id (:id (domain/get-document-by-name (:documents digging-app)
                                                                         "yleiset-alueet-maksaja"))]
            (-> mikko-auth :id) => (:id mikko-user)
            (-> mikko-auth :invite :documentId) => digging-maksaja-doc-id
            (-> mikko-auth :invite :documentName) => "yleiset-alueet-maksaja"))

        (fact "drawings are copied"
              (:drawings digging-app) => (:drawings source-app))))))

(fact "selected-digging-operations-for-organization"
      (-> (query pena :selected-digging-operations-for-organization :organization"753-YA")
          :operations)
      => [["yleisten-alueiden-luvat"
           [["katulupa"
             [["kaivaminen-yleisilla-alueilla"
               [["vesi-ja-viemarityot" "ya-katulupa-vesi-ja-viemarityot"]]]]]]]])
