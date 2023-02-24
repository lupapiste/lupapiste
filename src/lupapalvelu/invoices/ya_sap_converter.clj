(ns lupapalvelu.invoices.ya-sap-converter
  (:require [lupapalvelu.invoices.schemas :refer [IdocSapInput]]
            [lupapalvelu.invoices.xml-validator :refer [validate]]
            [sade.date :as date]
            [sade.strings :refer [trim-to limit]]
            [sade.xml :refer [element-to-string ->Element]]
            [schema.core :as sc])
  (:import [java.util Locale]))


; In Java 11 locales have been changed, en_FI now it returns comma which is not valid in XML schema
; (.getDecimalSeparator (DecimalFormatSymbols/getInstance))
; => \,

(defn ->2decimals
  "Formats given number into two decimal number using String/format with English
  locale. English locale has point as decimal separator, which is the required separator in SAP integration."
  [number]
  (String/format Locale/ROOT "%.2f" (into-array [(float number)])))

(defn- Item
  "Generates an <Item> element representing an invoice row"
  [invoice-row]
  ; FIXME LPK-5349 better fit all information to Tampere YA SAP
  (->Element :Item nil [(->Element :Description  nil (trim-to 40 (:text invoice-row)))
                        (->Element :ProfitCenter nil (:sap-profitcenter invoice-row))
                        (->Element :Material     nil (:sap-materialid invoice-row))
                        (->Element :UnitPrice    nil (->2decimals (:unitprice invoice-row)))
                        (->Element :Quantity     nil (->2decimals (:quantity invoice-row)))
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
        trimmed-address (trim-to element-max-length (get-in invoice [:target :street]))]
    (->Element :TextRow nil trimmed-address)))

(defn- operation-permitid-TextRow
  "Generates the second <TextRow> under <Text> which contains the operation name and the permit id. Total length max 70 chars"
  [{:keys [operation permit-id] :as invoice}]
  (let [element-max-length 70
        divider-str " "
        operation-max-length (- element-max-length (count permit-id) (count divider-str))
        trimmed-operation (trim-to operation-max-length operation)]
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
  [begin invoice]
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

(defn ->idoc-xml [invoices]
  (sc/validate [IdocSapInput] invoices)
  (let [xml-string (->> (->Element :SalesOrder nil (Header-elems invoices))
                        element-to-string
                        ;;TODO Java/Clojure  produces UTF-16 but at least SAX parser does not allow encoding as UTF-16.
                        ;;     Come up with a clever solution
                        (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))]
    (validate xml-string :tampere-ya)
    xml-string))
