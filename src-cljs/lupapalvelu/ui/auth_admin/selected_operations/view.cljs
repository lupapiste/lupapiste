(ns lupapalvelu.ui.auth-admin.selected-operations.view
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.auth-admin.org-subs :as org-subs]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [sade.util :as util]))

(defn- query-selectable-verdict-templates
  [_ [_ org-id]]
  {:fx [[:action/query {:name    :selectable-verdict-templates
                        :params  {:organizationId org-id}
                        :success [::selectable-verdict-templates.success]
                        :error   [::selectable-verdict-templates.error]}]]})

(defn- query-default-operation-verdict-templates
  [_ [_ org-id]]
  {:fx [[:action/query {:name    :default-operation-verdict-templates
                        :params  {:organizationId org-id}
                        :success [::default-operation-verdict-templates.success]
                        :error   [::default-operation-verdict-templates.error]}]]})

(rf/reg-event-fx ::query-default-operation-verdict-templates
  query-default-operation-verdict-templates)

(rf/reg-event-fx ::selectable-verdict-templates.success
  (fn [{:keys [db]} [_ {:keys [items]}]]
    {:db (assoc-in db [::data :verdict-templates] items)}))

(rf/reg-event-fx ::selectable-verdict-templates.error
  (fn [{:keys [db]} [_ {:keys [text]}]]
    ;; The `selectable-verdict-templates` query will fail if Pate is disabled -> ignore
    (if (= text "error.pate-disabled")
      {:db (assoc-in db [::data :verdict-templates] {})}
      {:dialog/show {:ltitle :error.dialog.title
                     :ltext  text}})))

(rf/reg-event-fx ::default-operation-verdict-templates.success
  (fn [{:keys [db]} [_ {:keys [templates]}]]
    {:db (assoc-in db [::data :default-templates] templates)}))

(rf/reg-event-fx ::default-operation-verdict-templates.error
  (fn [{:keys [db]} [_ {:keys [text]}]]
    ;; The `default-operation-verdict-templates` query will fail if Pate is disabled -> ignore
    (if (= text "error.pate-disabled")
      {:db (assoc-in db [::data :default-templates] {})}
      {:dialog/show {:ltitle :error.dialog.title
                     :ltext  text}})))

(defn- set-visible [{:keys [db]} _]
  {:db (assoc-in db [::data :visible?] true)})

(defn- cleanup [{:keys [db]}]
  {:db (dissoc db ::data)})

(rf/reg-event-fx ::toggle-view
  [(rf/inject-cofx ::org-subs/url-org-id)]
  (fn [cofx [_ visible?]]
    (let [org-id (::org-subs/url-org-id cofx)]
      (if visible?
        (nutil/merge-every-fn cofx [[set-visible]
                                    [org-subs/set-org-id org-id]
                                    ;; Ideally we'd like to avoid `force?` here, but there are a bunch
                                    ;; of knockout views, which may toggle various settings (e.g. section enabled)
                                    ;; and we have no way to know if our cached data is still valid
                                    [org-subs/fetch-org {:org-id org-id
                                                         :force? true}]
                                    [query-selectable-verdict-templates org-id]
                                    [query-default-operation-verdict-templates org-id]
                                    ;; If user uses the knockout model to change operations,
                                    ;; refresh our state
                                    [hub/subscribe-fx
                                     "organization-selected-operations"
                                     (fn [] (>evt [::refresh]))
                                     ::hub-subscription]])
        (nutil/merge-every-fn cofx [[cleanup]
                                    [hub/unsubscribe-fx ::hub-subscription]])))))

(rf/reg-event-fx ::refresh
  (fn [{:keys [db] :as cofx} _]
    (let [org-id (org-subs/db->org-id db)]
      (nutil/merge-every-fn cofx [[org-subs/fetch-org {:org-id org-id
                                                       :force? true}]
                                  [query-selectable-verdict-templates org-id]
                                  [query-default-operation-verdict-templates org-id]]))))

