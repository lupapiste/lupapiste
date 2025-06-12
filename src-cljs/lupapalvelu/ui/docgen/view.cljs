(ns lupapalvelu.ui.docgen.view
  "Handle views where we display docgen documents within accordions."
  (:require [goog.object :as googo]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.application.building-product-catalog-dialog :as building-product-catalog-dialog]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.accordion :as accordion]
            [lupapalvelu.ui.docgen.component.document-building-reference :as document-building-reference]
            [lupapalvelu.ui.docgen.component.error :as error]
            [lupapalvelu.ui.docgen.core :as docgen]
            [lupapalvelu.ui.docgen.feature.append-doc :as append-doc]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.document-types :as doc-types]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.docgen.state]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]]))

(defschema DisplayDocOptions
  {:app-id                                 sc/Str
   :doc-id                                 sc/Str
   :doc-ids                                [sc/Str]
   (sc/optional-key :disabled?)            sc/Bool
   (sc/optional-key :override-auth-source) schemas/OverrideAuthSource
   (sc/optional-key :expand-accordions?)   sc/Bool})

(sc/defn display-doc
  [{:keys [app-id doc-id doc-ids override-auth-source disabled? expand-accordions?]} :- DisplayDocOptions]
  (let [disabled? (or disabled? (<sub [::docs/disabled? doc-id]))
        body      (:body (<sub [::docs/schema doc-id]))]
    [:div.docgen-content ; docgenDiv
     [accordion/accordion
      (util/mmap doc-id override-auth-source expand-accordions?)
      [docgen/render-children body (util/assoc-when
                                     {:app-id app-id
                                      :doc-id doc-id
                                      :path   []}
                                     :disabled? disabled?
                                     :override-auth-source override-auth-source)]]
     ;; Some documents allow creating an (empty) copy of them, e.g. party documents
     (when (<sub [::append-doc/show-button? doc-id doc-ids disabled? override-auth-source])
       [append-doc/append-doc-button (util/mmap app-id doc-id)])]))

(defn- display-docs
  [doc-ids & {:as options}]
  (let [app-id (<sub [::app/application-id])]
    [:<>
     (->> doc-ids
          (map (fn [doc-id]
                 (let [options (assoc options
                                      :app-id app-id
                                      :doc-id doc-id
                                      :doc-ids doc-ids)]
                   ^{:key doc-id}
                   [error/boundary (select-keys options #{:app-id :doc-id})
                    [display-doc options]])))
          doall)]))

(defn- application-view
  []
  (let [doc-ids (<sub [::doc-types/non-party-doc-ids])]
    [display-docs doc-ids]))

(defn- parties-view
  []
  (let [ids (<sub [::doc-types/party-doc-ids])]
    [display-docs ids]))

(defn- app-and-parties-view
  []
  (let [ids (<sub [::doc-types/app-and-parties-doc-ids])]
    [display-docs ids]))

(defn- construction-view
  []
  (let [ids (<sub [::doc-types/editable-doc-ids])]
    [display-docs ids]))

(rf/reg-event-fx ::confirm-catalog-add
  (fn [_ [_ id]]
    {:dialog/show {:ltitle           "rakennustuoteluettelo.hyodynnetyt.add.title"
                   :size             :large
                   :type             :react
                   :dialog-component [building-product-catalog-dialog/view {:doc-id id}]
                   :minContentHeight "10em"}}))

(rf/reg-sub ::operation-documents
  :<- [::docs/op-documents]
  (fn [docs]
    (->> docs
         (sort-by (comp :created second))
         (into []))))

(defn- building-product-location-catalogs-view
  []
  (let [doc-ids (<sub [::doc-types/building-product-catalog-location-doc-ids])]
    (when (seq doc-ids)
      (let [location-doc-id         (<sub [::doc-types/location-doc-id])
            reused-products-catalog (<sub [::doc-types/reused-building-product-catalog location-doc-id])]
        [:div.mb-12
         [:h2.mb-6 (common/loc :rakennuspaikka._group_label)]
         [display-docs doc-ids]
         (when (and (not reused-products-catalog)
                    (<sub [::auth/auth? location-doc-id :add-building-product-catalog-reused nil]))
           [:button.primary.right
            {:on-click #(>evt [::confirm-catalog-add location-doc-id])}
            (common/loc :add.buildingProductCatalog.reused)])]))))

(defn- climate-reports-location-view
  []
  (let [doc-ids (<sub [::doc-types/climate-report-location-doc-ids])]
    (when (seq doc-ids)
      [:div
       [display-docs doc-ids]])))

(defn- low-carbon-buildings-view
  "This renders one 'building-section' in the low-carbon-assessment view.
   All building-product-catalogs and climate-reports for that section gets grouped together."
  []
  (when-let [op-docs (seq (<sub [::operation-documents]))]
    [:<>
     (->> op-docs
          (map (fn [[doc-id doc]]
                 (let [reused-products-catalog (<sub [::doc-types/reused-building-product-catalog doc-id])
                       catalog-doc-ids         (<sub [::doc-types/building-product-catalogs-for-op-doc-id doc-id])
                       report-doc-ids          (<sub [::doc-types/climate-reports-for-op-doc-id doc-id])]
                   (when (or (seq catalog-doc-ids) (seq report-doc-ids))
                     ^{:key doc-id}
                     [:div.mt-16
                      [:h2.mb-6 (document-building-reference/op-title doc)]
                      (when (seq catalog-doc-ids)
                        [:<>
                         [display-docs catalog-doc-ids]
                         (when (and (not reused-products-catalog)
                                    (<sub [::auth/auth? doc-id :add-building-product-catalog-reused nil]))
                           [:button.primary.right
                            {:on-click #(>evt [::confirm-catalog-add doc-id])}
                            (common/loc :add.buildingProductCatalog.reused)])])
                      (when (seq report-doc-ids)
                        [display-docs report-doc-ids])]))))
          doall)]))

(defn- neighbor-view
  []
  (let [doc-ids (<sub [::doc-types/non-party-doc-ids])]
    [display-docs doc-ids {:disabled?            true
                           :expand-accordions?   true
                           :override-auth-source :auth/application?}]))

(defn- neighbor-parties-view
  []
  (let [doc-ids (<sub [::doc-types/party-doc-ids])]
    [display-docs doc-ids {:disabled?            true
                           :expand-accordions?   true
                           :override-auth-source :auth/application?}]))

(defn- bulletin-view
  []
  (let [doc-ids (<sub [::doc-types/all-ids])]
    [display-docs doc-ids {:disabled?            true
                           :expand-accordions?   true
                           :override-auth-source :auth/disabled}]))

(defn view
  [params]
  (case (:doc-type params)
    :application-and-parties-documents  [app-and-parties-view]
    :application-documents              [application-view]
    :building-product-location-catalogs [building-product-location-catalogs-view]
    :bulletin                           [bulletin-view]
    :climate-reports-location           [climate-reports-location-view]
    :construction-time-documents        [construction-view]
    :low-carbon-buildings               [low-carbon-buildings-view]
    :neighbor                           [neighbor-view]
    :neighbor-parties                   [neighbor-parties-view]
    :party-documents                    [parties-view]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id ^js params]
  ;; TODO: remove debug
  (println "Mounting cljs docgen," dom-id (.stringify js/JSON params))
  (ui-dom/mount! mounter dom-id {:dom-id   dom-id
                                 :doc-type (keyword (googo/get params "docType"))}))
