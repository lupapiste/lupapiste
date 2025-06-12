(ns lupapalvelu.ui.notices.data
  "Notice resolution view logic.

  Related app-db contents under datapath:

  `:visible?`   Whether the view/section is currently visible.
  `:resolution` Published resolution.
  `:editing`    Currently edited draft resolution."
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.data :as data]
            [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.shared-quill :as sh-quill]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.date :as date]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [re-frame.fx]
            [sade.strings :as ss]))

(def set-d    (partial data/set-d ::data))
(def get-d    (partial data/get-d ::data))
(def unset-d  (partial data/unset-d ::data))

(defn- datetime-or-nil
  [ts]
  (some-> ts date/finnish-datetime))

(defn- get-attachment [{:keys [attachment-id]}]
  (some->> attachment-id
           (.rawAttachment js/lupapisteApp.services.attachmentsService)
           common/->cljs))

(defn- query-attachment-if-needed [{:keys [attachment-id]
                                    :as   resolution}]
  (when attachment-id
    (when-not (get-attachment resolution)
      (.queryOne js/lupapisteApp.services.attachmentsService
                 attachment-id))))

;; -----------------------------
;; Events
;; -----------------------------

(rf/reg-event-fx ::poll-notice-resolution.result
  (fn [_ [_ {:keys [ok waiting] :as response}]]
    (cond
      (not ok) nil ;; Fail silently
      waiting  {:action/query {:name    :poll-notice-resolution
                               :params  {:id (common/application-id)}
                               :success [::poll-notice-resolution.success]}}
      :else    {:fx [[:dispatch [::notice-resolution.success response]]]})))

(rf/reg-event-fx ::notice-resolution.success
  (fn [{db :db} [_ {:keys [resolution]}]]
    (let [{:keys [published attachment-id
                  job-id]} resolution]
      (when attachment-id
        (query-attachment-if-needed resolution))
      (cond-> {:db (cond
                     (nil? resolution) (-> db
                                           (unset-d :resolution)
                                           (unset-d :editing))
                     published         (-> db
                                           (unset-d :editing)
                                           (set-d :resolution resolution))
                     :else             (-> db
                                           (unset-d :resolution)
                                           (set-d :editing resolution)))}
        job-id (assoc :action/query {:name    :poll-notice-resolution
                                     :params  {:id (common/application-id)}
                                     :success [::poll-notice-resolution.result]
                                     :error   [::poll-notice-resolution.result]})))))

(rf/reg-event-db ::attachments-changed
  (fn [db]
    (cond-> db
      (get-d db :resolution :attachment-id)
      (set-d :resolution :trigger (gensym)))))

(rf/reg-event-fx ::notice-resolution
  (fn [_]
    {:action/query {:name    :notice-resolution
                    :params  {:id (common/application-id)}
                    :success [::notice-resolution.success]}}))

