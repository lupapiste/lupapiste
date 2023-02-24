(ns lupapalvelu.invoices.general-api-xml-converter-test
  (:require  [clojure.test :refer :all]
             [clojure.string :as str]
             [schema.core :as sc]
             [sade.xml :as sxml]
             [lupapalvelu.invoices.general-api-xml-converter :as xml-converter]
             [lupapalvelu.invoices.schemas :refer [GeneralApiXMLInput]]
             [lupapalvelu.invoices.xml-validator :as xml-validator]))

(def mock-created 1589824759148) ; Timestamp for 2020-05-18T17:59:19Z

;; Mocks
(def invoice {:invoice-type "EXTERNAL"
              :reference    "12344345"
              :payer        {:payer-type   "ORGANIZATION"
                             :organization {:id                  "1234567-8"
                                            :partner-code        "Fellow firm"
                                            :name                "Firma Oy"
                                            :contact-firstname   "John"
                                            :contact-lastname    "Doe"
                                            :contact-turvakielto "false"
                                            :streetaddress       "Jokukatu 10"
                                            :postalcode          "00100"
                                            :city                "Helsinki"
                                            :country             "FIN"
                                            :einvoice-address    "12345671001"
                                            :edi                 "003712345671"
                                            :operator            "BAWCFI22"}}
              ;; from organization's invoicing-config
              :payee        {:payee-organization-id "12345"
                             :payee-group           "54321"
                             :payee-sector          "24680"}
              ;; from application
              :target       {:street "F\u00e5gels\u00e5ngintie 46"}
              :operation    "vapaa-ajan-asuinrakennus"
              ;; from invoice
              :permit-id    "LP-753-2019-90002"
              :customer     {:client-number "sapcustid9"}
              :invoice-rows [{:code              "123"
                              :name              "Toimenpidemaksu"
                              :unit              "kpl"
                              :quantity          5
                              :discount-percent  0
                              :product-constants {:kustannuspaikka  "3490"
                                                  :alv              "900"
                                                  :laskentatunniste "3310"
                                                  :projekti         "15"
                                                  :kohde            "5555"
                                                  :toiminto         "function"
                                                  :muu-tunniste     ""}
                              :unitprice         40}
                             {:code              "456"
                              :name              "Tarkastusmaksu"
                              :unit              "pv"
                              :quantity          2
                              :discount-percent  10.5
                              :product-constants {:kustannuspaikka  "a"
                                                  :alv              "b"
                                                  :laskentatunniste "c"
                                                  :projekti         "d"
                                                  :kohde            "e"
                                                  :muu-tunniste     "f"}
                              :unitprice         150.50}
                             {:code             "799"
                              :name             "Without product-constants"
                              :unit             "kpl"
                              :quantity         1
                              :discount-percent 0
                              :unitprice        300.00}]})