(rf/reg-sub ::permit-types
  :<- [::org-subs/selected-operations]
  (fn [m]
    (->> (keys m)
         (map (fn [permit-type]
                {:permit-type permit-type :text (common/loc permit-type)}))
         (sort-by :text)
         (into []))))

(rf/reg-sub ::operations-by-permit-type
  :<- [::org-subs/selected-operations]
  (fn [m [_ permit-type]]
    (->> (get m permit-type)
         (map (fn [op]
                {:id op :text (common/loc (str "operations." op)) :permit-type permit-type}))
         (sort-by :text)
         (into []))))

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::verdict-templates
  :<- [::data]
  :-> :verdict-templates)

(rf/reg-sub ::verdict-templates-for
  :<- [::verdict-templates]
  (fn [templates [_ permit-type-or-op]]
    (get templates (keyword permit-type-or-op))))

(rf/reg-sub ::verdict-templates-supported?
  :<- [::verdict-templates]
  (fn [templates [_ permit-type]]
    (util/includes-as-kw? (keys templates) permit-type)))

(rf/reg-sub ::default-templates
  :<- [::data]
  :-> :default-templates)

(rf/reg-sub ::default-template-for
  :<- [::default-templates]
  (fn [templates [_ op]]
    (get templates (keyword op))))

(rf/reg-sub ::visible?
  :<- [::data]
  :-> (comp boolean :visible?))

(rf/reg-event-fx ::set-section-enabled
  (fn [{:keys [db]} [_ operation-id enabled?]]
    (let [org-id (org-subs/db->org-id db)]
      {:fx [[:action/command {:name    :section-toggle-operation
                              :params  {:organizationId org-id
                                        :operationId    operation-id
                                        :flag           enabled?}
                              :success (fn [_]
                                         (>evt [::org-subs/fetch-org {:org-id org-id :force? true}]))}]]})))

(rf/reg-event-fx ::set-suti-enabled
  (fn [{:keys [db]} [_ operation-id enabled?]]
    (let [org-id (org-subs/db->org-id db)]
      {:fx [[:action/command {:name    :suti-toggle-operation
                              :params  {:organizationId org-id
                                        :operationId    operation-id
                                        :flag           enabled?}
                              :success (fn [_]
                                         (>evt [::org-subs/fetch-org {:org-id org-id :force? true}]))}]]})))

(rf/reg-event-fx ::set-default-operation-verdict-template
  (fn [{:keys [db]} [_ operation-id template-id]]
    (let [org-id (org-subs/db->org-id db)]
      {:fx [[:action/command {:name    :set-default-operation-verdict-template
                              :params  {:organizationId org-id
                                        :operation      operation-id
                                        :template-id    template-id}
                              :success (fn [_]
                                         (>evt [::query-default-operation-verdict-templates org-id]))}]]})))

(rf/reg-sub ::verdict-template-selections
  (fn [[_ permit-type operation-id]]
    [(rf/subscribe [::verdict-templates-for permit-type])
     (rf/subscribe [::verdict-templates-for operation-id])])
  (fn [[for-permit for-operation]]
    (->> (concat for-permit for-operation)
         (map (fn [{:keys [id name]}]
                [id name]))
         (sort-by second)
         (cons ["" (common/loc :notSelected)])
         (into []))))

(defn- default-verdict-template-cell
  [operation-id permit-type]
  [:td
   (when (and (<sub [::org-subs/pate-enabled? permit-type])
              (<sub [::verdict-templates-supported? permit-type]))
     (when-let [templates (seq (<sub [::verdict-template-selections permit-type operation-id]))]
       (if (next templates) ; ignore the `:notSelected` choice
         (components/select
           (or (<sub [::default-template-for operation-id]) "")
           templates
           {:disabled (not (<sub [:auth/global? :set-default-operation-verdict-template]))
            :callback (fn [v] (>evt [::set-default-operation-verdict-template operation-id v]))})
         [:span (common/loc :pate.no-operation-templates)])))])

