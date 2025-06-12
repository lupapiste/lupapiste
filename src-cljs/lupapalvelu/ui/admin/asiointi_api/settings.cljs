(ns lupapalvelu.ui.admin.asiointi-api.settings
  "Admin settings page related to AsiointiAPI (users)."
  (:require [lupapalvelu.asiointi.roles :as asiointi-api-roles]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.components.reselect :as reselect]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(def users-for-datatables-query
  [:action/query {:name    :users-for-datatables
                  :params  {:params {:filter-role :asiointi-api}}
                  :success [::users-for-datatables.success]}])

(rf/reg-event-fx ::init
  (fn [{:keys [db]} [_ visible?]]
    (if visible?
      {:db (assoc db ::data {:visible? true})
       :fx [users-for-datatables-query]}
      {:db (dissoc db ::data)})))

(defn- coerce-org-authz
  "Coerce `:orgAuthz` from vectors of strings to sets of keywords."
  [user]
  (update user :orgAuthz (fn [authz]
                           (->> authz
                                (map (fn [[org-id roles]]
                                       [org-id (set (map keyword roles))]))
                                (into {})))))

(rf/reg-event-fx ::users-for-datatables.success
  (fn [{:keys [db]} [_ resp]]
    (let [users (->> resp :data :rows
                     (map (juxt :_id coerce-org-authz))
                     (into {}))]
      {:db (assoc-in db [::data :users] users)})))

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::visible?
  :<- [::data]
  :-> (comp boolean :visible?))

(rf/reg-sub ::dialog-visible?
  :<- [::data]
  (fn [data [_ user-id]]
    (= user-id (:dialog-for data))))

(defn db-clear-dialog-data
  [db]
  (update db ::data dissoc :edits :dialog-for))

(rf/reg-sub ::users
  :<- [::data]
  :-> :users)

(defn- db->user
  [db user-id]
  (-> db ::data :users (get user-id)))

(rf/reg-sub ::user
  :<- [::users]
  (fn [users [_ id]]
    {:pre [(map? users) id]}
    (get users id)))

(rf/reg-sub ::user-ids
  :<- [::users]
  (fn [users]
    (->> users
         vals
         (sort-by :email)
         (mapv :_id))))

(rf/reg-event-fx ::edit-username
  (fn [{:keys [db]} [_ username]]
    {:db (assoc-in db [::data :edits :username] username)}))

;; We display an organization selector separately for different roles.
;; This requires us to transform between
;;   {org-id [roles ..]}, iow. regular `:orgAuthz`
;; and
;;   {role   [org-ids ..]}

