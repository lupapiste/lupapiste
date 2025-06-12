(ns lupapalvelu.admin.import-export.api
  "Generic API for importing/exporting data as admin"
  (:require [lupapalvelu.action :refer [defcommand defquery defraw]]
            [lupapalvelu.admin.import-export.impl :as impl]
            [lupapalvelu.admin.import-export.inspection-summary]
            [lupapalvelu.admin.import-export.model :as model]
            [lupapalvelu.admin.import-export.operations-attachment]
            [lupapalvelu.admin.import-export.phrase]
            [lupapalvelu.admin.import-export.phrase-category]
            [lupapalvelu.admin.import-export.price-catalogue]
            [lupapalvelu.admin.import-export.verdict-template]
            [lupapalvelu.admin.import-export.verdict-template-settings]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [muuntaja.core :as m]
            [sade.core :refer [fail fail! ok]]
            [sade.shared-schemas :as ssc]
            [sade.strings :as ss]
            [schema.coerce :as sco]
            [schema.core :as sc :refer [defschema]]
            [schema.utils :as scu]))

(defschema StringVector
  (sc/named [ssc/NonBlankStr] 'vector-of-strings))

(defn- csv->str-vector-coercer
  "If given a csv-like query string (\"a,b,2\"), return a vector of the fields"
  [v]
  (if (and (string? v) (not (ss/blank? v)))
    (mapv ss/trim (ss/split v #","))
    v))

(defn- keyword->str-coercer
  [v]
  ;; Our HTTP middlewares are too eager to convert keys to keywords
  ;; and sometimes we want to keep them as strings
  (cond
    (keyword? v) (name v)
    :else        v))

(def matchers {StringVector    csv->str-vector-coercer
               sc/Str          keyword->str-coercer
               ssc/ObjectIdStr keyword->str-coercer})

(defn- json-matcher
  [schema]
  (or (matchers schema)
      (sco/json-coercion-matcher schema)))

;; TODO: Might be nice if `defaction` supported some `:coercer` key,
;; which would coerce and update the values in the command under `:data`

(defn- json-coercer [schema] (sco/coercer schema json-matcher))

(def mode+target (json-coercer {:mode   model/Modes
                                :target model/Targets}))

(def target+org-id (json-coercer {:target model/Targets
                                  :org-id org/OrgId}))

(def target+org-id+item-ids (json-coercer {:target   model/Targets
                                           :org-id   org/OrgId
                                           :item-ids StringVector}))

(def org-id+filename+document (json-coercer {:org-id                     org/OrgId
                                             :filename                   ssc/NonBlankStr
                                             :document                   model/ImportFile
                                             (sc/optional-key :replace?) sc/Bool}))

(defn validate
  [coercer]
  (fn [{:keys [data]}]
    (let [result (coercer data)]
      (when (scu/error? result)
        (log/errorf "Failed to coerce: %s" (pr-str result))
        (fail :error.illegal-value:schema-validation)))))

(defquery import-export-fetch-organizations
  {:description      "Find organizations which can import (or export) target items"
   :parameters       [mode target]
   :input-validators [(validate mode+target)]
   :user-roles       #{:admin}}
  [{:keys [data]}]
  (log/with-logging-context
    {:mode   mode
     :target target}
    (let [{:keys [mode target]} (mode+target data)]
      (ok {:organizations (impl/organization-query mode target)}))))

(defquery import-export-fetch-items
  {:description      "Find (exportable) items matching the given target from an organization"
   :parameters       [target org-id]
   :input-validators [(validate target+org-id)]
   :user-roles       #{:admin}}
  [{:keys [data]}]
  (log/with-logging-context
    {:target         target
     :organizationId org-id}
    (let [{:keys [target org-id]} (target+org-id data)]
      (ok {:items (impl/item-query target org-id nil)}))))

(defraw import-export-export-items
  {:description      "Export requested items as an transit+json file"
   :parameters       [target org-id item-ids]
   :input-validators [(validate target+org-id+item-ids)]
   :user-roles       #{:admin}}
  [{:keys [data]}]
  (log/with-logging-context
    {:target         target
     :organizationId org-id}
    (let [{:keys [target org-id item-ids]} (target+org-id+item-ids data)
          {:keys [data filename]}          (impl/produce-export target org-id item-ids)]
      {:status  200
       :headers {"Content-Type"        "application/transit+json"
                 "Content-Disposition" (format "attachment; filename=\"%s\"" filename)}
       :body    (m/encode "application/transit+json" data)})))

(defn- verify-org!
  [org-id]
  (if-let [org (mongo/by-id :organizations org-id)]
    (when-let [errors (org/organization-checker org)]
      (log/errorf "Failed to validate org %, errors: %s"
                  org-id errors)
      (fail! :error.organization-invalid))
    (fail! :error.organization-not-found)))

(defcommand import-export-import-items
  {:description      "Import items contained within the given document"
   :parameters       [org-id filename document]
   :input-validators [(validate org-id+filename+document)]
   :user-roles       #{:admin}}
  [{:keys [data]}]
  (let [{:keys [filename document replace?]
         :or   {replace? false}} (org-id+filename+document data)
        {:keys [target]}         document]
    (log/with-logging-context
      {:target         target
       :organizationId org-id
       :filename       filename}
      (log/info "Starting import")
      ;; Make sure org is valid both before and after import
      (verify-org! org-id)
      (let [result (impl/import! target org-id document replace?)]
        (verify-org! org-id)
        result))))
