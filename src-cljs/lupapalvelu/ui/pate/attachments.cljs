(ns lupapalvelu.ui.pate.attachments
  "Attachments related components and utilities for Pate."
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.attachment.components :as att]
            [lupapalvelu.ui.attachment.file-upload :as upload]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.pate.components :as pate-components]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [lupapalvelu.ui.rum-util :as rum-util]
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

(defn- common-file-link-hiccup [link-element content-type size extra]
  [:div.batch--filedata
   link-element
   [:div.batch--fileinfo
    (if (loc.hasTerm content-type)
      (path/loc content-type)
      content-type) " " (js/util.sizeString size) extra]])

(defn batch-file-link
  "File link with type and size information. For example:

  IMG_2253.JPG
  JPG-kuva 2.9 MB"
  [{:keys [filename size type file-id] :as file}  & extra]
  (common-file-link-hiccup
    (if file-id
      [:a.batch--filename {:href   (str "/api/raw/view-file?fileId=" file-id)
                           :target :_blank} filename]
      [:span.batch--filename filename])
    (or type (:contentType file))
    size
    extra))

(defn attachment-file-link
  "File link with type and size information for the latest attachment version."
  [{:keys [id latestVersion]}  & extra]
  (common-file-link-hiccup
    [:a.batch--filename {:href   (str "/api/raw/latest-attachment-version?attachment-id=" id)
                         :target :_blank} (:filename latestVersion)]
    (:contentType latestVersion)
    (:size latestVersion)
    extra))

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
      (let [filename    (keyword (:filename filedata))
            value       (or (:type (field-info fields* filedata))
                            (:value (util/find-by-key :value
                                                      (:default schema)
                                                      att-types)))
            set-type-fn (fn [type]
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
                             (map #(hash-map :text %)))
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

  (service/bind-attachments-batch
   @state/application-id
   (map (fn [{:keys [file-id filename]}]
          (let [filedata-fn (or (path/meta-value options :filedata)
                                assoc)]
            (filedata-fn options
                         ((keyword filename) @fields*)
                         :file-id file-id)))
        @files*)
   (fn [{:keys [done pending result] :as job}]
     (letfn [(finalize []
               (service/refresh-attachments)
               (reset! binding?* false))]
       (if job
         (do (swap! files* (fn [files]
                             (remove #(util/includes-as-kw? done
                                                            (:file-id %))
                                     files)))
             (when (empty? pending)
               (do (finalize)
                   (reset! fields* {}))))
         (do
           (finalize)
           (hub/send :indicator {:style :negative
                                 :message :attachment.bind-failed})))))))

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
       [:table.pate-batch-table
        [:thead
         [:tr
          [:th [:span (path/loc :attachment.file)]]
          [:th [:span.batch-required (path/loc :application.attachmentType)]]
          [:th [:span.batch-required (path/loc :application.attachmentContents)]]
          [:th.td-center (path/loc :remove)]]]
        [:tbody
         (for [{:keys [filename state] :as filedata} @files*]
           [:tr
            [:td.batch--file (batch-file-link filedata)]
            (case state
              :bad (td-error (:message filedata))
              :failed (td-error (path/loc :file.upload.failed))
              :progress (td-progress (:progress filedata))
              [
               [:td.batch--type
                {:key (common/unique-id "batch-type")}
                (type-selector options filedata)]
               [:td.batch--contents
                {:key (common/unique-id "batch-contents")}
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

(defn uploader-info [{:keys [user created]}]
  [:span.uploader-info (:firstName user) " " (:lastName user)
   [:br]
   (js/util.finnishDate created)])

(defn attachments-refresh-mixin []
  (rum-util/hubscribe "attachmentsService::changed"
                      {}
                      (fn [state]
                        (-> state
                            :rum/react-component
                            rum/request-render))))

(rum/defc attachments-list < rum/reactive
  (attachments-refresh-mixin)
  [{:keys [files*] :as options}]
  (when (-> files* rum/react empty?)
    (let [include?    (or (path/meta-value options :include?) identity)
          attachments (filter (partial include? options)
                              (service/attachments))]
      [:table.pate-attachments
       [:tbody
        (for [{:keys [type contents latestVersion can-delete?]
               :as   attachment} attachments]
          [:tr
           [:td (attachment-file-link attachment
                                      ". " (-> type kw-type type-loc)
                                      ": " contents)]
           [:td (uploader-info latestVersion)]
           (when can-delete?
             [:td.td--center [:i.lupicon-remove.primary
                              {:on-click #(att/delete-with-confirmation attachment)}]])])]])))

(rum/defc pate-attachments < rum/reactive
  "Displays and supports adding new attachments."
  [{:keys [schema path state] :as options}]
  (let [files*    (atom [])
        fields*   (atom {})
        binding?* (atom false)]
    [:div
     (attachments-list (assoc options :files* files*))
     (attachments-batch (assoc options
                              :files*  files*
                              :fields* fields*
                              :binding?* binding?*))
     (when (path/enabled? options)
       (att/upload-wrapper {:callback  (upload/file-monitors files*)
                            :dropzone  (:dropzone schema)
                            :multiple? (:multiple? schema)
                            :component (fn [{:keys [input input-id]}]
                                         [:div.add-file-div
                                          input
                                          (add-file-label binding?*
                                                          input-id)])}))]))

(defn- cmp-attachments
  "Attachments sorting order is:
   1. Type localization
   2. Filename"
  [{{a-group :type-group a-type :type-id} :type a-filename :filename}
   {{b-group :type-group b-type :type-id} :type b-filename :filename}]
  (let [type-cmp (compare (type-loc a-group a-type)
                          (type-loc b-group b-type))]
    (if (zero? type-cmp)
      (compare a-filename b-filename)
      type-cmp)))

(defn attachment-items
  "If grouped? is true (default false) the items are grouped by attachment
  type groups."
  ([grouped?]
   (let [items (->> (service/attachments)
                    (filter #(some-> % :latestVersion :fileId))
                    (map #(assoc (select-keys % [:type :id])
                                 :filename (get-in % [:latestVersion
                                                      :filename])
                                 :target-id (get-in % [:target :id]))))]
     (if grouped?
       (->> items
            (group-by #(-> % :type :type-group))
            (reduce-kv (fn [acc k v]
                         (assoc acc k (sort cmp-attachments v)))
                       {}))
       (sort cmp-attachments items))))
  ([] (attachment-items false)))

(rum/defcs select-application-attachments < rum/reactive
  (components/initial-value-mixin ::selected)
  (attachments-refresh-mixin)
  "Editor for selecting application attachments. The selection result
  is a set of attachment ids. Parameters [optional]:

  callback: Callback function that receives collection of attachment
  ids as parameter.

  [disabled?]: If true, the component is disabled.

  [target-id]: Attachments matching the target-id are always
  selected. The use case for this is the situation, where a verdict
  attachment has been previously added. Since selection does not
  modify attachments, we cannot allow discrepancy where a verdict
  attachment is marked non-verdict attachment."
  [{selected* ::selected} _ {:keys [callback disabled? target-id]}]
  (let [group-loc       #(type-loc (get-in % [:type :type-group] %)
                                   :_group_label)
        targeted?       #(= (:target-id %) target-id)
        selected?       #(or (targeted? %)
                             (-> selected* rum/react set (contains? (:id %))))
        att-items       (attachment-items true)
        group-selected? #(->> (get att-items %)
                              (remove targeted?)
                              (every? selected?))
        toggle          (fn [ids flag]
                          (swap! selected*
                                 (fn [selected]
                                   (let [result (if flag
                                                  (set/union (set selected)
                                                             (set ids))
                                                  (set/difference (set selected)
                                                                  (set ids)))]
                                     (callback result)
                                     result))))
        items           (reduce (fn [acc k]
                                  (concat acc (cons {:group k} (get att-items k))))
                                []
                                (sort #(compare (group-loc %1) (group-loc %2))
                                      (keys att-items)))]
    (if (seq items)
      [:div.pate-select-application-attachments
       (for [{:keys [group type id filename] :as item} items]
         [:div
          {:key (or id group)}
          (if group
            (components/toggle (group-selected? group)
                               {:text      (group-loc group)
                                :prefix    :pate-attachment-group
                                :callback  #(toggle (map :id (get att-items group)) %)
                                :disabled? disabled?})
            (components/toggle (selected? item)
                               {:text      (str (type-loc (:type-group type)
                                                          (:type-id type))
                                                ". " filename)
                                :prefix    :pate-attachment-check
                                :callback  #(toggle [id] %)
                                :disabled? (or disabled? (targeted? item))}))])]
      [:span (common/loc :pate.no-attachments)])))

(rum/defc pate-select-application-attachments < rum/reactive
  [{:keys [schema path state info] :as options} & [wrap-label?]]
  (pate-components/label-wrap-if-needed
   options
   {:component (select-application-attachments
                (path/state path state)
                {:callback  (partial path/meta-updated
                                     options)
                 :disabled? (path/disabled? options)
                 :target-id (path/value :id info)})
    :wrap-label? wrap-label?}))

(defn- attachments-table
  "Items are maps with type-string and amount keys."
  [items]
  [:div.tabby.pate-application-attachments
   (for [{:keys [type-string amount]} (sort-by :type-string items)]
       [:div.tabby__row
        {:key type-string}
        [:div.tabby__cell type-string]
        [:div.tabby__cell.amount amount]
        [:div.tabby__cell (common/loc :unit.kpl)]])])

(rum/defc pate-application-attachments < rum/reactive
  (attachments-refresh-mixin)
  [{:keys [path state info]}]
  (let [verdict-id (path/value :id info)
        marked     (set (path/react path state))]
    (->> (attachment-items)
         (filter (fn [{:keys [id target-id]}]
                   (or (contains? marked id)
                       (= target-id verdict-id))))
         (group-by #(type-loc (-> % :type :type-group)
                              (-> % :type :type-id)))
         (map (fn [[k v]]
                {:type-string k
                 :amount      (count v)}))
         attachments-table)))

(rum/defc pate-frozen-application-attachments
  "When a verdict is published its attachments list is frozen."
  [{:keys [path state]}]
  (->> (path/value path state)
       (map (fn [{:keys [type-group type-id amount]}]
          {:type-string (type-loc type-group type-id)
           :amount      amount}))
       attachments-table))

(rum/defc pate-attachments-view < rum/reactive
  [{:keys [info] :as options}]
  ((if (path/react :published info)
     pate-frozen-application-attachments
     pate-application-attachments) options))
