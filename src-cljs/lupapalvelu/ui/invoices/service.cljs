(ns lupapalvelu.ui.invoices.service
  (:require [lupapalvelu.invoices.shared.util :refer [keys-used-to-update-invoice]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.invoices.state :as state]
            [lupapiste-commons.shared-utils :as util]
            [rum.core :as rum]))

(defn vec-remove
  [coll pos]
  (vec (concat (subvec coll 0 pos) (subvec coll (inc pos)))))

(defn insert-invoice [app-id data callback]
  (common/command "insert-invoice"
                  callback
                  :id app-id
                  :invoice data))

(def drafts-last-then-time-created
  (juxt
   (fn [invoice] (= "draft" (:state invoice)))
   :created))

(defn fetch-invoices [app-id]
  (common/query :application-invoices
                (fn [data]
                  (reset! state/invoices (sort-by
                                          drafts-last-then-time-created
                                          >
                                          (:invoices data))))
                :id app-id))

(defn fetch-invoice [app-id invoice-id callback]
  (common/query :fetch-invoice
                (fn [data]
                  (callback (:invoice data)))
                :id app-id
                :invoice-id invoice-id))

(defn fetch-operations [app-id]
  (common/query :application-operations
                (fn [data]
                  (reset! state/operations (:operations data)))
                :id app-id))

(defn fetch-price-catalogues [app-id]
  (-> (js/lpAjax.query "application-price-catalogues" (clj->js {:id app-id}))
      (.success (fn [js-result]
                  (->> (js->clj js-result :keywordize-keys true)
                       :price-catalogues
                       (map (fn [catalog]
                              (update catalog :rows
                                      (partial map-indexed
                                               #(assoc %2 :order-number %1)))))
                       (reset! state/price-catalogues))))
      (.error js/util.showSavedIndicator)
      .call))

(defn delete-invoice [app-id invoice-id]
  (common/command :delete-invoice
                  (fn [response]
                    (fetch-invoices app-id))
                  :id app-id
                  :invoice-id invoice-id))

(defn upsert-invoice! [app-id invoice callback]
  (if (:is-new invoice)
    (insert-invoice app-id (select-keys invoice [:operations :price-catalogue-id :work-start-ms :work-end-ms])
                    (fn [response]
                      (callback response)
                      (fetch-invoices app-id)))
    (common/command :update-invoice
                    (fn [response]
                      (fetch-invoice app-id (:id invoice) callback))
                    :id app-id
                    :invoice (select-keys invoice (cons :id keys-used-to-update-invoice)))))

(defn create-invoice []
  (common/command {:command :new-invoice-draft
                   :success (fn [{:keys [invoice]}]
                              (swap! state/invoices (partial cons invoice)))}
                  :id @state/application-id
                  :price-catalogue-id (some-> state/price-catalogues deref first :id)))

(defn remove-operation-from-invoice [invoice operation-index]
  (util/dissoc-in invoice [:operations operation-index]))

(defn remove-invoice-row-from-invoice [invoice operation-index invoice-row-index]
  (util/dissoc-in invoice [:operations operation-index :invoice-rows invoice-row-index]))

(defn update-worktime [invoice* start-ts end-ts]
  (common/command {:command :update-invoice-worktime
                   :show-saved-indicator? true
                   :success (fn [{:keys [workdays]}]
                              (swap! invoice* assoc
                                     :workdays workdays
                                     :work-start-ms start-ts
                                     :work-end-ms end-ts))
                   :error #(reset! (rum/cursor-in invoice* [:workdays]) {:error true})}
                  :id (:application-id @invoice*)
                  :invoice-id (:id @invoice*)
                  :start-ts start-ts
                  :end-ts end-ts))

(defn fetch-backend-id-codes []
  (if (state/auth? :invoicing-backend-id-codes)
      (common/query :invoicing-backend-id-codes
                    (fn [response]
                      (reset! state/backend-id-codes (:codes response)))
                    :id @state/application-id)
      (reset! state/backend-id-codes [])))

(defn set-backend-id-code [invoice* code]
  (common/command :set-invoice-backend-code
                  #(swap! invoice* assoc :backend-code code)
                  :id @state/application-id
                  :invoice-id (:id @invoice*)
                  :code code))
