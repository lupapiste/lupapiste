(ns lupapalvelu.ui.location.model-details-page
  "IDS and compliance check results for given model file version.
  Compatible with the Solibri integration (see `model-details` for the other integration)"
  (:require [lupapalvelu.bim.checking-plan.schema :as bim-cp-schema]
            [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.day-edit :as day-edit]
            [lupapalvelu.ui.components.info-grid :as info-grid]
            [lupapalvelu.ui.docgen.component.collapse :as collapse]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.lazy :as lazy]
            [lupapalvelu.ui.location.file-upload :as file-upload]
            [lupapalvelu.ui.location.locations-operations :as locations-operations]
            [lupapalvelu.ui.location.model-solibri-result :as solibri-result]
            [lupapalvelu.ui.location.model-solibri-result-subs :as solibri-result-subs]
            [lupapalvelu.ui.location.model-subs :as model-subs]
            [lupapalvelu.ui.location.model-upload :as model-upload]
            [lupapalvelu.ui.location.model-utils :as model-utils]
            [lupapalvelu.ui.tab-panel :as tab-panel]
            [reagent.core :as r]
            [sade.util :as util]
            [shadow.lazy :as shadow-lazy]))

(defn version-validation-errors [version]
  (when-let [result (-> version :bim :validation-result)]
    (->> result
         ((juxt :tests-count :tests-passed))
         (apply -))))

(defn operation-button-row []
  (let [application-id (<sub [::model-subs/app-id])
        operation-id   (<sub [::model-subs/operation-id])
        file-id        (<sub [::model-subs/file-id])
        file-upload-id (file-upload/generate-component-id operation-id :file-id file-id)
        file-input-id  (file-upload/->file-element-id file-upload-id)]
    [:div
     [:div.flex.justify-between
      [:div
       (components/icon-button {:on-click #(>evt [::model-subs/back-to-structures application-id])
                                :class    :secondary
                                :icon     :lupicon-chevron-left
                                :text-loc :back})]
      (when (and (<sub [::model-subs/valid-model-phase?])
                 (<sub [:auth/application? :init-resumable-upload-for-application]))
        [:div.flex.gap-8
         [model-upload/model-version-upload (util/mmap {:success-event ::model-subs/model-version-upload.success}
                                                       operation-id file-id file-input-id file-upload-id)]
         (components/icon-button {:on-click #(>evt [::model-subs/click-file-input file-input-id])
                                  :class    :primary.gap--r1
                                  :icon     :lupicon-circle-plus
                                  :text-loc :model-details-page.update-model})])]
     [:hr]]))

(defn page-header []
  (let [application-address (<sub [::model-subs/application-address])
        operation-name      (<sub [::model-subs/operation-name])]
    [:div
     [:h2 application-address]
     [:span (common/loc (str "operations." operation-name))]]))

(defn bcf-file-download-url [file-id file-version-id check-type]
  (let [url (str "/api/raw/linked-file-bim-result-bcf?file-id=" file-id
                 "&file-version-id=" file-version-id
                 "&check-type=")]
    (case check-type
      :validation (str url "validation")
      :compliance (str url "compliance")
      nil)))

(defn bim-check-result-info-grid-row [check-type]
  (let [file-id              (<sub [::model-subs/file-id])
        model-type           (<sub [::model-subs/model-type])
        file-version-id      (<sub [::model-subs/selected-version-id])
        selected-version     (<sub [::model-subs/selected-version])
        check-result         (bim-cp-schema/linked-file-version-check-result check-type selected-version)
        job-ts               (<sub [::model-subs/selected-version-checked-ts check-type])
        operation-id         (<sub [::model-subs/operation-id])
        bcf-link-href        (bcf-file-download-url file-id file-version-id check-type)
        pending-job-ids      (<sub [::model-subs/pending-job-ids])
        sub-task             (keyword (bim-cp-schema/check-type->sub-task-name check-type))
        model-check-pending? (<sub [::model-subs/check-pending? pending-job-ids sub-task])]
    {:title              (common/loc (str "model-details-page.check-type." (name check-type)))
     :content            (or job-ts "--.--.-- --.--")
     :right-side-content [:div.flex.items-top {:class "gap-[1px]"}
                          (when model-check-pending?
                            ;; In progress spinner
                            [:div.text-sm.pr-8.-mb-2
                             {:class "py-[0.375rem]"}
                             [:i.lupicon-refresh.icon-spin.text-sm
                              {:title (common/loc :operations-locations.check-model-pending)}]
                             [:span.leading-none.hidden.sm:inline-block
                              (common/loc :operations-locations.check-model-pending)]])

                          ;; Download BCF
                          [info-grid/square-button {:icon      :lupicon-download
                                                    :text      (common/loc :model-details-page.download-result-file)
                                                    :href      bcf-link-href
                                                    :disabled? (boolean (or (not job-ts)
                                                                            (-> check-result :check-failed)))}]
                          ;; Re-run check
                          [info-grid/square-button {:icon      :lupicon-refresh
                                                    :text      (common/loc (if job-ts
                                                                             :model-details-page.check-model-again
                                                                             :model-details-page.check-model))
                                                    :disabled? (or model-check-pending?
                                                                   (not (<sub [:auth/application? :check-model])))
                                                    :on-click  #(>evt [::model-subs/start-check-model
                                                                       (case check-type
                                                                         :validation :ids
                                                                         check-type)
                                                                       operation-id
                                                                       file-id
                                                                       model-type
                                                                       selected-version])}]]}))

