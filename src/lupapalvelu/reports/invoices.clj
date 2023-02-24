(ns lupapalvelu.reports.invoices
  (:require [lupapalvelu.reports.excel :as excel]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.invoices :as invoices]
            [lupapalvelu.money :as money]
            [sade.env :as env])
  (:import [java.io OutputStream]
           [org.joda.money Money]))

(defn fields [lang]
  ; sequence of tuples [header-loc-key data-fetch-fn]
  [["applications.id.longtitle" :application-id]
   ["invoices.state-of-invoice" (comp #(i18n/localize lang "invoices.state" %) :state)]
   ["invoice.pdf.payer" :entity-name]
   ["osoite" :entity-address]
   ["osapuoli.laskuviite" :billing-reference]
   ["sum" (comp #(.getAmount ^Money %) money/MoneyResponse->Money :sum)]
   ["sum.with-vat" (comp (fn [^Money money-with-vat] (.getAmount money-with-vat))
                         #(money/vat-value % (env/value :vat :multiplier))
                         money/MoneyResponse->Money
                         :sum)]])

(defn headers [lang]
  (->> (map first (fields lang))
       (map (fn [key]
              (if (= "sum.with-vat" key)
                (i18n/localize-and-fill lang key (env/value :vat :percentage))
                (i18n/localize lang key))))))

(defn ^OutputStream invoices-between-excel [organizationId startTs endTs lang]
  (let [invoices (invoices/fetch-invoices-for-organizations [organizationId] {:from startTs
                                                                              :until endTs
                                                                              :exclude-states ["draft"]})
        wb (excel/create-workbook
             [{:sheet-name (i18n/localize lang "invoices")
               :header     (headers lang)
               :row-fn     (apply juxt (map second (fields lang)))
               :data       invoices}])]
    (excel/hyperlinks-to-formulas! wb)
    (excel/xlsx-stream wb)))
