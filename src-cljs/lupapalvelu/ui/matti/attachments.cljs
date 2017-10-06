(ns lupapalvelu.ui.matti.attachments
  "Attachments related components and utilities for Matti."
  (:require [lupapalvelu.ui.attachment.components :as att]
            [lupapalvelu.ui.attachment.file-upload :as upload]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.matti.path :as path]
            [rum.core :as rum]
            [sade.shared-util :as util])
  (:import [goog.async Delay]))

(defn attachment-types
  "Overkill in-demand initialization of attachment types. Returns atom
  that will be ultimately filled."
  ([types*]
   (letfn [(make-a-call []
             (let [types (js/lupapisteApp.services.attachmentsService.attachmentTypes)]
               (if (seq types)
                 (common/reset-if-needed! types*
                                          (map #(js->clj % :keywordize-keys true) types))
                 (.start (Delay. make-a-call 200)))))]
     (make-a-call)
     types*))
  ([] (attachment-types (atom nil))))

(defn fileinfo-link
  "File link with type and size information. For example:

  IMG_2253.JPG
  JPG-kuva 2.9 MB"
  [{:keys [filename size type file-id]}]
  [:div
   [:a {:href   (str "/api/raw/view-file?fileId=" file-id)
        :target :_blank} filename]
   [:br]
   [:span.fileinfo (path/loc type) " " (js/util.sizeString size)]])

(rum/defcs type-selector < (components/initial-value-mixin ::fields)
  rum/reactive
  (rum/local nil ::types)
  [{fields* ::fields
    types* ::types} _ {:keys [schema] :as options} filedata]
  (attachment-types types*)
  (let [type-loc  (fn [& args]
                    (path/loc (util/kw-path :attachmentType args)))
        att-types (some->> (rum/react types*)
                           (filter (fn [{:keys [type-group]}]
                                     (if-let [regex (:type-group schema)]
                                       (re-matches regex type-group)
                                       true)))
                           (map (fn [{:keys [type-group type-id]}]
                                  {:group (type-loc type-group :_group_label)
                                   :text  (type-loc type-group type-id)
                                   :value (util/kw-path type-group type-id)})))]
    (when (seq att-types)
      (let [filename (keyword (:filename filedata))
            {value :value} (util/find-by-key :value
                                             (:default schema)
                                             att-types)
            set-type-fn    (fn [v]
                             (swap! fields*
                                    update filename
                                    assoc :type v))]
        (when (and value
                   (nil? (get-in (rum/react fields*)
                                 [filename :type])))
          (set-type-fn value))
        (components/autocomplete value
                                 {:items     att-types
                                  :callback  set-type-fn
                                  :disabled? (not= (:state filedata)
                                                   :success)})))))

(rum/defcs content-editor < (components/initial-value-mixin ::field)
  rum/reactive
  (rum/local nil ::types)
  [{field* ::field
    types* ::types} _ {:keys [schema] :as options} filedata]
  (attachment-types types*)
  (let [selected-type (path/react :type field*)]))

(rum/defcs attachments-batch < rum/reactive
  (components/initial-value-mixin ::file-options)
  "Metadata editor for file upload. The name is a hat-tip to the
  AttachmentBatchModel."
  [{file-options* ::file-options} _ {:keys [schema] :as options}]
  (let [{files* :files
         fields* :fields} @file-options*]
    (when (-> files* rum/react seq)
      [:div
       [:table.attachment-batch-table
        [:thead
         [:tr
          [:th [:span (path/loc :attachment.file)]]
          [:th [:span.batch-required (path/loc :application.attachmentType)]]
          [:th [:span.batch-required (path/loc :application.attachmentContents)]]
          [:th.td-center (path/loc :remove)]]]
        [:tbody
         (for [{filename :filename :as filedata} @files*]

           [:tr
            [:td (fileinfo-link filedata)]
            [:td (type-selector fields* options filedata)]
            ])]]])))

(rum/defc matti-attachments < rum/reactive
  "Displays and supports adding new attachments."
  [{:keys [schema path state] :as options}]
  (let [files*  (atom [])
        fields* (atom {})]
    [:div
     (when (path/enabled? options)
       (att/upload-wrapper {:callback  (upload/file-monitors files*)
                            :dropzone  (:dropzone schema)
                            :multiple? (:multiple? schema)
                            :component (fn [{:keys [input input-id]}]
                                         [:div
                                          input
                                          [:label.btn.positive {:for input-id }
                                           [:i.lupicon-circle-plus]
                                           [:span (path/loc :attachment.addFile)]]])}))
     (attachments-batch {:files files*
                         :fields fields*}
                        options)


     (components/debug-atom files* "Files")
     (components/debug-atom fields* "Fields")]))
