(ns lupapalvelu.invoices
  "A common interface for accessing invoices, price catalogues and related data"
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$in]]
            [schema.core :as sc]
            [sade.core :refer [ok fail] :as sade]
            [sade.schemas :as ssc]
            [taoensso.timbre :refer [trace tracef debug debugf info infof
                                     warn warnf error errorf fatal fatalf]]
            [lupapalvelu.money-schema :refer [MoneyResponse]]
            [lupapalvelu.money :refer [sum-with-discounts ->currency ->MoneyResponse discounted-value multiply-amount]]
            [lupapalvelu.user :as user]
            [lupapalvelu.domain :refer [get-application-no-access-checking]]
            [lupapalvelu.application-schema :refer [Operation]]))

(sc/defschema User
  {:id                                        user/Id
   :firstName                                 (ssc/max-length-string 255)
   :lastName                                  (ssc/max-length-string 255)
   :role                                      user/Role
   :email                                     ssc/Email
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
   (sc/optional-key :sum) MoneyResponse})

(sc/defschema InvoiceInsertRequest
  {:operations [InvoiceOperation]})

(sc/defschema CatalogueRow
  {:code sc/Str
   :text sc/Str
   :unit InvoiceRowUnit
   :price-per-unit sc/Num
   (sc/optional-key :max-total-price) sc/Num
   (sc/optional-key :min-total-price) sc/Num
   :discount-percent DiscountPercent
   (sc/optional-key :operations) [sc/Str]})

(sc/defschema PriceCatalogue
  {:id sc/Str
   :organization-id sc/Str
   :state (sc/enum "draft"       ;;created as a draft
                   "published"   ;;published and in use if on validity period
                   )
   :valid-from ssc/Timestamp
   (sc/optional-key :valid-until) ssc/Timestamp
   :rows [CatalogueRow]
   :meta {:created ssc/Timestamp
          :created-by User}})

(defn fetch-invoice [invoice-id]
  (mongo/by-id :invoices invoice-id))

(defn validate-insert-invoice-request [{{invoice-data :invoice} :data :as command}]
  (debug ">> validate-insert-invoice request data: " invoice-data)
  (when (sc/check InvoiceInsertRequest invoice-data)
    (fail :error.invalid-invoice)))

(defn validate-invoice [invoice]
  (debug ">> validate-invoice: " invoice)
  (sc/validate Invoice invoice))


(defn sum-single-row [row]
  (let [sum-by-units (multiply-amount (:units row) (:price-per-unit row))
        discount-percent (:discount-percent row)]
    {:without-discount (->MoneyResponse sum-by-units)
     :with-discount (-> sum-by-units
                        (discounted-value discount-percent)
                        ->MoneyResponse)}))

(defn- merge-invoice-rows [rows]
  (map (fn [row]
         {:row-total
          (multiply-amount (:units row) (:price-per-unit row))
          :discount-percent
          (:discount-percent row)}) rows))

(defn sum-invoice [invoice]
  (let [rows (flatten (map :invoice-rows (:operations invoice)))
        merged-invoice-rows (merge-invoice-rows rows)]
    (assoc invoice :sum (sum-with-discounts :row-total :discount-percent merged-invoice-rows))))

(defn- enrich-sums-invoice-row [row]
  (assoc row :sums (sum-single-row row)))

(defn- enrich-rows-in-operations [invoice]
  (let [operations-from-invoice (:operations invoice)
        enriched-operations (map (fn [operation]
                                   (let [invoice-rows (:invoice-rows operation)]
                                     (assoc operation :invoice-rows
                                            (vec (map (fn [row]
                                                        (enrich-sums-invoice-row row))
                                                      invoice-rows))))) operations-from-invoice)
        p (info "eriched operations " enriched-operations)]
    (assoc invoice :operations (vec enriched-operations))))


(defn enrich-invoice-sums-before-save [invoice]
  (-> invoice
      (enrich-rows-in-operations)
      (sum-invoice)))



(defn create-invoice!
  [invoice]
  (debug ">> create-invoice! invoice-request: " invoice)
  (let [id (mongo/create-id)
        invoice-with-id (assoc invoice :id id)]
    (debug ">> invoice-with-id: " invoice-with-id)
    (->> invoice-with-id
         enrich-invoice-sums-before-save
         validate-invoice
         (mongo/insert :invoices))
    id))

(defn update-invoice!
  [{:keys [id] :as invoice}]
  (let [current-invoice (mongo/by-id "invoices" id)
        new-invoice (merge current-invoice (select-keys invoice [:operations :state]))]
    (->> new-invoice
         enrich-invoice-sums-before-save
         validate-invoice
         (mongo/update-by-id "invoices" id))))

(sc/defn ^:always-validate ->invoice-user :- User
 [user]
  (select-keys user [:id :firstName :lastName :role :email :username]))
(defn ->invoice-db
  [invoice {:keys [id organization] :as application} user]
  (debug "->invoice-db invoice-request: " invoice " app id: " id " user: " user)
  (merge invoice
         {:created (sade/now)
          :created-by (->invoice-user user)
          :application-id id
          :organization-id organization}))

(defn fetch-by-application-id [application-id]
  (mongo/select "invoices" {:application-id application-id}))

(sc/defn ^:always-validate  get-operations-from-application :- [Operation]
  "Returns a vector (primaryOperation being first) of operations from application, by combining primary and secondary operations to one seq"
  [application]
  (let [primary-operation (:primaryOperation application)
        secondary-operations (:secondaryOperations application)]
    (concat [primary-operation] secondary-operations)))

(defn fetch-application-operations [application-id]
  (let [application (get-application-no-access-checking application-id)]
    (get-operations-from-application application)))

(defn fetch-invoices-for-organizations [organization-ids]
  (mongo/select :invoices {:organization-id {$in organization-ids}}))

(defn get-user-orgs-having-role [user role]
  (->> (:orgAuthz user)
       (filter (fn [[org-id roles]]
                 (roles (keyword role))))
       (map (comp name first))))

(defn get-doc [doc-id docs]
  (some (fn [{:keys [id] :as doc}]
          (if (= id doc-id)
            doc))
        docs))

(defn enrich-org-data [user-orgs {:keys [organization-id] :as invoice}]
  (let [organization (get-doc organization-id user-orgs)
        localized-names (:name organization)]
    (assoc-in invoice [:enriched-data :organization :name] localized-names)))

(defn fetch-application-data [application-ids projection]
  (mongo/select :applications {:_id {$in application-ids}} projection))

(defn enrich-application-data [applications {:keys [application-id] :as invoice}]
  (let [application (get-doc application-id applications)
        address (:address application)]
    (assoc-in invoice [:enriched-data :application :address] address)))

(defn fetch-price-catalogues [organization-id]
  (mongo/select :price-catalogues {:organization-id organization-id}))

(defn validate-price-catalogues [price-catalogues]
  (info "validate-price-catalogues price-catalogues: " price-catalogues)
  (if-not (empty? price-catalogues)
    (sc/validate [PriceCatalogue] price-catalogues)))
