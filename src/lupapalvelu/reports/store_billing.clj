(ns lupapalvelu.reports.store-billing
  (:require [clj-http.client :as http]
            [clj-time.coerce :as tc]
            [clojure.string :as str]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.json :as json]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.reports.excel :as excel]
            [monger.operators :refer :all]
            [sade.core :refer [def-]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.property :refer [to-human-readable-property-id]]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import (java.io OutputStream)
           (java.util Locale)))

(defn- ->excel-time [timestamp-string]
  (date/finnish-datetime timestamp-string :zero-pad))

(defn- ->excel-date [timestamp-string]
  (date/finnish-date timestamp-string :zero-pad))

(defn cents->euros [cents]
  ;; Locale/ROOT forces the decimal separator to be "." regardless of locale settings in the environment:
  (Double/parseDouble (String/format Locale/ROOT "%.2f" (to-array [(/ (bigdec cents) 100)]))))

(defn resolve-fee
  "For recent documents, the organization fee is part of the document data. If not, or if it is zero,
  the fee is calculated from the document price (price - fixed commission of one euro). Return value
  is integer (cents)."
  [{fee   :organization_fee
    price :price_in_cents_without_vat}]
  (if (and fee (pos? fee))
    fee
    (- price 100)))

(defn- municipality [lang]
  #(i18n/localize lang (format "municipality.%s" (get-in % [:municipality :id]))))
(def- transaction-id :transaction_id)
(def- created-timestamp (comp ->excel-time :created_at))
(def- address :address)
(def- property-id (comp to-human-readable-property-id :property_id))
(def- building-ids (comp (partial ss/join ", ") :building_ids))
(def- document-description :description)
(defn- document-type [lang]
  (fn [{doc-type :document_type}]
    (let [[type-group type-id] (ss/split doc-type #"\.")]
      (i18n/localize lang "attachmentType" type-group type-id))))
(def- verdict-date (comp ->excel-date :paatospvm))
(def- organization-fee (comp cents->euros resolve-fee))

(defn- total-price-of-documents [data-rows]
  (->> data-rows
       (map resolve-fee)
       (apply +)
       cents->euros))

(def- total-amount-of-documents count)

(def- sheet-header-loc
  ["billing.municipality"
   "billing.transaction-id"
   "billing.created-timestamp"
   "billing.address"
   "billing.property-id"
   "billing.building-ids"
   "billing.document-type"
   "billing.document-description"
   "billing.verdict-date"
   "billing.organization-fee"])

(defn- document-entry-rows
  "Returns a vector of rows. Each row contains data of a single bought document."
  [data-rows lang show-municipality-name?]
  (mapv (apply juxt
               (filter some?
                 [(when show-municipality-name? (municipality lang))
                  transaction-id
                  created-timestamp
                  address
                  property-id
                  building-ids
                  (document-type lang)
                  document-description
                  verdict-date
                  organization-fee]))
        data-rows))

(def- separator-row [[]])

(defn- document-summary-rows
  "Returns a summary containing the amount of documents sold and the total price."
  [data-rows lang]
  [[nil
    (i18n/localize lang "billing.excel.documents")
    (i18n/localize lang "billing.excel.sum-price")
    (i18n/localize lang "billing.excel.vat")]
   [(i18n/localize lang "billing.excel.sum")
    (total-amount-of-documents data-rows)
    (total-price-of-documents data-rows)
    0]])

(defn billing-entries-sheet [start-date end-date lang data-rows]
  (let [multiple-municipalities?  (< 1 (count (distinct (map #(get-in % [:municipality :id]) data-rows))))
        entry-rows                (document-entry-rows data-rows lang multiple-municipalities?)
        summary-rows              (document-summary-rows data-rows lang)]
    {:sheet-name (str (date/finnish-date start-date :zero-pad)
                      " - "
                      (date/finnish-date end-date :zero-pad))
     :header (mapv (partial i18n/localize lang) (drop (if multiple-municipalities? 0 1) sheet-header-loc))
     :row-fn identity
     :data (concat entry-rows separator-row summary-rows)}))

(defn ^OutputStream billing-entries-excel [start-date end-date lang data-rows]
  (-> (billing-entries-sheet start-date end-date lang data-rows)
      vector
      excel/create-workbook
      excel/xlsx-stream))

(defn get-billing-entries-from-docstore! [organization start-ts end-ts]
  (-> (http/get (str (env/value :store-billing :url)
                     (when-not organization
                       "/all"))
                {:query-params (util/assoc-when {:startTimestampMillis start-ts
                                                 :endTimestampMillis end-ts}
                                                :organization organization)
                 :basic-auth [(env/value :store-billing :basic-auth :username)
                              (env/value :store-billing :basic-auth :password)]})
      :body
      (json/decode true)
      vec))

(defn billing-entries
  "Returns an Excel response containing the billing entries for the given organization.
   If organizationId is nil, returns a response containing all billing entries regardless of organization.
   The response with multiple organizations also includes a column containing the organization for that row."
  [organization-id start-ts end-ts lang]
  (->> (get-billing-entries-from-docstore! organization-id start-ts end-ts)
       (billing-entries-excel start-ts end-ts lang)))

;----------------------
;Report for downloads
;----------------------

(def- doc-api-user :apiUser)
(def- doc-application-id (comp :applicationId :metadata))
(def- doc-address (comp :address :metadata))
(def- downloaded-timestamp :timestamp)

(def- doc-property-id #(when-let [property-id (-> % :metadata :propertyId)]
                         (to-human-readable-property-id property-id)))
(defn- doc-type
  [lang]
  (fn [{{type :type} :metadata}]
    (when type
      (let [[type-group type-id] (ss/split type #"\.")]
        (i18n/localize lang "attachmentType" type-group type-id)))))

(defn- doc-municipality-name
  [lang]
  #(when-let [org (:organization %)]
     (i18n/localize lang (str "municipality." (-> org (str/split #"-") first)))))

(def- downloads-header-loc
  ["auth-admin.organization-name"
   "billing.api-user"
   "billing.created-timestamp"
   "application.applicationSummary"
   "billing.document-type"
   "billing.address"
   "billing.property-id"])

(def- summary-header-loc
  ["auth-admin.organization-name"
   "billing.api-user"
   "billing.downloads-count"])

(defn timestamps-to-excel-time-format
  [coll index]
  (mapv #(update % index (fn [ts] (-> ts ->excel-time))) coll))

(defn docstore-downloads
  [organization-id start-ts end-ts]
  (let [query {:timestamp {$gte start-ts $lte end-ts}}
        query (if organization-id
                (assoc query :organization organization-id)
                query)]
    (mongo/select :archive-api-usage
                  query
                  [:apiUser :_id :organization :timestamp :metadata.address
                   :metadata.applicationId :metadata.type :metadata.propertyId])))

(defn- rows-for-downloads
  "Returns a vector of rows. Each row contains data of a single downloaded document."
  [downloads lang]
  (mapv (apply juxt
               [(doc-municipality-name lang)
                doc-api-user
                downloaded-timestamp
                doc-application-id
                (doc-type lang)
                doc-address
                doc-property-id])
        downloads))

(defn- create-groups
  [data keywords]
  (->> data (group-by (apply juxt keywords)) vals))

(defn- downloaded-documents-count
  "Returns a vector of rows. Rows are type: [org apiUser count]. Where count shows how many documents apiUser
  has downloaded from a certain organization."
  [downloads lang]
  (let [keywords [:apiUser :organization]
        groups    (create-groups downloads keywords)]
    (mapv (apply juxt
                 [(comp (doc-municipality-name lang) first)
                  (comp :apiUser first)
                  (comp count)])
          groups)))

(defn downloads-entries-sheets
  [organization-id start-ts end-ts lang]
  (let [start-ts         (util/->long start-ts)
        end-ts           (util/->long end-ts)
        sheet-name       (str (date/finnish-date start-ts)
                              " - "
                              (date/finnish-date end-ts))
        downloads-header (map (partial i18n/localize lang)
                                 (->> downloads-header-loc (remove nil?)))
        downloads        (docstore-downloads organization-id start-ts end-ts)
        index-of-ts      2
        document-rows    (-> (rows-for-downloads downloads lang)
                                sort
                                (timestamps-to-excel-time-format index-of-ts))
        summary-phrase   [[(i18n/localize lang "billing.summary")]]
        summary-header   (map (partial i18n/localize lang)
                                 (->> summary-header-loc (remove nil?)))
        summary-rows     (-> (downloaded-documents-count downloads lang)
                                sort)]
    {:sheet-name sheet-name
     :header     downloads-header
     :row-fn     identity
     :data       (concat document-rows separator-row summary-phrase [summary-header] summary-rows)}))

(defn ^OutputStream docstore-downloads-entries
  [organization-id start-ts end-ts lang]
  (-> (downloads-entries-sheets organization-id start-ts end-ts lang)
      vector
      excel/create-workbook
      excel/xlsx-stream))
