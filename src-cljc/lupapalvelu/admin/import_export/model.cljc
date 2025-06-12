(ns lupapalvelu.admin.import-export.model
  (:require #?@(:clj [[lupapalvelu.invoices.schemas :as invoice]
                      [lupapalvelu.attachment :as attachment]
                      [lupapalvelu.pate.schemas :as pate-clj-schemas]])
            [lupapalvelu.pate.shared-schemas :as pate-schemas]
            [sade.shared-schemas :as ssc]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc :refer [defschema]]))

(def modes [{:id       :import
             :text-loc :admin.import-export.import
             :icon     :lupicon-download}
            {:id       :export
             :text-loc :admin.import-export.export
             :icon     :lupicon-upload}])

(def targets [{:id       :price-catalogue
               :text-loc :auth-admin.price-catalogue.title
               :icon     :lupicon-shopping-cart}
              {:id       :phrase-category
               :text-loc :admin.import-export.phrase-category
               :icon     :lupicon-documents}
              {:id       :phrase
               :text-loc :admin.import-export.phrase
               :icon     :lupicon-quote}
              ;; NOTE: Default attachments for different operation types are
              ;; sometimes called "default attachments" and sometimes "operations attachments"
              {:id       :operations-attachment
               :text-loc :admin.attachments.default-attachments
               :icon     :lupicon-paperclip}
              {:id       :verdict-template
               :text-loc :admin.import-export.verdict-template
               :icon     :lupicon-circle-section-sign}
              {:id       :verdict-template-settings
               :text-loc :admin.import-export.verdict-template-settings
               :icon     :lupicon-circle-section-sign}
              {:id       :inspection-summary
               :text-loc :admin.import-export.inspection-summary
               :icon     :lupicon-check}])

(defschema Modes
  "Are we importing or exporting"
  (apply sc/enum (map :id modes)))

(defschema Targets
  "What type of items are we processing"
  (apply sc/enum (map :id targets)))

(defschema BaseImportFile
  {:target   Targets
   :org-id   ssc/NonBlankStr
   :env      ssc/NonBlankStr ; prod, qa, dev, local,..
   :items    {sc/Str sc/Any}})

(defn- target-fn
  [expected-target]
  (fn [{:keys [target]}]
    (util/=as-kw expected-target target)))

;; Price catalogue

(defschema PriceCatalogue
  ;; Would be nice to use a better schema in the frontend, but that would
  ;; require moving quite a few schemas to `.cljc`
  #?(:cljs sc/Any
     :clj invoice/PriceCatalogue))

(defschema PriceCatalogueItems {ssc/ObjectIdStr PriceCatalogue})

(defschema PriceCatalogueImportFile
  (st/merge BaseImportFile
            {:items PriceCatalogueItems}))

;; Phrase category

(defschema PhraseCategory
  (->> [:fi :sv :en]
       (map #(vector (sc/optional-key %) sc/Str))
       (into {})))

(defschema PhraseCategoryItems {ssc/ObjectIdStr PhraseCategory})

(defschema PhraseCategoryImportFile
  (st/merge BaseImportFile
            {:items PhraseCategoryItems}))

;; Phrases

(defschema PhraseItems {ssc/ObjectIdStr pate-schemas/Phrase})

(defschema PhraseImportFile
  (st/merge BaseImportFile
            {:items PhraseItems}))

;; Operations attachment

(defschema AttachmentId+Group
  [(sc/one #?(:cljs sc/Keyword
              :clj attachment/TypeGroup) 'type-group)
   (sc/one #?(:cljs sc/Keyword
              :clj attachment/TypeId) 'type-id)])

(defschema OperationsAttachmentItems {sc/Str [AttachmentId+Group]})

(defschema OperationsAttachmentFile
  (st/merge BaseImportFile
            {:items OperationsAttachmentItems}))

;; Verdict templates

(defschema VerdictTemplateItem #?(:clj  pate-clj-schemas/PateSavedTemplate
                                  :cljs sc/Any))

(defschema VerdictTemplateItems {ssc/ObjectIdStr VerdictTemplateItem})

(defschema VerdictTemplateFile
  (st/merge BaseImportFile
            {:items VerdictTemplateItems}))

;; Verdict template settings

(defschema VerdictTemplateSettingsItem #?(:clj  pate-clj-schemas/PateSavedSettings
                                          :cljs sc/Any))
(defschema VerdictTemplateSettingsItems {sc/Str VerdictTemplateSettingsItem})

(defschema VerdictTemplateSettingsFile
  (st/merge BaseImportFile
            {:items VerdictTemplateSettingsItems}))

;; Inspection summary (templates)

(sc/defschema InspectionSummaryItem
  {:id       ssc/ObjectIdStr
   :name     sc/Str
   :modified ssc/Timestamp
   :items    [sc/Str]})

(defschema InspectionSummaryItems {sc/Str InspectionSummaryItem})

(defschema InspectionSummaryFile
  (st/merge BaseImportFile
            {:items InspectionSummaryItems}))

;; Import/export file schema

(defschema ImportFile
  (sc/conditional
    (target-fn :price-catalogue) PriceCatalogueImportFile
    (target-fn :phrase-category) PhraseCategoryImportFile
    (target-fn :phrase) PhraseImportFile
    (target-fn :operations-attachment) OperationsAttachmentFile
    (target-fn :verdict-template) VerdictTemplateFile
    (target-fn :verdict-template-settings) VerdictTemplateSettingsFile
    (target-fn :inspection-summary) InspectionSummaryFile
    :else BaseImportFile))

(def import-checker (sc/checker ImportFile))
