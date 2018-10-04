(ns lupapalvelu.invoices
  "A common interface for accessing invoices, price catalogues and related data"
  (:require [lupapalvelu.mongo :as mongo]
            [schema.core :as sc]
            [sade.core :refer [ok fail now]]
            [sade.schemas :as ssc]
            [taoensso.timbre :refer [trace tracef debug debugf info infof
                                     warn warnf error errorf fatal fatalf]]
            [lupapalvelu.user :as user]))

(sc/defschema User
  {:id                                        user/Id
   :firstName                                 (ssc/max-length-string 255)
   :lastName                                  (ssc/max-length-string 255)
   :role                                      user/Role
   :email                                     ssc/Email
   :username                                  ssc/Username})

(sc/defschema DiscountPercent
  (sc/constrained sc/Num #(and (>= % 0) (<= % 100))))

(sc/defschema InvoiceRow
  {:text sc/Str
   :unit (sc/enum "m2" "m3" "kpl")
   :price-per-unit sc/Num
   :units sc/Num
   :discount-percent DiscountPercent})

;;TODO should operation-id and name come from constants in lupapalvelu.operations
;;     and/or a schema somewhere else?
(sc/defschema InvoiceOperation
  {:operation-id sc/Str
   :name sc/Str
   :invoice-rows [InvoiceRow]})



(sc/defschema Invoice
  {:id sc/Str
   :created ssc/Timestamp
   :created-by User
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
  (debug ">> validate-insert-invoice request data: " invoice-data)
  (when (sc/check InvoiceInsertRequest invoice-data)
    (fail :error.invalid-invoice)))

(defn validate-invoice [invoice]
  (debug ">> validate-invoice: " invoice)
  (sc/validate Invoice invoice))

(defn create-invoice!
  [invoice]
  (debug ">> create-invoice! invoice-request: " invoice)
  (let [id (mongo/create-id)
        invoice-with-id (assoc invoice :id id)]
    (debug ">> invoice-with-id: " invoice-with-id)
    (validate-invoice invoice-with-id)
    (mongo/insert :invoices invoice-with-id)
    id))

(defn update-invoice!
  [{:keys [id] :as invoice}]
  (let [current-invoice (mongo/by-id "invoices" id)
        new-invoice (merge current-invoice (select-keys invoice [:operations :state]))]
    (validate-invoice new-invoice)
    (mongo/update-by-id "invoices" id new-invoice)))

(sc/defn ^:always-validate ->invoice-user :- User
 [user]
 (select-keys user [:id :firstName :lastName :role :email :username]))

(defn ->invoice-db
  [invoice {:keys [id organization] :as application} user]
  (debug "->invoice-db invoice-request: " invoice " app id: " id " user: " user)
  (merge invoice
         {:created (now)
          :created-by (->invoice-user user)
          :application-id id
          :organization-id organization}))

(defn fetch-by-application-id [application-id]
  (mongo/select "invoices" {:application-id application-id}))
