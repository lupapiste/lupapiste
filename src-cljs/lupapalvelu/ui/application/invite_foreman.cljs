(ns lupapalvelu.ui.application.invite-foreman
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.application.data :as data]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.strings :as ss]
            [sade.util :as util]))

(def task-data-service js/lupapisteApp.services.taskService)

(def other "select-other")

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::foreman-role
  :<- [::data]
  :-> :foreman-role)

(rf/reg-sub ::foreman-party
  :<- [::data]
  :-> :foreman-party)
;; sort documents by name and required on the top

(rf/reg-sub ::documents :-> ::documents)

(rf/reg-sub ::invite-email
  :<- [::data]
  :-> :invite-email)

(defn- valid-email?
  [email]
  (js/util.isValidEmailAddress email))

(rf/reg-sub ::email-invalid?
  :<- [::invite-email]
  (fn [email]
    (boolean (and (seq email)
                  (not (valid-email? email))))))

(rf/reg-sub ::invite-disabled
  (fn [{{:keys [foreman-role foreman-party invite-email]} ::data}]
    (let [valid? (and (ss/blank-as-nil foreman-role)
                      (or
                        ;; Inviting an existing party?
                        (and (ss/blank-as-nil foreman-party)
                             (not= foreman-party other))
                        ;; Inviting by email?
                        (and (= foreman-party other)
                             (ss/blank-as-nil invite-email)
                             (valid-email? invite-email))))]
      (not valid?))))

(rf/reg-event-db ::set-foreman-role
  (fn [db [_ value]]
    (assoc-in db [::data :foreman-role] value)))

(rf/reg-event-db ::set-foreman-role-and-task
  (fn [db [_ {:keys [selectedRole taskId]}]]
    (-> (assoc-in db [::data :foreman-role] selectedRole)
        (assoc-in [::data :task-id] taskId))))

(rf/reg-event-db ::set-foreman-party
  (fn [db [_ value]]
    (assoc-in db [::data :foreman-party] value)))

(rf/reg-event-db ::set-invite-email
  (fn [db [_ value]]
    (assoc-in db [::data :invite-email] value)))

(rf/reg-event-fx ::invite-foreman.success
  (fn [{:keys [db]} [_ app-id]]
    {:db (dissoc db ::data)
     :fx [[:application/load app-id]
          [:dialog/close]
          [:dispatch [::data/fetch-foreman-applications]]]}))

(rf/reg-event-fx ::invite-foreman
  (fn [{{{:keys [foreman-party foreman-role invite-email task-id]} ::data :as db} :db} _]
    (let [app-id (common/application-id)]
      {:action/command (-> (if (= other foreman-party)
                             {:name   :create-and-invite-foreman-by-email
                              :params (util/assoc-when {:id           app-id
                                                        :foremanRole  foreman-role
                                                        :foremanEmail invite-email}
                                                       :taskId task-id)}
                             {:name   :create-and-invite-foreman-by-id
                              :params (util/assoc-when {:id          app-id
                                                        :foremanRole foreman-role
                                                        :foremanUser foreman-party}
                                                       :taskId task-id)})
                           (assoc :success #(>evt [::invite-foreman.success app-id])))})))

(def required-suffix (str " (" (loc :foreman.task.required.label) ")"))

(defn option-text [req-foremen role]
  (str (loc (-> role :i18nkey keyword)) (when ((set req-foremen) (ss/lower-case (:name role))) required-suffix)))

(defn- required-foremen []
  (-> task-data-service
      (.foremanOrder)
      common/->cljs))

(defn sort-foremen [order rows]
  (let [index (zipmap order (range))] ; Create a map of order to index for quick lookup
    (sort-by #(get index % (count order)) rows)))

(rf/reg-event-db ::open-dialog
  (fn [db _]
    (dissoc db ::data)))

(rf/reg-event-db ::open-but-retain-role
  (fn [db [_ {:keys [foreman-role]}]]
    (cond-> (dissoc db ::data)
      foreman-role (assoc-in [::data :foreman-role] foreman-role))))

(defn role-select [foreman-roles]
  [:div.mt-5
   [:div
    [:label {:for "foreman-role"} (loc :foreman.role)]]
   [components/select
    (or (<sub [::foreman-role]) "")
    foreman-roles
    {:id       "foreman-role"
     :callback #(>evt [::set-foreman-role %])}]])

(defn foreman-select [authorized-parties]
  [:div.mt-5
   [:div
    [:label {:for "party"} (loc :foreman.selectForeman)]]
   [components/select
    (or (<sub [::foreman-party]) "")
    authorized-parties
    {:id       "party"
     :callback #(>evt [::set-foreman-party %])}]])

(defn email-input []
  [:div.mt-5
   [:div
    [:label {:for "invite-email"} (loc :userinfo.email)]]
   [components/text-edit
    (<sub [::invite-email])
    {:id       "invite-email"
     :invalid? (<sub [::email-invalid?])
     :callback #(>evt [::set-invite-email %])}]])

(defn dialog-buttons []
  [:div.mt-5.flex-row
   [:div.left
    [components/icon-button {:icon      :lupicon-check
                             :class     :primary.close
                             :text-loc  :application.parties.addForeman
                             :test-id   :invite-foreman
                             :wait?     false
                             :disabled? (<sub [::invite-disabled])
                             :on-click  #(>evt [::invite-foreman])}]]
   [:div.right
    [components/icon-button {:icon     :lupicon-remove
                             :class    :secondary
                             :text-loc :cancel
                             :on-click #(>evt [:dialog/close])}]]])

(defn- parties []
  (->> (js/ko.unwrap js/lupapisteApp.models.application.roles())
       common/->cljs
       (util/remove-by-key :type "company")
       ;; Do not show invited parties that have not yet accepted the invite
       (remove (fn [party]
                 ;; Pending invites show up as `:invite`.
                 ;; Accepted invites have `:inviteAccepted`
                 (some? (:invite party))))
       (into [])))

(defn view []
  (r/with-let [_ (>evt [::hub/subscribe "open-invite-foreman"
                        #(>evt [::open-dialog])
                        ::dialog-open])
               _ (>evt [::hub/subscribe "foreman-selected-role"
                        #(>evt [::set-foreman-role-and-task %])
                        ::selected-role])]
    (let [parties            (parties)
          req-foremen        (required-foremen)
          foreman-roles      (->> (common/->cljs js/LUPAPISTE.config.foremanRoles)
                                  (map (juxt :name (partial option-text req-foremen)))
                                  (sort-foremen req-foremen)
                                  (cons ["" ""]))
          authorized-parties (->> (concat [["" ""]]
                                          (map (juxt :id #(ss/trim-join " " (:firstName %) (:lastName %))) parties)
                                          [[other (loc other)]])
                                  (into []))]
      [:div.dialog-content
       [:form
        [common/loc-html-reagent :div :invite.foreman.desc-v3]
        [role-select foreman-roles]
        [foreman-select authorized-parties]
        (when (= other (<sub [::foreman-party]))
          [email-input])
        [dialog-buttons]]])
    (finally
      (>evt [::hub/unsubscribe ::dialog-open ::selected-role]))))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))

;; Event to open dialog

(rf/reg-event-fx ::open-foreman-dialog
  (fn [_ [_ foreman-role]]
    {:fx [[:dispatch [::open-but-retain-role {:foreman-role foreman-role}]]]
     :dialog/show {:ltitle           :application.parties.addForeman
                   :size             :large
                   :type             :react
                   :dialog-component [view]
                   :minContentHeight "50em"}}))
