(ns lupapalvelu.create-property-formation-app-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.factlet :refer [facts*]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.test-util :refer [walk-dissoc-keys]]
            [lupapalvelu.domain :as domain]))

(apply-remote-minimal)

(def ^:private mikko-user (find-user-from-minimal-by-apikey mikko))
(def ^:private teppo-user (find-user-from-minimal-by-apikey teppo))
(def ^:private pena-user (find-user-from-minimal-by-apikey pena))
(def ^:private sonja-user (find-user-from-minimal-by-apikey sonja))
(def ^:private solita-company (company-from-minimal-by-id "solita"))
(def ^:private solita-company-admin (find-user-from-minimal-by-apikey kaino))

(facts "create property formation-app based on tonttijako app"
  (let [{app-id :id :as source-app} (create-and-submit-application pena :operation "tonttijako" :propertyId sipoo-property-id)]
    (facts "invite candidates"
      (fact "fails if the given app does not exist"
        (query teppo :property-formation-app-invite-candidates :id "nonexistent")
        => (partial expected-failure? "error.application-not-accessible"))

      (fact "Property info"
        (query pena :property-info :id app-id)
        => {:ok true
            :propertyInfo {:address (:address source-app)
                           :municipality (:municipality source-app)
                           :propertyId (:propertyId source-app)}})

      (fact "query returns [] if nobody is invited to the app"
        (query pena :property-formation-app-invite-candidates :id app-id)
        => {:candidates [] :ok true})

      (fact "fails if a caller is not writer"
        (query teppo :property-formation-app-invite-candidates :id app-id)
        => {:ok false :text "error.application-not-accessible"})

      (let [_ (invite-company-and-accept-invitation pena app-id "solita" kaino)
            _ (command pena :invite-with-role :id app-id :email (email-for-key mikko)
                       :role "writer" :text "wilkommen" :documentName "" :documentId "" :path "") => ok?
            _ (command mikko :approve-invite :id app-id) => ok?
            _ (command pena :invite-with-role :id app-id :email (email-for-key teppo)
                     :role "writer" :text "huanying" :documentName "" :documentId "" :path "") => ok?
            hakija-doc-id (:id (domain/get-applicant-document (:documents source-app)))
            _ (command pena :update-doc :id app-id :doc hakija-doc-id
                       :updates [["henkilo.henkilotiedot.etunimi" (:firstName mikko-user)]
                                 ["henkilo.henkilotiedot.sukunimi" (:lastName mikko-user)]
                                 ["henkilo.userId" (:id mikko-user)]]) => ok?]
        (fact "For Sonja, invited candidates are Pena, Mikko, Teppo and Solita"
            (:candidates (query sonja :property-formation-app-invite-candidates :id app-id))
            => (just [(assoc (select-keys pena-user [:firstName :lastName :id]) :email nil :role "writer" :roleSource "auth")
                      (assoc (select-keys mikko-user [:firstName :lastName :id]) :email nil :role "hakija" :roleSource "document")
                      (assoc (select-keys teppo-user [:firstName :lastName :id]) :email "teppo@example.com" :role "writer" :roleSource "auth")
                      (assoc {:firstName (:name solita-company)
                              :lastName  ""
                              :id        (:id solita-company)} :email nil :role "writer" :roleSource "auth")]
                     :in-any-order))

        (fact "For Kaino, invited candidates are Pena, Mikko and Teppo, but not Solita (automatically invited)"
            (:candidates (query kaino :property-formation-app-invite-candidates :id app-id))
            => (just [(assoc (select-keys pena-user [:firstName :lastName :id]) :email nil :role "writer" :roleSource "auth")
                      (assoc (select-keys mikko-user [:firstName :lastName :id]) :email nil :role "hakija" :roleSource "document")
                      (assoc (select-keys teppo-user [:firstName :lastName :id]) :email "teppo@example.com" :role "writer" :roleSource "auth")]
                     :in-any-order))

        (fact "For Pena, invited candidates are Solita, Mikko and Teppo"
            (:candidates (query pena :property-formation-app-invite-candidates :id app-id))
            => (just [(assoc {:firstName (:name solita-company)
                              :lastName  ""
                              :id        (:id solita-company)} :email nil :role "writer" :roleSource "auth")
                      (assoc (select-keys mikko-user [:firstName :lastName :id]) :email nil :role "hakija" :roleSource "document")
                      (assoc (select-keys teppo-user [:firstName :lastName :id]) :email "teppo@example.com" :role "writer" :roleSource "auth")]
                     :in-any-order))))

    (facts "create property formation app."
      (fact "only tonttijako app can be source app"
        (let [{r-app-id :id} (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
              {asemaakava-app-id :id} (create-and-submit-application pena :operation "asemakaava" :propertyId sipoo-property-id)]
          (command pena :create-property-formation-app :id r-app-id :invites []) => (partial expected-failure? "error.invalid-permit-type")
          (command pena :create-property-formation-app :id asemaakava-app-id :invites []) => (partial expected-failure? "error.primary-operation-is-not-tonttijako")))
      (let [create-app-resp (command pena :create-property-formation-app :id app-id :invites [(:id solita-company) teppo-id mikko-id]) => ok?
            created-app     (query-application pena (:id create-app-resp))]
        (facts "primaryOperation and permitType are correct"
          (-> created-app :primaryOperation :name) => "kiinteistonmuodostus"
          (:permitType created-app) => "KT")
        (facts "Operation document"
          (let [op-docs (filter #(get-in % [:schema-info :op]) (:documents created-app))]
            (count op-docs) => 1
            (fact "have name"
              (get-in (first op-docs) [:schema-info :name]) => "kiinteistonmuodostus")
            (fact "has correct id"
              (get-in (first op-docs) [:schema-info :op :id]) => (get-in created-app [:primaryOperation :id]))))

        (fact "created app and source app have the same address, propertyId, and location"
          (select-keys created-app [:address :propertyId :location])
          => (select-keys source-app [:address :propertyId :location]))

        (fact "Pena is authorized to the application, Solita is invited as company , Mikko is invited as writer,
              Teppo's pending invitation has been transferred successfully"
          (let [created-app-auth (:auth created-app)]
            (count created-app-auth) => 4
            (-> created-app-auth (first) ((juxt :id :role))) => [(:id pena-user) "writer"]
            (-> created-app-auth (second) ((juxt :name :type))) => [(:name solita-company) "company"]
            (-> created-app-auth (get 2) ((juxt :firstName :id :role))) => ["Mikko" (:id mikko-user) "reader"]
            (-> created-app-auth (last) ((juxt :username :id))) => ["teppo@example.com" (:id teppo-user)]
            (-> created-app-auth (last) :invite) => (contains {:role "writer" :email "teppo@example.com"})))

        (facts "link between apps exists"
          (let [link-permits (:appsLinkingToUs (query-application pena app-id))]
            (count link-permits) => 1
            (:id (first link-permits)) => (:id created-app)))

        (facts "docs have correct data"
          (let [source-app (query-application sonja app-id)
                hakija-doc-from-new-app (domain/get-applicant-document (:documents created-app))
                hakija-doc-from-source-app (domain/get-applicant-document (:documents source-app))]
            (fact "correct docs"
              (map #(get-in % [:schema-info :name]) (:documents created-app))
              => ["kiinteistonmuodostus" "kiinteisto" "hakija-kt" "maksaja"])

            (fact "schema-name = hakija-kt"
              (-> hakija-doc-from-new-app :schema-info :name)
              => "hakija-kt")

            (fact "First name of the applicant is Mikko"
              (-> hakija-doc-from-new-app :data :henkilo :henkilotiedot :etunimi :value)
              => "Mikko")

            (fact "applicant data is copied from source app"
              (:data hakija-doc-from-new-app)
              => (update-in (:data hakija-doc-from-source-app) [:henkilo] dissoc :userId))))))))