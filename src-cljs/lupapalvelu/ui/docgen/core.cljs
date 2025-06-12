(ns lupapalvelu.ui.docgen.core
  "The core docgen rendering logic / dispatch."
  (:require [lupapalvelu.ui.application.consent :as field-consent]
            [lupapalvelu.ui.application.foreman-actions :as foreman-actions]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.error :as error]
            [lupapalvelu.ui.docgen.feature.other-key :as other-key]
            [lupapalvelu.ui.docgen.feature.visibility :as visibility]
            [lupapalvelu.ui.docgen.field.carbon-footprint :as carbon-footprint]
            [lupapalvelu.ui.docgen.field.carbon-footprint-threshold :as carbon-footprint-threshold]
            [lupapalvelu.ui.docgen.field.checkbox :as field-checkbox]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.company-selector :as field-company-select]
            [lupapalvelu.ui.docgen.field.construction-waste-report :as field-construction-waste-report]
            [lupapalvelu.ui.docgen.field.date :as field-date]
            [lupapalvelu.ui.docgen.field.docgen-allu-application-kind :as allu-application-kind]
            [lupapalvelu.ui.docgen.field.docgen-allu-drawings :as field-docgen-allu-drawings]
            [lupapalvelu.ui.docgen.field.docgen-allu-map :as field-docgen-allu-map]
            [lupapalvelu.ui.docgen.field.docgen-building-selector :as field-docgen-building-selector]
            [lupapalvelu.ui.docgen.field.docgen-calculation :as field-docgen-calculation]
            [lupapalvelu.ui.docgen.field.docgen-check-string :as field-check-string]
            [lupapalvelu.ui.docgen.field.docgen-checkbox :as field-docgen-checkbox]
            [lupapalvelu.ui.docgen.field.docgen-checkbox-wrapper :as field-docgen-checkbox-wrapper]
            [lupapalvelu.ui.docgen.field.docgen-date :as field-docgen-date]
            [lupapalvelu.ui.docgen.field.docgen-fill-my-info-button :as field-docgen-fill-my-info-button]
            [lupapalvelu.ui.docgen.field.docgen-funding-select :as field-funding-select]
            [lupapalvelu.ui.docgen.field.docgen-group :as field-docgen-group]
            [lupapalvelu.ui.docgen.field.docgen-huoneistot-table :as field-docgen-huoneistot]
            [lupapalvelu.ui.docgen.field.docgen-inline-string :as field-docgen-inline-string]
            [lupapalvelu.ui.docgen.field.docgen-localized-string :as field-docgen-localized-string]
            [lupapalvelu.ui.docgen.field.docgen-maaraala-tunnus :as field-docgen-maaraala]
            [lupapalvelu.ui.docgen.field.docgen-paragraph :as field-docgen-paragraph]
            [lupapalvelu.ui.docgen.field.docgen-partial-review-buildings :as field-docgen-partial-review-buildings]
            [lupapalvelu.ui.docgen.field.docgen-person-select :as field-person-select]
            [lupapalvelu.ui.docgen.field.docgen-property-group :as field-docgen-property-group]
            [lupapalvelu.ui.docgen.field.docgen-radio-group :as field-docgen-radio-group]
            [lupapalvelu.ui.docgen.field.docgen-rakennuskohde :as field-docgen-rakennuskohde]
            [lupapalvelu.ui.docgen.field.docgen-review-buildings :as field-docgen-review-buildings]
            [lupapalvelu.ui.docgen.field.docgen-review-officer :as field-docgen-review-officer]
            [lupapalvelu.ui.docgen.field.docgen-select :as field-docgen-select]
            [lupapalvelu.ui.docgen.field.docgen-string :as field-docgen-string]
            [lupapalvelu.ui.docgen.field.docgen-table :as field-docgen-table]
            [lupapalvelu.ui.docgen.field.docgen-text :as field-docgen-text]
            [lupapalvelu.ui.docgen.field.docgen-textlink :as field-docgen-textlink]
            [lupapalvelu.ui.docgen.field.docgen-time :as field-docgen-time]
            [lupapalvelu.ui.docgen.field.foreman-history :as foreman-history]
            [lupapalvelu.ui.docgen.field.foreman-other-applications :as foreman-other-applications]
            [lupapalvelu.ui.docgen.field.group :as field-group]
            [lupapalvelu.ui.docgen.field.link-permit-selector :as field-link-permit-selector]
            [lupapalvelu.ui.docgen.field.party-attachment :as party-attachment]
            [lupapalvelu.ui.docgen.field.property-list :as property-list]
            [lupapalvelu.ui.docgen.field.radio-group :as field-radio]
            [lupapalvelu.ui.docgen.field.string :as field-string]
            [lupapalvelu.ui.docgen.field.table :as field-table]
            [lupapalvelu.ui.docgen.field.text :as field-text]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [schema-tools.core :as st]
            [schema.core :as sc :refer [defschema]]))

