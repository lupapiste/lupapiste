(ns lupapalvelu.invoices.schemas
  "Schemas used all around invoices feature.
  Mainly notable thing here is that we use schemas for API responses,
  meaning that we kinda rarely return the actual db-representation.

  This is a good thing."
  (:require [lupapalvelu.application-schema :as app-schema]
            [lupapalvelu.invoices.shared.schemas :refer [DiscountPercent InvoiceRowUnit
                                                         ProductConstants CatalogueRow]]
            [lupapalvelu.money-schema :refer [MoneyResponse]]
            [lupapalvelu.user :as usr]
            [sade.core :as sade]
            [sade.schemas :as ssc]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(def InvoiceId sc/Str)

(def TransferBatchId sc/Str)

(def NameLength (ssc/max-length-string 255))

(sc/defschema User
  {:id        usr/Id
   :firstName NameLength
   :lastName  NameLength
   :role      usr/Role
   :username  ssc/Username})

(sc/defschema InvoiceRowType
  (sc/enum "from-price-catalogue" "custom"))

(sc/defschema InvoiceRow
  {(sc/optional-key :code)              sc/Str
   :text                                sc/Str
   :type                                InvoiceRowType
   :unit                                InvoiceRowUnit
   :price-per-unit                      sc/Num
   :units                               sc/Num
   :discount-percent                    DiscountPercent
   (sc/optional-key :product-constants) ProductConstants
   (sc/optional-key :order-number)      ssc/Nat
   (sc/optional-key :comment)           sc/Str
   (sc/optional-key :min-unit-price)    sc/Num
   (sc/optional-key :max-unit-price)    sc/Num
   (sc/optional-key :sums)              {:without-discount MoneyResponse
                                         :with-discount    MoneyResponse}})

(sc/defschema InvoiceOperation
  {:operation-id (get app-schema/Operation :id)
   :name         (get app-schema/Operation :name)
   :invoice-rows [InvoiceRow]})

(sc/defschema InvoiceState
  (sc/enum "draft"       ;;created
           "checked"     ;;checked by decision maker and moved to biller
           "confirmed"   ;;biller has confirmed the invoice
           "transferred" ;;biller has exported the invoice
           "billed"      ;;biller has marked the invoice as billed
           ))

(sc/defschema InvoiceHistoryEntry
  {:user User
   :time ssc/Timestamp
   :state InvoiceState
   :action sc/Str})

(sc/defschema Invoice
  {:id                                               InvoiceId
   (sc/optional-key :price-catalogue-id)             sc/Str
   :created                                          ssc/Timestamp
   :created-by                                       User
   (sc/optional-key :history)                        [InvoiceHistoryEntry]
   (sc/optional-key :modified)                       ssc/Timestamp
   :state                                            InvoiceState
   :application-id                                   ssc/ApplicationId
   :organization-id                                  ssc/NonBlankStr
   :operations                                       [InvoiceOperation]
   :description                                      sc/Str
   (sc/optional-key :sum)                            MoneyResponse
   (sc/optional-key :entity-name)                    sc/Str
   (sc/optional-key :sap-number)                     sc/Str
   (sc/optional-key :entity-address)                 sc/Str
   (sc/optional-key :billing-reference)              sc/Str
   (sc/optional-key :organization-internal-invoice?) sc/Bool
   (sc/optional-key :partner-code)                   sc/Str
   (sc/optional-key :payer-type)                     (sc/enum "company" "person")
   (sc/optional-key :company-id)                     sc/Str
   (sc/optional-key :person-id)                      sc/Str
   (sc/optional-key :ovt)                            sc/Str
   (sc/optional-key :operator)                       sc/Str
   (sc/optional-key :case-reference)                 sc/Str
   (sc/optional-key :letter)                         sc/Str
   (sc/optional-key :company-contact-person)         sc/Str
   (sc/optional-key :application-payer?)             sc/Bool
   (sc/optional-key :backend-code)                   ssc/NonBlankStr
   (sc/optional-key :backend-id)                     ssc/NonBlankStr

   (sc/optional-key :internal-info)                  sc/Str
   (sc/optional-key :work-start-ms)                  ssc/Timestamp
   (sc/optional-key :work-end-ms)                    ssc/Timestamp
   (sc/optional-key :transferbatch-id)               TransferBatchId})

(sc/defschema UserOrganizationsInvoicesRequest
  {(sc/optional-key :states) [InvoiceState]
   (sc/optional-key :from) ssc/Timestamp
   (sc/optional-key :until) ssc/Timestamp
   (sc/optional-key :limit) sc/Int})

(sc/defschema CatalogueType
  (sc/enum "R" "YA"))

(sc/defschema NoBillingPeriod
  {:start sc/Str
   :end   sc/Str})

(sc/defschema Modified
  {:modified    ssc/Timestamp
   :modified-by User})

(sc/defschema PriceCatalogue
  {:id                                   ssc/ObjectIdStr
   :organization-id                      ssc/NonBlankStr
   :name                                 ssc/NonBlankStr
   :type                                 CatalogueType
   :state                                (sc/eq "published")
   (sc/optional-key :valid-from)         ssc/Timestamp
   (sc/optional-key :valid-until)        ssc/Timestamp
   :rows                                 [CatalogueRow]
   (sc/optional-key :no-billing-periods) {sc/Keyword NoBillingPeriod}
   :meta                                 Modified})

(sc/defschema DraftCatalogueRow
  "Draft row is more tolerant for missing data, since a row is updated in a piece-meal fashion."
  {:id                                  ssc/ObjectIdStr
   (sc/optional-key :code)              sc/Str
   (sc/optional-key :text)              sc/Str
   (sc/optional-key :unit)              (sc/maybe InvoiceRowUnit)
   (sc/optional-key :price-per-unit)    (sc/maybe sc/Num)
   (sc/optional-key :max-total-price)   (sc/maybe sc/Num)
   (sc/optional-key :min-total-price)   (sc/maybe sc/Num)
   (sc/optional-key :discount-percent)  (sc/maybe DiscountPercent)
   (sc/optional-key :operations)        [ssc/NonBlankStr]
   (sc/optional-key :product-constants) {(sc/optional-key :kustannuspaikka)  sc/Str
                                         (sc/optional-key :alv)              sc/Str
                                         (sc/optional-key :laskentatunniste) sc/Str
                                         (sc/optional-key :projekti)         sc/Str
                                         (sc/optional-key :kohde)            sc/Str
                                         (sc/optional-key :muu-tunniste)     sc/Str
                                         (sc/optional-key :toiminto)         sc/Str}})

(sc/defschema PriceCatalogueDraft
  (st/merge PriceCatalogue
            {:state (sc/eq "draft")
             :rows  [DraftCatalogueRow]}))

(sc/defschema MoveRow
  {:organizationId     ssc/NonBlankStr
   :price-catalogue-id ssc/ObjectIdStr
   :row-id             ssc/ObjectIdStr
   :direction          (sc/enum "up" "down")})

(sc/defschema PriceCatalogueDraftEditParams
  {:organizationId     ssc/NonBlankStr
   :price-catalogue-id ssc/ObjectIdStr
   :edit               (sc/conditional
                         :name {:name ssc/NonBlankStr}
                         :row {:row (st/assoc DraftCatalogueRow
                                              (sc/optional-key :id) ssc/ObjectIdStr)}
                         :delete-row {:delete-row ssc/ObjectIdStr}
                         :valid {:valid {(sc/optional-key :from)  ssc/Timestamp
                                         (sc/optional-key :until) ssc/Timestamp}})})

(sc/defschema PriceCatalogueInsertRequest
  {:valid-from-str sc/Str ;; dd.mm.yyyy checker would be nice here
   :rows [CatalogueRow]})

(sc/defschema InvoiceInTransferBatch
  {:id InvoiceId
   :added-to-transfer-batch ssc/Timestamp
   :organization-id sc/Str})

(sc/defschema TransferBatchState
  (sc/enum "open" "closed"))

(sc/defschema TransferBatchHistoryEntry
  {:user User
   :time ssc/Timestamp
   :state TransferBatchState
   :action sc/Str})

(sc/defschema InvoiceTransferBatch
  {:id TransferBatchId
   :state TransferBatchState
   :organization-id sc/Str
   (sc/optional-key :closed) ssc/Timestamp
   :created ssc/Timestamp
   (sc/optional-key :modified) ssc/Timestamp
   (sc/optional-key :history) [TransferBatchHistoryEntry]
   :created-by User
   :invoices [InvoiceInTransferBatch]
   :number-of-rows sc/Num
   :sum MoneyResponse})

(sc/defschema OrganizationsTransferBatchesRequest
  {(sc/optional-key :states) [TransferBatchState]
   (sc/optional-key :from) ssc/Timestamp
   (sc/optional-key :until) ssc/Timestamp
   (sc/optional-key :limit) sc/Int})

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
  [invoice {{:keys [id organization]} :application ts :created} user]
  (merge {:description id
          :created (or ts (sade/now))}
         invoice
         {:created-by (->invoice-user user)
          :application-id id
          :organization-id organization}))

(sc/defschema IdocSapInputTarget
  {:street sc/Str})

(sc/defschema IdocSapInputInvoiceRow
  {:sap-materialid sc/Str
   :quantity sc/Num
   :unitprice sc/Num
   :sap-plant sc/Str
   :sap-profitcenter sc/Str
   :text sc/Str})

(sc/defschema IdocSapInputCustomer
 {(sc/optional-key :sap-number) sc/Str})

(sc/defschema IdocSapInput
  {:sap-integration-id sc/Str
   :sap-ordertype sc/Str
   :sap-salesorganization sc/Str
   :sap-distributionchannel sc/Str
   :sap-division sc/Str
   :permit-id sc/Str
   :operation sc/Str
   :sap-bill-date ssc/Timestamp
   :sap-term-id sc/Str
   :description sc/Str
   :target IdocSapInputTarget
   :invoice-rows [IdocSapInputInvoiceRow]
   :customer IdocSapInputCustomer
   (sc/optional-key :your-reference) sc/Str})

;;
;; General api
;;

(sc/defschema GeneralApiInputCustomer
  {:client-number sc/Str})

(sc/defschema GeneralApiInputInvoiceRow
  {:code      sc/Str
   :name      sc/Str
   :unit      InvoiceRowUnit
   :quantity  sc/Num
   :discount-percent DiscountPercent
   :unitprice sc/Num
   (sc/optional-key :product-constants) (st/optional-keys ProductConstants)})

(sc/defschema GeneralApiPayee
  {:payee-organization-id sc/Str
   :payee-group           sc/Str
   :payee-sector          sc/Str})

(sc/defschema GeneralApiPayer
  (sc/constrained
    {:payer-type                     (sc/enum "ORGANIZATION" "PERSON")
     (sc/optional-key :organization) {:id                  sc/Str
                                      :partner-code        sc/Str
                                      :name                sc/Str
                                      :contact-firstname   sc/Str
                                      :contact-lastname    sc/Str
                                      :contact-turvakielto sc/Str
                                      :streetaddress       sc/Str
                                      :postalcode          sc/Str
                                      :city                sc/Str
                                      :country             sc/Str
                                      :einvoice-address    sc/Str
                                      :edi                 sc/Str
                                      :operator            sc/Str}
     (sc/optional-key :person)       {:id            sc/Str
                                      :partner-code  sc/Str
                                      :firstname     sc/Str
                                      :lastname      sc/Str
                                      :turvakielto   sc/Str
                                      :streetaddress sc/Str
                                      :postalcode    sc/Str
                                      :city          sc/Str
                                      :country       sc/Str}}
    #(and (or (:organization %) (:person %))
          (not ((every-pred :organization :person) %)))
    'payer-info-must-declare-either-organization-or-person))

(sc/defschema GeneralApiXMLInput
  {:invoice-type                 (sc/enum "INTERNAL" "EXTERNAL")
   :reference                    sc/Str
   (sc/optional-key :backend-id) ssc/NonBlankStr
   :permit-id                    sc/Str
   :operation                    sc/Str
   :target                       IdocSapInputTarget
   :customer                     GeneralApiInputCustomer
   :payee                        GeneralApiPayee
   :payer                        GeneralApiPayer
   :invoice-rows                 [GeneralApiInputInvoiceRow]})
