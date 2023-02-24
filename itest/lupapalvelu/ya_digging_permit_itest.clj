(ns lupapalvelu.ya-digging-permit-itest
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.date :as date]))

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
      (give-legacy-verdict sonja (:id source-app))
      (command pena :create-digging-permit :id (:id source-app)
               :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen")
      => (partial expected-failure? "error.not-digging-permit-operation")))

  (fact "fails if operation is not selected for source permit's organization"
    (let [source-app (create-and-submit-application pena :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen")]
      (give-legacy-verdict sonja (:id source-app))
      (command pena :create-digging-permit :id (:id source-app)
               :operation "ya-katulupa-maalampotyot") ; not selected for Sipoo YA in minimal fixture
      => (partial expected-failure? "error.operations.hidden")))

  (facts "copied data from sijoitus application"

    ;; Setup
    (let [source-app     (create-and-submit-application pena :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen")
          app-id         (:id source-app)
          maksaja-doc-id (:id (domain/get-document-by-name (:documents source-app) "yleiset-alueet-maksaja"))
          hakija-doc-id  (:id (domain/get-applicant-document (:documents source-app)))]

      ;; Invite Mikko
      (command pena :invite-with-role :id app-id :email (email-for-key mikko)
               :role "writer" :text "wilkommen" :documentName "" :documentId "" :path "") => ok?
      (command mikko :approve-invite :id app-id) => ok?

      ;; Add Pena's personal information to applicant document
      (command pena :update-doc :id app-id :doc hakija-doc-id
               :updates [["_selected" "henkilo"]
                         ["henkilo.henkilotiedot.etunimi" (:firstName pena-user)]
                         ["henkilo.henkilotiedot.sukunimi" (:lastName pena-user)]
                         ["henkilo.userId" (:id pena-user)]])

      ;; Add Mikko's personal information to payer document
      (command pena :update-doc :id app-id :doc maksaja-doc-id
               :updates [["_selected" "henkilo"]
                         ["henkilo.henkilotiedot.etunimi" (:firstName mikko-user)]
                         ["henkilo.henkilotiedot.sukunimi" (:lastName mikko-user)]
                         ["henkilo.userId" (:id mikko-user)]]) => ok?

      ;; Add drawings
      (command pena :save-application-drawings
               :id app-id
               :drawings [{:id       1,
                           :name     "A",
                           :desc     "A desc",
                           :category "123",
                           :geometry "POLYGON((438952 6666883.25,441420 6666039.25,441920 6667359.25,439508 6667543.25,438952 6666883.25))",
                           :area     "2686992",
                           :height   "1"}])

      (give-legacy-verdict sonja app-id)

      (let [{digging-app-id :id} (command pena :create-digging-permit :id (:id source-app)
                                          :operation "ya-katulupa-vesi-ja-viemarityot")
            digging-app          (query-application pena digging-app-id)
            source-app           (query-application pena (:id source-app))]

        (fact "applicant and payer documents are copied"
          ;; Pena's id is present since he is the one creating the application
          (-> (domain/get-document-by-name digging-app "hakija-ya") :data)
          => (-> (domain/get-document-by-name source-app "hakija-ya") :data)

          (-> (domain/get-document-by-name digging-app "yleiset-alueet-maksaja") :data)
          => (-> (domain/get-document-by-name source-app "yleiset-alueet-maksaja") :data
                 (assoc-in [:henkilo :userId :value] "")))

        (fact "applicant and payer parties are invited"
          (count (:auth digging-app)) => 2
          (let [mikko-auth             (-> digging-app :auth second)
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

(facts "Warranty"
  (let [start-ts           (date/timestamp (date/today))
        end-ts             (date/timestamp "31.12.2100")
        today-str          (date/finnish-date (date/today))
        {app-id :id
         :as    app}       (create-and-submit-application pena :operation "ya-kayttolupa-nostotyot")
        kuvaus-doc-id      (:id (domain/get-document-by-subtype app "hankkeen-kuvaus"))
        tyoaika-doc-id     (:id (domain/get-document-by-name app "tyoaika"))
        warranty-period    (fn []
                             ((juxt :warrantyStart :warrantyEnd)
                              (query-application pena app-id)))
        set-warranty       (fn [apikey start-ts end-ts check]
                             (facts "Set warranty period"
                               (command apikey :change-warranty-start-date :id app-id
                                        :startDate start-ts)
                               => check
                               (command apikey :change-warranty-end-date :id app-id
                                        :endDate end-ts)
                               => check))
        construction-state (fn []
                             (command sonja :change-application-state :id app-id
                                      :state "constructionStarted") => ok?)]
    (facts "Fill application"
      (command pena :update-doc :id app-id
               :collection "documents"
               :doc kuvaus-doc-id
               :updates [["kayttotarkoitus" "Lorem ipsum"]
                         ["varattava-pinta-ala" "120"]]) => ok?
      (command pena :update-doc :id app-id
               :collection "documents"
               :doc tyoaika-doc-id
               :updates [["tyoaika-alkaa-ms" (date/timestamp "1.1.2099")]
                         ["tyoaika-paattyy-ms" (date/timestamp "1.2.2099")]]) => ok?
      (doseq [{doc-id :id} (domain/get-documents-by-type app "party")]
        (command pena :update-doc :id app-id
                 :collection "documents"
                 :doc doc-id
                 :updates [["_selected" "henkilo"]]) => ok?
        (command pena :set-user-to-document :id app-id
                 :collection "documents"
                 :documentId doc-id
                 :path "henkilo"
                 :userId pena-id) => ok?))


    (facts "Warranty period cannot be yet set"
      (set-warranty sonja start-ts end-ts fail?))

    (give-legacy-verdict sonja app-id)

    (facts "Applicant cannot set arranty period"
      (set-warranty pena start-ts end-ts unauthorized?))

    (facts "Warranty period can now be set"
      (set-warranty sonja start-ts end-ts ok?)

      (warranty-period) => [start-ts end-ts])

    (facts "Nil values are allowed"
      (set-warranty sonja nil nil ok?)
      (warranty-period) => [nil nil])

    (facts "Non-positive and bad values are not"
      (doseq [bad [0 -1 "bad date" "12.12.2222"]]
        (set-warranty sonja bad bad fail?)))

    (facts "Default warranty period is not generated with backing system"
      (command sonja :change-application-state :id app-id
               :state "closed") => ok?
      (warranty-period) => [nil nil]
      (construction-state)
      (command sonja :inform-construction-ready :id app-id
               :lang "fi" :readyTimestampStr today-str)
      => ok?
      (warranty-period) => [nil nil])

    (fact "Disable backing system"
      (command sipoo-ya :set-krysp-endpoint
               :organizationId "753-YA"
               :permitType "YA"
               :password ""
               :username ""
               :url ""
               :version "") => ok?)

    (facts "Default warranty period is generated"
      (construction-state)
      (command sonja :change-application-state :id app-id
               :state "closed") => ok?
      (warranty-period) => (just truthy truthy)
      (set-warranty sonja nil nil ok?)
      (warranty-period) => [nil nil]
      (construction-state)
      (command sonja :inform-construction-ready :id app-id
               :lang "fi" :readyTimestampStr today-str)
      => ok?
      (warranty-period) => (just truthy truthy))

    (facts "Default warranty period is not generated if the period has values"
      (doseq [[a b] [[start-ts end-ts] [start-ts nil] [nil end-ts]]]
        (construction-state)
        (set-warranty sonja a b ok?)
        (command sonja :change-application-state :id app-id
                 :state "closed") => ok?
        (warranty-period) => [a b]
        (construction-state)
        (command sonja :inform-construction-ready :id app-id
               :lang "fi" :readyTimestampStr today-str)
        => ok?
        (warranty-period) => [a b]))))
