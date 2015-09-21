(ns lupapalvelu.invite-itest
  (:require [midje.sweet  :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]))

(defn- invite [apikey application-id document-id doc-name email & [text]]
  (command apikey :invite-with-role
    :id application-id
    :email email
    :text  (or text email)
    :documentName doc-name
    :documentId document-id
    :path ""
    :role "writer"))

(apply-remote-minimal)

(facts* "Applicant invites designer"

  (doseq [user-key [mikko teppo veikko sonja]]
    (let [resp (query user-key :invites) => ok?]
      (count (:invites resp)) => 0))

  (let [application-id (create-app-id mikko :propertyId sipoo-property-id :address "Kutsukatu 13")
        app    (query-application mikko application-id)
        {hakija-doc :doc}  (command mikko :create-doc :id application-id :schemaName "hakija-r") => truthy
        suunnittelija-doc (:id (domain/get-document-by-name app "suunnittelija")) => truthy
        paasuunnittelija-doc (:id (domain/get-document-by-name app "paasuunnittelija")) => truthy]

    (facts "email is validated"
      (fact "Empty email is rejected"
        (:text (invite mikko application-id suunnittelija-doc "suunnittelija" "")) => "error.missing-parameters")
      (fact "Email contains whitespace"
        (:text (invite mikko application-id suunnittelija-doc "suunnittelija" "juha jokimaki@solita.fi")) => "error.email")
      (fact "Email contains non-ascii chars"
        (:text (invite mikko application-id suunnittelija-doc "suunnittelija" "juha.jokim\u00e4ki@solita.fi")) => "error.email"))

    (fact "Teppo must not be able to invite himself!"
      (invite teppo application-id suunnittelija-doc "suunnittelija" (email-for-key teppo)) => not-accessible?)


    (fact "Mikko must be able to invite Teppo!"
      (last-email) ; Inbox zero

      (invite mikko application-id suunnittelija-doc "suunnittelija" (email-for-key teppo) "Hei, sinut on kutsuttu") => ok?

      (let [invs (query teppo :invites)
            email (last-email)]

        (count (:invites invs)) => 1

        (fact "invite response has correct application map keys"
          (keys (:application (first (:invites invs)))) => (just [:id :municipality :address :primaryOperation] :in-any-order))

        email => (partial contains-application-link? application-id "applicant")
        (:to email) => (contains (email-for-key teppo))
        (:subject email) => "Lupapiste.fi: Kutsukatu 13 - kutsu"
        (get-in email [:body :plain]) => (contains "Hei, sinut on kutsuttu")))

    (fact "Sonja must NOT be able to uninvite Teppo!"
      (command sonja :remove-auth :id application-id :username (email-for-key teppo)) => unauthorized?
      (count (:invites (query teppo :invites))) => 1)

    (fact "Mikko can't unsubscribe Teppo's notifications"
      (command pena :unsubscribe-notifications :id application-id :username (email-for-key teppo)) => not-accessible?)

    (fact "Mikko must be able to uninvite Teppo!"
      (command mikko :remove-auth :id application-id :username (email-for-key teppo)) => ok?
      (count (:invites (query teppo :invites))) => 0)

    (fact "Mikko must be able to re-invite Teppo!"
      (invite mikko application-id suunnittelija-doc "suunnittelija" (email-for-key teppo)) => ok?
      (count (:invites (query teppo :invites))) => 1)

    (fact "Teppo must be able to decline invitation!"
      (command teppo :decline-invitation :id application-id) => ok?
      (count (:invites (query teppo :invites))) => 0)

    (fact "Mikko must NOT be able to accept Teppo's invite"
      (invite mikko application-id suunnittelija-doc "suunnittelija" (email-for-key teppo)) => ok?
      (command mikko :approve-invite :id application-id :email (email-for-key teppo))
      (count (:invites (query teppo :invites))) => 1)

    (fact "Mikko submits"
      (command mikko :submit-application :id application-id) => ok?)

    (last-email) ; Inbox zero

    (fact "Sonja adds comment, only Mikko gets email"
      (comment-application sonja application-id false) => ok?
      (let [emails (sent-emails)]
        (count emails) => 1
        (:to (first emails)) => (contains (email-for-key mikko))))

    (fact "Teppo can read Mikko's application before accepting"
      (query teppo :application :id application-id) => ok?)

    (fact "Teppo can not be se to document before he accepts invite"
      (command mikko :set-user-to-document :id application-id :documentId hakija-doc :userId teppo-id :path "henkilo") => (partial expected-failure? "error.application-does-not-have-given-auth"))

    (fact "Teppo can not comment on Mikko's application before accepting the invite"
      (comment-application teppo application-id true) => unauthorized?)

    (fact "Teppo must be able to accept Mikko's invite"
      (command teppo :approve-invite :id application-id) => ok?
      (count (:invites (query teppo :invites))) => 0)

    (fact "Teppo must be able to comment!"
      (comment-application teppo application-id true) => ok?
      (fact "application stays in submitted state"
        (:state (query-application teppo application-id)) => "submitted"))

    (fact "Mikko is the applicant"
      (let [application  (query-application mikko application-id)
            first-hakija (domain/get-applicant-document (:documents application))]
        (:id first-hakija) =not=> hakija-doc
        (get-in first-hakija [:data :henkilo :henkilotiedot :etunimi :value]) => ""
        (:applicant application ) => "Intonen Mikko"))

    (fact "Mikko sets Teppo as co-applicant"
      (command mikko :set-user-to-document :id application-id :documentId hakija-doc :userId teppo-id :path "henkilo") => ok?
      (fact "Teppo is now the applicant"
        (let [application  (query-application mikko application-id)
              hakija (domain/get-document-by-id application hakija-doc)]
          (get-in hakija [:data :henkilo :henkilotiedot :etunimi :value]) => "Teppo"
          (get-in hakija [:data :henkilo :userId :value]) => teppo-id
          (:applicant application ) => "Nieminen Teppo")))

    (let [actions (:actions (query teppo :allowed-actions :id application-id))]
      (fact "Teppo should be able to"
        (fact "add-operation" (-> actions :add-operation :ok) => true)
        (fact "update-doc" (-> actions :update-doc :ok) => true)
        (fact "cancel application" (-> actions :cancel-application :ok) => true)))

    (fact "Sonja must be able to remove authz from Teppo!"
      (command sonja :remove-auth :id application-id :username (email-for-key teppo)) => ok?)

    (fact "Teppo should NOT be able to do stuff."
      (query teppo :allowed-actions :id application-id) => not-accessible?)

    (fact "Teppo's user ID is removed from document but data not"
      (let [application  (query-application mikko application-id)
            hakija (domain/get-document-by-id application hakija-doc)]
          (get-in hakija [:data :henkilo :henkilotiedot :etunimi :value]) => "Teppo"
          (get-in hakija [:data :henkilo :userId :value]) => nil?
          (:applicant application ) => "Nieminen Teppo"))

    (fact "Pena is inveted to a deleted doc"
      (invite mikko application-id paasuunnittelija-doc "paasuunnittelija" (email-for "pena")) => ok?
      (command mikko :remove-doc :id application-id :docId paasuunnittelija-doc) => ok?
      (count (:invites (query pena :invites))) => 1

      (fact "Pena can still approve invite"
        (command pena :approve-invite :id application-id) => ok?))))

