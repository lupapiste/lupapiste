(ns lupapalvelu.invoices.transfer-batch-test
  (:require [lupapalvelu.document.model :as dm]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.invoices.transfer-batch :refer :all]
            [lupapalvelu.itest-util :refer [expected-failure?]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core]
            [sade.strings :as ss]
            [sade.util :as util]))

(testable-privates lupapalvelu.invoices.transfer-batch
                   ->idoc-invoice-data
                   filename-for-xml-sap
                   filename-for-xml-sftp
                   ->invoice-data
                   delivery-type
                   validate-config!
                   sftp-connect-info
                   tidywalk
                   parse-person-name
                   save-xml-to-local-sftp!)

(def dummy-product-constants {:kustannuspaikka  "a"
                              :alv              "b"
                              :laskentatunniste "c"
                              :projekti         "d"
                              :kohde            "e"
                              :toiminto         "f"
                              :muu-tunniste     "g"})

(facts "->invoice-transfer-batch-db"
  (let [user-data           {:foo       "some-value"
                             :id        "penan-id"
                             :firstName "pena"
                             :lastName  "panaani"
                             :role      "authority"
                             :email     "pena@panaani.fi"
                             :username  "pena"}
        org-id              "foo-org-id"
        transfer-batch-data {}
        test-timestamp      123456]
    (fact "returns proper invoice-transfer-batch-ready-to-be-stored-to-database"
      (->invoice-transfer-batch-db transfer-batch-data org-id user-data test-timestamp)
      => {:organization-id org-id
          :created         test-timestamp
          :created-by      {:id        "penan-id"
                            :firstName "pena"
                            :lastName  "panaani"
                            :role      "authority"
                            :username  "pena"}
          :invoices        []
          :state "open"
          :number-of-rows  0
          :sum             {:currency "EUR" :major 0 :minor 0 :text ""}})))

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

(def invoice {:application-id "LP-753-2019-12345"
              :description "Dummy Description"
              :operations     [{:operation-id "linjasaneeraus"
                                :name         "linjasaneeraus"
                                :invoice-rows [{:text              "Kadun puhkurointi"
                                                :type              "from-price-catalogue"
                                                :unit              "kpl"
                                                :price-per-unit    10
                                                :units             2
                                                :discount-percent  0
                                                :product-constants dummy-product-constants}
                                               {:text             "Pientareen rompulointi"
                                                :type             "from-price-catalogue"
                                                :unit             "kpl"
                                                :price-per-unit   50
                                                :units            1
                                                :discount-percent 0}]}]

              :application    {:address          "Latokuja 3"
                               :primaryOperation {:name "ya-katulupa-veri-ja-viemarityot"}}
              :sap-bill-date  1559120400000 ;;29.5.2019 12:00 finnish time
              })

(facts "->idoc-invoice-data"
  (fact "returns data with proper fields"
    (let [invoice-with-sap-number (assoc invoice :sap-number "sap-123")]

      (->idoc-invoice-data org-config invoice-with-sap-number) => {:sap-integration-id      "110"
                                                                   :sap-ordertype           "ZLUP"
                                                                                  :sap-salesorganization   "1111"
                                                                                  :sap-distributionchannel "00"
                                                                                  :sap-division            "00"
                                                                                  :permit-id               "LP-753-2019-12345"
                                                                                  :operation               "ya-katulupa-veri-ja-viemarityot"
                                                                                  :sap-term-id             "2106" ;;vakio
                                                                                  :description "Dummy Description"
                                                                                  :target                  {:street "Latokuja 3"}
                                                                                  :sap-bill-date           1559120400000
                                                                                  :customer                {:sap-number "sap-123"}
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
                                                                                                             :text             "Pientareen rompulointi"}]})))

