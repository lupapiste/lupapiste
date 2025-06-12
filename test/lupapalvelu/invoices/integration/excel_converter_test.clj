(ns lupapalvelu.invoices.integration.excel-converter-test
  (:require [lupapalvelu.invoices.integration.excel-converter :as excel]
            [lupapalvelu.invoices.schemas :refer [Invoice]]
            [lupapalvelu.invoices.util :as invoices-util]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [schema.core :as sc])
  (:import [org.apache.poi.xssf.usermodel XSSFWorkbook]))

(set! *warn-on-reflection* true)

(testable-privates lupapalvelu.invoices.integration.excel-converter
                   price-per-unit get-enriched-invoice-rows
                   get-invoice-excel-row-values)

(facts "price-per-unit"
  (fact "Integer"
    (price-per-unit {:total-cents 8000
                     :units       20}) => 4.0)
  (fact "No rounding needed"
    (price-per-unit {:total-cents 81250
                     :units       1250}) => 0.65)

  (fact "Round up"
    (price-per-unit {:total-cents 81250
                     :units       1259}) => 0.65)
  (fact "Round down"
    (price-per-unit {:total-cents 81250
                     :units       1260}) => 0.64)
  (fact "Decimal units: 28.571428571428573 -> 28.57"
    (price-per-unit {:total-cents 10000
                     :units       3.5}) => 28.57)
  (fact "Zero units"
    (price-per-unit {:sums  0
                     :units 0}) => 0)

  (fact "1000/3 -> 333.333... -> 3.33"
    (price-per-unit {:total-cents 1000
                     :units       3}) => 3.33))

(def long-text "Lorem ipsum dolor sit amet, consect>   < adipiscing elit. Quisque luctus mauris non pretium laoreet. ")


(def app1 {:address "Luotikuja 12"})
(def app2 {:address "Testikatu 33"})
(def org1 {:invoicing-config {:constants {:jakelutie 123}}})
(def op1 (mongo/create-id))
(def op2 (mongo/create-id))

(def by-bob {:id        "bob-id"
             :username  "bob@example.com"
             :firstName "Robert"
             :lastName  "Sample"
             :role      "applicant"})

(def invoice1 (sc/validate Invoice
                           {:id              "invoice1"
                            :application-id  "LP-753-2022-80001"
                            :organization-id "org1"
                            :state           "checked"
                            :description     "Hello"
                            :created         1667227504365
                            :created-by      by-bob
                            :payer-type      "person"
                            :entity-name     "Does not matter"
                            :first-name      " Randy "
                            :last-name       " Random "
                            :person-id       "290705-377T"
                            :sap-number      "sap1"
                            :operations      [{:id   op1
                                               :name "pientalo"}]
                            :rows            [{:id             "row1"
                                               :operation-id   op1
                                               :price-per-unit 2.5
                                               :code           "§2a"
                                               :unit           "kpl"
                                               :units          11
                                               :text           "Betonilasku"
                                               :total-cents    (int (* 11 2.5 100))}]}))
(def invoice2 (sc/validate Invoice
                           {:id                "invoice2"
                            :application-id    "LP-753-2022-80002"
                            :organization-id   "org1"
                            :state             "confirmed"
                            :description       "world"
                            :created           1667227652978
                            :created-by        by-bob
                            :payer-type        "company"
                            :entity-name       "Jankon Betonin Poika"
                            :billing-reference "BILL-67890"
                            :case-reference    "CASE-001"
                            :sap-number        "sap2"
                            :company-id        "8504682-4"
                            :entity-address    "Betonitie 10"
                            :city              "Betsamo"
                            :postal-code       "12345"
                            :letter            "Noted"
                            :operations        [{:id   op2
                                                 :name "aita"}]
                            :rows              [{:id             "row1"
                                                 :operation-id   op2
                                                 :price-per-unit 1
                                                 :code           "§3a"
                                                 :unit           "pv"
                                                 :units          5
                                                 :text           "Sementtilasku"
                                                 :total-cents    500}
                                                {:id               "row2"
                                                 :operation-id     op2
                                                 :price-per-unit   10
                                                 :discount-percent 10
                                                 :code             "§3b"
                                                 :unit             "m2"
                                                 :units            4
                                                 :text             long-text
                                                 :total-cents      (* 4 9 100)}]}))


