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

(defn field-info [fields* {filename :filename}]
  (let [c (rum/cursor-in fields* [(keyword filename)])]
    (rum/react c)))

(defn set-field [fields* {filename :filename} key value]
  (swap! fields*
         update (keyword filename)
         assoc key value))

(defn- kw-type [{:keys [type-group type-id]}]
  (util/kw-path type-group type-id))

(defn type-loc [& args]
  (path/loc (util/kw-path :attachmentType args)))

(rum/defcs type-selector < rum/reactive
  (rum/local nil ::types)
  [{types* ::types} {:keys [schema files* fields*] :as options} filedata]
  (attachment-types types*)
  (let [att-types (some->> (rum/react types*)
                           (filter (fn [{:keys [type-group]}]
                                     (if-let [regex (:type-group schema)]
                                       (re-matches regex type-group)
                                       true)))
                           (map (fn [{:keys [type-group type-id] :as type}]
                                  {:group (type-loc type-group :_group_label)
                                   :text  (type-loc type-group type-id)
                                   :value (kw-type type)})))]
    (when (seq att-types)
      (let [filename (keyword (:filename filedata))
            {value :value} (util/find-by-key :value
                                             (:default schema)
                                             att-types)
            set-type-fn (fn [type]
                          (set-field fields* filedata :type type)
                          (set-field fields* filedata :contents nil))]
        (when (and value
                   (-> (field-info fields* filedata) :type nil?))
          (set-field fields* filedata :type value))
        (components/autocomplete value
                                 {:items     att-types
                                  :callback  set-type-fn
                                  :disabled? (not= (:state filedata)
                                                   :success)})))))

(rum/defcs contents-editor < rum/reactive
  (rum/local [] ::types)
  [{types*  ::types} {fields* :fields*} {filename :filename :as filedata}]
  (attachment-types types*)
  (let [{:keys [type contents]} (field-info fields* filedata)
        att-types               (rum/react types*)
        set-fn                  (partial set-field fields* filedata :contents)]
    (when (and type
               (seq att-types))
      (let [items   (some->> att-types
                             (util/find-first #(= type (kw-type %)))
                             :metadata
                             :contents
                             (map #(path/loc :attachments.contents %))
                             sort
                             (map #(hash-map :text % :value %)))
            default (if (<= (count items) 1)
                      (or (-> items first :text)
                          (type-loc type))
                      "")]
        (when-not contents
          (set-fn default))
        (components/combobox (rum/cursor-in fields*
                                            [(keyword filename) :contents])
                             {:items     items
                              :callback  set-fn
                              :required? true
                              :disabled? (not= (:state filedata)
                                               :success)})))))

(rum/defc attachments-batch < rum/reactive
  "Metadata editor for file upload. The name is a hat-tip to the
  AttachmentBatchModel."
  [{:keys [schema files* fields*] :as options}]
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
          [:td.batch--file (fileinfo-link filedata)]
          [:td.batch--type (type-selector options filedata)]
          [:td.batch--contents (contents-editor options filedata)]
          [:td.td-center [:i.lupicon-remove.primary]]
          ])]]]))

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
     (attachments-batch (assoc options
                               :files*  files*
                               :fields* fields*))


     (components/debug-atom files* "Files")
     (components/debug-atom fields* "Fields")]))
