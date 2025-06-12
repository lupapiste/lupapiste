(ns lupapalvelu.invoices.schemas
  (:require [lupapalvelu.application-schema :as app-schema]
            [lupapalvelu.invoices.shared.schemas :refer [CatalogueRow InvoiceRowUnit Percentage ProductConstants]]
            [lupapalvelu.user :as usr]
            [sade.schemas :as ssc]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(def InvoiceId ssc/NonBlankStr)
(def OperationId (st/get-in app-schema/Operation [:id]))
(def OperationName (st/get-in app-schema/Operation [:name]))

(def TransferBatchId ssc/NonBlankStr)

(sc/defschema UserOrSheriff (st/optional-keys usr/SummaryUser [:id :role :username]))

(sc/defschema InvoiceRow
  {:id                                  ssc/NonBlankStr
   :operation-id                        OperationId
   (sc/optional-key :code)              sc/Str
   (sc/optional-key :text)              sc/Str
   (sc/optional-key :custom?)           sc/Bool
   :unit                                InvoiceRowUnit
   (sc/optional-key :price-per-unit)    sc/Num
   (sc/optional-key :units)             sc/Num
   (sc/optional-key :discount-percent)  Percentage
   (sc/optional-key :product-constants) ProductConstants
   (sc/optional-key :comment)           sc/Str
   (sc/optional-key :min-unit-price)    sc/Num
   (sc/optional-key :max-unit-price)    sc/Num
   (sc/optional-key :total-cents)       sc/Int})

(sc/defschema EditableRowFields
  "Number fields are strings because a) empty fields are supported and b) we want to do
  the number parsing in the backend in order to avoid browser variance."
  {(sc/optional-key :text)              sc/Str
   (sc/optional-key :unit)              InvoiceRowUnit
   (sc/optional-key :price-per-unit)    sc/Str
   (sc/optional-key :units)             sc/Str
   (sc/optional-key :discount-percent)  sc/Str
   (sc/optional-key :comment)           sc/Str
   (sc/optional-key :product-constants) ProductConstants})

(sc/defschema InvoiceOperation
  {:id   OperationId
   :name OperationName})

(sc/defschema InvoiceState
  (sc/enum "draft"       ;; created
           "checked"     ;; checked by decision maker and moved to biller
           "confirmed"   ;; biller has confirmed the invoice
           "transferred" ;; biller has closed the transfer batch for this invoice
           ))

(sc/defschema InvoicePayerFields
  "Editable payer information"
  {(sc/optional-key :entity-name)        sc/Str  ;; Company only
   (sc/optional-key :first-name)         sc/Str  ;; Person or company contact
   (sc/optional-key :last-name)          sc/Str  ;; Person or company contact
   (sc/optional-key :protected?)         sc/Bool ;; Person or company contact
   (sc/optional-key :entity-address)     sc/Str  ;; Street
   (sc/optional-key :postal-code)        sc/Str
   (sc/optional-key :city)               sc/Str
   (sc/optional-key :country)            sc/Str
   (sc/optional-key :phone)              sc/Str  ;; Person or company contact
   (sc/optional-key :email)              sc/Str  ;; Person or company contact
   (sc/optional-key :billing-reference)  sc/Str
   (sc/optional-key :payer-type)         (sc/enum "company" "person")
   (sc/optional-key :company-id)         sc/Str ;; Company only
   (sc/optional-key :person-id)          sc/Str ;; Person only
   (sc/optional-key :netbill)            sc/Str ;; Company only
   (sc/optional-key :ovt)                sc/Str ;; Company only
   (sc/optional-key :operator)           sc/Str ;; Company only
   (sc/optional-key :application-payer?) sc/Bool})

(sc/defschema BillerEditableInvoiceFields
  "See the `set-invoice-field` command."
  (st/merge InvoicePayerFields
            {:state                                            InvoiceState
             :description                                      sc/Str
             (sc/optional-key :sap-number)                     sc/Str
             (sc/optional-key :organization-internal-invoice?) sc/Bool
             (sc/optional-key :partner-code)                   sc/Str
             (sc/optional-key :case-reference)                 sc/Str
             (sc/optional-key :letter)                         sc/Str
             (sc/optional-key :internal-info)                  sc/Str
             (sc/optional-key :transfer-batch-id)              TransferBatchId}))

(sc/defschema Invoice
  (st/merge ssc/SheriffNotes
            BillerEditableInvoiceFields
            {:id                                   InvoiceId
             (sc/optional-key :price-catalogue-id) sc/Str
             :created                              ssc/Timestamp
             :created-by                           usr/SummaryUser
             (sc/optional-key :modified)           ssc/Timestamp
             (sc/optional-key :sent)               ssc/Timestamp
             (sc/optional-key :modified-by)        UserOrSheriff
             :application-id                       ssc/ApplicationId
             :organization-id                      ssc/NonBlankStr
             :operations                           [InvoiceOperation]
             :rows                                 [InvoiceRow]
             (sc/optional-key :work-start-ms)      ssc/Timestamp
             (sc/optional-key :work-end-ms)        ssc/Timestamp
             (sc/optional-key :backend-code)       ssc/NonBlankStr
             (sc/optional-key :backend-id)         ssc/NonBlankStr}))

(def valid-invoices! (sc/validator [Invoice]))

(def internal-invoice-fields
  "Fields that should not be passed to the frontend"
  [:_sheriff-notes :modified-by])

(sc/defschema AuthorityEditableInvoiceFields
  "Top-level fields that can be edited by a 'regular' authority for the draft/checked
  invoices."
  {(sc/optional-key :state)                          (sc/enum "draft" "checked")
   (sc/optional-key :price-catalogue-id)             (sc/maybe sc/Str)
   (sc/optional-key :backend-code)                   (sc/maybe sc/Str)
   (sc/optional-key :organization-internal-invoice?) sc/Bool})

(sc/defschema CatalogueType
  (sc/enum "R" "YA"))

(sc/defschema NoBillingPeriod
  {:start sc/Str
   :end   sc/Str})

(sc/defschema Modified
  {:modified    ssc/Timestamp
   :modified-by UserOrSheriff})

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

(def valid-price-catalogue! (sc/validator PriceCatalogue))

(sc/defschema DraftCatalogueRow
  "Draft row is more tolerant for missing data, since a row is updated in a piece-meal fashion."
  {:id                                  ssc/ObjectIdStr
   (sc/optional-key :code)              sc/Str
   (sc/optional-key :text)              sc/Str
   (sc/optional-key :unit)              (sc/maybe InvoiceRowUnit)
   (sc/optional-key :price-per-unit)    (sc/maybe sc/Num)
   (sc/optional-key :max-total-price)   (sc/maybe sc/Num)
   (sc/optional-key :min-total-price)   (sc/maybe sc/Num)
   (sc/optional-key :discount-percent)  (sc/maybe Percentage)
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

(def valid-catalogue-draft! (sc/validator PriceCatalogueDraft))

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

(sc/defschema TransferBatchHistoryEntry
  {:user                         UserOrSheriff
   :time                         ssc/Timestamp
   (sc/optional-key :message-id) ssc/NonBlankStr
   :action                       ssc/NonBlankStr})

(sc/defschema InvoiceTransferBatch
  (st/merge ssc/SheriffNotes
            {:id                       TransferBatchId
             :state                    (sc/enum "open" "closed")
             :organization-id          sc/Str
             :created                  ssc/Timestamp
             :history                  [TransferBatchHistoryEntry]
             (sc/optional-key :closed) ssc/Timestamp
             (sc/optional-key :sent)   ssc/Timestamp
             (sc/optional-key :name)   sc/Str}))

(sc/defschema IdocSapInputTarget
  {:street sc/Str})

(sc/defschema IdocSapInputInvoiceRow
  {:sap-materialid   sc/Str
   :quantity         sc/Num
   :unitprice        sc/Num
   :sap-plant        sc/Str
   :sap-profitcenter sc/Str
   :text             sc/Str})

(sc/defschema IdocSapInputCustomer
  {(sc/optional-key :sap-number) sc/Str})

(sc/defschema IdocSapInput
  {:sap-integration-id               sc/Str
   :sap-ordertype                    sc/Str
   :sap-salesorganization            sc/Str
   :sap-distributionchannel          sc/Str
   :sap-division                     sc/Str
   :permit-id                        sc/Str
   :operation                        sc/Str
   :sap-bill-date                    ssc/Timestamp
   :sap-term-id                      sc/Str
   :description                      sc/Str
   :target                           IdocSapInputTarget
   :invoice-rows                     [(sc/one IdocSapInputInvoiceRow 'at-least-one) IdocSapInputInvoiceRow]
   :customer                         IdocSapInputCustomer
   (sc/optional-key :your-reference) sc/Str})

(def valid-idoc-sap-input! (sc/validator IdocSapInput))
(def valid-idoc-sap-inputs! (sc/validator [IdocSapInput]))

;;
;; General api
;;

(sc/defschema GeneralApiInputTarget
  {:street      sc/Str
   :postal-code sc/Str
   :city        sc/Str})

(sc/defschema GeneralApiInputCustomer
  {:client-number sc/Str})

(sc/defschema GeneralApiInputInvoiceRow
  {:code                                sc/Str
   :name                                sc/Str
   :unit                                InvoiceRowUnit
   :quantity                            sc/Num
   :discount-percent                    Percentage
   :unitprice                           sc/Num
   (sc/optional-key :product-constants) (st/optional-keys ProductConstants)})

(sc/defschema GeneralApiPayee
  {:payee-organization-id sc/Str
   :payee-group           sc/Str
   :payee-sector          sc/Str})

(sc/defschema GeneralApiPayer
  (sc/conditional
    :organization {:payer-type   (sc/eq "ORGANIZATION")
                   :organization {:id                  sc/Str
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
                                  :operator            sc/Str}}
    :person {:payer-type (sc/eq "PERSON")
             :person     {:id            sc/Str
                          :partner-code  sc/Str
                          :firstname     sc/Str
                          :lastname      sc/Str
                          :turvakielto   sc/Str
                          :streetaddress sc/Str
                          :postalcode    sc/Str
                          :city          sc/Str
                          :country       sc/Str}}))

(sc/defschema GeneralApiXMLInput
  {:invoice-type                 (sc/enum "INTERNAL" "EXTERNAL")
   :reference                    sc/Str
   (sc/optional-key :case)       sc/Str
   (sc/optional-key :letter)     sc/Str
   (sc/optional-key :backend-id) ssc/NonBlankStr
   :permit-id                    sc/Str
   :property-id                  sc/Str
   :operation                    sc/Str
   :target                       GeneralApiInputTarget
   :customer                     GeneralApiInputCustomer
   :payee                        GeneralApiPayee
   :payer                        GeneralApiPayer
   :invoice-rows                 [(sc/one GeneralApiInputInvoiceRow 'at-least-one) GeneralApiInputInvoiceRow]})

(def valid-general-api-input! (sc/validator GeneralApiXMLInput))
(def valid-general-api-inputs! (sc/validator [GeneralApiXMLInput]))
