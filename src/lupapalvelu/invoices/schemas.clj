(ns lupapalvelu.invoices.schemas
  (:require
   [schema.core :as sc]
   [sade.schemas :as ssc]
   [sade.core :as sade]
   [lupapalvelu.money-schema :refer [MoneyResponse]]
   [taoensso.timbre :refer [trace tracef debug debugf info infof
                                     warn warnf error errorf fatal fatalf]]
   [lupapalvelu.user :as usr]))

(def InvoiceId sc/Str)

(def NameLength (ssc/max-length-string 255))

(sc/defschema User
  {:id                                        usr/Id
   :firstName                                 NameLength
   :lastName                                  NameLength
   :role                                      usr/Role
   :username                                  ssc/Username})

(sc/defschema DiscountPercent
  (sc/constrained sc/Num #(and (>= % 0) (<= % 100))))

(sc/defschema InvoiceRowType
  (sc/enum "from-price-catalogue" "custom"))

(sc/defschema InvoiceRowUnit
  (sc/enum "m2" "m3" "kpl"))

(sc/defschema InvoiceRow
  {:text sc/Str
   :type InvoiceRowType
   :unit InvoiceRowUnit
   :price-per-unit sc/Num
   :units sc/Num
   :discount-percent DiscountPercent
   (sc/optional-key :sums) {:without-discount MoneyResponse
                            :with-discount MoneyResponse}})

;;TODO should operation-id and name come from constants in lupapalvelu.operations
;;     and/or a schema somewhere else?
(sc/defschema InvoiceOperation
  {:operation-id sc/Str
   :name sc/Str
   :invoice-rows [InvoiceRow]})

(sc/defschema Invoice
  {:id InvoiceId
   :created ssc/Timestamp
   :created-by User
   :state (sc/enum "draft"       ;;created
                   "checked"     ;;checked by decision maker and moved to biller
                   "confirmed"   ;;biller has confirmed the invoice
                   "transferred" ;;biller has exported the invoice
                   "billed"      ;;biller has marked the invoice as billed
                   )
   :application-id sc/Str
   :organization-id sc/Str
   :operations [InvoiceOperation]
   (sc/optional-key :sum) MoneyResponse
   (sc/optional-key :permit-number) sc/Str
   (sc/optional-key :entity-name) sc/Str
   (sc/optional-key :sap-number) sc/Str
   (sc/optional-key :entity-address) sc/Str
   (sc/optional-key :billing-reference) sc/Str
   (sc/optional-key :identification-number) sc/Str
   (sc/optional-key :internal-info) sc/Str})

(sc/defschema InvoiceInsertRequest
  {:operations [InvoiceOperation]})

(sc/defschema CatalogueRow
  {:code sc/Str
   :text sc/Str
   :unit InvoiceRowUnit
   :price-per-unit sc/Num
   :max-total-price (sc/maybe sc/Num)
   :min-total-price (sc/maybe sc/Num)
   :discount-percent (sc/maybe DiscountPercent)
   :operations [sc/Str]})

(sc/defschema PriceCatalogue
  {:id sc/Str
   :organization-id sc/Str
   :state (sc/enum "draft"       ;;created as a draft
                   "published"   ;;published and in use if on validity period
                   )
   :valid-from ssc/Timestamp
   :valid-until (sc/maybe ssc/Timestamp)
   :rows [CatalogueRow]
   :meta {:created ssc/Timestamp
          :created-by User}})

(sc/defschema PriceCatalogueInsertRequest
  {:valid-from-str sc/Str ;; dd.mm.yyyy checker would be nice here
   :rows [CatalogueRow]})

(def TransferBatchId sc/Str)

(sc/defschema InvoiceInTransferBatch
  {:id InvoiceId
   :added-to-transfer-batch ssc/Timestamp
   :organization-id sc/Str})

(sc/defschema InvoiceTransferBatch
  {:id TransferBatchId
   :organization-id sc/Str
   :created ssc/Timestamp
   :created-by User
   :invoices [InvoiceInTransferBatch]
   :number-of-rows sc/Num
   :sum MoneyResponse})


(sc/defschema TransferBatchResponseTransferBatch
  {:id sc/Str
   :invoices [Invoice]
   :invoice-count sc/Num
   :invoice-row-count sc/Num
   :transfer-batch InvoiceTransferBatch})

(sc/defschema TransferBatchOrgsResponse
  {sc/Str [TransferBatchResponseTransferBatch]})

(sc/defn ^:always-validate ->invoice-user :- User
 [user]
  (select-keys user [:id :firstName :lastName :role :username]))

(defn ->invoice-db
  [invoice {:keys [id organization] :as application} user]
  (debug "->invoice-db invoice-request: " invoice " app id: " id " user: " user)
  (merge invoice
         {:created (sade/now)
          :created-by (->invoice-user user)
          :application-id id
          :organization-id organization}))
