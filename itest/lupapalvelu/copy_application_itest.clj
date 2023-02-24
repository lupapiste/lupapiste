(ns lupapalvelu.copy-application-itest
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer [facts*]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.test-util :refer [walk-dissoc-keys]]
            [midje.sweet :refer :all]
            [sade.property :as sprop]))

(apply-remote-minimal)

(defn- copy-application [apikey app-id & {:keys [x y address auth-invites propertyId]}]
  (command apikey :copy-application
           :x (or x 444445.0) :y (or y 6666665.0)
           :address (or address "Testitie 1")
           :auth-invites (or auth-invites [])
           :propertyId (or propertyId "75312312341234")
           :source-application-id app-id))

(defn restore-sipoo-selected-operations []
  (command sipoo "set-organization-selected-operations" :organizationId "753-R" :operations
           (map first (filter (fn [[_ v]]
                                (#{"R" "P" "YI" "YL" "YM" "MAL" "VVVL" "KT" "MM"} (name (:permit-type v))))
                              operations/operations))))

(def ^:private mikko-user (find-user-from-minimal-by-apikey mikko))
(def ^:private teppo-user (find-user-from-minimal-by-apikey teppo))
(def ^:private pena-user (find-user-from-minimal-by-apikey pena))
(def ^:private sonja-user (find-user-from-minimal-by-apikey sonja))
(def ^:private solita-company (company-from-minimal-by-id "solita"))
(def ^:private solita-company-admin (find-user-from-minimal-by-apikey kaino))

(facts "invite candidates"
  (fact "fails if the given application does not exist"
    (query sonja :copy-application-invite-candidates :source-application-id "nonexistent")
    => (partial expected-failure? "error.application-not-found"))

  (let [{app-id :id :as app} (create-and-submit-application pena)
        _ (invite-company-and-accept-invitation pena app-id "solita" kaino)
        _ (command pena :invite-with-role :id app-id :email (email-for-key mikko)
                   :role "writer" :text "wilkommen" :documentName "" :documentId "" :path "") => ok?
        _ (command mikko :approve-invite :id app-id) => ok?
        _ (command pena :invite-with-role :id app-id :email (email-for-key teppo)
                   :role "writer" :text "huanying" :documentName "" :documentId "" :path "") => ok?
        hakija-doc-id (:id (domain/get-applicant-document (:documents app)))
        _ (command pena :update-doc :id app-id :doc hakija-doc-id
                   :updates [["henkilo.henkilotiedot.etunimi" (:firstName mikko-user)]
                             ["henkilo.henkilotiedot.sukunimi" (:lastName mikko-user)]
                             ["henkilo.userId" (:id mikko-user)]]) => ok?]

    (fact "fails if caller is not authority or company user"
      (query pena :copy-application-invite-candidates :source-application-id app-id)
      => (partial expected-failure? "error.unauthorized")
      (query kaino :copy-application-invite-candidates :source-application-id app-id) => ok?
      (query sonja :copy-application-invite-candidates :source-application-id app-id) => ok?)

    (fact "For Sonja, invited candidates are Pena, Mikko, Teppo and Solita"
      (:candidates (query sonja :copy-application-invite-candidates :source-application-id app-id))
      => (just [(assoc (select-keys pena-user [:firstName :lastName :id]) :email nil :role "writer" :roleSource "auth")
                (assoc (select-keys mikko-user [:firstName :lastName :id]) :email nil :role "hakija" :roleSource "document")
                (assoc (select-keys teppo-user [:firstName :lastName :id]) :email "teppo@example.com" :role "writer" :roleSource "auth")
                (assoc {:firstName (:name solita-company)
                        :lastName  ""
                        :id        (:id solita-company)} :email nil :role "writer" :roleSource "auth")]
               :in-any-order))

    (fact "For Kaino, invited candidates are Pena, Mikko and Teppo, but not Solita (automatically invited)"
      (:candidates (query kaino :copy-application-invite-candidates :source-application-id app-id))
      => (just [(assoc (select-keys pena-user [:firstName :lastName :id]) :email nil :role "writer" :roleSource "auth")
                (assoc (select-keys mikko-user [:firstName :lastName :id]) :email nil :role "hakija" :roleSource "document")
                (assoc (select-keys teppo-user [:firstName :lastName :id]) :email "teppo@example.com" :role "writer" :roleSource "auth")]
               :in-any-order))))

(facts* "copying application"

  (fact "fails if caller is not authority or company user"
    (let [{app-id :id} (create-and-submit-application pena)]
      (copy-application pena app-id) => (partial expected-failure? "error.unauthorized")
      (copy-application kaino app-id) => (partial expected-failure? "error.application-not-found")
      (invite-company-and-accept-invitation pena app-id "solita" kaino)
      (copy-application kaino app-id) => ok?))

  (fact "fails if organization does not support given operation"
    (let [{app-id :id} (create-and-submit-application pena)] ; kerrostalo-rivitalo
      (command sipoo "set-organization-selected-operations" :organizationId "753-R"
               :operations ["pientalo" "aita"]) => ok?
      (copy-application sonja app-id) => (partial expected-failure? "error.operations.hidden")
      (restore-sipoo-selected-operations)))

  (let [{app-id :id} (create-and-submit-application pena)
        _ (invite-company-and-accept-invitation pena app-id "solita" kaino)
        _ (command pena :invite-with-role :id app-id :email (email-for-key teppo)
                   :role "writer" :text "huanying" :documentName "" :documentId "" :path "")
        app (query-application sonja app-id)
        x 444445.0, y 6666665.0
        property-id "75312312341234"
        _ (sent-emails)                                   ; reset sent emails
        copy-app-response (copy-application sonja app-id
                                            :x x :y y
                                            :address "Testitie 1"
                                            :auth-invites [pena-id (:id solita-company) teppo-id]
                                            :propertyId property-id) => ok?
        copy-app (query-application sonja (:id copy-app-response))]

    (fact "primaryOperation is copied, but id is new"
      (dissoc (:primaryOperation copy-app) :id)
      => (dissoc (:primaryOperation app) :id)
      (:id (:primaryOperation copy-app))
      =not=> (:id (:primaryOperation app)))

    (facts "Operation documents"
      (let [op-docs (filter #(get-in % [:schema-info :op]) (:documents copy-app))]
        (count op-docs) => 1
        (fact "have name"
          (get-in (first op-docs) [:schema-info :name]) => "uusiRakennus")
        (fact "has correct id"
          (get-in (first op-docs) [:schema-info :op :id]) => (get-in copy-app [:primaryOperation :id]))))

    (fact "documents are copied, apart from ids"
      (let [copied-docs (walk-dissoc-keys (:documents copy-app) :id :created :allowedActions :modified)
            docs (walk-dissoc-keys (:documents app) :id :created :allowedActions :modified)]
        (fact "Document count matches"
          (count copied-docs) => (count docs))
        (doseq [i (range (count docs))
                :let [doc (nth docs i)
                      copy (nth copied-docs i)]]
          (fact {:midje/description (str "Document: " (-> doc :schema-info :name))}
            doc => copy))))

    (fact "the copied app has the provided location and property id"
      (:location copy-app) => {:x x :y y}
      (:propertyId copy-app) => property-id
      (:municipality copy-app) => (sprop/municipality-id-by-property-id property-id))

    (fact "Sonja is authorized to application, Pena is invited as writer, Teppo's pending invitation has been transferred successfully"
      (count (:auth copy-app)) => 4
      (-> copy-app :auth (first) ((juxt :id :role))) => [(:id sonja-user) "writer"]
      (-> copy-app :auth (second) ((juxt :id (comp :role :invite)))) => [pena-id "writer"]
      (-> copy-app :auth (get 2) ((juxt :name :type))) => [(:name solita-company) "company"]
      (let [teppo-auth (-> copy-app :auth last)]
        teppo-auth => (contains {:role "reader" :username "teppo@example.com"})
        (:invite teppo-auth) => (contains {:role "writer" :email "teppo@example.com"}))
      (let [emails (sent-emails)]
        (count emails) => 3
        (doall (mapv (fn [{:keys [to body]}]
                       (let [[t b] (cond
                                     (re-find #"Pena" to) ["Pena Panaani"
                                                           "Sinut halutaan valtuuttaa kirjoitusoikeudella"]
                                     (re-find #"kaino" to) [(:email solita-company-admin)
                                                            "Sonja Sibbo haluaa valtuuttaa yrityksenne"]
                                     (re-find #"Teppo" to) ["Teppo Nieminen"
                                                            "Sinut halutaan valtuuttaa kirjoitusoikeudella"])]
                         (fact {:midje/description (str "Email for " t)}
                           to => (contains t)
                           (:html body) => (contains b))))
                     emails))))

    (fact "the source application is stored in the source-applications collection"
      (let [source-app (:source-application (query sonja :source-application :copy-application-id (:id copy-app)))]
        (:id source-app) => (:id app)))

    (fact "Only auths with ids in auth-invites are copied from old app"
      (let [copy-app-response (copy-application sonja app-id
                                                :auth-invites []) => ok?
            copy-app (query-application sonja (:id copy-app-response))]
        (-> copy-app :auth (first) ((juxt :id :role))) => [(:id sonja-user) "writer"]
        (-> copy-app :auth count) => 1))
    (fact "Copying fails if some of provided auth-invites don't exist in source application"
      (copy-application sonja app-id :auth-invites ["nonexistent" pena-id])
      => (partial expected-failure? "error.nonexistent-auths"))))

(facts "checking if app is copyable to location"

  (fact "fails if organization does not support given operation"
    (let [{app-id :id} (create-and-submit-application pena)] ; kerrostalo-rivitalo
      (command sipoo "set-organization-selected-operations" :organizationId "753-R"
               :operations ["pientalo" "aita"]) => ok?
      (query sonja :application-copyable-to-location :source-application-id app-id
             :x 444445.0 :y 6666665.0 :address "Testitie 1" :propertyId "75312312341234")
      => (partial expected-failure? "error.operations.hidden")
      (restore-sipoo-selected-operations)))

  (fact "succeeds if organization supports given operation"
    (let [{app-id :id} (create-and-submit-application pena)] ; kerrostalo-rivitalo
      (query sonja :application-copyable-to-location :source-application-id app-id
             :x 444445.0 :y 6666665.0 :address "Testitie 1" :propertyId "75312312341234")
      => ok?)))
