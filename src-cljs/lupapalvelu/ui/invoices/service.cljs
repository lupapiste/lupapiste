(ns lupapalvelu.ui.invoices.service
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.invoices.state :as state]
            [sade.shared-util :as util]))

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

(defn fetch-price-catalogue [app-id]
  (common/query :application-price-catalogue
                (fn [data]
                  (reset! state/price-catalogue (:price-catalogue data)))
                :id app-id))

(defn upsert-invoice! [app-id invoice-data callback]
  (if (:is-new invoice-data)
    (insert-invoice app-id {:operations (:operations invoice-data)} (fn [response]
                                                                      (callback response)
                                                                      (fetch-invoices app-id)
                                                                      (reset! state/new-invoice nil)))
    (common/command :update-invoice
                    (fn [response]
                      (fetch-invoice app-id
                                     (:id invoice-data)
                                     callback))
                    :id app-id
                    :invoice invoice-data)))

(defn create-invoice []
  (reset! state/new-invoice {:state "draft" :is-new true :operations []}))

(defn cancel-new-invoice []
  (reset! state/new-invoice nil))

(defn add-operation-to-invoice [invoice operation rows]
  (assoc-in invoice [:operations] (-> (:operations invoice)
                                      (conj {:operation-id operation :name operation :invoice-rows rows}))))

(defn remove-operation-from-invoice [invoice operation-index]
  (util/dissoc-in invoice [:operations operation-index]))

(defn remove-invoice-row-from-invoice [invoice operation-index invoice-row-index]
  (util/dissoc-in invoice [:operations operation-index :invoice-rows invoice-row-index]))
