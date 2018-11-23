(ns lupapalvelu.invoices-test
  (:require [lupapalvelu.invoices :refer [get-operations-from-application] :as invoices]
            [lupapalvelu.invoices.schemas :refer [->invoice-user ->invoice-db]]
            [midje.sweet :refer :all]
            [schema.core :as sc]
            [sade.core]
            [lupapalvelu.invoices :as invoices]))

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

(defn user-with [user-props]
  (merge {:id "777777777777777777000023"
          :username "sonja"
          :firstName "Sonja"
          :lastName "Sibbo"
          :role "authority"
          :email "sonja.sibbo@sipoo.fi"
          :orgAuthz {:753-R #{:authority :approver} :753-YA #{:authority :approver}}
          :language "fi"}
         user-props))

(facts "get-user-orgs-having-role"
       (fact "returns empty coll when user has not orgs"
             (invoices/get-user-orgs-having-role (user-with {:orgAuthz []}) "any-role") => [])

       (fact "returns empty coll when user does not have the given role in any org"
             (invoices/get-user-orgs-having-role  (user-with {:orgAuthz {:753-R #{:authority :approver}
                                                                         :753-YA #{:authority :approver}}}) "NON-EXISTING-ROLE") => [])

       (fact "returns org ids for orgs where user has the given role"
             (invoices/get-user-orgs-having-role (user-with {:orgAuthz {:753-R #{:authority :approver}
                                                                        :NO-AUTH-1 #{:approver}
                                                                        :753-YA #{:authority :approver}
                                                                        :NO-AUTH-2 #{:foobar}}})  "authority") => ["753-R" "753-YA"]))

(facts "sum-single-row"
       (fact "sums invoice-row with 1 unit of 10 to ten"
             (let [test-invoice-row {:text "Laskurivi1 kpl"
                                     :type "from-price-catalogue"
                                     :unit "kpl"
                                     :price-per-unit 10
                                     :units 1
                                     :discount-percent 0}
                   result (invoices/sum-single-row test-invoice-row)]

               (:without-discount result) => {:major 10
                                              :minor 1000
                                              :currency "EUR"
                                              :text "EUR10.00"}
               (:with-discount result) => {:major 10
                                           :minor 1000
                                           :currency "EUR"
                                           :text "EUR10.00"}))
       (fact "sums invoice-row with 2 unit of 10 to 20"
             (let [test-invoice-row {:text "Laskurivi1 kpl"
                                     :type "from-price-catalogue"
                                     :unit "kpl"
                                     :price-per-unit 10
                                     :units 2
                                     :discount-percent 0}
                   result (invoices/sum-single-row test-invoice-row)]

               (:without-discount result) => {:major 20
                                              :minor 2000
                                              :currency "EUR"
                                              :text "EUR20.00"}
               (:with-discount result) => {:major 20
                                           :minor 2000
                                           :currency "EUR"
                                           :text "EUR20.00"}))
       (fact "sums invoice-row with 2 unit of 10 to 20 with discount of 32% to 13.60 and w/o discount to 20"
             (let [test-invoice-row {:text "Laskurivi1 kpl"
                                     :type "from-price-catalogue"
                                     :unit "kpl"
                                     :price-per-unit 10
                                     :units 2
                                     :discount-percent 32}
                   result (invoices/sum-single-row test-invoice-row)]

               (:without-discount result) => {:major 20
                                              :minor 2000
                                              :currency "EUR"
                                              :text "EUR20.00"}
               (:with-discount result) => {:major 13
                                           :minor 1360
                                           :currency "EUR"
                                           :text "EUR13.60"})))

(facts "sum-invoice"
       (fact "sums invoice with rows worth 10 and 10 without discounts as 20"
             (let [test-invoice {:state "draft"
                                 :operations [{:operation-id "linjasaneeraus"
                                               :name "linjasaneeraus"
                                               :invoice-rows [{:text "Laskurivi1 kpl"
                                                               :type "from-price-catalogue"
                                                               :unit "kpl"
                                                               :price-per-unit 10
                                                               :units 1
                                                               :discount-percent 0}
                                                              {:text "Laskurivi1 kpl"
                                                               :type "from-price-catalogue"
                                                               :unit "kpl"
                                                               :price-per-unit 10
                                                               :units 1
                                                               :discount-percent 0}]}]}]
               (:sum (invoices/sum-invoice test-invoice)) => {:major 20
                                                              :minor 2000
                                                              :currency "EUR"
                                                              :text "EUR20.00"}))
       (fact "sums invoice with operation without invoices results in 0EUR"
             (let [test-invoice {:state "draft"
                                 :operations [{:operation-id "linjasaneeraus"
                                               :name "linjasaneeraus"
                                               :invoice-rows []}]}]
               (:sum (invoices/sum-invoice test-invoice)) => {:major 0
                                                              :minor 0
                                                              :currency "EUR"
                                                              :text "EUR0.00"}))
       (fact "sums invoice with rows worth 10 and 2 * 10 without discounts as 30"
             (let [test-invoice {:state "draft"
                                 :operations [{:operation-id "linjasaneeraus"
                                               :name "linjasaneeraus"
                                               :invoice-rows [{:text "Laskurivi1 kpl"
                                                               :type "from-price-catalogue"
                                                               :unit "kpl"
                                                               :price-per-unit 10
                                                               :units 1
                                                               :discount-percent 0}
                                                              {:text "Laskurivi1 kpl"
                                                               :type "from-price-catalogue"
                                                               :unit "kpl"
                                                               :price-per-unit 10
                                                               :units 2
                                                               :discount-percent 0}]}]}]
               (:sum (invoices/sum-invoice test-invoice)) => {:major 30
                                                              :minor 3000
                                                              :currency "EUR"
                                                              :text "EUR30.00"}))
       (fact "sums invoice with rows worth 10 and 2 * 10  and 10 without discounts as 40 when in two operatons"
             (let [test-invoice {:state "draft"
                                 :operations [{:operation-id "linjasaneeraus"
                                               :name "linjasaneeraus"
                                               :invoice-rows [{:text "Laskurivi1 kpl"
                                                               :type "from-price-catalogue"
                                                               :unit "kpl"
                                                               :price-per-unit 10
                                                               :units 1
                                                               :discount-percent 0}
                                                              {:text "Laskurivi1 kpl"
                                                               :type "from-price-catalogue"
                                                               :unit "kpl"
                                                               :price-per-unit 10
                                                               :units 2
                                                               :discount-percent 0}]}
                                              {:operation-id "rakennuksen-purkaminen"
                                               :name "rakennuksen purkaminen"
                                               :invoice-rows [{:text "Laskurivi1 kpl"
                                                               :type "from-price-catalogue"
                                                               :unit "kpl"
                                                               :price-per-unit 10
                                                               :units 1
                                                               :discount-percent 0}
                                                              ]}]}]
               (:sum (invoices/sum-invoice test-invoice)) => {:major 40
                                                              :minor 4000
                                                              :currency "EUR"
                                                              :text "EUR40.00"}))
       (fact "sums invoice with rows worth 10 and 2 * 10  and 2* 10 with discount 20 without discounts as 40 when in two operatons"
             (let [test-invoice {:state "draft"
                                 :operations [{:operation-id "linjasaneeraus"
                                               :name "linjasaneeraus"
                                               :invoice-rows [{:text "Laskurivi1 kpl"
                                                               :type "from-price-catalogue"
                                                               :unit "kpl"
                                                               :price-per-unit 10
                                                               :units 1
                                                               :discount-percent 0}
                                                              {:text "Laskurivi1 kpl"
                                                               :type "from-price-catalogue"
                                                               :unit "kpl"
                                                               :price-per-unit 10
                                                               :units 2
                                                               :discount-percent 20}]}
                                              {:operation-id "rakennuksen-purkaminen"
                                               :name "rakennuksen purkaminen"
                                               :invoice-rows [{:text "Laskurivi1 kpl"
                                                               :type "from-price-catalogue"
                                                               :unit "kpl"
                                                               :price-per-unit 10
                                                               :units 1
                                                               :discount-percent 0}
                                                              ]}]}]
               (:sum (invoices/sum-invoice test-invoice)) => {:major 36
                                                              :minor 3600
                                                              :currency "EUR"
                                                              :text "EUR36.00"})))
