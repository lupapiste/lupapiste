(ns lupapalvelu.ui.application.data
  (:require [lupapalvelu.application :as application]
            [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.next.router-api :as router]
            [lupapalvelu.ui.application.foreman-util :as foreman-util]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.misc :as misc]
            [re-frame.core :as rf]))

;; TODO: For reuse with cljs docgen, it would likely make sense to cleanup the
;; common/application-id uses in this ns, and provide app-id from event vector.

(rf/reg-sub ::selected-operation :-> ::selected-operation)

(rf/reg-event-db ::clear-foreman-data
  (fn [db _]
    (dissoc db ::app-id ::editing-start-date ::termination-reason
            ::termination-date ::responsibility-start-date
            ::selected-foreman-document ::selected-foreman-document-timestamps
            ::foreman-applications)))

(rf/reg-event-db ::clear-foreman-termination-data
  (fn [db [_ tj-id]]
    (-> db
        (update ::termination-reason dissoc tj-id)
        (update ::termination-date dissoc tj-id))))

(rf/reg-event-db ::select-operation
  (fn [db [_ operation]]
    (assoc db ::selected-operation operation)))

(rf/reg-event-fx ::fetch-operations.success
  (fn [{db :db} [_ {:keys [operations]}]]
    {:db (assoc db ::operations operations)}))

(rf/reg-event-fx ::cancel
  (fn [_ _]
    {::misc/navigate-back true}))

(rf/reg-event-fx ::create-operation
  (fn [{db :db} _]
    {:db             (dissoc db ::selected-operation)
     :action/command {:name    :add-execution-permit
                      :params  {:id        (common/application-id)
                                :operation (get db ::selected-operation)}
                      :success #(.openPage js/pageutil (str "application/" (:id %)))
                      :fail    ::generic.error}}))

(rf/reg-event-fx ::fetch-operations
  (fn [_]
    (when-let [id (common/application-id)]
      {:action/query {:name    :all-operations-for-schema
                      :params  {:id   id
                                :path application/operations-path-for-toteuttamislupa}
                      :success ::fetch-operations.success}})))

(rf/reg-sub ::termination-reason
  (fn [db [_ tj-id]]
    (get-in db [::termination-reason tj-id])))

(rf/reg-sub ::termination-date
  (fn [db [_ tj-id]]
    (get-in db [::termination-date tj-id])))

(rf/reg-sub ::responsibility-start-date
  (fn [db [_ tj-id]]
    (get-in db [::responsibility-start-date tj-id])))

(rf/reg-sub ::editing-start-date?
  (fn [db [_ tj-id]]
    (get-in db [::editing-start-date tj-id])))

(rf/reg-sub ::foreman-doc-type
  (fn [db [_ foreman-doc-id]]
    (get-in db [::doc-type foreman-doc-id])))

(rf/reg-event-db ::set-termination-reason
  (fn [db [_ tj-id reason]]
    (assoc-in db [::termination-reason tj-id] reason)))

(rf/reg-event-db ::set-termination-date
  (fn [db [_ tj-id date]]
    (assoc-in db [::termination-date tj-id] date)))

(rf/reg-event-db ::set-responsibility-start-date
  (fn [db [_ tj-id date]]
    (assoc-in db [::responsibility-start-date tj-id] date)))

(rf/reg-event-db ::toggle-edit-start-date
  (fn [db [_ tj-id]]
    (update-in db [::editing-start-date tj-id] not)))

(rf/reg-sub ::selected-foreman-document :-> ::selected-foreman-document)
(rf/reg-sub ::selected-foreman-document-timestamps :-> ::selected-foreman-document-timestamps)

(rf/reg-sub ::selected-foreman-tasks
  :<- [::selected-foreman-document]
  (fn [tj-document]
    (-> tj-document :data :vastattavatTyotehtavat)))

(rf/reg-sub ::selected-foreman-responsibilities
  :<- [::selected-foreman-tasks]
  (fn [tasks]
    (->> tasks
         (filter (comp boolean :value second))
         keys
         (filter (fn [key]
                   (let [include-other (and (:muuMika tasks) (-> tasks :muuMika :value))]
                     (not (or (= key :muuMika)
                              (and (= key :muuMikaValue) (not include-other)))))))
         (map (fn [key]
                (if (= key :muuMikaValue)
                  (-> tasks (get key) :value)
                  (loc (str "osapuoli.tyonjohtaja.vastattavatTyotehtavat." (name key)))))))))

(rf/reg-sub ::app-id :-> ::app-id)

(rf/reg-event-fx ::update-foremen
  (fn [_ [_ app-id]]
    {:fx [[:application/load app-id]
          [:dispatch [::fetch-foreman-applications]]]}))

(rf/reg-event-fx ::terminate-foreman.success
  (fn [_ [_ app-id tj-id]]
    {:fx [[:ajaxwait/show false]
          [:dispatch [::update-foremen app-id]]
          [:dispatch [::clear-foreman-termination-data tj-id]]]}))