(defn- with-schema-label
  "Set schema label to `true` if unspecified."
  [options]
  (update-in options [:schema :label] (fnil identity true)))

(defn- with-disabled-flag
  [options]
  (if (docgen-common/is-disabled? options)
    (assoc options :disabled? true)
    options))


(declare render-docgen-field)

(defschema RenderChildrenOptions
  (st/dissoc schemas/DocgenFieldArguments
             :schema))

(sc/defn render-children
  [body :- schemas/DocgenBody options :- RenderChildrenOptions]
  ;; Note that the child fields
  ;;  a) fetch data through subscriptions but also
  ;;  b) access their own (sub)schema (including all child schemas!) through an argument

  ;; The whole schema tree is quite large, but on the other hand it's also static (is it?). Alternatively we could try
  ;; to pass just paths (similar to how we already access the data through a path) and then subscribe to a particular
  ;; subschema within the whole document schema.
  (let [other-mapping (other-key/resolve-other-fields body (:path options))
        xf            (comp
                        ;; First keep only visible `body` fields
                        (visibility/filter-body-xf body options)
                        (map with-schema-label)
                        (map (partial other-key/with-primary-source other-mapping))
                        (map other-key/with-secondary-field-hidden-flag)
                        (map with-disabled-flag)
                        (map (fn [options]
                               ^{:key (path/path->str (:path options))}
                               [error/boundary (select-keys options #{:app-id :doc-id :path})
                                [render-docgen-field options]])))]
    [:<> (doall (sequence xf body))]))

(defn- dispatch-docmodel
  "Renders a docgen field, see `builders` in `docmodel.js`.

  Some schemas (incorrectly) specify KO-specific fields, like :inputType. We
  should ignore those when we are mimicking `docmodel.js` and restrict ourselves
  to what `builders` supports."
  [{:keys [schema] :as args}]
  (let [schema-component (keyword (:uicomponent schema))
        schema-type      (keyword (:type schema))]
    (case schema-component
      :constructionWasteReport    [field-construction-waste-report/render (assoc args :render-fn render-children)]
      :docgen-property-list       [property-list/render args]
      :docgenGroup                [field-docgen-group/render (assoc args :render-fn render-children)]
      :docgenHuoneistot           [field-docgen-huoneistot/render args]
      :docgenTable                [field-docgen-table/render (assoc args :render-fn render-children)]
      :hanke-table                [foreman-other-applications/render (assoc args :render-fn render-children)]
      :propertyGroup              [field-docgen-property-group/render (assoc args :render-fn render-children)]
      :carbon-footprint           [carbon-footprint/render (assoc args :render-fn render-children)]
      :exceeding-carbon-threshold [carbon-footprint-threshold/render (assoc args :render-fn render-children)]
      (do
        (when schema-component
          (println "unknown schema-component" schema-component (:path args)))
        (case schema-type
          :buildingSelector      [:f> field-docgen-building-selector/render args]
          :checkbox              [field-checkbox/render args]
          :companySelector       [field-company-select/render args]
          :consent               [field-consent/render args]
          :date                  [:f> field-date/render args]
          :fillMyInfoButton      [field-docgen-fill-my-info-button/render args]
          :foremanActions        [foreman-actions/render args]
          :foremanHistory        [foreman-history/render args]
          :fundingSelector       [field-funding-select/render args]
          ;; To enable recursive rendering (without worrying about cyclic dependencies)
          ;; -> provide the render function/component as an argument when needed
          :group                 [field-group/render (assoc args :render-fn render-children)]
          :hetu                  [field-string/render args]
          :linkPermitSelector    [:f> field-link-permit-selector/render args]
          :msDate                [:f> field-date/render args {:format :epoch}]
          :partyAttachment       [:f> party-attachment/render args]
          :personSelector        [field-person-select/render args]
          :radioGroup            [field-radio/render args]
          :rakennuskohdeSelector [field-docgen-rakennuskohde/render args]
          :select                [:f> field-docgen-select/render args]
          :string                [field-string/render args]
          :table                 [field-table/render (assoc args :render-fn render-children)]
          :text                  [:f> field-text/render args]
          ;; This is technically wrong, we should have a `docmodel.js` variant
          ;; of the `time` field. However, it seems no actual schema uses this,
          ;; all use cases end up using the KO variant.
          :time                  [:f> field-docgen-time/render args]
          :url                   [field-string/render args]
          (do
            (println "unknown schema-type" schema-type (:path args))
            [:p (str "Unknown: " schema-type)]))))))

