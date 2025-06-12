(ns lupapalvelu.invoices.integration.ya-sap.core-test
  (:require [lupapalvelu.invoices.integration.ya-sap.core]
            [lupapalvelu.invoices.schemas :refer [Invoice]]
            [lupapalvelu.invoices.util :as invoices-util]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [schema.core :as sc]))

(testable-privates lupapalvelu.invoices.integration.ya-sap.core
                   ->idoc-invoice-data
                   filename-for-xml-sap)

(fact "filenames"
  (filename-for-xml-sap nil 1560772700083)
  => "ID334_Lupapiste_17.6.2019_14.58.20_1560772700083.xml"
  (filename-for-xml-sap  {:invoice-file-prefix "MySystem--"}
                         1560772700083)
  => "MySystem--17.6.2019_14.58.20_1560772700083.xml")

(def dummy-product-constants {:kustannuspaikka  "a"
                              :alv              "b"
                              :laskentatunniste "c"
                              :projekti         "d"
                              :kohde            "e"
                              :toiminto         "f"
                              :muu-tunniste     "g"})

(def by-bob {:id        "bob-id"
             :username  "bob@example.com"
             :firstName "Robert"
             :lastName  "Sample"
             :role      "applicant"})

(def org-config {:sap-integration-id      "110"
                 :sap-ordertype           "ZLUP"
                 :sap-salesorganization   "1111"
                 :sap-distributionchannel "00"
                 :sap-division            "00"
                 :permit-id               "25106"
                 :sap-term-id             "2106"
                 :sap-materialid          "000000000000502557"
                 :sap-plant               "1111"
                 :sap-profitcenter        "0000111886"})

(def application {:id "LP-753-2019-12345"
                  :address          "Latokuja 3"
                  :primaryOperation {:name "ya-katulupa-veri-ja-viemarityot"}})

(def op1-id (mongo/create-id))

(def invoice {:id              "ivoice-id"
              :organization-id "753-R"
              :state           "checked"
              :application-id  "LP-753-2019-12345"
              :description     "Dummy Description"
              :created         1664571600000
              :created-by      by-bob
              :operations      [{:id   op1-id
                                 :name "linjasaneeraus"}]
              :rows            [{:id                "row1"
                                 :operation-id      op1-id
                                 :text              "Kadun puhkurointi"
                                 :unit              "kpl"
                                 :price-per-unit    10
                                 :units             2
                                 :discount-percent  0
                                 :product-constants dummy-product-constants}
                                {:id               "row2"
                                 :operation-id     op1-id
                                 :text             "Pientareen rompulointi"
                                 :unit             "kpl"
                                 :price-per-unit   50
                                 :units            1
                                 :discount-percent 0}]})

(defn make-invoice
  [& kvs]
  (invoices-util/enrich-invoice
    (sc/validate Invoice (merge invoice (apply hash-map kvs)))
    application))

(def bill-date-ts  1559120400000) ;; 29.5.2019 12:00 Finnish time

(facts "->idoc-invoice-data"
  (sc/with-fn-validation
    (let [base {:sap-integration-id      "110"
                :sap-ordertype           "ZLUP"
                :sap-salesorganization   "1111"
                :sap-distributionchannel "00"
                :sap-division            "00"
                :permit-id               "LP-753-2019-12345"
                :operation               "ya-katulupa-veri-ja-viemarityot"
                :sap-term-id             "2106" ;;vakio
                :description             "Dummy Description"
                :target                  {:street "Latokuja 3"}
                :sap-bill-date           1559120400000
                :customer                {}
                :invoice-rows            [{:sap-materialid   "000000000000502557"
                                           :sap-plant        "1111"
                                           :sap-profitcenter "0000111886"
                                           :quantity         2
                                           :unitprice        10
                                           :text             "Kadun puhkurointi"}
                                          {:sap-materialid   "000000000000502557"
                                           :sap-plant        "1111"
                                           :sap-profitcenter "0000111886"
                                           :quantity         1
                                           :unitprice        50
                                           :text             "Pientareen rompulointi"}]}]
      (fact "No sap-number, no billing-reference"
        (->idoc-invoice-data org-config bill-date-ts (make-invoice)) => base
        (->idoc-invoice-data org-config bill-date-ts
                             (make-invoice :sap-number "  "
                                           :billing-reference "  "))
        => base)
      (fact "Sap-number and billing-reference"
        (->idoc-invoice-data org-config bill-date-ts
                             (make-invoice :sap-number "sap-123"
                                           :billing-reference "show-me-the-money"))
        => (merge base
                  {:customer       {:sap-number "sap-123"}
                   :your-reference "show-me-the-money"})))))
