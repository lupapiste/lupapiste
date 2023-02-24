(ns lupapalvelu.ui.sftp.configuration
  "SFTP admin configuration UI for sftpType and users."
  (:require [clojure.set :as set]
            [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))

(def ^:const LEGACY         "legacy")
(def ^:const GCS            "gcs")
(def ^:const BACKING-SYSTEM "backing-system")
(def ^:const CM             "case-management")
(def ^:const INVOICING      "invoicing")

(defn bad-username? [username]
  (not (re-matches #"^[a-z0-9_\-]+$" (ss/trim username))))

;; ---------------------------
;; Events
;; ---------------------------

(rf/reg-event-fx
  ::fetch-configuration
  (fn [{db :db} _]
    {:dispatch     [::toggle-edit false]
     :action/query {:name    :sftp-organization-configuration
                    :params  {:organizationId (::organization-id db)}
                    :success [:action/store-response :configuration
                              ::configuration]}}))

(rf/reg-event-fx
  ::set-organization-id
  (fn [{db :db} [_ organization-id]]
    {:db       (assoc db ::organization-id organization-id)
     :dispatch [::fetch-configuration]}))

(rf/reg-event-db
  ::toggle-edit
  (fn [db [_ edit?]]
    (if edit?
      (assoc db ::edit-configuration
             (update (::configuration db)
                     :users (fn [users]
                              (->> (sort-by :username users)
                                   (map-indexed #(vector %1 %2))
                                   (into {})))))
      (dissoc db ::edit-configuration))))

(rf/reg-event-db
  ::delete-user
  (fn [db [_ user-id]]
    (util/dissoc-in db [::edit-configuration :users user-id])))

(rf/reg-event-db
  ::add-user
  (fn [db _]
    (let [k (or (some->> db ::edit-configuration :users
                         keys (apply max) inc)
                0)]
      (assoc-in db [::edit-configuration :users k]
                {:username "" :type "" :permitTypes []}))))

(rf/reg-event-db
  ::update-errors
  (fn [db [_ response]]
    (update db ::edit-configuration
            assoc
            :errors (select-keys response [:text :directories])
            :saving? false)))

(rf/reg-event-fx
  ::save-configuration
  (fn [{db :db} _]
    (let [{:keys [sftpType users]} (ss/trimwalk (::edit-configuration db))]
      {:db (assoc-in db [::edit-configuration :saving?] true)
       :action/command
       {:name                  :update-sftp-organization-configuration
        :params                {:organizationId (::organization-id db)
                                :sftpType       sftpType
                                :users          (vals users)}
        :show-saved-indicator? true
        :success               ::fetch-configuration
        :error                 ::update-errors}})))

;; ---------------------------
;; Subs
;; ---------------------------

(rf/reg-sub
  ::edit-configuration
  (fn [db [_ & path]]
    (cond-> (::edit-configuration db)
      path (get-in (flatten path)))))

(rf/reg-sub
  ::available-permit-types
  :<- [::edit-configuration]
  (fn [edit-configuration [_ user-id]]
    (let [{pts :permitTypes users :users} edit-configuration]
      (->> (dissoc users user-id)
           vals
           (mapcat :permitTypes)
           set
           (set/difference (set pts))))))

(rf/reg-sub
  ::available-user-types
  :<- [::edit-configuration :users]
  (fn [users [_ user-id]]
    (let [invoicing-used? (->> (dissoc users user-id)
                               vals
                               (some #(= (:type %) INVOICING)))]
      (cond-> [BACKING-SYSTEM CM]
        (not invoicing-used?) (conj INVOICING)))))

(rf/reg-sub
  ::can-save?
  :<- [::edit-configuration :users]
  (fn [users _]
    (->> users
         vals
         (every? (fn [{:keys [username type permitTypes]}]
                   (and (not (bad-username? (ss/trim username)))
                        (ss/not-blank? type)
                        (if (= type INVOICING)
                          (empty? permitTypes)
                          (seq permitTypes))))))))

(rf/reg-sub
  ::usernames
  (fn [{:keys [::edit-configuration ::configuration]} _]
    (->> (:users edit-configuration)
         vals
         (map (comp ss/lower-case ss/trim :username))
         (filter ss/not-blank?)
         (concat (map :username (:users configuration)))
         distinct
         sort)))

;; ---------------------------
;; Components
;; ---------------------------

(defn viewer
  "Shows the the current configuration."
  []
  (when-let [{:keys [sftpType users]} (<sub [:value/get ::configuration])]
    [:div
     [:div.primary-note (str (loc :sftp.type.title) ": " (loc (util/kw-path :sftp sftpType)))]
     (if (some->> users seq (sort-by :username))
       [:table.pate-templates-table.w--max-70em
        [:thead
         [:tr
          [:th (loc :userinfo.username)]
          [:th (loc :sftp.use-case)]
          [:th (loc :sftp.permit-types)]]]
        [:tbody
         (for [{:keys [username type permitTypes]} (sort-by :username users)]
           [:tr {:key (common/unique-id "user")}
            [:td username]
            [:td (loc (util/kw-path :sftp type))]
            [:td (ss/join ", " permitTypes)]])]]
       [:div.primary-note (loc :sftp.no-users)])
     [:div.gap--t2
      [components/icon-button {:icon :lupicon-pen
                               :text-loc :edit
                               :class :primary
                               :on-click #(>evt [::toggle-edit true])}]]]))

(defn sftp-type-editor
  "Radio-group editor for sftpType (legacy vs. gcs)."
  []
  (when-let [sftpType (<sub [::edit-configuration :sftpType])]
    [:div.gap--v1
     [components/toggle-group
      #{sftpType}
      {:items    [{:text-loc :sftp.legacy :value LEGACY}
                  {:text-loc :sftp.gcs :value GCS}]
       :radio?   true
       :prefix   :radio
       :disabled? (<sub [::edit-configuration :saving?])
       :callback #(>evt [:value/set ::edit-configuration :sftpType %])}]]))

(defn user-editor
  "Editor table row. Combobox for the username, select for the user type (backing system,
  case management, invoicing) and (optional) permit-type selections. Note that each permit
  type can be selected only for one row."
  [id]
  (when-let [{:keys [username permitTypes
                     type]} (<sub [::edit-configuration :users id])]
    (let [disabled? (<sub [::edit-configuration :saving?])]
      [:tr
       [:td.w--min-20em.w--max-20em
        [components/combobox
         username
         {:items     (map #(hash-map :text %) (<sub [::usernames]))
          :callback  #(>evt [:value/set ::edit-configuration :users id :username (ss/trim %)])
          :required? true
          :disabled  disabled?
          :invalid?  (and (ss/not-blank? username) (bad-username? username))}]]
       [:td [components/dropdown
             type
             {:items     (for [v (<sub [::available-user-types id])]
                           {:value v :text (loc (util/kw-path :sftp v))})
              :required? true
              :disabled? disabled?
              :callback  (fn [selected]
                           (>evt [:value/set ::edit-configuration :users id :type selected])
                           (when (= selected INVOICING)
                             (>evt [:value/set ::edit-configuration :users id :permitTypes []])))}]]
       [:td.w--100
        (when-not (= type INVOICING)
          (let [permit-types (<sub [::available-permit-types id])]
            [components/toggle-group
             (set/intersection permit-types (set permitTypes))
             {:items     (map #(hash-map :text % :value %) (sort permit-types))
              :disabled? disabled?
              :callback  #(>evt [:value/set ::edit-configuration :users id :permitTypes %])}]))]
       [:td [components/icon-button {:icon       :lupicon-remove
                                     :icon-only? true
                                     :text-loc   :save
                                     :disabled?  disabled?
                                     :class      :tertiary
                                     :on-click   #(>evt [::delete-user id])}]]])))

(defn editor
  "Editor view."
  []
  (let [saving? (<sub [::edit-configuration :saving?])]
    [:div
     [sftp-type-editor]
     [:table.pate-templates-table.w--max-70em
      [:thead
       [:tr
        [:th (loc :userinfo.username)]
        [:th (loc :sftp.use-case)]
        [:th (loc :sftp.permit-types)]
        [:th]]]
      [:tbody
       [:<>
        (for [id (keys (<sub [::edit-configuration :users]))]
          ^{:key id} [user-editor id])]
       [:tr
        [:td {:col-span 4}
         [components/icon-button {:icon      :lupicon-circle-plus
                                  :class     :primary.gap--v1
                                  :disabled? saving?
                                  :text-loc  :add
                                  :on-click  #(>evt [::add-user])}]]]]]

     (when-let [{:keys [text directories]} (<sub [::edit-configuration :errors])]
       [:div.gap--v2.dsp--inline-block
        [:div.error-note
         [:div.pad--v2.pad--r2
          [:span.txt--bold (loc text)]
          (when directories
            (into [:ul]
                  (for [dir directories]
                    [:li.txt--code dir])))]]])

     [:div.flex.flex--gap2.gap--v1
      [components/icon-button {:icon     :lupicon-save
                               :text-loc :save
                               :wait?    saving?
                               :enabled? (<sub [::can-save?])
                               :class    :primary
                               :on-click #(>evt [::save-configuration])}]
      [:button.secondary
       {:on-click #(>evt [::toggle-edit false])
        :disabled saving?}
       (loc :cancel)]]]))

(defn configuration
  "The root component that defers either to `viewer` or `editor` depending on the state."
  []
  (r/with-let [_ (>evt [::hub/subscribe :organization::open
                        #(>evt [::set-organization-id (:organizationId %)])
                        ::open-hub-id])
               _ (>evt [::hub/subscribe :organization::scope-added
                        #(>evt [::fetch-organization-id])
                        ::scope-hub-id])]
    [:div.gap--v6
     [:h2.h2 (loc :sftp.title)]
     (if (<sub [:value/get ::edit-configuration])
       [editor]
       [viewer])]
    (finally
      (>evt [::hub/unsubscribe ::open-hub-id ::scope-hub-id]))))

(defn mount-component [dom-id]
  (rd/render [configuration] (.getElementById js/document dom-id)))

(defn ^:export start [dom-id params]
  (>evt [::set-organization-id (common/->cljs (common/oget params "orgId"))])
  (mount-component dom-id))
