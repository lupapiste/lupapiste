(ns lupapalvelu.price-catalogues
  "A common interface for accessing price catalogues and related data"
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$in]]
            [schema.core :as sc]
            [sade.core :refer [ok fail] :as sade]
            [sade.schemas :as ssc]
            [taoensso.timbre :refer [trace tracef debug debugf info infof
                                     warn warnf error errorf fatal fatalf]]
            [lupapalvelu.invoices :as invoices]))


(sc/defschema CatalogueRow
  {:code sc/Str
   :text sc/Str
   :unit invoices/InvoiceRowUnit
   :price-per-unit sc/Num
   :max-total-price (sc/maybe sc/Num)
   :min-total-price (sc/maybe sc/Num)
   :discount-percent (sc/maybe invoices/DiscountPercent)
   :operations [sc/Str]})

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
          :created-by invoices/User}})

(sc/defschema PriceCatalogueInsertRequest
  {:valid-from ssc/Timestamp
   :rows [CatalogueRow]})

(defn fetch-price-catalogues [organization-id]
  (mongo/select :price-catalogues {:organization-id organization-id}))

(defn validate-price-catalogues [price-catalogues]
  (info "validate-price-catalogues price-catalogues: " price-catalogues)
  (if-not (empty? price-catalogues)
    (sc/validate [PriceCatalogue] price-catalogues)))

(defn validate-insert-price-catalogue-request [{{catalogue-data :price-catalogue} :data :as command}]
  (try
    (sc/validate PriceCatalogueInsertRequest catalogue-data)
    nil
    (catch Exception e
      (warn "Invalid price catalogue request " (.getMessage e))
      (fail :error.invalid-price-catalogue))))

(defn ->price-catalogue-db
  [price-catalogue user organization-id]
  (debug "->price-catalogue-db price-catalogue-request: " price-catalogue " organization-id: " organization-id " user: " user)
  (merge price-catalogue
         {:meta {:created (sade/now)
                 :created-by (invoices/->User user)}
          :organization-id organization-id}))

(defn with-id [price-catalogue]
  (assoc price-catalogue :id (mongo/create-id)))

(defn validate-price-catalogue [price-catalogue]
  (sc/validate PriceCatalogue price-catalogue))

(defn create-price-catalogue!
  [price-catalogue & [defaults]]
  (debug ">> create-price-catalogue! catalogue: " price-catalogue " defaults: " defaults)
  (let [catalogue-doc (->> price-catalogue
                           with-id
                           (merge defaults))]
    (->> catalogue-doc
         validate-price-catalogue
         (mongo/insert :price-catalogues))
    (:id catalogue-doc)))