(def result-general-api-xml
  "<InvoiceTransfer created=\"2020-05-18T20:59:19+03:00\">
    <From>
      <System>
        <Id>Lupapiste</Id>
      </System>
    </From>
    <Invoices>
      <!-- Invoice-elementtejä yksi per lasku -->
      <Invoice>
                <!-- Jos sisäinen maksu -->
              <!--
                <Type>INTERNAL</Type>
              -->
        <!-- Jos ulkoinen maksu -->
        <Type>EXTERNAL</Type>
        <ApplicationContext>
          <Id>LP-753-2019-90002</Id>
          <StreetAddress>F\u00e5gels\u00e5ngintie 46</StreetAddress>
          <PermitType>vapaa-ajan-asuinrakennus</PermitType>
        </ApplicationContext>
        <Reference>12344345</Reference>
        <Payee>
          <Organization>
            <!-- Vakiot asetuksista -->
            <Id>12345</Id>
            <Group>54321</Group>
            <Sector>24680</Sector>
          </Organization>
        </Payee>
        <Payer>
          <!-- Jos maksaja on organisaatio -->
          <Type>ORGANIZATION</Type>
          <CustomerId>sapcustid9</CustomerId>
          <Organization>
            <Id>1234567-8</Id>
            <PartnerCode>Fellow firm</PartnerCode>
            <Name>Firma Oy</Name>
            <Contact>
              <FirstName>John</FirstName>
              <LastName>Doe</LastName>
              <Turvakielto>false</Turvakielto>
            </Contact>
            <Address>
              <StreetAddress>Jokukatu 10</StreetAddress>
              <PostalCode>00100</PostalCode>
              <City>Helsinki</City>
              <Country>FIN</Country>
            </Address>
            <EInvoiceAddress>12345671001</EInvoiceAddress>
            <EDI>003712345671</EDI>
            <Operator>BAWCFI22</Operator>
          </Organization>
                    <!-- Jos maksaja on henkilö -->
                  <!--
                    <Type>PERSON</Type>
                    <Person>
                      <Id>121212-0123</Id>
                      <FirstName>John</FirstName>
                      <LastName>Doe</LastName>
                      <Turvakielto>true</Turvakielto>
                      <Address>
                        <StreetAddress>Jokukatu 10</StreetAddress>
                        <PostalCode>00100</PostalCode>
                        <City>Helsinki</City>
                        <Country>Finland</Country>
                      </Address>
                    </Person>
                  -->
        </Payer>
        <Rows>
          <!-- Row-elementtejä yksi per laskurivi -->
          <Row>
            <Product>
              <Name>123 Toimenpidemaksu</Name>
              <Unit>PIECE</Unit>
              <Quantity>5.00</Quantity>
              <DiscountPercent>0.00</DiscountPercent>
              <UnitPrice>40.00</UnitPrice>
              <ProductConstants>
                  <CostCentre>3490</CostCentre>
                  <VAT>900</VAT>
                  <CalculationTag>3310</CalculationTag>
                  <Project>15</Project>
                  <Target>5555</Target>
                  <Function>function</Function>
              </ProductConstants>
            </Product>
          </Row>
          <Row>
            <Product>
              <Name>456 Tarkastusmaksu</Name>
              <Unit>DAY</Unit>
              <Quantity>2.00</Quantity>
              <DiscountPercent>10.50</DiscountPercent>
              <UnitPrice>150.50</UnitPrice>
              <ProductConstants>
                  <CostCentre>a</CostCentre>
                  <VAT>b</VAT>
                  <CalculationTag>c</CalculationTag>
                  <Project>d</Project>
                  <Target>e</Target>
                  <OtherTag>f</OtherTag>
              </ProductConstants>
            </Product>
          </Row>
          <Row>
            <Product>
              <Name>799 Without product-constants</Name>
              <Unit>PIECE</Unit>
              <Quantity>1.00</Quantity>
              <DiscountPercent>0.00</DiscountPercent>
              <UnitPrice>300.00</UnitPrice>
            </Product>
          </Row>
        </Rows>
      </Invoice>
    </Invoices>
  </InvoiceTransfer>")


(def system-id "Lupapiste")

(defn content
  "Gets the content value of an element that supposedly does not have child elements"
  [element]
  (-> element :content first))

(deftest validate-invoice-input
  (testing "validate invoice"
    (sc/validate GeneralApiXMLInput invoice)))

(deftest generating-general-api-xml
  (testing "->xml"

    (testing "given one invoice"

      (let [xml (xml-converter/->xml mock-created system-id [invoice])]
        (testing "given one invoice, creates a proper XML message"
          (is (= (sxml/parse result-general-api-xml)
                 (sxml/parse xml))
              "having identical data with the expected XML string"))

        (testing "given one invoice, created XML message passes XML validation"
          (xml-validator/validate xml :general-api))))

    (testing "Partner code is optional"
      (let [xml    (xml-converter/->xml mock-created
                                        system-id
                                        [(assoc-in invoice
                                                   [:payer :organization :partner-code] "")])
            parsed (sxml/parse xml)]
        (testing "XML is generated but without partner code"
          (xml-validator/validate xml :general-api)
          (is (= ["1234567-8"]
                 (:content (sxml/select1 parsed [:Invoice :Payer :Organization :Id]))))
          (is (nil? (sxml/select1 parsed [:Invoice :Payer :Organization :PartnerCode]))))))


    (let [invoices        [invoice invoice]
          result-xml      (xml-converter/->xml mock-created system-id invoices)
          parsed-xml      (sxml/parse result-xml)
          xml-as-edn      (sxml/xml->edn parsed-xml)
          ;; NOTE: sxml/xml->edn works the collections like this:
          ;;   from xml {:Invoices [{:Invoice {...}}, {:Invoice {...}}]}
          ;;   to edn   {:Invoices {:Invoice [{...}, {...}]}}
          result-invoices (get-in xml-as-edn [:InvoiceTransfer :Invoices :Invoice])
          first-invoice   (first result-invoices)
          second-invoice  (second result-invoices)]

      (testing "given 2 invoices"

        (testing "creates a general api xml string that matches the invoices"

          (is (str/starts-with? result-xml "<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
              "having an XML document declaration"))

        (testing "creates a proper general api xml from an invoice edn representation"

          (is (= :InvoiceTransfer (:tag parsed-xml))
              "with root element :InvoiceTransfer")

          (is (= 2 (count result-invoices))
              "with 2 invoices (that means :Invoice elements)")

          (testing "the first invoice with"

            (is (= "EXTERNAL" (:Type first-invoice))
                "<Type>EXTERNAL</Type>")

            (is (= {:Id            "LP-753-2019-90002",
                    :StreetAddress "Fågelsångintie 46",
                    :PermitType    "vapaa-ajan-asuinrakennus"}
                  (:ApplicationContext first-invoice))
                "correct <ApplicationContext> data")

            (is (= "12344345" (:Reference first-invoice))
                "<Reference>12344345</Reference>")

            (let [payee (:Payee first-invoice)]
              (testing "Payee"
                (is (= {:Organization {:Id "12345" :Group "54321" :Sector "24680"}} payee)
                    "correct <Payee> data")))

            (let [payer (:Payer first-invoice)]
              (testing "Payer that has"

                (is (= "ORGANIZATION" (:Type payer))
                    "<Type>ORGANIZATION</Type>")

                (is (= "sapcustid9" (:CustomerId payer))
                    "<CustomerId>sapcustId9</CustomerId>")

                (testing "Organization with"
                  (is (= "1234567-8" (get-in payer [:Organization :Id]))
                      "<Id>1234567-8</Id>")

                  (is (= "Fellow firm" (get-in payer [:Organization :PartnerCode]))
                      "<PartnerCode>Fellow firm</PartnerCode>")

                  (is (= "Firma Oy" (get-in payer [:Organization :Name]))
                      "<Name>Firma Oy</Name>")

                  (is (= {:FirstName   "John",
                          :LastName    "Doe",
                          :Turvakielto "false"}
                         (get-in payer [:Organization :Contact]))
                      "correct Contact data")

                  (is (= {:StreetAddress "Jokukatu 10" :PostalCode "00100" :City "Helsinki" :Country "FIN"}
                         (get-in payer [:Organization :Address]))
                      "correct Address data")

                  (is (= "12345671001" (get-in payer [:Organization :EInvoiceAddress]))
                      "<EInvoiceAddress>12345671001</EInvoiceAddress>")

                  (is (= "003712345671" (get-in payer [:Organization :EDI]))
                      "<EDI>003712345671</EDI>")

                  (is (= "BAWCFI22" (get-in payer [:Organization :Operator]))
                      "<Operator>BAWCFI22</Operator>"))))

            (let [rows (get-in first-invoice [:Rows :Row])]
              (testing "Rows"
                (is (= 3 (count rows))
                    "Row count is 3")

                (testing "First Row"
                  (is (= {:Product {:Name             "123 Toimenpidemaksu"
                                    :Unit             "PIECE"
                                    :Quantity         "5.00"
                                    :DiscountPercent  "0.00"
                                    :UnitPrice        "40.00"
                                    :ProductConstants {:CostCentre     "3490"
                                                       :VAT            "900"
                                                       :CalculationTag "3310"
                                                       :Project        "15"
                                                       :Target         "5555"
                                                       :Function       "function"}}}
                         (first rows))
                      "<Operator>BAWCFI22</Operator>"))

                (testing "Second Row"
                  (is (= {:Product {:Name             "456 Tarkastusmaksu"
                                    :Unit             "DAY"
                                    :Quantity         "2.00"
                                    :DiscountPercent  "10.50"
                                    :UnitPrice        "150.50"
                                    :ProductConstants {:CostCentre     "a"
                                                       :VAT            "b"
                                                       :CalculationTag "c"
                                                       :Project        "d"
                                                       :Target         "e"
                                                       :OtherTag       "f"}}}
                         (second rows))
                      "<Operator>BAWCFI22</Operator>"))

                (testing "Third row (without product-constants)"
                  (is (= {:Product {:Name            "799 Without product-constants"
                                    :Unit            "PIECE"
                                    :Quantity        "1.00"
                                    :DiscountPercent "0.00"
                                    :UnitPrice       "300.00"}}
                         (nth rows 2)))))))


          (testing "the second invoice"
            (is (= second-invoice
                   {:Type               "EXTERNAL"
                    :ApplicationContext {:Id            "LP-753-2019-90002"
                                         :StreetAddress "Fågelsångintie 46"
                                         :PermitType    "vapaa-ajan-asuinrakennus"}
                    :Reference          "12344345"
                    :Payee              {:Organization {:Id "12345" :Group "54321" :Sector "24680"}}
                    :Payer              {:Type         "ORGANIZATION"
                                         :CustomerId   "sapcustid9"
                                         :Organization {:Id              "1234567-8"
                                                        :PartnerCode     "Fellow firm"
                                                        :Name            "Firma Oy"
                                                        :Contact         {:FirstName   "John"
                                                                          :LastName    "Doe"
                                                                          :Turvakielto "false"}
                                                        :Address         {:StreetAddress "Jokukatu 10"
                                                                          :PostalCode    "00100"
                                                                          :City          "Helsinki"
                                                                          :Country       "FIN"}
                                                        :EInvoiceAddress "12345671001"
                                                        :EDI             "003712345671"
                                                        :Operator        "BAWCFI22"}}
                    :Rows               {:Row '({:Product {:Name             "123 Toimenpidemaksu"
                                                           :Unit             "PIECE"
                                                           :Quantity         "5.00"
                                                           :DiscountPercent  "0.00"
                                                           :UnitPrice        "40.00"
                                                           :ProductConstants {:CostCentre     "3490"
                                                                              :VAT            "900"
                                                                              :CalculationTag "3310"
                                                                              :Project        "15"
                                                                              :Target         "5555"
                                                                              :Function       "function"}}}
                                                {:Product {:Name             "456 Tarkastusmaksu"
                                                           :Unit             "DAY"
                                                           :Quantity         "2.00"
                                                           :DiscountPercent  "10.50"
                                                           :UnitPrice        "150.50"
                                                           :ProductConstants {:CostCentre     "a"
                                                                              :VAT            "b"
                                                                              :CalculationTag "c"
                                                                              :Project        "d"
                                                                              :Target         "e"
                                                                              :OtherTag       "f"}}}
                                                {:Product {:Name            "799 Without product-constants"
                                                           :Unit            "PIECE"
                                                           :Quantity        "1.00"
                                                           :DiscountPercent "0.00"
                                                           :UnitPrice       "300.00"}})}}))))))

    (testing "BackendId is optional"
      (testing "XML is generated without BackendId"
        (let [xml    (xml-converter/->xml mock-created system-id [invoice])
              parsed (sxml/parse xml)]
          (xml-validator/validate xml :general-api)
          (is (nil? (sxml/select1 parsed [:Invoice :BackendId])))))
      (testing "XML is generated with BackendId"
        (let [xml    (xml-converter/->xml mock-created system-id [(assoc invoice :backend-id "HAZ00028")])
              parsed (sxml/parse xml)]
          (xml-validator/validate xml :general-api)
          (is (= ["HAZ00028"]
                 (:content (sxml/select1 parsed [:Invoice :BackendId])))))))))

(deftest ->OptionalElement
  (testing "Fullish"
    (is (= (sxml/->Element :Foo nil "hello")
           (xml-converter/->OptionalElement :Foo "hello")))
    (is (= (sxml/->Element :Foo nil  ["hello" "world"])
           (xml-converter/->OptionalElement :Foo ["hello" "world"])))
    (is (= (sxml/->Element :Foo nil 0)
           (xml-converter/->OptionalElement :Foo 0))))
  (testing "Emptyish"
    (is (nil? (xml-converter/->OptionalElement :Foo nil)))
        (is (nil? (xml-converter/->OptionalElement :Foo "")))
    (is (nil? (xml-converter/->OptionalElement :Foo "  ")))
    (is (nil? (xml-converter/->OptionalElement :Foo [])))))