(facts "->idoc-invoice-data"
  (fact "works when sap-number is missing from the invoice"
    (->idoc-invoice-data org-config invoice) => {:sap-integration-id      "110"
                                                                :sap-ordertype           "ZLUP"
                                                                :sap-salesorganization   "1111"
                                                                :sap-distributionchannel "00"
                                                                :sap-division            "00"
                                                                :permit-id               "LP-753-2019-12345"
                                                                :description             "Dummy Description"
                                                                :operation               "ya-katulupa-veri-ja-viemarityot"
                                                                :sap-term-id             "2106" ;;vakio
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
                                                                                           :text             "Pientareen rompulointi"}]}))

(fact "filenames"
  (filename-for-xml-sap "ID334_Lupapiste_" 1560772700083)
  => "ID334_Lupapiste_17.6.2019_14.58.20_1560772700083.xml"
  (filename-for-xml-sftp "Prefix_" 1560772700083)
  => "Prefix_190617145820.xml")

(facts "->invoice-data"
  (facts "returns data with proper fields"
    (let [invoicing-config {:integration-url                       "url"
                            :credentials                           {:username "username" :password "password"}
                            :integration-requires-customer-number? false
                            :constants                             {:sektori      "1"
                                                                    :nimike       "2"
                                                                    :jakelutie    "3"
                                                                    :tulosyksikko "4"
                                                                    :laskuttaja   "5"
                                                                    :myyntiorg    "6"
                                                                    :tilauslaji   "7"}}

          payer-doc        {:schema-info {:name "maksaja" :version 1 :type "party" :subtype "maksaja"}
                            :data        {:_selected  {:value "yritys"}
                                          :laskuviite {:value "laskuviite"}
                                          :yritys     {:companyId            {:value "1234567-1"}
                                                       :yritysnimi           {:value "Esimerkki Oy"}
                                                       :liikeJaYhteisoTunnus {:value "1234567-1"}
                                                       :osoite               {:katu                 {:value "Merkintie 88"}
                                                                              :postinumero          {:value "12345"}
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
                                                                              :valittajaTunnus   {:value "BAWCFI22"}}}}}

          application      {:address          "Fågelsångintie 46"
                            :primaryOperation {:name "vapaa-ajan-asuinrakennus"}
                            :documents        [payer-doc]}

          invoice          {:application-id    "LP-753-2019-12345"
                            :application       application
                            :organization-id   "753-R"
                            :sum               {:major 0 :minor 0 :text "EUR0.00" :currency "EUR"}
                            :sap-number        "sapcustid9"
                            :company-id        "1234567-1"
                            :entity-name       "Esimerkki Oy"
                            :entity-address    "Merkintie 88 12345 Humppila"
                            :ovt               "003710601555"
                            :billing-reference "laskuviite"
                            :operations        [{:name         "vapaa-ajan-asuinrakennus"
                                                 :operation-id "vapaa-ajan-asuinrakennus"
                                                 :invoice-rows [{:code              "123"
                                                                 :text              "Toimenpidemaksu"
                                                                 :type              "from-price-catalogue"
                                                                 :unit              "kpl"
                                                                 :units             5
                                                                 :price-per-unit    40
                                                                 :discount-percent  0
                                                                 :product-constants dummy-product-constants
                                                                 :sums              {:with-discount    {:currency "EUR" :major 200 :minor 20000 :text "EUR200.00"}
                                                                                     :without-discount {:currency "EUR" :major 200 :minor 20000 :text "EUR200.00"}}}]}
                                                {:name         "puun-kaataminen"
                                                 :operation-id "puun-kaataminen"
                                                 :invoice-rows [{:code             "456"
                                                                 :text             "Tarkastusmaksu"
                                                                 :type             "from-price-catalogue"
                                                                 :unit             "pv"
                                                                 :units            2
                                                                 :price-per-unit   150.5
                                                                 :discount-percent 10
                                                                 :sums             {:with-discount    {:currency "EUR" :major 301 :minor 30100 :text "EUR301.00"}
                                                                                    :without-discount {:currency "EUR" :major 301 :minor 30100 :text "EUR301.00"}}}]}
                                                {:name         "linjasaneeraus"
                                                 :operation-id "linjasaneeraus"
                                                 :invoice-rows [{:code              "111"
                                                                 :text              "Toimittajan oma maksu"
                                                                 :type              "from-price-catalogue"
                                                                 :unit              "kpl"
                                                                 :price-per-unit    1000
                                                                 :units             1
                                                                 :discount-percent  0
                                                                 :product-constants (assoc dummy-product-constants
                                                                                           :kustannuspaikka "  "
                                                                                           :alv nil)
                                                                 :sums              {:with-discount    {:currency "EUR" :major 1000 :minor 100000 :text "EUR1000.00"}
                                                                                     :without-discount {:currency "EUR" :major 1000 :minor 100000 :text "EUR1000.00"}}}]}]}]

      (fact "payer document is valid"
        (let [schema            (schemas/get-schema (schemas/get-latest-schema-version) "maksaja")
              validation-result (dm/validate application payer-doc schema)]
          validation-result => empty?))

      (fact "company payer"
        (->invoice-data invoicing-config invoice)
        => {:invoice-type "EXTERNAL"
            :reference    "laskuviite"
            :payer        {:payer-type   "ORGANIZATION"
                           :organization {:id                  "1234567-1"
                                          :partner-code        ""
                                          :name                "Esimerkki Oy"
                                          :contact-firstname   "Esko"
                                          :contact-lastname    "Ala-Pentti"
                                          :contact-turvakielto "false"
                                          :streetaddress       "Merkintie 88"
                                          :postalcode          "12345"
                                          :city                "Humppila"
                                          :country             "FIN"
                                          :einvoice-address    "samplebilling"
                                          :edi                 "003710601555"
                                          :operator            "BAWCFI22"}}
            ;; from organization's invoicing-config
            :payee        {:payee-organization-id "6"
                           :payee-group           "3"
                           :payee-sector          "1"}
            ;; from application
            :target       {:street "F\u00e5gels\u00e5ngintie 46"}
            :operation    "vapaa-ajan-asuinrakennus"
            ;; from invoice
            :permit-id    "LP-753-2019-12345"
            :customer     {:client-number "sapcustid9"}
            :invoice-rows [{:code              "123"
                            :name              "Toimenpidemaksu"
                            :unit              "kpl"
                            :quantity          5
                            :discount-percent  0
                            :unitprice         40
                            :product-constants dummy-product-constants}
                           {:code             "456"
                            :name             "Tarkastusmaksu"
                            :unit             "pv"
                            :quantity         2
                            :discount-percent 10
                            :unitprice        150.50}
                           {:code              "111"
                            :name              "Toimittajan oma maksu"
                            :unit              "kpl"
                            :quantity          1
                            :discount-percent  0
                            :unitprice         1000
                            :product-constants (dissoc dummy-product-constants :kustannuspaikka :alv)}]})
      (fact "company payer with nil valittajaTunnus"
        (let [payer-doc   (assoc-in payer-doc [:data :yritys :verkkolaskutustieto :valittajaTunnus] nil)
              application (assoc application :documents [payer-doc])]
          (fact "payer doc is valid"
            (let [schema            (schemas/get-schema (schemas/get-latest-schema-version) "maksaja")
                  validation-result (dm/validate application payer-doc schema)]
              validation-result => empty?))
          (fact "->invoice-data"
            (-> (->invoice-data invoicing-config (assoc invoice :application application))
                :payer :organization)
            => {:id                  "1234567-1"
                :partner-code        ""
                :name                "Esimerkki Oy"
                :contact-firstname   "Esko"
                :contact-lastname    "Ala-Pentti"
                :contact-turvakielto "false"
                :streetaddress       "Merkintie 88"
                :postalcode          "12345"
                :city                "Humppila"
                :country             "FIN"
                :einvoice-address    "samplebilling"
                :edi                 "003710601555"
                :operator            ""})))
      (fact "Company payer for invoice with :operator and :company-contact-person"
        (fact "->invoice-data"
          (-> (->invoice-data invoicing-config (assoc invoice
                                                      :application application
                                                      :operator "HANDFIHH"
                                                      :company-contact-person "Tim Apple"))
              :payer :organization)
          => {:id                  "1234567-1"
              :partner-code        ""
              :name                "Esimerkki Oy"
              :contact-firstname   "Tim"
              :contact-lastname    "Apple"
              :contact-turvakielto "false"
              :streetaddress       "Merkintie 88"
              :postalcode          "12345"
              :city                "Humppila"
              :country             "FIN"
              :einvoice-address    "samplebilling"
              :edi                 "003710601555"
              :operator            "HANDFIHH"})
        (fact "->invoice-data with empty fields"
          (-> (->invoice-data invoicing-config (assoc invoice
                                                      :application application
                                                      :operator ""
                                                      :company-contact-person ""))
              :payer :organization)
          => {:id                  "1234567-1"
              :partner-code        ""
              :name                "Esimerkki Oy"
              :contact-firstname   ""
              :contact-lastname    ""
              :contact-turvakielto "false"
              :streetaddress       "Merkintie 88"
              :postalcode          "12345"
              :city                "Humppila"
              :country             "FIN"
              :einvoice-address    "samplebilling"
              :edi                 "003710601555"
              :operator            ""}))
      (fact ":payer-type overrides :company-id"
        (some-> (->invoice-data invoicing-config (assoc invoice
                                                        :application application
                                                        :payer-type "person"))
                :payer :organization) => nil
        (some-> (->invoice-data invoicing-config (-> invoice
                                                     (dissoc :company-id)
                                                     (assoc :application application
                                                            :payer-type "company")))
                :payer :organization)
        => {:id                  ""
            :partner-code        ""
            :name                "Esimerkki Oy"
            :contact-firstname   "Esko"
            :contact-lastname    "Ala-Pentti"
            :contact-turvakielto "false"
            :streetaddress       "Merkintie 88"
            :postalcode          "12345"
            :city                "Humppila"
            :country             "FIN"
            :einvoice-address    "samplebilling"
            :edi                 "003710601555"
            :operator            "BAWCFI22"})

      (fact "Company payer with partner code"
        (-> (->invoice-data invoicing-config (assoc invoice :partner-code "  my code "))
            :payer :organization)
        => {:id                  "1234567-1"
            :partner-code        "my code"
            :name                "Esimerkki Oy"
            :contact-firstname   "Esko"
            :contact-lastname    "Ala-Pentti"
            :contact-turvakielto "false"
            :streetaddress       "Merkintie 88"
            :postalcode          "12345"
            :city                "Humppila"
            :country             "FIN"
            :einvoice-address    "samplebilling"
            :edi                 "003710601555"
            :operator            "BAWCFI22"})


      (fact "person payer"
        (let [payer-doc-henkilo (-> payer-doc
                                    (assoc-in [:data :_selected :value] "henkilo")
                                    (assoc-in [:data :henkilo] {:userId        {:value "777777777777777777000020"}
                                                                :henkilotiedot {:etunimi           {:value "Not"}
                                                                                :sukunimi          {:value "Used"}
                                                                                :hetu              {:value "010203-040A"}
                                                                                :turvakieltoKytkin {:value false}}
                                                                :osoite        {:katu                 {:value "Paapankuja 12"}
                                                                                :postinumero          {:value "33333"}
                                                                                :postitoimipaikannimi {:value "Piippola"}
                                                                                :maa                  {:value "FIN"}}
                                                                :yhteystiedot  {:puhelin {:value "0102030405"}
                                                                                :email   {:value "pena@example.com"}}
                                                                :kytkimet      {:suoramarkkinointilupa {:value true}}})
                                    (util/dissoc-in [:data :yritys]))
              invoice           (-> invoice
                                    (merge {:person-id         "010203-040A"
                                            :entity-name       "Pena Panaani"
                                            :entity-address    "Paapankuja 12 33333 Piippola"
                                            :billing-reference "laskuviite"})
                                    (dissoc :company-id :ovt :company-contact-person)
                                    (assoc-in [:application :documents] [payer-doc-henkilo]))]

          (->invoice-data invoicing-config invoice)
          => (contains {:payer {:payer-type "PERSON"
                                :person     {:id            "010203-040A"
                                             :partner-code  ""
                                             :firstname     "Pena"
                                             :lastname      "Panaani"
                                             :turvakielto   "false"
                                             :streetaddress "Paapankuja 12"
                                             :postalcode    "33333"
                                             :city          "Piippola"
                                             :country       "FIN"}}})
          (fact ":payer-type overrides :person-id"
            (some-> (->invoice-data invoicing-config (assoc invoice :payer-type "company"))
                    :payer :person) => nil
            (some-> (->invoice-data invoicing-config (-> invoice
                                                         (dissoc :person-id)
                                                         (assoc :payer-type "person")))
                    :payer :person)
            => {:id            ""
                :partner-code  ""
                :firstname     "Pena"
                :lastname      "Panaani"
                :turvakielto   "false"
                :streetaddress "Paapankuja 12"
                :postalcode    "33333"
                :city          "Piippola"
                :country       "FIN"})
          (fact "Person payer with partner code"
            (-> (->invoice-data invoicing-config
                                (assoc invoice :partner-code "   My code  "))
                :payer :person)
            => {:id            "010203-040A"
                :partner-code  "My code"
                :firstname     "Pena"
                :lastname      "Panaani"
                :turvakielto   "false"
                :streetaddress "Paapankuja 12"
                :postalcode    "33333"
                :city          "Piippola"
                :country       "FIN"})))

      (facts "Backend-id"
        (fact "Not enabled"
          (:backend-id (->invoice-data invoicing-config
                                       (assoc invoice :backend-id "HAZ0012")))
          => nil)
        (fact "Enabled"
          (:backend-id (->invoice-data (assoc invoicing-config :backend-id? true)
                                       (assoc invoice :backend-id "HAZ0012")))
          => "HAZ0012")))))