(facts* "Authority invites designer"
  (doseq [user-key [sonja mikko teppo]]
    (let [resp (query user-key :invites) => ok?]
      (count (:invites resp)) => 0))

  (let [resp  (create-app sonja :propertyId sipoo-property-id) => ok?
        id    (:id resp) => truthy
        app    (query-application sonja id)
        suunnittelija-doc (:id (domain/get-document-by-name app "suunnittelija"))]

    (fact "Sonja must be able to invite Teppo!"
      (invite sonja id suunnittelija-doc "suunnittelija" (email-for-key teppo)) => ok?
      (count (:invites (query teppo :invites))) => 1)

    (fact "Sonja must be able to uninvite Teppo!"
      (command sonja :remove-auth :id id :username (email-for-key teppo)) => ok?
      (count (:invites (query teppo :invites))) => 0)

    (fact "Reinvite & accept"
      (invite sonja id suunnittelija-doc "suunnittelija" (email-for-key teppo)) => ok?
      (count (:invites (query teppo :invites))) => 1
      (command teppo :approve-invite :id id) => ok?)

    (fact "Teppo must be able to comment!"
      (comment-application teppo id false) => ok?)

    (fact "Teppo must be able to invite another designer, Mikko!"
      (invite teppo id suunnittelija-doc "suunnittelija" "mikko@example.com") => ok?)

    (fact "Sonja must be able to remove authz from Teppo!"
      (command sonja :remove-auth :id id :username (email-for-key teppo)) => ok?)

    (fact "Invite without document"
      (invite sonja id "" "" (email-for-key teppo)) => ok?
      (count (:invites (query teppo :invites))) => 1
      (command teppo :approve-invite :id id) => ok?)))
