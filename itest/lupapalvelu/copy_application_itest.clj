(ns lupapalvelu.copy-application-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.test-util :refer [walk-dissoc-keys]]
            [lupapalvelu.application :as app]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.operations :as operations]
            [sade.property :as prop]))

(apply-remote-minimal)

(defn- copy-application [apikey app-id & {:keys [x y address auth-invites propertyId]}]
  (command apikey :copy-application
           :x (or x 444445.0) :y (or y 6666665.0)
           :address (or address "Testitie 1")
           :auth-invites (or auth-invites [])
           :propertyId (or propertyId "75312312341234")
           :source-application-id app-id))

(defn restore-sipoo-selected-operations []
  (command sipoo "set-organization-selected-operations" :operations
           (map first (filter (fn [[_ v]]
                                (#{"R" "P" "YI" "YL" "YM" "MAL" "VVVL" "KT" "MM"} (name (:permit-type v))))
                              operations/operations))))

(def ^:private mikko-user (find-user-from-minimal-by-apikey mikko))
(def ^:private pena-user (find-user-from-minimal-by-apikey pena))
(def ^:private sonja-user (find-user-from-minimal-by-apikey sonja))
(def ^:private solita-company (company-from-minimal-by-id "solita"))
(def ^:private solita-company-admin (find-user-from-minimal-by-apikey kaino))

(facts "invite candidates"
  (fact "fails if the given application does not exist"
    (query sonja :copy-application-invite-candidates :source-application-id "nonexistent")
    => (partial expected-failure? "error.no-source-application"))

  (let [{app-id :id :as app}  (create-and-submit-application pena)
        _                     (invite-company-and-accept-invitation pena app-id "solita")
        _                     (command pena :invite-with-role :id app-id :email (email-for-key mikko)
                                       :role "writer" :text "wilkommen" :documentName "" :documentId "" :path "") => ok?
        _                     (command mikko :approve-invite :id app-id) => ok?
        hakija-doc-id         (:id (domain/get-applicant-document (:documents app)))
        resp                  (command pena :update-doc :id app-id :doc hakija-doc-id  :collection "documents"
                                       :updates [["henkilo.henkilotiedot.etunimi" (:firstName mikko-user)]
                                                 ["henkilo.henkilotiedot.sukunimi" (:lastName mikko-user)]
                                                 ["henkilo.userId" (:id mikko-user)]]) => ok?]

    (fact "fails if caller is not authority or company user"
      (query pena :copy-application-invite-candidates :source-application-id app-id)
      => (partial expected-failure? "error.unauthorized")
      (query kaino :copy-application-invite-candidates :source-application-id app-id) => ok?
      (query sonja :copy-application-invite-candidates :source-application-id app-id) => ok?)

    (fact "Pena, Mikko and Solita are candidates"
      (:candidates (query sonja :copy-application-invite-candidates :source-application-id app-id))
      => (just [(assoc (select-keys pena-user [:firstName :lastName :id])  :email nil :role "owner")
                (assoc (select-keys mikko-user [:firstName :lastName :id]) :email nil :role "hakija") ; role from hakija document
                (assoc {:firstName (:name solita-company)
                        :lastName ""
                        :id (:_id solita-company)} :email nil :role "writer")]
               :in-any-order))))

(facts "copying application"

  (fact "fails if caller is not authority or company user"
    (let [{app-id :id} (create-and-submit-application pena)]
      (copy-application pena app-id) => (partial expected-failure? "error.unauthorized")
      (copy-application kaino app-id) => (partial expected-failure? "error.no-source-application")
      (invite-company-and-accept-invitation pena app-id "solita")
      (copy-application kaino app-id) => ok?))

  (fact "fails if organization does not support given operation"
    (let [{app-id :id} (create-and-submit-application pena)] ; kerrostalo-rivitalo
      (command sipoo "set-organization-selected-operations" :operations ["pientalo" "aita"]) => ok?
      (copy-application sonja app-id) => (partial expected-failure? "error.operation-not-supported-by-organization")
      (restore-sipoo-selected-operations)))

  (let [{app-id :id} (create-and-submit-application pena)
        _ (invite-company-and-accept-invitation pena app-id "solita")
        app (query-application sonja app-id)
        x 444445.0 y 6666665.0
        property-id "75312312341234"
        _ (sent-emails) ; reset sent emails
        {copy-app-id :id} (copy-application sonja app-id
                                   :x x :y y
                                   :address "Testitie 1"
                                   :auth-invites [pena-id (:_id solita-company)]
                                   :propertyId property-id) => ok?
        copy-app (query-application sonja copy-app-id)]

    (fact "primaryOperation is copied, but id is new"
      (dissoc (:primaryOperation copy-app) :id)
      => (dissoc (:primaryOperation app) :id)
      (:id (:primaryOperation copy-app))
      =not=> (:id (:primaryOperation app)))

    (fact "documents are copied, apart from ids"
      (walk-dissoc-keys (:documents copy-app) :id :created :allowedActions)
      => (just (walk-dissoc-keys (:documents app) :id :created  :allowedActions) :in-any-order))

    (fact "the copied app has the provided location and property id"
      (:location copy-app) => {:x x :y y}
      (:propertyId copy-app) => property-id
      (:municipality copy-app) => (prop/municipality-id-by-property-id property-id))

    (fact "Sonja is new owner, Pena (previous owner) is invited as writer"
      (count (:auth copy-app)) => 3
      (-> copy-app :auth (first) ((juxt :id :role))) => [(:id sonja-user) "owner"]
      (-> copy-app :auth (second) ((juxt :id (comp :role :invite)))) => [pena-id "writer"]
      (-> copy-app :auth (get 2) ((juxt :name :type))) => [(:name solita-company) "company"]
      (let [emails (sent-emails)]
        (count emails) => 2

        (-> emails first :body :html) => (contains "Sinut halutaan valtuuttaa kirjoitusoikeudella")
        (-> emails first :to) => (contains "Pena Panaani")

        (-> emails second :body :html) => (contains "Sonja Sibbo haluaa valtuuttaa yrityksenne")
        (-> emails second :to) => (contains (:email solita-company-admin))))

    (fact "Only auths with ids in auth-invites are copied from old app"
          (let [{copy-app-id :id} (copy-application sonja app-id
                                           :auth-invites []) => ok?
                copy-app (query-application sonja copy-app-id)]
            (-> copy-app :auth (first) ((juxt :id :role))) => [(:id sonja-user) "owner"]
            (-> copy-app :auth count) => 1))
    (fact "Copying fails if some of provided auth-invites don't exist in source application"
          (copy-application sonja app-id :auth-invites ["nonexistent" pena-id])
          => (partial expected-failure? "error.nonexistent-auths"))))

(facts "checking if app is copyable to location"

  (fact "fails if organization does not support given operation"
    (let [{app-id :id} (create-and-submit-application pena)] ; kerrostalo-rivitalo
      (command sipoo "set-organization-selected-operations" :operations ["pientalo" "aita"]) => ok?
      (query sonja :application-copyable-to-location :source-application-id app-id
             :x 444445.0 :y 6666665.0 :address "Testitie 1" :propertyId "75312312341234")
      => (partial expected-failure? "error.operation-not-supported-by-organization")
      (restore-sipoo-selected-operations)))

  (fact "succeeds if organization supports given operation"
    (let [{app-id :id} (create-and-submit-application pena)] ; kerrostalo-rivitalo
      (query sonja :application-copyable-to-location :source-application-id app-id
             :x 444445.0 :y 6666665.0 :address "Testitie 1" :propertyId "75312312341234")
      => ok?)))