(rf/reg-sub ::inspection-templates
  (fn [[_ op]]
    (rf/subscribe [::org-subs/operations-templates-for-op op]))
  (fn [templates]
    (->> templates
         (map :name)
         sort
         (into []))))

(defn- list-inspection-templates-cell
  "Table cell for listing the inspection summary templates (tarkastusasiakirjapohjat) per operation"
  [operation-id permit-type]
  [:td
   (when (<sub [:auth/global? :organization-inspection-summary-settings])
     (when (= :R permit-type)
       (->> (<sub [::inspection-templates operation-id])
            (map (fn [template-name]
                   [:li template-name]))
            (into [:ul {:style {:list-style-type :none
                                :text-align      :left}}]))))])

(defn- section-cell
  "Table cell for toggling the section flag (pykälä) per operation"
  [operation-id]
  (when (<sub [::org-subs/verdict-section-enabled?])
    [:td.sot--center
     (let [cb-id (str "section-" operation-id)]
       [:div.sectionbox-wrapper
        [ui-input/checkbox
         {:id        cb-id
          :checked?  (<sub [::org-subs/verdict-section-enabled-for-op? operation-id])
          :disabled? (not (<sub [:auth/global? :section-toggle-operation]))
          :on-change (fn [checked?] (>evt [::set-section-enabled operation-id checked?]))}]
        [:label.sectionbox-label {:for cb-id}]])]))

(defn- suti-cell
  "Table cell for toggling the suti flag (suunnittelun lähtötiedot, rakennuslupakartta) per operation"
  [operation-id]
  (when (<sub [::org-subs/suti-enabled?])
    [:td.sot--center
     (let [cb-id (str "suti-" operation-id)]
       [:div.checkbox-wrapper
        [ui-input/checkbox
         {:id        cb-id
          :checked?  (<sub [::org-subs/suti-enabled-for-op? operation-id])
          :disabled? (not (<sub [:auth/global? :suti-toggle-operation]))
          :on-change (fn [checked?] (>evt [::set-suti-enabled operation-id checked?]))}]
        [:label.checkbox-label {:for cb-id}]])]))

(defn- operations-table-section-rows
  [{operation-id :id
    :keys        [text permit-type]}]
  [:tbody
   [:tr.sel-op-row
    [:td text]
    [default-verdict-template-cell operation-id permit-type]
    [list-inspection-templates-cell operation-id permit-type]
    [section-cell operation-id]
    [suti-cell operation-id]]])

(defn- operations-table-section-headers
  "Table headers for a particular permit type"
  [{:keys [permit-type text]}]
  [:thead
   [:tr
    [:th.sot--full-width text]
    [:th (when (and (<sub [::org-subs/pate-enabled? permit-type])
                    (<sub [::verdict-templates-supported? permit-type]))
           [:span (common/loc :pate-verdict-template)])]
    [:th (when (and (<sub [:auth/global? :organization-inspection-summary-settings])
                    (= :R permit-type))
           [:span (common/loc :auth-admin.inspection-summary-templates)])]
    (when (<sub [::org-subs/verdict-section-enabled?])
      [:th (common/loc :section.column)])
    (when (<sub [::org-subs/suti-enabled?])
      [:th (common/loc :suti.column)])]])

(defn- operations-table-section
  "Each permit type gets their own section in the table"
  [{:keys [permit-type] :as item}]
  (into [:<>
         [operations-table-section-headers item]]
        (->> (<sub [::operations-by-permit-type permit-type])
             (mapv (fn [op-item]
                     [operations-table-section-rows op-item])))))

(defn- operations-table
  []
  (into [:table.selected-operations-table]
        (->> (<sub [::permit-types])
             (mapv (fn [item]
                     [operations-table-section item])))))

(defn- view
  []
  (when (<sub [::visible?])
    [:<>
     [:h2 (common/loc :auth-admin.selected-operations)]
     [operations-table]]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
