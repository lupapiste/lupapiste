(ns lupapalvelu.invoices.general-api-xml-converter
  (:require [lupapalvelu.invoices.schemas :refer [GeneralApiXMLInput]]
            [lupapalvelu.invoices.xml-validator :refer [validate]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :refer [element-to-string ->Element]]
            [schema.core :as sc])
  (:import [java.util Locale]))

(defn ->OptionalElement
  "Passes args through to `->Element` if content is fullish. Otherwise nil."
  [tag content]
  (when (util/fullish? content)
    (->Element tag nil content)))

(defn ->2decimals
  "Formats given number into two decimal number using String/format with English
  locale. English locale has point as decimal separator, which is the required separator in SAP integration."
  [number]
  (String/format Locale/ROOT "%.2f" (into-array [(float number)])))

(def unit-mapping {"kpl" "PIECE"
                   "t"   "HOUR"
                   "pv"  "DAY"
                   "vk"  "WEEK"
                   "m2"  "M2"
                   "m3"  "M3"})

(defn ProductConstants
  "Generates the element <ProductConstants> with <ProductConstant> elements representing product constants"
  [product-constants]
  (when product-constants
    (->Element :ProductConstants nil
               ;; Constants ordering must much the XMLS schema sequence.
               (for [[tag k] (partition 2 [:CostCentre     :kustannuspaikka
                                           :VAT            :alv
                                           :CalculationTag :laskentatunniste
                                           :Project        :projekti
                                           :Target         :kohde
                                           :Function       :toiminto
                                           :OtherTag       :muu-tunniste])
                     :let    [v (k product-constants)]
                     :when   (ss/not-blank? v)]
                 (->Element tag nil v)))))

(defn Row
  "Generates an <Row> element representing an invoice row"
  [{:keys [code name unit quantity discount-percent unitprice product-constants]}]
  (->Element :Row nil
    [(->Element :Product nil [(->Element :Name            nil (str code " " name))
                              (->Element :Unit            nil (unit-mapping unit))
                              (->Element :Quantity        nil (->2decimals quantity))
                              (->Element :DiscountPercent nil (->2decimals discount-percent))
                              (->Element :UnitPrice       nil (->2decimals unitprice))
                              (ProductConstants product-constants)])]))

(defn Rows
    "Generates the element <Rows> with <Row> elements representing invoice rows"
    [invoice-rows]
    (let [item-elems (map Row invoice-rows)]
      (->Element :Rows nil item-elems)))

(defn OrganizationPayer
  "Generates an <Organization> element that contains info about the organization-type payer"
  [organization-payer]
  (->Element :Organization nil [(->Element :Id           nil (:id organization-payer))
                                (->OptionalElement :PartnerCode (:partner-code organization-payer))
                                (->Element :Name         nil (:name organization-payer))
                                (->Element :Contact      nil [(->Element :FirstName    nil (:contact-firstname organization-payer))
                                                              (->Element :LastName     nil (:contact-lastname organization-payer))
                                                              (->Element :Turvakielto  nil (:contact-turvakielto organization-payer))])
                                (->Element :Address      nil [(->Element :StreetAddress nil (:streetaddress organization-payer))
                                                              (->Element :PostalCode    nil (:postalcode organization-payer))
                                                              (->Element :City          nil (:city organization-payer))
                                                              (->Element :Country       nil (:country organization-payer))])
                                (->Element :EInvoiceAddress  nil (:einvoice-address organization-payer))
                                (->Element :EDI              nil (:edi organization-payer))
                                (->Element :Operator         nil (:operator organization-payer))]))

(defn PersonPayer
  "Generates an <Person> element that contains info about the person-type payer"
  [person-payer]
  (->Element :Person nil [(->Element :Id           nil (:id person-payer))
                          (->OptionalElement :PartnerCode (:partner-code person-payer))
                          (->Element :FirstName    nil (:firstname person-payer))
                          (->Element :LastName     nil (:lastname person-payer))
                          (->Element :Turvakielto  nil (:turvakielto person-payer))
                          (->Element :Address      nil [(->Element :StreetAddress  nil (:streetaddress person-payer))
                                                        (->Element :PostalCode  nil (:postalcode person-payer))
                                                        (->Element :City  nil (:city person-payer))
                                                        (->Element :Country  nil (:country person-payer))])]))

(defn Payer
  "Generates an <Payer> element that contains info about the payer of the invoice"
  [{:keys [payer customer]}]
  (->Element :Payer nil [(->Element :Type       nil (:payer-type payer))
                         (->Element :CustomerId nil (:client-number customer))
                         (if (= "ORGANIZATION" (:payer-type payer))
                           (OrganizationPayer (:organization payer))
                           (PersonPayer       (:person payer)))]))

(defn Payee
  "Generates an <Payee> element that contains info about the payer of the invoice"
  [invoice-row]
  (->Element :Payee nil
    [(->Element :Organization nil [(->Element :Id     nil (:payee-organization-id invoice-row))
                                   (->Element :Group  nil (:payee-group invoice-row))
                                   (->Element :Sector nil (:payee-sector invoice-row))])]))

(defn ApplicationContext
  "Generates the element <ApplicationContext> with 3 elements that define the Lupapiste-application associated with the invoice"
  [invoice]
  (->Element :ApplicationContext nil [(->Element :Id            nil (:permit-id invoice))
                                      (->Element :StreetAddress nil (get-in invoice [:target :street]))
                                      (->Element :PermitType    nil (:operation invoice))]))

(defn Invoice
  "Generates the element <Invoice> that contains all data for one invoice"
  [index invoice]
  (->Element :Invoice nil [(->Element :Type nil (:invoice-type invoice))
                           (ApplicationContext invoice)
                           (->Element :Reference nil (:reference invoice))
                           (->OptionalElement :BackendId (:backend-id invoice))
                           (Payee (:payee invoice))
                           (Payer (select-keys invoice [:payer :customer]))
                           (Rows (:invoice-rows invoice))]))

(defn Invoices
  "Generates the element <Invoices> with <Invoice> elements representing invoice rows"
  [invoices]
  (sc/validate [GeneralApiXMLInput] invoices)
  (->Element :Invoices nil
             (map-indexed (fn [i invoice]
                            (Invoice (inc i) invoice))
                          invoices)))

(defn From
  "Generates the element <From> that contains info about us, the source system sending invoicing data"
  [system-id]
  (->Element :From nil [(->Element :System nil [(->Element :Id nil system-id)])]))

(defn XmlRoot
  "Represents the root level of the xml to be generated"
  [timestamp system-id invoices]
  (->Element :InvoiceTransfer
             {:created (date/xml-datetime timestamp)}
             [(From system-id)
              (Invoices invoices)]))

(defn ->xml
  "Generates an xml from the received invoice data and system id"
  [timestamp system-id invoices]
  (sc/validate [GeneralApiXMLInput] invoices)
  (let [xml-string (->> (XmlRoot timestamp system-id invoices)
                        element-to-string
                        ;;TODO Java/Clojure  produces UTF-16 but at least SAX parser does not allow encoding as UTF-16.
                        ;;     Come up with a clever solution
                        (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))]
    (validate xml-string :general-api)
    xml-string))
