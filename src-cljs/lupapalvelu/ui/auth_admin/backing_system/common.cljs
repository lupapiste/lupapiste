(ns lupapalvelu.ui.auth-admin.backing-system.common
  "Asianhallinta configuration section for the municipal admin"
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [lupapalvelu.backing-system.integration :as integration]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.auth-admin.backing-system.components :as components]
            [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]))

;;; Utilities

(defn- target->str
  [permit-type]
  (loc permit-type))

(rf/reg-event-fx ::fetch-configs
  (fn [_ _]
    {:fx [[:action/query {:name    :integration-config
                          :success [::config-fetched]}]]}))

(rf/reg-event-fx ::config-fetched
  (fn [{:keys [db]} [_ {:keys [data]}]]
    (let [data (-> data
                   (update :configs (fn [configs]
                                      (->> configs
                                           (map (juxt :id integration/json-config->config))
                                           (remove (comp nil? second))
                                           (into {})))))]
      {:db (assoc db ::data (select-keys data [:configs :available]))})))

(defn- save-fx
  [config]
  [:action/command {:name                  :save-integration-config
                    :params                {:config config}
                    :success               [::fetch-configs]
                    :show-saved-indicator? true}])

(rf/reg-event-fx ::on-enabled-clicked
  (fn [_ [_ config enabled?]]
    {:fx [(save-fx (assoc config :enabled? enabled?))]}))

(rf/reg-event-fx ::save-button-clicked
  (fn [_ [_ config]]
    {:fx [(save-fx config)
          [:dialog/close true]]}))

(rf/reg-event-fx ::cancel-button-clicked
  (fn [{:keys [db]} _]
    {:db (update db ::data dissoc :changes :new)
     :fx [[:dialog/close true]]}))

(declare integration-dialog)

