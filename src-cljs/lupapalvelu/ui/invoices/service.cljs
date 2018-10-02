(ns lupapalvelu.ui.invoices.service
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.invoices.state :as state]
            [sade.shared-util :as util]))

(defn insert-invoice [app-id data callback]
  (println ">> insert-invoice app-id: " app-id " data: " data)
  (common/command "insert-invoice"
                  callback
                  :id app-id
                  :invoice data))

(defn fetch-invoices [app-id]
  (common/query :application-invoices
                (fn [data]
                  (println data)
                  (reset! state/invoices (:invoices data)))
                :id app-id))

(defn upsert-invoice! [app-id invoice-data callback]
  (if (:is-new invoice-data)
    (insert-invoice app-id {:operations (:operations invoice-data)} (fn [response]
                                                                      (callback response)
                                                                      (reset! state/new-invoice nil)))
    (common/command :update-invoice
                    callback
                    :id app-id
                    :invoice invoice-data)))

(defn create-invoice []
  (reset! state/new-invoice {:state "draft" :is-new true :operations []}))

(defn cancel-new-invoice []
  (reset! state/new-invoice nil))



(defn add-operation-to-invoice [invoice operation]
  (assoc-in invoice [:operations] (-> (:operations invoice)
                                      (conj {:operation-id operation :name operation :invoice-rows []}))))
