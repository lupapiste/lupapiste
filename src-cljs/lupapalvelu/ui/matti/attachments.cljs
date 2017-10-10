(ns lupapalvelu.ui.matti.attachments
  "Attachments related components and utilities for Matti."
  (:require [clojure.string :as s]
            [lupapalvelu.ui.attachment.components :as att]
            [lupapalvelu.ui.attachment.file-upload :as upload]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.service :as service]
            [lupapalvelu.ui.matti.state :as state]
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
  [:div.batch--filedata
   (if file-id
     [:a.batch--filename {:href   (str "/api/raw/view-file?fileId=" file-id)
          :target :_blank} filename]
     [:span.batch--filename filename])
   [:div.batch--fileinfo (if (loc.hasTerm type)
                     (path/loc type)
                     type) " " (js/util.sizeString size)]])

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
  [{types* ::types} {:keys [schema files* fields* binding?*]
                     :as   options} filedata]
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
      (let [filename       (keyword (:filename filedata))
            {value :value} (util/find-by-key :value
                                             (:default schema)
                                             att-types)
            set-type-fn    (fn [type]
                             (set-field fields* filedata :type type)
                             (set-field fields* filedata :contents nil))]
        (when (and value
                   (-> (field-info fields* filedata) :type nil?))
          (set-field fields* filedata :type value))
        (components/autocomplete value
                                 {:items     att-types
                                  :callback  set-type-fn
                                  :disabled? (or (rum/react binding?*)
                                                 (not= (:state filedata)
                                                       :success))})))))

(rum/defcs contents-editor < rum/reactive
  (rum/local [] ::types)
  [{types*  ::types} {:keys [fields* binding?*]} {filename :filename
                                                  :as filedata}]
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
                              :disabled? (or (rum/react binding?*)
                                             (not= (:state filedata)
                                                   :success))})))))

(defn- td-error [msg]
  [:td.batch--error {:colSpan 2} msg])

(defn- td-progress [percentage]
  [:td.batch--progress {:colSpan 2}
   [:div [:span {:style {:width (str percentage "%")}} " "]]])

(defn- remove-file [{:keys [files* fields*]} {:keys [filename file-id]}]
  (when file-id
    (service/delete-file file-id))
  (swap! files*
         (partial remove #(util/=as-kw filename (:filename %))))
  (swap! fields*
         dissoc (keyword filename)))

(defn- bind-batch [{:keys [files* fields* binding?*] :as options}]
  (reset! binding?* true)
  (swap! files* (fn [files]
                  (filter #(= (:state %) :success) files)))

  (service/bind-attachments-batch @state/application-id
                                  (map (fn [{:keys [file-id filename]}]
                                         (let [filedata-fn (or (path/meta-value options :filedata)
                                                               assoc)]
                                           (filedata-fn options
                                                        ((keyword filename) @fields*)
                                                        :file-id file-id)))
                                       @files*)
                                  (fn [{:keys [done pending result] :as job}]
                                    (if job
                                      (do (swap! files* (fn [files]
                                                          (remove #(util/includes-as-kw? done
                                                                                         (:file-id %))
                                                                  files)))
                                          (when (empty? pending)
                                            (do (reset! binding?* false)
                                                (reset! fields* {}))))
                                      (do
                                        (reset! binding?* false)
                                        (hub/send :indicator {:style :negative
                                                              :message :attachment.bind-failed}))))))

(rum/defc batch-buttons < rum/reactive
  [{:keys [schema files* fields* binding?*] :as options}]
  (let [binding? (rum/react binding?*)]
    [:tfoot.batch--buttons
     [:tr
      [:td {:colSpan 4}
       [:button.primary.outline
        {:on-click #(do (doseq [filedata @files*]
                          (remove-file options filedata))
                        (reset! fields* {}))
         :disabled binding?}
        (path/loc :cancel)]
       [:button.positive
        {:disabled (or binding?
                       (some #(-> % :contents s/blank?)
                             (vals (rum/react fields*)))
                       (not-every? #(-> % :state #{:bad :success :failed})
                                   (rum/react files*)))
         :on-click #(bind-batch options)}
        (if binding?
          [:i.lupicon-refresh.icon-spin]
          [:i.lupicon-check])
        [:span (path/loc :attachment.batch-ready)]]]]]))

(rum/defc attachments-batch < rum/reactive
  "Metadata editor for file upload. The name is a hat-tip to the
  AttachmentBatchModel."
  [{:keys [schema files* fields* binding?*] :as options}]
  (when (-> files* rum/react seq)
    (let [binding? (rum/react binding?*)]
      [:div
       [:table.attachment-batch-table
        [:thead
         [:tr
          [:th [:span (path/loc :attachment.file)]]
          [:th [:span.batch-required (path/loc :application.attachmentType)]]
          [:th [:span.batch-required (path/loc :application.attachmentContents)]]
          [:th.td-center (path/loc :remove)]]]
        [:tbody
         (for [{:keys [filename state] :as filedata} @files*]
           [:tr
            [:td.batch--file (fileinfo-link filedata)]
            (case state
              :bad (td-error (:message filedata))
              :failed (td-error (path/loc :file.upload.failed))
              :progress (td-progress (:progress filedata))
              [
               [:td.batch--type
                {:key (path/unique-id "batch-type")}
                (type-selector options filedata)]
               [:td.batch--contents
                {:key (path/unique-id "batch-contents")}
                (contents-editor options filedata)]])
            [:td.td-center
             (when-not binding?
               [:i.lupicon-remove.primary
                {:on-click #(remove-file options filedata)}])]])]
        (batch-buttons options)]])))

(rum/defc add-file-label < rum/reactive
  "Add file label button as a separate component for binding?* atom's
  benefit."
  [binding?* input-id]
  (let [binding? (rum/react binding?*)]
    (hub/send "fileuploadService::toggle-enabled"
              {:input   input-id
               :enabled (not binding?)})
    [:label.btn.positive.batch--add-file
     {:for   input-id
      :class (common/css-flags :disabled binding?)}
     [:i.lupicon-circle-plus]
     [:span (path/loc :attachment.addFile)]]))



(rum/defc matti-attachments
  "Displays and supports adding new attachments. This cannot be
  reactive since we want the input-id to remain somewhat constant."
  [{:keys [schema path state] :as options}]
  (let [files*    (atom [])
        fields*   (atom {})
        binding?* (atom false)]
    [:div
     (when path/enabled?
       (att/upload-wrapper {:callback  (upload/file-monitors files*)
                            :dropzone  (:dropzone schema)
                            :multiple? (:multiple? schema)
                            :component (fn [{:keys [input input-id]}]
                                         [:div
                                          input
                                          (add-file-label binding?*
                                                          input-id)])}))
     (attachments-batch (assoc options
                              :files*  files*
                              :fields* fields*
                              :binding?* binding?*))


    (components/debug-atom files* "Files")
    (components/debug-atom fields* "Fields")]))
