(ns lupapalvelu.invoices.integration.general.converter-test
  (:require [lupapalvelu.document.model :as dm]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.invoices.integration.general.converter]
            [lupapalvelu.invoices.schemas :refer [Invoice]]
            [lupapalvelu.invoices.util :as invoices-util]
            [lupapalvelu.mml.geocoding.core :as geocoding]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [schema.core :as sc]))

(testable-privates lupapalvelu.invoices.integration.general.converter
                   ->invoice-data
                   tidywalk)

(facts "tidywalk"
  (tidywalk nil) => nil
  (tidywalk []) => []
  (tidywalk "  hello  ") => "hello"
  (tidywalk :foo) => :foo
  (tidywalk 123) => 123
  (tidywalk true) => "true"
  (tidywalk false) => "false"
  (tidywalk {:number 123
             :string "  string  "
             :nil    nil
             :map    {:one   "  one  "
                      :two   [1 2 "  3  " nil 4]
                      :three :three
                      :flag  true
                      :four  nil}})
  => {:number 123
      :string "string"
      :nil    ""
      :map    {:one   "one"
               :two   [1 2 "3" "" 4]
               :three :three
               :flag  "true"
               :four  ""}})

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

(facts "->invoice-data"
  (sc/with-fn-validation
    (against-background [(geocoding/address-by-point! "fi" 1 2) => {:name {:fi "Turku"}, :postal-code "12345"}]
      (facts "returns data with proper fields"
        (let [invoicing-config           {:integration-url                       "url"
                                          :credentials                           {:username "username" :password "password"}
                                          :integration-requires-customer-number? false
                                          :constants                             {:sektori        "1"
                                                                                  :nimike         "2"
                                                                                  :jakelutie      "3"
                                                                                  :tulosyksikko   "4"
                                                                                  :laskuttaja     "5"
                                                                                  :myyntiorg      "6"
                                                                                  :tilauslaji     "7"
                                                                                  :myyntitoimisto "8"}}

              payer-doc                  {:schema-info {:name "maksaja" :version 1 :type "party" :subtype "maksaja"}
                                          :data        {:_selected  {:value "yritys"}
                                                        :laskuviite {:value "laskuviite"}
                                                        :yritys     {:companyId            {:value "1234567-1"}
                                                                     :yritysnimi           {:value "Esimerkki Oy"}
                                                                     :liikeJaYhteisoTunnus {:value "1234567-1"}
                                                                     :osoite               {:katu                 {:value "Merkintie 88"}
                                                                                            :postinumero          {:value "31640"}
                                                                                            :postitoimipaikannimi {:value "Humppila"}
                                                                                            :maa                  {:value "FIN"}}
                                                                     :yhteyshenkilo        {:henkilotiedot {:etunimi           {:value "Esko"}
                                                                                                            :sukunimi          {:value "Ala-Pentti"}
                                                                                                            :turvakieltoKytkin {:value false}}
                                                                                            :yhteystiedot  {:puhelin {:value "0401234567"}
                                                                                                            :email   {:value "esko@example.com"}}
                                                                                            :kytkimet      {:suoramarkkinointilupa {:value false}}}
                                                                     :verkkolaskutustieto  {:verkkolaskuTunnus {:value "samplebilling"}
                                                                                            :ovtTunnus         {:value "003710601555"}
                                                                                            :valittajaTunnus   {:value "BAWCFI22"}}}
                                                        :henkilo    {:henkilotiedot {:etunimi                 {:value "Pena"},
                                                                                     :hetu                    {:value "010203-040A"},
                                                                                     :not-finnish-hetu        {:value false},
                                                                                     :sukunimi                {:value "Panaani"},
                                                                                     :turvakieltoKytkin       {:value false},
                                                                                     :ulkomainenHenkilotunnus {:value ""}},
                                                                     :kytkimet      {:suoramarkkinointilupa {:value true}},
                                                                     :osoite        {:katu                 {:value "Paapankuja 12"},
                                                                                     :maa                  {:value "FIN"},
                                                                                     :postinumero          {:value "92620"},
                                                                                     :postitoimipaikannimi {:value "Piippola"}},
                                                                     :yhteystiedot  {:email   {:value "pena@example.com"},
                                                                                     :puhelin {:value "0102030405"}}}}}

              application                {:address          "Fågelsångintie 46"
                                          :primaryOperation {:name "vapaa-ajan-asuinrakennus"}
                                          :propertyId       "75341600110060"
                                          :location         [1 2]
                                          :documents        [payer-doc]}
              [vapaa-id puu-id linja-id] (repeatedly 3 mongo/create-id)
              invoice                    (sc/validate Invoice
                                                      {:id                "invoice-id"
                                                       :application-id    "LP-753-2019-12345"
                                                       :description       "hello"
                                                       :state             "confirmed"
                                                       :created           1664571600000
                                                       :created-by        by-bob
                                                       :organization-id   "753-R"
                                                       :sap-number        "sapcustid9"
                                                       :first-name        "Connie"
                                                       :last-name         "Contact"
                                                       :protected?        true
                                                       :phone             "+123 400 900"
                                                       :email             "cc@example.net"
                                                       :company-id        "sample.com.id"
                                                       :person-id         "abcd-id"
                                                       :entity-name       "Sample Systems Ltd"
                                                       :entity-address    "Äntligenvägen 10"
                                                       :postal-code       "02233"
                                                       :city              "Tallkulla"
                                                       :payer-type        "person"
                                                       :country           "SWE"
                                                       :netbill           "my-atm"
                                                       :ovt               "003710602999"
                                                       :operator          "my-operator"
                                                       :partner-code      "bill buddy"
                                                       :billing-reference "reffy ref"
                                                       :case-reference    "handbag"
                                                       :letter            "to whom it may concern"
                                                       :operations        [{:name "vapaa-ajan-asuinrakennus" :id vapaa-id}
                                                                           {:name "puun-kaataminen" :id puu-id}
                                                                           {:name "linjasaneeraus" :id linja-id}]
                                                       :rows              [{:id                "row1"
                                                                            :operation-id      vapaa-id
                                                                            :code              "123"
                                                                            :text              "Toimenpidemaksu"
                                                                            :unit              "kpl"
                                                                            :units             5
                                                                            :price-per-unit    40
                                                                            :discount-percent  0
                                                                            :product-constants dummy-product-constants
                                                                            :total-cents       20000}
                                                                           {:id               "row2"
                                                                            :operation-id     puu-id
                                                                            :code             "456"
                                                                            :text             "Tarkastusmaksu"
                                                                            :unit             "pv"
                                                                            :units            2
                                                                            :price-per-unit   150.5
                                                                            :discount-percent 10
                                                                            :total-cents      27090}
                                                                           {:id                "row3"
                                                                            :operation-id      linja-id
                                                                            :code              "111"
                                                                            :text              "Toimittajan oma maksu"
                                                                            :unit              "kpl"
                                                                            :price-per-unit    1000
                                                                            :units             1
                                                                            :product-constants (-> dummy-product-constants
                                                                                                   (assoc :kustannuspaikka "  ")
                                                                                                   (dissoc :alv))}]})
              min-invoice                (sc/validate Invoice
                                                      (-> invoice
                                                          (select-keys [:id :application-id :description :state
                                                                        :created :created-by :organization-id])
                                                          (assoc :operations (take 1 (:operations invoice))
                                                                 :rows [(-> (:rows invoice)
                                                                            first
                                                                            (dissoc :discount-percent
                                                                                    :product-constants))])))]

          (fact "payer document is valid"
            (let [schema            (schemas/get-schema (schemas/get-latest-schema-version) "maksaja")
                  validation-result (dm/validate application payer-doc schema)]
              validation-result => empty?))

          (let [base              {:case         "handbag"
                                   :customer     {:client-number "sapcustid9"}
                                   :invoice-rows [{:code              "123"
                                                   :discount-percent  0
                                                   :name              "Toimenpidemaksu"
                                                   :product-constants {:alv              "b"
                                                                       :kohde            "e"
                                                                       :kustannuspaikka  "a"
                                                                       :laskentatunniste "c"
                                                                       :muu-tunniste     "g"
                                                                       :projekti         "d"
                                                                       :toiminto         "f"}
                                                   :quantity          5
                                                   :unit              "kpl"
                                                   :unitprice         40}
                                                  {:code             "456"
                                                   :discount-percent 10
                                                   :name             "Tarkastusmaksu"
                                                   :quantity         2
                                                   :unit             "pv"
                                                   :unitprice        150.5}
                                                  {:code              "111"
                                                   :discount-percent  0
                                                   :name              "Toimittajan oma maksu"
                                                   :product-constants {:kohde            "e"
                                                                       :laskentatunniste "c"
                                                                       :muu-tunniste     "g"
                                                                       :projekti         "d"
                                                                       :toiminto         "f"}
                                                   :quantity          1
                                                   :unit              "kpl"
                                                   :unitprice         1000}]
                                   :invoice-type "EXTERNAL"
                                   :letter       "to whom it may concern"
                                   :operation    "vapaa-ajan-asuinrakennus"
                                   :payee        {:payee-group "3" :payee-organization-id "6" :payee-sector "1"}
                                   :permit-id    "LP-753-2019-12345"
                                   :property-id  "75341600110060"
                                   :reference    "reffy ref"
                                   :target       {:street      "Fågelsångintie 46"
                                                  :city        "Turku"
                                                  :postal-code "12345"}}
                person-payer      {:payer-type "PERSON"
                                   :person     {:city          "Tallkulla"
                                                :country       "SWE"
                                                :firstname     "Connie"
                                                :id            "abcd-id"
                                                :lastname      "Contact"
                                                :partner-code  "bill buddy"
                                                :postalcode    "02233"
                                                :streetaddress "Äntligenvägen 10"
                                                :turvakielto   "true"}}
                company-payer     {:organization {:city                "Tallkulla"
                                                  :contact-firstname   "Connie"
                                                  :contact-lastname    "Contact"
                                                  :contact-turvakielto "false"
                                                  :country             "SWE"
                                                  :edi                 "003710602999"
                                                  :einvoice-address    "my-atm"
                                                  :id                  "sample.com.id"
                                                  :name                "Sample Systems Ltd"
                                                  :operator            "my-operator"
                                                  :partner-code        "bill buddy"
                                                  :postalcode          "02233"
                                                  :streetaddress       "Äntligenvägen 10"}
                                   :payer-type   "ORGANIZATION"}
                app-person-payer  {:payer-type "PERSON"
                                   :person     {:city          "Piippola"
                                                :country       "FIN"
                                                :firstname     "Pena"
                                                :id            "010203-040A"
                                                :lastname      "Panaani"
                                                :postalcode    "92620"
                                                :streetaddress "Paapankuja 12"
                                                :turvakielto   "false"}}
                app-company-payer {:organization {:city                "Humppila"
                                                  :contact-firstname   "Esko"
                                                  :contact-lastname    "Ala-Pentti"
                                                  :contact-turvakielto "false"
                                                  :country             "FIN"
                                                  :edi                 "003710601555"
                                                  :einvoice-address    "samplebilling"
                                                  :id                  "1234567-1"
                                                  :name                "Esimerkki Oy"
                                                  :operator            "BAWCFI22"
                                                  :postalcode          "31640"
                                                  :streetaddress       "Merkintie 88"}
                                   :payer-type   "ORGANIZATION"}]


            (fact "invoice person payer"
              (->invoice-data invoicing-config (invoices-util/enrich-invoice invoice application))
              => (assoc base :payer person-payer))

            (fact "invoice company payer"
              (->invoice-data invoicing-config
                              (invoices-util/enrich-invoice (assoc invoice
                                                                   :payer-type "company"
                                                                   :organization-internal-invoice? true
                                                                   :protected? false)
                                                            application))
              => (assoc base
                        :invoice-type "INTERNAL"
                        :payer company-payer))


            (fact "Minimal person invoice"
              (->invoice-data invoicing-config (invoices-util/enrich-invoice min-invoice
                                                                             application))
              => {:case         ""
                  :customer     {:client-number ""}
                  :invoice-rows [{:code             "123"
                                  :discount-percent 0
                                  :name             "Toimenpidemaksu"
                                  :quantity         5
                                  :unit             "kpl"
                                  :unitprice        40}]
                  :invoice-type "EXTERNAL"
                  :letter       ""
                  :operation    "vapaa-ajan-asuinrakennus"
                  :payee        {:payee-group "3" :payee-organization-id "6" :payee-sector "1"}
                  :payer        {:payer-type "PERSON"
                                 :person     {:city          ""
                                              :country       ""
                                              :firstname     ""
                                              :id            ""
                                              :lastname      ""
                                              :partner-code  ""
                                              :postalcode    ""
                                              :streetaddress ""
                                              :turvakielto   "false"}}
                  :permit-id    "LP-753-2019-12345"
                  :property-id  "75341600110060"
                  :reference    ""
                  :target       {:street      "Fågelsångintie 46"
                                 :city        "Turku"
                                 :postal-code "12345"}})

            (fact "Minimal company invoice"
              (->invoice-data invoicing-config
                              (invoices-util/enrich-invoice (assoc min-invoice :payer-type "company")
                                                            application))
              => {:case         ""
                  :customer     {:client-number ""}
                  :invoice-rows [{:code             "123"
                                  :discount-percent 0
                                  :name             "Toimenpidemaksu"
                                  :quantity         5
                                  :unit             "kpl"
                                  :unitprice        40}]
                  :invoice-type "EXTERNAL"
                  :letter       ""
                  :operation    "vapaa-ajan-asuinrakennus"
                  :payee        {:payee-group "3" :payee-organization-id "6" :payee-sector "1"}
                  :payer        {:organization {:city                ""
                                                :contact-firstname   ""
                                                :contact-lastname    ""
                                                :contact-turvakielto "false"
                                                :country             ""
                                                :edi                 ""
                                                :einvoice-address    ""
                                                :id                  ""
                                                :name                ""
                                                :operator            ""
                                                :partner-code        ""
                                                :postalcode          ""
                                                :streetaddress       ""}
                                 :payer-type   "ORGANIZATION"}
                  :permit-id    "LP-753-2019-12345"
                  :property-id  "75341600110060"
                  :reference    ""
                  :target       {:street      "Fågelsångintie 46"
                                 :city        "Turku"
                                 :postal-code "12345"}})

            (fact "Application company payer with extra invoice fields"
              (->invoice-data invoicing-config
                              (invoices-util/enrich-invoice (assoc invoice :application-payer? true)
                                                            application))
              => (assoc base
                        :payer (assoc-in app-company-payer [:organization :partner-code] "bill buddy")
                        :reference "laskuviite"))

            (fact "Application company payer with minimal invoice fields"
              (->invoice-data invoicing-config
                              (invoices-util/enrich-invoice (assoc min-invoice :application-payer? true)
                                                            application))
              => {:case         ""
                  :customer     {:client-number ""}
                  :invoice-rows [{:code             "123"
                                  :discount-percent 0
                                  :name             "Toimenpidemaksu"
                                  :quantity         5
                                  :unit             "kpl"
                                  :unitprice        40}]
                  :invoice-type "EXTERNAL"
                  :letter       ""
                  :operation    "vapaa-ajan-asuinrakennus"
                  :payee        {:payee-group "3" :payee-organization-id "6" :payee-sector "1"}
                  :payer        (assoc-in app-company-payer [:organization :partner-code] "")
                  :permit-id    "LP-753-2019-12345"
                  :property-id  "75341600110060"
                  :reference    "laskuviite"
                  :target       {:street      "Fågelsångintie 46"
                                 :city        "Turku"
                                 :postal-code "12345"}})

            (fact "Application person payer with extra invoice fields"
              (->invoice-data invoicing-config
                              (invoices-util/enrich-invoice (assoc invoice :application-payer? true)
                                                            (assoc-in application
                                                                      [:documents 0 :data :_selected :value]
                                                                      "henkilo")))
              => (assoc base
                        :payer (assoc-in app-person-payer [:person :partner-code] "bill buddy")
                        :reference "laskuviite"))
            (fact "Application person payer with minimal invoice fields"
              (->invoice-data invoicing-config
                              (invoices-util/enrich-invoice (assoc min-invoice :application-payer? true)
                                                            (assoc-in application
                                                                      [:documents 0 :data :_selected :value]
                                                                      "henkilo")))
              => {:case         ""
                  :customer     {:client-number ""}
                  :invoice-rows [{:code             "123"
                                  :discount-percent 0
                                  :name             "Toimenpidemaksu"
                                  :quantity         5
                                  :unit             "kpl"
                                  :unitprice        40}]
                  :invoice-type "EXTERNAL"
                  :letter       ""
                  :operation    "vapaa-ajan-asuinrakennus"
                  :payee        {:payee-group "3" :payee-organization-id "6" :payee-sector "1"}
                  :payer        (assoc-in app-person-payer [:person :partner-code] "")
                  :permit-id    "LP-753-2019-12345"
                  :property-id  "75341600110060"
                  :reference    "laskuviite"
                  :target       {:street      "Fågelsångintie 46"
                                 :city        "Turku"
                                 :postal-code "12345"}}))

          (facts "Backend-id"
            (fact "Not enabled"
              (:backend-id (->invoice-data invoicing-config
                                           (invoices-util/enrich-invoice
                                             (assoc invoice :backend-id "HAZ0012")
                                             application)))
              => nil)
            (fact "Enabled"
              (:backend-id (->invoice-data (assoc invoicing-config :backend-id? true)
                                           (invoices-util/enrich-invoice
                                             (assoc invoice :backend-id "HAZ0012")
                                             application)))
              => "HAZ0012")))))))
