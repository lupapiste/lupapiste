(ns lupapalvelu.invoices-test
  (:require [lupapalvelu.invoices :refer [->invoice-user ->invoice-db]]
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
