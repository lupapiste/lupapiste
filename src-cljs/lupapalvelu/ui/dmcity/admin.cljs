(ns lupapalvelu.ui.dmcity.admin
  "Container component for Admin admin dmcity configuration and batchrun functionality."
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.pate.path :refer [loc]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dmcity.batchrun :as dm-run]
            [lupapalvelu.ui.dmcity.shared :as dm-sh]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.shared-schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(def ToggleField
  (sc/conditional
    :url {:enabled sc/Bool :url ssc/NonBlankStr}
    :else {:enabled (sc/eq false)}))

(def Config {:url      ssc/NonBlankStr
             :username ssc/NonBlankStr
             :password ssc/NonBlankStr
             :vault    ssc/NonBlankStr
             :targets  (-> (conj dm-sh/permit-types :state :conversion :link)
                           (zipmap (repeat sc/Bool))
                           (assoc :building   ToggleField
                                  :address    ToggleField)
                           st/optional-keys)})

(def check-config (sc/checker Config))

(defn- cfg->view
  "Fetched configuration into a more conveniently viewable format."
  [{:keys [targets] :as cfg} permit-types]
  (let [flag-ks (conj permit-types :state :conversion :link)]
    {:text-fields   (select-keys cfg [:username :password :vault :url])
     :flags         (merge (zipmap flag-ks (repeat false))
                           (select-keys targets flag-ks))
     :toggle-fields (select-keys targets [:building :address])}))

(defn- view->cfg
  "Valid configuration or nil"
  [{:keys [text-fields flags toggle-fields]}]
  (let [cfg (merge text-fields
                   {:targets (->> toggle-fields
                                  (keep (fn [[k {:keys [enabled url] :as v}]]
                                          (when-not (and (not enabled) (ss/blank? url))
                                            [k v])))
                                  (into flags))})]
    (when-not (check-config cfg)
      cfg)))

;; ---------------------------
;; Events
;; ---------------------------

(rf/reg-event-db
  ::fetch-configuration.success
  (fn [db [_ {:keys [configuration permit-types]}]]
    (dm-sh/assoc-data db
                :configuration configuration
                :permit-types (map keyword permit-types))))

(rf/reg-event-fx
  ::fetch-configuration
  (fn [{db :db} _]
    {:action/query {:name    :dmcity-configuration
                    :params  {:organizationId (dm-sh/get-data db :organization-id)}
                    :success [::fetch-configuration.success]}}))

(rf/reg-event-fx
  ::reset-organization-id
  (fn [{db :db} [_ organization-id]]
    {:db       (dm-sh/reset-data db :organization-id organization-id)
     :dispatch [::fetch-configuration]}))

(rf/reg-event-db
  ::edit-configuration
  (fn [db _]
    (dm-sh/assoc-data db :configuration-edit (cfg->view (dm-sh/get-data db :configuration)
                                                        (dm-sh/get-data db :permit-types)))))

(rf/reg-event-fx
  ::save-configuration.success
  (fn [{db :db} _]
    {:db (dm-sh/dissoc-data db :configuration-edit)
     :dispatch [::fetch-configuration]}))

(rf/reg-event-fx
  ::save-configuration
  (fn [{db :db} _]
    (let [{:keys [configuration-edit
                  organization-id]} (dm-sh/get-data db)
          cfg                       (view->cfg configuration-edit)]
      {:action/command {:name                  :upsert-dmcity-configuration
                        :params                {:organizationId organization-id
                                                :configuration  cfg}
                        :show-saved-indicator? true
                        :success               [::save-configuration.success]}})))

(rf/reg-event-db
  ::cancel-configuration-edit
  (fn [db _]
    (dm-sh/dissoc-data db :configuration-edit)))

(rf/reg-event-db
  ::edit-field
  (fn [db [_ & xs]]
    (let [path  (drop-last xs)
          value (last xs)]
      (dm-sh/update-data db assoc-in (cons :configuration-edit path) value))))

(rf/reg-event-db
  ::toggle-show-password
  (fn [db [_ show?]]
    (dm-sh/assoc-data db :show-password? show?)))

