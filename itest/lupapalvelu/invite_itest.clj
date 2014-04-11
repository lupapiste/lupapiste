(ns lupapalvelu.invite-itest
  (:require [midje.sweet  :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]))

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

    (facts "email is validated"
      (fact "Empty email is rejected"
        (:text (invite mikko id suunnittelija-doc "")) => "error.missing-parameters")
      (fact "Email contains whitespace"
        (:text (invite mikko id suunnittelija-doc "juha jokimaki@solita.fi")) => "error.email")
      (fact "Email contains non-ascii chars"
        (:text (invite mikko id suunnittelija-doc "juha.jokim\u00e4ki@solita.fi")) => "error.email"))

    (fact "Teppo must not be able to invite himself!"
      (invite teppo id suunnittelija-doc (email-for-key teppo)) => unauthorized?)


    (fact "Mikko must be able to invite Teppo!"
      (last-email) ; Inbox zero

      (invite mikko id suunnittelija-doc (email-for-key teppo)) => ok?

      (count (:invites (query teppo :invites))) => 1

      (let [email (last-email)]
        email => (partial contains-application-link? id)
        (:to email) => (email-for-key teppo)
        (:subject email) => "Lupapiste.fi: Kutsukatu 13 - kutsu"))

    (fact "Sonja must NOT be able to uninvite Teppo!"
      (command sonja :remove-auth :id id :email (email-for-key teppo)) => unauthorized?
      (count (:invites (query teppo :invites))) => 1)

    (fact "Mikko must be able to uninvite Teppo!"
      (command mikko :remove-auth :id id :email (email-for-key teppo)) => ok?
      (count (:invites (query teppo :invites))) => 0)

    (fact "Mikko must be able to re-invite Teppo!"
      (invite mikko id suunnittelija-doc (email-for-key teppo)) => ok?
      (count (:invites (query teppo :invites))) => 1)

    (fact "Teppo must be able to decline invitation!"
      (command teppo :decline-invitation :id id) => ok?
      (count (:invites (query teppo :invites))) => 0)

    (fact "Mikko must NOT be able to accept Teppo's invite"
      (invite mikko id suunnittelija-doc (email-for-key teppo)) => ok?
      (command mikko :approve-invite :id id :email (email-for-key teppo))
      (count (:invites (query teppo :invites))) => 1)

    (fact "Mikko submits"
      (command mikko :submit-application :id id) => ok?)

    (last-email) ; Inbox zero

    (fact "Sonja adds comment, only Mikko gets email"
      (comment-application id sonja false)
      (let [emails (sent-emails)]
        (count emails) => 1
        (:to (first emails)) => (email-for-key mikko)))

    (fact "Teppo must be able to accept Teppo's invite"
      (command teppo :approve-invite :id id) => ok?
      (count (:invites (query teppo :invites))) => 0)

    (fact "Teppo must be able to comment!"
      (command teppo :add-comment :id id :text (email-for-key teppo) :target {:type "application"} :openApplication true) => ok?)

    (let [actions (:actions (query teppo :allowed-actions :id id))]
      (fact "Teppo should be able to do stuff."
        (-> actions :add-operation :ok) => true
        (-> actions :submit-application :ok) => true
        (-> actions :cancel-application :ok) => true))

    (fact "Sonja must be able to remove authz from Teppo!"
      (command sonja :remove-auth :id id :email (email-for-key teppo)) => ok?)

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
      (invite sonja id suunnittelija-doc (email-for-key teppo)) => ok?
      (count (:invites (query teppo :invites))) => 1)

    (fact "Sonja must be able to uninvite Teppo!"
      (command sonja :remove-auth :id id :email (email-for-key teppo)) => ok?
      (count (:invites (query teppo :invites))) => 0)

    (fact "Reinvite & accept"
      (invite sonja id suunnittelija-doc (email-for-key teppo)) => ok?
      (count (:invites (query teppo :invites))) => 1
      (command teppo :approve-invite :id id) => ok?)

    (fact "Teppo must be able to comment!"
      (command teppo :add-comment :id id :text (email-for-key teppo) :target {:type "application"}) => ok?)

    (fact "Teppo must be able to invite another designer, Mikko!"
      (invite teppo id suunnittelija-doc "mikko@example.com") => ok?)

    (fact "Sonja must be able to remove authz from Teppo!"
      (command sonja :remove-auth :id id :email (email-for-key teppo)) => ok?)))
