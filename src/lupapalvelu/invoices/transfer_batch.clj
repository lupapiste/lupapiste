(ns lupapalvelu.invoices.transfer-batch
  (:require
   [sade.schemas :as ssc]
   [monger.operators :refer [$in]]
   [schema.core :as sc]
   [lupapalvelu.money :as money]
   [sade.core :as sade]
   [lupapalvelu.mongo :as mongo]
   [lupapalvelu.invoices.schemas :refer [User
                                         Invoice
                                         InvoiceId
                                         ->invoice-user
                                         InvoiceInTransferBatch
                                         InvoiceTransferBatch
                                         TransferBatchResponseTransferBatch
                                         TransferBatchOrgsResponse]]))

(def transfer-batch-db-key :invoice-transfer-batches)

(defn validate-invoice-transfer-batch [invoice-transfer-batch]
  (sc/validate InvoiceTransferBatch invoice-transfer-batch))

(defn validate-invoice-transfer-batches-orgs-response [invoice-transfer-batch-response]
  (sc/validate TransferBatchOrgsResponse invoice-transfer-batch-response))

(defn ->invoice-transfer-batch-db
  "Enrich invoice-transfer-batch map with user data,
  so that it can be _inserted_ to database

  `invoice-transfer-batch` is invoice-transfer-batch map
  `org-id` Organization id added to invoice-transfer-batch
  `user` is user map to be transferred to `lupapalvelu.invoices/User`

  Returns `lupapalvelu.invoices.transfer-batch/InvoiceTransferBatch`"
  [invoice-transfer-batch org-id user]
  (merge invoice-transfer-batch
         {:created (sade/now)
          :created-by (->invoice-user user)
          :organization-id org-id
          :number-of-rows 0
          :invoices []
          :sum {:minor 0
                :major 0
                :currency ""
                :text ""}}))

(defn- count-rows-in-invoice-transfer-batch [invoice-transfer-batch ]
  (let [invoices (:invoices invoice-transfer-batch)
        rows (flatten (map (fn [invoice]
                             (:invoice-rows invoice)) invoices))]
    (count rows)))

(defn- count-rows-in-operations [operations]
  (apply + (map (comp count :invoice-rows) operations)))

(defn- count-rows-in-invoice [invoice]
  (let [operations (:operations invoice)]
    (count-rows-in-operations operations)))

(defn- fetch-existing-transfer-batch [org-id]
  (mongo/select-one transfer-batch-db-key {:organization-id org-id}))

(defn- create-new-transfer-batch [org-id user]
  (let [transfer-batch (->invoice-transfer-batch-db {} org-id user)
        id (mongo/create-id)
        transfer-batch-with-id (assoc transfer-batch :id id)]
    (validate-invoice-transfer-batch transfer-batch-with-id)
    (mongo/insert transfer-batch-db-key transfer-batch-with-id)
    id))

(defn get-or-create-invoice-transfer-batch-for-org [org-id user]
  (let [existing-transfer-batch (fetch-existing-transfer-batch org-id)]
    (if existing-transfer-batch
      (:id existing-transfer-batch)
      (create-new-transfer-batch org-id user))))

(defn- ->tb-invoice [invoice]
  {:id (:id invoice)
   :added-to-transfer-batch (sade/now)
   :organization-id (:organization-id invoice)})

(defn- invoices-for-transferbatch [transfer-batch]
  (let [invoices-in-tb (:invoices transfer-batch)
        invoice-ids (map :id invoices-in-tb)]
    (mongo/select :invoices {:_id {$in invoice-ids}})))

(defn- invoice-to-transfer-batch [transfer-batch invoice]
  (let [invoice-data-to-tb (->tb-invoice invoice)
        invoices (:invoices transfer-batch)
        added-invoices (conj invoices invoice-data-to-tb)
        tb-with-invoices (assoc transfer-batch :invoices added-invoices)
        invoices-from-db (invoices-for-transferbatch tb-with-invoices)
        number-of-invoice-rows (reduce + (map (fn [invoice]
                                                (count-rows-in-invoice invoice)) invoices-from-db))
        invoice-sums-as-money (map (fn [invoice]
                                     (let [invoice-sum (:sum invoice)
                                           currency (money/->currency-code (:currency invoice-sum))
                                           money-value (money/minor->currency currency (:minor invoice-sum))]
                                       {:sum money-value}))
                                   invoices-from-db)
        sum (money/sum-by :sum invoice-sums-as-money)]
    (assoc tb-with-invoices :number-of-rows number-of-invoice-rows :sum sum)))

(defn add-invoice-to-transfer-batch
  "inserts invoice to transfer batch. Checks if organization has open transferbatch, and adds invoice to that.
  If organization does not have open transferbatch, creates new one an inserts invoice to there.
  If transfer batch would have more rows than is should, closes transferbatch and creates new, and adds invoice there."
  [invoice user-who-adds-invoice]
  (let [transfer-batch (mongo/by-id transfer-batch-db-key (get-or-create-invoice-transfer-batch-for-org (:organization-id invoice) user-who-adds-invoice))
        transfer-batch-with-invoice-added (invoice-to-transfer-batch transfer-batch invoice)]
    (validate-invoice-transfer-batch transfer-batch-with-invoice-added)
    (mongo/update-by-id transfer-batch-db-key (:id transfer-batch) transfer-batch-with-invoice-added)
    (:id transfer-batch)))

(defn fetch-transfer-batches-for-org
  "Returns transferbatches as vector for organization"
  [org-id]
  (vec (mongo/select transfer-batch-db-key {:organization-id org-id})))

(defn get-transfer-batch-for-orgs
  "Retrurns transferbatch response, where transfer batches and corresponding invoices are mapped by organization id.
  Basically this is the way they're handled in billing ui."
  [org-ids]
  (let [response (reduce (fn [memo org-id]
                           (let [transfer-batches-for-org (fetch-transfer-batches-for-org org-id)
                                 transfer-batches-for-response (mapv (fn [tb]
                                                                      (let [invoices-for-tb (vec (invoices-for-transferbatch tb))
                                                                            invoice-count (count invoices-for-tb)
                                                                            invoice-row-count (:number-of-rows tb)]
                                                                        {:id (:id tb)
                                                                         :transfer-batch tb
                                                                         :invoice-count invoice-count
                                                                         :invoice-row-count invoice-row-count
                                                                         :invoices invoices-for-tb}))
                                                                     transfer-batches-for-org)]
                             (assoc memo org-id transfer-batches-for-response))) {} org-ids)]
    (validate-invoice-transfer-batches-orgs-response response)))