(rf/reg-event-fx
  ::delete-configuration
  (fn [{db :db}]
    (when-let [org-id (dm-sh/get-data db :organization-id)]
      {:action/command {:name    :delete-dmcity-configuration
                        :params  {:organizationId org-id}
                        :success #(>evt [::reset-organization-id org-id])}})))

;; ---------------------------
;; Subs
;; ---------------------------

(rf/reg-sub
  ::configuration
  :-> (dm-sh/access :configuration))

(rf/reg-sub
  ::permit-types
  :-> (dm-sh/access :permit-types))

(rf/reg-sub
  ::configuration-view
  :<- [::configuration]
  :<- [::permit-types]
  (fn [[cfg permit-types] _]
    (some-> cfg (cfg->view permit-types))))

(rf/reg-sub
  ::configuration-edit
  :-> (dm-sh/access :configuration-edit))

(rf/reg-sub
  ::active-configuration
  :<- [::configuration-view]
  :<- [::configuration-edit]
  (fn [[cfg cfg-edit]]
    (or cfg-edit cfg)))

(rf/reg-sub
  ::active-configuration?
  :<- [::active-configuration]
  :-> some?)

(rf/reg-sub
  ::editing?
  :<- [::configuration-edit]
  :-> some?)

(rf/reg-sub
  ::show-password?
  :-> (dm-sh/access :show-password?))

(rf/reg-sub
  ::can-save?
  :<- [::configuration-edit]
  (fn [edit-cfg _]
    (some? (view->cfg edit-cfg))))

(rf/reg-sub
  ::text-row-options
  :<- [::dm-sh/organization-id]
  :<- [::active-configuration]
  :<- [::editing?]
  :<- [::show-password?]
  (fn [[org-id cfg editing? show-pw?] [_ text-key]]
    (let [masked? (and (= text-key :password) (not show-pw?))
          v       (if (and masked? (not editing?))
                    "*************"
                    (get-in cfg [:text-fields text-key]))]
      {:id         (name (util/kw-path :dmcity org-id text-key))
       :value      v
       :editing?   editing?
       :input-type (if masked? "password" "text")})))

(rf/reg-sub
  ::flags
  :<- [::active-configuration]
  :-> :flags)

(rf/reg-sub
  ::toggle-field
  :<- [::active-configuration]
  (fn [cfg [_ field-key]]
    (get-in cfg [:toggle-fields field-key])))

;; ---------------------------
;; Components
;; ---------------------------

(defn- text-row
  [k]
  (let [{:keys [id value editing?
                input-type]} (<sub [::text-row-options k])]
    [:div.tabby__row
     [:div.tabby__cell
      [:label.lux.required {:for id} (loc :dmcity k)]]
     [:div.tabby__cell
      (if editing?
        [components/text-edit
         value
         {:required? true
          :id        id
          :type      input-type
          :callback  #(>evt [::edit-field :text-fields k %])}]
        value)]]))

(defn- usecases-row []
  (let [flags    (<sub [::flags])
        editing? (<sub [::editing?])]
    [:div.tabby__row
     [:div.tabby__cell
      [:label.lux (loc :dmcity.usecases)]]
     [:div.tabby__cell
      (if editing?
        (->> flags
             (map (fn [[k v]]
                    [components/toggle
                     v
                     {:text     (loc (dm-sh/flag-loc k))
                      :callback #(>evt[::edit-field :flags k %])}]))
             (sort-by #(:text (nth % 2)))
             (into [:<>]))
        (->> flags
             (keep (fn [[k v]]
                     (when v
                       [:li (-> k dm-sh/flag-loc loc)])))
             (sort-by second)
             (into [:ul])))]]))

(defn- toggle-row [k]
  (let [{:keys [enabled url]} (<sub [::toggle-field k])
        editing?              (<sub [::editing?])]
    [:div.tabby__row
     [:div.tabby__cell
      [:label.lux (-> k dm-sh/flag-loc loc)]]
     [:div.tabby__cell
      (cond
        editing? [:div.flex--column
                  [components/toggle
                   enabled
                   {:text-loc :auth-admin.active
                    :callback #(>evt [::edit-field :toggle-fields
                                      k :enabled %])}]
                  [components/text-edit
                   url
                   {:required? true
                    :disabled  (not enabled)
                    :callback  #(>evt [::edit-field :toggle-fields
                                       k :url %])}]]

        enabled url

        :else [:em (loc :dmcity.disabled)])]]))

(defn configuration-editor []
  (let [editing? (<sub [::editing?])
        show-pw? (<sub [::show-password?])]
    [:div.flex--column.flex--row-gap1
     [:div.dictionary.no-gap
      [:div.tabby
       [text-row :url]
       [text-row :username]
       [text-row :password]
       [:div.tabby__row
        [:div.tabby__cell]
        [:div.tabby__cell.w--80
         [components/toggle
          show-pw?
          {:text-loc :a11y.show-password
           :callback #(>evt [::toggle-show-password %])}]]]
       [text-row :vault]
       [usecases-row]
       [toggle-row :building]
       [toggle-row :address]]]
     (if editing?
       [:div.flex.flex--gap2
        [components/icon-button {:text-loc :cancel
                                 :class    :tertiary
                                 :on-click #(>evt [::cancel-configuration-edit])}]
        [components/icon-button {:text-loc :save
                                 :icon     :lupicon-save
                                 :enabled? (boolean (<sub [::can-save?]))
                                 :class    :primary
                                 :on-click #(>evt [::save-configuration])}]]
       [:div.flex.flex--gap2
        [components/icon-button {:text-loc :edit
                                 :icon     :lupicon-pen
                                 :class    :primary
                                 :on-click #(>evt [::edit-configuration])}]
        [components/icon-button {:text-loc :remove
                                 :icon     :lupicon-trash
                                 :class    :danger
                                 :on-click #(>evt [:dialog/show
                                                   {:text-loc :dmcity.delete-confirmation
                                                    :type :yes-no
                                                    :event [::delete-configuration]}])}]])]))

(defn view []
  (r/with-let [_ (>evt [::hub/subscribe :organization::open
                        #(>evt [::reset-organization-id (:organizationId %)])
                        ::open-hub-id])
               _ (>evt [::hub/subscribe :organization::scope-added
                        #(>evt [::fetch-organization-id])
                        ::scope-hub-id])]
    (let [cfg?     (<sub [::active-configuration?])
          editing? (<sub [::editing?])]
      [:div.gap--v6.flex--column.flex--row-gap2
       [:h2.h2 (loc (<sub [::dm-sh/dmcity-product-loc]))]
       (if cfg?
         [:div.flex--column.flex--row-gap4
          [configuration-editor]
          (when-not editing?
            [dm-run/view])]
         [components/icon-button
          {:text-loc :dmcity.new-configuration
           :icon     :lupicon-circle-plus
           :class    :primary.flex--self-start
           :on-click #(>evt [::edit-configuration])}])])
    (finally
      (>evt [::hub/unsubscribe ::open-hub-id ::scope-hub-id])
      (>evt [::dm-sh/clear-data]))))

(defonce mounter (ui-dom/init :reagent (constantly view)))

(defn ^:export start [dom-id params]
  (>evt [::reset-organization-id (common/->cljs (common/oget params "organizationId"))])
  (ui-dom/mount! mounter dom-id))
