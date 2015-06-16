(ns lupapalvelu.api-itest
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(fact "anti-csrf is active"
  (feature? :disable-anti-csrf) => false)

(fact "Disabled user must not be able to create an application!"
  (raw-command dummy :create-application :operation "kerrostalo-rivitalo"
    :propertyId "75312312341234"
    :x 444444 :y 6666666
    :address "foo 42, bar") => invalid-csrf-token?)

(fact "non-admin users should not be able to get actions"
  (query pena :actions) => unauthorized?
  (query sonja :actions) => unauthorized?)

(let [{id :id :as resp}  (create-app mikko :propertyId sipoo-property-id)]
  (fact "Mikko must be able to create an application!"
    resp => ok?)

  (let [resp  (query mikko :application :id id)
        application (:application resp)
        hakija (domain/get-document-by-name application "hakija-r")]

    hakija => map?

    (facts "Mikko can see his application!"
      (:ok resp)
      (:text rest)
      application => truthy
      (:username (first (:auth application))) => "mikko@example.com")

    (fact "Disabled user must not see Mikko's application!"
      (raw-query dummy :application :id id) => invalid-csrf-token?)

    (fact "Teppo must not see Mikko's application!"
      (query teppo :application :id id) => not-accessible?)

    (fact "Mikko must be able to comment!"
      (comment-application mikko id true) => ok?)

    (fact "Teppo must not be able to comment!"
      (comment-application teppo id false) => not-accessible?)

    (fact "Veikko must not be able to comment!"
      (comment-application veikko id false) => not-accessible?)

    (fact "Sonja must be able to see the application!"
      (let [resp (query sonja :application :id id)]
        resp => ok?))

    (fact "Sonja must be able to comment!"
      (comment-application sonja id false) => ok?)

    (fact "Mikko must not be able to assign to himself!"
      (command mikko :assign-application :id id :assigneeId mikko-id) => unauthorized?)

    (fact "Teppo must not be able to assign to himself!"
      (command teppo :assign-application :id id :assigneeId teppo-id) => unauthorized?)

    (fact "Veikko must not be able to assign to himself!"
      (command veikko :assign-application :id id :assigneeId veikko-id) => not-accessible?)

    (fact "Sonja must be able to assign to herself!"
      (command sonja :assign-application :id id :assigneeId sonja-id) => ok?)))
