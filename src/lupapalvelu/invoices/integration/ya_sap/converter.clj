(ns lupapalvelu.invoices.integration.ya-sap.converter
  (:require [lupapalvelu.invoices.integration.xml-validator :as xml-validator :refer [validate]]
            [lupapalvelu.invoices.schemas :as schemas]
            [lupapalvelu.invoices.util :refer [xml-decimal-string]]
            [sade.date :as date]
            [sade.strings :as ss :refer [limit trim-to]]
            [sade.xml :refer [->Element element-to-string]]))

(defmethod xml-validator/provide-schemas :tampere-ya
  [_]
  ["invoices/tampere-ya/salesOrder_v11.2.xsd"])

(defn- Item
  "Generates an <Item> element representing an invoice row"
  [invoice-row]
  ;; FIXME LPK-5349 better fit all information to Tampere YA SAP
  (->Element :Item nil [(->Element :Description  nil (trim-to 40 (:text invoice-row)))
                        (->Element :ProfitCenter nil (:sap-profitcenter invoice-row))
                        (->Element :Material     nil (:sap-materialid invoice-row))
                        (->Element :UnitPrice    nil (xml-decimal-string (:unitprice invoice-row)))
                        (->Element :Quantity     nil (xml-decimal-string (:quantity invoice-row)))
                        (->Element :Plant        nil (:sap-plant invoice-row))]))

(defn- Items
  "Generates the element <Items> with <Item> elements representing invoice rows"
  [invoice]
  (let [item-elems (map Item (:invoice-rows invoice))]
    (->Element :Items nil item-elems)))

(defn- address-TextRow
  "Generates the first <TextRow> under <Text> which contains the target address"
  [invoice]
  (let [element-max-length 70
        trimmed-address    (trim-to element-max-length (get-in invoice [:target :street]))]
    (->Element :TextRow nil trimmed-address)))

(defn- operation-permitid-TextRow
  "Generates the second <TextRow> under <Text> which contains the operation name and the permit id. Total length max 70 chars"
  [{:keys [operation permit-id]}]
  (let [element-max-length   70
        divider-str          " "
        operation-max-length (- element-max-length (count permit-id) (count divider-str))
        trimmed-operation    (trim-to operation-max-length operation)]
    (->Element :TextRow nil (str trimmed-operation divider-str permit-id))))

(defn- Text
  "Generates the element <Text> with 2 <TextRow> elements in it"
  [invoice]
  (->Element :Text nil [(address-TextRow invoice)
                        (operation-permitid-TextRow invoice)]))

(defn- SapID
  "Generates the element <SapID> with sap-number in it"
  [invoice]
  (->Element :SapID nil (get-in invoice [:customer :sap-number])))

(defn- Customer
  "Generates the element <Customer>"
  [invoice]
  (->Element :Customer nil [(SapID invoice)]))

(defn- Header
  "Generates the element <Header> that contains all data for one invoice"
  [_begin invoice]
  (->Element :Header nil
             (filter
               (comp seq :content) ; remove elements with empty contents
               [(Customer invoice)
                (->Element :BillingDate nil (date/xml-date (:sap-bill-date invoice)))
                (->Element :BillNumber nil (-> (or (:your-reference invoice)
                                                   (:permit-id invoice))
                                               (limit 20)))
                (->Element :SalesOrganisation nil (:sap-salesorganization invoice))
                (->Element :DistributionChannel nil (:sap-distributionchannel invoice))
                (->Element :Division nil (:sap-division invoice))
                (->Element :SalesOrderType nil (:sap-ordertype invoice))
                (->Element :Description nil (:description invoice))
                (->Element :InterfaceID nil (:sap-integration-id invoice))
                (Text invoice)
                (Items invoice)])))

(defn- Header-elems [invoices]
  (map-indexed (fn [i invoice]
                 (Header (inc i) invoice))
               invoices))

(defn ->idoc-xml ^String [invoices]
  (schemas/valid-idoc-sap-inputs! invoices)
  (let [xml-string (->> (->Element :SalesOrder nil (Header-elems invoices))
                        element-to-string
                        ;;TODO Java/Clojure  produces UTF-16 but at least SAX parser does not allow encoding as UTF-16.
                        ;;     Come up with a clever solution
                        (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))]
    (validate xml-string :tampere-ya)
    xml-string))
