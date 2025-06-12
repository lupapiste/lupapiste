(ns lupapalvelu.ui.location.model-upload
  "BIM model upload code.
  Note that `lupapalvelu.ui.location.file-upload` is a generic file handler which this is a wrapper around"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :refer [loc loc-html-reagent]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.location.file-upload :as file-upload]
            [lupapalvelu.ui.location.model-utils :as model-utils]
            [re-frame.core :as rf]
            [sade.util :as util]))

(defn upload-prompt
  "When users click Add model (lisää malli), prompt user to choose the file and set model-type"
  [component-id file-input-id]
  (let [pending-files                   (<sub [::file-upload/pending-files-for component-id])
        {:keys [metadata md5-progress]} (<sub [::file-upload/get-upload-metadata component-id])
        {:keys [in-progress?]}          md5-progress
        {related-file-ok   :ok
         related-file-name :name}       (-> pending-files first (select-keys [:ok :name]))
        selected-model-type             (:model-type metadata)
        model-type-id                   (str component-id "-model-type")
        can-add                         (and related-file-ok
                                             (some? selected-model-type))]
    [:div {:style {:height "100%"}}
     (loc-html-reagent :p :operations-locations.load-dialog.instruction)
     [:label {:for "model-file"} (loc :operations-locations.load-dialog.file-label)]
     [:div.boxgrid
      [:input#model-file.boxitem.box--3.gap--r1 {:type     :text
                                                 :disabled true
                                                 :value    (or related-file-name "")}]
      (if in-progress?
        [file-upload/file-preparation-progress-indicator component-id]
        [:label.label-button.primary {:for file-input-id}
         (loc :attachment.chooseFile)])]
     [:div.flex.flex--column.pad--t1
      [:label {:for model-type-id} (loc :operations-locations.load-dialog.model-type-label)]
      [components/dropdown
       selected-model-type
       {:id       model-type-id
        :items    (model-utils/model-type-dropdown-items)
        :sort-by  :text
        :test-id  :value
        :callback #(>evt [::file-upload/assoc-in-upload-metadata component-id
                          :model-type %])}]]
     [:div.button-group
      [:div.flex.flex--right.flex--gap2
       [:button.cancel.ghost
        {:on-click #(>evt [::cancel-add-new-file-dialog component-id])}
        (loc :button.cancel)]
       [:button.primary
        {:disabled (not can-add)
         :on-click #(>evt [::add-new-file-dialog component-id])}
        (loc :add)]]]]))

(rf/reg-event-fx ::add-new-file-dialog
  (fn [_ [_ component-id]]
    {:fx [[:dispatch [::file-upload/upload-event component-id :upload-triggered]]
          [:dialog/close true]]}))

(rf/reg-event-fx ::cancel-add-new-file-dialog
  (fn [_ [_ component-id]]
    {:fx [[:dispatch [::file-upload/upload-event component-id :reset]]
          [:dialog/close true]]}))

(rf/reg-event-fx ::open-upload-model-dialog
  (fn [_ [_ component-id file-input-id]]
    {:dialog/show {:title            (loc :operations-locations.load-dialog.title)
                   :size             :large
                   :type             :react
                   :dialog-component [upload-prompt component-id file-input-id]
                   :callback         [::file-upload/upload-event component-id :upload-triggered]
                   :cancel-callback  (partial >evt [::cancel-add-new-file-dialog component-id])
                   :minContentHeight "200px"}}))

(rf/reg-event-fx ::on-model-upload-success
  (fn [_ [_ _component-id file-version-id]]
    {:fx [[:dispatch [::model-utils/fetch-linked-file-metadatas]]
          [:dispatch [::model-utils/fetch-ifc-data-availability (when file-version-id #{file-version-id})]]]}))

(defn model-upload-button
  "Contains the file picker and upload button elements for the BIM file group.
  Shows only the upload button, or an indicator if the file upload is still initializing."
  [{:keys [id phase]}]
  (let [file-upload-id (file-upload/generate-component-id (str id "-" (name phase)))
        file-input-id  (file-upload/->file-element-id file-upload-id)]
    [:div.mr-3.mb-2
     [file-upload/file-picker
      {:id                file-upload-id
       :input-id          file-input-id
       :label-text        (loc :operations-locations.upload-version-button-label)
       :accept-types      [".ifc"]
       :on-upload-fn-args {:operation-id  id
                           :file-metadata {:model-phase phase}
                           :success-event ::on-model-upload-success}}]
     (if-not (<sub [::file-upload/is-ready? file-upload-id])
       [file-upload/file-loading-indicator file-upload-id]
       [components/icon-button
        {:class     "tertiary.caps.flex--align-center"
         :text-loc  (cond-> :operations-locations.upload-button-label
                      (= phase :completion-time) (util/kw-path :completion-time))
         :icon      :lupicon-circle-plus
         :right?    true
         :disabled? (not (<sub [::model-utils/can-upload-and-modify?]))
         :on-click  #(>evt [::open-upload-model-dialog file-upload-id file-input-id])}])]))

(defn model-version-upload
  "Upload component for uploading a new version to a specific existing IFC file"
  [{:keys [operation-id file-id file-input-id file-upload-id success-event]}]
  [:<>
   [file-upload/file-picker
    {:id                file-upload-id
     :input-id          file-input-id
     :file-id           file-id
     :auto-upload       true
     :label-text        (loc :operations-locations.upload-button-label)
     :accept-types      [".ifc"]
     :on-upload-fn-args (util/mmap file-id operation-id success-event)}]
   [file-upload/file-loading-indicator file-upload-id]])
