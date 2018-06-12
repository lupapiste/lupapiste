(ns lupapalvelu.reports.store-billing
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clj-time.coerce :as tc]
            [clj-time.format :as tf]
            [sade.core :refer [now]]
            [sade.property :refer [to-human-readable-property-id]]
            [sade.strings :as str]
            [sade.util :as util :refer [fn->]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.reports.excel :as excel])
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



(def transaction-id :transaction_id)
(def created-timestamp (comp ->excel-time :created_at))
(def address :address)
(def property-id (comp to-human-readable-property-id :property_id))
(def building-ids (comp (partial str/join ", ") :building_ids))
(def document-description :description)
(defn document-type [lang]
  (fn [{doc-type :document_type}]
    (let [[type-group type-id] (str/split doc-type #"\.")]
      (i18n/localize lang "attachmentType" type-group type-id))))
(def document-price (comp #(/ % 100) :price_in_cents_without_vat))
(def verdict-date (comp ->excel-date :paatospvm))


(defn ^OutputStream billing-entries-excel [organization start-date end-date data-rows lang]
  ;; Create a spreadsheet and save it
  (let [sheet-name         (str #_(i18n/localize lang "billing.sheet-name-prefix")
                                " "
                                (->excel-date start-date)
                                " - "
                                (->excel-date end-date))
        header-row-content (map (partial i18n/localize lang) ["billing.transaction-id"
                                                              "billing.created-timestamp"
                                                              "billing.address"
                                                              "billing.property-id"
                                                              "billing.building-ids"
                                                              "billing.document-type"
                                                              "billing.document-description"
                                                              "billing.verdict-date"
                                                              "billing.document-price"])
        row-fn (juxt transaction-id
                     created-timestamp
                     address
                     property-id
                     building-ids
                     (document-type lang)
                     document-description
                     verdict-date
                     document-price)
        wb (excel/create-workbook data-rows sheet-name header-row-content row-fn)
        ; TODO lisää summarivi
        ]
    (excel/xlsx-stream wb)))

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
  (let [organization "091-R" ; TODO userista
        billing-entries (get-billing-entries-from-docstore! organization startTs endTs)]
    (billing-entries-excel organization startTs endTs billing-entries lang)))
