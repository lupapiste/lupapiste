(ns lupapalvelu.invoices.integration.ya-sap.core
  (:require [lupapalvelu.invoices.integration.generate-impl :as impl]
            [lupapalvelu.invoices.integration.ya-sap.converter :as converter]
            [lupapalvelu.invoices.schemas :as schemas]
            [lupapalvelu.invoices.transfer-batch :as tb]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(set! *warn-on-reflection* true)

(def tampere-ya-sap-config
  {:sap-integration-id      "334"
   :sap-ordertype           "ZLUP"
   :sap-salesorganization   "1111"
   :sap-distributionchannel "00"
   :sap-division            "00"
   :sap-term-id             ""
   :sap-materialid          "502557"
   :sap-plant               "1111"
   :sap-profitcenter        "111886"
   :sap-file-prefix         "ID334_Lupapiste_"
   :sap-action              "send-to-sap"})

(defn ->idoc-row [{:keys [sap-materialid sap-plant sap-profitcenter]} invoice-row]
  {:sap-materialid   sap-materialid
   :sap-plant        sap-plant
   :sap-profitcenter sap-profitcenter
   :quantity         (:units invoice-row)
   :unitprice        (:price-per-unit invoice-row)
   :text             (:text invoice-row)})

(sc/defn ^:private ->idoc-invoice-data :- (sc/maybe schemas/IdocSapInput)
  [org-config timestamp
   {:keys [sap-number enriched-data rows billing-reference]
    :as   invoice}]
  (let [{:keys [address operation]} (:application enriched-data)]
    (when rows
      (util/assoc-when
        {;;from org config
         :sap-integration-id      (:sap-integration-id org-config)
         :sap-ordertype           (:sap-ordertype org-config)
         :sap-salesorganization   (:sap-salesorganization org-config)
         :sap-distributionchannel (:sap-distributionchannel org-config)
         :sap-division            (:sap-division org-config)
         :sap-term-id             (:sap-term-id org-config)

         ;;from application
         :target                  {:street address}
         :operation               operation

         ;;from invoice
         :customer                (util/assoc-when
                                    {}
                                    :sap-number (ss/blank-as-nil sap-number))
         :permit-id               (:application-id invoice)
         :sap-bill-date           timestamp
         :description             (:description invoice)
         :invoice-rows            (mapv (partial ->idoc-row org-config) rows)}
        :your-reference (ss/blank-as-nil billing-reference)))))

(defn ->idoc-xml [timestamp invoices]
  (->> invoices
       (keep (partial ->idoc-invoice-data tampere-ya-sap-config timestamp))
       converter/->idoc-xml))

(def ^:private sap-datetime (date/pattern-formatter "d.M.YYYY_HH.mm.ss"))

(defn filename-for-xml-sap [cfg timestamp]
  (format "%s%s_%s.xml"
          (tb/filename-prefix cfg (:sap-file-prefix tampere-ya-sap-config))
          (date/zoned-format timestamp sap-datetime)
          timestamp))

(defmethod impl/generate-invoice-transfer-batch "sap-idoc"
  [{:keys [cfg created invoices]}]
  {:integration-message-action "send-to-sap"
   :content                    (->idoc-xml created invoices)
   :filename                   (filename-for-xml-sap cfg created)
   :content-type               "text/xml;charset=UTF-8"})
