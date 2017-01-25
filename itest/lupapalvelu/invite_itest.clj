(ns lupapalvelu.invite-itest
  (:require [midje.sweet  :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]))

(defn- invite [apikey application-id document-id doc-name email & [path text]]
  (command apikey :invite-with-role
    :id application-id
    :email email
    :text  (or text email)
    :documentName doc-name
    :documentId document-id
    :path (or path "")
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
        (:text (invite mikko application-id suunnittelija-doc "suunnittelija" "eero eratuli@example.com")) => "error.email")
      (fact "Email contains non-ascii chars"
        (:text (invite mikko application-id suunnittelija-doc "suunnittelija" "eero.er\u00e4tuli@example.com")) => "error.email"))

    (fact "Teppo must not be able to invite himself!"
      (invite teppo application-id suunnittelija-doc "suunnittelija" (email-for-key teppo)) => not-accessible?)


    (fact "Mikko must be able to invite Teppo!"
      (last-email) ; Inbox zero

      (invite mikko application-id suunnittelija-doc "suunnittelija" (email-for-key teppo) "" "Hei, sinut on kutsuttu") => ok?

      (let [invs (query teppo :invites)
            email (last-email)]

        (count (:invites invs)) => 1

        (fact "invite response has correct application map keys"
          (-> invs :invites first :application keys) => (just [:id :municipality :address :primaryOperation] :in-any-order))

        (fact "invite email contents"
          email => (partial contains-application-link? application-id "applicant")
          (:to email) => (contains (email-for-key teppo))
          (:subject email) => "Lupapiste: Kutsukatu 13, Sipoo - valtuutus Lupapisteess\u00e4"
          (get-in email [:body :plain]) => (contains "Hei, sinut on kutsuttu")
          (get-in email [:body :plain]) => (contains (email-for-key teppo))
          (get-in email [:body :plain]) => (contains (email-for-key mikko)))))

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

    (fact "Teppo can not downgrade Mikko's authtorization"
      (command teppo :change-auth :id application-id :role "writer" :userId (id-for-key mikko)) => unauthorized?)

    (fact "Teppo can not change own authtorization"
      (command teppo :change-auth :id application-id :role "foreman" :userId (id-for-key teppo)) => unauthorized?)

    (fact "Mikko prefils Teppo's company name"
      (command mikko :update-doc :id application-id :doc suunnittelija-doc  :collection "documents" :updates [["yritys.yritysnimi" "Tepon saneeraus"]]) => ok?)

    (fact "Teppo must be able to accept Mikko's invite"
      (command teppo :approve-invite :id application-id) => ok?
      (count (:invites (query teppo :invites))) => 0)

    (fact "Teppo's info is now in place"
      (let [application  (query-application mikko application-id)
            suunnittelija (domain/get-document-by-id application suunnittelija-doc)]
        (get-in suunnittelija [:data :henkilotiedot :etunimi :value]) => "Teppo"
        (get-in suunnittelija [:data :henkilotiedot :sukunimi :value]) => "Nieminen"
        (get-in suunnittelija [:data :osoite :katu :value]) => "Mutakatu 7"
        (get-in suunnittelija [:data :osoite :postitoimipaikannimi :value]) => "Tampere"

        (fact "Teppo does not have company in his profile, but the data was not overwriten"
          (get-in suunnittelija [:data :yritys :yritysnimi :value]) => "Tepon saneeraus")))

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
          (get-in hakija [:data :henkilo :kytkimet :suoramarkkinointilupa :value]) => true
          (:applicant application ) => "Nieminen Teppo")))

    (fact "Mikko removes Teppo by mistake"
      (command mikko :remove-auth :id application-id :username (email-for-key teppo)) => ok?
      (fact "invites again as applicant"
        (invite mikko application-id hakija-doc "hakija-r" (email-for-key teppo) "henkilo" "moi") => ok?)
      (fact "Teppo approves"
        (command teppo :approve-invite :id application-id) => ok?))

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
      (command sonja :remove-doc :id application-id :docId paasuunnittelija-doc) => ok?
      (count (:invites (query pena :invites))) => 1

      (fact "Pena can still approve invite"
        (command pena :approve-invite :id application-id) => ok?))
    ))

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
