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

(fact "invalid command is not persisted in memory"
  (query pena :aaaa-foopar) => (partial expected-failure? "error.invalid-command")
  (query pena :aaaa-foopar) => (partial expected-failure? "error.invalid-command")
  (-> (query pena :allowed-actions) :actions :aaaa-foopar) => nil)



(def resp (create-app mikko :propertyId sipoo-property-id))
(def app-id (:id resp))

(fact "Mikko must be able to create an application!"
  resp => ok?)

(facts "Mikko can see his application!"
  (let [resp (query mikko :application :id app-id)]

    (fact "query is ok"
      resp => ok?)

    (fact "query response contains application"
      (:application resp) => truthy)

    (fact "application contains hakija-document"
      (domain/get-document-by-name (:application resp) "hakija-r") => map?)

    (fact "mikko is first in application auth array"
      (-> resp :application :auth first :username) => "mikko@example.com")))

(fact "Disabled user must not see Mikko's application!"
  (raw-query dummy :application :id app-id) => invalid-csrf-token?)

(fact "Teppo must not see Mikko's application!"
  (query teppo :application :id app-id) => not-accessible?)

(fact "Mikko must be able to comment!"
  (comment-application mikko app-id true) => ok?)

(fact "Teppo must not be able to comment!"
  (comment-application teppo app-id false) => not-accessible?)

(fact "Veikko must not be able to comment!"
  (comment-application veikko app-id false) => not-accessible?)

(fact "Sonja must be able to see the application!"
  (query sonja :application :id app-id) => ok?)

(fact "Sonja must be able to comment!"
  (comment-application sonja app-id false) => ok?)

(fact "Mikko must not be able to assign to himself!"
  (command mikko :assign-application :id app-id :assigneeId mikko-id) => unauthorized?)

(fact "Teppo must not be able to assign to himself!"
  (command teppo :assign-application :id app-id :assigneeId teppo-id) => unauthorized?)

(fact "Veikko must not be able to assign to himself!"
  (command veikko :assign-application :id app-id :assigneeId veikko-id) => not-accessible?)

(fact "Sonja must be able to assign to herself!"
  (command sonja :assign-application :id app-id :assigneeId sonja-id) => ok?)
