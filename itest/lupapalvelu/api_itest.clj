(ns lupapalvelu.api-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet])
  (:require [lupapalvelu.domain :as domain]))

(facts "Secury! SECURITY!!"
  (apply-remote-minimal)

  (fact "Disabled user must not be able to create an application!"
    (create-app dummy) => invalid-csrf-token?)

  (let [resp  (create-app mikko :municipality sonja-muni)
        id    (:id resp)]
    (fact "Mikko must be able to create an application!"
      resp => ok?)

    (let [resp  (query mikko :application :id id)
          application (:application resp)
          hakija (domain/get-document-by-name application "hakija")]
      (facts "Mikko can see hes application!"
        (:ok resp)
        (:text rest)
        application => truthy
        (:username (first (:auth application))) => "mikko@example.com"
        (get-in hakija [:data :henkilo :henkilotiedot]) => {:etunimi {:value "Mikko"} :sukunimi {:value "Intonen"}}

        (let [listing (query mikko :applications)]
          listing => ok?
          (:id (first (:applications listing))) => id))

      (fact "Disabled user must not see Mikko's application!"
        (query dummy :application :id id) => invalid-csrf-token?)

      (fact "Teppo must not see Mikko's application!"
        (query teppo :application :id id) => unauthorized?)

      (fact "Mikko must be able to comment!"
        (command mikko :add-comment :id id :text "mikko@example.com" :target "application") => ok?)

      (fact "Teppo must not be able to comment!"
        (command teppo :add-comment :id id :text "teppo@example.com" :target "application") => unauthorized?)

      (fact "Veikko must not be able to comment!"
        (command veikko :add-comment :id id :text "sonja" :target "application") => unauthorized?)

      (fact "Sonja must be able to see the application!"
        (let [listing (query sonja :applications)]
          listing => ok?
          (-> listing :applications first :id) => id))

      (fact "Sonja must be able to comment!"
        (command sonja :add-comment :id id :text "sonja" :target "application") => ok?)

      (fact "Mikko must not be able to assign to himself!"
        (command mikko :assign-to-me :id id) => unauthorized?)

      (fact "Teppo must not be able to assign to himself!"
        (command teppo :assign-to-me :id id) => unauthorized?)

      (fact "Veikko must not be able to assign to himself!"
        (command veikko :assign-to-me :id id) => unauthorized?)

      (fact "Sonja must be able to assign to herself!"
        (command sonja :assign-to-me :id id) => ok?)

      (fact "Assigning to document"
        (let [paasuunnittelija (domain/get-document-by-name application "paasuunnittelija")
              documentId       (:id paasuunnittelija)
              userId           (get-in (query mikko :user) [:user :id])]

          (fact "there is no paasuunnittelija"
            (get-in paasuunnittelija [:data :henkilotiedot]) => nil)

          (command mikko :set-user-to-document :id id :documentId documentId :userId userId :path "") => ok?

          (let [new-application       (:application (query mikko :application :id id))
                new-paasuunnittelija (domain/get-document-by-name new-application "paasuunnittelija")]

            (fact "new paasuunnittelija is set"
              (get-in new-paasuunnittelija [:data :henkilotiedot]) => {:etunimi {:value "Mikko"} :sukunimi {:value "Intonen"}})))))))

(defn- invite [apikey application-id email]
  (command apikey :invite :id application-id :email email :title email :text email :documentName "suunnittelija"))

(facts "Secure invites"
  (apply-remote-minimal)

  (doseq [user-key [mikko teppo veikko sonja sipoo]]
    (let [resp (query user-key :invites)]
      resp => ok?
      (count (:invites resp)) => 0))

  (let [resp  (create-app mikko :municipality sonja-muni)
        id    (:id resp)]
    resp => ok?

    (fact "Teppo must not be able to invite himself!"
      (invite teppo id "teppo@example.com") => unauthorized?)

    (fact "Mikko must be able to invite Teppo!"
      (invite mikko id "teppo@example.com") => ok?)

    (count (:invites (query teppo :invites))) => 1

    (fact "Mikko must be able to uninvite Teppo!"
      (command mikko :remove-invite :id id :email "teppo@example.com") => ok?
      (count (:invites (query teppo :invites))) => 0)

    (fact "Mikko must be able to re-invite Teppo!"
      (invite mikko id "teppo@example.com") => ok?)

    (count (:invites (query teppo :invites))) => 1

    (fact "Mikko must not be able to accept Teppo's invite"
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
        (-> actions :cancel-application :ok) => true))))
