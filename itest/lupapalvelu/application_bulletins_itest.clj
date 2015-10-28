(ns lupapalvelu.application-bulletins-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.mongo :as mongo]))

(when (sade.env/feature? :publish-bulletin)
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
        (count (:data resp)) => 2)

      (facts "Response data"
        (let [bulletin (query-bulletin pena (:id oulu-app))]
          (fact "bulletin state is 'proclaimed'"
            (:bulletinState bulletin) => "proclaimed")
          (fact "each documents has schema definition"
            (:documents bulletin) => (partial every? :schema))
          (fact "no party documents"
            (:documents bulletin) => (partial every? #(not= (-> % :schema-info :type keyword) :party)))))

      (facts "Filters"
       (fact "Municipality"
         (:municipalities (query pena :application-bulletin-municipalities)) => (just ["564" "753"])
         (let [{data :data} (datatables pena :application-bulletins :page 1 :searchText "" :municipality "753" :state nil :sort nil)]
           (count data) => 1
           (:id (first data)) => (:id sipoo-app)))

       (fact "State"
         (:states (query pena :application-bulletin-states)) => (just ["proclaimed"])
         (let [{data :data} (datatables pena :application-bulletins :page 1 :searchText "" :municipality nil :state "proclaimed" :sort nil)]
           (count data) => 2))

       (fact "Free text"
         (let [{data :data} (datatables pena :application-bulletins :page 1 :searchText "hitan" :municipality nil :state nil :sort nil)]
           (count data) => 1
           (:id (first data)) => (:id sipoo-app))))

      (facts "Paging"
       (dotimes [_ 20]
         (let [{id :id} (create-and-submit-application pena :operation "jatteen-keraystoiminta"
                                                            :propertyId oulu-property-id
                                                            :x 430109.3125 :y 7210461.375
                                                            :address "Oulu 10")]
           (command olli :publish-bulletin :id id)))
       (let [{p1-data :data p1-left :left} (datatables pena :application-bulletins :page 1
                                                                                   :searchText ""
                                                                                   :municipality nil
                                                                                   :state nil
                                                                                   :sort nil)
             {p2-data :data p2-left :left} (datatables pena :application-bulletins :page 2
                                                                                   :searchText ""
                                                                                   :municipality nil
                                                                                   :state nil
                                                                                   :sort nil)
             {p3-data :data p3-left :left} (datatables pena :application-bulletins :page 3
                                                                                   :searchText ""
                                                                                   :municipality nil
                                                                                   :state nil
                                                                                   :sort nil)]
         (fact "page 1"
           (count p1-data) => 10
           p1-left => 12)
         (fact "page 2"
           (count p2-data) => 10
           p2-left => 2)
         (fact "page 3"
           (count p3-data) => 2
           p3-left => -8))))))