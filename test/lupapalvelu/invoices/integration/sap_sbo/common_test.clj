(ns lupapalvelu.invoices.integration.sap-sbo.common-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.invoices.integration.sap-sbo.common :as sap]
            [lupapalvelu.invoices.integration.sap-sbo.error-util :as sap-error]
            [lupapalvelu.invoices.integration.sap-sbo.sap-test-utils :as sap-test]
            [sade.date :as date]
            [schema.core :as sc]))

(use-fixtures :once (fn [test-fn] (sc/with-fn-validation
                                    (test-fn))))

(defn- translated?
  [field]
  (i18n/has-term? :fi (sap-error/loc-field field)))

(deftest field-translation-test
  (testing "keys found"
    (is (= #{:backend-id
             :billing-reference
             :city
             :code
             :company-id
             :config
             :constants
             :description
             :invoices
             :jakelutie
             :laskuttaja
             :myyntiorg
             :myyntitoimisto
             :ovt
             :payer-type
             :person-id
             :postal-code
             :price-per-unit
             :product-constants
             :projekti
             :rows
             :sap-number
             :sektori
             :text
             :tilauslaji
             :unit
             :units}
           ;; Sanity check: recursively searching for keys
           ;; from the root schema should return the same result
           ;; as merging the results of the individual schemas
           (sap-test/schema->keys sap/SapInput)
           (->> [sap/SapInput
                 sap/SapConfig
                 sap/SapConstants
                 sap/SapPersonInvoice
                 sap/SapCompanyInvoice
                 sap/SapRow
                 sap/SapProductConstants]
                (mapcat sap-test/schema->keys)
                (into #{})))))
  (testing "all fields have translations"
    (doseq [field (sap-test/schema->keys sap/SapInput)]
      (is (translated? field)))))

(deftest make-sales-orders-test
  (let [lang                    :fi
        config                  {:constants {:jakelutie      "12"
                                             :laskuttaja     "ID123"
                                             :myyntiorg      "3"
                                             :myyntitoimisto "2"
                                             :sektori        "34"
                                             :tilauslaji     "1"}}
        invoices                [{:backend-id        "0123456789"
                                  :city              "Tampere"
                                  :payer-type        "person"
                                  :postal-code       "10000"
                                  :billing-reference "5432"
                                  :rows              [{:code              "12345678"
                                                       :product-constants {:projekti "1234567890123456"}
                                                       :text              ""
                                                       :price-per-unit    1.23
                                                       :unit              "kpl"
                                                       :units             5}]
                                  :sap-number        "0123456789"}]
        invoices-with-discounts [{:backend-id        "0123456789"
                                  :city              "Tampere"
                                  :payer-type        "person"
                                  :postal-code       "10000"
                                  :billing-reference "5432"
                                  :rows              [{:code              "12345678"
                                                       :product-constants {:projekti "1234567890123456"}
                                                       :text              ""
                                                       :price-per-unit    1.23
                                                       :discount-percent  33.5
                                                       :unit              "kpl"
                                                       :units             5}
                                                      {:code              "negative"
                                                       :product-constants {:projekti "1234567890123456"}
                                                       :text              ""
                                                       :price-per-unit    -8.56
                                                       :discount-percent  65
                                                       :unit              "m2"
                                                       :units             1}]
                                  :sap-number        "0123456789"}]]
    (testing "Happy paths"
      (testing "person with sap-number"
        (is (= {:sales-orders [{:distribution-channel "12"
                                :division             "34"
                                :line-items           [{:material    "12345678"
                                                        :price       "1.23"
                                                        :quantity    "5.000"
                                                        :unit        "kpl"
                                                        :wbs-element "1234567890123456"}]
                                :order-party          {:city            "Tampere"
                                                       :postal-code     "10000"
                                                       :sap-customer-id "0123456789"}
                                :order-type           "1"
                                :po-number            "5432"
                                :reference            "0123456789"
                                :sales-office         "2"
                                :sales-org            "3"
                                :sender-id            "ID123"}]}
               (sap/make-sales-orders-data config invoices lang))))
      (testing "person with person-id"
        (let [invoices (update invoices 0 (fn [invoice]
                                            (-> invoice
                                                (dissoc :sap-number)
                                                (assoc :person-id "010107A941L"))))]
          (is (= {:sales-orders [{:distribution-channel "12"
                                  :division             "34"
                                  :line-items           [{:material    "12345678"
                                                          :price       "1.23"
                                                          :quantity    "5.000"
                                                          :unit        "kpl"
                                                          :wbs-element "1234567890123456"}]
                                  :order-party          {:city        "Tampere"
                                                         :postal-code "10000"
                                                         :customer-id "010107A941L"}
                                  :order-type           "1"
                                  :po-number            "5432"
                                  :reference            "0123456789"
                                  :sales-office         "2"
                                  :sales-org            "3"
                                  :sender-id            "ID123"}]}
                 (sap/make-sales-orders-data config invoices lang)))))
      (testing "person with person-id and full details"
        (let [invoices (update invoices 0 (fn [invoice]
                                            (-> invoice
                                                (dissoc :sap-number)
                                                (assoc :person-id "010107A941L"
                                                       :first-name "FIRST 1234567890 1234567890"
                                                       :last-name "LAST 1234567890 1234567890"
                                                       :entity-address "Testikatu 123. 1234567890 1234567890 1234567890"
                                                       :description "DESCRIPTION")
                                                (update-in [:rows 0] assoc :text "ROW-TEXT"))))]
          (is (= {:sales-orders [{:bill-text            "DESCRIPTION"
                                  :distribution-channel "12"
                                  :division             "34"
                                  :line-items           [{:material             "12345678"
                                                          :material-description "ROW-TEXT"
                                                          :price                "1.23"
                                                          :quantity             "5.000"
                                                          :unit                 "kpl"
                                                          :wbs-element          "1234567890123456"}]
                                  :order-party          {:city               "Tampere"
                                                         :postal-code        "10000"
                                                         ;; max 35 chars
                                                         :priority-addresses ["Testikatu 123. 1234567890 123456789"]
                                                         :customer-id        "010107A941L"
                                                         ;; max 35 chars
                                                         :priority-names     ["LAST 1234567890 1234567890, FIRST 1"]}
                                  :order-type           "1"
                                  :po-number            "5432"
                                  :reference            "0123456789"
                                  :sales-office         "2"
                                  :sales-org            "3"
                                  :sender-id            "ID123"}]}
                 (sap/make-sales-orders-data config invoices lang)))))
      (testing "company with sap-number"
        (let [invoices (update invoices 0 (fn [invoice]
                                            (-> invoice
                                                (assoc :payer-type "company"))))]
          (is (= {:sales-orders [{:distribution-channel "12"
                                  :division             "34"
                                  :line-items           [{:material    "12345678"
                                                          :price       "1.23"
                                                          :quantity    "5.000"
                                                          :unit        "kpl"
                                                          :wbs-element "1234567890123456"}]
                                  :order-party          {:city            "Tampere"
                                                         :postal-code     "10000"
                                                         :sap-customer-id "0123456789"}
                                  :order-type           "1"
                                  :po-number            "5432"
                                  :reference            "0123456789"
                                  :sales-office         "2"
                                  :sales-org            "3"
                                  :sender-id            "ID123"}]}
                 (sap/make-sales-orders-data config invoices lang)))))
      (testing "company with y-tunnus"
        (let [invoices (update invoices 0 (fn [invoice]
                                            (-> invoice
                                                (dissoc :sap-number)
                                                (assoc :payer-type "company"
                                                       :company-id "1234567-8"))))]
          (is (= {:sales-orders [{:distribution-channel "12"
                                  :division             "34"
                                  :line-items           [{:material    "12345678"
                                                          :price       "1.23"
                                                          :quantity    "5.000"
                                                          :unit        "kpl"
                                                          :wbs-element "1234567890123456"}]
                                  :order-party          {:city         "Tampere"
                                                         :postal-code  "10000"
                                                         :customer-yid "1234567-8"}
                                  :order-type           "1"
                                  :po-number            "5432"
                                  :reference            "0123456789"
                                  :sales-office         "2"
                                  :sales-org            "3"
                                  :sender-id            "ID123"}]}
                 (sap/make-sales-orders-data config invoices lang)))))
      (testing "short project number"
        (let [invoices (update invoices 0 (fn [invoice]
                                            (-> invoice
                                                (update-in [:rows 0] assoc-in [:product-constants :projekti] "42"))))]
          (is (= {:sales-orders [{:distribution-channel "12"
                                  :division             "34"
                                  :line-items           [{:material    "12345678"
                                                          :price       "1.23"
                                                          :quantity    "5.000"
                                                          :unit        "kpl"
                                                          :wbs-element "42"}]
                                  :order-party          {:city            "Tampere"
                                                         :postal-code     "10000"
                                                         :sap-customer-id "0123456789"}
                                  :order-type           "1"
                                  :po-number            "5432"
                                  :reference            "0123456789"
                                  :sales-office         "2"
                                  :sales-org            "3"
                                  :sender-id            "ID123"}]}
                 (sap/make-sales-orders-data config invoices lang)))))
      (testing "discounts"
        (is (= {:sales-orders [{:distribution-channel "12"
                                :division             "34"
                                :line-items           [{:material    "12345678"
                                                        :price       "0.82"
                                                        :quantity    "5.000"
                                                        :unit        "kpl"
                                                        :wbs-element "1234567890123456"}
                                                       {:material    "negative"
                                                        :price       "-3.00"
                                                        :quantity    "1.000"
                                                        :unit        "m2"
                                                        :wbs-element "1234567890123456"}]
                                :order-party          {:city            "Tampere"
                                                       :postal-code     "10000"
                                                       :sap-customer-id "0123456789"}
                                :order-type           "1"
                                :po-number            "5432"
                                :reference            "0123456789"
                                :sales-office         "2"
                                :sales-org            "3"
                                :sender-id            "ID123"}]}
               (sap/make-sales-orders-data config invoices-with-discounts lang)))))
    (testing "error paths"
      (testing "errors with config"
        ;; In case Lupapiste admin misconfigures something
        (testing "no config"
          (let [config {}]
            (is (= {:error [{:error "missing-required-key"
                             :field ["config" "constants"]
                             :loc   {:location "Laskutusasetukset > Vakiot"
                                     :error    "Pakollinen kenttä puuttuu"}}]}
                   (sap/make-sales-orders-data config invoices lang)))))
        (testing "Missing constant(s)"
          (let [config (assoc config :constants {})]
            (is (= {:error [{:field ["config" "constants" "jakelutie"]
                             :error "missing-required-key"
                             :loc   {:location "Laskutusasetukset > Vakiot > Jakelutie"
                                     :error    "Pakollinen kenttä puuttuu"}}
                            {:field ["config" "constants" "sektori"]
                             :error "missing-required-key"
                             :loc   {:location "Laskutusasetukset > Vakiot > Sektori"
                                     :error    "Pakollinen kenttä puuttuu"}}
                            {:field ["config" "constants" "tilauslaji"]
                             :error "missing-required-key"
                             :loc   {:location "Laskutusasetukset > Vakiot > Tilauslaji"
                                     :error    "Pakollinen kenttä puuttuu"}}
                            {:field ["config" "constants" "myyntitoimisto"]
                             :error "missing-required-key"
                             :loc   {:location "Laskutusasetukset > Vakiot > Myyntitoimisto"
                                     :error    "Pakollinen kenttä puuttuu"}}
                            {:field ["config" "constants" "myyntiorg"]
                             :error "missing-required-key"
                             :loc   {:location "Laskutusasetukset > Vakiot > Myyntiorganisaatio"
                                     :error    "Pakollinen kenttä puuttuu"}}
                            {:field ["config" "constants" "laskuttaja"]
                             :error "missing-required-key"
                             :loc   {:location "Laskutusasetukset > Vakiot > Laskuttaja"
                                     :error    "Pakollinen kenttä puuttuu"}}]}
                   (sap/make-sales-orders-data config invoices lang)))))
        (testing "Too long constant"
          (let [config (update config :constants assoc :myyntitoimisto "12345")]
            (is (= {:error [{:error    "not-valid"
                             :field    ["config" "constants" "myyntitoimisto"]
                             :value    "12345"
                             :expected [:min-max-str 1 4]
                             :loc      {:location "Laskutusasetukset > Vakiot > Myyntitoimisto"
                                        :error    "Arvon tulee olla 1-4 merkkiä pitkä"}}]}
                   (sap/make-sales-orders-data config invoices lang)))))
        (testing "Invalid pattern"
          (let [config (update config :constants assoc :laskuttaja "FOO")]
            (is (= {:error [{:error    "not-valid"
                             :field    ["config" "constants" "laskuttaja"]
                             :value    "FOO"
                             :expected [:pattern "ID\\d{3}"]
                             :loc      {:location "Laskutusasetukset > Vakiot > Laskuttaja"
                                        :error    "Väärä muoto"}}]}
                   (sap/make-sales-orders-data config invoices lang))))))
      (testing "errors with invoice"
        (testing "no invoices"
          (let [invoices []]
            (is (= {:error [{:error    "not-valid"
                             :expected [:not-empty]
                             :field    ["invoices"]
                             :value    []
                             :loc      {:location "Lasku"
                                        :error    "Ei saa olla tyhjä"}}]}
                   (sap/make-sales-orders-data config invoices lang)))))
        (testing "empty invoice"
          ;; This should be rather impossible
          (let [invoices [{}]]
            (is (= {:error [{:field ["invoices" 0 "city"]
                             :error "missing-required-key"
                             :loc   {:location "Lasku: 1 > Kaupunki"
                                     :error    "Pakollinen kenttä puuttuu"}}
                            {:field ["invoices" 0 "payer-type"]
                             :error "missing-required-key"
                             :loc   {:location "Lasku: 1 > Maksajan tyyppi"
                                     :error    "Pakollinen kenttä puuttuu"}}
                            {:field ["invoices" 0 "postal-code"]
                             :error "missing-required-key"
                             :loc   {:location "Lasku: 1 > Postinumero"
                                     :error    "Pakollinen kenttä puuttuu"}}
                            {:field ["invoices" 0 "rows"]
                             :error "missing-required-key"
                             :loc   {:location "Lasku: 1 > Rivi"
                                     :error    "Pakollinen kenttä puuttuu"}}
                            {:field ["invoices" 0 "backend-id"]
                             :error "missing-required-key"
                             :loc   {:error    "Pakollinen kenttä puuttuu"
                                     :location "Lasku: 1 > Laskulaji"}}]}
                   (sap/make-sales-orders-data config invoices lang)))))
        (testing "invalid sap-number"
          (let [invoices (update invoices 0 assoc :sap-number "42")]
            (is (= {:error [{:error    "not-valid"
                             :expected [:fixed 10]
                             :field    ["invoices" 0 "sap-number"]
                             :value    "42"
                             :loc      {:location "Lasku: 1 > Asiakasnumero"
                                        :error    "Arvon tulee olla 10 merkkiä pitkä"}}]}
                   (sap/make-sales-orders-data config invoices lang)))))
        (testing "invalid hetu"
          (let [invoices (update invoices 0 assoc :person-id "007")]
            (is (= {:error [{:error    "not-valid"
                             :expected [:fixed 11]
                             :field    ["invoices" 0 "person-id"]
                             :value    "007"
                             :loc      {:location "Lasku: 1 > Henkilötunnus"
                                        :error    "Arvon tulee olla 11 merkkiä pitkä"}}]}
                   (sap/make-sales-orders-data config invoices lang)))))
        (testing "person with neither person ir nor sap-number"
          (let [invoices (update invoices 0 (fn [invoice]
                                              (dissoc invoice :sap-number)))]
            (is (= {:error [{:field    ["invoices" 0]
                             :error    "not-valid"
                             :value    {:backend-id        "0123456789"
                                        :billing-reference "5432"
                                        :city              "Tampere"
                                        :payer-type        "person"
                                        :postal-code       "10000"
                                        :rows              [{:code              "12345678"
                                                             :price-per-unit    1.23
                                                             :product-constants {:projekti "1234567890123456"}
                                                             :unit              "kpl"
                                                             :units             5}]}
                             :expected [:required-keys #{:person-id :sap-number}]
                             :loc      {:error    "Ainakin yksi kenttä määriteltävä (Asiakasnumero, Henkilötunnus)"
                                        :location "Lasku: 1"}}]}
                   (sap/make-sales-orders-data config invoices lang))))))
      (testing "errors with invoice rows"
        (testing "no rows"
          (let [invoices (update invoices 0 assoc :rows [])]
            (is (= {:error [{:error    "not-valid"
                             :expected [:not-empty]
                             :field    ["invoices" 0 "rows"]
                             :value    []
                             :loc      {:location "Lasku: 1 > Rivi"
                                        :error    "Ei saa olla tyhjä"}}]}
                   (sap/make-sales-orders-data config invoices lang)))))
        (testing "empty row"
          ;; This should be rather impossible
          (let [invoices (update invoices 0 (fn [invoice]
                                              (assoc-in invoice [:rows 0] {})))]
            (is (= {:error
                    [{:field ["invoices" 0 "rows" 0 "code"]
                      :error "missing-required-key"
                      :loc   {:location "Lasku: 1 > Rivi: 1 > Koodi"
                              :error    "Pakollinen kenttä puuttuu"}}
                     {:field ["invoices" 0 "rows" 0 "product-constants"]
                      :error "missing-required-key"
                      :loc   {:location
                              "Lasku: 1 > Rivi: 1 > Tiliöintitiedot"
                              :error "Pakollinen kenttä puuttuu"}}
                     {:field ["invoices" 0 "rows" 0 "price-per-unit"]
                      :error "missing-required-key"
                      :loc   {:location "Lasku: 1 > Rivi: 1 > Yksikköhinta"
                              :error    "Pakollinen kenttä puuttuu"}}
                     {:field ["invoices" 0 "rows" 0 "unit"]
                      :error "missing-required-key"
                      :loc   {:location "Lasku: 1 > Rivi: 1 > Yksikkö"
                              :error    "Pakollinen kenttä puuttuu"}}
                     {:field ["invoices" 0 "rows" 0 "units"]
                      :error "missing-required-key"
                      :loc   {:location "Lasku: 1 > Rivi: 1 > Määrä"
                              :error    "Pakollinen kenttä puuttuu"}}]}
                   (sap/make-sales-orders-data config invoices lang)))))
        (testing "empty project"
          (let [invoices (update invoices 0 (fn [invoice]
                                              (update-in invoice [:rows 0] assoc-in [:product-constants :projekti] "")))]
            (is (= {:error [{:error "missing-required-key"
                             :field ["invoices" 0 "rows" 0 "product-constants" "projekti"]
                             :loc   {:location "Lasku: 1 > Rivi: 1 > Tiliöintitiedot > Projekti"
                                     :error    "Pakollinen kenttä puuttuu"}}]}
                   (sap/make-sales-orders-data config invoices lang)))))
        (testing "missing row code"
          (let [invoices (update invoices 0 (fn [invoice]
                                              (update-in invoice [:rows 0] dissoc :code)))]
            (is (= {:error [{:error "missing-required-key"
                             :field ["invoices" 0 "rows" 0 "code"]
                             :loc   {:location "Lasku: 1 > Rivi: 1 > Koodi"
                                     :error    "Pakollinen kenttä puuttuu"}}]}
                   (sap/make-sales-orders-data config invoices lang)))))
        (testing "invalid row code"
          (let [invoices (update invoices 0 (fn [invoice]
                                              (update-in invoice [:rows 0] assoc :code "1")))]
            (is (= {:error [{:error    "not-valid"
                             :field    ["invoices" 0 "rows" 0 "code"]
                             :value    "1"
                             :expected [:fixed 8]
                             :loc      {:location "Lasku: 1 > Rivi: 1 > Koodi"
                                        :error    "Arvon tulee olla 8 merkkiä pitkä"}}]}
                   (sap/make-sales-orders-data config invoices lang)))))
        (testing "invalid price"
          (let [invoices (update invoices 0 (fn [invoice]
                                              (update-in invoice [:rows 0] assoc :price-per-unit 999999999999)))]
            (is (= {:error [{:error    "not-valid"
                             :field    ["invoices" 0 "rows" 0 "price-per-unit"]
                             :value    999999999999
                             :expected "valid-price-per-unit"
                             :loc      {:location "Lasku: 1 > Rivi: 1 > Yksikköhinta"
                                        :error    "Arvo virheellinen"}}]}
                   (sap/make-sales-orders-data config invoices lang)))))))))

(deftest build-response-test
  (let [cfg     {}
        created (date/timestamp "3.4.2024 12:31:11")]
    (is (= {:content      "<foobar/>"
            :filename     "Lupapiste_240403123111.xml"
            :content-type "text/xml;charset=UTF-8"}
           (sap/build-response cfg created "<foobar/>")))
    (testing "with prefix"
      (let [cfg (assoc cfg :invoice-file-prefix "<PREFIX>")]
        (is (= {:content      "<foobar/>"
                :filename     "<PREFIX>240403123111.xml"
                :content-type "text/xml;charset=UTF-8"}
               (sap/build-response cfg created "<foobar/>")))))))
