(ns lupapalvelu.price-catalogues
  "A common interface for accessing price catalogues and related data"
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [lupapalvelu.invoices :as invoices]
            [lupapalvelu.invoices.schemas :as invoice-schemas]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$in]]
            [schema.core :as sc]
            [sade.core :refer [ok fail] :as sade]
            [sade.schemas :as ssc]
            [sade.util :refer [to-millis-from-local-date-string]]
            [taoensso.timbre :refer [trace tracef debug debugf info infof
                                     warn warnf error errorf fatal fatalf]]))


(sc/defschema CatalogueRow
  {:code sc/Str
   :text sc/Str
   :unit invoice-schemas/InvoiceRowUnit
   :price-per-unit sc/Num
   :max-total-price (sc/maybe sc/Num)
   :min-total-price (sc/maybe sc/Num)
   :discount-percent (sc/maybe invoice-schemas/DiscountPercent)
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
          :created-by invoice-schemas/User}})

(sc/defschema PriceCatalogueInsertRequest
  {:valid-from-str sc/Str ;;TODO dd.mm.yyyy checker here
   :rows [CatalogueRow]})

(defn fetch-price-catalogues [organization-id]
  (mongo/select :price-catalogues {:organization-id organization-id}))

(defn validate-price-catalogues [price-catalogues]
  (info "validate-price-catalogues price-catalogues: " price-catalogues)
  (if-not (empty? price-catalogues)
    (sc/validate [PriceCatalogue] price-catalogues)))

(def time-format (tf/formatter "dd.MM.YYYY"))

(defn ->date [date-str]
  (tf/parse time-format date-str))

(defn tomorrow []
  (-> (t/today)
      (t/plus (t/days 1))
      (tc/to-date-time)))

(defn tomorrow-or-later? [date-str]
  (if date-str
    (not (t/before? (->date date-str) (tomorrow)))))

(defn validate-insert-price-catalogue-request [{{catalogue-request :price-catalogue} :data :as command}]
  (try
    (sc/validate PriceCatalogueInsertRequest catalogue-request)
    (if (not (tomorrow-or-later? (:valid-from-str catalogue-request)))
      (fail :error.price-catalogue.incorrect-date))

    (catch Exception e
      (warn "Invalid price catalogue request " (.getMessage e))
      (fail :error.invalid-price-catalogue))))

(defn ->price-catalogue-db
  [price-catalogue-req user organization-id]
  (debug "->price-catalogue-db price-catalogue-request: " price-catalogue-req " organization-id: " organization-id " user: " user)
  {:rows (:rows price-catalogue-req)
   :valid-from (to-millis-from-local-date-string (:valid-from-str price-catalogue-req))
   :meta {:created (sade/now)
          :created-by (invoice-schemas/->invoice-user user)}
   :organization-id organization-id})

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
