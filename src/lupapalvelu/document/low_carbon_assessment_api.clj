(ns lupapalvelu.document.low-carbon-assessment-api
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.building-product-catalog :as building-product-catalog]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.low-carbon-assessment.climate-report-utils :as climate-report-utils]
            [lupapalvelu.low-carbon-assessment.config :as low-carbon]
            [monger.operators :refer [$elemMatch $push $set]]
            [sade.core :refer [fail ok]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(defn- build-building-product-catalog-params [{application-id :id} {document-id :id}]
  {:id  application-id
   :doc document-id
   :docId document-id
   :documentId document-id
   :collection "building-product-catalogs"})

(defn- build-climate-report-params [{application-id :id} {document-id :id}]
  {:id  application-id
   :doc document-id
   :docId document-id
   :documentId document-id
   :collection "climate-reports"})

(defmethod action/allowed-actions-for-category :building-product-catalogs
  [command]
  (action/allowed-actions-for-collection :building-product-catalogs build-building-product-catalog-params command))

(defmethod action/allowed-actions-for-category :climate-reports
  [command]
  (action/allowed-actions-for-collection :climate-reports build-climate-report-params command))

(defn- command->catalog [{:keys [application data]}]
  (util/find-by-id (:doc data) (:building-product-catalogs application)))

(defn- valid-catalog [{:keys [data] :as command}]
  (when-not (command->catalog command)
    (fail :error.document-not-found :doc-id (:doc data))))

(defn- reused-catalog-must-not-exist [{:keys [data application]}]
  (let [catalog-doc (->> (util/filter-by-key :building-ref (:doc data) (:building-product-catalogs application))
                         (some #(= (-> % :schema-info :name) "rakennustuoteluettelo-hyodynnetyt")))]
    (when catalog-doc
      (fail :error.reused-catalog-already-exists :doc-id (:doc data)))))

(defn- document-linked-to-catalog [{:keys [data application]}]
  (let [catalog-doc (util/find-by-key :building-ref (:doc data) (:building-product-catalogs application))]
    (when-not catalog-doc
      (fail :error.document-is-not-linked-to-building-product-catalog :doc-id (:doc data)))))

(defn- ref-update-permitted [{:keys [data] :as command}]
  (let [schema (schemas/get-schema 1 (some-> (command->catalog command) :schema-info :name))]
    (when-not (some-> schema :info :ref-update-permitted?)
      (fail :error.document-not-found :doc-id (:doc data)))))

(defn low-carbon-assessment-enabled
  "Pre-check that validates the application has the necessary data for low-carbon assessment.

  For pre-ryhti applications: Requires at least one building-product-catalog
  For ryhti-2026 applications: Requires at least one document with both kohteen-laji = 'rakennus'
  AND rakennusluokka-vahahiilisyys set on the same document."
  [{application :application}]
  (if (:ryhti-data-2026 application)
    (let [rakennusluokka-path     (concat [:data] low-carbon/building-doc-rakennusluokka-vahahiilisyys-path [:value])
          kohteen-laji-path       (concat [:data] low-carbon/building-doc-kohteen-laji-path [:value])
          has-valid-building-doc? (some (fn [doc]
                                          (and (= (get-in doc kohteen-laji-path) "rakennus")
                                               (-> (get-in doc rakennusluokka-path) (ss/blank-as-nil))))
                                        (:documents application))]
      (when-not has-valid-building-doc?
        (fail :error.no-building-document-for-low-carbon-assessment)))
    (when (empty? (:building-product-catalogs application))
      (fail :error.building-product-catalogs-required-for-low-carbon-assessment))))

(defn- climate-report-exists [{:keys [data application]}]
  (when-not (util/find-by-id (:doc data) (:climate-reports application))
    (fail :error.document-not-found :doc-id (:doc data))))

(defn climate-reports-valid
  "Pre-checker that validates all climate reports in the application are valid"
  [{{climate-reports :climate-reports} :application}]
  (when-not (every? climate-report-utils/valid-climate-report? climate-reports)
    (fail :error.climate-reports-not-valid)))

(defquery climate-reports-valid
  {:description      "Pseudo query to tell if all climate reports related to the application are valid"
   :parameters       [id]
   ;; This command is relevant for tasks (reviews) so it's category is :tasks
   :categories       #{:tasks}
   :feature          :building-product-catalog
   :permissions      [{:required [:application/read]}]
   :input-validators [{:id ssc/NonBlankStr}]
   :pre-checks       [climate-reports-valid]})

(defcommand update-building-product-catalog
  {:description      "Edit an individual building-product-catalog details."
   :parameters       [id doc updates]
   :categories       #{:building-product-catalogs}
   :input-validators [{:id      ssc/NonBlankStr
                       :doc     ssc/NonBlankStr
                       :updates [[ssc/NonBlankStr]]}]
   :permissions      [{:required [:application/edit]}]
   :pre-checks       [valid-catalog]}
  [command]
  (doc-persistence/update! command doc updates "building-product-catalogs"))

(defcommand update-building-product-catalog-operation
  {:description      "Change building-product-catalog building-ref."
   :parameters       [id doc ref]
   :categories       #{:building-product-catalogs}
   :input-validators [{:id  ssc/NonBlankStr
                       :doc ssc/NonBlankStr
                       :ref ssc/NonBlankStr}]
   :permissions      [{:required [:application/edit]}]
   :pre-checks       [valid-catalog
                      ref-update-permitted]}
  [{:keys [application] :as command}]
  (let [op (util/find-by-id ref (:documents application))]
    (if (some-> op :schema-info :op)
      (or (action/update-application command
                                     {:building-product-catalogs {$elemMatch {:id doc}}}
                                     {$set {:building-product-catalogs.$.building-ref ref}})
          (ok))
      (fail :error.document-wrong-type :doc-id doc))))

(defcommand update-climate-report
  {:description      "Edit an individual climate-reports details."
   :parameters       [id doc updates]
   :categories       #{:climate-reports}
   :input-validators [{:id      ssc/NonBlankStr
                       :doc     ssc/NonBlankStr
                       :updates [[ssc/NonBlankStr]]}]
   :permissions      [{:required [:application/edit]}]
   :pre-checks       [climate-report-exists]}
  [command]
  (doc-persistence/update! command doc updates "climate-reports"))

(defcommand add-building-product-catalog-reused
  {:description      "Add reused building-product-catalog to the application."
   :parameters       [id doc copy?]
   :categories       #{:documents}
   :input-validators [{:id    ssc/NonBlankStr
                       :doc   ssc/NonBlankStr
                       :copy? sc/Bool}]
   :permissions      [{:required [:application/edit]}]
   :states           #{:constructionStarted}
   :pre-checks       [document-linked-to-catalog
                      reused-catalog-must-not-exist]}
  [{:keys [created application] :as command}]
  (let [document    (tools/by-id application "documents" doc)
        schema-name (if (-> document :schema-info :name (= "rakennuspaikka"))
                      "rakennustuoteluettelo-rakennuspaikka-hyodynnetyt"
                      "rakennustuoteluettelo-hyodynnetyt")
        new-catalog (if copy?
                      (building-product-catalog/copy-building-product-catalog-document
                        doc
                        schema-name
                        command)
                      (building-product-catalog/new-building-product-catalog-document
                        doc
                        schema-name
                        command))]
    (action/update-application command
      {$push {:building-product-catalogs new-catalog}})
    (ok)))

(defquery low-carbon-assessment
  {:description      "Pseudo query to tell if the low-carbon assessment tab should be visible"
   :parameters       [id]
   :categories       #{:documents}
   :feature          :building-product-catalog
   :permissions      [{:required [:application/read]}]
   :input-validators [{:id ssc/NonBlankStr}]
   :pre-checks       [low-carbon-assessment-enabled]})
