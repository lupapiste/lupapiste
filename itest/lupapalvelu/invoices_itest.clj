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
            [sade.env :as env]
            [taoensso.timbre :refer [trace tracef debug info infof warn warnf error errorf fatal spy]]))

(def dummy-user {:id                                        "penan-id"
                 :firstName                                 "pena"
                 :lastName                                  "panaani"
                 :role                                      "authority"
                 :email                                     "pena@panaani.fi"
                 :username                                  "pena"})

(def dummy-invoice {:application-id "LP-753-2018-90108"
                       :organization-id "753-R"
                       :state "draft"
                       :operations [{:operation-id "linjasaneeraus"
                                     :name "linjasaneeraus"
                                     :invoice-rows [{:text "Laskurivi1 kpl"
                                                     :type "from-price-catalogue"
                                                     :unit "kpl"
                                                     :price-per-unit 10
                                                     :units 2
                                                     :discount-percent 0}]}]})

(defn invoice-with [properties]
  (merge dummy-invoice properties))

(defn application-with [properties]
  (let [dummy-application {:id "APP-ID-1"
                           :organization-id "HC_ORG"
                           :state "draft"}]
    (merge dummy-application properties)))

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
                                             :invoice-rows [{:text "Row 1 kpl"
                                                             :type "from-price-catalogue"
                                                             :unit "kpl"
                                                             :price-per-unit 10
                                                             :units 2
                                                             :discount-percent 0}
                                                            {:text "Row 2 m2"
                                                             :type "from-price-catalogue"
                                                             :unit "m2"
                                                             :price-per-unit 20.5
                                                             :units 15.8
                                                             :discount-percent 50}
                                                            {:text "Custom row m3"
                                                             :type "custom"
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
                                                             :type "from-price-catalogue"
                                                             :unit "kpl"
                                                             :price-per-unit 10
                                                             :units 2
                                                             :discount-percent 0}
                                                            {:text "Laskurivi2 m3 "
                                                             :type "from-price-catalogue"
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
                                                                   :type "from-price-catalogue"
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
                                                                    :type "custom"
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
                                                                   :type "from-price-catalogue"
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
                      count) => 3)))

    (fact "fetch-invoice query"
          (fact "sould return invoice when invoice is inserted"
                (let [draft-invoice (invoice-with {:state "draft"})
                      {app-a-id :id :as app-a} (dummy-submitted-application)
                      inserted-invoice-id (invoices/create-invoice! (->invoice-db draft-invoice app-a dummy-user))
                      {invoice-from-query :invoice} (local-query sonja :fetch-invoice :id app-a-id :invoice-id inserted-invoice-id)]
                  (:application-id invoice-from-query) => app-a-id)))

    (fact "application-operations-query"
          (fact "should return vector containing primary operation"
                (let [{:keys [id] :as app} (dummy-submitted-application)]
                  (-> (local-query sonja :application-operations :id id)
                      :operations
                      count) => 1

                  (-> (local-query sonja :application-operations :id id)
                      :operations
                      first
                      :name) => "pientalo")))


    (fact "user-orgnizations-invoices"
          (fact "should return empty seq when there's no invoices for any organization"
                (with-redefs [invoices/get-user-orgs-having-role (fn [user role] [])]
                  (let [result (local-query sonja :user-organizations-invoices)]
                    (:invoices result) => [])))

          (fact "should the invoices for orgs user has the role in"


                (with-redefs [invoices/get-user-orgs-having-role (fn [user role] ["USER-ORG-1" "USER-ORG-2"])]

                  (invoices/create-invoice! (->invoice-db dummy-invoice (application-with {:organization "USER-ORG-1"}) dummy-user))
                  (invoices/create-invoice! (->invoice-db dummy-invoice (application-with {:organization "FOO-ORG"}) dummy-user))
                  (invoices/create-invoice! (->invoice-db dummy-invoice (application-with {:organization "USER-ORG-2"}) dummy-user))
                  (invoices/create-invoice! (->invoice-db dummy-invoice (application-with {:organization "BAR-ORG"}) dummy-user))

                  (let [invoices (:invoices (local-query sonja :user-organizations-invoices))
                        invoice-orgs (set (map :organization-id invoices))]
                    (count invoices) => 2
                    invoice-orgs => #{"USER-ORG-1" "USER-ORG-2"}))))))
