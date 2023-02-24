(ns lupapalvelu.ui.location.locations-operations
  "Location operations (Kohteet) application section."
  (:require [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.ui.common :refer [loc loc-html] :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.location.file-upload :as file-upload]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [sade.shared-util :as util]
            [sade.shared-strings :as ss]))

;; Utility functions

(defn ->download-url [file-id & {:keys [file-version-id]}]
  (str "/api/raw/linked-file-version?file-id=" file-id
       "&download=true"
       (when file-version-id (str "&file-version-id=" file-version-id))))

(defn format-fi-datetime
  "Returns a map with keys :year, :month, and :day from the given ISO 8601 date string."
  [date]
  (.format (js/moment date) "D.M.YYYY HH.mm"))

(defn format-keyed-map-as-list [separator field-keys obj]
  (->> obj
       ((apply juxt field-keys))
       (ss/join separator)))

(def format-version (partial format-keyed-map-as-list "." [:major :minor]))
(def format-user-name (partial format-keyed-map-as-list " " [:firstName :lastName]))

(defn get-model-type-options []
  (letfn [(localized-name [type] (loc (util/kw-path :operations-locations.model-types type)))
          (->option [type]
            {:value type
             :text  (localized-name type)})]
    (->> ["arkkitehtimalli" "rakennemalli" "talotekniikkamalli"]
         (map ->option))))

(defn can-edit-location? []
  (<sub [:auth/application? :set-operation-location]))

(defn can-upload-and-modify? []
  (<sub [:auth/application?
         :init-resumable-upload-for-application
         :link-resumable-upload-for-application
         :set-linked-application-file-metadata-field]))


;; Dialog UI

