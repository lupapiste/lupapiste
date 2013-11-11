(ns lupapalvelu.invite-itest
  (:use [lupapalvelu.itest-util]
        [lupapalvelu.factlet]
        [midje.sweet])
  (:require [lupapalvelu.domain :as domain]))

(defn- invite [apikey application-id document-id email]
  (command apikey :invite
    :id application-id
    :email email
    :title email
    :text email
    :documentName "suunnittelija"
    :documentId document-id
    :path ""))

(apply-remote-minimal)

(facts* "Applicant invites designer"

  (doseq [user-key [mikko teppo veikko sonja sipoo]]
    (let [resp (query user-key :invites) => ok?]
      (count (:invites resp)) => 0))

  (let [resp   (create-app mikko :municipality sonja-muni :address "Kutsukatu 13") => ok?
        id     (:id resp) => truthy
        app    (query-application mikko id)
        doc-id (:id (domain/get-document-by-name app "suunnittelija"))]

      (fact "Teppo must not be able to invite himself!"
        (invite teppo id doc-id "teppo@example.com") => unauthorized?)

      (fact "Empty email is rejected"
        (:text (invite mikko id doc-id "")) => "error.missing-parameters")

      (fact "Mikko must be able to invite Teppo!"
        (last-email) ; Inbox zero

        (invite mikko id doc-id "teppo@example.com") => ok?

        (count (:invites (query teppo :invites))) => 1

        (let [email (last-email)
              plain (get-in email [:body :plain])
              [href a-id a-id-again]   (re-find #"(?sm)http.+/app/fi/applicant\?hashbang=!/application/([A-Za-z0-9-]+)#!/application/([A-Za-z0-9-]+)" plain)]
          email => has-html-and-plain?
          (:to email) => "teppo@example.com"
          (:subject email) => "Lupapiste.fi: Kutsukatu 13 - kutsu"
          a-id => id
          a-id-again => id))

      (fact "Sonja must NOT be able to uninvite Teppo!"
        (command sonja :remove-invite :id id :email "teppo@example.com") => unauthorized?
        (count (:invites (query teppo :invites))) => 1)

      (fact "Mikko must be able to uninvite Teppo!"
        (command mikko :remove-invite :id id :email "teppo@example.com") => ok?
        (count (:invites (query teppo :invites))) => 0)

      (fact "Mikko must be able to re-invite Teppo!"
        (invite mikko id doc-id "teppo@example.com") => ok?
        (count (:invites (query teppo :invites))) => 1)

      (fact "Mikko must NOT be able to accept Teppo's invite"
        (command mikko :approve-invite :id id :email "teppo@example.com")
        (count (:invites (query teppo :invites))) => 1)

      (fact "Teppo must be able to accept Teppo's invite"
        (command teppo :approve-invite :id id) => ok?
        (count (:invites (query teppo :invites))) => 0)

      (fact "Teppo must be able to comment!"
        (command teppo :add-comment :id id :text "teppo@example.com" :target "application") => ok?)

      (let [actions (:actions (query teppo :allowed-actions :id id))]
        (fact "Teppo should be able to do stuff."
          (-> actions :add-operation :ok) => true
          (-> actions :submit-application :ok) => true
          (-> actions :cancel-application :ok) => true))

      (fact "Sonja must be able to remove authz from Teppo!"
        (command sonja :remove-auth :id id :email "teppo@example.com") => ok?)))

(facts* "Auhtority invites designer"
  (doseq [user-key [sonja mikko teppo]]
    (let [resp (query user-key :invites) => ok?]
      (count (:invites resp)) => 0))

  (let [resp  (create-app sonja :municipality sonja-muni) => ok?
        id    (:id resp) => truthy
        app    (query-application sonja id)
        doc-id (:id (domain/get-document-by-name app "suunnittelija"))]

    (fact "Sonja must be able to invite Teppo!"
      (invite sonja id doc-id "teppo@example.com") => ok?
      (count (:invites (query teppo :invites))) => 1)

    (fact "Sonja must be able to uninvite Teppo!"
      (command sonja :remove-invite :id id :email "teppo@example.com") => ok?
      (count (:invites (query teppo :invites))) => 0)

    (fact "Reinvite & accept"
      (invite sonja id doc-id "teppo@example.com") => ok?
      (count (:invites (query teppo :invites))) => 1
      (command teppo :approve-invite :id id) => ok?)

    (fact "Teppo must be able to comment!"
      (command teppo :add-comment :id id :text "teppo@example.com" :target "application") => ok?)

    (fact "Teppo must be able to invite another designer, Mikko!"
      (invite teppo id doc-id "mikko@example.com") => ok?)

    (fact "Sonja must be able to remove authz from Teppo!"
      (command sonja :remove-auth :id id :email "teppo@example.com") => ok?)))
