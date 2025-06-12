(ns lupapalvelu.ui.admin.sso-keys.subs
  (:require [re-frame.core :as rf]
            [sade.strings :as ss]))

(def query-sso-keys [:action/query {:name    :get-single-sign-on-keys
                                    :success ::get-single-sign-on-keys.success}])

(rf/reg-event-fx ::init
  (fn [{:keys [db]} [_ visible?]]
    (if visible?
      {:db (assoc db ::data {:visible? true})
       :fx [query-sso-keys]}
      {:db (dissoc db ::data)})))

(rf/reg-event-fx ::get-single-sign-on-keys.success
  (fn [{:keys [db]} [_ {sso-keys :ssoKeys}]]
    {:db (assoc-in db [::data :sso-keys] (->> sso-keys
                                              (map (fn [{:keys [id] :as entry}]
                                                     [id entry]))
                                              (into {})))}))

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::visible?
  :<- [::data]
  :-> :visible?)

(rf/reg-sub ::sso-keys
  :<- [::data]
  :-> :sso-keys)

(rf/reg-sub ::row-ids
  :<- [::sso-keys]
  (fn [entries]
    (->> entries
         keys
         sort
         (into []))))

(rf/reg-sub ::changes
  :<- [::data]
  :-> :changes)

(rf/reg-sub ::original-entry-for-id
  :<- [::sso-keys]
  (fn [entries [_ id]]
    (get entries id)))

(rf/reg-sub ::changes-for-id
  :<- [::changes]
  (fn [changes [_ id]]
    (get changes id)))

(rf/reg-sub ::current-entry
  (fn [[_ id]]
    [(rf/subscribe [::original-entry-for-id id])
     (rf/subscribe [::changes-for-id id])])
  (fn [[original changes]]
    (merge original changes)))

(rf/reg-sub ::can-save?
  (fn [[_ id]]
    [(rf/subscribe [::original-entry-for-id id])
     (rf/subscribe [::current-entry id])])
  (fn [[original current]]
    (and (not= original current)
         (some ss/not-blank? (vals current)))))

(defn- sub-current-entry [[_ id]] (rf/subscribe [::current-entry id]))

(rf/reg-sub ::ip sub-current-entry :-> :ip)
(rf/reg-sub ::sso-key sub-current-entry :-> :sso-key)
(rf/reg-sub ::comment sub-current-entry :-> :comment)

(rf/reg-event-fx ::change-ip
  (fn [{:keys [db]} [_ {:keys [id ip]}]]
    {:db (assoc-in db [::data :changes id :ip] ip)}))

(rf/reg-event-fx ::change-sso-key
  (fn [{:keys [db]} [_ {:keys [id sso-key]}]]
    {:db (assoc-in db [::data :changes id :sso-key] sso-key)}))

(rf/reg-event-fx ::change-comment
  (fn [{:keys [db]} [_ {:keys [id comment]}]]
    {:db (assoc-in db [::data :changes id :comment] comment)}))

(rf/reg-event-fx ::save-changes
  (fn [_ [_ {:keys [id entry]}]]
    (let [adding? (= :sso/new-entry id)]
      {:fx [[:action/command {:name    (if adding?
                                         :add-single-sign-on-key
                                         :update-single-sign-on-key)
                              :params  (cond-> {:ip      (:ip entry)
                                                :key     (:sso-key entry)
                                                :comment (:comment entry)}
                                         (not adding?) (assoc :sso-id id))
                              :success [::update-single-sign-on-key.success id]}]]})))

(rf/reg-event-fx ::update-single-sign-on-key.success
  (fn [{:keys [db]} [_ resp id]]
    {:fx [query-sso-keys
          [:indicator/saved resp]
          [:hub/send {:event :sso-keys-changed}]]
     :db (update-in db [::data :changes] dissoc id)}))

(rf/reg-event-fx ::show-remove-entry-dialog
  (fn [_ [_ options]]
    {:fx [[:dialog/show {:title "Poista SSO-avain"
                         :text  "Haluatko varmasti poistaa SSO-avaimen"
                         :type  :yes-no
                         :event [::remove-entry options]}]]}))

(rf/reg-event-fx ::remove-entry
  (fn [_ [_ {:keys [id]}]]
    {:fx [[:action/command {:name    :remove-single-sign-on-key
                            :params  {:sso-id id}
                            :success ::remove-single-sign-on-key.success}]]}))

(rf/reg-event-fx ::remove-single-sign-on-key.success
  (fn [_ [_ resp]]
    {:fx [query-sso-keys
          [:indicator/saved resp]
          [:hub/send {:event :sso-keys-changed}]]}))
