(ns lupapalvelu.reports.invoices
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.invoices.core :as invoices]
            [lupapalvelu.invoices.util :as invoices-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.reports.excel :as excel]
            [monger.operators :refer :all]
            [sade.util :as util])
  (:import [java.io OutputStream]))

(set! *warn-on-reflection* true)

(defn fields [lang]
  ; sequence of tuples [header-loc-key data-fetch-fn]
  [["applications.id.longtitle" :application-id]
   ["invoices.state-of-invoice" #(i18n/localize lang "invoices.state" (:state %))]
   ["invoice.pdf.payer" invoices-util/payer-name]
   ["osoite" (partial invoices-util/payer-address lang)]
   ["turvakielto" :protected?]
   ["osapuoli.laskuviite" :billing-reference]
   ["sum" (comp invoices-util/cents->num :sum-total-cents)]])

(defn headers [lang]
  (mapv (util/fn->> first (i18n/localize lang))
        (fields lang)))

(defn invoices-between-excel
  ^OutputStream [organizationId startTs endTs lang]
  ;; Since the amount of invoices can be arbitrarily high, we'll fetch all the needed
  ;; applications beforehand. Otherwise network delays could become untolerable (e.g.,
  ;; frontend timeout).
  (let [invoices        (invoices/fetch-invoices-for-organizations [organizationId]
                                                                   {:from            startTs
                                                                    :until           endTs
                                                                    :exclude-states? true
                                                                    :states          ["draft"]})
        application-ids (some->> invoices
                                 (filter :application-payer?)
                                 seq
                                 (map :application-id)
                                 set)
        applications    (when application-ids
                          (->> (mongo/select :applications {:_id {$in application-ids}} [:documents])
                               (map (juxt :id identity))
                               (into {})))
        invoices        (map (fn [{:keys [application-id] :as invoice}]
                               (-> (invoices/enrich-sum-total invoice)
                                   (invoices-util/resolve-invoice-payer-fields (get applications application-id))))
                             invoices)
        wb              (excel/create-workbook
                          [{:sheet-name (i18n/localize lang "invoices")
                            :header     (headers lang)
                            :row-fn     (apply juxt (map second (fields lang)))
                            :data       invoices}])]
    (excel/xlsx-stream wb)))
