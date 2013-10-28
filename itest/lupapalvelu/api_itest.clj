(ns lupapalvelu.api-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet])
  (:require [lupapalvelu.domain :as domain]))

(apply-remote-minimal)

(fact "anti-csrf is active"
  (feature? :disable-anti-csrf) => false)

(fact "Disabled user must not be able to create an application!"
  (raw-command dummy :create-application :operation "asuinrakennus"
    :propertyId "75312312341234"
    :x 444444 :y 6666666
    :address "foo 42, bar"
    :municipality "753") => invalid-csrf-token?)

(fact "non-admin users should not be able to get actions"
  (query pena :actions) => unauthorized?
  (query sonja :actions) => unauthorized?)

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
      (get-in hakija [:data :henkilo :henkilotiedot :etunimi :value]) => "Mikko"
      (get-in hakija [:data :henkilo :henkilotiedot :sukunimi :value]) => "Intonen"

      (let [listing (query mikko :applications)]
        listing => ok?
        (:id (first (:applications listing))) => id))

    (fact "Disabled user must not see Mikko's application!"
      (raw-query dummy :application :id id) => invalid-csrf-token?)

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
      (command sonja :assign-to-me :id id) => ok?)))