(defn- dispatch-input-type
  [args input-type]
  (case input-type
    :check-string     [field-check-string/render args]
    :checkbox         [field-docgen-checkbox/render args]
    :checkbox-wrapper [field-docgen-checkbox-wrapper/render args]
    :inline-string    [:f> field-docgen-inline-string/render args]
    :localized-string [field-docgen-localized-string/render args]
    :paragraph        [field-docgen-paragraph/render args]
    :string           [:f> field-docgen-string/render args]
    (do (println "unknown docgen-input type: " input-type (:path args))
        [:p (str "Unknown: " input-type)])))

(defn- dispatch-ko
  "Renders a docgen field, prefers KO components"
  [{:keys [schema] :as args}]
  (let [schema-component (keyword (:uicomponent schema))
        schema-type      (keyword (:type schema))
        input-type       (keyword (:inputType schema))]
    (case schema-component
      :constructionWasteReport [field-construction-waste-report/render (assoc args :render-fn render-children)]
      :docgen-input            (dispatch-input-type args input-type)
      :docgen-property-list    [property-list/render args]
      :docgen-radio-group      [field-docgen-radio-group/render args]
      :docgen-review-buildings (if (common/feature? :partial-building-review)
                                 [field-docgen-partial-review-buildings/render (assoc args :render-fn render-children)]
                                 [field-docgen-review-buildings/render (assoc args :render-fn render-children)])
      :docgen-select           [:f> field-docgen-select/render args]
      :docgenGroup             [field-docgen-group/render (assoc args :render-fn render-children)]
      :docgenHuoneistot        [field-docgen-huoneistot/render args]
      :docgenTable             [field-docgen-table/render (assoc args :render-fn render-children)]
      :hanke-table             [foreman-other-applications/render (assoc args :render-fn render-children)]
      :maaraala-tunnus         [field-docgen-maaraala/render args]
      :propertyGroup           [field-docgen-property-group/render (assoc args :render-fn render-children)]
      (do
        (when schema-component
          (println "unknown schema-component" schema-component (:path args)))
        (if input-type
          (dispatch-input-type args input-type)
          (case schema-type
            :allu-application-kind   [allu-application-kind/render args]
            :allu-drawings           [field-docgen-allu-drawings/render args]
            :allu-map                [field-docgen-allu-map/render args]
            :buildingSelector        [:f> field-docgen-building-selector/render args]
            :calculation             [field-docgen-calculation/render args]
            :checkbox                [field-docgen-checkbox/render args]
            :companySelector         [field-company-select/render args]
            :consent                 [field-consent/render args]
            :date                    [:f> field-docgen-date/render args]
            :fillMyInfoButton        [field-docgen-fill-my-info-button/render args]
            :foremanActions          [foreman-actions/render args]
            :foremanHistory          [foreman-history/render args]
            :fundingSelector         [field-funding-select/render args]
            ;; To enable recursive rendering (without worrying about cyclic dependencies)
            ;; -> provide the render function/component as an argument when needed
            :group                   [field-docgen-group/render (assoc args :render-fn render-children)]
            :hetu                    [field-string/render args]
            :linkPermitSelector      [:f> field-link-permit-selector/render args]
            :msDate                  [:f> field-docgen-date/render args {:format :epoch}]
            :partyAttachment         [:f> party-attachment/render args]
            :personSelector          [field-person-select/render args]
            :radioGroup              [field-docgen-radio-group/render args]
            :rakennuskohdeSelector   [field-docgen-rakennuskohde/render args]
            :review-officer-dropdown [field-docgen-review-officer/render args]
            :select                  [:f> field-docgen-select/render args]
            :string                  [:f> field-docgen-string/render args]
            :table                   [field-docgen-table/render (assoc args :render-fn render-children)]
            :text                    [:f> field-docgen-text/render args]
            :textlink                [field-docgen-textlink/render args]
            :time                    [:f> field-docgen-time/render args]
            :url                     [field-string/render args]
            (do
              (println "unknown schema-type" schema-type (:path args))
              [:p (str "Unknown: " schema-type)])))))))

(sc/defn render-docgen-field
  "Renders a docgen field"
  [{:keys [prefer-ko-component?] :as args} :- schemas/DocgenFieldArguments]
  (let [args (dissoc args :render-fn)]
    (if prefer-ko-component?
      [dispatch-ko args]
      [dispatch-docmodel args])))
