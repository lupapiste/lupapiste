(ns lupapalvelu.invoices
  "A common interface for accessing invoices, price catalogues and related data"
  (:require [taoensso.timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [schema.core :as sc]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
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
    (info "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC")
    (info ">> invoices/price-catalogue lang:" lang " application: " application)
    (info "catalogue: " catalogue)
    catalogue
    ))