(defn- org-authz->orgs
  [org-authz]
  (->> org-authz
       (mapcat (fn [[org-id roles]]
                 (map (fn [role] [org-id (keyword role)]) roles)))
       (reduce (fn [acc [org-id role]]
                 (update acc role (fnil conj #{}) org-id))
               {})))

(defn- orgs->org-authz
  [orgs]
  (->> orgs
       (mapcat (fn [[role orgs]]
                 (map (fn [org-id] [org-id role]) orgs)))
       (reduce (fn [acc [org-id role]]
                 (update acc org-id (fnil conj #{}) role))
               {})))

(defn- refresh-org-authz
  "Refresh the resulting `:orgAuthz` in app-db when read/write orgs are selected"
  [db user-id]
  (let [existing    (-> (if (= ::new user-id)
                          {}
                          (:orgAuthz (db->user db user-id)))
                        org-authz->orgs)
        db-orgs     (-> db ::data :edits :org)
        merged-orgs (reduce (fn [acc role]
                              (if-let [modified-orgs (get db-orgs role)]
                                (assoc acc role modified-orgs)
                                acc))
                            existing
                            asiointi-api-roles/roles)]
    (assoc-in db [::data :edits :org-authz] (orgs->org-authz merged-orgs))))

(rf/reg-sub ::current-org-authz
  :<- [::edits]
  (fn [edits]
    (-> edits :org-authz)))

(defn- db->org-authz
  [db]
  (-> db ::data :edits :org-authz))

(rf/reg-event-fx ::edit-orgs
  (fn [{:keys [db]} [_ user-id role orgs]]
    {:db (-> db
             (assoc-in [::data :edits :org role] orgs)
             (refresh-org-authz user-id))}))

(rf/reg-sub ::organizations
  :<- [::data]
  :-> :orgs)

(rf/reg-sub ::edits
  :<- [::data]
  :-> :edits)

(rf/reg-sub ::current-username
  (fn [[_ user-id]]
    {:pre [user-id]}
    (cond-> {:edits (rf/subscribe [::edits])}
      (not= ::new user-id) (assoc :user (rf/subscribe [::user user-id]))))
  (fn [{:keys [edits user]}]
    (or (:username user)
        (:username edits))))

;; Create a new user

(rf/reg-sub ::can-submit-new-user?
  :<- [::current-username ::new]
  (fn [username]
    (-> username ss/blank? not)))

(rf/reg-event-fx ::submit-new-user
  (fn [{:keys [db]} _]
    (let [username  (-> db ::data :edits :username)
          org-authz (db->org-authz db)]
      {:fx [[:action/command {:name    :create-asiointi-api-user
                              :params  {:username username
                                        :orgAuthz org-authz}
                              :success [::create-asiointi-api-user.success]}]]})))

(defn- user-created-success-dialog
  "Show the generated password to the user"
  [{:keys [user]}]
  [:div.dialog-content
   [:h3 (common/loc :admin.asiointiapi.add-user.success)]
   [:p (common/loc :admin.asiointiapi.add-user.password-note) ":"]
   [:pre.font-mono.m-2.bg-gray-300
    (:password user)]
   [:div.flex-row.mt-4
    [:div.right
     [components/icon-button {:class    :primary
                              :text-loc :ok
                              :on-click #(>evt [::close-dialog])}]]]])

(rf/reg-event-fx ::create-asiointi-api-user.success
  (fn [{:keys [db]} [_ resp]]
    {:db (db-clear-dialog-data db)
     :fx [users-for-datatables-query
          [:dialog/close]
          [:dialog/show {:ltitle           :admin.asiointiapi.add-user.success
                         :size             :medium
                         :type             :react
                         :dialog-component [user-created-success-dialog resp]}]]}))

;; Modify an existing user

(rf/reg-sub ::can-submit-modified-user?
  (fn [[_ user-id]]
    [(rf/subscribe [::user user-id])
     (rf/subscribe [::current-org-authz])])
  (fn [[user new-org-authz]]
    (not= (:orgAuthz user)
          new-org-authz)))

(rf/reg-event-fx ::submit-modified-user
  (fn [{:keys [db]} [_ user-id]]
    (let [user      (db->user db user-id)
          org-authz (db->org-authz db)]
      {:fx [[:action/command {:name                  :modify-asiointi-api-user
                              :params                {:username (:username user)
                                                      :orgAuthz org-authz}
                              :success               [::modify-asiointi-api-user.success]
                              :show-saved-indicator? true}]]})))

(rf/reg-event-fx ::modify-asiointi-api-user.success
  (fn [{:keys [db]} [_ _resp]]
    {:db (db-clear-dialog-data db)
     :fx [users-for-datatables-query
          [:dialog/close]]}))

;; Dialog field(s)

(defn- username-field
  [user-id]
  [:<>
   [:label {:for "username"}
    (common/loc :username)]
   [ui-input/text-input
    {:id          "username"
     :class       "lux"
     :placeholder (common/loc :username)
     :disabled?   (not= ::new user-id)
     :value       (<sub [::current-username user-id])
     :on-change   (fn [val] (>evt [::edit-username val]))}]])

;; Role selector(s)

(rf/reg-sub ::current-orgs
  :<- [::current-org-authz]
  :-> org-authz->orgs)

(rf/reg-sub ::current-orgs-for-role
  :<- [::current-orgs]
  (fn [orgs [_ role]]
    (get orgs role #{})))

(rf/reg-sub ::current-orgs-for-not-role
  :<- [::current-orgs]
  (fn [orgs [_ role]]
    (->> orgs
         (filter (fn [[r _orgs]]
                   (not= r role)))
         (mapcat val)
         (into #{}))))

(rf/reg-sub ::organization-items
  (fn [[_ role]]
    [(rf/subscribe [::organizations])
     (rf/subscribe [::current-orgs-for-not-role role])])
  (fn [[orgs ignore]]
    (let [lang (keyword (common/get-current-language))]
      (->> orgs
           ;; Don't allow choosing an org for this role,
           ;; if the same org is already chosen for another role
           (remove (fn [[org-id _]]
                     (contains? ignore org-id)))
           (map (fn [[org-id data]]
                  {:value org-id
                   :text  (str (name org-id) ": " (get data lang))}))
           (sort-by :text)
           (into [])))))

(defn- role-selectors
  [user-id]
  [:<>
   (->> asiointi-api-roles/roles
        (map (fn [role]
               (let [id (str "role-" role "-orgs")]
                 ^{:key (name role)}
                 [:<>
                  [:label {:for id} (common/loc (util/kw-path :admin.asiointiapi.role role))]
                  [reselect/reselect
                   (<sub [::current-orgs-for-role role])
                   {:id       id
                    :multi?   true
                    :clear?   true
                    :items    (<sub [::organization-items role])
                    :callback (fn [orgs] (>evt [::edit-orgs user-id role orgs]))}]])))
        doall)])

(defn- create-or-edit-modal
  [user-id]
  ;; The modal system in Lupapiste seems to leave old modals in the DOM.
  ;; Use a visibility check to discard the contents & subscriptions
  (when (<sub [::dialog-visible? user-id])
    (let [new? (= ::new user-id)]
      [:div.dialog-content
       [:div.flex.flex-col.gap-2.m-2
        [:h3 (common/loc (if new?
                           :admin.asiointiapi.add-user.title
                           :admin.asiointiapi.edit-user.title))]
        [username-field user-id]
        [role-selectors user-id]
        [:div.flex-row
         [:div.left
          (if new?
            [components/icon-button {:icon      :lupicon-check
                                     :class     :primary.close
                                     :text-loc  :add
                                     :disabled? (not (<sub [::can-submit-new-user?]))
                                     :on-click  #(>evt [::submit-new-user])}]
            [components/icon-button {:icon      :lupicon-check
                                     :class     :primary.close
                                     :text-loc  :save
                                     :disabled? (not (<sub [::can-submit-modified-user? user-id]))
                                     :on-click  #(>evt [::submit-modified-user user-id])}])]
         [:div.right
          [components/icon-button {:icon     :lupicon-remove
                                   :class    :secondary
                                   :text-loc :cancel
                                   :on-click #(>evt [::close-dialog])}]]]]])))

(defn- show-create-or-edit-dialog-fx
  [db user-id]
  (let [update-orgs? (not (-> db ::data :orgs))]
    {:db (-> db
             (update ::data dissoc :edits)
             (assoc-in [::data :dialog-for] user-id)
             (refresh-org-authz user-id))
     :fx [[:dialog/show {:ltitle           (if (= ::new user-id)
                                             :admin.asiointiapi.add-user
                                             :admin.asiointiapi.edit-user)
                         :size             :medium
                         :type             :react
                         :dialog-component [create-or-edit-modal user-id]
                         :minContentHeight "40em"
                         :cancel-callback  #(>evt [::close-dialog])}]
          (when update-orgs?
            [:action/query {:name    :asiointi-api-organizations
                            :success [::asiointi-api-organizations.success]}])]}))

(rf/reg-event-fx ::asiointi-api-organizations.success
  (fn [{:keys [db]} [_ {:keys [organizations]}]]
    {:db (assoc-in db [::data :orgs] organizations)}))

(rf/reg-event-fx ::close-dialog
  (fn [{:keys [db]}]
    {:db (db-clear-dialog-data db)
     :fx [[:dialog/close]]}))

(rf/reg-event-fx ::add-clicked
  (fn [{:keys [db]} [_]]
    (show-create-or-edit-dialog-fx db ::new)))

(rf/reg-event-fx ::edit-clicked
  (fn [{:keys [db]} [_ user-id]]
    (show-create-or-edit-dialog-fx db user-id)))

(rf/reg-event-fx ::reset-password-clicked
  (fn [{:keys [db]} [_ user-id]]
    (let [user (db->user db user-id)]
      {:fx [[:dialog/show {:text  (common/loc :admin.asiointiapi.reset-password
                                              (:username user))
                           :type  :yes-no
                           :event [::reset-password-confirmed (:username user)]}]]})))

(rf/reg-event-fx ::reset-password-confirmed
  (fn [_ [_ username]]
    {:fx [[:action/command {:name    :reset-asiointi-api-user-password
                            :params  {:username username}
                            :success [::reset-asiointi-api-user-password.success]}]]}))

(defn- password-reset-success-dialog
  [{:keys [password]}]
  [:div.dialog-content
   [:h3 (common/loc :admin.asiointiapi.reset-password.success.title)]
   [:p (common/loc :admin.asiointiapi.reset-password.success.note) ":"]
   [:pre.font-mono.m-2.bg-gray-300
    password]
   [:div.flex-row.mt-4
    [:div.right
     [components/icon-button {:class    :primary
                              :text-loc :ok
                              :on-click #(>evt [::close-dialog])}]]]])

(rf/reg-event-fx ::reset-asiointi-api-user-password.success
  (fn [{:keys [db]} [_ resp]]
    {:db (db-clear-dialog-data db)
     :fx [[:dialog/close]
          [:dialog/show {:ltitle           :admin.asiointiapi.reset-password.success.title
                         :size             :medium
                         :type             :react
                         :dialog-component [password-reset-success-dialog resp]}]]}))

(rf/reg-event-fx ::deactivate-clicked
  (fn [{:keys [db]} [_ user-id]]
    (let [user (db->user db user-id)]
      {:fx [[:dialog/show {:text  (common/loc :admin.asiointiapi.deactivate-user
                                              (:username user))
                           :type  :yes-no
                           :event [::deactivate-user-confirmed (:email user)]}]]})))

(rf/reg-event-fx ::deactivate-user-confirmed
  (fn [_ [_ user-email]]
    {:fx [[:action/command {:name    :set-user-enabled
                            :params  {:email   user-email
                                      :enabled false}
                            :success [::set-user-enabled.success]}]]}))

(rf/reg-event-fx ::undo-clicked
  (fn [{:keys [db]} [_ user-id]]
    (let [user (db->user db user-id)]
      {:fx [[:action/command {:name    :set-user-enabled
                              :params  {:email   (:email user)
                                        :enabled true}
                              :success [::set-user-enabled.success]}]]})))

(rf/reg-event-fx ::set-user-enabled.success
  (fn [_ [_ _]]
    {:fx [users-for-datatables-query]}))

(defn- authz-type
  [roles]
  (cond
    (util/includes-as-kw? roles :api-writer) :api-writer
    (util/includes-as-kw? roles :api-reader) :api-reader))

(defn- authz-pill
  [text authz-type]
  [:div
   {:class [(case authz-type
              :api-reader "bg-gray-500"
              :api-writer "bg-lime-700"
              ;; unknown
              "bg-red-800")
            "p-1"
            "rounded-lg"
            "text-white"]}
   text])

(defn- org-authz
  [org-authz]
  [:div.flex.flex-wrap.gap-2
   (->> org-authz
        (sort-by first)
        (map (fn [[org roles]]
               (let [authz-type (authz-type roles)
                     text       (name org)]
                 ^{:key (str text "-" authz-type)}
                 [authz-pill text authz-type])))
        doall)])

(defn- user-row
  [user-id]
  (let [{:keys [username orgAuthz enabled]} (<sub [::user user-id])]
    [:tr
     [:td
      {:class (when-not enabled
                :line-through)}
      username]
     [:td
      (org-authz orgAuthz)]
     [:td
      [:div.flex.flex-wrap.gap-2
       [components/icon-button
        {:icon      :lupicon-pen
         :class     :primary
         :text-loc  :edit
         :disabled? (not enabled)
         :on-click  #(>evt [::edit-clicked user-id])}]
       [components/icon-button
        {:icon      :lupicon-circle-attention
         :class     :secondary
         :text-loc  :users.op.resetPassword
         :disabled? (not enabled)
         :on-click  #(>evt [::reset-password-clicked user-id])}]
       (if enabled
         [components/icon-button
          {:icon       :lupicon-trash
           :icon-only? true
           :class      :secondary
           :text-loc   :remove
           :on-click   #(>evt [::deactivate-clicked user-id])}]
         [components/icon-button
          {:icon     :lupicon-undo
           :class    :secondary
           :text-loc :undo
           :on-click #(>evt [::undo-clicked user-id])}])]]]))

(defn- user-table
  []
  [:<>
   [:h3 (common/loc :admin.asiointiapi.user-table.header)]
   [components/icon-button
    {:icon     :lupicon-plus
     :class    :secondary.mb-2
     :text-loc :add
     :on-click #(>evt [::add-clicked])}]
   [:table
    [:thead
     [:tr
      [:th (common/loc :username)]
      [:th (common/loc :admin.organizations)]
      [:th (common/loc :admin.actions)]]]
    [:tbody
     (->> (<sub [::user-ids])
          (map (fn [id]
                 ^{:key id}
                 [user-row id]))
          doall)]]])

(defn- view
  []
  (when (<sub [::visible?])
    [:div
     [:h2 (common/loc :admin.asiointiapi.settings)]
     [user-table]]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id _params]
  (ui-dom/mount! mounter dom-id))
