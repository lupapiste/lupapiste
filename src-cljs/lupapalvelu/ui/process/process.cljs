(ns lupapalvelu.ui.process.process
  (:require [clojure.set :as set]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.shared-quill :as quill]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.datepicker :as datepicker]
            [lupapalvelu.ui.components.day-edit :as day-edit]
            [lupapalvelu.ui.components.quill :as quill-component]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.process.replies :as replies]
            [lupapalvelu.ui.user :as user]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(rf/reg-sub
  ::show-done?
  :-> (comp boolean ::show-done?))

(rf/reg-event-db
  ::set-show-done
  (fn [db [_ show-done?]]
    (assoc db ::show-done? show-done?)))

(rf/reg-sub
  ::show-full?
  (fn [db [_ id]]
    (-> db ::show-full (contains? id))))

(rf/reg-event-db
  ::set-show-full
  (fn [db [_ id]]
    (update db ::show-full (fnil conj #{}) id)))

(rf/reg-event-db
  ::fetch-supplement-requests.success
  (fn [db [_ {:keys [supplement-requests]}]]
    (assoc db ::supplement-requests supplement-requests)))

(rf/reg-sub
  ::supplement-requests
  :-> ::supplement-requests)

(rf/reg-sub
  ::supplement-requests-in-state
  :<- [::supplement-requests]
  (fn [requests [_ state]]
    (filter #(-> % :state keyword (= state))
            requests)))

(rf/reg-event-fx
  ::fetch-supplement-requests
  (fn [{db :db} _]
    (let [app-id (common/application-id)]
      {:db           (assoc db ::locations-operations :loading)
       :action/query {:name    :get-supplement-requests
                      :params  {:id app-id}
                      :success [::fetch-supplement-requests.success]}})))

(rf/reg-sub
  ::invalid-due-date?
  (fn [db]
    (let [{:keys [due-date]} (::editing db)]
      (< due-date (datepicker/day-begin-ts (js/Date.))))))

(rf/reg-sub
  ::invalid-data?
  :<- [::invalid-due-date?]
  :<- [:value/get ::editing]
  (fn [[idd? {:keys [text]}]]
    (or (every? (comp ss/blank? :insert) text)
        idd?)))

(rf/reg-event-fx
  ::save-request.success
  (fn [{:keys [db]} _]
    {:db (dissoc db ::editing)
     :fx [[:dispatch [::fetch-supplement-requests]]]}))

(rf/reg-event-fx
  ::create-request
  (fn [{:keys [db]} _]
    (let [app-id (common/application-id)]
      {:action/command {:name                  :create-supplement-request
                        :params                (assoc (::editing db)
                                                 :id app-id)
                        :success               [::save-request.success]
                        :show-saved-indicator? true}})))

(rf/reg-event-fx
  ::update-request
  (fn [{:keys [db]} _]
    (let [app-id (common/application-id)]
      {:action/command {:name                  :update-supplement-request
                        :params                (-> db
                                                   ::editing
                                                   (select-keys [:id :text :due-date])
                                                   (set/rename-keys {:id :request-id})
                                                   (assoc :id app-id))
                        :success               [::save-request.success]
                        :show-saved-indicator? true}})))

(rf/reg-event-fx
  ::change-request-state
  (fn [_ [_ id command]]
    (let [app-id (common/application-id)]
      {:action/command {:name                  command
                        :params                {:id         app-id
                                                :request-id id}
                        :success               [::save-request.success]
                        :show-saved-indicator? true}})))

(rf/reg-event-fx
  ::delete-request
  (fn [_ [_ id]]
    (let [app-id (common/application-id)]
      {:action/command {:name                  :delete-supplement-request
                        :params                {:id         app-id
                                                :request-id id}
                        :success               [::save-request.success]
                        :show-saved-indicator? true}})))

(rf/reg-event-fx
  ::confirm-withdraw-request
  (fn [_ [_ id]]
    {:dialog/show {:type  :yes-no
                   :text  (common/loc :supplement-request.withdraw-confirm)
                   :event [::change-request-state id :withdraw-supplement-request]}}))

(rf/reg-event-fx
  ::confirm-mark-request-done
  (fn [_ [_ id replied?]]
    (let [event [::change-request-state id :mark-supplement-request-done]]
      (if replied?
        {:fx [[:dispatch event]]}
        {:dialog/show {:type  :yes-no
                       :text  (common/loc :supplement-request.mark-done-confirm)
                       :event event}}))))

(defn timestamp+user [field request]
  (let [user-field (keyword (str (name field) "-by"))
        ts         (-> field request)
        {:keys [firstName lastName]} (user-field request)]
    (when ts
      [:span (str (-> ts js/util.finnishDateAndTime)
                  " "
                  (str firstName " " lastName))])))

(defn in-state? [state & required-states]
  (->> state keyword (contains? (set required-states))))

(defn actions [{:keys [id] :as request}]
  (let [authority?    (user/authority?)
        in-state?     (partial in-state? (:state request))
        can-edit?     (and (in-state? :draft)
                           (<sub [:auth/application? :update-supplement-request]))
        can-send?     (and (in-state? :draft)
                           (<sub [:auth/application? :send-supplement-request]))
        can-reply?    (and (in-state? :requested)
                           (<sub [:auth/application? :reply-supplement-request])
                           (not authority?))
        can-withdraw? (and (in-state? :requested)
                           (<sub [:auth/application? :withdraw-supplement-request]))
        can-ok?       (and (in-state? :requested :replied)
                           (<sub [:auth/application? :mark-supplement-request-done]))
        replied?      (in-state? :replied)]
    [:div.flex.flex--right.flex--gap1
     (when can-send? [:button.primary
                      {:on-click #(>evt [::change-request-state id :send-supplement-request])}
                      (common/loc :supplement-request.send)])
     (when can-edit? [:<>
                      [:button.secondary
                       {:on-click #(>evt [:value/set ::editing request])}
                       (common/loc :edit)]
                      [:button.danger
                       {:on-click #(>evt [::delete-request id])}
                       (common/loc :remove)]])
     (when can-reply? [:button.primary
                       {:on-click #(>evt [::change-request-state id :reply-supplement-request])}
                       (common/loc :supplement-request.reply)])
     (when can-ok? [:button
                    {:class    (if replied? "primary" "secondary")
                     :on-click #(>evt [::confirm-mark-request-done id replied?])}
                    (common/loc :ok)])
     (when can-withdraw? [:button.secondary
                          {:on-click #(>evt [::confirm-withdraw-request id])}
                          (common/loc :supplement-request.withdraw)])]))

(defn request-row [state {:keys [id] :as request} cols]
  (let [in-state?       (partial in-state? state)
        show-full?      (<sub [::show-full? id])
        text-tags       (-> request :text quill/->tags)
        paragraph-count (-> text-tags rest count)
        long-text?      (> paragraph-count 3)
        ;; Always show 3 first paragraphs
        displayed-tags  (if (or show-full?
                                (not long-text?))
                          text-tags
                          (vec (drop-last (- paragraph-count 3) text-tags)))]
    [:<>
     [:tr
      [:td.pad--1 [timestamp+user :modified request]]
      [:td.pad--1 (common/format-timestamp (:due-date request))]
      (when (in-state? :replied :done)
        [:td.pad--1 [timestamp+user :replied request]])
      [:td.pad--1
       [actions request]]]
     [:tr
      [:td.pad--1 {:col-span cols}
       displayed-tags
       (when (and long-text?
                  (not show-full?))
         [components/click-link {:click    #(>evt [::set-show-full id])
                                 :text-loc :supplement-request.show-full}])]]
     [:tr.empty {:aria-hidden true} [:td]]]))

(defn request-table [state]
  (let [in-state?   (partial in-state? state)
        requests    (<sub [::supplement-requests-in-state state])
        headers     (cond-> [(if (in-state? :draft) :modified :requested) :due-date]
                      (in-state? :replied :done) (concat [:replied]))
        show-done?  (<sub [::show-done?])
        show-table? (or (not (in-state? :done))
                        show-done?)]
    (when (seq requests)
      [:div.pad--t2
       [:h3 (str
              (common/loc (util/kw-path :supplement-request.state-title state))
              " (" (count requests) ")")]
       (when (in-state? :done)
         [components/toggle show-done?
          {:text-loc :supplement-request.show-done
           :prefix   :blockbox
           :callback #(>evt [::set-show-done %])
           :class    "gap--t2 gap--b2"}])
       (when show-table?
         [:table.w--100.table.no-stripes.gap--b2
          [:thead
           (into [:tr.bg--gray]
                 (concat (for [header headers]
                           [:th.txt--left.pad--1
                            {:key header}
                            (common/loc (util/kw-path :supplement-request header))])
                         [[:th.txt--left.pad--1 ""]]))]
          [:tbody
           (for [request requests]
             ^{:key (:id request)} [request-row state request (-> headers count inc)])]])])))

(defn request-form []
  (let [{:keys [id text due-date]} (<sub [:value/get ::editing])
        submit-disabled?  (<sub [::invalid-data?])
        invalid-due-date? (<sub [::invalid-due-date?])]
    [:div.form-grid.form-grid--no-border
     [:div.row
      [:div.row
       [:div.col-4
        [:label.required {:for "supplement-request-text"} (common/loc :supplement-request)]
        [quill-component/quill-edit text {:id        "supplement-request-text"
                                          :callback  #(>evt [:value/set ::editing :text %])
                                          :required? true
                                          :readOnly  true
                                          :modules   {:toolbar quill-component/minimal-toolbar}}]]]
      [:div.row
       [:div.col-1
        [:label.required {:for "supplement-request-due-date"} (common/loc :supplement-request.due-date)]
        [day-edit/day-edit due-date {:id        "supplement-request-due-date"
                                     :callback  #(>evt [:value/set ::editing :due-date (datepicker/day-begin-ts %)])
                                     :invalid?  (and (some? due-date) invalid-due-date?)
                                     :required? true}]]]]
     [:div.row
      (components/icon-button {:class     :primary.gap--r1
                               :on-click  #(>evt [(if id ::update-request ::create-request)])
                               :icon      :lupicon-save
                               :disabled? submit-disabled?
                               :text-loc  :save})
      (components/icon-button {:class    :secondary
                               :on-click #(>evt [:value/set ::editing nil])
                               :icon     :lupicon-remove
                               :text-loc :cancel})]]))

(defn request-editor []
  (let [data        (<sub [:value/get ::editing])
        can-create? (<sub [:auth/application? :create-supplement-request])]
    (if data
      [request-form]
      (when can-create?
        [:div.pad--v2 [:button.primary
                       {:on-click #(>evt [:value/set ::editing {}])}
                       (common/loc :supplement-request.create)]]))))

(defn supplement-requests []
  (let [editing? (<sub [:value/get ::editing])
        requests (<sub [::supplement-requests])]
    [:<> [:h2 (common/loc :supplement-requests)]
     [request-editor]
     (when-not editing?
       (if (seq requests)
         [:<>
          [request-table :draft]
          [request-table :requested]
          [request-table :replied]
          [request-table :done]]
         [:span (common/loc :supplement-request.none)]))]))

(defn process-view []
  [:div
   [:h1 (common/loc :application.tabProcess)]
   [supplement-requests]
   [replies/replies]])

(defonce mounter (ui-dom/init :reagent (constantly process-view)))

(defn ^:export start [dom-id]
  (>evt [::fetch-supplement-requests])
  (>evt [::replies/fetch-replies])
  (>evt [::replies/fetch-replies-targets])
  (ui-dom/mount! mounter dom-id))
