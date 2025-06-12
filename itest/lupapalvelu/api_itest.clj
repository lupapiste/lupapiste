(ns lupapalvelu.api-itest
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.env :as env]))

(defn- run-test-cases
  []
  (fact "anti-csrf is active"
    (feature? :disable-anti-csrf) => false)

  (fact "Disabled user must not be able to create an application!"
    (raw-command dummy :create-application :operation "kerrostalo-rivitalo"
                 :propertyId "75312312341234"
                 :x 444444 :y 6666666
                 :address "foo 42, bar") => invalid-csrf-token?)

  (fact "invalid command is not persisted in memory"
    (query pena :aaaa-foopar) => (partial expected-failure? "error.invalid-command")
    (query pena :aaaa-foopar) => (partial expected-failure? "error.invalid-command")
    (-> (query pena :allowed-actions) :actions :aaaa-foopar) => nil)

  (let [app-id (create-app-id mikko :propertyId sipoo-property-id)]
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

    (fact "Mikko (owner) must not be able to assign to himself!"
      (command mikko :upsert-application-handler :id app-id :userId mikko-id :roleId sipoo-general-handler-id) => unauthorized?)

    (fact "Teppo (random applicant) must not be able to assign to himself!"
      (command teppo :upsert-application-handler :id app-id :userId teppo-id :roleId sipoo-general-handler-id) => not-accessible?)

    (fact "Veikko (authority in other organization) must not be able to assign to himself!"
      (command veikko :upsert-application-handler :id app-id :userId veikko-id :roleId sipoo-general-handler-id) => not-accessible?)

    (fact "Sonja (authority) must be able to assign to herself!"
      (command sonja :upsert-application-handler :id app-id :userId sonja-id :roleId sipoo-general-handler-id) => ok?)

    (facts "allowed-actions-for-category"
      (fact "bad category"
        (query sonja :allowed-actions-for-category :id app-id
               :category "noidea") => (err :error.invalid-category)
        (query sonja :allowed-actions-for-category :id app-id
               :category "default") => (err :error.invalid-category))
      (fact "good category"
        (:actionsById (query sonja :allowed-actions-for-category :id app-id
                             :category "documents"))
        => (every-pred map? not-empty)
        (:actionsById (query sonja :allowed-actions-for-category :id app-id
                             :category "reviews"))
        => (every-pred map? empty?)))))

;; Test using Noir
(env/with-feature-value :reitit-api false
  (with-test-system (-> {:fixture "minimal"}
                        test-system/with-remote-components)
    (run-test-cases)))

;; Test using reitit
(env/with-feature-value :reitit-api true
  (with-test-system (-> {:fixture "minimal"}
                        test-system/with-remote-components)
    (run-test-cases)))
