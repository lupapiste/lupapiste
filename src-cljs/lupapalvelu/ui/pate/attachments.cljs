(ns lupapalvelu.ui.pate.attachments
  "Attachments related components and utilities for Pate."
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.attachment.components :as att]
            [lupapalvelu.ui.attachment.file-upload :as upload]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.common.hub :as hub]
            [lupapalvelu.ui.pate.components :as pate-components]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [lupapalvelu.ui.rum-util :as rum-util]
            [rum.core :as rum]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))

(defn- common-file-link-hiccup [link-element content-type size extra]
  [:div.batch--filedata
   link-element
   [:div.batch--fileinfo
    (if (js/loc.hasTerm content-type)
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

(defn- access-attachment-types []
  (->> (js/lupapisteApp.services.attachmentsService.attachmentTypes)
       (map #(js->clj % :keywordize-keys true))))

(rum/defc type-selector
  [{:keys [on-select value type-schema binding? test-id]} filedata]
  (let [[types set-types!] (rum/use-state (access-attachment-types))
        [value set-value!] (rum/use-state value)]
    (rum/use-effect!
      (fn []
        (if-let [from-service (seq (access-attachment-types))]
          (do
            (set-types! from-service)
            (when-not value
              ;; select default from the types, call on select
              (if-let [default (->> from-service
                                    (map kw-type)
                                    (util/find-first (partial = (:default type-schema))))]
                (do
                  (set-value! default)
                  (on-select default))
                (js/console.error "No default attachment type found from service for schema default" (:default type-schema)))))
          ;; FIXME some kind of recur in case of no results (yet) from service (service uses autoFetch extender)
          ;; though I guess it's usually rare case that types are not initialized yet (eg when you refresh verdict page)
          (js/console.warn "No attachment types from attachment service, type-selector will be empty")))
      [])
    (if-let [att-types (some->> types
                                (filter (fn [{:keys [type-group]}]
                                          (if-let [regex (:type-group type-schema)]
                                            (re-matches regex type-group)
                                            true)))
                                (map (fn [{:keys [type-group type-id] :as type}]
                                       {:group (type-loc type-group :_group_label)
                                        :text  (type-loc type-group type-id)
                                        :value (kw-type type)}))
                                (seq))]
      (components/autocomplete value
                               {:items     att-types
                                :test-id   test-id
                                :callback  (fn [v]
                                             (set-value! v)
                                             (on-select v))
                                :disabled? (or binding? (not= (:state filedata) :success))})
      [:div
       [:i.wait.spin.lupicon-refresh]])))

(rum/defc contents-editor
  [{:keys [on-change binding? test-id type contents]} filedata]
  (let [types->content-selections  (fn [types]
                                    (some->> (seq types)
                                             (util/find-first #(= type (kw-type %)))
                                             :metadata
                                             :contents
                                             (map #(path/loc :attachments.contents %))
                                             sort
                                             (map #(hash-map :text %))))
        [selections set-contents!] (rum/use-state (->> (access-attachment-types)
                                                       (types->content-selections)))]
    (rum/use-effect!
      (fn []
        (if-let [contents-texts (types->content-selections (access-attachment-types))]
          (do
            (set-contents! contents-texts)
            (when-not contents
              ;; select default contents text from the types, call on select
              (let [default (if (<= (count contents-texts) 1)
                              (or (-> contents-texts first :text)
                                  (type-loc type))
                              "")]
                (on-change default))))
          ;; FIXME some kind of recur in case of no results (yet) from service (service uses autoFetch extender)
          ;; though I guess it's usually rare case that types are not initialized yet (eg when you refresh verdict page)
          (when-let [default-type (and (s/blank? contents) type)]
            (on-change (type-loc default-type)))))
      [type])
    (when (and type (seq (access-attachment-types)))
      (components/combobox (or contents "")
                           {:items      selections
                            :callback   on-change
                            :aria-label (common/loc :attachment.th-content)
                            :required?  true
                            :test-id    test-id
                            :disabled   (or binding? (not= (:state filedata) :success))}))))

(defn- td-error [index msg]
  [:td.batch--error (common/add-test-id { :colSpan 2}
                                        :batch index :error)
   msg])

(defn- td-progress [index percentage]
  [:td.batch--progress (common/add-test-id {:colSpan 2}
                                           :batch index :progress)
   [:div [:span {:style {:width (str percentage "%")}} " "]]])

(defn- remove-file [idx {:keys [files* fields*]} {:keys [filename file-id]}]
  (when file-id
    (service/delete-file file-id))
  (swap! files* #(util/drop-nth idx %))
  (swap! fields* dissoc (keyword filename)))

(defn- bind-batch [{:keys [files* fields* binding?*] :as options}]
  (service/bind-attachments
    @state/application-id
    (->> @files*
         (map (fn [{:keys [file-id filename]}]
                (let [filedata-fn (or (path/meta-value options :filedata)
                                      assoc)]
                  (filedata-fn options
                               ((keyword filename) @fields*)
                               :file-id file-id))))
         (service/canonize-filedatas))
    {:waiting? binding?*
     :success  (partial service/batch-job
                        (fn [{:keys [done pending] :as job}]
                          (letfn [(finalize []
                                    (service/refresh-attachments)
                                    (reset! binding?* false)
                                    (reset! fields* {})
                                    (reset! files* []))]
                            (if job
                              (if (empty? pending)
                                (finalize)
                                (swap! files* (fn [files]
                                                (->> files
                                                     (remove #(util/includes-as-kw? done (:file-id %)))
                                                     vec))))
                              (do
                                (finalize)
                                (hub/send :indicator {:style   :negative
                                                      :message :attachment.bind-failed}))))))}
    (:draft? options)))

(rum/defc batch-buttons < rum/reactive
  [{:keys [files* fields* binding?*] :as options}]
  (let [binding? (rum/react binding?*)
        files    (rum/react files*)
        fields   (rum/react fields*)]
    [:tfoot.batch--buttons
     [:tr
      [:td {:colSpan 4}
       [:button.primary.outline
        (common/add-test-id {:on-click #(do (doseq [[idx filedata] (map-indexed vector files)]
                                              (remove-file idx options filedata))
                                            (reset! fields* {}))
                             :disabled binding?}
                            :batch-cancel)
        (path/loc :cancel)]
       (components/icon-button {:disabled? (or binding?
                                               (empty? fields)
                                               (some #(or (-> % :contents ss/blank?)
                                                          (-> % :type ss/blank?))
                                                     (vals fields))
                                               (not-every? #(-> % :state #{:bad :success :failed})
                                                           files))
                                :on-click  #(bind-batch options)
                                :test-id   :batch-ready
                                :class     :positive
                                :icon      :lupicon-check
                                :wait?     binding?*
                                :text-loc  :attachment.batch-ready})]]]))

(rum/defc attachments-batch < rum/reactive
  "Metadata editor for file upload. The name is a hat-tip to the
  AttachmentBatchModel."
  [{:keys [files* binding?* bind? schema fields*] :as options}]
  (when-let [files (-> files* rum/react seq)]
    (let [bind?        (boolean (or (nil? bind?) bind?))
          binding?     (if bind? false (rum/react binding?*))
          set-type     (fn [filedata type]
                         (set-field fields* filedata :type type)
                         (set-field fields* filedata :contents nil))
          set-contents (fn [filedata contents]
                         (set-field fields* filedata :contents contents))]
      [:div
       [:table.pate-batch-table
        [:thead
         [:tr
          [:th [:span (path/loc :attachment.file)]]
          [:th [:span.batch-required (path/loc :application.attachmentType)]]
          [:th [:span.batch-required (path/loc :application.attachmentContents)]]
          [:th.td-center (path/loc :remove)]]]
        [:tbody
         (for [{:keys [idx state] :as filedata} (map-indexed #(assoc %2 :idx %1) files)
               :let                             [file-info (field-info fields* filedata)]]
           [:tr
            {:key (str "batch-row-" idx)}
            [:td.batch--file (common/add-test-id {} :batch idx :file-link)
             (batch-file-link filedata)]
            (case state
              :bad      (td-error idx (:message filedata))
              :failed   (td-error idx (path/loc :file.upload.failed))
              :progress (td-progress idx (:progress filedata))
              [:<>
               [:td.batch--type
                {:key (common/test-id :batch idx :type)}
                (type-selector
                  {:on-select   (partial set-type filedata)
                   :type-schema schema
                   :value       (:type file-info)
                   :binding?    binding?
                   :test-id     (common/test-id :batch idx :type)}
                  filedata)]
               [:td.batch--contents
                {:key (common/test-id :batch idx :contents)}
                (contents-editor
                  {:on-change (partial set-contents filedata)
                   :type      (:type file-info)
                   :contents  (:contents file-info)
                   :binding?  binding?
                   :test-id   (common/test-id :batch idx :contents)}
                  filedata)]])
            [:td.td-center
             (when-not binding?
               (components/icon-button {:class      :secondary.no-border
                                        :icon-only? true
                                        :on-click   #(remove-file idx options filedata)
                                        :icon       :lupicon-remove
                                        :test-id    [:batch idx :remove]
                                        :text-loc   :remove}))]])]
        (when bind?
          (batch-buttons options))]])))

(rum/defc add-file-label < rum/static
  "Add file label -button. On change of the binding? property, sends fileuploadService::toggle-enabled events."
  [binding? input-id & [test-id]]
  (rum/use-effect!
    #(hub/send "fileuploadService::toggle-enabled"
               {:input   input-id
                :enabled (not binding?)})
    [binding?])
  [:label.primary
   {:for          input-id
    :data-test-id (str (name (or test-id :pate-upload)) "-label")
    :class        (common/css-flags :disabled binding?)}
   [:i.lupicon-circle-plus {:aria-hidden true}]
   [:span (path/loc :attachment.addFile)]])

(defn uploader-info [{:keys [user created]}]
  [:span.uploader-info (:firstName user) " " (:lastName user)
   [:br]
   (js/util.finnishDate created)])

(defn attachments-refresh-mixin
  "Requests component re-render when attachmentService::changed event is received"
  []
  (rum-util/hubscribe "attachmentsService::changed"
                      {}
                      (fn [state]
                        (-> state
                            :rum/react-component
                            rum/request-render))))

(rum/defc attachments-list
  [options]
  (let [include?                       (or (path/meta-value options :include?) identity)
        get-attachments                #(filter (partial include? options) (service/attachments))
        [attachments set-attachments!] (rum/use-state get-attachments)]
    (rum/use-effect! (fn []
                       (let [hub-id (hub/subscribe "attachmentsService::changed"
                                                   #(set-attachments! (get-attachments)))]
                         #(hub/unsubscribe hub-id)))
                     [])
    [:table.pate-attachments
     [:tbody
      (for [{:keys [type contents latestVersion can-delete?]
             :as   attachment} attachments]
        [:tr
         {:key (:id attachment)}
         [:td (attachment-file-link attachment
                                    ". " (-> type kw-type type-loc)
                                    ": " contents)]
         [:td (uploader-info latestVersion)]
         (when can-delete?
           [:td.td--center
            (components/icon-button {:on-click   #(att/delete-with-confirmation attachment)
                                     :icon       :lupicon-remove
                                     :text-loc   :attachment.delete
                                     :icon-only? true
                                     :class      :secondary.no-border})])])]]))

(rum/defcs pate-attachments < rum/reactive
                             (rum/local [] ::files)
                             (rum/local {} ::fields)
                             (rum/local false ::binding?)
  "Displays and supports adding new attachments."
  [{files* ::files fields* ::fields binding?* ::binding?} {:keys [schema] :as options}]
  (let [test-id (pate-components/test-id options)]
    [:div
     (when (empty? @files*)
       (attachments-list options))
     (attachments-batch (assoc options
                              :files*  files*
                              :fields* fields*
                              :binding?* binding?*
                              :draft? (:draft? schema)))
     (when (path/enabled? options)
       (att/upload-wrapper {:callback  (upload/file-monitors files*)
                            :dropzone  (:dropzone schema)
                            :multiple? (:multiple? schema)
                            :test-id   test-id
                            :component (fn [{:keys [input input-id]}]
                                         [:div.add-file-div
                                          input
                                          (add-file-label @binding?*
                                                          input-id
                                                          test-id)])}))]))





(rum/defc batch-upload-files < rum/reactive
  "Adding attachments without Pate dependencies.
  - files*  should be vector atom where the upload file data will be updated
  - fields* should be map atom where information from batch table is updated"
  [files* fields* {:keys [enabled? dropzone multiple?] :as options}]
  (let [binding?* (atom false)]
    [:div
     (attachments-batch (assoc options
                          :files* files*
                          :fields* fields*
                          :binding?* binding?*))
     (when enabled?
       (att/upload-wrapper {:callback  (upload/file-monitors files*)
                            :dropzone  dropzone
                            :multiple? multiple?
                            :test-id   :pate-upload
                            :component (fn [{:keys [input input-id]}]
                                         [:div.add-file-div
                                          input
                                          (add-file-label (rum/react binding?*)
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

(rum/defc select-application-attachments < rum/reactive
  (attachments-refresh-mixin)
  "Editor for selecting application attachments. The selection result
  is a set of attachment ids. Parameters [optional]:

  selected-cursor* - cursor or atom with selected IDs (two-way binding)

  callback: Callback function that receives collection of attachment
  ids as parameter.

  [disabled?]: If true, the component is disabled.

  [target-id]: Attachments matching the target-id are always
  selected. The use case for this is the situation, where a verdict
  attachment has been previously added. Since selection does not
  modify attachments, we cannot allow discrepancy where a verdict
  attachment is marked non-verdict attachment."
  [selected-cursor* {:keys [callback disabled? target-id]}]
  (let [group-loc       #(type-loc (get-in % [:type :type-group] %)
                                   :_group_label)
        targeted?       #(= (:target-id %) target-id)
        selected?       #(or (targeted? %)
                             (-> selected-cursor* rum/react set (contains? (:id %))))
        att-items       (attachment-items true)
        group-selected? #(->> (get att-items %)
                              (remove targeted?)
                              (every? selected?))
        toggle          (fn [ids flag]
                          (swap! selected-cursor*
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
                               {:id        group
                                :text      (group-loc group)
                                :prefix    :pate-attachment-group
                                :callback  #(toggle (map :id (get att-items group)) %)
                                :disabled? disabled?})
            (components/toggle (selected? item)
                               {:id        id
                                :text      (str (type-loc (:type-group type)
                                                          (:type-id type))
                                                ". " filename)
                                :prefix    :pate-attachment-check
                                :callback  #(toggle [id] %)
                                :disabled? (or disabled? (targeted? item))}))])]
      [:span (common/loc :pate.no-attachments)])))

(rum/defc pate-select-application-attachments < rum/reactive
  [{:keys [path state info] :as options} & [wrap-label?]]
  (pate-components/with-label
    (assoc options :wrap-label? wrap-label?)
    (select-application-attachments
     (path/state path state)
     {:callback  (partial path/meta-updated
                          options)
      :disabled? (path/disabled? options)
      :target-id (path/value :id info)})))

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

(rum/defc attachments-view < rum/static
  "Static view of attachments information with view links. Not
  specific to Pate."
  [attachments]
  [:table.pate-attachments
   [:tbody
    (for [{:keys [id type contents latestVersion idx] :as attachment} (map-indexed #(assoc %2 :idx %1) attachments)]
      [:tr {:key id}
       [:td (common/add-test-id {} :file-link idx)
        (attachment-file-link attachment
                              ". " (-> type kw-type type-loc)
                              ": " contents)]
       [:td (common/add-test-id {} :info idx)
        (uploader-info latestVersion)]])]])
