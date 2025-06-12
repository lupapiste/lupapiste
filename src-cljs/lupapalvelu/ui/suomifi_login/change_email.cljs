(ns lupapalvelu.ui.suomifi-login.change-email
  "Authenticated user wishes to change the email of one of their Lupapiste accounts"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.router-api :as router]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.misc :as misc]
            [lupapalvelu.ui.suomifi-login.subs :as subs]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(defmethod router/route-init-fn :suomifi-change-email
  [_]
  (fn [{:keys [db]} [_ visible?]]
    (if visible?
      (let [user-id (-> db router/db->path-parameters :user-id)]
        {:db (assoc db ::data {:visible? true})
         :fx [(when-not (subs/users? db)
                [:dispatch [::subs/fetch-users
                            {:on-success (fn [{:keys [accounts]}]
                                           (when-not (util/find-by-id user-id accounts)
                                             (rf/dispatch [:route/push :suomifi-error])))}]])]})
      {:db (update db ::data #(select-keys % [:changed-emails]))})))

(rf/reg-sub ::data
  :-> ::data)

(rf/reg-sub ::visible?
  :<- [::data]
  :-> (comp boolean :visible?))

(rf/reg-sub ::pending?
  :<- [::data]
  :-> (comp boolean :pending?))

(rf/reg-sub ::finished?
  :<- [::data]
  :-> (comp boolean :finished?))

(rf/reg-sub ::user-id
  :<- [:route/path-parameters]
  :-> :user-id)

(rf/reg-sub ::user
  :<- [::user-id]
  :<- [::subs/users]
  (fn [[user-id users]]
    (util/find-by-id user-id users)))

(rf/reg-sub ::current-email
  :<- [::user]
  :-> :email)

(rf/reg-event-fx ::email-edited
  (fn [{:keys [db]} [_ email]]
    (if-let [trimmed (-> email ss/trim ss/blank-as-nil)]
      {:db (assoc-in db [::data :email] trimmed)}
      {:db (update db ::data dissoc :email)})))

(rf/reg-sub ::new-email
  :<- [::data]
  :-> :email)

(rf/reg-sub ::disabled?
  :<- [::pending?]
  :<- [::new-email]
  (fn [[pending? new-email]]
    (or pending? (nil? new-email))))

(rf/reg-event-fx ::cancel
  (fn [_ _]
    {::misc/navigate-back true}))

(rf/reg-sub ::changed-emails
  :<- [::data]
  (fn [{:keys [changed-emails]}]
    (or changed-emails #{})))

(rf/reg-event-fx ::submit.success
  (fn [{:keys [db]} [_ _resp old-email]]
    {:db (-> db
             ;; Keep track of which accounts have pending requests
             ;; for a new email
             (update-in [::data :changed-emails] (fnil conj #{}) old-email)
             (assoc-in [::data :finished?] true)
             (assoc-in [::data :pending?] false))}))

(rf/reg-event-fx ::submit.failure
  (fn [{:keys [db]} [_ {:keys [text]}]]
    {:db          (assoc-in db [::data :pending?] false)
     :dialog/show {:ltitle :error.dialog.title
                   :ltext  text}}))

(rf/reg-event-fx ::submit
  (fn [{:keys [db]} [_ {:keys [user-id old-email email]}]]
    {:db             (assoc-in db [::data :pending?] true)
     :action/command {:name                  :change-email-init-suomifi
                      :params                {:userId user-id
                                              :email  email}
                      :show-saved-indicator? true
                      :success               [::submit.success old-email]
                      :error                 ::submit.failure}}))

(defn- submit
  []
  (let [user-id   (<sub [::user-id])
        old-email (<sub [::current-email])
        email     (<sub [::new-email])]
    [:button.primary
     {:disabled (<sub [::disabled?])
      :on-click #(>evt [::submit {:email     email
                                  :old-email old-email
                                  :user-id   user-id}])}
     (common/loc :userinfo.change-email-submit)]))

(defn- back-button
  [label-kw]
  [:button.secondary
   {:on-click #(>evt [::cancel])}
   (common/loc label-kw)])

(defn- input
  [{:keys [id label value error-id error-message on-change disabled? required?]}]
  (let [valid? (nil? error-message)]
    [:div.col-2
     [:div.field--vertical
      [:label.lux.field--block
       {:for   id
        :class [(when (and required? (nil? (ss/blank-as-nil value)))
                  "required")]}
       label]
      [:input.lux.field--block
       {:id           id
        :type         :text
        :defaultValue value
        :disabled     disabled?
        :on-change    (fn [e]
                        (on-change (util/tval e)))}]
      (when (not valid?)
        [:div.error-note.field--block
         {:id   error-id
          :role "alert"}
         error-message])]]))

(defn- success
  []
  [:div.flex-col
   [:p.flex.flex--gap1
    [:i.lupicon-check.positive {:aria-hidden true}]
    (common/loc :userinfo.change-email-proceed)]
   [back-button :back]])

(defn view
  []
  (when (<sub [::visible?])
    [:<>
     [:h1 (common/loc :userinfo.change-email)]
     [:p (common/loc :userinfo.change-email-info.a)]
     [:p
      [:strong (common/loc :userinfo.change-email-info.b)]
      " " (common/loc :userinfo.change-email-info.c)]
     [:div.form-grid.no-padding
      [:div.row
       [input {:id        "old-email"
               :label     (common/loc :userinfo.current-email)
               :value     (<sub [::current-email])
               :disabled? true}]
       [input {:id        "new-email"
               :label     (common/loc :userinfo.new-email)
               :value     (<sub [::new-email])
               :disabled? (<sub [::finished?])
               :on-change (fn [v]
                            (>evt [::email-edited v]))}]]]
     [:div.flex.flex--gap2
      (if (<sub [::finished?])
        [success]
        [:<>
         [submit]
         [back-button :button.cancel]])]]))