(defn- show-dialog-for
  [id]
  {:dialog/show {:ltitle           (if (= :new id)
                                     :auth-admin.integration.add
                                     :auth-admin.integration.edit)
                 :size             :medium
                 :type             :react
                 :dialog-component [integration-dialog id]
                 :cancel-callback  #(>evt [::cancel-button-clicked])}})

(rf/reg-event-fx ::edit-link-clicked
  (fn [_ [_ id]]
    (show-dialog-for id)))

(rf/reg-event-fx ::add-button-clicked
  (fn [_ _]
    (show-dialog-for :new)))

(rf/reg-event-fx ::integration-type-selected
  (fn [{:keys [db]} [_ id integration-type]]
    ;; Note that we clear all other settings when changing type
    {:db (assoc-in db [::data :changes id] {:integration-type integration-type
                                            :enabled?         false})}))

(rf/reg-event-fx ::new-target-selected
  (fn [{:keys [db]} [_ id target]]
    {:db (assoc-in db [::data :changes id :targets] #{target})}))

(rf/reg-event-fx ::target-toggled
  (fn [{:keys [db]} [_ id target]]
    (let [targets (or (get-in db [::data :changes id :targets])
                      (get-in db [::data :configs id :targets])
                      #{})]
      {:db (assoc-in db [::data :changes id :targets] (if (contains? targets target)
                                                        (disj targets target)
                                                        (conj targets target)))})))

(rf/reg-event-fx ::option-changed
  (fn [{:keys [db]} [_ id option value]]
    {:db (assoc-in db [::data :changes id :options option] value)}))

(rf/reg-sub ::data
  :-> ::data)

;; Changes contain local changes not yet stored to the backend
(rf/reg-sub ::changes
  :<- [::data]
  :-> :changes)

(rf/reg-sub ::configs
  :<- [::data]
  :-> :configs)

(rf/reg-sub ::available
  :<- [::data]
  :-> :available)

(rf/reg-sub ::ids
  :<- [::configs]
  (fn [configs]
    (->> configs
         (sort-by (juxt :created-at :id))
         keys)))

(rf/reg-sub ::configs?
  :<- [::configs]
  (fn [configs]
    (boolean (seq configs))))

(rf/reg-sub ::available-targets?
  :<- [::available]
  (fn [available]
    (boolean (seq available))))

(rf/reg-sub ::available-integration-types
  :<- [::available]
  (fn [available]
    (->> available
         keys
         (sort-by (comp loc integration/integration-type->name))
         (into []))))

(rf/reg-sub ::available-targets-for
  (fn [[_ id]]
    [(rf/subscribe [::available])
     (rf/subscribe [::integration-type id])])
  (fn [[available integration-type]]
    (get available integration-type)))

(rf/reg-sub ::current-targets
  (fn [[_ id]]
    (rf/subscribe [::current-config id]))
  (fn [config]
    (->> config
         :targets
         (sort-by target->str))))

(rf/reg-sub ::all-targets-for
  (fn [[_ id]]
    [(rf/subscribe [::available-targets-for id])
     (rf/subscribe [::config-for id])])
  (fn [[available config]]
    (->> (set/union (set available) (-> config :targets set))
         (sort-by target->str))))

(rf/reg-sub ::targets-str
  (fn [[_ id]]
    (rf/subscribe [::config-for id]))
  (fn [config]
    (some->> config
             :targets
             seq
             (map target->str)
             sort
             (str/join ", "))))

(rf/reg-sub ::current-targets-or-default
  (fn [[_ id]]
    [(rf/subscribe [::current-config id])
     (rf/subscribe [::available-targets-for id])])
  (fn [[config available]]
    (or (-> config :targets)
        (->> available (take 1)))))

(rf/reg-sub ::config-for
  :<- [::configs]
  :<- [::available-integration-types]
  (fn [[configs types] [_ id]]
    (get configs id {:integration-type (first types)
                     :enabled?         false})))

(rf/reg-sub ::changes-for
  :<- [::changes]
  (fn [changes [_ id]]
    (get changes id)))

(defn- deep-merge
  [a b]
  (cond
    (map? a) (merge-with deep-merge a b)
    :else    b))

(rf/reg-sub ::current-config
  (fn [[_ id]]
    [(rf/subscribe [::config-for id])
     (rf/subscribe [::changes-for id])])
  (fn [[config changes]]
    (merge-with deep-merge config changes)))

(rf/reg-sub ::options
  (fn [[_ id]]
    (rf/subscribe [::current-config id]))
  :-> :options)

(rf/reg-sub ::integration-type
  (fn [[_ id]]
    (rf/subscribe [::current-config id]))
  :-> :integration-type)

(rf/reg-sub ::config-changed?
  (fn [[_ id]]
    [(rf/subscribe [::config-for id])
     (rf/subscribe [::current-config id])])
  (fn [[old-config new-config]]
    (not= old-config new-config)))

(rf/reg-sub ::config-valid?
  (fn [[_ id]]
    (rf/subscribe [::current-config id]))
  (fn [config]
    (integration/valid-config? config)))

(rf/reg-sub ::can-save?
  (fn [[_ id]]
    [(rf/subscribe [::config-changed? id])
     (rf/subscribe [::config-valid? id])])
  (fn [[changes? valid?]]
    (and changes? valid?)))

(rf/reg-sub ::can-enable?
  (fn [[_ id]]
    [(rf/subscribe [::config-valid? id])
     (rf/subscribe [::config-for id])])
  (fn [[valid? config]]
    (and valid?
         (-> config :targets seq))))

(rf/reg-sub ::section-visible?
  :<- [::configs?]
  :<- [::available-targets?]
  (fn [[configs? available?]]
    (or configs? available?)))

;;; General implementation editor

(defn select-single-target
  "Selector for a single target"
  [id {:keys [read-only?]}]
  (let [available-targets (<sub [::available-targets-for id])]
    [components/select-with-label
     {:id            "select-targets"
      :label-text    (loc :auth-admin.integration.target)
      :disabled?     read-only?
      :allow-empty?  true
      :value         (first (<sub [::current-targets id]))
      :options       (if read-only?
                       (<sub [::current-targets id])
                       available-targets)
      :option->text  target->str
      :option->value target->str
      :on-change     (fn [target]
                       (>evt [::new-target-selected id target]))}]))

(defn select-multiple-targets
  "Selector for multiple targets"
  [id {:keys []}]
  (let [current-targets (set (<sub [::current-targets id]))
        all-targets     (<sub [::all-targets-for id])]
    [components/checkbox-group
     {:label            (loc :auth-admin.integration.targets)
      :options          all-targets
      :option->id       target->str
      :option->label    target->str
      :option->checked? #(contains? current-targets %)
      :on-change        #(>evt [::target-toggled id %])}]))

(defn select-targets
  [integration-type id]
  (if (integration/integration-type->single-target? integration-type)
    [select-single-target id {:read-only? (not= id :new)}]
    [select-multiple-targets id {:read-only? false}]))

(defn- select-integration-type
  [id]
  (let [read-only? (not= id :new)
        value      (<sub [::integration-type id])]
    [components/select-with-label
     {:id            "select-integration-type"
      :label-text    (loc :auth-admin.integration.type)
      :disabled?     read-only?
      :value         value
      :options       (if read-only?
                       [value]
                       (<sub [::available-integration-types]))
      :option->value name
      :option->text  (comp loc integration/integration-type->name)
      :on-change     (fn [integration-type]
                       (>evt [::integration-type-selected id integration-type]))}]))

;; Editors for individual options (per type)

(defmulti edit-option
  "Editor components for different types of options"
  (fn [option _id]
    (:option/type option)))

(defmethod edit-option :option/text
  [{:option/keys [name label placeholder optional?]} id]
  (let [options (<sub [::options id])]
    [components/text-with-label
     {:id          (str "option-text-" name)
      :label-text  (loc label)
      :placeholder (loc (or placeholder label))
      :value       (get options name)
      :on-change   (fn [v] (>evt [::option-changed id name v]))
      :required?   (not optional?)}]))

(defmethod edit-option :option/password
  [{:option/keys [name label placeholder optional?]} id]
  (let [options (<sub [::options id])]
    [components/password-with-label
     {:id          (str "option-password-" name)
      :label-text  (loc label)
      :placeholder (loc (or placeholder label))
      :value       (get options name)
      :on-change   (fn [v] (>evt [::option-changed id name v]))
      :required?   (not optional?)}]))

(defn- options-editor
  [selected-type id]
  (let [options (integration/integration-type->options selected-type)]
    [:<>
     (for [{:option/keys [name] :as option} options]
       ^{:key name} [edit-option option id])]))

(defn- integration-dialog
  [id]
  (let [selected-type  (<sub [::integration-type id])
        current-config (<sub [::current-config id])]
    [:div
     [:div.flex.flex--column.flex--gap1
      [select-integration-type id]
      [select-targets selected-type id]
      [options-editor selected-type id]]
     [:div.button-group
      [:div.flex.flex--right.flex--gap2
       [:button.primary
        {:disabled (not (<sub [::can-save? id]))
         :on-click #(>evt [::save-button-clicked current-config])}
        (loc :save)]
       [:button.cancel.ghost
        {:on-click #(>evt [::cancel-button-clicked])}
        (loc :button.cancel)]]]]))

;;; List view

(defn- row
  [id]
  (let [{:keys [enabled? integration-type] :as config} (<sub [::config-for id])]
    [:tr
     [:td (loc (integration/integration-type->name integration-type)) ]
     [:td.width-60
      (or (<sub [::targets-str id])
          [:em (loc :auth-admin.integration.no-targets)])]
     [:td [:span.indicator-wrapper
           [components/checkbox {:id        (str "enable-" id)
                                 :value     enabled?
                                 :disabled? (not (<sub [::can-enable? id]))
                                 :on-change (fn [enabled?]
                                              (>evt [::on-enabled-clicked config enabled?]))}]]]
     [:td [components/link {:text     (loc :edit)
                            :on-click #(>evt [::edit-link-clicked id])}]]]))

(defn- rows
  []
  [:tbody
   (doall
     (for [id (<sub [::ids])]
       ^{:key id} [row id]))])

(defn- add-new-integration-controls
  []
  [:button.primary
   {:on-click #(>evt [::add-button-clicked])}
   (loc :auth-admin.integration.add)])

(defn view
  []
  (when (<sub [::section-visible?])
    [:section
     [:h2 (loc :auth-admin.integration.list)]
     [:table
      [:thead
       [:tr
        [:th (loc :auth-admin.integration.type)]
        [:th (loc :auth-admin.integration.targets)]
        [:th (loc :auth-admin.integration.enabled)]
        [:th (loc :auth-admin.actions)]]]
      [rows]]
     (when (<sub [::available-targets?])
       [add-new-integration-controls])]))