(facts "delivery-type"
  (fact "sftp"
    (delivery-type {:url-data {:scheme "sftp"}}) => :sftp)
  (fact "local sftp"
    (delivery-type {:local-sftp? true}) => :local-sftp)
  (fact "any other"
    (delivery-type {:url-data {:scheme "any-other"}}) => :http
    (delivery-type nil) => :http
    (delivery-type {:local-sftp? false}) => :http))

(let [invoicing-config {:integration-url "x"
                        :credentials {:username "username"
                                      :password "password"}}]
  (facts "validate-credentials!"

    (fact "valid config"
      (validate-config! invoicing-config) => nil)

    (fact "invalid integration-url"
      (validate-config! (assoc invoicing-config :integration-url "  \n ")) => (partial expected-failure? :error.invalid-configuration))

    (fact "blank username"
      (validate-config! (assoc-in invoicing-config [:credentials :username] "  \n ")) => (partial expected-failure? :error.invalid-configuration))

    (fact "missing field: username"
      (try
        (validate-config! (util/dissoc-in invoicing-config [:credentials :username]))
        (catch clojure.lang.ExceptionInfo e
          (ex-data e))) => (contains {:type :schema.core/error
                                      :error {:credentials {:username (symbol "missing-required-key")}}}))

    (fact "missing field: password"
      (validate-config! (util/dissoc-in invoicing-config [:credentials :password])) => (partial expected-failure? :error.invalid-configuration))

    (fact "extra fields in credentials"
      (try
        (validate-config! (assoc-in invoicing-config [:credentials :foo] "bar"))
        (catch clojure.lang.ExceptionInfo e
          (ex-data e))) => (contains {:type :schema.core/error
                                      :error {:credentials {:foo (symbol "disallowed-key")}}}))

    (fact "Local sftp"
      (validate-config! {:local-sftp? true}) => nil
      (validate-config! (assoc invoicing-config :integration-url "  \n " :local-sftp? true)) => nil
      (validate-config! (assoc invoicing-config :integration-url "  \n " :local-sftp? false))
      => (partial expected-failure? :error.invalid-configuration)))

  ;; :url-data is added later to the invoicing config, in the function deliver-general-transfer-batch-to-invoicing-system!
  (let [invoicing-config (assoc invoicing-config :url-data {:path "/tama/on/tiedoston/polku"
                                                            :host "host"
                                                            :scheme "sftp"})]
    (facts "sftp-connect-info"

      (fact "for authentication method: username/password"
        (sftp-connect-info invoicing-config "filename") => {:host "host"
                                                            :remote-sftp-file-path "tama/on/tiedoston/polku/filename"
                                                            :auth {:username "username" :password "password"}})

      (fact "for authentication method: ssh key"
        (sftp-connect-info
          (util/dissoc-in invoicing-config [:credentials :password])
          "filename") => (just {:host "host"
                                :remote-sftp-file-path "tama/on/tiedoston/polku/filename"
                                :auth (just {:username "username"
                                             :private-key-path #(ss/ends-with-i % "/invoices_id_rsa")})}))))

  (let [invoicing-config (assoc invoicing-config :url-data {:path ""
                                                            :host "host"
                                                            :scheme "sftp"})]
    (facts "sftp-connect-info"

      (fact "empty path works"
        (sftp-connect-info invoicing-config "filename")
        => {:host                  "host"
            :remote-sftp-file-path "filename"
            :auth                  {:username "username" :password "password"}})
      (fact "nil path works"
        (sftp-connect-info (assoc invoicing-config :path nil) "filename")
        => {:host                  "host"
            :remote-sftp-file-path "filename"
            :auth                  {:username "username" :password "password"}})
      (fact "slash path works"
        (sftp-connect-info (assoc invoicing-config :path "/") "filename")
        => {:host                  "host"
            :remote-sftp-file-path "filename"
            :auth                  {:username "username" :password "password"}}))))

(facts "tidywalk"
  (tidywalk nil) => nil
  (tidywalk []) => []
  (tidywalk "  hello  ") => "hello"
  (tidywalk :foo) => :foo
  (tidywalk 123) => 123
  (tidywalk {:number 123
             :string "  string  "
             :nil    nil
             :map    {:one   "  one  "
                      :two   [1 2 "  3  " nil 4]
                      :three :three
                      :four  nil}})
  => {:number 123
      :string "string"
      :nil    ""
      :map    {:one   "one"
               :two   [1 2 "3" "" 4]
               :three :three
               :four  ""}})

(facts "parse-person-name"
  (parse-person-name nil) => ["" ""]
  (parse-person-name "") => ["" ""]
  (parse-person-name "    ") => ["" ""]
  (fact "Single word is interpreted as the lastname"
    (parse-person-name "Lastname") => ["" "Lastname"])

  (parse-person-name "First Last") => ["First" "Last"]
  (parse-person-name "First Middle Last") => ["First Middle" "Last"]
  (parse-person-name "  First   One   Two    Three   Last  ") => ["First One Two Three" "Last"]
  (fact "Lastname is always one word"
    (parse-person-name "First van Last") => ["First van" "Last"]))

(fact "filename-prefix"
  (filename-prefix {:empty "config"}) => ""
  (filename-prefix {:invoice-file-prefix "prefix"}) => "prefix"
  (filename-prefix {:invoice-file-prefix "prefix" :default-invoice-file-prefix "default-value"}) => "prefix"
  (filename-prefix {:invoice-file-prefix "" :default-invoice-file-prefix "default-value"}) => "default-value"
  (filename-prefix {:invoice-file-prefix "    " :default-invoice-file-prefix "default-value"}) => "default-value"
  (filename-prefix {:invoice-file-prefix "  with-surrounding-whitespace  " :default-invoice-file-prefix "default-value"}) => "with-surrounding-whitespace"
  (filename-prefix {:invoice-file-prefix "  with    _a_    hole  " :default-invoice-file-prefix "default-value"}) => "with_a_hole"
  (filename-prefix {:default-invoice-file-prefix "default-value"}) => "default-value")
