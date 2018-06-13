(ns lupapalvelu.reports.store-billing
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clj-time.coerce :as tc]
            [clj-time.format :as tf]
            [dk.ative.docjure.spreadsheet :as spreadsheet]
            [sade.core :refer [def- now]]
            [sade.property :refer [to-human-readable-property-id]]
            [sade.strings :as str]
            [sade.util :as util :refer [fn->]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.reports.excel :as excel]
            [lupapalvelu.user :as user])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream OutputStream)
           (org.apache.poi.xssf.usermodel XSSFWorkbook)
           (org.apache.poi.ss.usermodel CellType)
           (org.joda.time DateTime DateTimeZone)))

(defn- ->excel-time [timestamp-string]
  (-> timestamp-string
      tc/to-long
      util/to-local-datetime))

(defn- ->excel-date [timestamp-string]
  (-> timestamp-string
      tc/to-long
      util/to-local-date))

(defn cents->euros [cents] (/ cents 100))

(def- transaction-id :transaction_id)
(def- created-timestamp (comp ->excel-time :created_at))
(def- address :address)
(def- property-id (comp to-human-readable-property-id :property_id))
(def- building-ids (comp (partial str/join ", ") :building_ids))
(def- document-description :description)
(defn- document-type [lang]
  (fn [{doc-type :document_type}]
    (let [[type-group type-id] (str/split doc-type #"\.")]
      (i18n/localize lang "attachmentType" type-group type-id))))
(def- document-price (comp cents->euros :price_in_cents_without_vat))
(def- verdict-date (comp ->excel-date :paatospvm))

(defn- total-price-of-documents [data-rows]
  (->> data-rows
       (map :price_in_cents_without_vat)
       (apply +)
       cents->euros))

(def- total-amount-of-documents count)

(def- billing-sheet-column-localization-keys
  ["billing.transaction-id"
   "billing.created-timestamp"
   "billing.address"
   "billing.property-id"
   "billing.building-ids"
   "billing.document-type"
   "billing.document-description"
   "billing.verdict-date"
   "billing.document-price"])

(defn- document-entry-rows
  "Returns a vector of rows. Each row contains data of a single bought document."
  [data-rows lang]
  (mapv (juxt transaction-id
              created-timestamp
              address
              property-id
              building-ids
              (document-type lang)
              document-description
              verdict-date
              document-price)
        data-rows))

(def- separator-row [[]])

(defn- document-summary-rows
  "Returns a summary containing the amount of documents sold and the total price."
  [data-rows lang]
  [[nil
    (i18n/localize lang "billing.excel.documents")
    (i18n/localize lang "billing.excel.sum-price")]
   [(i18n/localize lang "billing.excel.sum")
    (total-amount-of-documents data-rows)
    (total-price-of-documents data-rows)]])

(defn billing-entries-sheet [organization start-date end-date data-rows lang]
  {:sheet-name (str (->excel-date start-date)
                    " - "
                    (->excel-date end-date))
   :header (mapv (partial i18n/localize lang)
                 billing-sheet-column-localization-keys)
   :row-fn identity
   :data (concat (document-entry-rows data-rows lang)
                 separator-row
                 (document-summary-rows data-rows lang))})

(defn ^OutputStream billing-entries-excel [organization start-date end-date data-rows lang]
  (-> (billing-entries-sheet organization start-date end-date data-rows lang)
      vector
      excel/create-workbook
      excel/xlsx-stream))

(defn get-billing-entries-from-docstore! [organization startTs endTs]
  (-> (http/get "http://localhost:5000/api/billing/entries" ; TODO properties
                {:query-params {:organization organization
                                :startTimestampMillis startTs
                                :endTimestampMillis endTs}
                 :basic-auth "lupis:lupis"}) ; TODO properties
      :body
      (json/parse-string true)
      vec))

(defn billing-entries [user startTs endTs lang]
  (let [organization "091-R" ; (usr/authority-admins-organization-id user)
        billing-entries (get-billing-entries-from-docstore! organization startTs endTs)]
    (billing-entries-excel organization startTs endTs billing-entries lang)))