(defn file-download-link [file-id version-id file-name size]
  (when file-id
    [:span [:a {:href (model-utils/->download-url file-id :file-version-id version-id)} file-name]
     " (" (common/sizeString size) ")"]))

(defn additional-file-upload [component-id-prefix target-type {:keys [existing-file-id preserve-file-id? accept-types
                                                                      button-type reverse-button-order?
                                                                      disabled?]}]
  (let [operation-id   (<sub [::model-subs/operation-id])
        parent-file-id (<sub [::model-subs/file-id])
        file-upload-id (file-upload/generate-component-id component-id-prefix :file-id parent-file-id)
        file-input-id  (file-upload/->file-element-id file-upload-id)
        success-event  ::model-subs/model-version-upload.success]
    [:div.flex.justify-between
     [:div.flex.gap-8
      {:class (when reverse-button-order? "flex-row-reverse")}
      [:div.hidden
       [file-upload/file-picker
        {:id                file-upload-id
         :input-id          file-input-id
         :file-id           existing-file-id
         :preserve-file-id? preserve-file-id?
         :auto-upload       true
         :label-text        (common/loc :operations-locations.upload-button-label)
         :accept-types      accept-types
         :on-upload-fn-args (util/mmap target-type parent-file-id operation-id success-event)}]]
      [:span.text-sm
       [file-upload/file-loading-indicator file-upload-id :hide-bytes true]]
      (if (= button-type :square-button)
        [info-grid/square-button {:icon      :lupicon-upload
                                  :text      (common/loc :model-details-page.add-new-file)
                                  :disabled? (boolean disabled?)
                                  :label-for file-input-id}]
        (components/icon-button {:class     :secondary.no-twp
                                 :icon      :lupicon-plus
                                 :right?    true
                                 :disabled? (boolean disabled?)
                                 :text-loc  :model-details-page.add-new-file
                                 :on-click  #(>evt [::model-subs/click-file-input file-input-id])}))]]))

(defn bim-inspection-report []
  (let [bim-inspection-report (<sub [::model-subs/bim-inspection-report])
        version               (-> bim-inspection-report :versions model-subs/get-file-version)
        {:keys [name size]}   (-> version :metadata)
        file-id               (-> bim-inspection-report :target-entity :file-id)
        version-id            (-> version :metadata :file-version-id)]
    {:title              (common/loc :model-details-page.model-inspection-report)
     :content            [file-download-link file-id version-id name size]
     :right-side-content [:div.flex.items-top {:class "gap-[1px]"}
                          [additional-file-upload
                           "inspection-report"
                           "bim-inspection-report"
                           {:existing-file-id  file-id
                            :disabled?         (not (<sub [:auth/application? :init-resumable-upload-for-application]))
                            :accept-types      [".pdf"]
                            :button-type       :square-button
                            :preserve-file-id? true}]
                          [info-grid/square-button
                           {:icon      :lupicon-trash
                            :text      (common/loc :model-details-page.remove-file)
                            :disabled? (or (not file-id)
                                           (not (<sub [:auth/application? :delete-linked-file])))
                            :on-click  #(>evt [::model-subs/delete-additional-file file-id])}]]}))

(defn info-table []
  (let [model-type                     (<sub [::model-subs/model-type])
        file-id                        (<sub [::model-subs/file-id])
        selected-version-info          (<sub [::model-subs/selected-version-info])
        selected-version-file-name     (<sub [::model-subs/selected-version-file-name])
        selected-version-file-size     (<sub [::model-subs/selected-version-file-size])
        selected-version-id            (<sub [::model-subs/selected-version-id])
        selected-version-ifc-version   (<sub [::model-subs/selected-version-ifc-version])
        selected-version-ifc-timestamp (<sub [::model-subs/selected-version-ifc-timestamp])
        rakennusluokka                 (<sub [::model-subs/rakennusluokka])
        rakennusluokka-enabled         (<sub [::model-subs/rakennusluokka-enabled])]

    [info-grid/info-grid [;; Model version in Lupapiste
                          {:title   (common/loc :model-details-page.version)
                           :content selected-version-info}

                          ;; Rakennusluokka
                          (when rakennusluokka-enabled
                            {:title   (common/loc :rakennusluokka)
                             :content (some->> rakennusluokka
                                               (path/loc :rakennusluokka))})

                          ;; Model type
                          {:title   (common/loc :model-details-page.model-type)
                           :content (path/loc :application.bim.model-type model-type)}

                          ;; Model file
                          {:title   (common/loc :model-details-page.file)
                           :content [:span [:a.no-twp {:tab-index 0
                                                       :on-click  #(>evt [::locations-operations/open-download-file-dialog
                                                                          file-id
                                                                          selected-version-id])}
                                            selected-version-file-name]
                                     " (" (common/sizeString selected-version-file-size) ")"]}

                          ;; Model timestamp
                          (when selected-version-ifc-timestamp
                            {:title   (common/loc :model-details-page.model-timestamp)
                             :content selected-version-ifc-timestamp})

                          ;; Model IFC version
                          (when selected-version-ifc-version
                            {:title   (common/loc :model-details-page.model-ifc-version)
                             :content selected-version-ifc-version})

                          ;; Validation check
                          (bim-check-result-info-grid-row :validation)

                          ;; Compliance check
                          (when (<sub [::model-subs/bim-compliance-check-enabled])
                            (bim-check-result-info-grid-row :compliance))

                          ;; BIM model inspection report
                          (bim-inspection-report)]]))

(defn additional-file-item [file ryhti-data-2026]
  (r/with-let [expanded? (r/atom false)]
    (let [file-id                                               (-> file :target-entity :file-id)
          version                                               (-> file :versions model-subs/get-file-version)
          version-id                                            (-> version :file-version-id)
          metadata                                              (-> version :metadata)
          {:keys [name size document-identifier document-date]} metadata
          collapse-id                                           (str "collapse-id-" file-id)
          date-input-id                                         (str "additional-file-document-date-" file-id)
          identifier-input-id                                   (str "additional-file-document-identifier-" file-id)
          disabled?                                             (not (<sub [:auth/application? :init-resumable-upload-for-application]))]
      (fn []
        [:div
         [:div.flex.justify-between.items-center.gap-2
          [:span.py-1.px-3
           [file-download-link file-id version-id name size]]
          [:div.flex.gap-2.items-center
           [info-grid/square-button
            {:icon      :lupicon-trash
             :disabled? (not (<sub [:auth/application? :delete-linked-file]))
             :text      (common/loc :model-details-page.remove-file)
             :on-click  #(>evt [::model-subs/delete-additional-file file-id])}]
           (when ryhti-data-2026
             [info-grid/square-button
              {:icon          (if @expanded? :lupicon-chevron-up :lupicon-chevron-down)
               :text          (common/loc (if @expanded? :a11y.collapse :a11y.expand))
               :aria-expanded @expanded?
               :aria-controls collapse-id
               :on-click      #(swap! expanded? not)}])]]
         (when ryhti-data-2026
           [collapse/collapse {:open? @expanded?
                               :id    collapse-id}
            [:div.flex.flex-wrap.gap-4.mt-2.p-2
             [:div.flex.flex-col.gap-1
              [:label.lux {:for date-input-id}
               (common/loc :attachment.label.documentDate)]
              [day-edit/day-edit
               document-date
               {:id        date-input-id
                :disabled? disabled?
                :format    :string
                :callback  #(>evt [::model-subs/save-additional-file-metadata
                                   version-id :document-date %])}]]
             [:div.flex.flex-col.gap-1
              [:label.lux {:for identifier-input-id}
               (common/loc :attachment.label.documentIdentifier)]
              [components/text-edit
               document-identifier
               {:id        identifier-input-id
                :on-blur?  true
                :disabled? disabled?
                :callback  #(>evt [::model-subs/save-additional-file-metadata
                                   version-id :document-identifier %])}]]]])]))))

(defn additional-files-list []
  (let [ryhti-data-2026 (<sub [::model-subs/ryhti-data-2026])]
    [:div
     [:h3.strong (common/loc :model-details-page.additional-files)]
     [:div.twp
      (when-let [bim-additional-files (seq (<sub [::model-subs/bim-additional-files]))]
        (into [:div.mb-4
               {:class (concat info-grid/responsive-width
                               info-grid/collapsing-grid-borders)}]
              (for [file bim-additional-files]
                [additional-file-item file ryhti-data-2026])))
      (when (<sub [:auth/application? :init-resumable-upload-for-application])
        [additional-file-upload
         "additional-file-upload"
         "bim-additional-file"
         {:reverse-button-order? true
          :accept-types          [".pdf" ".smc" ".bcf"
                                  "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                  "application/msword"
                                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                  "application/vnd.ms-excel"]}])]]))

(defn model-version-history []
  (let [selected-version-id (<sub [::model-subs/selected-version-id])
        versions            (<sub [::model-subs/versions])]
    [:table.mt-4.no-twp
     [:thead
      [:tr
       [:th (common/loc :model-details-page.version)]
       [:th (common/loc :model-details-page.date)]
       [:th (common/loc :model-details-page.result)]
       [:th [:span.pad--l1 [:span.pad--l05 (common/loc :model-details-page.actions)]]]]]
     (into [:tbody]
           (for [version (sort-by :uploaded-at > versions)]
             (let [selected? (= (:file-version-id version) selected-version-id)
                   td-params (when selected? {:class ["bg--blue pad--h2"]})
                   errors    (version-validation-errors version)]
               [:tr (when selected? {:class "strong"})
                [:td td-params (model-subs/version-number version)]
                [:td td-params (model-subs/version-edit-info version)]
                [:td td-params (if (-> version :bim :validation-result :check-failed)
                                 (common/loc :error.dialog.title)
                                 (case errors
                                   nil "-"
                                   0 (common/loc :operations-locations.check-model-result-success)
                                   1 (common/loc :model-details-page.error)
                                   (common/loc :model-details-page.errors errors)))]
                [:td td-params
                 (if selected?
                   [:div.pad--l1.pad--t1 {:style {:height "40px"}}
                    [:span.pad--l05 (common/loc :model-details-page.this-file)]]
                   [:span
                    (components/icon-button {:on-click #(>evt [::model-subs/open-model-version (:file-version-id version)])
                                             :class    :tertiary
                                             :text-loc :model-details-page.open-this-file})])]])))]))

(defn lazy-bim-viewer
  "Use Shadow CLJS's and React's lazy loading for the BIM viewer to break it off from the rest of the UI code
  and therefore allowing the 2 MB xeokit-sdk library it uses to be loaded only when needed"
  []
  [lazy/lazy-component
   #_{:clj-kondo/ignore [:unresolved-namespace]}
   {:loadable (shadow-lazy/loadable lupapalvelu.ui.location.bim-viewer.viewer/bim-viewer)
    :params   [{:application-id (<sub [::model-subs/app-id])
                :operation-id   (<sub [::model-subs/operation-id])}]}])

(defn page []
  (let [bim-compliance-check-enabled (<sub [::model-subs/bim-compliance-check-enabled])
        check-results                (<sub [::solibri-result-subs/selected-version-check-results])
        archived-version-count       (-> (<sub [::model-subs/versions]) count dec)]
    (when (<sub [::model-subs/application-data])
      [:div.container.flex.flex-col.gap-8 {:role :main}
       [operation-button-row]
       [page-header]
       [info-table]
       [additional-files-list]
       [lazy-bim-viewer]
       [tab-panel/tab-panel
        {:id        "model-details-tabbar"
         :class     "h-full"
         :tab-class "h-full"
         :key->str  #(common/loc %)
         :tabs      (cond-> []
                      (:validation-result check-results) (conj {:key          :model-details-page.check-type.validation
                                                                :content      [solibri-result/result-table]
                                                                :on-select-fn #(>evt [::solibri-result-subs/set-selected-result-type :validation-result])})
                      (and bim-compliance-check-enabled
                           (:compliance-result check-results)) (conj {:key          :model-details-page.check-type.compliance
                                                                      :content      [solibri-result/result-table]
                                                                      :on-select-fn #(>evt [::solibri-result-subs/set-selected-result-type :compliance-result])})
                      (pos? archived-version-count) (conj {:key     :model-details-page.show-version-history
                                                           :content [model-version-history]
                                                           :after   (str archived-version-count)}))}]])))

(defonce mounter (ui-dom/init :reagent (fn [] page)))

(defn ^:export start [dom-id _]
  (ui-dom/mount! mounter dom-id))
