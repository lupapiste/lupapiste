(ns lupapalvelu.ui.attachment.admin.attachment-settings-view
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.ui.common :refer [loc loc-html] :as common]
            [lupapalvelu.ui.components :as components]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))

(defn get-attachment-list
  "Return attachment-types as map for operation and attachment list type

  Types:

  * `:allowed-attachments` Returns the allowed attachment types specified by an admin. If an admin has not
   overridden the types, returns the default attachment type list of the permit-type of the operation.
  * `:defaults-attachments` Returns defaults attachments for operations, or empty map if none is set.
  * `:allowed-attachments-baseline` Returns attachment baseline depended upon permit-type of operation."
  [att-settings op type]
  (let [operation-settings (get-in att-settings [:operation-nodes op])

        {:keys [permit-type default-attachments allowed-attachments]} operation-settings

        permit-type-baseline (get-in att-settings [:defaults :allowed-attachments (keyword permit-type)])

        {:keys [mode types]} allowed-attachments]
    (cond
      (= type :default-attachments)
      default-attachments

      (or (= type :allowed-attachments-baseline)
          (and
            (= type :allowed-attachments)
            (= mode "inherit")))
      permit-type-baseline

      (and
        (= type :allowed-attachments)
        (= mode "set"))
      types)))

(defn attachment-type-selector
  "Renders attachment type selector dialog

  Parameters:
  * `op` operation name as keyword
  * `type` either :default-attachments or :allowed-attachments."
  [op type]
  (let [att-settings (<sub [::att-settings])

        permit-type-baseline (get-attachment-list att-settings op :allowed-attachments-baseline)

        selected-items (get-attachment-list att-settings op type)

        to-selectm-option
        (fn [group-id type-id]
          {:id   {:type-group group-id
                  :type-id    type-id}
           :text (loc (util/kw-path :attachmentType group-id type-id))})

        group->types-source-list-fragment
        (fn [[group-id type-ids]]
          [(loc (util/kw-path :attachmentType group-id :_group_label))
           (map (partial to-selectm-option group-id) type-ids)])

        group->types-target-list-fragment
        (fn [[group-id type-ids]]
          (map (partial to-selectm-option group-id) type-ids))

        source-types (->> permit-type-baseline (map group->types-source-list-fragment))
        target-types (->> selected-items (mapcat group->types-target-list-fragment))]
    (r/create-class
      {:component-did-mount (fn [comp]
                              (-> (js/$ "#dialog-edit-attachments")
                                  (.selectm nil "edit-attachments")
                                  (.ok #(>evt [::open-attachment-type-selector.ok op (js->clj % :keywordize-keys true) type]))
                                  (.cancel #(>evt [::open-attachment-type-selector.cancel]))
                                  (.reset (clj->js source-types) (clj->js target-types))))
       :reagent-render
       (fn []
         [:div#dialog-edit-attachments.autosized.selectm-dialog])})))

(rf/reg-event-fx
  ::open-attachment-type-selector.cancel
  (fn [_ _]
    {:dialog/close true}))

(rf/reg-event-fx
  ::open-attachment-type-selector.ok
  (fn [{db :db} [_ op selected-values type]]
    (let [types-vector-as-list (map (fn [{:keys [type-group type-id]}] [type-group type-id]) selected-values)
          cmd (case type
                :default-attachments {:name                  :organization-operations-attachments
                                      :params                {:organizationId (::organization-id db)
                                                              :operation      op
                                                              :attachments    types-vector-as-list}
                                      :show-saved-indicator? true
                                      :success               [::update-organization-operations-attachments.success]}
                :allowed-attachments {:name                  :set-organization-operations-allowed-attachments
                                      :params                {:organizationId (::organization-id db)
                                                              :mode           :set
                                                              :operation      op
                                                              :attachments    types-vector-as-list}
                                      :show-saved-indicator? true
                                      :success               [::update-organization-operations-attachments.success]})]
      {:dialog/close   true
       :action/command cmd})))

(rf/reg-event-fx
  ::update-organization-operations-attachments.success
  (fn [_ [_ res]]
    {:dispatch [::fetch-organization-config]}))

(rf/reg-event-fx
  ::open-attachment-type-selector
  (fn [_ [_ op type]]
    {:dialog/show {:ltitle           (util/kw-path :operations op)
                   :size             :large
                   :type             :react
                   :dialog-component [attachment-type-selector op type]
                   :minContentHeight "590px"}}))

(rf/reg-event-db
  ::fetch-organization-config.success
  (fn [db [_ {:keys [organization operation-attachment-settings] :as res}]]
    (merge db {::organization        organization
               ::attachment-settings operation-attachment-settings})))

(rf/reg-event-fx
  ::fetch-organization-config
  (fn [{db :db} _]
    {:action/query {:name    :organization-by-user
                    :params  {:organizationId          (::organization-id db)
                              :attachment-types-layout :map}
                    :success [::fetch-organization-config.success]}}))

(rf/reg-event-fx
  ::fetch-available-tos-functions
  (fn [{db :db} _]
    {:action/query {:name    :available-tos-functions
                    :params  {:organizationId (::organization-id db)}
                    :success [::fetch-available-tos-functions.success]}}))

(rf/reg-event-db
  ::fetch-available-tos-functions.success
  (fn [db [_ {:keys [functions]}]]
    (assoc db ::available-tos-functions
              (->> functions
                   (map (fn [{:keys [code name]}] {:text name :value code}))))))

(rf/reg-event-fx
  ::toggle-default-attachments-mandatory-operation
  (fn [{db :db} [_ op value]]
    {:db             (assoc-in db [::attachment-settings :operation-nodes op :default-attachments-mandatory?] value)
     :action/command {:name                  :toggle-default-attachments-mandatory-operation
                      :params                {:organizationId (::organization-id db)
                                              :operationId    op
                                              :mandatory      value}
                      :show-saved-indicator? true}}))

(rf/reg-event-fx
  ::update-tos-function
  (fn [{db :db} [_ op value]]
    {:db             (assoc-in db [::attachment-settings :operation-nodes op :tos-function] value)
     :action/command {:name                  (if (ss/blank? value) :remove-tos-function-from-operation
                                                                   :set-tos-function-for-operation)
                      :params                (util/assoc-when {:organizationId (::organization-id db)
                                                               :operation      op
                                                               :functionCode   (ss/blank-as-nil value)})
                      :show-saved-indicator? true}}))

(rf/reg-event-db
  ::toggle-view-allowed-attachments
  (fn [db [_ op value]]
    (assoc-in db [::view-allowed-attachments op] value)))

(rf/reg-sub
  ::view-allowed-attachments
  (fn [db [_ op]]
    (get-in db [::view-allowed-attachments op])))

(rf/reg-sub
  ::available-tos-functions
  (fn [db _]
    (::available-tos-functions db)))

(rf/reg-sub
  ::att-settings
  (fn [db _]
    (::attachment-settings db)))

(rf/reg-sub
  ::selected-permit-types
  (fn [db _]
    (-> (get-in db [::attachment-settings :permit-type-nodes])
        keys)))

(defn- selected-op? [selected-operations permit-type op-name]
  (some #(util/=as-kw op-name %)
        (get selected-operations (keyword permit-type))))

(rf/reg-sub
  ::selected-operation?
  :<- [:value/get ::organization :selectedOperations]
  (fn [operations [_ permit-type op-name]]
    (selected-op? operations permit-type op-name)))

(rf/reg-sub
  ::operations-of-permit-type
  :<- [::att-settings]
  :<- [:value/get ::organization :selectedOperations]
  :<- [:value/get ::selected-operations?]
  (fn [[att-settings selected-operations selected?] [_ permit-type]]
    (let [limit-fn (if selected?
                     (partial selected-op? selected-operations permit-type)
                     identity)]
      (->> (:operation-nodes att-settings)
           (keep (fn [[op-name settings]]
                   (when (and (util/=as-kw permit-type (:permit-type settings))
                              (limit-fn op-name))
                     op-name)))
           (sort-by #(loc (util/kw-path :operations %)))
           (into [])))))

(rf/reg-sub
  ::tos-function-visible?
  (fn [db _]
    (boolean (seq (get db ::available-tos-functions)))))

(rf/reg-sub
  ::operations-attachment-settings
  (fn [db [_ op]]
    (get-in db [::attachment-settings :operation-nodes (keyword op)])))

(defn operations-table-header []
  (let [tos-function-visible? (<sub [::tos-function-visible?])]
    [:thead
     [:tr
      [:th.w--50 (loc :auth-admin.operations-attachments.operation)]
      (when tos-function-visible?
        [:th
         (loc :tos.function)])
      [:th.w--50 (loc :auth-admin.operations-attachments.attachments)]
      [:th (loc :auth-admin.default-attachments-mandatory)]]]))

(defn allowed-attachment-viewer [op]
  (let [view-allowed (<sub [::view-allowed-attachments op])
        att-settings (<sub [::att-settings])

        att-map (get-attachment-list att-settings op :allowed-attachments)

        defaults-attachment-localiced
        (->> att-map
             (mapcat (fn [[k ids]] (map (partial util/kw-path :attachmentType k) ids)))
             (map loc))

        list (->> defaults-attachment-localiced (sort) (ss/join ", "))

        att-count (count defaults-attachment-localiced)]
    (if view-allowed
      [:span list
       [components/icon-button
        {:class    "tertiary caps dsp--inline-block btn-small gap--l1"
         :text-loc :attachment-settings.hide
         :icon     :i.lupicon-eye-off
         :on-click #(>evt [::toggle-view-allowed-attachments op false])}
        ]]
      [:span (loc-html :span
                       (if (= att-count 1)
                         :attachment-settings.allowed-attachment-count-one
                         :attachment-settings.allowed-attachment-count)
                       att-count)
       [components/icon-button
        {:class    "tertiary caps dsp--inline-block btn-small gap--l1"
         :text-loc :attachment-settings.show-allowed
         :icon     :i.lupicon-eye
         :on-click #(>evt [::toggle-view-allowed-attachments op true])}]])))

(defn operation-row [permit-type op]
  (let [tos-function-visible?   (<sub [::tos-function-visible?])
        available-tos-functions (<sub [::available-tos-functions])

        organization-operations-attachments-allowed?
        (<sub [:auth/global? :organization-operations-attachments])

        {:keys [tos-function
                default-attachments
                default-attachments-mandatory?
                deprecated?]}
        (<sub [::operations-attachment-settings op])

        defaults-attachment-localiced
        (->> default-attachments
             (mapcat (fn [[k ids]] (map (partial util/kw-path :attachmentType k) ids)))
             (map loc)
             (ss/join ", "))]

    ^{:key op}
    [:tr.operations
     [:td {:style {:padding-left "2em"}}
      (loc (util/kw-path [:operations op]))
      (when deprecated?
        [:div.txt--bold (loc :attachment-settings.operation.deprecated)])]
     (when tos-function-visible?
       [:td
        [components/dropdown
         tos-function
         {:id        (str "tos-function-" op)
          :items     available-tos-functions
          :required? false
          :sort-by   :text
          :test-id   :value
          :callback  #(>evt [::update-tos-function op %])}]])
     [:td
      [:div
       [:span [:b (loc :admin.attachments.default-attachments)] ": "]
       [:span defaults-attachment-localiced]
       (when organization-operations-attachments-allowed?
         [:span
          [components/icon-button
           {:class    "tertiary caps dsp--inline-block btn-small gap--l1"
            :text-loc :edit
            :icon     :lupicon-pen
            :on-click #(>evt [::open-attachment-type-selector op :default-attachments])}
           ]])]

      [:div
       [:span [:b (loc :admin.attachments.allowed-attachments)] ": "]
       [allowed-attachment-viewer op]
       (when organization-operations-attachments-allowed?
         [:span
          [components/icon-button
           {:class    "tertiary caps display--inline-block btn-small gap--l1"
            :text-loc :edit
            :icon     :lupicon-pen
            :on-click #(>evt [::open-attachment-type-selector op :allowed-attachments])}
           ]])]]
     [:td
      (let [auth?     (<sub [:auth/global? :toggle-default-attachments-mandatory-operation])
            selected? (<sub [::selected-operation? permit-type op])]
        [components/toggle
         default-attachments-mandatory?
         {:test-id   (str "mandatory-attachments-" op)
          :callback #(>evt [::toggle-default-attachments-mandatory-operation op %])
          :enabled? (boolean (and auth? selected?))}])]]))

(defn permit-type-rows [permit-type]
  (let [tos-function-visible? (<sub [::tos-function-visible?])
        operations            (<sub [::operations-of-permit-type permit-type])]
    [:<>
     [:tr.permit-type
      [:td {:col-span 3}
       [:h3 {:style {:padding-top "1em"}}
        (loc :auth-admin.permit-type) ": " [:b (loc [permit-type])]]]
      (when tos-function-visible? [:td])]
     (for [op operations]
       ^{:key op}
       [operation-row permit-type op])]))

(defn operations-table []
  (let [permit-types (<sub [::selected-permit-types])]
    [:table
     [operations-table-header]
     [:tbody
      (for [permit-type permit-types]
        ^{:key permit-type}
        [permit-type-rows permit-type])]]))

(defn main-view
  []
  (r/with-let [_ (>evt [:auth/refresh])
               _ (>evt [::fetch-organization-config])
               _ (>evt [::fetch-available-tos-functions])
               _ (>evt [:value/set ::selected-operations? true])
               _ (>evt [::hub/subscribe "organization-selected-operations"
                        #(>evt [::fetch-organization-config])
                        ::operations-hub-id])]
              (when (<sub [::att-settings])
                [:div.flex--between.flex--wrap.flex--gap1.flex--align-end
                 [:h2 (loc :auth-admin.operations-attachments)]
                 [components/toggle
                  (<sub [:value/get ::selected-operations?])
                  {:text-loc :auth-admin.selected-operations
                   :callback #(>evt [:value/set ::selected-operations? %])}]
                 [operations-table]])
    (finally
      (>evt [::hub/unsubscribe ::operations-hub-id]))))

(defn mount-component [dom-id]
  (rd/render [main-view]
             (.getElementById js/document dom-id)))

(defn ^:export start [dom-id params]
  (>evt [:value/set ::organization-id (common/->cljs (common/oget params "orgId"))])
  (mount-component dom-id))