(rf/reg-event-fx ::set-foreman-start-date
  (fn [{:keys [db]} [_ foreman-app-id]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :set-foreman-start-date
                        :params  {:id             app-id
                                  :foreman-app-id foreman-app-id
                                  :started        (get-in db [::responsibility-start-date foreman-app-id])}
                        :success #(>evt [::update-foremen app-id])}})))

(rf/reg-event-fx ::set-foreman-start-date-v3
  (fn [{:keys [db]} [_ foreman-doc-id]]
    (let [app-id (common/application-id)]
      (merge
        (when-let [started (get-in db [::responsibility-start-date foreman-doc-id])]
          {:action/command {:name    :set-foreman-start-date-v3
                            :params  {:id             app-id
                                      :foreman-doc-id foreman-doc-id
                                      :started        started}
                            :success #(>evt [::update-foremen app-id])}})
        {:fx [[:dispatch [::toggle-edit-start-date foreman-doc-id]]]}))))

(rf/reg-event-fx ::terminate-foreman-v3
  (fn [{:keys [db]} [_ foreman-doc-id]]
    (let [app-id (common/application-id)]
      {:fx [[:dialog/close]
            [:ajaxwait/show true]]
       :action/command {:name    :terminate-foreman-v3
                        :params  {:id             app-id
                                  :foreman-doc-id foreman-doc-id
                                  :reason         (get-in db [::termination-reason foreman-doc-id])
                                  :ended          (get-in db [::termination-date foreman-doc-id])}
                        :success #(>evt [::terminate-foreman.success app-id foreman-doc-id])}})))

(rf/reg-event-fx ::terminate-foreman
  (fn [{:keys [db]} [_ foreman-app-id]]
    (let [app-id (common/application-id)]
      {:fx [[:dialog/close]
            [:ajaxwait/show true]]
       :action/command {:name    :terminate-foreman
                        :params  {:id             app-id
                                  :foreman-app-id foreman-app-id
                                  :reason         (get-in db [::termination-reason foreman-app-id])
                                  :ended          (get-in db [::termination-date foreman-app-id])}
                        :success #(>evt [::terminate-foreman.success app-id foreman-app-id])}})))

(rf/reg-event-fx ::request-foreman-termination-v3
  (fn [{:keys [db]} [_ email foreman-doc-id]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :request-foreman-termination-v3
                        :params  {:id             app-id
                                  :foreman-doc-id foreman-doc-id
                                  :foreman-email  (or email "")
                                  :reason         (get-in db [::termination-reason foreman-doc-id])}
                        :success #(>evt [::update-foremen app-id])}})))

(rf/reg-event-fx ::request-foreman-termination
  (fn [{:keys [db]} [_ email foreman-app-id]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :request-foreman-termination
                        :params  {:id             app-id
                                  :foreman-app-id foreman-app-id
                                  :foreman-email  (or email "")
                                  :reason         (get-in db [::termination-reason foreman-app-id])}
                        :success #(>evt [::update-foremen app-id])}})))

(rf/reg-event-fx ::confirm-termination
  (fn [_ [_ foreman-app-id]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :confirm-foreman-termination
                        :params  {:id             app-id
                                  :foreman-app-id foreman-app-id}
                        :success #(>evt [::update-foremen app-id])}})))

(rf/reg-event-fx ::confirm-termination-v3
  (fn [_ [_ foreman-doc-id]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :confirm-foreman-termination-v3
                        :params  {:id             app-id
                                  :foreman-doc-id foreman-doc-id}
                        :success #(>evt [::update-foremen app-id])}})))

(rf/reg-event-fx ::cancel-foreman-termination-request
  (fn [_ [_ email foreman-app-id]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :cancel-foreman-termination-request
                        :params  {:id             app-id
                                  :foreman-email  (or email "")
                                  :foreman-app-id foreman-app-id}
                        :success #(>evt [::update-foremen app-id])}})))

(rf/reg-event-fx ::cancel-foreman-termination-request-v3
  (fn [_ [_ email foreman-doc-id]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :cancel-foreman-termination-request-v3
                        :params  {:id             app-id
                                  :foreman-email  (or email "")
                                  :foreman-doc-id foreman-doc-id}
                        :success #(>evt [::update-foremen app-id])}})))

(rf/reg-event-fx ::send-foreman-message
  (fn [_ [_ foreman-doc-id]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :send-foreman-message
                        :params  {:id             app-id
                                  :foreman-doc-id foreman-doc-id}
                        :success #(>evt [::update-foremen app-id])}})))

(rf/reg-event-fx ::get-responsibility-timestamps.success
  (fn [{:keys [db]} [_ data]]
    {:db (assoc db ::selected-foreman-document-timestamps data)}))

(rf/reg-event-fx ::get-responsibility-timestamps
  (fn [{:keys [db]} [_ foreman-doc]]
    (when (:tj-document foreman-doc)
      {:action/query {:name    :foreman-responsibility-timestamps
                      :params  {:id             (router/path-parameter db :app-id)
                                :foreman-doc-id (:id foreman-doc)}
                      :success ::get-responsibility-timestamps.success}})))

(rf/reg-event-fx ::open-terminate-foreman
  (fn [{:keys [db]} [_ app-id foreman-doc termination-dialog]]
    {:db          (assoc db ::app-id app-id ::selected-foreman-document foreman-doc)
     :fx          [[:dispatch [::get-responsibility-timestamps foreman-doc]]]
     :dialog/show {:ltitle           :foreman.terminate.header
                   :size             :large
                   :type             :react
                   :dialog-component [termination-dialog]
                   :minContentHeight "50em"}}))

(rf/reg-sub ::foreman-applications :-> ::foreman-applications)

(rf/reg-sub ::foreman-v2-applications
  :<- [::foreman-applications]
  (fn [apps]
    (->> apps
         (filter #(-> %
                      foreman-util/foreman-item->tj-document
                      :schema-info
                      :name
                      keyword
                      (= :tyonjohtaja-v2)))
         seq)))

(rf/reg-event-db ::fetch-foreman-applications.success
  (fn [db [_ {:keys [applications]}]]
    (assoc db ::foreman-applications applications)))

(rf/reg-event-fx ::fetch-foreman-applications
  (fn [{:keys [db]}]
    (when-let [id (router/path-parameter db :app-id)]
      {:action/query {:name    :foreman-applications
                      :params  {:id id}
                      :success ::fetch-foreman-applications.success}})))

(rf/reg-event-db ::fetch-designer-applications.success
  (fn [db [_ {:keys [designers]}]]
    (assoc db ::designer-applications designers)))

(rf/reg-event-fx ::fetch-designer-applications
  (fn [{:keys [db]}]
    (when-let [id (router/path-parameter db :app-id)]
      {:action/query {:name    :designer-applications
                      :params  {:id id}
                      :success ::fetch-designer-applications.success}})))

(rf/reg-sub ::designer-applications :-> ::designer-applications)

(rf/reg-event-fx ::foreman-consent
  (fn [_ [_ doc-id checked?]]
    (let [app-id (common/application-id)]
      ;; Both of these commands also return the updated authModel, which is
      ;; already caught in common/command and stored to the documentDataService.
      {:action/command {:name    :foreman-consent
                        :params  {:id             app-id
                                  :consent        checked?
                                  :foreman-doc-id doc-id}}})))

(rf/reg-event-fx ::designer-consent
  (fn [_ [_ doc-id checked?]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :designer-consent
                        :params  {:id             app-id
                                  :consent        checked?
                                  :documentId     doc-id}}})))

(rf/reg-event-fx ::foreman-edit.success
  (fn [_ [_ app-id]]
    {:fx [[:application/load app-id]
          [:dispatch [::fetch-foreman-applications]]]}))

(rf/reg-event-fx ::declare-foreman
  (fn [_ [_ foreman-doc-id]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :declare-foreman
                        :params  {:id             app-id
                                  :foreman-doc-id foreman-doc-id}
                        :success #(>evt [::foreman-edit.success app-id])}})))

(rf/reg-event-fx ::submit-foreman
  (fn [_ [_ foreman-doc-id]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :submit-foreman
                        :params  {:id             app-id
                                  :foreman-doc-id foreman-doc-id}
                        :success #(>evt [::foreman-edit.success app-id])}})))

(rf/reg-event-db ::set-foreman-doc-type
  (fn [db [_ foreman-doc-id type]]
    (assoc-in db [::doc-type foreman-doc-id] (keyword type))))

(rf/reg-event-fx ::change-foreman-doc-type
  (fn [_ [_ foreman-doc-id type]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :change-foreman-doc-type
                        :params  {:id             app-id
                                  :foreman-doc-id foreman-doc-id
                                  :foreman-type   type}
                        :success #(>evt [::foreman-edit.success app-id])}})))

(rf/reg-event-fx ::acknowledge-foreman
  (fn [_ [_ foreman-doc-id]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :acknowledge-foreman
                        :params  {:id             app-id
                                  :foreman-doc-id foreman-doc-id}
                        :success #(>evt [::foreman-edit.success app-id])}})))

(rf/reg-event-fx ::approve-foreman
  (fn [_ [_ foreman-doc-id]]
    (let [app-id (common/application-id)]
      {:action/command {:name    :approve-foreman
                        :params  {:id             app-id
                                  :foreman-doc-id foreman-doc-id}
                        :success #(>evt [::foreman-edit.success app-id])}})))

(rf/reg-sub ::updated-email-value
  (fn [db [_ foreman-doc-id]]
    (get-in db [::updated-email foreman-doc-id])))
