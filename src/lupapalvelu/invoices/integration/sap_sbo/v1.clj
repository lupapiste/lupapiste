(ns lupapalvelu.invoices.integration.sap-sbo.v1
  "V1 refers to the older `ECC` version of the API."
  (:require [lupapalvelu.invoices.integration.generate-impl :as impl]
            [lupapalvelu.invoices.integration.sap-sbo.common :as sap-common]
            [lupapalvelu.xml.util :as xml-util]
            [schema.core :as sc])
  (:import [jakarta.xml.bind JAXBContext]
           [lupapalvelu.sap_sbo.v1 ObjectFactory
                                   SBOSalesOrderContainerType
                                   SBOSalesOrderType
                                   SBOSalesOrderType$BillingParty1
                                   SBOSalesOrderType$BillingParty2
                                   SBOSalesOrderType$LineItem
                                   SBOSalesOrderType$OrderParty
                                   SBOSalesOrderType$PayerParty
                                   SBOSalesOrderType$ShipToParty]))

(set! *warn-on-reflection* true)

(def ^ObjectFactory factory (ObjectFactory.))

(def jaxb-context (JAXBContext/newInstance ^Class/1 (into-array [SBOSalesOrderContainerType])))

(def root-schema-path "V1 schema path'" "invoices/sap_sbo/SBO_SalesOrderContainer.xsd")

(def root-schema "V1 schema instance'" (xml-util/load-schema root-schema-path))

;;; Empty XML entities

;; The schema specifies that all the fields are mandatory, but doesn't
;; actually require that they contain anything.

(def empty-order-party (-> (SBOSalesOrderType$OrderParty/builder)
                           (.withCustomerID "")
                           (.withCustomerOVT "")
                           (.withCustomerYID "")
                           (.withPriorityAddress1 "")
                           (.withPriorityAddress2 "")
                           (.withPriorityCity "")
                           (.withPriorityName1 "")
                           (.withPriorityName2 "")
                           (.withPriorityName3 "")
                           (.withPriorityName4 "")
                           (.withPriorityPOCode "")
                           (.withPriorityPostalcode "")
                           (.withSAPCustomerID "")
                           (.withTemporaryAddress1 "")
                           (.withTemporaryAddress2 "")
                           (.withTemporaryCity "")
                           (.withTemporaryPOCode "")
                           (.withTemporaryPostalcode "")
                           (.build)))

(def empty-billing-party-1 (-> (SBOSalesOrderType$BillingParty1/builder)
                               (.withCustomerID "")
                               (.withCustomerOVT "")
                               (.withCustomerYID "")
                               (.withPriorityAddress1 "")
                               (.withPriorityAddress2 "")
                               (.withPriorityCity "")
                               (.withPriorityName1 "")
                               (.withPriorityName2 "")
                               (.withPriorityName3 "")
                               (.withPriorityName4 "")
                               (.withPriorityPOCode "")
                               (.withPriorityPostalcode "")
                               (.withSAPCustomerID "")
                               (.withTemporaryAddress1 "")
                               (.withTemporaryAddress2 "")
                               (.withTemporaryCity "")
                               (.withTemporaryPOCode "")
                               (.withTemporaryPostalcode "")
                               .build))

(def empty-billing-party-2 (-> (SBOSalesOrderType$BillingParty2/builder)
                               (.withCustomerID "")
                               (.withCustomerOVT "")
                               (.withCustomerYID "")
                               (.withPriorityAddress1 "")
                               (.withPriorityAddress2 "")
                               (.withPriorityCity "")
                               (.withPriorityName1 "")
                               (.withPriorityName2 "")
                               (.withPriorityName3 "")
                               (.withPriorityName4 "")
                               (.withPriorityPOCode "")
                               (.withPriorityPostalcode "")
                               (.withSAPCustomerID "")
                               (.withTemporaryAddress1 "")
                               (.withTemporaryAddress2 "")
                               (.withTemporaryCity "")
                               (.withTemporaryPOCode "")
                               (.withTemporaryPostalcode "")
                               .build))

