(ns lupapalvelu.application-bulletins-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.mongo :as mongo]))

(apply-remote-minimal)

(facts "Publishing bulletins"
  (let [app (create-and-submit-application pena :operation "jatteen-keraystoiminta"
                                                :propertyId oulu-property-id
                                                :x 430109.3125 :y 7210461.375
                                                :address "Oulu 10")
        app-id (:id app)]

    (fact "Bulletin not found before publishing"
      (query pena :bulletin :bulletinId app-id) => (partial expected-failure? :error.bulletin.not-found))

    (fact "Authority can publish bulletin"
      (command olli :publish-bulletin :id app-id) => ok?)
    (fact "Regular user can't publish bulletin"
      (command pena :publish-bulletin :id app-id) => fail?)

    (facts "Bulletin query"
      (let [bulletin (query-bulletin pena app-id)]
        (fact "bulletin state is 'proclaimed'"
          (:bulletinState bulletin) => "proclaimed")
        (fact "each documents has schema definition"
          (:documents bulletin) => (partial every? :schema))
        (fact "no party documents"
          (:documents bulletin) => (partial every? #(not= (-> % :schema-info :type keyword) :party)))))

    (fact "Publishing again creates new version snapshot"
      (command olli :publish-bulletin :id app-id) => ok?
      (-> (mongo/with-db test-db-name
            (mongo/by-id :application-bulletins app-id [:versions]))
        :versions
        count) => 2)))

(mongo/with-db test-db-name ; clear bulletins
  (mongo/remove-many :application-bulletins {}))

(facts* "Querying bulletins"
  (let [oulu-app (create-and-submit-application pena :operation "jatteen-keraystoiminta"
                                                :propertyId oulu-property-id
                                                :x 430109.3125 :y 7210461.375
                                                :address "Oulu 10")
        sipoo-app (create-and-submit-application pena :operation "meluilmoitus"
                                                :propertyId sipoo-property-id
                                                :x 406898.625 :y 6684125.375
                                                :address "Hitantine 108")
        _ (command olli :publish-bulletin :id (:id oulu-app)) => ok?
        _ (command sonja :publish-bulletin :id (:id sipoo-app)) => ok?
        _ (datatables pena :application-bulletins :page "1"
                                                  :searchText ""
                                                  :municipality nil
                                                  :state nil
                                                  :sort nil) => (partial expected-failure? :error.illegal-number)
        resp (datatables pena :application-bulletins :page 1 :searchText "" :municipality nil :state nil :sort nil) => ok?]
    (fact "Two bulletins is returned"
      (count (:data resp)) => 2)))
