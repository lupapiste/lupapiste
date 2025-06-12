(ns lupapalvelu.ui.docgen.component.company-invite-dialog
  "Displays a dialog for choosing a company to invite.
  The user first chooses a company and then separately submits the choice.
  See `company-invite-dialog-model.js`."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.reselect :as reselect]
            [re-frame.core :as rf]))

(defn- submit-button-group
  "See `submit-button-group` component."
  [{:keys [submit-visible?
           submit-enabled?
           submit-title
           on-submit
           on-cancel]}]
  [:div.button-group
   (when submit-visible?
     [:button.btn.btn-primary
      {:disabled (not submit-enabled?)
       :on-click  on-submit}
      (common/loc submit-title)])
   [:button.secondary.cancel
    {:on-click on-cancel}
    (common/loc :cancel)]])

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::phase :<- [::data] :-> :phase)
(rf/reg-sub ::pending? :<- [::data] :-> :pending?)
(rf/reg-sub ::selected-company-id :<- [::data] :-> :selected-company-id)

(rf/reg-sub ::selected-company
  :<- [::companies]
  :<- [::selected-company-id]
  (fn [[companies id]]
    (get companies id)))

(defn- cleanup-fx
  [{:keys [db]} [_]]
  {:db (dissoc db ::data)})

(defn- close-dialog-fx
  [_ [_ _]]
  {:fx [[:hub/send {:event :close-dialog}]]})

(rf/reg-event-fx ::dialog-closed
  (fn [cofx _]
    (nutil/merge-every-fn cofx [[cleanup-fx]
                                [close-dialog-fx]])))

(defn- cancel-callback
  [_]
  (>evt [::dialog-closed]))

(rf/reg-sub ::confirm-text
  :<- [::selected-company]
  (fn [company]
    (common/loc :company-invite-confirm.desc (:name company))))

(rf/reg-sub ::confirm-enabled?
  :<- [::pending?]
  :<- [::selected-company]
  (fn [[pending? company]]
    (and (not pending?)
         (some? company))))

(rf/reg-sub ::companies
  :<- [::data]
  :-> :companies)

(rf/reg-sub ::company-choices
  :<- [::companies]
  (fn [companies]
    (->> companies
         (map (fn [[id {company-name :name
                        :keys        [address1 po]}]]
                {:value id
                 :text  (str company-name
                             ", "
                             (some-> address1 (str " "))
                             po)}))
         (sort-by (juxt :text :value))
         (into []))))


(rf/reg-event-fx ::company-selected
  (fn [{:keys [db]} [_ id]]
    {:db (assoc-in db [::data :selected-company-id] id)}))

(defn- company-selector
  []
  [reselect/reselect
   (<sub [::selected-company-id])
   {:id       "company-invite-autocomplete"
    :multi?   false
    :items    (<sub [::company-choices])
    :callback (fn [id] (>evt [::company-selected id]))}])

;; Transition from `::select` to `::confirm`
(rf/reg-event-fx ::confirm-clicked
  (fn [{:keys [db]} _]
    {:db (assoc-in db [::data :phase] ::submit)}))

(defn- on-confirm
  "User has just selected a company and clicks 'confirm'."
  [_]
  (>evt [::confirm-clicked]))

(defn- select-view
  []
  [:<>
   [:p.dialog-desc (common/loc :company-invite.desc)]
   [company-selector]
   [submit-button-group {:submit-visible? true
                         :submit-enabled? (<sub [::confirm-enabled?])
                         :submit-title    :continue
                         :on-submit       on-confirm
                         :on-cancel       cancel-callback}]])

;; Submit choice and close dialog
(rf/reg-event-fx ::submit-clicked
  (fn [{:keys [db]} _]
    (let [{:keys [app-id selected-company-id]} (::data db)]
      {:db (assoc-in db [::data :pending?] true)
       :fx [[:action/command {:name   :company-invite
                              :params {:id         app-id
                                       :company-id selected-company-id}
                              :success [::invite.success app-id]}]]})))

(defn- reload-fx
  [_ [_ app-id]]
  {:fx [[:application/load app-id]]})

(rf/reg-event-fx ::invite.success
  (fn [cofx [_ _resp app-id]]
    (nutil/merge-every-fn cofx
                          [[reload-fx app-id]
                           [close-dialog-fx]
                           [cleanup-fx]])))

(defn- on-submit
  [_]
  (>evt [::submit-clicked]))

(defn- submit-view
  []
  [:<>
   [:p.dialog-desc (<sub [::confirm-text])]
   [submit-button-group {:submit-visible? true
                         :submit-enabled? (<sub [::confirm-enabled?])
                         :submit-title    :company-invite.invite
                         :on-submit       on-submit
                         :on-cancel       cancel-callback}]])

(defn- view
  []
  (when-let [phase (<sub [::phase])]
    [:div.content
     (case phase
       ;; First we find and choose the correct company
       ;; Pressing `confirm` will move us to the next phase
       ::select [select-view]
       ;; In the submit phase we confirm our choice and submit it
       ::submit [submit-view])]))

(rf/reg-event-fx ::query-companies.success
  (fn [{:keys [db]} [_ {:keys [companies]}]]
    {:db (assoc-in db [::data :companies] (->> companies
                                               (map (juxt :id identity))
                                               (into {})))}))

(defn show-invite-dialog-fx
  "Show dialog for inviting a new (or an existing) company

  * `app-id` -- Application ID"
  [{:keys [db]} [_ {:keys [app-id]}]]
  {:pre [(string? app-id)]}
  {:db (assoc db ::data {:app-id app-id
                         :phase  ::select})
   :fx [[:action/query {:name    :companies
                        :success [::query-companies.success]}]
        [:dialog/show {:ltitle           :company-invite.add
                       :size             :large
                       :type             :react
                       :dialog-component [view]
                       :minContentHeight "290px"
                       :cancel-callback  cancel-callback}]]})

(rf/reg-event-fx ::show-invite-dialog
  show-invite-dialog-fx)