(def empty-payer-party (-> (SBOSalesOrderType$PayerParty/builder)
                           (.withCustomerID "")
                           (.withCustomerOVT "")
                           (.withCustomerYID "")
                           (.withPriorityAddress1 "")
                           (.withPriorityAddress2 "")
                           (.withPriorityCity "")
                           (.withPriorityName1 "")
                           (.withPriorityName2 "")
                           (.withPriorityName3 "")
                           (.withPriorityName4 "")
                           (.withPriorityPOCode "")
                           (.withPriorityPostalcode "")
                           (.withSAPCustomerID "")
                           (.withTemporaryAddress1 "")
                           (.withTemporaryAddress2 "")
                           (.withTemporaryCity "")
                           (.withTemporaryPOCode "")
                           (.withTemporaryPostalcode "")
                           .build))

(def empty-ship-to-party (-> (SBOSalesOrderType$ShipToParty/builder)
                             (.withCustomerID "")
                             (.withCustomerOVT "")
                             (.withCustomerYID "")
                             (.withPriorityAddress1 "")
                             (.withPriorityAddress2 "")
                             (.withPriorityCity "")
                             (.withPriorityName1 "")
                             (.withPriorityName2 "")
                             (.withPriorityName3 "")
                             (.withPriorityName4 "")
                             (.withPriorityPOCode "")
                             (.withPriorityPostalcode "")
                             (.withSAPCustomerID "")
                             (.withTemporaryAddress1 "")
                             (.withTemporaryAddress2 "")
                             (.withTemporaryCity "")
                             (.withTemporaryPOCode "")
                             (.withTemporaryPostalcode "")
                             .build))

(def empty-line-item (-> (SBOSalesOrderType$LineItem/builder)
                         (.withGroupingFactor "")
                         (.withLineText "")
                         (.withMaterial "")
                         (.withMaterialDescription "")
                         (.withMaterialSalesText "")
                         (.withNetPrice "")
                         (.withOrderItemNumber "")
                         (.withProfitCenter "")
                         (.withQuantity "")
                         (.withTaxCode "") ;; max 2
                         (.withUnit "") ;; max 3
                         (.withWBSElement "")
                         .build))

(def empty-items [empty-line-item])

(def ^SBOSalesOrderType empty-sales-order
  (-> (SBOSalesOrderType/builder)
      (.withBillText "") ; Ulkoisen tekstilajin teksti rivi, optional, 70 chars
      (.withBillingBlock "") ; Laskulukitus, optional, 4 chars
      (.withBillingDate "") ; Laskun päivämäärä, optional, YYYYmmdd
      (.withBillingParty1 empty-billing-party-1)
      (.withBillingParty2 empty-billing-party-2)
      (.withDistributionChannel "") ; Jakelutie, required, 2 chars
      (.withDivision "") ; Sektori, required, 2 chars
      (.withHiddenText "") ; Sisäinen tekstilajin teksti rivi, optional, 70 chars
      (.withLineItem ^Iterable empty-items)
      (.withOrderParty empty-order-party)
      (.withOrderReason "") ; Tilaussyy, optional, 3 chars
      (.withOrderType "") ; Tilauslaji, required, max 4
      (.withOriginalOrder "") ; Hyvitettävän laskun numero, optional, 10 chars
      (.withPMNTTERM "") ; Maksuehto, optional, 4 chars
      (.withPONumber "") ; Ostotilausnumero, optional, 35 chars
      (.withPayerParty empty-payer-party)
      (.withPricingDate "") ; Hinnoittelu päivämäärä, optional, YYYYmmdd
      (.withReference "") ; Laskunumero, optional, 10 chars
      (.withReferenceText "") ; Viitteemme teksti, optional, 20 chars
      (.withSalesDistrict "") ; Myyntipiiri, optional, 6 chars
      (.withSalesGroup "") ; Myyntiryhmä, optional, 3 chars
      (.withSalesOffice "") ; Myyntitoimisto, required, 4 chars
      (.withSalesOrg "") ; Myyntiorganisaatio, virastotunnus, required, 4 chars
      (.withSenderId "") ; Lähettäjätunnus, required, max 5 chars
      (.withShipToParty empty-ship-to-party)
      .build))

