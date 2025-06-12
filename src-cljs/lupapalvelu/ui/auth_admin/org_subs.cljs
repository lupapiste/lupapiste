(ns lupapalvelu.ui.auth-admin.org-subs
  "Common shared `app-db` storage/subs for organization(s) for authority admin view(s)"
  (:require [lupapalvelu.application.foreman :as foreman]
            [re-frame.core :as rf]
            [sade.util :as util]))

(defn- query-org-fx
  [org-id]
  [:action/query {:name    :organization-by-user
                  :params  {:organizationId org-id}
                  :success [::fetch-org.success]}])

(defn fetch-org
  "Effect handler for fetching organization data. Intended to be used
  with `lupapalvelu.next.util/merge-every-fn` while doing other effects."
  [{:keys [db]} [_ {:keys [org-id force?]}]]
  (let [no-org? (nil? (some-> db ::data :orgs (get org-id)))]
    ;; To avoid fetching on each page navigation, only fetch if we don't have
    ;; the data or someone forced us to fetch
    {:fx [(when (or no-org? force?)
            (query-org-fx org-id))]}))

;; Sometimes you just want to fetch the org, e.g. in an API success callback
(rf/reg-event-fx ::fetch-org
  fetch-org)

(rf/reg-event-fx ::fetch-org.success
  (fn [{:keys [db]} [_ {:keys [organization]}]]
    {:db (assoc-in db [::data :orgs (:id organization)] organization)}))

(rf/reg-sub ::data
  :-> ::data)

(rf/reg-sub ::orgs
  :<- [::data]
  :-> :orgs)

;;; API when selecting & accessing the current org and its properties

(rf/reg-cofx ::url-org-id
  (fn [cofx]
    (let [path       (-> js/window .-location .-pathname)
          [_ org-id] (re-find #"/app/.*/authority-admin/(.*)" path)]
      (assoc cofx ::url-org-id org-id))))

(defn set-org-id
  "Select the active organization"
  [{:keys [db]} [_ org-id]]
  {:pre [org-id]}
  {:db (assoc-in db [::data :org-id] org-id)})

(defn db->org-id
  [db]
  (->> db ::data :org-id))

(defn db->org
  [db]
  (-> db ::data :orgs (get (db->org-id db))))

(rf/reg-sub ::org-id
  :<- [::data]
  :-> :org-id)

(rf/reg-sub ::org
  :<- [::orgs]
  :<- [::org-id]
  (fn [[orgs org-id]]
    (get orgs org-id)))

(rf/reg-sub ::inspection-summaries-enabled?
  :<- [::org]
  :-> (comp boolean :inspection-summaries-enabled))

(rf/reg-sub ::pate-enabled?
  :<- [::org]
  (fn [{:keys [scope]}]
    (some (fn [s] (-> s :pate :enabled)) scope)))

(rf/reg-sub ::org-section
  :<- [::org]
  :-> :section)

(rf/reg-sub ::verdict-section-enabled?
  :<- [::org-section]
  :-> (comp boolean :enabled))

(rf/reg-sub ::verdict-section-enabled-for-op?
  :<- [::org-section]
  (fn [{:keys [operations]} [_ op]]
    (boolean (some #(= op %) operations))))

(rf/reg-sub ::org-suti
  :<- [::org]
  :-> :suti)

(rf/reg-sub ::suti-enabled?
  :<- [::org-suti]
  :-> (comp boolean :enabled))

(rf/reg-sub ::suti-enabled-for-op?
  :<- [::org-suti]
  (fn [{:keys [operations]} [_ op]]
    (boolean (some #(= op %) operations))))

(rf/reg-sub ::selected-operations
  :<- [::org]
  :-> :selectedOperations)

(rf/reg-sub ::inspection-summary
  :<- [::org]
  :-> :inspection-summary)

(rf/reg-sub ::templates
  :<- [::inspection-summary]
  :-> :templates)

(rf/reg-sub ::operations-templates
  :<- [::inspection-summary]
  :-> :operations-templates)

(rf/reg-sub ::operations-templates-for-op
  :<- [::operations-templates]
  :<- [::templates]
  (fn [[templates-per-op templates] [_ id]]
    (let [template-ids (set (get templates-per-op (keyword id)))]
      (->> templates
           (filterv (fn [{:keys [id]}]
                      (contains? template-ids id)))))))

(rf/reg-sub ::application-submit-assurance-templates
  :<- [::org]
  :-> :application-submit-assurance-templates)


(rf/reg-sub ::foreman-settings-submit
  :<- [::org]
  (fn [org _]
    (let [settings (get org :foreman-submit-settings {})]
      (foreman/with-submit-settings-defaults settings))))

(rf/reg-event-fx ::set-foreman-setting
  (fn [_ [_ org-id title type value]]
    {:fx [[:action/command {:name   :set-foreman-submit-setting
                            :show-saved-indicator?   true
                            ;; path is a map to make it easier to check the schema in the commands input-validator
                            ;; The command uses `toggle-organization-flag` so it still needs the enabled param
                            :params {:path           {title {type value}}
                                     :enabled        value
                                     :organizationId org-id}
                            :success [::set-foreman-setting.success org-id title type value]}]]}))

(rf/reg-event-db ::set-foreman-setting.success
                 (fn [db [_ _ org-id title type value]]
                   (assoc-in db [::data
                                 :orgs
                                 org-id
                                 :foreman-submit-settings
                                 (keyword title)
                                 (keyword type)] value)))