(rf/reg-event-fx ::toggle-view
  (fn [{db :db} [_ visible?]]
    (if visible?
      {:db (set-d db {:visible? true})
       :fx [[:dispatch [::notice-resolution]]
            [:dispatch [:auth/refresh]]
            [:dispatch [::hub/subscribe :attachmentsService::changed
                        #(>evt [::attachments-changed]) ::notice-hub-id]]]}
      {:db (unset-d db)
       :fx [[:dispatch [::hub/unsubscribe ::notice-hub-id]]]})))

(rf/reg-event-fx ::toggle-editing
  (fn [{db :db} [_ editing?]]
    (let [editing (get-d db :editing)]
      (cond
        (and editing? editing) nil
        editing?               {:db (set-d db :editing {})}
        :else
        {:db             (unset-d db :editing)
         :action/command {:name   :delete-draft-notice-resolution
                          :params {:id (common/application-id)}}}))))

(rf/reg-event-db ::save-notice-resolution.success
  (fn [db [_ {:keys [modified]}]]
    (cond-> db
      (get-d db :editing) (set-d :editing :modified modified))))

(rf/reg-event-fx ::save-resolution
  (fn [{db :db}]
    (when-let [m (some-> db (get-d :editing) (dissoc :modified :user))]
      {:action/command {:name :save-notice-resolution
                        :params (assoc m :id (common/application-id))
                        :success [::save-notice-resolution.success]}})))

(rf/reg-event-fx ::edit
  (fn [{db :db} [_ k v]]
    {:db                (set-d db :editing k v)
     :dispatch-debounce {:id      :save-resolution
                         :action  :dispatch
                         :event   [::save-resolution]
                         :timeout 750}}))

(rf/reg-event-fx ::publish-notice-resolution.success
  (fn [_ [_ response]]
    {:fx [[:dispatch [::notice-resolution.success response]]
          [:dispatch [:auth/refresh]]]
     :application/load (common/application-id)}))

(rf/reg-event-fx ::publish-notice-resolution.error
  (fn [{db :db} [_ response]]
    {:db (unset-d db :editing :waiting?)
     :indicator/saved response}))

(rf/reg-event-fx ::publish-resolution
  (fn [{db :db}]
    {:db             (set-d db :editing :waiting? true)
     :action/command {:name    :publish-notice-resolution
                      :params  (assoc (get-d db :editing)
                                      :id (common/application-id)
                                      :lang (common/get-current-language))
                      :success [::publish-notice-resolution.success]
                      :error   [::publish-notice-resolution.error]}}))

(rf/reg-event-fx ::delete-published-notice-resolution.success
  (fn [{db :db}]
    {:db (-> db
             (unset-d :resolution))
     :fx [[:dispatch [:auth/refresh]]]
     :application/load (common/application-id)}))

(rf/reg-event-fx ::delete-resolution
  (fn []
    {:action/command {:name :delete-published-notice-resolution
                      :params  {:id (common/application-id)}
                      :success [::delete-published-notice-resolution.success]}}))


;; -----------------------------
;; Subs
;; -----------------------------

(rf/reg-sub ::data :-> get-d)

(rf/reg-sub ::editing
  :<- [::data]
  :-> :editing)

(rf/reg-sub ::editing?
  :<- [::editing]
  :-> boolean)

(rf/reg-sub ::waiting?
  :<- [::editing]
  :-> (comp boolean :waiting?))

(rf/reg-sub ::editing-state
  :<- [::editing]
  :-> :state)

(rf/reg-sub ::editing-text
  :<- [::editing]
  :-> :text)

(rf/reg-sub ::editing-time
  :<- [::editing]
  :-> (comp datetime-or-nil :modified))

(rf/reg-sub ::can-publish?
  :<- [::editing-state]
  :<- [::editing-text]
  (fn [[state text]]
    (and (ss/not-blank? state)
         (some (comp ss/not-blank? :insert)
               text))))

(rf/reg-sub ::visible?
  :<- [::data]
  :-> :visible?)

(rf/reg-sub ::resolution
  :<- [::data]
  :-> :resolution)

(rf/reg-sub ::resolution-state
  :<- [::resolution]
  :-> :state)

(rf/reg-sub ::resolution-tags
  :<- [::resolution]
  :-> (comp sh-quill/->tags :text))

(rf/reg-sub ::resolution-name
  :<- [::resolution]
  (fn [{:keys [user]}]
    (ss/trim-join " " (:firstName user) (:lastName user))))

(rf/reg-sub ::resolution-published?
  :<- [::resolution]
  :-> (comp boolean :published))

(rf/reg-sub ::resolution-date
  :<- [::resolution]
  :-> (comp date/finnish-date :published))

(rf/reg-sub ::resolution-attachment
  :<- [::resolution]
  (fn [{:keys [job-id attachment-id] :as resolution}]
    (cond
      attachment-id
      (some->> (get-attachment resolution)
               :latestVersion :filename
               (assoc {:url (str "/api/raw/latest-attachment-version?attachment-id="
                                 (:attachment-id resolution))}
                      :filename))
      (not job-id)
      {:error :notice.attachment.error})))
