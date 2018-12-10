(ns lupapalvelu.invoices.transfer-batch-itest
  (:require [lupapalvelu.invoices.transfer-batch :refer [add-invoice-to-transfer-batch ->invoice-transfer-batch-db transfer-batch-db-key get-or-create-invoice-transfer-batch-for-org]]
            [midje.sweet :refer :all]
            [lupapalvelu.invoices :refer [validate-invoice] :as invoices]
            [lupapalvelu.invoices.schemas :refer [->invoice-db]]
            [lupapalvelu.itest-util :refer [local-command local-query
                                            create-and-submit-application
                                            create-and-submit-local-application
                                            sonja pena ok? fail?] :as itu]
            [sade.env :as env]
            [sade.core :as sade]
            [lupapalvelu.mongo :as mongo]))


(def dummy-user {:id        "penan-id"
                 :firstName "pena"
                 :lastName  "panaani"
                 :role      "authority"
                 :email     "pena@panaani.fi"
                 :username  "pena"})

(def dummy-invoice {:id "dummy-invoice-id-1"
                    :application-id "LP-753-2018-90108"
                    :organization-id "753-R"
                    :sum {:major 20
                          :minor 2000
                          :currency "EUR"
                          :text "EUR20.00"}

                    :state "draft"
                    :operations [{:operation-id "linjasaneeraus"
                                  :name "linjasaneeraus"
                                  :invoice-rows [{:text "Laskurivi1 kpl"
                                                  :type "from-price-catalogue"
                                                  :unit "kpl"
                                                  :price-per-unit 10
                                                  :units 2
                                                  :discount-percent 0}]}]})

(env/with-feature-value :invoices true
  (mongo/connect!)

  (mongo/with-db itu/test-db-name
    (lupapalvelu.fixture.core/apply-fixture "invoicing-enabled")

    (defn get-transfer-batch-db [id]
      (mongo/by-id transfer-batch-db-key id))

    (fact "create-invoice-transferbatch-for-org"
          (fact "Create new transferbatch for org if one does not exists"
                (let [org-id "FOO-ORG"
                      transfer-batch-id (get-or-create-invoice-transfer-batch-for-org org-id dummy-user)
                      fetched-tb (get-transfer-batch-db transfer-batch-id)]
                  (:organization-id fetched-tb) => org-id))
          (fact "Return existing transferbatch for organization if it exists and has enough rows"
                (let [org-id "FOO-ORG"
                      transfer-batch-id (get-or-create-invoice-transfer-batch-for-org org-id dummy-user)
                      transfer-batch-id-is-same (get-or-create-invoice-transfer-batch-for-org org-id dummy-user)]
                  transfer-batch-id => transfer-batch-id-is-same)))

    (fact "add-invoice-to-invoice-transfer-batch"
          (let [invoice-one-id (mongo/create-id)
                invoice-one (merge dummy-invoice {:id invoice-one-id})]
            (mongo/insert :invoices invoice-one)
            (fact "when dummy-invoice-is-inserted to transfer batch, when organization doesn't have existing ones, a new one is created, with dummy invoice and number of rows is 1"
                  (let [now 12345]
                    (with-redefs [sade/now (fn [] now)]
                      (let [transfer-batch-id (add-invoice-to-transfer-batch invoice-one dummy-user)
                            transfer-batch-from-db (get-transfer-batch-db transfer-batch-id)
                            now 12345]

                        (first (:invoices transfer-batch-from-db)) =>
                        {:id invoice-one-id
                         :organization-id (:organization-id invoice-one)
                         :added-to-transfer-batch now}
                        (:organization-id transfer-batch-from-db) => (:organization-id invoice-one)
                        (:number-of-rows transfer-batch-from-db) => 1))))
            (fact "when another invoice is added with 3 rows, number of rows in 4, when existing invoice had 1 row"
                  (let [two-operations-with-total-3-rows  {:operations [{:operation-id "linjasaneeraus KAKKONEN"
                                                                         :name "linjasaneeraus kakkonen"
                                                                         :invoice-rows [{:text "Laskurivi1 kpl"
                                                                                         :type "from-price-catalogue"
                                                                                         :unit "kpl"
                                                                                         :price-per-unit 10
                                                                                         :units 2
                                                                                         :discount-percent 0}]}
                                                                        {:operation-id "joku muu"
                                                                         :name "joku muu"
                                                                         :invoice-rows [{:text "Laskurivi1 kpl"
                                                                                         :type "from-price-catalogue"
                                                                                         :unit "kpl"
                                                                                         :price-per-unit 10
                                                                                         :units 2
                                                                                         :discount-percent 0}
                                                                                        {:text "Laskurivi1 kpl"
                                                                                         :type "from-price-catalogue"
                                                                                         :unit "kpl"
                                                                                         :price-per-unit 10
                                                                                         :units 2
                                                                                         :discount-percent 0}]}]}
                        invoice-two-id (mongo/create-id)
                        invoice-two (merge dummy-invoice {:id invoice-two-id} two-operations-with-total-3-rows)]
                    (mongo/insert :invoices invoice-two)
                    (let [transfer-batch-id (add-invoice-to-transfer-batch invoice-two dummy-user)
                          transfer-batch (get-transfer-batch-db transfer-batch-id)]
                      (:number-of-rows transfer-batch) => 4
                      (:sum transfer-batch) => {:currency "EUR" :major 40 :minor 4000 :text "EUR40.00"})))))))