(defn download-prompt
  "When user clicks file link, warn user that model file is big"
  [file-id]
  [:div
   [:p (loc :operations-locations.download-dialog.text)]
   [:div.button-group
    [:div.flex.flex--right.flex--gap2
     [:button.ghost
      {:on-click #(>evt [:dialog/close])}
      (loc :button.cancel)]
     [:a.btn.primary {:href (->download-url file-id) :on-click #(>evt [:dialog/close])}
      (loc :operations-locations.load-dialog.donwload-button-label)]]]])


(defn download-version-modal
  "Show file versions and allow user to download a version"
  [file-id file-versions]
  [:div
   [:p (loc :operations-locations.download-version-dialog.text)]
   [:div.flex.flex--right.flex--gap2
    [:table
     [:thead
      [:tr [:th (loc :attachment.version)]
       [:th (loc :operations-locations.uploaded-at)]
       [:th (loc :operations-locations.uploaded-by)]
       [:th]]]
     [:tbody
      (for [file-version file-versions
            :let [{:keys [file-version-id uploaded-at uploaded-by version]} file-version]]
        ^{:key file-version-id}
        [:tr
         [:td (format-version version)]
         [:td (format-fi-datetime uploaded-at)]
         [:td (format-user-name uploaded-by)]
         [:td
          [:a.btn.ghost
           {:href (->download-url file-id :file-version-id file-version-id)}
           (loc :operations-locations.download-version-button-label)]]])]]]
   [:div.button-group.flex.flex--right
    [:button.primary {:on-click #(>evt [:dialog/close])} (loc :button.ok)]]])

(defn upload-prompt
  "When users click Add model (lisää malli), prompt user to choose the file and set model-type"
  [component-id]
  (let [pending-files (<sub [::file-upload/pending-files-for component-id])
        {:keys [metadata md5-progress]} (<sub [::file-upload/get-upload-metadata component-id])
        {:keys [in-progress?]} md5-progress
        {related-file-ok   :ok
         related-file-name :name} (-> pending-files first (select-keys [:ok :name]))
        selected-model-type (:model-type metadata)
        model-type-id (str component-id "-model-type")
        can-add (and related-file-ok
                     (not (nil? selected-model-type)))]
    [:div {:style {:height "100%"}}
     (loc-html :p :operations-locations.load-dialog.instruction)
     [:div.boxgrid
      [:input.boxitem.box--3.gap--r1 {:type     :text
                                      :disabled true
                                      :value    (or related-file-name "")}]
      (if in-progress?
        [file-upload/file-preparation-progress-indicator component-id]
        [:button.ghost.boxitem-1
         {:on-click #(>evt [::file-upload/open-file-picker component-id %])}
         (loc :attachment.chooseFile)])]
     [:div.flex.flex--column.pad--t1
      [:label {:for model-type-id} (loc :operations-locations.load-dialog.model-type-label)]
      [components/dropdown
       selected-model-type
       {:id       model-type-id
        :items    (get-model-type-options)
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

;; Events

(rf/reg-event-fx
  ::open-download-file-dialog
  (fn [_ [_ file-id]]
    {:dialog/show {:title            (loc :operations-locations.download-dialog.title)
                   :size             :medium
                   :type             :react
                   :dialog-component [download-prompt file-id]
                   :minContentHeight "100%"}}))

(rf/reg-event-fx
  ::update-file-metadata
  (fn [{db :db} [_ {:keys [target-entity]} field value]]
    (let [{:keys [application-id]} target-entity]
      {:db             (update-in db [::linked-file-metadatas]
                                  (fn [all-files]
                                    (mapv
                                      #(if (= (:target-entity %) target-entity)
                                         (assoc-in % [:metadata field] value)
                                         %) all-files)))
       :action/command {:name    :set-linked-application-file-metadata-field
                        :params  {:id                 application-id
                                  :linked-file-target target-entity
                                  :field              field
                                  :value              value}
                        :success [::update-file-metadata.success]}})))

(rf/reg-event-fx
  ::update-file-metadata.success
  (fn [_ _]))

(rf/reg-event-fx
  ::open-download-file-version-dialog
  (fn [{db :db} [_ file-id file-versions]]
    {:dialog/show {:title            (loc :operations-locations.download-version-dialog.title)
                   :size             :large
                   :type             :react
                   :dialog-component [download-version-modal file-id file-versions]
                   :minContentHeight "100%"}}))

(rf/reg-event-fx
  ::add-new-file-dialog
  (fn [{db :db} [_ component-id]]
    {:fx [[:dispatch [::file-upload/upload-event component-id :upload-triggered]]
          [:dialog/close true]]}))

(rf/reg-event-fx
  ::cancel-add-new-file-dialog
  (fn [{db :db} [_ component-id]]
    {:fx [[:dispatch [::file-upload/upload-event component-id :reset]]
          [:dialog/close true]]}))

(rf/reg-event-fx
  ::open-load-model-dialog
  (fn [_ [_ component-id]]
    {:dialog/show {:title            (loc :operations-locations.load-dialog.title)
                   :size             :large
                   :type             :react
                   :dialog-component [upload-prompt component-id]
                   :callback         [::file-upload/upload-event component-id :upload-triggered]
                   :cancel-callback  (partial >evt [::cancel-add-new-file-dialog component-id])
                   :minContentHeight "100%"}}))

(rf/reg-event-fx
  ::fetch-locations-operations
  (fn [{db :db} _]
    (let [app-id (::current-application-id db)]
      {:db           (assoc db ::locations-operations :loading)
       :action/query {:name    :location-operations
                      :params  {:id app-id}
                      :success [::fetch-locations-operations.success]}})))

(rf/reg-event-db
  ::fetch-locations-operations.success
  (fn [db [_ data]]
    (assoc db ::locations-operations data)))

(rf/reg-event-fx
  ::location-changed
  (fn [{db :db} [_ operation-id x y]]
    (let [application-id (::current-application-id db)]
      {:dialog/close   true
       :action/command {:name    :set-operation-location
                        :params  {:id           application-id
                                  :operation-id operation-id
                                  :x            x
                                  :y            y}
                        :success [::fetch-locations-operations]}})))

(rf/reg-event-fx
  ::link-resumable-upload
  (fn [{db :db} [_ {:keys [operation-id file-version-id file-id name file-metadata size content-type] :as event-data} component-id]]
    (let [application-id (::current-application-id db)]
      {:action/command {:name    :link-resumable-upload-for-application
                        :params  {:id              application-id
                                  :file-version-id file-version-id
                                  :file-metadata   file-metadata
                                  :version-metadata
                                  {:name         name
                                   :size         size
                                   :content-type content-type}
                                  :linked-file-target
                                  {:target-type    "operations-location-file"
                                   :application-id application-id
                                   :operation-id   operation-id
                                   :file-id        file-id}}
                        :success [::link-resumable-upload.success component-id]}})))

(rf/reg-event-fx
  ::link-resumable-upload.success
  (fn [_ [_ _ component-id]]
    {:fx [[:dispatch [::fetch-linked-file-metadatas]]
          [:dispatch [::file-upload/upload-event component-id :reset]]]}))

(rf/reg-event-fx
  ::fetch-linked-file-metadatas
  (fn [{db :db} [_]]
    (let [application-id (get db ::current-application-id)]
      {:action/query {:name    :fetch-linked-file-metadatas-for-application
                      :params  {:id          application-id
                                :target-type "operations-location-file"}
                      :success [::fetch-linked-file-metadatas.success]}})))

(rf/reg-event-db
  ::fetch-linked-file-metadatas.success
  (fn [db [_ {:keys [data]}]]
    (->> data
         (assoc db ::linked-file-metadatas))))

(rf/reg-event-fx
  ::open-change-location-dialog
  (fn [{db :db} [_ op]]
    {:dialog/show {:title            (loc :location.edit)
                   :size             :large
                   :type             :location-editor
                   :callback         #(>evt [::location-changed (:id op) %1 %2])
                   :cancel-callback  #(>evt [:dialog/close])
                   :component-params {:x      (get-in op [:location 0])
                                      :y      (get-in op [:location 1])
                                      :center (::current-application-location db)}}}))

(rf/reg-sub
  ::get-operations
  (fn [{res ::locations-operations} _]
    (if (:ok res)
      (:operations res)
      res)))

(rf/reg-sub
  ::get-operations-files
  (fn [{files ::linked-file-metadatas} [_ operation-id]]
    (when files
      (->> files
           (filter #(= operation-id (get-in % [:target-entity :operation-id])))
           (sort-by (comp :uploaded-at first :versions))))))

(defn field [loc-key value]
  [:div.field.boxitem.box--2.pad--v1.boxitem--bold-labels
   [:label (loc loc-key)]
   [:div value]])

(defn model-type-dropbox-for-file
  [{:keys [target-entity metadata] :as file}]
  [components/dropdown
   (:model-type metadata)
   {:id       (str (get target-entity :file-id) "-model-type")
    :items    (get-model-type-options)
    :choose?  false
    :sort-by  :text
    :test-id  :value
    :callback #(>evt [::update-file-metadata file
                      :model-type %])}])

(defn file-row
  [application-id operation-id file]
  (let [{:keys [file-id]} (:target-entity file)
        {:keys [name model-type]} (:metadata file)
        can-upload-and-modify (can-upload-and-modify?)
        file-versions (:versions file)
        last-version (last (sort-by (comp (juxt :major :minor) :version) file-versions))
        last-version-number (format-keyed-map-as-list "." [:major :minor] (:version last-version))

        file-upload-component-id (file-upload/generate-component-id operation-id :file-id file-id)
        is-ready? (<sub [::file-upload/is-ready? file-upload-component-id])]
    [:tr
     [:td {:data-label (loc :operations-locations.filename)}
      [:a.download-model {:on-click #(>evt [::open-download-file-dialog file-id])} name]]
     [:td {:data-label (loc :operations-locations.type)}
      [model-type-dropbox-for-file file :with-not-selected-value? (nil? model-type)]]
     [:td {:data-label (loc :attachment.version)}
      [:div.flex--align-center
       [:a.version-link {:on-click #(>evt [::open-download-file-version-dialog file-id file-versions])}
        last-version-number]
       [file-upload/file-picker
        {:id                  file-upload-component-id
         :file-id             file-id
         :auto-upload         true
         :label-text          (loc :operations-locations.upload-button-label)
         :accept-types        ["*.ifc"]
         :on-uploaded-file-fn (fn [{:keys [file-id file-version-id name content-type size] :as a} file-metadata component-id]
                                (>evt [::link-resumable-upload
                                       {:file-id         file-id
                                        :file-metadata   file-metadata
                                        :file-version-id file-version-id
                                        :application-id  application-id
                                        :operation-id    operation-id
                                        :content-type    content-type
                                        :size            size
                                        :name            name}
                                       component-id]))}]
       (if is-ready?
         [components/icon-button
          {:class    "tertiary.caps.flex--align-center"
           :text-loc :operations-locations.upload-version-button-label
           :icon     :lupicon-circle-plus
           :right?   true
           :disabled (not can-upload-and-modify)
           :on-click #(>evt [::file-upload/open-file-picker file-upload-component-id %])}]
         [file-upload/file-loading-indicator file-upload-component-id])]]
     [:td]]))

(defn uploaded-models-table [{:keys [application-id operation-id]}]
  (let [files (<sub [::get-operations-files operation-id])]
    [:table.reset.model-table
     [:thead [:tr
              [:th.boxitem.box--3 (loc :operations-locations.filename)]
              [:th (loc :operations-locations.type)]
              [:th (loc :attachment.version)]
              [:th]]]
     [:tbody
      (if (seq files)
        (for [file files
              :let [file-id (get-in file [:target-entity :file-id])]]
          ^{:key file-id}
          [file-row application-id operation-id file])
        ^{:key "not-rows-file-id"}
        [:tr [:td {:col-span 4} (loc :operations-locations.no-files)]])]]))

(defn operation-item
  [{:keys [id operation tag description location building-id] :as op}]
  (let [desc (ss/join ": " (keep identity [tag description]))
        [x y] location
        coords (when (and x y) (ss/join ", " location))
        application-id (<sub [:value/get ::current-application-id])
        can-edit-location (can-edit-location?)
        can-upload-and-modify (can-edit-location?)
        file-upload-component-id (file-upload/generate-component-id id)
        is-ready? (<sub [::file-upload/is-ready? file-upload-component-id])]
    [:div.bg--blue.pad--2.rounded--1.gap--b2
     [:section.basic-info.boxgrid
      [:div.fields.boxgrid.boxitem.box--4
       [field :location.operation (loc (str "operations." operation))]
       [field :location.tag desc]
       [field :location.building-id building-id]
       (if coords
         [field :location.location coords]
         [field :location.location [:span.location-missing (loc :location.missing)]])]
      [:div.actions.boxitem {:style {:display         :flex
                                     :justify-content :space-between
                                     :flex-direction  :column}}
       [:button.btn-primary
        {:disabled (not can-edit-location)
         :on-click #(>evt [::open-change-location-dialog op])}
        (loc :location.edit)]
       [file-upload/file-picker
        {:id                  file-upload-component-id
         :label-text          (loc :operations-locations.upload-version-button-label)
         :accept-types        [".ifc"]
         :on-uploaded-file-fn (fn [{:keys [file-id file-version-id name content-type size] :as all} file-metadata component-id]
                                (>evt [::link-resumable-upload
                                       {:file-id         file-id
                                        :file-metadata   file-metadata
                                        :file-version-id file-version-id
                                        :application-id  application-id
                                        :operation-id    id
                                        :content-type    content-type
                                        :size            size
                                        :name            name}
                                       component-id]
                                      ))}]
       (if is-ready?
         [components/icon-button
          {:class    "tertiary.caps.flex--align-center"
           :text-loc :operations-locations.upload-button-label
           :icon     :lupicon-circle-plus
           :right?   true
           :disabled (not can-upload-and-modify)
           :on-click #(>evt [::open-load-model-dialog file-upload-component-id])}]
         [file-upload/file-loading-indicator file-upload-component-id])]]
     [:hr]
     [:div.boxitem.box--5 [uploaded-models-table {:application-id application-id
                                                  :operation-id   id}]]]))

(defn operations-list [operations]
  [:div
   (for [op operations]
     ^{:key (:id op)}
     [operation-item op])])

(defn main-view []
  (r/with-let [_ (>evt [::fetch-locations-operations])
               _ (>evt [::fetch-linked-file-metadatas])]
              (let [operations (<sub [::get-operations])]
                (if (= operations :loading)
                  [:div (loc "loading")]
                  [operations-list operations]))))

(defn mount-component [dom-id]
  (rd/render [main-view]
             (.getElementById js/document dom-id)))

(defn ^:export start [dom-id params]
  (let [{:keys [applicationId applicationLocation]} (common/->cljs params)]
    (>evt [:value/set ::current-application-id applicationId])
    (>evt [:value/set ::current-application-location applicationLocation])
    (mount-component dom-id)))
