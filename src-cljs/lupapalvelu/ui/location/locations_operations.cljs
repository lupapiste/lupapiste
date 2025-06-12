(ns lupapalvelu.ui.location.locations-operations
  "Location operations (Tietomallit) application section.
  The weird name is due to legacy reasons but the page still contains BIM data models grouped by operation."
  (:require [lupapalvelu.bim.schema :as bim-schema]
            [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.router-api :as router]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.location.assignments :as bim-assignments]
            [lupapalvelu.ui.location.file-upload :as file-upload]
            [lupapalvelu.ui.location.model-upload :as model-upload]
            [lupapalvelu.ui.location.model-utils :as model-utils]
            [lupapalvelu.ui.location.submit-data-source-selector :as data-source-selector]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

;; Dialog UI

(defn download-prompt
  "When user clicks file link, warn user that model file is big"
  [file-id & [file-version-id]]
  [:div
   [:p (loc :operations-locations.download-dialog.text)]
   [:div.button-group
    [:div.flex.flex--right.flex--gap2
     [:button.ghost
      {:on-click #(>evt [:dialog/close])}
      (loc :button.cancel)]
     [:a.btn.primary {:href     (model-utils/->download-url file-id :file-version-id file-version-id)
                      :on-click #(>evt [:dialog/close])}
      (loc :operations-locations.load-dialog.download-button-label)]]]])

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
         [:td (model-utils/format-version version)]
         [:td (model-utils/format-fi-datetime uploaded-at)]
         [:td (model-utils/format-user-name uploaded-by)]
         [:td
          [:a.btn.ghost
           {:href (model-utils/->download-url file-id :file-version-id file-version-id)}
           (loc :operations-locations.download-version-button-label)]]])]]]
   [:div.button-group.flex.flex--right
    [:button.primary {:on-click #(>evt [:dialog/close])} (loc :button.ok)]]])

(defn element-row [element]
  [:tr [:td (loc element)]])

(defn model-data-summary
  [job]
  (let [elements      (-> job :value :task :report :elements)
        premise-count (-> job :value :task :report :premises)]
    [:div {:style {:height "100%"}}
     [:div [:span (str (loc :rakennushanke.toimenpiteet.huoneistoja) " " premise-count)]]
     (when (> premise-count 0)
       [:div
        [:div (loc :operations-locations.read-model-report.premises)]
        [:table
         [:tbody
          (for [element (filter #(ss/includes? % "huoneistot") elements)]
            ^{:key element}
            [element-row element])]]])
     [:div (loc :operations-locations.read-model-report.other)]
     [:table
      [:tbody
       (for [element (filter #(not (ss/includes? % "huoneistot")) elements)]
         ^{:key element}
         [element-row element])]]]))

;; Events

(rf/reg-event-fx ::toggle-view
  (fn [{:keys [db]} [_ visible?]]
    (if visible?
      {:fx [[:dispatch [::scroll-service-follow]]
            [:dispatch [::fetch-locations-operations]]
            [:dispatch [::model-utils/fetch-linked-file-metadatas]]
            [:dispatch [::model-utils/fetch-ifc-data-availability]]
            [:dispatch [::bim-assignments/refresh-assignments]]
            [:dispatch [::hub/subscribe
                        "assignmentService::changed-cljs"
                        #(>evt [::bim-assignments/refresh-assignments])
                        ::assignments-changed]]]}
      {:fx [[:dispatch [::hub/unsubscribe ::assignments-changed]]]
       :db (dissoc db
                   ::locations-operations
                   ::model-utils/linked-file-metadatas
                   ::model-utils/ifc-data-ready-version-ids
                   ::model-utils/pending-ifc-version-ids
                   ::selected-files)})))

(rf/reg-event-db ::toggle-file-selection
  (fn [db [_ file-version-id]]
    (update-in db [::selected-files] #(if (contains? % file-version-id)
                                        (disj % file-version-id)
                                        (conj (set %) file-version-id)))))

(rf/reg-event-db ::toggle-phase-accordion
  (fn [db [_ operation-id model-phase]]
    (update-in db [::phase-accordion-open? operation-id model-phase] not)))

(rf/reg-event-fx ::scroll-service-follow
  (fn [_ _]
    {:hub/send {:event "scrollService::follow"
                :data  {:hashRe #"/structures$"}}}))

(rf/reg-event-fx ::open-download-file-dialog
  (fn [_ [_ file-id file-version-id]]
    {:dialog/show {:title            (loc :operations-locations.download-dialog.title)
                   :size             :medium
                   :type             :react
                   :dialog-component [download-prompt file-id file-version-id]
                   :minContentHeight "45px"}}))

(rf/reg-event-fx ::open-download-file-version-dialog
  (fn [_ [_ file-id file-versions]]
    {:dialog/show {:title            (loc :operations-locations.download-version-dialog.title)
                   :size             :large
                   :type             :react
                   :dialog-component [download-version-modal file-id file-versions]
                   :minContentHeight "200px"}}))

(rf/reg-event-fx ::open-structure-page
  (fn [{db :db} [_ file-id]]
    {:page/open [:structure (router/path-parameter db :app-id) file-id]}))

(rf/reg-event-fx ::open-structures-page
  (fn [{db :db} [_ file-ids]]
    {:page/open [:structures
                 (router/path-parameter db :app-id)
                 (ss/join "%2C" file-ids)]})) ; "CSV with comma (%2C) separator

(rf/reg-event-fx ::fetch-locations-operations
  (fn [{db :db} _]
    {:db           (assoc db ::locations-operations :loading)
     :action/query {:name    :location-operations
                    :params  {:id (router/path-parameter db :app-id)}
                    :success [::fetch-locations-operations.success]}}))

(rf/reg-event-db ::fetch-locations-operations.success
  (fn [db [_ data]]
    (assoc db ::locations-operations data)))

(rf/reg-event-fx ::location-changed
  (fn [{db :db} [_ operation-id x y]]
    {:dialog/close   true
     :action/command {:name    :set-operation-location
                      :params  {:id           (router/path-parameter db :app-id)
                                :operation-id operation-id
                                :x            x
                                :y            y}
                      :success [::fetch-locations-operations]}}))

(rf/reg-event-fx ::open-change-location-dialog
  (fn [{db :db} [_ op]]
    {:dialog/show {:title            (loc :location.edit)
                   :size             :large
                   :type             :location-editor
                   :callback         #(>evt [::location-changed (:id op) %1 %2])
                   :cancel-callback  #(>evt [:dialog/close])
                   :component-params {:x      (get-in op [:location 0])
                                      :y      (get-in op [:location 1])
                                      :center (::current-application-location db)}}}))

(rf/reg-sub ::app-id
  :<- [:route/path-parameters]
  :-> :app-id)

(rf/reg-sub ::locations-operations :-> ::locations-operations)

(rf/reg-sub ::operations
  :<- [::locations-operations]
  (fn [{:keys [ok operations]} _]
    (if ok
      operations
      :loading)))

(rf/reg-sub ::valid-model-phase-set
  :<- [::locations-operations]
  (fn [{:keys [ok valid-model-phases]} _]
    (when ok
      (->> valid-model-phases (map keyword) set))))

(rf/reg-sub ::valid-phase?
  :<- [::valid-model-phase-set]
  (fn [valid-phase? [_ model-phase]]
    (and valid-phase?
         (valid-phase? (keyword model-phase)))))

(rf/reg-sub ::phase-visible?
  (fn [[_ operation-id model-phase]]
    [(rf/subscribe [::valid-model-phase-set])
     (rf/subscribe [::model-utils/phase-file-count operation-id model-phase])])
  (fn [[valid-phase? file-count] [_ _ model-phase]]
    (or (some-> file-count pos?)
        (valid-phase? model-phase))))

(rf/reg-sub ::selected-files
  (fn [{::keys [selected-files]}]
    (set selected-files)))

(rf/reg-sub ::file-selected?
  :<- [::selected-files]
  (fn [selected-files [_ file-id]]
    (contains? selected-files file-id)))

(rf/reg-sub ::phase-accordion-states :-> ::phase-accordion-open?)

(rf/reg-sub ::phase-accordion-open?
  :<- [::phase-accordion-states]
  (fn [states [_ operation-id model-phase]]
    (boolean (get-in states [operation-id model-phase]))))

(rf/reg-event-fx ::open-model-data-summary-dialog
  (fn [_ [_ job]]
    {:dialog/show {:title            (loc :operations-locations.read-model-report.title)
                   :size             :large
                   :type             :react
                   :dialog-component [model-data-summary job]
                   :minContentHeight "100px"}}))

(rf/reg-event-fx ::apply-ifc-model-data.success
  (fn [{:keys [db]} [_ _response target-file-path]]
    {:db (update db ::pending-model-read #(dissoc % target-file-path))
     :fx [[:dispatch [::fetch-locations-operations]]
          [:dispatch [:indicator/positive]]
          [:application/lightload (router/path-parameter db :app-id)]]}))

(rf/reg-event-fx ::apply-ifc-model-data.error
  (fn [{:keys [db]} [_ _response target-file-path]]
    {:db (update db ::pending-model-read #(dissoc % target-file-path))
     :fx [[:dispatch [:indicator/negative]]]}))

(rf/reg-event-fx ::apply-ifc-model-data
  (fn [{db :db} [_ {:keys [file-version-id target-file-path] :as version}]]
    {:db           (update db ::pending-model-read #(assoc % target-file-path :pending))
     :action/query {:name    :fetch-ifc-doc
                    :params  {:id              (router/path-parameter db :app-id)
                              :file-version-id file-version-id}
                    :success [::apply-ifc-model-data.doc-fetched version]
                    :error   [::apply-ifc-model-data.error nil target-file-path]}}))

(rf/reg-event-fx ::apply-ifc-model-data.doc-fetched
  (fn [{db :db} [_ {:keys [app-doc]} {:keys [target-file-path file-version-id]}]]
    {:action/command {:name    :apply-ifc-model-data
                      :params  {:id              (router/path-parameter db :app-id)
                                :file-version-id file-version-id
                                :docId           (:id app-doc)}
                      :success [::apply-ifc-model-data.success target-file-path]
                      :error   [::apply-ifc-model-data.error target-file-path]}}))

(rf/reg-event-fx ::read-model-data.success
  (fn [{:keys [db]} [_ job]]
    {:db (update db ::pending-model-read #(dissoc % (-> job :value vals first :target-file-path)))
     :fx [[:dispatch [::fetch-locations-operations]]
          [:dispatch [::open-model-data-summary-dialog job]]
          [:dispatch [:indicator/positive]]
          [:application/lightload (router/path-parameter db :app-id)]]}))

(rf/reg-event-fx ::read-model-data.error
  (fn [{:keys [db]} [_ job]]
    {:db (update db ::pending-model-read #(dissoc % (-> job :value vals first :target-file-path)))
     :fx [[:dispatch [:indicator/negative]]]}))

(rf/reg-event-fx ::poll-read-model-data-job.success
  (fn [_ [_ resp job]]
    (case (-> resp :job :value vals first :status)
      "done" {:fx [[:dispatch [::read-model-data.success (:job resp)]]]}
      "error" {:fx [[:dispatch [::read-model-data.error job]]]}
      {:fx [[:dispatch [::poll-read-model-data-job {:job job}]]]})))

(rf/reg-event-fx ::poll-read-model-data-job
  (fn [{db :db} [_ {:keys [job]}]]
    {:action/query {:name    :read-model-data-job
                    :params  {:id      (router/path-parameter db :app-id)
                              :job-id  (:id job)
                              :version (:version job)}
                    :success [::poll-read-model-data-job.success job]}}))

(rf/reg-sub ::read-model-data-pending?
  (fn [db [_ target-file-path]]
    (some-> db ::pending-model-read (get target-file-path) (= :pending))))

(rf/reg-sub ::check-model-pending?
  (fn [db [_ target-file-path]]
    (some-> db ::pending-model-check (get-in [target-file-path :status]) (= :pending))))

(rf/reg-event-fx ::delete-model.success
  (fn [_ _]
    {:fx [[:dispatch [::fetch-locations-operations]]
          [:dispatch [::model-utils/fetch-linked-file-metadatas]]
          [:dispatch [:indicator/positive]]]}))

(rf/reg-event-fx ::delete-model
  (fn [{:keys [db]} [_ file-id]]
    {:action/command {:name    :delete-linked-file
                      :params  {:id      (router/path-parameter db :app-id)
                                :file-id file-id}
                      :success [::delete-model.success]}}))

(defn field [loc-key value]
  [:div.flex.flex--wrap.flex--gap2
   [:label.txt--bold (loc loc-key)]
   (when value [:div value])])

(defn model-type-dropbox-for-file
  [{:keys [target-entity] {:keys [model-type model-phase]} :metadata :as file}]
  [components/dropdown
   model-type
   {:id       (str (get target-entity :file-id) "-model-type")
    :items    (model-utils/model-type-dropdown-items)
    :choose?  false
    :sort-by  :text
    :test-id  :value
    :enabled? (boolean (and (<sub [::valid-phase? model-phase])
                            (<sub [:auth/application? :set-linked-application-file-metadata-field])))
    :callback #(>evt [::model-utils/update-file-metadata file :model-type %])}])

(defn check-passed?
  "Returns
    true    when check is passed
    false   when check is failed (not all tests passed or there was an error)
    nil     when check is not yet executed"
  [{:keys [tests-count tests-passed timestamp check-failed]}]
  (and timestamp ; and returns nil if missing
       (>= tests-passed tests-count)
       (not check-failed)))

(defn check-status [version]
  (let [model-check-pending? (<sub [::check-model-pending? (:target-file-path version)])
        [class text]         (if model-check-pending?
                               ["lupicon-refresh icon-spin" :operations-locations.check-model-pending]
                               (let [results (->> version
                                                  :bim
                                                  ((juxt :validation-result :compliance-result))
                                                  (keep check-passed?)
                                                  seq)]
                                 (cond
                                   (nil? results)            ["lupicon-circle-question"
                                                              :operations-locations.check-model-result-none]
                                   (every? identity results) ["lupicon-circle-check positive"
                                                              :operations-locations.check-model-result-success]
                                   :else                     ["lupicon-circle-attention negative"
                                                              :operations-locations.check-model-result-error])))]
    [:div.flex.flex--gap1 {:class (when (not model-check-pending?) "pad--t1")}
     [:i {:class class}]
     [:span (loc text)]]))

(defn context-menu
  [{:keys [operation-id file-id last-version file-upload-id file-input-id file]}]
  (when (and (<sub [::model-utils/can-upload-and-modify?])
             (<sub [::valid-phase? (-> file :metadata :model-phase)]))
    (let [is-ready?              (<sub [::file-upload/is-ready? file-upload-id])
          target-file-path       (:target-file-path last-version)
          model-read-pending?    (<sub [::read-model-data-pending? target-file-path])
          model-check-pending?   (<sub [::check-model-pending? target-file-path])]
      [components/context-menu
       [(when (and is-ready?
                   (not model-read-pending?))
          {:actions [{:component-type :icon-label-button
                      :label-opts     {:for file-input-id}
                      :title          :operations-locations.upload-version-button-label
                      :icon           :lupicon-upload}]})
        (when (and (not model-read-pending?)
                   (not model-check-pending?))
          (let [ifc-ready? (<sub [::model-utils/ifc-data-ready? (:file-version-id last-version)])]
            {:actions [{:component-type :icon-button-with-wait
                        :disabled?      (not ifc-ready?)
                        :on-click       (when ifc-ready?
                                          (fn [] (components/confirm-dialog
                                                   "operations-locations.read-model-confirm.title"
                                                   "operations-locations.read-model-confirm.message"
                                                   #(>evt [::apply-ifc-model-data last-version]))))
                        :title          :operations-locations.read-model-label
                        :icon           :lupicon-refresh}]}))
        {:actions [{:component-type :icon-button
                    :on-click       (fn [] (components/confirm-dialog
                                             "operations-locations.delete-model-confirm.title"
                                             "operations-locations.delete-model-confirm.message"
                                             #(>evt [::delete-model file-id])))
                    :title          :operations-locations.delete-model-label
                    :icon           :lupicon-trash}]}]
       {}])))

(defn file-select-toggle
  "Allows user to select one or more BIM files; for now used for opening multiple files in the BIM viewer"
  [{:keys [last-version operation-id] {last-version-id :file-version-id} :last-version}]
  [components/toggle
   (<sub [::file-selected? last-version-id])
   {:id        (str operation-id "-" last-version-id "-checkbox")
    :disabled? (-> last-version :bim :xkt-file nil?)
    :callback  #(>evt [::toggle-file-selection last-version-id])}])

(defn open-model-button
  "Displays button that opens the model page, or a spinner animation if it is not yet available"
  [{:keys [file-id last-version]}]
  (if (<sub [::read-model-data-pending? (:target-file-path last-version)])
    [:div.flex.flex--gap1.pad--t1
     [:i.lupicon-refresh.icon-spin]
     [:span (loc :operations-locations.read-model-status)]]
    [:button.tertiary.caps
     {:on-click #(>evt [::open-structure-page file-id])}
     (loc :operations-locations.model-open)]))

(defn model-download-link [{:keys [file-id] {{file-name :name} :metadata} :last-version}]
  [:a.download-model {:on-click #(>evt [::open-download-file-dialog file-id])} file-name])

(defn model-version-download-dialog [{:keys [file-id file-versions last-version]}]
  [:a.version-link {:on-click #(>evt [::open-download-file-version-dialog file-id file-versions])}
   (model-utils/format-version (:version last-version))])

(defn assignment-counter [assignment-count]
  (when (pos? assignment-count)
    [:span.attachment-remark-counter.mr-1
     {:aria-hidden true
      :class       :attachment-remark-warn}
     assignment-count]))

(defn file-row
  "Row in the BIM file table for a single IFC file and its versions"
  [{:keys [operation-id file] :as ctx}]
  (let [{:keys [file-id]}      (:target-entity file)
        file-versions          (:versions file)
        last-version           (last (sort-by (comp (juxt :major :minor) :version) file-versions))
        file-upload-id         (file-upload/generate-component-id operation-id :file-id file-id)
        file-input-id          (file-upload/->file-element-id file-upload-id)
        ctx                    (merge ctx (util/mmap file-id file-versions last-version file-upload-id file-input-id))]
    [:tr
     [:td {:data-label (loc :operations-locations.filename)}
      [:div.flex--align-center
       [file-select-toggle ctx]
       [assignment-counter (<sub [::bim-assignments/file-assignment-count file])]
       [model-download-link ctx]]]
     [:td {:data-label (loc :operations-locations.type)}
      [model-type-dropbox-for-file file]]
     [:td {:data-label (loc :attachment.version)}
      [:div.flex--align-center
       [model-version-download-dialog ctx]
       [model-upload/model-version-upload (assoc ctx :success-event ::model-upload/on-model-upload-success)]]]
     [:td {:data-label (loc :operations-locations.model-check)}
      [check-status last-version]]
     [:td
      [:div.flex.justify-end.flex--gap1
       [open-model-button ctx]
       [context-menu ctx]]]]))

(defn uploaded-models-table [{:keys [operation-id model-phase] :as ctx}]
  (let [files (<sub [::model-utils/phase-files operation-id model-phase])]
    [:table.model-table.w--100
     [:thead [:tr
              [:th.rounded-tl-lg (loc :operations-locations.filename)]
              [:th (loc :operations-locations.type)]
              [:th (loc :attachment.version)]
              [:th (loc :operations-locations.model-check)]
              [:th.rounded-tr-lg]]]
     (or (some->> (seq files)
                  (map (fn [{{:keys [file-id]} :target-entity :as file}]
                         ^{:key file-id} [file-row (assoc ctx :file file)]))
                  (into [:tbody]))
         [:tbody [:tr [:td {:col-span 4} (loc :operations-locations.no-files)]]])]))

(defn edit-location-button [op]
  [:button.tertiary.caps
   {:disabled (not (<sub [:auth/application? :set-operation-location]))
    :on-click #(>evt [::open-change-location-dialog op])}
   (loc :location.edit)])

(defn operation-item-model-phase
  "Group of IFC BIM files; groups are based on application phase the file is relevant for,
  i.e. planned during application submission, amended during construction, or reported on completion"
  [{:keys [id phase] :as op}]
  (when (<sub [::phase-visible? id phase])
    (let [accordion-open?  (<sub [::phase-accordion-open? id phase])
          accordion-title  (loc (util/kw-path :operations-locations.model-phases phase :title))
          file-count       (<sub [::model-utils/phase-file-count id phase])]
      [:section
       [:div.flex--between.flex--wrap
        [:div.flex--wrap.cursor-pointer
         {:on-click #(>evt [::toggle-phase-accordion id phase])}
         (if accordion-open?
           [:i.lupicon-chevron-up.mx-2.text-lg]
           [:i.lupicon-chevron-down.mx-2.text-lg])
         [assignment-counter (<sub [::bim-assignments/phase-assignment-count id phase])]
         [:h2 accordion-title " (" file-count ")"]]
        (when (<sub [::valid-phase? phase])
          [model-upload/model-upload-button op])]
       (when accordion-open?
         [:div.px-3 [uploaded-models-table {:operation-id id :model-phase phase}]])])))

(defn operation-item-fields
  "Lists some info about the operation (building), separated by a vertical bar"
  [{:keys [tag description building-id] [x y] :location}]
  (let [desc-str (->> [tag description] (remove nil?) (ss/join ": "))
        coords   (if (and x y)
                   (ss/join ", " [x y])
                   [:span.location-missing (loc :location.missing)])]
    (->> [[field :location.location coords]
          [field :location.building-id building-id]
          [field :location.tag desc-str]]
         (interpose [:div "|"])
         (into [:div.flex.flex--wrap.flex--gap2]))))

(defn operation-item-phases
  "Lists of BIM files grouped by application phase (:submission-time, :construction-time or :completion-time)"
  [op]
  (when (<sub [::valid-model-phase-set]) ; not loading still
    (->> (reverse bim-schema/model-phases) ; display from newest to earliest
         (map (fn [phase]
                ^{:key (str (:id op) "-phase-" (name phase))}
                [operation-item-model-phase (assoc op :phase phase)]))
         (into [:<>]))))

(defn operation-item
  "The card for a single operation; contains some info about the operation and building
  and lists of BIM files grouped by application phase"
  [op]
  [:div.rounded--1.border-solid.border-ui-gray-221.gap--b4
   [:div.bg--blue.pad--1.rounded--1.gap--b2
    [:div.flex.flex--between
     [:h2.txt--bold (->> op :operation (str "operations.") loc)]
     [edit-location-button op]]
    [:hr.mt-0.border-black]
    [operation-item-fields op]]
   [operation-item-phases op]])

(defn operations-list [operations]
  [:div
   (for [op operations]
     ^{:key (:id op)}
     [operation-item op])])

(defn open-selected-structures-button
  "Opens multiple selected files in the same BIM viewer.
  Selection is made above in `file-row`"
  []
  (when-let [selected-files (seq (<sub [::selected-files]))]
    [:button.secondary.mb-6.uppercase
     {:on-click #(>evt [::open-structures-page selected-files])}
     (loc :operations-locations.model-open.multiselect)]))

(defn main-view []
  (let [operations (<sub [::operations])]
    [:div
     [:div.flex.justify-end
      [open-selected-structures-button]]
     [:div.gap--b2
      [data-source-selector/render]]
     (if (= operations :loading)
       [:div (loc "loading")]
       [operations-list operations])]))

(defonce mounter (ui-dom/init :reagent (fn [] main-view)))

(defn ^:export start [dom-id params]
  (let [{:keys [applicationLocation]} (common/->cljs params)]
    (>evt [:value/set ::current-application-location applicationLocation])
    (ui-dom/mount! mounter dom-id)))
