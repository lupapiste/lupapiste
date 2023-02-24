(ns lupapalvelu.invoices.ya-sap-converter-test
  (:require  [clojure.test :refer :all]
             [lupapalvelu.invoices.schemas :refer [IdocSapInput]]
             [lupapalvelu.invoices.xml-validator :refer [validate]]
             [lupapalvelu.invoices.ya-sap-converter :as xml-converter]
             [sade.xml :refer [select1 select] :as sxml]
             [schema.core :as s]))

;; Mocks
(def invoice {:sap-integration-id "334"

              ;; Laskukohtainen data
              :sap-ordertype "ZLUP" ;; vakio

              :sap-salesorganization "1111" ;;vakio
              :sap-distributionchannel "00" ;;vakio
              :sap-division "00"            ;;vakio

              :permit-id "LP-753-2019-90002"
              :description "LP-753-2019-90002"
              :operation "Katulupa"

              :target {:street "Viinikankatu 9"}

              :sap-bill-date 1551780000000 ;;kirjauspvm 3.5.2019 12:00

              :sap-term-id "2106"         ;;vakio

              :invoice-rows [{:sap-materialid "502557" ;;vakio?
                              :quantity 1
                              :unitprice 60.00
                              :sap-plant "1111"
                              :sap-profitcenter "111886" ;;vakio
                              :text "Maksuvyöhyke II, Alle 60 m2"}
                             {:sap-materialid "512345" ;;vakio?
                              :quantity 2
                              :unitprice 20.00
                              :sap-plant "1110"
                              :sap-profitcenter "111886" ;;vakio
                              :text "Maksuvyöhyke III"}]
              ;; Asiakas
              :customer {:sap-number "sapcustid9"}})


(defn invoice-with [data]
  (merge invoice data))

