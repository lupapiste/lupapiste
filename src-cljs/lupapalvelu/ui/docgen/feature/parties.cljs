(ns lupapalvelu.ui.docgen.feature.parties
  "Produce values for the person/party selector.
  Also provides 'roles' data.
  See `parties-model.js`."
  (:require [lupapalvelu.ui.authorization :as ui-auth]
            [lupapalvelu.ui.user :as ui-user]
            [re-frame.core :as rf]
            [sade.strings :as ss]))

(defn with-roles
  "See `withRoles` in `application-model.js`."
  [auth]
  (reduce (fn [acc {:keys [id role] :as i}]
            (let [auth (or (get acc id)
                           (assoc i :roles #{}))
                  auth (if-not (contains? (:roles auth) role)
                         (update auth :roles conj role)
                         auth)]
              (assoc acc id auth)))
          {}
          auth))

(defn on-app-loaded
  [{:keys [db]} [_ {{:keys [id auth]} :app}]]
  (let [company-parties? (->> auth
                              (some (fn [party]
                                      (= "company" (:type party)))))
        valid-persons    (->> auth
                              (filter (fn [{:keys [firstName lastName role]}]
                                        (and (ss/not-blank? firstName)
                                             (ss/not-blank? lastName)
                                             (not (contains? #{"statementGiver"
                                                               "guest"
                                                               "guestAuthority"
                                                               "reader"} role)))))
                              (map (juxt :id identity))
                              (into {}))
        query-users?     (and company-parties?
                              (ui-auth/application-auth? :company-users-for-person-selector))]
    (cond-> {:db (-> db
                     (assoc-in [::data :valid-persons] valid-persons)
                     (assoc-in [::data :roles] (with-roles auth)))}
      query-users? (assoc :fx [[:action/query {:name    :company-users-for-person-selector
                                               :params  {:id id}
                                               :success [::company-users.success]}]]))))

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::data)})

(rf/reg-event-fx ::company-users.success
  (fn [{:keys [db]} [_ {:keys [users]}]]
    (let [valid-user-ids  (-> db ::data :valid-persons keys set)
          current-user-id (ui-user/current-user-id)
          company-users   (->> users
                               (map (fn [{:keys [id] :as user}]
                                      (assoc user :personally-authorized
                                             (or (= current-user-id id)
                                                 (contains? valid-user-ids id)))))
                               (map (juxt :id identity))
                               (into {}))]
      {:db (assoc-in db [::data :company-users] company-users)})))

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::valid-persons
  :<- [::data]
  :-> :valid-persons)

(rf/reg-sub ::company-users
  :<- [::data]
  :-> :company-users)

(rf/reg-sub ::person-items
  :<- [::valid-persons]
  :<- [::company-users]
  (fn [[persons company-users] [_ exclude-companies?]]
    (->> (merge (when-not exclude-companies?
                  company-users)
                persons)
         vals
         (into #{}))))

(defn db->company-user
  [db auth-id]
  (let [users (get-in db [::data :company-users])]
    (get users auth-id)))

(rf/reg-sub ::roles
  :<- [::data]
  :-> :roles)

(rf/reg-sub ::company-roles
  :<- [::roles]
  (fn [roles]
    (->> roles
         vals
         (filter (fn [auth]
                   (= "company" (:type auth))))
         (into #{}))))
