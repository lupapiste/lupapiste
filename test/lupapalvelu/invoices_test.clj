(ns lupapalvelu.invoices-test
  (:require [lupapalvelu.invoices :refer [->invoice-user ->invoice-db get-operations-from-application]]
            [midje.sweet :refer :all]
            [schema.core :as sc]
            [sade.core]))

(facts "->invoice-user"
       (fact "throws (validation) error when user map given as argument lack require fields for constructing an (invoice) User"
             (->invoice-user {:foo "foo"
                              :id "user-id"}) => (throws Exception))
       (fact "returns a proper user object given user data map with the required fields"
             (let [user-data {:foo "foo"
                              :bar "bar"
                              :id        "penan-id"
                              :firstName "pena"
                              :lastName  "panaani"
                              :role      "authority"
                              :email     "pena@panaani.fi"
                              :username  "pena"}]
               (->invoice-user user-data) => {:id                                        "penan-id"
                                              :firstName                                 "pena"
                                              :lastName                                  "panaani"
                                              :role                                      "authority"
                                              :email                                     "pena@panaani.fi"
                                              :username                                  "pena"})))

(facts "->invoice-db"
       (let [user-data {:foo       "some-value"
                        :id        "penan-id"
                        :firstName "pena"
                        :lastName  "panaani"
                        :role      "authority"
                        :email     "pena@panaani.fi"
                        :username  "pena"}
             invoice-data {:state "draft"
                           :operations [{:operation-id "linjasaneeraus"
                                         :name "linjasaneeraus"
                                         :invoice-rows [{:text "Laskurivi1 kpl"
                                                         :type "from-price-catalogue"
                                                         :unit "kpl"
                                                         :price-per-unit 10
                                                         :units 2
                                                         :discount-percent 0}]}]}
             application-data {:id "LPK-1-TEST" :organization "123-R-TEST"}]
         (fact "returns a proper invoice map ready to be stored to the database"
               (with-redefs [sade.core/now (fn [] 12345)]

                 (->invoice-db invoice-data application-data user-data) => {:state "draft"
                                                                            :created 12345
                                                                            :created-by {:id        "penan-id"
                                                                                         :firstName "pena"
                                                                                         :lastName  "panaani"
                                                                                         :role      "authority"
                                                                                         :email     "pena@panaani.fi"
                                                                                         :username  "pena"}
                                                                            :application-id "LPK-1-TEST"
                                                                            :organization-id "123-R-TEST"
                                                                            :operations [{:operation-id "linjasaneeraus"
                                                                                          :name "linjasaneeraus"
                                                                                          :invoice-rows [{:text "Laskurivi1 kpl"
                                                                                                          :type "from-price-catalogue"
                                                                                                          :unit "kpl"
                                                                                                          :price-per-unit 10
                                                                                                          :units 2
                                                                                                          :discount-percent 0}]}]}))))
(facts "get-operations-from-application"
       (let [primary-operation {:id "5bbc76b0b170d541a3c488ec"
                                :name "kerrostalo-rivitalo"
                                :description nil
                                :created 12344566}
             secondary-operation {:id "6bbc76b0b170d541a3c488ec"
                                  :name "purkaminen"
                                  :description nil
                                  :created 12344566}
             mock-application-with-primary-operation-only {:primaryOperation primary-operation}
             mock-application-with-primary-operation-and-secondary-operations {:primaryOperation primary-operation :secondaryOperations [secondary-operation secondary-operation]}
             mock-application-with-primary-operation-and-empty-secondary-operations {:primaryOperation primary-operation :secondaryOperations []}
             mock-application-with-primary-operation-and-nil-secondary-operations {:primaryOperation primary-operation :secondaryOperations nil}]

         (fact "returns vector contaning primary operation when only primary operation is in application"
               (get-operations-from-application mock-application-with-primary-operation-only) => [primary-operation])

         (fact "returns vector contaning primary operation and secondary operations when we have primary operation and secondary operation"
               (get-operations-from-application mock-application-with-primary-operation-and-secondary-operations) => [primary-operation secondary-operation secondary-operation])

         (fact "returns vector contaning only primary operation when we have primary operation and secondary operation is empty vec"
               (get-operations-from-application mock-application-with-primary-operation-and-empty-secondary-operations) => [primary-operation])

         (fact "returns vector contaning only primary operation when we have primary operation and secondary operation is nil"
               (get-operations-from-application mock-application-with-primary-operation-and-nil-secondary-operations) => [primary-operation])))