;;; SAP XML builders

(sc/defn make-order-party
  [{:keys [city customer-id customer-ovt customer-yid
           postal-code priority-addresses priority-names
           sap-customer-id]} :- sap-common/OrderParty]
  (-> (SBOSalesOrderType$OrderParty/copyOf empty-order-party)
      (.withCustomerID (or customer-id ""))
      (.withCustomerOVT (or customer-ovt ""))
      (.withCustomerYID (or customer-yid ""))
      (.withPriorityAddress1 (get priority-addresses 0 ""))
      (.withPriorityAddress2 (get priority-addresses 1 ""))
      (.withPriorityCity (or city ""))
      (.withPriorityName1 (get priority-names 0 ""))
      (.withPriorityName2 (get priority-names 1 ""))
      (.withPriorityName3 (get priority-names 2 ""))
      (.withPriorityName4 (get priority-names 3 ""))
      (.withPriorityPostalcode (or postal-code ""))
      (.withSAPCustomerID (or sap-customer-id ""))
      (.build)))

(sc/defn make-line-item
  [{:keys [material material-description price quantity unit wbs-element]} :- sap-common/LineItem]
  (-> (SBOSalesOrderType$LineItem/copyOf empty-line-item)
      (.withMaterial material)
      (.withMaterialDescription (or material-description ""))
      (.withNetPrice price)
      (.withQuantity quantity)
      (.withUnit (or unit ""))
      (.withWBSElement wbs-element)
      .build))

(sc/defn make-sales-order
  [{:keys [bill-text distribution-channel division
           line-items order-party order-type reference po-number
           sales-office sales-org sender-id]} :- sap-common/SalesOrder]
  (-> (SBOSalesOrderType/copyOf empty-sales-order)
      (.withBillText (or bill-text ""))
      (.withBillingParty1 empty-billing-party-1)
      (.withBillingParty2 empty-billing-party-2)
      (.withDistributionChannel distribution-channel)
      (.withDivision division)
      (.withLineItem ^Iterable (mapv make-line-item line-items))
      (.withOrderParty (make-order-party order-party))
      (.withOrderType order-type)
      (.withPONumber (or po-number ""))
      (.withPayerParty empty-payer-party)
      (.withReference reference)
      (.withSalesOffice sales-office)
      (.withSalesOrg sales-org)
      (.withSenderId sender-id)
      (.withShipToParty empty-ship-to-party)
      .build))

(sc/defn make-sales-order-container
  [orders :- sap-common/SalesOrderContainer]
  (.createSBOSalesOrderContainer
    factory
    (-> (SBOSalesOrderContainerType/builder)
        (.addSBOSalesOrder ^Iterable (mapv make-sales-order orders))
        .build)))

(defn doc->xml
  ([document]
   (doc->xml document {}))
  ([document options]
   (xml-util/->xml (merge
                     {:document      document
                      :jaxb-context  jaxb-context
                      :pretty-print? false
                      :schema        root-schema}
                     options))))

(defn ->xml
  ([config invoices lang]
   (->xml config invoices lang true))
  ([config invoices lang pretty-print?]
   (let [result (sap-common/make-sales-orders-data config invoices lang)]
     (if (:error result)
       result
       (-> result
           :sales-orders
           make-sales-order-container
           (doc->xml {:pretty-print? pretty-print?}))))))

(defmethod impl/generate-invoice-transfer-batch "sap-sbo-v1"
  [{:keys [cfg created lang invoices pretty-print?] :or {lang :fi}}]
  (let [result (->xml cfg invoices lang pretty-print?)]
    (if (string? result)
      (sap-common/build-response cfg created result)
      result)))
