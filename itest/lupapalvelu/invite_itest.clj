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
        suunnittelija-doc (:id (domain/get-document-by-name app "suunnittelija")) => truthy
        paasuunnittelija-doc (:id (domain/get-document-by-name app "paasuunnittelija")) => truthy]

    (fact "Teppo must not be able to invite himself!"
      (invite teppo id suunnittelija-doc "teppo@example.com") => unauthorized?)

    (fact "Empty email is rejected"
      (:text (invite mikko id suunnittelija-doc "")) => "error.missing-parameters")

    (fact "Mikko must be able to invite Teppo!"
      (last-email) ; Inbox zero

      (invite mikko id suunnittelija-doc "teppo@example.com") => ok?

      (count (:invites (query teppo :invites))) => 1

      (let [email (last-email)]
        email => (partial contains-application-link? id)
        (:to email) => "teppo@example.com"
        (:subject email) => "Lupapiste.fi: Kutsukatu 13 - kutsu"))

    (fact "Sonja must NOT be able to uninvite Teppo!"
      (command sonja :remove-invite :id id :email "teppo@example.com") => unauthorized?
      (count (:invites (query teppo :invites))) => 1)

    (fact "Mikko must be able to uninvite Teppo!"
      (command mikko :remove-invite :id id :email "teppo@example.com") => ok?
      (count (:invites (query teppo :invites))) => 0)

    (fact "Mikko must be able to re-invite Teppo!"
      (invite mikko id suunnittelija-doc "teppo@example.com") => ok?
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
      (command sonja :remove-auth :id id :email "teppo@example.com") => ok?)

    (fact "Pena is inveted to a deleted doc"
      (invite mikko id paasuunnittelija-doc (email-for "pena")) => ok?
      (command mikko :remove-doc :id id :docId paasuunnittelija-doc) => ok?
      (count (:invites (query pena :invites))) => 1

      (fact "Pena can still approve invite"
        (command pena :approve-invite :id id) => ok?)))
  )

(facts* "Auhtority invites designer"
  (doseq [user-key [sonja mikko teppo]]
    (let [resp (query user-key :invites) => ok?]
      (count (:invites resp)) => 0))

  (let [resp  (create-app sonja :municipality sonja-muni) => ok?
        id    (:id resp) => truthy
        app    (query-application sonja id)
        suunnittelija-doc (:id (domain/get-document-by-name app "suunnittelija"))]

    (fact "Sonja must be able to invite Teppo!"
      (invite sonja id suunnittelija-doc "teppo@example.com") => ok?
      (count (:invites (query teppo :invites))) => 1)

    (fact "Sonja must be able to uninvite Teppo!"
      (command sonja :remove-invite :id id :email "teppo@example.com") => ok?
      (count (:invites (query teppo :invites))) => 0)

    (fact "Reinvite & accept"
      (invite sonja id suunnittelija-doc "teppo@example.com") => ok?
      (count (:invites (query teppo :invites))) => 1
      (command teppo :approve-invite :id id) => ok?)

    (fact "Teppo must be able to comment!"
      (command teppo :add-comment :id id :text "teppo@example.com" :target "application") => ok?)

    (fact "Teppo must be able to invite another designer, Mikko!"
      (invite teppo id suunnittelija-doc "mikko@example.com") => ok?)

    (fact "Sonja must be able to remove authz from Teppo!"
      (command sonja :remove-auth :id id :email "teppo@example.com") => ok?)))
