(ns lupapalvelu.invoices-itest
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.integrations-api]
            [lupapalvelu.itest-util :refer [local-command local-query
                                            create-and-submit-application
                                            create-and-submit-local-application
                                            sonja pena sipoo sipoo-ya admin
                                            ok? fail?] :as itu]
            [lupapalvelu.invoice-api]
            [lupapalvelu.invoices.schemas :refer [->invoice-db]]
            [lupapalvelu.invoices :refer [validate-invoice] :as invoices]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.env :as env]
            [sade.util :refer [to-millis-from-local-date-string]]
            [taoensso.timbre :refer [trace tracef debug info infof warn warnf error errorf fatal spy]]
            [lupapalvelu.price-catalogues :as catalogues]))

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

(defn err [error]
  (partial itu/expected-failure? error))

(defn toggle-invoicing [flag]
  (local-command admin :update-organization
               :invoicingEnabled flag
               :municipality "753"
               :permitType "R"))

(env/with-feature-value :invoices true
  (mongo/connect!)

  (mongo/with-db itu/test-db-name
    (lupapalvelu.fixture.core/apply-fixture "minimal")

    (fact "Invoicing not enabled for 753-R"
      (local-query sipoo :user-organizations-invoices)
      => (err :error.invoicing-disabled)
      (local-query sipoo :organization-price-catalogues
             :organization-id "753-R")
      => (err :error.invoicing-disabled)
      (local-query sipoo :organizations-transferbatches)
      => (err :error.invoicing-disabled))

    (fact "Enable invoicing for 753-R"
      (toggle-invoicing true) => ok?
      (local-query sipoo :user-organizations-invoices) => ok?)

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
                  (validate-invoice  (mongo/by-id "invoices" invoice-id))
                  (fact "Disable invoicing"
                    (toggle-invoicing false) => ok?
                    (local-command sonja :insert-invoice
                                   :id id
                                   :invoice invoice)
                    => (err :error.invoicing-disabled))
                  (fact "Enable invoicing"
                    (toggle-invoicing true) => ok?)))

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
                                                                    :discount-percent 10
                                                                    :sums {:with-discount {:currency "EUR"
                                                                                           :major 45
                                                                                           :minor 4500
                                                                                           :text "EUR45.00"}
                                                                           :without-discount {:currency "EUR"
                                                                                              :major 50
                                                                                              :minor 5000
                                                                                              :text "EUR50.00"}}}]}]}]
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


    (fact "user-organizations-invoices"
          (fact "should return empty seq when there's no invoices for any organization"
                (with-redefs [invoices/get-user-orgs-having-role (fn [user role] [])]
                  (let [result (local-query sonja :user-organizations-invoices)]
                    (:invoices result) => [])))

          (fact "should return invoices for orgs user has the role in"

                (with-redefs [invoices/get-user-orgs-having-role (fn [user role] ["USER-ORG-1" "USER-ORG-2"])]

                  (invoices/create-invoice! (->invoice-db dummy-invoice (application-with {:organization "USER-ORG-1"}) dummy-user))
                  (invoices/create-invoice! (->invoice-db dummy-invoice (application-with {:organization "FOO-ORG"}) dummy-user))
                  (invoices/create-invoice! (->invoice-db dummy-invoice (application-with {:organization "USER-ORG-2"}) dummy-user))
                  (invoices/create-invoice! (->invoice-db dummy-invoice (application-with {:organization "BAR-ORG"}) dummy-user))

                  (let [invoices (:invoices (local-query sonja :user-organizations-invoices))
                        invoice-orgs (set (map :organization-id invoices))]
                    (count invoices) => 2
                    invoice-orgs => #{"USER-ORG-1" "USER-ORG-2"})))

          (fact "should return invoice with"
                (let [{org-id :organization app-id :id :as application} (create-and-submit-local-application pena
                                                                                                             :address "Kukkuja 7")
                      invoice (invoice-with {:organization-id org-id
                                             :application-id app-id})
                      invoice-id (invoices/create-invoice! (->invoice-db invoice application dummy-user))]

                  (let [result (local-query sonja :user-organizations-invoices)
                        invoices (:invoices result)
                        invoice (invoices/get-doc invoice-id invoices)]

                    (fact "organisation data enriched to it"
                          (get-in invoice [:enriched-data :organization]) => {:name {:fi "Sipoon rakennusvalvonta"
                                                                                     :en "Sipoon rakennusvalvonta"
                                                                                     :sv "Sipoon rakennusvalvonta"}})
                    (fact "application data enriched to it"
                          (get-in invoice [:enriched-data :application]) => {:address "Kukkuja 7"})))))


    (fact "organizations-transferbatches"
          (fact "Should return transferbatch with one invoice when invoice is transferred to confirmed"
                (defn invoice->confirmed [draft-invoice]
                  (let [{:keys [id] :as app} (dummy-submitted-application)
                        new-invoice-id (:invoice-id (local-command sonja :insert-invoice :id id :invoice draft-invoice))
                        new-invoice (:invoice (local-query sonja :fetch-invoice :id id :invoice-id new-invoice-id))]
                    (local-command sonja :update-invoice :id id :invoice (assoc new-invoice  :state "checked"))
                    (local-command sonja :update-invoice :id id :invoice (assoc new-invoice  :state "confirmed"))))
                (let [invoice {:operations [{:operation-id "linjasaneeraus"
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
                                                             :discount-percent 100}]}]}]
                  (invoice->confirmed invoice)
                  (let [transferbatch-result (:transfer-batches (local-query sonja :organizations-transferbatches))
                        org-transferbatch (get transferbatch-result "753-R")]
                    (:invoice-count (first org-transferbatch)) => 1
                    (:invoice-row-count (first org-transferbatch))=> 3
                    (:sum (:transfer-batch (first org-transferbatch))) => {:currency "EUR" :major 181 :minor 18195 :text "EUR181.95"}))))))