(def idoc-xml
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<SalesOrder>
  <Header>
    <Customer>
      <SapID>sapcustid9</SapID>
    </Customer>
    <BillingDate>2019-03-05+02:00</BillingDate>
    <BillNumber>LP-753-2019-90002</BillNumber>
    <SalesOrganisation>1111</SalesOrganisation>
    <DistributionChannel>00</DistributionChannel>
    <Division>00</Division>
    <SalesOrderType>ZLUP</SalesOrderType>
    <Description>LP-753-2019-90002</Description>
    <InterfaceID>334</InterfaceID>
    <Text>
      <TextRow>Viinikankatu 9</TextRow>
      <TextRow>Katulupa LP-753-2019-90002</TextRow>
    </Text>
    <Items>
      <Item>
        <Description>Maksuvyöhyke II, Alle 60 m2</Description>
        <ProfitCenter>111886</ProfitCenter>
        <Material>502557</Material>
        <UnitPrice>60.00</UnitPrice>
        <Quantity>1.00</Quantity>
        <Plant>1111</Plant>
      </Item>
      <Item>
        <Description>Maksuvyöhyke III</Description>
        <ProfitCenter>111886</ProfitCenter>
        <Material>512345</Material>
        <UnitPrice>20.00</UnitPrice>
        <Quantity>2.00</Quantity>
        <Plant>1110</Plant>
      </Item>
    </Items>
  </Header>
</SalesOrder>")


(defn content
  "Gets the content value of an element that supposedly does not have child elements"
  [element]
  (-> element :content first))

(deftest validate-invoice-input
  (testing "validate invoice"
    (s/validate IdocSapInput invoice)))

(deftest generating-sap-idoc-xml-2
  (testing "->idoc-xml"

    (let [invoices   [invoice invoice]
          result-xml (xml-converter/->idoc-xml invoices)
          parsed-xml (sxml/parse result-xml)]

      (testing "given one invoice, creates a proper XML message"
        (is (= (sxml/parse idoc-xml)
               (sxml/parse (xml-converter/->idoc-xml [invoice])))
            "having identical data with the expected XML string"))

      (testing "given one invoice, created XML message passes XML validation"
        (is (nil? (validate (xml-converter/->idoc-xml [invoice]) :tampere-ya))))

      (testing "given an invoice with a text too long for <Description> in XML schema"
        (let [result-xml (xml-converter/->idoc-xml [(invoice-with {:invoice-rows [{:sap-materialid "502557"
                                                                         :quantity 1
                                                                         :unitprice 60.00
                                                                         :sap-plant "1111"
                                                                         :sap-profitcenter "111886"

                                                                         ;; Text that is over 40 chars long
                                                                         :text "_123456789_123456789_123456789_123456789-XXX"}
                                                                        ]})])
              parsed-xml (sxml/parse result-xml)]

          (testing  "trims the invoice row text to the allowed length in XML schema"
            (let [Item (-> (select1 parsed-xml [:SalesOrder :Header :Items])
                                 :content
                                 first)
                  invoice-row-text (-> (select1 Item [:Description]) content)
                  trimmed-text "_123456789_123456789_123456789_123456789"]
              (is (= trimmed-text invoice-row-text))))

          (testing "passes XML validation"
            (validate result-xml :tampere-ya))))

      (testing "given an invoice with a too long operation/permit-id combination for a <TextRow> element in XML schema which allows 70 chars"
        (let [result-xml (xml-converter/->idoc-xml [(invoice-with {;; 80 chars
                                                         :operation (apply str (take 80 (repeat "R")))

                                                         ;;17 chars
                                                         :permit-id "LP-753-2019-90002"})])
              parsed-xml (sxml/parse result-xml)]

          (testing  "trims the operation to allow operation and permit id to fit the the allowed length of 70 chars in XML schema"
            (let [Text (-> (select1 parsed-xml [:SalesOrder :Header :Text]))
                  operation-permitid-value (-> Text :content second content)
                  trimmed-version "RRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRR LP-753-2019-90002"] ;;70 chars total
              (is (= trimmed-version operation-permitid-value))))

          (testing "passes XML validation"
            (validate result-xml :tampere-ya))))

      (testing "with laskuviite"
        (let [result-xml (xml-converter/->idoc-xml [(invoice-with {:your-reference "LASKUVIITE123"})])
              parsed-xml (sxml/parse result-xml)]

          (testing "reference is regarded as BillNumber"
            (let [reference (sxml/get-text parsed-xml [:SalesOrder :Header :BillNumber])]
              (is (= "LASKUVIITE123" reference))))

          (testing "passes XML validation"
            (validate result-xml :tampere-ya))))

      (testing "given an invoice with a too long target address for a <TextRow> element in XML schema which allows 70 chars"
        (let [result-xml (xml-converter/->idoc-xml [(invoice-with {:target {:street "123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789"}})])
              parsed-xml (sxml/parse result-xml)]

          (testing  "trims the operation to allow operation and permit id to fit the the allowed length of 70 chars in XML schema"
            (let [Text (-> (select1 parsed-xml [:SalesOrder :Header :Text]))
                  address-value (-> Text :content first content)
                  trimmed-version "123456789_123456789_123456789_123456789_123456789_123456789_123456789_"] ;;70 chars total
              (is (= trimmed-version address-value))))

          (testing "passes XML validation"
            (validate result-xml :tampere-ya))))

      (testing "given 2 invoices"

        (testing "creates an IDOC xml string that matches the invoices"

          (is (.startsWith result-xml (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
              "having an XML document declaration"))

        (testing "creates a proper IDOC xml from an invoice edn representation"

          (is (= :SalesOrder (:tag parsed-xml))
              "with root element :SalesOrder")

          (is (= 2 (count (select parsed-xml [:SalesOrder :Header])))
              "with 2 invoices (= :Header elements)")

          (testing "the first invoice with"

            (testing "<Customer>"
              (is (= "sapcustid9"
                     (-> (select1 parsed-xml [:SalesOrder :Header :SapID]) content))
                  "has <SapID>sapcustId9</SapID>"))

            (is (= "2019-03-05+02:00" (-> (select1 parsed-xml [:SalesOrder :Header :BillingDate]) content))
                "<BillingDate>2019-03-05+02:00</BillingDate>")

            (is (= "LP-753-2019-90002" (-> (select1 parsed-xml [:SalesOrder :Header :BillNumber]) content))
                "<BillNumber>LP-753-2019-90002</BillNumber>")

            (is (= "1111" (-> (select1 parsed-xml [:SalesOrder :Header :SalesOrganisation]) content))
                "<SalesOrganisation>1111</SalesOrganisation>")

            (is (= "00" (-> (select1 parsed-xml [:SalesOrder :Header :DistributionChannel]) content))
                "<DistributionChannel>00</DistributionChannel>")

            (is (= "00" (-> (select1 parsed-xml [:SalesOrder :Header :Division]) content))
                "<Division>00</Division>")

            (is (= "ZLUP" (-> (select1 parsed-xml [:SalesOrder :Header :SalesOrderType]) content))
                "<SalesOrderType>ZLUP</SalesOrderType>")

            (is (= "LP-753-2019-90002" (-> (select1 parsed-xml [:SalesOrder :Header :> :Description]) content))
                "<Description>LP-753-2019-90002</Description>")

            (is (= "334" (-> (select1 parsed-xml [:SalesOrder :Header :InterfaceID]) content))
                "<InterfaceID>334</InterfaceID>")


            (testing "<Text>"

              (let [Text (select1 parsed-xml [:SalesOrder :Header :Text])
                    first-TextRow  (-> Text :content first)
                    second-TextRow (-> Text :content second)]

                (is (= "Viinikankatu 9" (content first-TextRow) )
                    "has first TextRow as <TextRow>Viinikankatu 9</TextRow>")

                (is (= "Katulupa LP-753-2019-90002" (content second-TextRow) )
                    "has second TextRow as <TextRow>Katulupa LP-753-2019-90002</TextRow>")))

            (testing "<Items>"

              (let [Items (select1 parsed-xml [:SalesOrder :Header :Items])
                    first-Item  (-> Items :content first)
                    second-Item (-> Items :content second)]

                (is (= 2 (-> Items :content count))
                    "has 2 <Item> elements")

                (testing "first <Item>"

                  (is (= "Maksuvyöhyke II, Alle 60 m2" (-> (select1 first-Item [:Description]) content))
                      "contains invoice row text as <Description>Maksuvyöhyke II, Alle 60 m2</Description>")

                  (is (= "111886" (-> (select1 first-Item [:ProfitCenter]) content))
                      "contains SAP profit center as <ProfitCenter>111886</ProfitCenter>")

                  (is (= "502557" (-> (select1 first-Item [:Material]) content))
                      "contains SAP material as <Material>502557</Material>")

                  (is (= "60.00" (-> (select1 first-Item [:UnitPrice]) content))
                      "contains price per unit as <UnitPrice>60.00</UnitPrice>")

                  (is (= "1.00" (-> (select1 first-Item [:Quantity]) content))
                      "contains number of units as <Quantity>1.00</Quantity>")

                  (is (= "1111" (-> (select1 first-Item [:Plant]) content))
                      "contains SAP Plant <Plant>1111</Plant>"))


                (testing "second <Item>"

                  (is (= "Maksuvyöhyke III" (-> (select1 second-Item [:Description]) content))
                      "contains invoice row text as <Description>Maksuvyöhyke III</Description>")

                  (is (= "111886" (-> (select1 second-Item [:ProfitCenter]) content))
                      "contains SAP profit center as <ProfitCenter>111886</ProfitCenter>")

                  (is (= "512345" (-> (select1 second-Item [:Material]) content))
                      "contains SAP material as <Material>512345</Material>")

                  (is (= "20.00" (-> (select1 second-Item [:UnitPrice]) content))
                      "contains price per unit as <UnitPrice>20.00</UnitPrice>")

                  (is (= "2.00" (-> (select1 second-Item [:Quantity]) content))
                      "contains number of units as <Quantity>2.00</Quantity>")

                  (is (= "1110" (-> (select1 second-Item [:Plant]) content))
                      "contains SAP Plant <Plant>1110</Plant>"))))))))))