(facts "workbook"
  (let [sheets                 [{:name "sheet" :header '("a" "b" "c") :rows '([1 2 3] [4 5 6] [7 8 9])}]
        ^XSSFWorkbook workbook (#'excel/make-workbook sheets)]
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

(facts "get-enriched-invoice-rows -> cell values"
  (let [constants  (merge excel/default-organization-constants {:jakelutie 123})
        rows-fn    (fn [invoice app]
                     (->> (invoices-util/enrich-invoice invoice app)
                          (get-enriched-invoice-rows constants)
                          (map get-invoice-excel-row-values)))
        [row1 row2
         :as rows] (rows-fn invoice2 app2)]
    (facts "invoice with 2 rows, company payer"
      (count rows) => 2
      (fact "first row"
        row1 => [1 ; First row
                 "Z001" ; Tilauslaji
                 1000 ; Myyntiorg
                 123 ; Jakelutie
                 47 ; Sektori
                 nil ; Viitelasku
                 "sap2" ; Asiakas
                 "8504682-4" ; Tunniste
                 "Jankon Betonin Poika" ; Nimi
                 "Betonitie 10" ; Osoite
                 "12345" ; PostiNo
                 "Betsamo" ; Toimipaikka
                 nil ; Pvm
                 "Z034" ; Maksuehto
                 10000480 ; Laskuttaja
                 "CASE-001" ; Asiaviite
                 ;; Otsikkoteksti
                 "Tämä maksu voidaan ulosmitata ilman tuomiota tai päätöstä MRL 145 § 4 mom. \"alv 0% viranomaistoiminta\"\nViite: BILL-67890"
                 ;; Otsikkomuistio
                 "Liittyy lupaan LP-753-2022-80002, Testikatu 33"
                 32876600 ; Nimike
                 5 ; Määrä
                 "" ; Kpl
                 1.0 ; Yksikköhinta
                 "B" ; Brutto/Netto
                 147552010 ; Tulosyksikkö
                 nil ; Sis. tilaus
                 nil ; Empty column
                 "Noted" ; Littera
                 nil ; Tilastoraportti
                 nil ; Ympäristökohde
                 nil ; Toiminto
                 nil ; Varakoodi 2
                 "§3a Sementtilasku" ; Riviteksti
                 nil ; Rivimuistio
                 ])
      (fact "second row"
        row2 => [0 ; Second row
                 nil; Tilauslaji
                 nil ; Myyntiorg
                 nil ; Jakelutie
                 nil ; Sektori
                 nil ; Viitelasku
                 nil ; Asiakas
                 nil ; Tunniste
                 nil ; Nimi
                 nil ; Osoite
                 nil ; PostiNo
                 nil ; Toimipaikka
                 nil ; Pvm
                 nil ; Maksuehto
                 nil ; Laskuttaja
                 nil ; Asiaviite
                 nil ; Otsikkoteksti
                 nil ; Otsikkomuistio
                 32876600 ; Nimike
                 4 ; Määrä
                 "" ; Kpl
                 9.0 ; Yksikköhinta
                 "B" ; Brutto/Netto
                 147552010; Tulosyksikkö
                 nil ; Sis. tilaus
                 nil ; Empty column
                 nil ; Littera
                 nil ; Tilastoraportti
                 nil ; Ympäristökohde
                 nil ; Toiminto
                 nil ; Varakoodi 2
                 ;; Riviteksti
                 "§3b Lorem ipsum dolor sit amet, consect>"
                 ;; Rivimuistio
                 "< adipiscing elit. Quisque luctus mauris non pretium laoreet."]))
    (fact "Invoice with one row, person payer"
      (rows-fn invoice1 app1)
      => [[1 ; First row
           "Z001" ; Tilauslaji
           1000 ; Myyntiorg
           123 ; Jakelutie
           47 ; Sektori
           nil ; Viitelasku
           "sap1" ; Asiakas
           "290705-377T" ; Tunniste
           "Randy Random" ; Nimi
           nil ; Osoite
           nil ; PostiNo
           nil ; Toimipaikka
           nil ; Pvm
           "Z034" ; Maksuehto
           10000480 ; Laskuttaja
           nil ; Asiaviite
           ;; Otsikkoteksti
           "Tämä maksu voidaan ulosmitata ilman tuomiota tai päätöstä MRL 145 § 4 mom. \"alv 0% viranomaistoiminta\"\nViite: Randy Random"
           ;; Otsikkomuistio
           "Liittyy lupaan LP-753-2022-80001, Luotikuja 12"
           32876600 ; Nimike
           11 ; Määrä
           "" ; Kpl
           2.5 ; Yksikköhinta
           "B" ; Brutto/Netto
           147552010 ; Tulosyksikkö
           nil ; Sis. tilaus
           nil ; Empty column
           nil ; Littera
           nil ; Tilastoraportti
           nil ; Ympäristökohde
           nil ; Toiminto
           nil ; Varakoodi 2
           "§2a Betonilasku" ; Riviteksti
           nil ; Rivimuistio
           ]])))
