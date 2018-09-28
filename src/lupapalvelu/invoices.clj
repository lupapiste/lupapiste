(ns lupapalvelu.invoices
  "A common interface for accessing invoices, price catalogues and related data"
  (:require [taoensso.timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [schema.core :as sc]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :refer [ok fail]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]))

(defn fetch-price-catalogue
  "Fetches price catalogue for the db"
  ([id]
   (debug ">> fetch-price-catalogue")
   (let [query {:_id id}
         result (mongo/select-one :price-catalogues query)]
     (info "result:" result)
     result)))

(sc/defschema PriceCatalogue
  {:id                                     sc/Str
   :foo                                    sc/Str})

(sc/defn ^:always-validate price-catalogue :- PriceCatalogue
  [{:keys [lang application]}]
  (let [catalogue (fetch-price-catalogue "vantaa-hinnasto-1")]
    (debug "PPPPPPPPPPPPPPPP")
    (debug ">> invoices/price-catalogue lang:" lang " application: " (keys application))
    (debug "data: " (:data application))
    (debug "application: " application)
    (debug "catalogue: " catalogue)
    catalogue
    ))

(sc/defschema InvoiceRow
  {:text sc/Str
   :unit (sc/enum "m2" "m3" "kpl")
   :price-per-unit sc/Num
   :units sc/Num})

(sc/defschema InvoiceOperation
  {:operation-id sc/Str
   :name sc/Str
   :invoice-rows [InvoiceRow]})

(sc/defschema InvoiceInsertRequest
  {:operations [InvoiceOperation]})

(defn validate-new-invoice [{{invoice-data :invoice} :data}]
  (debug ">>> validate-invoice data: " invoice-data)
  (debug "(sc/check InvoiceNewFromUI invoice-data): " (sc/check InvoiceInsertRequest invoice-data))
  (when (sc/check InvoiceInsertRequest invoice-data)
    (fail :error.invalid-invoice)))

(defn create-invoice!
  [{{invoice-data :invoice} :data}]
  (debug ">> create-invoice! invoice-data: " invoice-data)
  (let [id (mongo/create-id)]
    (mongo/insert :invoices (merge invoice-data {:id id}))
    id))
