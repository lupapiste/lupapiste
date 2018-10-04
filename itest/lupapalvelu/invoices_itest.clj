(ns lupapalvelu.invoices-itest
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.integrations-api]
            [lupapalvelu.itest-util :refer [local-command local-query
                                            create-and-submit-local-application
                                            sonja pena ok? fail?] :as itu]
            [lupapalvelu.invoice-api]
            [lupapalvelu.invoices :refer [validate-invoice ->invoice-db] :as invoices]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.env :as env]))

(def dummy-user {:id                                        "penan-id"
                 :firstName                                 "pena"
                 :lastName                                  "panaani"
                 :role                                      "authority"
                 :email                                     "pena@panaani.fi"
                 :username                                  "pena"})

(defn invoice-with [properties]
  (let [dummy-invoice {:application-id "LP-753-2018-90108"
                       :organization-id "753-R"
                       :state "draft"
                       :operations [{:operation-id "linjasaneeraus"
                                     :name "linjasaneeraus"
                                     :invoice-rows [{:text "Laskurivi1 kpl"
                                                     :unit "kpl"
                                                     :price-per-unit 10
                                                     :units 2
                                                     :discount-percent 0}]}]}]
    (merge dummy-invoice properties)))

(env/with-feature-value :invoices true
  (mongo/connect!)

  (mongo/with-db itu/test-db-name
    (lupapalvelu.fixture.core/apply-fixture "minimal")

  (defn dummy-submitted-application []
    (create-and-submit-local-application
     pena
     :operation "pientalo"
     :x "385770.46" :y "6672188.964"
     :address "Kaivokatu 1"))

    (fact "insert-invoice command"
          (fact "should add an invoice to the db with with all the required fields"
                (let [{:keys [id] :as app} (dummy-submitted-application)
                      invoice {:operations [{:operation-id "linjasaneeraus"
                                             :name "linjasaneeraus"
                                             :invoice-rows [{:text "Laskurivi1 kpl"
                                                             :unit "kpl"
                                                             :price-per-unit 10
                                                             :units 2
                                                             :discount-percent 0}
                                                            {:text "Laskurivi2 m2 "
                                                             :unit "m2"
                                                             :price-per-unit 20.5
                                                             :units 15.8
                                                             :discount-percent 50}
                                                            {:text "Laskurivi3 m3 "
                                                             :unit "m3"
                                                             :price-per-unit 20.5
                                                             :units 15.8
                                                             :discount-percent 100}]}]}
                      {:keys [invoice-id]} (local-command sonja :insert-invoice
                                                              :id id
                                                              :invoice invoice) => ok?]
                  invoice-id => string?
                  (validate-invoice  (mongo/by-id "invoices" invoice-id))))

          (fact "should not create an invoice when one of the invoice-rows in the request has an unknown unit"
                (let [{:keys [id] :as app} (dummy-submitted-application)
                      invoice {:operations [{:operation-id "linjasaneeraus"
                                             :name "linjasaneeraus"
                                             :invoice-rows [{:text "Laskurivi1 kpl"
                                                             :unit "kpl"
                                                             :price-per-unit 10
                                                             :units 2
                                                             :discount-percent 0}
                                                            {:text "Laskurivi2 m3 "
                                                             :unit "UNKOWN-UNIT"
                                                             :price-per-unit 20.5
                                                             :units 15.8
                                                             :discount-percent 0}]}]}]
                  (local-command sonja :insert-invoice :id id :invoice invoice) => fail?)))

    (fact "update-invoice command"
          (fact "with the role authority"
                (fact "should update the operations of an existing invoice"
                      (let [{:keys [id] :as app} (dummy-submitted-application)
                            invoice {:operations [{:operation-id "linjasaneeraus"
                                                   :name "linjasaneeraus"
                                                   :invoice-rows [{:text "Laskurivi1 kpl"
                                                                   :unit "kpl"
                                                                   :price-per-unit 10
                                                                   :units 2
                                                                   :discount-percent 0}]}]}
                            {:keys [invoice-id]} (local-command sonja :insert-invoice
                                                                    :id id
                                                                    :invoice invoice) => ok?
                            new-data {:id invoice-id
                                      :operations [{:operation-id "sisatila-muutos"
                                                    :name "sisatila-muutos"
                                                    :invoice-rows [{:text "Laskurivi1 m2"
                                                                    :unit "m2"
                                                                    :price-per-unit 5
                                                                    :units 10
                                                                    :discount-percent 10}]}]}]
                        (local-command sonja :update-invoice :id id :invoice new-data) => ok?
                        (let [updated-invoice (mongo/by-id "invoices" invoice-id)]
                          (:operations updated-invoice) => (:operations new-data))))

                (fact "should"
                      (let [{:keys [id] :as app} (dummy-submitted-application)
                            invoice {:operations [{:operation-id "linjasaneeraus"
                                                   :name "linjasaneeraus"
                                                   :invoice-rows [{:text "Laskurivi1 kpl"
                                                                   :unit "kpl"
                                                                   :price-per-unit 10
                                                                   :units 2
                                                                   :discount-percent 0}]}]}
                            {:keys [invoice-id]} (local-command sonja :insert-invoice
                                                                    :id id
                                                                    :invoice invoice) => ok?]
                        (fact "update the state from draft to checked"
                              (local-command sonja :update-invoice
                                                 :id id
                                                 :invoice {:id invoice-id
                                                           :state "checked"}) => ok?
                              (:state (mongo/by-id "invoices" invoice-id)) => "checked")

                        (fact "update the state from checked to draft"
                              (local-command sonja :update-invoice
                                                 :id id
                                                 :invoice {:id invoice-id
                                                           :state "draft"}) => ok?
                              (:state (mongo/by-id "invoices" invoice-id)) => "draft")))))

    (fact "application-invoices query"
          (fact "should return an empty collection of invoices when none found for the application"
                (let [{app-a :id} (dummy-submitted-application)]
                  (-> (local-query sonja :application-invoices :id app-a)
                      :invoices
                      count) => 0))

          (fact "should fetch all application invoices"
                (let [draft-invoice (invoice-with {:state "draft"})
                      {app-a-id :id :as app-a} (dummy-submitted-application)
                      {app-b-id :id :as app-b} (dummy-submitted-application)
                      invoices [(->invoice-db draft-invoice app-a dummy-user)
                                (->invoice-db draft-invoice app-a dummy-user)

                                (->invoice-db draft-invoice app-b dummy-user)
                                (->invoice-db draft-invoice app-b dummy-user)
                                (->invoice-db draft-invoice app-b dummy-user)]]
                  (doseq [invoice invoices] (invoices/create-invoice! invoice))

                  (-> (local-query sonja :application-invoices :id app-a-id)
                      :invoices
                      count) => 2

                  (-> (local-query sonja :application-invoices :id app-b-id)
                      :invoices
                      count) => 3)))))
