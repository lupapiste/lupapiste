(ns lupapalvelu.invoices
  "A common interface for accessing invoices, price catalogues and related data"
  (:require [lupapalvelu.mongo :as mongo]
            [schema.core :as sc]
            [sade.core :refer [ok fail]]
            [taoensso.timbre :refer [trace tracef debug debugf info infof
                                     warn warnf error errorf fatal fatalf]]))

(sc/defschema InvoiceRow
  {:text sc/Str
   :unit (sc/enum "m2" "m3" "kpl")
   :price-per-unit sc/Num
   :units sc/Num})

(sc/defschema InvoiceOperation
  {:operation-id sc/Str
   :name sc/Str
   :invoice-rows [InvoiceRow]})

(sc/defschema Invoice
  {:id sc/Str
   :state (sc/enum "draft"       ;;created
                   "checked"     ;;checked by decision maker and moved to biller
                   "confirmed"   ;;biller has confirmed the invoice
                   "transferred" ;;biller has exported the invoice
                   "billed"      ;;biler has marked the invoice as billed
                   )
   :application-id sc/Str
   :organization-id sc/Str
   :operations [InvoiceOperation]})

(sc/defschema InvoiceInsertRequest
  {:operations [InvoiceOperation]})

(defn validate-insert-invoice-request [{{invoice-data :invoice} :data :as command}]
  (debug ">> validate-invoice data: " invoice-data)
  (when (sc/check InvoiceInsertRequest invoice-data)
    (fail :error.invalid-invoice)))

(defn validate-invoice [invoice]
  (debug ">>> validate-invoice: " invoice)
  (sc/validate Invoice invoice))

(defn create-invoice!
  [invoice]
  (debug ">> create-invoice! invoice-request: " invoice)
  (let [id (mongo/create-id)
        invoice-with-id (assoc invoice :id id)]
    (validate-invoice invoice-with-id)
    (mongo/insert :invoices invoice-with-id)
    id))

(defn ->invoice-db
  [invoice {:keys [id organization] :as application}]
  (debug "->invoice-db invoice-request: " invoice " app id: " id)
  (merge invoice
         {:application-id id
          :organization-id organization}))

(defn fetch-by-application-id [application-id]
  (mongo/select "invoices" {:application-id application-id}))
