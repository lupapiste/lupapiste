(ns lupapalvelu.invoices.integration.sap-sbo.common
  "Map Lupapiste invoices to SAP datastructures

  - Define strict schemas for SAP data structures, based on the XML schemas
  - Create stricter alternatives of the Lupapiste invoice schemas by taking
    each field in the SAP data structures and applying the restrictions on the
    source fields in the Lupapiste schema.
  - Validate the input invoices using these stricter invoice schemas
  - If invalid, report the original invoice + row + field information to the caller
  - Otherwise transform the invoices into SAP data structures
  - The caller can then convert the data structures to one of the SAP XML formats."
  (:require [lupapalvelu.invoices.integration.sap-sbo.error-util :as sap-error]
            [lupapalvelu.invoices.transfer-batch :as tb]
            [lupapalvelu.invoices.util :as inv-util]
            [sade.date :as date]
            [sade.schema-errors :as se]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc :refer [defschema]]))

(defn- limit-str
  [s limit]
  (some-> s
          ss/trim
          (ss/limit limit)))

;;; Output SAP schemas to XML builders (v1 & v2)

;; Based on the restrictions set by the SAP XML schema(s)

;; Certain fields allow long strings
(def long-line-length 70)
(def material-description-length 40)
(def medium-line-length 35)

(defschema OrderParty
  (st/optional-keys
    {:city               ssc/NonBlankStr
     :customer-id        (se/fixed-str 11) ; Henkilöasiakkaan hetu
     :customer-ovt       (se/min-max-str 12 17) ; Yritysasiakkaan OVT-tunnus
     :customer-yid       (se/fixed-str 9) ; Yritysasiakkaan Y-tunnus
     :postal-code        ssc/NonBlankStr
     :priority-addresses [(se/max-str medium-line-length)]
     :priority-names     [(se/max-str medium-line-length)]
     :sap-customer-id    (se/fixed-str 10) ; Asiakkaan SAP-asiakasnumero
     }))

