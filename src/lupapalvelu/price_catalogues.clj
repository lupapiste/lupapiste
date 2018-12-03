(ns lupapalvelu.price-catalogues
  "A common interface for accessing price catalogues and related data"
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [lupapalvelu.time-util :refer [tomorrow day-before ->date
                                           ->date-str tomorrow-or-later?]]
            [lupapalvelu.invoices :as invoices]
            [lupapalvelu.invoices.schemas :as invoice-schemas]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$in $and $lt $gt]]
            [schema.core :as sc]
            [sade.core :refer [ok fail] :as sade]
            [sade.schemas :as ssc]
            [sade.util :refer [to-millis-from-local-date-string to-finnish-date]]
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
   :valid-until (sc/maybe ssc/Timestamp)
   ;;(sc/optional-key :valid-until) ssc/Timestamp
   :rows [CatalogueRow]
   :meta {:created ssc/Timestamp
          :created-by invoice-schemas/User}})

(sc/defschema PriceCatalogueInsertRequest
  {:valid-from-str sc/Str ;;TODO dd.mm.yyyy checker here
   :rows [CatalogueRow]})

(defn fetch-price-catalogues [organization-id]
  (mongo/select :price-catalogues {:organization-id organization-id}))

(defn fetch-previous-published-price-catalogue
  [{:keys [valid-from organization-id] :as price-catalogue}]
  (debug ">> fetch-previous-published-price-catalogue catalogue: " price-catalogue)
  (if (and valid-from organization-id)
    (let [prev-catalogues (mongo/select :price-catalogues {$and [{:organization-id organization-id}
                                                                 {:valid-from {$lt valid-from}}
                                                                 {:state "published"}]})]
      (if (not (empty? prev-catalogues))
        (apply max-key :valid-from prev-catalogues)))))

(defn fetch-next-published-price-catalogue
  [{:keys [valid-from organization-id] :as price-catalogue}]
  (debug ">> fetch-next-price-catalogue catalogue: " price-catalogue)
  (if (and valid-from organization-id)
    (let [next-catalogues (mongo/select :price-catalogues {$and [{:organization-id organization-id}
                                                                   {:valid-from {$gt valid-from}}
                                                                   {:state "published"}]})]
      (if (not (empty? next-catalogues))
        (apply min-key :valid-from next-catalogues)))))

(defn fetch-same-day-published-price-catalogues
  [{:keys [valid-from organization-id] :as price-catalogue}]
  (debug ">>>> fetch-same-day-price-catalogue catalogue: " price-catalogue)
  (if (and valid-from organization-id)
    (let [same-day-catalogues (mongo/select :price-catalogues {$and [{:organization-id organization-id}
                                                                     {:valid-from valid-from}
                                                                     {:state "published"}
                                                                     ]})]
      same-day-catalogues)))

(defn validate-price-catalogues [price-catalogues]
  (debug ">> validate-price-catalogues price-catalogues: " price-catalogues)
  (if-not (empty? price-catalogues)
    (sc/validate [PriceCatalogue] price-catalogues)))

(def time-format (tf/formatter "dd.MM.YYYY"))

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

(defn catalogue-with-valid-until-one-day-before-timestamp [timestamp catalogue]
  (debug ">> catalogue-with-valid-until-one-day-before-timestamp timestamp" timestamp "for cat " (:id catalogue))
  (let [date (tc/from-long timestamp)
        timestamp-day-before (tc/to-long (day-before date))]
    (assoc catalogue :valid-until timestamp-day-before)))

(defn update-catalogue! [{:keys [id] :as catalogue}]
  ;; (println "UPDATE catalogue 3 " {:id (:id catalogue)
  ;;                                :valid-until (:valid-until catalogue)
  ;;                                 :valid-until-str (to-finnish-date (:valid-until catalogue))})
  (validate-price-catalogue catalogue)
  (mongo/update-by-id :price-catalogues id catalogue)
  id)

(defn update-previous-catalogue! [previous-catalogue {new-catalogue-start :valid-from :as new-catalogue}]
  (debug ">> update-previous-catalogue!")
  (if (and previous-catalogue (not (:valid-until previous-catalogue)))
    (let [prev-catalogue-with-valid-until (catalogue-with-valid-until-one-day-before-timestamp new-catalogue-start previous-catalogue)]
      (update-catalogue! prev-catalogue-with-valid-until))))

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

(defn delete-catalogues! [catalogues]
  (doseq [{:keys [id]} catalogues]
    (when id
      (mongo/remove :price-catalogues id))))
