(ns lupapalvelu.invoices.excel-converter-test
  (:require [lupapalvelu.invoices.excel-converter :as excel]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.invoices.excel-converter
                   price-per-unit)

(facts "price-per-unit: integer or two decimals"
  (fact "Integer"
    (price-per-unit {:sums {:with-discount {:minor 8000}}
                     :units 20}) => 4)
  (fact "13/20 -> 0.65 (more decimals in excel for some reason) -> 0.65"
    (price-per-unit {:sums {:with-discount {:minor 81250}}
                     :units 1250}) => 0.65)

  (fact "1625/2518 -> 0.64535344 -> 0.65"
    (price-per-unit {:sums {:with-discount {:minor 81250}}
                     :units 1259})=> 0.65)
  (fact "325/504 -> 0.64484125 -> 0.64"
    (price-per-unit {:sums {:with-discount {:minor 81250}}
                     :units 1260}) => 0.64)
  (fact "Decimal units: 28.571428571428573 -> 28.57"
    (price-per-unit {:sums {:with-discount {:minor 10000}}
                     :units 3.5}) => 28.57)
  (fact "Zero units"
    (price-per-unit {:sums {:with-discount {:minor 0}}
                     :units 0}) => 0))

(def long-text "Lorem ipsum dolor sit amet, consectetur>< adipiscing elit. Quisque luctus mauris non pretium laoreet.")

;; Mocks
(def mongo-mock
  {:applications             {"app1" {:address "Luotikuja 12"}
                              "app2" {:address "Testikatu 33"}}
   :organizations            {"org1" {:invoicing-config {:constants {:jakelutie 123}}}}
   :invoice-transfer-batches {"batch1" {:invoices [{:id "invoice1"}
                                                   {:id "invoice2"}]}
                              "batch2" {:invoices []}}
   :invoices                 {"invoice1" {:application-id         "app1"
                                          :organization-id        "org1"
                                          :entity-name            "Jankon Betoni"
                                          :company-contact-person "Sonja Sibbo"
                                          :sap-number             "sap1"
                                          :operations             [{:invoice-rows
                                                                    [{:price-per-unit 2.5
                                                                      :code           "§2a"
                                                                      :units          11
                                                                      :text           "Betonilasku"
                                                                      :sums           {:with-discount {:minor (int (* 11 2.5 100))}}}]}]}
                              "invoice2" {:application-id    "app1"
                                          :organization-id   "org1"
                                          :entity-name       "Jankon Betonin Poika"
                                          :billing-reference "BILL-67890"
                                          :sap-number        "sap2"
                                          :operations        [{:invoice-rows
                                                               [{:price-per-unit 1
                                                                 :code           "§3a"
                                                                 :units          5
                                                                 :text           "Sementtilasku"
                                                                 :sums           {:with-discount {:minor 500}}}
                                                                {:price-per-unit   10
                                                                 :discount-percent 10
                                                                 :code             "§3b"
                                                                 :units            4
                                                                 :text             long-text
                                                                 :sums             {:with-discount {:minor (* 4 9 100)}}}]}]}}})

(defn mock-get-by-id
  "A function for mocking the mongo database; only supports vector projection"
  ([db id]
   (get-in mongo-mock [db id]))
  ([db id projection]
    (-> (get-in mongo-mock [db id])
        (select-keys projection))))

(defn mock-get-invoicing-config [organization-id]
  (get-in mongo-mock [:organizations organization-id :invoicing-config]))


;; Tests
(with-redefs [mongo/by-id              mock-get-by-id
              org/get-invoicing-config mock-get-invoicing-config]

  (facts "mongo-mock sanity tests"
    (fact "mongo/by-id"
      (:address (mongo/by-id :applications "app1" [:address])) => "Luotikuja 12"))

  (facts "workbook"
    (let [sheets   [{:name "sheet" :header '("a" "b" "c") :rows '([1 2 3] [4 5 6] [7 8 9])}]
          workbook (#'excel/make-workbook sheets)]
      (fact "workbook has correct number of sheets"
        (.getNumberOfSheets workbook) => 1)
      (fact "workbook has correctly placed cell b"
        (-> workbook
            (.getSheet "sheet")
            (.getRow 0)
            (.getCell 1)
            (.getStringCellValue)) => "b")
      (fact "workbook has correctly placed cell 4"
        (-> workbook
            (.getSheet "sheet")
            (.getRow 2)
            (.getCell 0)
            (.getNumericCellValue)) => 4.0)
      (fact "workbook has correctly placed cell 9"
        (-> workbook
            (.getSheet "sheet")
            (.getRow 3)
            (.getCell 2)
            (.getNumericCellValue)) => 9.0)
      (fact "workbook has correct number of headers"
        (-> workbook
            (.getSheet "sheet")
            (.getRow 0)
            (.getLastCellNum)) => 3)))

  (facts "get-organization-invoice-config"
    (fact "existing organization config"
      (#'excel/get-organization-invoice-config "org1")
      => (assoc excel/default-organization-constants :jakelutie 123))
    (fact "non-existing organization config"
      (#'excel/get-organization-invoice-config "org99")
      => excel/default-organization-constants))

  (facts "get-enriched-invoice-rows"
    (let [test-invoice (get-in mongo-mock [:invoices "invoice2"])
          common-data  (-> (dissoc test-invoice :operations)
                           (assoc :address "Luotikuja 12")
                           (merge excel/default-organization-constants {:jakelutie 123}))
          test-row1    (merge common-data (get-in test-invoice [:operations 0 :invoice-rows 0]))
          test-row2    (merge common-data (get-in test-invoice [:operations 0 :invoice-rows 1]))]
      (fact "invoice with 2 rows"
        (#'excel/get-enriched-invoice-rows test-invoice)
        => (list test-row1 test-row2))))

  (facts "get-invoice-excel-row-values"
    (let [test-row     {:units          123       :person-id       "041287-332V" :address    "Testikuja 234"
                        :application-id "LP-123"  :organization-id "org1"        :sap-number "sap1"
                        :code           "§1"
                        :text           long-text :entity-name     "ET"}
          test-columns [:t :qty :kpl :aardvark-camping-site? :asiakas :otsikkoteksti :otsikkomuistio
                        :riviteksti :rivimuistio]
          long-text    (str "§1 " long-text)
          text1        (subs long-text 0 40)
          text2        (subs long-text 40)]
      (fact "first row"
        (#'excel/get-invoice-excel-row-values (assoc test-row :first-row? true) test-columns)
        => [1 123 "" nil "sap1"
            (str "T\u00e4m\u00e4 maksu voidaan ulosmitata ilman tuomiota tai p\u00e4\u00e4t\u00f6st\u00e4 MRL 145 § 4 mom. "
                 "\"alv 0% viranomaistoiminta\""
                 "\nViite: ")
            "Liittyy lupaan LP-123, Testikuja 234" text1 text2])
      (fact "second row"
        (#'excel/get-invoice-excel-row-values test-row test-columns)
        => [0 123 "" nil nil nil nil text1 text2])
      (fact "Short text"
        (#'excel/get-invoice-excel-row-values {:text "Short text" :code "§1"} [:riviteksti :rivimuistio])
        => ["§1 Short text" nil])))

  (facts "fetch-transfer-batch-excel-data"
    (fact "no data on non-existing key"
      (#'excel/fetch-transfer-batch-excel-data "non-batch") => '())
    (fact "no data on batch without invoices"
      (#'excel/fetch-transfer-batch-excel-data "batch2") => '())
    (fact "normal functionality"
      (let [common-data (->> {:address   "Luotikuja 12" :application-id "app1" :organization-id "org1"
                              :jakelutie 123}
                             (conj excel/default-organization-constants))
            [concrete cement discounted
             :as rows]  (#'excel/fetch-transfer-batch-excel-data "batch1")]
        rows => (list
                  (conj common-data
                        {:company-contact-person "Sonja Sibbo"
                         :entity-name            "Jankon Betoni"
                         :first-row?             true
                         :price-per-unit         2.5
                         :text                   "Betonilasku"
                         :code                   "§2a"
                         :units                  11
                         :sap-number             "sap1"
                         :sums                   {:with-discount {:minor 2750}}})
                  (conj common-data
                        {:billing-reference "BILL-67890"
                         :entity-name       "Jankon Betonin Poika"
                         :first-row?        true
                         :price-per-unit    1
                         :text              "Sementtilasku"
                         :code              "§3a"
                         :units             5
                         :sap-number        "sap2"
                         :sums              {:with-discount {:minor 500}}})
                  (conj common-data
                        {:billing-reference "BILL-67890"
                         :entity-name       "Jankon Betonin Poika"
                         :price-per-unit    10
                         :text              long-text
                         :code              "§3b"
                         :units             4
                         :discount-percent  10
                         :sap-number        "sap2"
                         :sums              {:with-discount {:minor 3600}}}))
        (facts "get-invoice-excel-cell-value"
          (let [fun #'excel/get-invoice-excel-cell-value
                chk (fn [k & [r]]
                      (fact {:midje/description (or r k)}
                        (fun {k "hello"} (or r k))) => "hello")]
            (fun concrete :t) => 1
            (fun discounted :t) => 0
            (doseq [k [:tilauslaji :myyntiorg :jakelutie :sektori :laskuttaja
                       :nimike :tulosyksikko :maksuehto]]
              (chk k))
            (fun {} :bruttonetto) => "B"
            (fun {} :kpl) => ""
            (chk :case-reference :asiaviite)
            (chk :letter :littera)
            (chk :entity-name :nimi)
            (chk :units :qty)
            (chk :sap-number :asiakas)
            (fun concrete :otsikkoteksti) => "Tämä maksu voidaan ulosmitata ilman tuomiota tai päätöstä MRL 145 § 4 mom. \"alv 0% viranomaistoiminta\"\nViite: Sonja Sibbo"
            (fun cement :otsikkoteksti) => "Tämä maksu voidaan ulosmitata ilman tuomiota tai päätöstä MRL 145 § 4 mom. \"alv 0% viranomaistoiminta\"\nViite: BILL-67890"
            (fun concrete :otsikkomuistio) => "Liittyy lupaan app1, Luotikuja 12"
            (fun concrete :price-per-unit) => 2.5
            (fun discounted :price-per-unit) => 9
            (fun cement :riviteksti) => "§3a Sementtilasku"
            (fun discounted :riviteksti) => "§3b Lorem ipsum dolor sit amet, consecte"
            (fun cement :rivimuistio) => nil
            (fun discounted :rivimuistio) => "tur>< adipiscing elit. Quisque luctus mauris non pretium laoreet."))))))