(defschema LineItem
  (st/merge
    {:material    (se/fixed-str 8)
     :quantity    (se/pattern-str #"\d{1,10}[.,]\d{3}")
     :price       (se/pattern-str #"[-]?\d{1,11}[.,]\d{2}") ; unit price, not total price
     :wbs-element (se/min-max-str 1 16)}
    (st/optional-keys
      {:material-description (se/max-str material-description-length)
       :unit                 (se/max-str 3)})))

(defschema SalesOrder
  (st/merge
    {:distribution-channel (se/fixed-str 2)
     :division             (se/fixed-str 2)
     :line-items           [LineItem]
     :order-party          OrderParty
     :order-type           (se/min-max-str 1 4)
     :sales-office         (se/min-max-str 1 4)
     :sales-org            (se/min-max-str 1 4)
     :sender-id            (se/pattern-str #"ID\d{3}")
     :reference            (se/min-max-str 1 10)}
    (st/optional-keys
      {:bill-text (se/max-str long-line-length)
       ;; ostotilausnumero, not post-office etc.
       :po-number (se/min-max-str 1 medium-line-length)})))

(defschema SalesOrderContainer
  "Collection of `SalesOrder`s."
  (se/not-empty-seq SalesOrder))

(def container-checker (sc/checker SalesOrderContainer))

;;; Input schemas

;; We apply additional restrictions from the output SAP schemas on the generic Lupapiste invoicing schemas
;; (`lupapalvelu.invoices.schemas/*`). We can then use these stricter schemas to validate the original
;; Lupapiste invoices before doing any transformations on them. Any validation issues detected will thus point
;; to the original invoice data instead of some already manipulated SAP data structure.

(def constants-mapping
  "Mapping between (some) SAP `SalesOrder` fields and `lupapalvelu.organization/InvoiceConstants`"
  {:distribution-channel :jakelutie
   :division             :sektori
   :order-type           :tilauslaji
   :sales-office         :myyntitoimisto
   :sales-org            :myyntiorg
   :sender-id            :laskuttaja})

(defschema SapConstants
  "Apply the SAP restrictions on `InvoiceConstants` fields"
  (st/open-schema
    (->> constants-mapping
         (map (fn [[sap-key config-key]]
                [config-key (st/get-in SalesOrder [sap-key])]))
         (into {}))))

(defn config->sap-constants
  [{:keys [constants] :as _config}]
  (->> constants-mapping
       (map (fn [[sap-key constants-key]]
              [sap-key (get constants constants-key)]))
       (into {})))

(defschema SapConfig
  (st/open-schema {:constants SapConstants}))

(defschema SapProductConstants
  (st/open-schema
    {:projekti (st/get-in LineItem [:wbs-element])}))

(defschema SapRow
  (st/open-schema
    (st/merge
      {:code              (st/get-in LineItem [:material])
       :product-constants SapProductConstants
       :price-per-unit    (sc/constrained sc/Num (fn [v] (<= (abs v) 99999999999)) "valid-price-per-unit")
       :unit              (st/get-in LineItem [:unit])
       :units             (sc/constrained sc/Num (fn [v] (<= 0 v 999999999)) "valid-units")}
      (st/optional-keys
        {;; Limited to `long-line-text` chars, but we truncate if necessary
         :text sc/Str}))))

(defschema SapCommonInvoice
  "Apply the SAP restrictions on `lupapalvelu.invoices.schemas/Invoice` fields."
  (st/open-schema
    (st/merge
      {:city        (st/get-in OrderParty [:city])
       :postal-code (st/get-in OrderParty [:postal-code])
       :rows        (se/not-empty-seq SapRow)
       :payer-type  (sc/enum "company" "person")
       :backend-id  (st/get-in SalesOrder [:reference])}
      (st/optional-keys
        {:description       sc/Str ; Limited to `material-description-length` chars, but we truncate if necessary
         :sap-number        (st/get-in OrderParty [:sap-customer-id])
         :billing-reference (st/get-in SalesOrder [:po-number])}))))

(defschema SapCompanyInvoice
  (-> SapCommonInvoice
      (st/merge (st/optional-keys {:company-id (st/get-in OrderParty [:customer-yid])
                                   :ovt        (st/get-in OrderParty [:customer-ovt])}))
      (se/has-one-of #{:sap-number :company-id})))

(defschema SapPersonInvoice
  (-> SapCommonInvoice
      (st/merge (st/optional-keys {:person-id (st/get-in OrderParty [:customer-id])}))
      (se/has-one-of #{:sap-number :person-id})))

(defschema SapInvoice
  "Apply the SAP restrictions on `lupapalvelu.invoices.schemas/Invoice` fields."
  (sc/conditional
    (comp #{"person"} :payer-type) SapPersonInvoice
    :else SapCompanyInvoice))

(defschema SapInput
  {:config   SapConfig
   :invoices (se/not-empty-seq SapInvoice)})

(def input-checker (sc/checker SapInput))

(defmulti invoice->order-party
  "Produce a order party map from invoice"
  :payer-type)

(defn- common-order-party
  [{:keys [city entity-address postal-code sap-number]}]
  (cond-> {:city               city
           :postal-code        postal-code
           :sap-customer-id    sap-number}
    entity-address (assoc :priority-addresses [(limit-str entity-address medium-line-length)])))

(defmethod invoice->order-party "person"
  [{:keys [first-name last-name person-id] :as invoice}]
  (cond-> (common-order-party invoice)
    person-id                 (assoc :customer-id person-id)
    (or last-name first-name) (assoc :priority-names  [(-> (ss/join-non-blanks ", " [last-name first-name])
                                                           (limit-str medium-line-length))])))

(defmethod invoice->order-party "company"
  [{:keys [company-id entity-name first-name last-name ovt] :as invoice}]
  (cond-> (common-order-party invoice)
    company-id      (assoc :customer-yid company-id)
    ovt             (assoc :customer-ovt ovt)
    (or entity-name
        last-name
        first-name) (assoc :priority-names (->> [(limit-str entity-name medium-line-length)
                                                 (-> (ss/join-non-blanks ", " [last-name first-name])
                                                     (limit-str medium-line-length))]
                                                (filter some?)
                                                (into [])))))

(defn- net-price-per-unit [{:keys [price-per-unit discount-percent]}]
  (-> (inv-util/num->cents price-per-unit)
      (inv-util/calculate-discount (or discount-percent 0))
      :amount-cents
      inv-util/cents->num))

(sc/defn invoices->sales-orders :- SalesOrderContainer
  [{:keys [config invoices]} :- SapInput]
  (let [common-constants (config->sap-constants config)]
    (->> invoices
         (mapv (fn [{:keys [backend-id billing-reference description rows]
                     :as   invoice}]
                 (-> common-constants
                     (merge {:bill-text   (limit-str description long-line-length)
                             :po-number   billing-reference
                             :order-party (invoice->order-party invoice)
                             :reference   backend-id
                             :line-items  (mapv
                                            (fn [{:keys [code product-constants text unit units]
                                                  :as   row}]
                                              (cond-> {:material    code
                                                       :price       (ss/format-fixed "%.2f" (net-price-per-unit row))
                                                       :quantity    (ss/format-fixed "%.3f" (double units))
                                                       :unit        (limit-str unit 3)
                                                       :wbs-element (:projekti product-constants)}
                                                text (assoc :material-description (limit-str text material-description-length))))
                                            rows)})
                     ss/trimwalk
                     util/strip-blanks))))))

(defn make-sales-orders-data
  "Convert Lupapiste invoices into generic SAP sales orders, compatible with both V1 & V2."
  [config invoices lang]
  (let [;; Strip any "" values
        input  {:config   (-> config
                              ss/trimwalk
                              util/strip-blanks)
                :invoices (-> invoices
                              ss/trimwalk
                              util/strip-blanks)}
        errors (some->> input
                        input-checker
                        se/error-or-errors->data
                        (sap-error/with-localized-errors lang))]
    (if (seq errors)
      {:error errors}
      (let [sales-orders (invoices->sales-orders input)
            ;; TODO: can this checking ever fail?
            errors       (some->> sales-orders
                                  container-checker
                                  se/error-or-errors->data
                                  (sap-error/with-localized-errors lang))]
        (if (seq errors)
          {:error errors}
          {:sales-orders sales-orders})))))

(def ^:private datetime-format (date/pattern-formatter "YYMMddHHmmss"))

(defn- filename-for-xml
  [cfg timestamp]
  (str (tb/filename-prefix cfg "Lupapiste_")
       (date/zoned-format timestamp datetime-format)
       ".xml"))

(defn build-response
  [cfg created content]
  {:content      content
   :filename     (filename-for-xml cfg created)
   :content-type "text/xml;charset=UTF-8"})
