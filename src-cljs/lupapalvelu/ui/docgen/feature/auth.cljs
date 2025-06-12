(ns lupapalvelu.ui.docgen.feature.auth
  (:require [lupapalvelu.next.session :as session]
            [lupapalvelu.ui.docgen.feature.application :as application]
            [re-frame.core :as rf]))

(defn on-app-loaded
  [{:keys [db]} [_ {:keys [app]}]]
  ;; Store the initial auth models
  {:db (assoc-in db [::data :doc-auths] (->> (application/app->documents app)
                                             (map (juxt :id :allowedActions))
                                             (filter second)
                                             (into {})))})

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::data)})

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::doc-auths
  :<- [::data]
  :-> :doc-auths)

(rf/reg-sub ::allowed-actions
  :<- [::doc-auths]
  (fn [auth [_ doc-id]]
    (get auth doc-id)))

(rf/reg-sub ::doc-auth?
  (fn doc-auth-inputfn [[_ doc-id action]]
    {:pre [doc-id action]}
    (rf/subscribe [::allowed-actions doc-id]))
  (fn [allowed-actions [_ _doc-id action]]
    (true? (-> allowed-actions action :ok))))

(rf/reg-sub ::doc-auth-reason
  (fn [[_ doc-id _action]]
    (rf/subscribe [::allowed-actions doc-id]))
  (fn [doc-allowed-actions [_ _doc-id action]]
    (:reason (get doc-allowed-actions action))))

(rf/reg-sub ::task-auths
  :<- [::data]
  :-> :task-auths)

(rf/reg-sub ::task-auth
  :<- [::task-auths]
  (fn [auths [_ auth-id]]
    (get auths auth-id)))

(rf/reg-sub ::task-auth-ok?
  (fn [[_ doc-id _action]]
    (rf/subscribe [::task-auth doc-id]))
  (fn [auth [_ _doc-id action]]
    (true? (-> auth action :ok))))

;; Special task authorization: query both
;; the document category auth model but also the application
;; model which has been creating within the scope of a particular task
(rf/reg-sub :auth/task?
  (fn [[_ doc-id action]]
    [(rf/subscribe [::task-auth-ok? doc-id action])
     (rf/subscribe [::doc-auth? doc-id action])])
  (fn [[auth-ok? doc-ok?]]
    (or auth-ok? doc-ok?)))

(rf/reg-sub ::auth?
  (fn auth-inputfn [[_ doc-id action override-auth-source]]
    {:pre [doc-id action]}
    (case override-auth-source
      ;; Special task-specific auth-model?
      :auth/task?
      (rf/subscribe [:auth/task? doc-id action])

      ;; Deny everything?
      :auth/disabled
      (delay false)

      ;; Delegate to the JS-wrapper subs in `lupapalvelu.ui.misc`?
      (:auth/global? :auth/application? :auth/attachment?)
      (rf/subscribe [override-auth-source action])

      ;; Otherwise, refer to our own document-specific auth models
      (rf/subscribe [::doc-auth? doc-id action])))
  :-> boolean)

(rf/reg-event-fx ::refresh-doc-auth.success
  (fn [{:keys [db]} [_ {:keys [actionsById]}]]
    {:db (reduce (fn [db [doc-id auth]]
                   (assoc-in db [::data :doc-auths (name doc-id)] auth))
                 db
                 actionsById)}))

(rf/reg-event-db ::store-doc-auth-result
  (fn [db [_ doc-id auth-data]]
    (assoc-in db [::data :doc-auths doc-id] auth-data)))

(defn refresh-doc-auth-fx
  "Refresh the auth-model for a single document(ish) target"
  [_ [_ {:keys [app-id doc-id category]}]]
  {:fx [[:action/query {:name    :allowed-actions-for-category
                        :params  {:id       app-id
                                  :category (or category :documents)
                                  :docId    doc-id}
                        :success [::refresh-doc-auth.success]}]]})

;; from CurrentUser model

(rf/reg-sub ::outside-authority
  :<- [::session/role]
  :<- [::application/organization]
  :<- [::session/org-authz]
  (fn [[role app-org authz] _]
    (and (= "authority" role)
         (not (get authz (keyword app-org))))))

(rf/reg-sub ::current-user-is-authority
  :<- [::session/role]
  :<- [::outside-authority]
  (fn [[role outside-authority] _]
    (and (= "authority" role)
         (not outside-authority))))

(rf/reg-sub ::current-user-is-applicant
  :<- [::session/role]
  :<- [::outside-authority]
  (fn [[role outside-authority] _]
    (or (= "applicant" role)
        outside-authority)))

(rf/reg-event-fx ::refresh-task-auth.success
  (fn [{:keys [db]} [_ {:keys [actions]} task-id]]
    {:db (assoc-in db [::data :task-auths task-id] actions)}))

(defn refresh-task-auth-fx
  "Refresh the auth-model for the application wrt. a specific task"
  [_ [_ {:keys [app-id task-id]}]]
  ;; NOTE: We are quering the whole app model but we also supply
  ;; the `taskId` parameter so that certain commands/pre-checks will know what
  ;; task we are interested in. For some reason several task-specific
  ;; commands in the task API _are not_ using `:categories #{:tasks}` so they
  ;; are not included in `allowed-actions-for-category`.

  ;; This is similar to `authorizationModel.refresh(application, {taskId: taskId}, ...)`
  {:fx [[:action/query {:name    :allowed-actions
                        :params  {:id     app-id
                                  :taskId task-id}
                        :success [::refresh-task-auth.success task-id]}]]})

(rf/reg-event-fx ::refresh-task-auth
  refresh-task-auth-fx)

(defn clear-task-auth
  "Cleanup any task-specific auth models fetched by `refresh-task-auth-fx`."
  [{:keys [db]} [_ task-id]]
  {:db (update-in db [::data :task-auths] dissoc task-id)})
