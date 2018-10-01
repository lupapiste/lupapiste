(ns lupapalvelu.invoices-itest
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.integrations-api]
            [lupapalvelu.itest-util :refer :all :as itu]
            [lupapalvelu.invoice-api]
            [lupapalvelu.invoices :refer [validate-invoice] :as invoices]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.env :as env]))

(defn invoice-with [properties]
  (let [dummy-invoice {:application-id "LP-753-2018-90108"
                       :organization-id "753-R"
                       :state "draft"
                       :operations [{:operation-id "linjasaneeraus"
                                     :name "linjasaneeraus"
                                     :invoice-rows [{:text "Laskurivi1 kpl"
                                                     :unit "kpl"
                                                     :price-per-unit 10
                                                     :units 2}]}]}]
    (merge dummy-invoice properties)))

(env/with-feature-value :invoices true
  (mongo/connect!)

  (mongo/with-db itu/test-db-name
    (lupapalvelu.fixture.core/apply-fixture "minimal")

  (defn dummy-submitted-application []
    (itu/create-and-submit-local-application
     pena
     :operation "pientalo"
     :x "385770.46" :y "6672188.964"
     :address "Kaivokatu 1"
     ;;:propertyId "09143200010023"
     ))

    (fact "insert-invoice command"
          (fact "should add an invoice to the db with with all the required fields"
                (let [{:keys [id] :as app} (dummy-submitted-application)
                      invoice {:operations [{:operation-id "linjasaneeraus"
                                             :name "linjasaneeraus"
                                             :invoice-rows [{:text "Laskurivi1 kpl"
                                                             :unit "kpl"
                                                             :price-per-unit 10
                                                             :units 2}
                                                            {:text "Laskurivi2 m2 "
                                                             :unit "m2"
                                                             :price-per-unit 20.5
                                                             :units 15.8}
                                                            {:text "Laskurivi3 m3 "
                                                             :unit "m3"
                                                             :price-per-unit 20.5
                                                             :units 15.8}]}]}
                      {:keys [invoice-id]} (itu/local-command sonja :insert-invoice
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
                                                             :units 2}
                                                            {:text "Laskurivi2 m3 "
                                                             :unit "UNKOWN-UNIT"
                                                             :price-per-unit 20.5
                                                             :units 15.8}]}]}]
                  (itu/local-command sonja :insert-invoice :id id :invoice invoice) => fail?)))

    (fact "update-invoice command"
          (fact "with the role authority"
                (fact "should update the operations of an existing invoice"
                      (let [{:keys [id] :as app} (dummy-submitted-application)
                            invoice {:operations [{:operation-id "linjasaneeraus"
                                                   :name "linjasaneeraus"
                                                   :invoice-rows [{:text "Laskurivi1 kpl"
                                                                   :unit "kpl"
                                                                   :price-per-unit 10
                                                                   :units 2}]}]}
                            {:keys [invoice-id]} (itu/local-command sonja :insert-invoice
                                                                    :id id
                                                                    :invoice invoice) => ok?
                            new-data {:id invoice-id
                                      :operations [{:operation-id "sisatila-muutos"
                                                    :name "sisatila-muutos"
                                                    :invoice-rows [{:text "Laskurivi1 m2"
                                                                    :unit "m2"
                                                                    :price-per-unit 5
                                                                    :units 10}]}]}
                            ]
                        (itu/local-command sonja :update-invoice :id id :invoice new-data) => ok?
                        (let [updated-invoice (mongo/by-id "invoices" invoice-id)]
                          (:operations updated-invoice) => (:operations new-data))))

                (fact "should"
                      (let [{:keys [id] :as app} (dummy-submitted-application)
                            invoice {:operations [{:operation-id "linjasaneeraus"
                                                   :name "linjasaneeraus"
                                                   :invoice-rows [{:text "Laskurivi1 kpl"
                                                                   :unit "kpl"
                                                                   :price-per-unit 10
                                                                   :units 2}]}]}
                            {:keys [invoice-id]} (itu/local-command sonja :insert-invoice
                                                                    :id id
                                                                    :invoice invoice) => ok?]
                        (fact "update the state from draft to checked"
                              (itu/local-command sonja :update-invoice
                                                 :id id
                                                 :invoice {:id invoice-id
                                                           :state "checked"}) => ok?
                              (:state (mongo/by-id "invoices" invoice-id)) => "checked")

                        (fact "update the state from checked to draft"
                              (itu/local-command sonja :update-invoice
                                                 :id id
                                                 :invoice {:id invoice-id
                                                           :state "draft"}) => ok?
                              (:state (mongo/by-id "invoices" invoice-id)) => "draft")))))

    (fact "application-invoices query"
          (fact "should return an empty collection of invoices when none found for the application"
                (let [{app-a :id} (dummy-submitted-application)]
                  (-> (itu/local-query sonja :application-invoices :id app-a)
                      :invoices
                      count) => 0))

          (fact "should fetch all application invoices"
                (let [{app-a :id} (dummy-submitted-application)
                      {app-b :id} (dummy-submitted-application)
                      invoices [(invoice-with {:application-id app-a :state "draft"})
                                (invoice-with {:application-id app-a :state "draft"})
                                (invoice-with {:application-id app-b :state "draft"})
                                (invoice-with {:application-id app-b :state "draft"})
                                (invoice-with {:application-id app-b :state "draft"})]]
                  (doseq [invoice invoices] (invoices/create-invoice! invoice))

                  (-> (itu/local-query sonja :application-invoices :id app-a)
                      :invoices
                      count) => 2

                  (-> (itu/local-query sonja :application-invoices :id app-b)
                      :invoices
                      count) => 3)))
    ))
