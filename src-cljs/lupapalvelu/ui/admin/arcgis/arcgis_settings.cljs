(ns lupapalvelu.ui.admin.arcgis.arcgis-settings
  "Lupapiste Admin user interface for configuring an organization's ArcGIS integration"
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- process-layer-config
  "Parses number list strings into number arrays"
  [{:keys [layer-ids] :as layer-config}]
  (cond-> layer-config
    (string? layer-ids)
    (assoc :layer-ids (->> layer-ids (re-seq #"\d+") (mapv util/->int)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Subs

(rf/reg-sub ::unedited-config :-> ::unedited-config)

(rf/reg-sub ::config :-> ::config)

(rf/reg-sub ::layer-count #(-> % ::config :layers count))

(rf/reg-sub ::has-changes?
  :<- [::config]
  :<- [::unedited-config]
  (fn [[config unedited-config]]
    (not= config unedited-config)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Events

(rf/reg-event-fx ::fetch-config
  (fn [{:keys [db]} [_ org-id]]
    {:db           (assoc db ::org-id org-id)
     :action/query {:name    :arcgis-config
                    :params  {:organizationId org-id}
                    :success [::fetch-config.success]}}))

(rf/reg-event-db ::fetch-config.success
  (fn [db [_ {:keys [arcgis-config]}]]
    (assoc db ::config arcgis-config ::unedited-config arcgis-config)))

(rf/reg-event-db ::set-field
  (fn [db [_ path value]]
    (-> db
        (assoc-in (cons ::config path) value)
        (update ::config util/strip-blanks))))

(rf/reg-event-db ::add-layer
  (fn [db _]
    (update-in db [::config :layers] (comp vec conj) {}))) ; vec so assoc works in set-field

(rf/reg-event-db ::remove-layer
  (fn [db [_ idx]]
    (update-in db [::config :layers] #(util/drop-nth idx %))))

(rf/reg-event-db ::undo-changes
  (fn [db _]
    (assoc db ::config (::unedited-config db))))

(rf/reg-event-fx ::save-changes
  (fn [{{::keys [config org-id]} :db} _]
    {:action/command {:name    :save-arcgis-config
                      :params  {:organizationId org-id
                                :config         (update config :layers #(map process-layer-config %))}
                      :success #(>evt [::fetch-config org-id])}}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Views

(defn- header-row
  "A title row for a section of related field-rows with optional title suffix and button"
  [title-kw & [suffix button]]
  [:tr [:td.txt--bold.bg--dark-gray
        {:colSpan 2}
        (->> title-kw (util/kw-path :arcgis) common/loc)
        (when suffix (str " " suffix))
        (when button [:span.gap--2 button])]])

(defn- field-row
  "A row with a field's label and input.
  The label is by default localized using the path of the field but
  can be overridden with loc-kw or can ignore part of the path with shared-loc?"
  [path & {:keys [shared-loc? loc-kw required? type input]}]
  [:tr
   [:td [:label.lux
         (when required? {:class "required"})
         (or (some-> loc-kw common/loc)
             (->> (if shared-loc?
                    [:arcgis (last path)]
                    (cons :arcgis path))
                  (remove number?) ; array indexes
                  util/kw-path
                  common/loc))]]
   [:td [(or input :input.lux)
         {:style       {:width "100%"}
          :placeholder (when (= type :password) "**********")
          :type        (or type :text)
          :value       (get-in (<sub [::config]) path)
          :on-change   #(>evt [::set-field path (cond-> %
                                                  (nil? input)     util/tval
                                                  (= type :number) util/->int)])}]]])

(defn- number-list-editor
  "Input for editing an array of numbers as text field (separated by whitespace or commas)"
  [{:keys [style value on-change]}]
  [:input.lux
   {:type      :text
    :style     style
    :value     (if (string? value) value (ss/join ", " value))
    :on-change #(-> % util/tval on-change)}])

(defn- loc-editor
  "Input for editing a map of localizations as text fields"
  [{:keys [value on-change]}]
  (->> (for [lang (common/supported-langs)]
         [:<>
          [:label.pad--h2 (name lang)]
          [:input.lux
           {:type     :text
            :value    (get value lang "")
            :on-change #(->> % util/tval (assoc value lang) on-change)}]])
       (into [:div.flex--row.flex--gap4])))

(defn- remove-button
  "Button for removing the configuration for one map layer"
  [idx]
  [components/icon-button
   {:class      :btn-small.tertiary
    :icon       :lupicon-trash
    :icon-only? true
    :on-click   #(>evt [::remove-layer idx])}])

(defn- layer-rows
  "Rows for configuring each map layer"
  []
  (->> (for [idx (range (<sub [::layer-count]))]
         [:<>
          [header-row :layers (inc idx) [remove-button idx]]
          [field-row [:layers idx :folder] :shared-loc? true]
          [field-row [:layers idx :service] :shared-loc? true]
          [field-row [:layers idx :service-type] :shared-loc? true]
          [field-row [:layers idx :layer-ids] :input number-list-editor :required? true]
          [field-row [:layers idx :title] :input loc-editor :required? true]])
       (into [:<>])))

(defn- config-buttons
  "Buttons for saving the config or canceling the changes (as well as adding new layers)"
  []
  [:div.flex--wrap.flex--gap4
   [components/icon-button
    {:class    :secondary
     :icon     :lupicon-circle-plus
     :on-click #(>evt [::add-layer])
     :text-loc :arcgis.layers.add}]
   (when (<sub [::has-changes?])
     [:div.flex--wrap.flex--gap1
      [:button.secondary {:on-click #(>evt [::undo-changes])} (common/loc :cancel)]
      [:button.primary {:on-click #(>evt [::save-changes])} (common/loc :save)]])])

(defn view
  "Organization ArcGIS integration for Lupapiste Admin.
  Divided into three sections; general server settings, point search settings and optional
  map layer settings (each layer with its own section)"
  []
  (r/with-let [_ (>evt [::hub/subscribe "organization::open" #(>evt [::fetch-config (:organizationId %)])])]
     [:div.gap--v6
      [:h2 (common/loc :arcgis.config)]
      [:table
       [:tbody
        [header-row :server]
        [field-row [:server :username]]
        [field-row [:server :password] :type :password]
        [field-row [:server :service-url] :required? true]
        [field-row [:server :token-url]]
        [header-row :search]
        [field-row [:search :folder] :shared-loc? true]
        [field-row [:search :service] :shared-loc? true]
        [field-row [:search :service-type] :shared-loc? true]
        [field-row [:search :layer-id] :type :number :required? true]
        [header-row :search.fields]
        [field-row [:search :fields :feature-id] :loc-kw :map.feature_information.feature_id :required? true]
        [field-row [:search :fields :property-id] :loc-kw :map.feature_information.property_id]
        [layer-rows]]]
      [config-buttons]]))

(defonce mounter (ui-dom/init :reagent (constantly view)))

(defn ^:export start [dom-id params]
  (>evt [::fetch-config (-> params (common/oget "organizationId") common/->cljs)])
  (ui-dom/mount! mounter dom-id))
