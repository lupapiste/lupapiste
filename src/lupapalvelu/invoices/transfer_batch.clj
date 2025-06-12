(ns lupapalvelu.invoices.transfer-batch
  "Transfer batch is a collection of invoices and works as grouping mechanism for integration."
  (:require [lupapalvelu.integrations.messages :as imessages]
            [lupapalvelu.invoices.schemas :refer [InvoiceTransferBatch TransferBatchHistoryEntry]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$in $push $set $unset]]
            [sade.core :refer [fail fail!]]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(def transfer-batch-db-key :invoice-transfer-batches)

(defn command->transfer-batch
  "Fetches the transfer batch from mongo or fail!'s"
  [{data :data}]
  (or (mongo/select-one transfer-batch-db-key
                        {:_id             (:transfer-batch-id data)
                         :organization-id (:organizationId data)})
      (fail! :error.not-found)))

(defn transfer-batch-check
  "Returns a pre-checker that fails if the transfer-batch does not exist or any of the
  optional checks fails. Supported checks: `:empty`, `:not-empty`, `:open` and `:closed` (must be
  empty/not-empty/open/closed respectively)."
  [& checks]
  {:pre [(every? #{:empty :not-empty :open :closed} checks)]}
  (let [{:keys [empty not-empty open closed]} (zipmap checks (cycle [true]))]
    (fn [command]
      (when-let [batch-id (some-> command :data :transfer-batch-id)]
        (let [{:keys [organization-id
                      state]} (command->transfer-batch command)
              open?           (= state "open")
              *any?           (delay (mongo/any? :invoices {:organization-id   organization-id
                                                            :transfer-batch-id batch-id}))]

          (cond
            (or (and open (not open?))
                (and closed open?))
            (fail :error.transfer-batch.wrong-state)

            (and empty @*any?)
            (fail :error.transfer-batch.not-empty)

            (and not-empty (not @*any?))
            (fail :error.transfer-batch.empty)))))))

(def ^:private valid-transfer-batch-history-entry! (sc/validator TransferBatchHistoryEntry))

(defn make-history-entry [{:keys [user created]} action & [message-id]]
  (->> {:user       (usr/summary user)
        :time       created
        :action     (name action)
        :message-id message-id}
       util/strip-nils
       valid-transfer-batch-history-entry!))

(def ^:private valid-invoice-transfer-batch! (sc/validator InvoiceTransferBatch))

(defn insert-new-transfer-batch
  "Inserts new open transfer batch to mongo. Returns the batch id."
  ([{:keys [created] :as command} organization-id data]
   (let [id (mongo/create-id)]
     (->> (merge {:id              id
                  :created         created
                  :history         [(make-history-entry command "create")]
                  :organization-id organization-id
                  :state           "open"}
                 data)
          valid-invoice-transfer-batch!
          (mongo/insert transfer-batch-db-key))
     id))
  ([command organization-id]
   (insert-new-transfer-batch command organization-id nil))
  ([{:keys [data] :as command}]
   (insert-new-transfer-batch command (:organizationId data))))

(defn set-transfer-batch-state [{:keys [user created] :as command} state]
  (let [batch-id         (:id (command->transfer-batch command))
        open?            (util/=as-kw state :open)
        [action
         invoice-state ] (if open?
                           [:open :confirmed]
                           [:close :transferred])
        updates          (cond-> {$set  {:state state}
                                  $push {:history (make-history-entry command
                                                                      action)}}
                           open?       (assoc $unset {:closed true})
                           (not open?) (assoc-in [$set :closed] created))]
    (mongo/update-by-id transfer-batch-db-key batch-id updates)
    (mongo/update-by-query :invoices
                           {:transfer-batch-id batch-id}
                           {$set {:state       invoice-state
                                  :modified    created
                                  :modified-by (usr/summary user)}})))

(defn transfer-batch-rowcount [batch-id]
  (->> (mongo/select :invoices {:transfer-batch-id batch-id} [:rows])
       (map (comp count :rows))
       (apply +)))

(defn transfer-batch-for-invoice
  "Finds a transfer batch for `invoice`. The transfer batch must be open and have enough
  'room' for the invoice rows. If `candidate-batch-id` is given, the search pool consists
  of only it. Returns the transfer batch id (or nil if not found)."
  ([{:keys [organization-id rows]} candidate-batch-id]
   (let [row-limit (some-> (get (org/get-invoicing-config organization-id)
                                :max-invoice-row-count-in-transfer-batch)
                           (- (count rows)))
         batch-ids (map :id
                        (mongo/select transfer-batch-db-key
                                      (util/assoc-when {:state           :open
                                                        :organization-id organization-id}
                                                       :_id candidate-batch-id)
                                      [:id]
                                      {:created 1}))]
     (if row-limit
       (util/find-first #(>= row-limit (transfer-batch-rowcount %)) batch-ids)
       (first batch-ids))))
  ([invoice] (transfer-batch-for-invoice invoice nil)))

(defn filename-prefix
  ([{:keys [invoice-file-prefix]} default-prefix]
   (ss/replace (or (ss/blank-as-nil invoice-file-prefix) default-prefix "")
               #"\s+" ""))
  ([cfg]
   (filename-prefix cfg nil)))

(defn save-integration-message
  "Saves integration message and returns it."
  [{:keys [user created]}
   {:keys [id organization-id]} xml action]
  (let [message {:id          (mongo/create-id)
                 :direction   "out"
                 :messageType "deliver-transfer-batch-to-invoicing-system"
                 :format      "xml"
                 :created     created
                 :status      "processing"
                 :initiator   (select-keys user [:id :username])
                 :action      action
                 :data        {:xml               xml
                               :transfer-batch-id id
                               :organization-id   organization-id}} ]
    (imessages/save message)
    message))

(defn mark-sent
  "Marks the integration message as processed and updates the transfer batch history
  and invoices."
  [command batch-id {:keys [id action]} invoice-ids]
  (imessages/set-message-status id "processed")
  (mongo/update-by-id transfer-batch-db-key
                      batch-id
                      {$set  {:sent (:created command)}
                       $push {:history (make-history-entry command
                                                           action
                                                           id)}})
  (mongo/update-by-query :invoices
                         {:_id {$in (set invoice-ids)}}
                         {$set {:sent (:created command)}}))
