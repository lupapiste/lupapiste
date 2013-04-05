(ns lupapalvelu.api-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet])
  (:require [lupapalvelu.domain :as domain]))

(facts "Secury! SECURITY!!"
  (apply-remote-minimal)

  (fact "Disabled user must not be able to create an application!"
    (invalid-csrf-token (create-app dummy)) => true)

  (let [resp  (create-app mikko :municipality sonja-muni)
        id    (:id resp)]
    (fact "Mikko must be able to create an application!"
      (success resp) => true)

    (let [resp  (query mikko :application :id id)
          application (:application resp)
          hakija (domain/get-document-by-name application "hakija")]
      (facts "Mikko can see hes application!"
        (:ok resp)
        (:text rest)
        application => truthy
        (:username (first (:auth application))) => "mikko@example.com"
        (get-in hakija [:data :henkilo :henkilotiedot]) => {:etunimi "Mikko" :sukunimi "Intonen"}

        (let [listing (query mikko :applications)]
          (success listing) => true
          (:id (first (:applications listing))) => id))

      (fact "Disabled user must not see Mikko's application!"
        (invalid-csrf-token (query dummy :application :id id)) => true)

      (fact "Teppo must not see Mikko's application!"
        (unauthorized (query teppo :application :id id)) => true)

      (fact "Mikko must be able to comment!"
        (success (command mikko :add-comment :id id :text "mikko@example.com" :target "application")) => true)

      (fact "Teppo must not be able to comment!"
        (unauthorized (command teppo :add-comment :id id :text "teppo@example.com" :target "application")) => true)

      (fact "Veikko must not be able to comment!"
        (unauthorized (command veikko :add-comment :id id :text "sonja" :target "application")) => true)

      (fact "Sonja must be able to see the application!"
        (let [listing (query sonja :applications)]
          (success listing) => true
          (:id (first (:applications listing))) => id))

      (fact "Sonja must be able to comment!"
        (success (command sonja :add-comment :id id :text "sonja" :target "application")) => true)

      (fact "Mikko must not be able to assign to himself!"
        (unauthorized (command mikko :assign-to-me :id id)) => true)

      (fact "Teppo must not be able to assign to himself!"
        (unauthorized (command teppo :assign-to-me :id id)) => true)

      (fact "Veikko must not be able to assign to himself!"
        (unauthorized (command veikko :assign-to-me :id id)) => true)

      (fact "Sonja must be able to assign to herself!"
        (success (command sonja :assign-to-me :id id)) => true)

      (fact "Assigning to document"
        (let [paasuunnittelija (domain/get-document-by-name application "paasuunnittelija")
              documentId       (:id paasuunnittelija)
              userId           (get-in (query mikko :user) [:user :id])]

          (fact "there is no paasuunnittelija"
            (get-in paasuunnittelija [:data :henkilotiedot]) => nil)

          (success
            (command mikko :set-user-to-document :id id :documentId documentId :userId userId :path ""))

          (let [new-application       (:application (query mikko :application :id id))
                new-paasuunnittelija (domain/get-document-by-name new-application "paasuunnittelija")]

            (fact "new paasuunnittelija is set"
              (get-in new-paasuunnittelija [:data :henkilotiedot]) => {:etunimi "Mikko" :sukunimi "Intonen"})))))))

(defn- invite [apikey application-id email]
  (command apikey :invite :id application-id :email email :title email :text email :documentName "suunnittelija"))

(facts "Secure invites"
  (apply-remote-minimal)

  (doseq [user-key [mikko teppo veikko sonja sipoo]]
    (let [resp (query user-key :invites)]
      (success resp) => true
      (count (:invites resp)) => 0))

  (let [resp  (create-app mikko :municipality sonja-muni)
        id    (:id resp)]
    (success resp) => true

    (fact "Teppo must not be able to invite himself!"
      (unauthorized (invite teppo id "teppo@example.com")) => true)

    (fact "Mikko must be able to invite Teppo!"
      (success (invite mikko id "teppo@example.com")) => true)

    (count (:invites (query teppo :invites))) => 1

    (fact "Mikko must be able to uninvite Teppo!"
      (success (command mikko :remove-invite :id id :email "teppo@example.com")) => true
      (count (:invites (query teppo :invites))) => 0)

    (fact "Mikko must be able to re-invite Teppo!"
      (success (invite mikko id "teppo@example.com")) => true)

    (count (:invites (query teppo :invites))) => 1

    (fact "Mikko must not be able to accept Teppo's invite"
      (command mikko :approve-invite :id id :email "teppo@example.com")
      (count (:invites (query teppo :invites))) => 1)

    (fact "Teppo must be able to accept Teppo's invite"
      (success (command teppo :approve-invite :id id)) => true
      (count (:invites (query teppo :invites))) => 0)

    (fact "Teppo must be able to comment!"
      (success (command teppo :add-comment :id id :text "teppo@example.com" :target "application")) => true)

    (let [actions (:actions (query teppo :allowed-actions :id id))]
      (fact "Teppo should be able to do stuff."
        (-> actions :add-operation :ok) => true
        (-> actions :submit-application :ok) => true
        (-> actions :cancel-application :ok) => true))))
