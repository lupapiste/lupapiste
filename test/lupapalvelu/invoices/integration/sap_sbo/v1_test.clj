(ns lupapalvelu.invoices.integration.sap-sbo.v1-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.invoices.integration.sap-sbo.v1 :as sap]
            [sade.util :as util]
            [sade.xml :as xml]
            [schema.core :as sc])
  (:import [lupapalvelu.sap_sbo.v1 SBOSalesOrderContainerType]))

(set! *warn-on-reflection* true)

(use-fixtures :once (fn [test-fn] (sc/with-fn-validation
                                    (test-fn))))

(deftest empty-xml-test
  (let [empty-orders [sap/empty-sales-order]]
    (is (string? (-> (.createSBOSalesOrderContainer
                       sap/factory
                       (-> (SBOSalesOrderContainerType/builder)
                           (.addSBOSalesOrder ^Iterable empty-orders)
                           .build))
                     (sap/doc->xml))))))

(def str->xml->edn (comp util/strip-empty-maps
                         util/strip-nils
                         xml/xml->edn
                         xml/parse))

(deftest generate-xml-test
  (let [lang     :fi
        config   {:constants {:jakelutie      "12"
                              :laskuttaja     "ID123"
                              :myyntiorg      "3"
                              :myyntitoimisto "2"
                              :sektori        "34"
                              :tilauslaji     "1"}}
        invoices [{:backend-id        "0123456789"
                   :billing-reference "5432"
                   :city              "Tampere"
                   :payer-type        "person"
                   :postal-code       "10000"
                   :rows              [{:code              "12345678"
                                        :price-per-unit    1.23
                                        :product-constants {:projekti "1234567890123456"}
                                        :text              ""
                                        :unit              "kpl"
                                        :units             5}]
                   :sap-number        "0123456789"}]]
    ;; error case are tested in `lupapalvelu.invoices.integration.sap-sbo.sap-common-test`
    (testing "Happy paths"
      (testing "person with sap-number"
        (let [result (sap/->xml config invoices lang)]
          (is (string? result))
          (when (string? result)
            (is (= {:SBO_SalesOrderContainer {:SBO_SalesOrder {:DistributionChannel "12"
                                                               :Division            "34"
                                                               :LineItem            {:Material    "12345678"
                                                                                     :NetPrice    "1.23"
                                                                                     :Quantity    "5.000"
                                                                                     :Unit        "kpl"
                                                                                     :WBS_Element "1234567890123456"}
                                                               :OrderParty          {:PriorityCity       "Tampere"
                                                                                     :PriorityPostalcode "10000"
                                                                                     :SAPCustomerID      "0123456789"}
                                                               :OrderType           "1"
                                                               :PONumber            "5432"
                                                               :Reference           "0123456789"
                                                               :SalesOffice         "2"
                                                               :SalesOrg            "3"
                                                               :SenderId            "ID123"}}}
                   (str->xml->edn result))))))
      (testing "person with person-id"
        (let [invoices (update invoices 0 (fn [invoice]
                                            (-> invoice
                                                (dissoc :sap-number)
                                                (assoc :person-id "010107A941L"))))
              result   (sap/->xml config invoices lang)]
          (is (string? result))
          (when (string? result)
            (is (= {:SBO_SalesOrderContainer {:SBO_SalesOrder {:DistributionChannel "12"
                                                               :Division            "34"
                                                               :LineItem            {:Material    "12345678"
                                                                                     :NetPrice    "1.23"
                                                                                     :Quantity    "5.000"
                                                                                     :Unit        "kpl"
                                                                                     :WBS_Element "1234567890123456"}
                                                               :OrderParty          {:CustomerID         "010107A941L"
                                                                                     :PriorityCity       "Tampere"
                                                                                     :PriorityPostalcode "10000"}
                                                               :OrderType           "1"
                                                               :PONumber            "5432"
                                                               :Reference           "0123456789"
                                                               :SalesOffice         "2"
                                                               :SalesOrg            "3"
                                                               :SenderId            "ID123"}}}
                   (str->xml->edn result))))))
      (testing "person with person-id and full details"
        (let [invoices (update invoices 0 (fn [invoice]
                                            (-> invoice
                                                (dissoc :sap-number)
                                                (assoc :person-id "010107A941L"
                                                       :first-name "FIRST"
                                                       :last-name "LAST"
                                                       :entity-address "Testikatu 1."
                                                       :description "DESCRIPTION")
                                                (update-in [:rows 0] assoc :text "ROW-TEXT"))))
              result   (sap/->xml config invoices lang)]
          (is (string? result))
          (when (string? result)
            (is (= {:SBO_SalesOrderContainer {:SBO_SalesOrder {:BillText            "DESCRIPTION"
                                                               :DistributionChannel "12"
                                                               :Division            "34"
                                                               :LineItem            {:Material            "12345678"
                                                                                     :MaterialDescription "ROW-TEXT"
                                                                                     :NetPrice            "1.23"
                                                                                     :Quantity            "5.000"
                                                                                     :Unit                "kpl"
                                                                                     :WBS_Element         "1234567890123456"}
                                                               :OrderParty          {:CustomerID         "010107A941L"
                                                                                     :PriorityAddress1   "Testikatu 1."
                                                                                     :PriorityCity       "Tampere"
                                                                                     :PriorityName1      "LAST, FIRST"
                                                                                     :PriorityPostalcode "10000"}
                                                               :OrderType           "1"
                                                               :PONumber            "5432"
                                                               :Reference           "0123456789"
                                                               :SalesOffice         "2"
                                                               :SalesOrg            "3"
                                                               :SenderId            "ID123"}}}
                   (str->xml->edn result))))))
      (testing "company with sap-number"
        (let [invoices (update invoices 0 (fn [invoice]
                                            (-> invoice
                                                (assoc :payer-type "company"))))
              result   (sap/->xml config invoices lang)]
          (is (string? result))
          (when (string? result)
            (is (= {:SBO_SalesOrderContainer {:SBO_SalesOrder {:DistributionChannel "12"
                                                               :Division            "34"
                                                               :LineItem            {:Material    "12345678"
                                                                                     :NetPrice    "1.23"
                                                                                     :Quantity    "5.000"
                                                                                     :Unit        "kpl"
                                                                                     :WBS_Element "1234567890123456"}
                                                               :OrderParty          {:PriorityCity       "Tampere"
                                                                                     :PriorityPostalcode "10000"
                                                                                     :SAPCustomerID      "0123456789"}
                                                               :OrderType           "1"
                                                               :PONumber            "5432"
                                                               :Reference           "0123456789"
                                                               :SalesOffice         "2"
                                                               :SalesOrg            "3"
                                                               :SenderId            "ID123"}}}
                   (str->xml->edn result))))))
      (testing "company with y-tunnus"
        (let [invoices (update invoices 0 (fn [invoice]
                                            (-> invoice
                                                (dissoc :sap-number)
                                                (assoc :payer-type "company"
                                                       :company-id "1234567-8"))))
              result   (sap/->xml config invoices lang)]
          (is (string? result))
          (when (string? result)
            (is (= {:SBO_SalesOrderContainer {:SBO_SalesOrder {:DistributionChannel "12"
                                                               :Division            "34"
                                                               :LineItem            {:Material    "12345678"
                                                                                     :NetPrice    "1.23"
                                                                                     :Quantity    "5.000"
                                                                                     :Unit        "kpl"
                                                                                     :WBS_Element "1234567890123456"}
                                                               :OrderParty          {:CustomerYID        "1234567-8"
                                                                                     :PriorityCity       "Tampere"
                                                                                     :PriorityPostalcode "10000"}
                                                               :OrderType           "1"
                                                               :PONumber            "5432"
                                                               :Reference           "0123456789"
                                                               :SalesOffice         "2"
                                                               :SalesOrg            "3"
                                                               :SenderId            "ID123"}}}
                   (str->xml->edn result)))))))))
