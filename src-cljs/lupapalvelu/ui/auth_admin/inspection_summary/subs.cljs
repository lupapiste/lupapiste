(ns lupapalvelu.ui.auth-admin.inspection-summary.subs
  (:require [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.auth-admin.org-subs :as org-subs]
            [lupapalvelu.ui.misc :as misc]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- set-visible [{:keys [db]} _]
  {:db (assoc-in db [::data :visible?] true)})

(defn- register-auth-model-listener [_ _]
  {:fx [[:dispatch [:auth/refresh]]]})

(rf/reg-event-fx ::toggle-view
  [(rf/inject-cofx ::org-subs/url-org-id)]
  (fn [{:keys [db] :as cofx} [_ visible?]]
    (let [org-id (::org-subs/url-org-id cofx)]
      (if visible?
        (nutil/merge-every-fn cofx [[set-visible]
                                    [org-subs/set-org-id org-id]
                                    [register-auth-model-listener]
                                    (when org-id
                                      [org-subs/fetch-org {:org-id org-id}])])
        {:db (dissoc db ::data)}))))

(rf/reg-sub ::data :-> ::data)

(rf/reg-event-fx ::start-creating
  (fn [{:keys [db]} _]
    {:db (-> db
             (assoc-in [::data :editing-id] :inspection-summary/new)
             (assoc-in [::data :edits] {}))}))

(rf/reg-event-fx ::start-editing
  (fn [{:keys [db]} [_ id]]
    {:db (-> db
             (assoc-in [::data :editing-id] id)
             (assoc-in [::data :edits] {}))}))

(rf/reg-event-fx ::cancel-editing
  (fn [{:keys [db]} _]
    {:db (update db ::data dissoc :editing-id)}))

(rf/reg-sub ::editing-id
  :<- [::data]
  :-> :editing-id)

(rf/reg-sub ::editing-id?
  :<- [::editing-id]
  (fn [editing-id [_ id]]
    (boolean (and editing-id (= editing-id id)))))

(rf/reg-sub ::creating-new?
  :<- [::editing-id]
  (fn [editing-id _]
    (boolean (and editing-id (= editing-id :inspection-summary/new)))))

(rf/reg-event-fx ::alter-name
  (fn [{:keys [db]} [_ name]]
    {:db (assoc-in db [::data :edits :name] name)}))

(rf/reg-event-fx ::alter-template-text
  (fn [{:keys [db]} [_ template-text]]
    {:db (assoc-in db [::data :edits :template-text] template-text)}))

(rf/reg-event-fx ::alter-operations
  (fn [{:keys [db]} [_ operations]]
    {:db (assoc-in db [::data :edits :operations] operations)}))

(rf/reg-sub ::edits
  (fn [[_ id]]
    [(rf/subscribe [::data])
     (rf/subscribe [::editing-id? id])])
  (fn [[data editing?]]
    (when editing?
      (:edits data))))

(defn- build-params
  [db]
  ;; We could be lazy and pass all the required values from the view
  ;; within the event, but that would cause re-rendering the whole editor bubble
  ;; every time a single field is modified. Instead, pass just the ID and
  ;; calculate the params here
  (let [{:keys [editing-id edits]} (::data db)]
    (-> {:organizationId     (org-subs/db->org-id db)
         :templateId         (when (not= :inspection-summary/new editing-id)
                               editing-id)
         ;; Only send changes
         :name               (some-> edits :name ss/trim)
         :templateText       (some-> edits :template-text ss/trim)
         :templateOperations (some-> edits :operations)}
        util/strip-nils)))

(rf/reg-event-fx ::create-or-modify-template
  (fn [{:keys [db]} [_ row-id]]
    {:fx [[:action/command {:name    (if (= :inspection-summary/new row-id)
                                       :create-inspection-summary-template
                                       :modify-inspection-summary-template)
                            :params  (build-params db)
                            :success [::create-or-modify-success]}]]}))

(defn- clear-edits [{:keys [db]} _]
  {:db (update db ::data dissoc :editing-id :edits)})

(rf/reg-event-fx ::create-or-modify-success
  (fn [{:keys [db] :as cofx} _]
    (let [org-id (org-subs/db->org-id db)]
      (nutil/merge-every-fn cofx [[clear-edits]
                                  [org-subs/fetch-org {:org-id org-id :force? true}]]))))

(rf/reg-event-fx ::delete-template
  (fn [{:keys [db]} [_ row-id]]
    (let [org-id (org-subs/db->org-id db)]
      {:fx [[:action/command {:name    :delete-inspection-summary-template
                              :params  {:organizationId org-id
                                        :templateId     row-id}
                              :success (fn []
                                         (>evt [::org-subs/fetch-org {:org-id org-id :force? true}]))}]]})))

(rf/reg-event-fx ::inspection-summaries-enabled-clicked
  (fn [{:keys [db]} [_ enabled?]]
    (let [org-id (org-subs/db->org-id db)]
      {:fx [[:action/command {:name    :set-organization-inspection-summaries
                              :params  {:enabled enabled?}
                              :success (fn []
                                         (>evt [::org-subs/fetch-org {:org-id org-id :force? true}]))}]
            [::misc/update-global-auth]]})))
