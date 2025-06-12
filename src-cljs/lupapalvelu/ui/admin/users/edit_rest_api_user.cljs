(ns lupapalvelu.ui.admin.users.edit-rest-api-user
  "REST API users have orgAuthz like authorities but cannot be edited by the organization's authority admin
  since they are not technically authority users (they have the `rest-api` role instead of `authority`)"
  (:require [clojure.set :as set]
            [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

;; User fetching

(rf/reg-sub ::user :-> ::user)

(rf/reg-event-db ::set-user
  (fn [db [_ data]]
    (assoc db ::user data ::fields {})))

(rf/reg-event-fx ::fetch-user
  (fn [{:keys [db]} [_ email]]
    {:db           (dissoc db ::user)
     :action/query {:name    :users
                    :params  {:email email :role "rest-api"}
                    ;; Email only matches exactly so only one user found, if any
                    :success #(do (>evt [::set-user (-> % :users first)])
                                  (>evt [::set-field
                                         :orgAuthz
                                         (->> % :users first :orgAuthz (util/map-values (partial ss/join "\n")))]))}}))

;; Form fields

(rf/reg-sub ::fields :-> ::fields)

(rf/reg-event-db ::set-field
  (fn [db [_ attr value]]
    (assoc-in db [::fields attr] value)))

(rf/reg-event-db ::update-field
  (fn [db [_ attr & xs]]
    (apply update-in db [::fields attr] xs)))

;; Saving

(rf/reg-event-fx ::save-changes
  (fn [{:keys [db]} _]
    {:action/command {:name    :update-rest-api-user
                      :params  {:email    (-> db ::user :email)
                                :orgAuthz (->> db ::fields :orgAuthz (util/map-values #(re-seq #"\w+" %)))}
                      :success #(hub/send "admin::editRestApiUserFinished")}}))

;; View

(defn org-authz-form
  "Form for editing all orgAuthz as textareas with auths on different lines"
  []
  (let [{:keys [orgAuthz]} (<sub [::fields])]
    [:div.top-marginM
     [:table
      [:thead
       [:tr
        [:th (loc :admin.add-dialog.organization-code)]
        [:th (loc :auth-admin.add-dialog.roles)]
        [:th (loc :admin.actions)]]]
      (->> orgAuthz
           (map (fn [[org-id authz-str]]
                  [:tr
                   [:td
                    [:input
                     {:type      :text
                      :value     org-id
                      :on-change #(>evt [::update-field :orgAuthz set/rename-keys {org-id (util/tval %)}])}]]
                   [:td
                    [:textarea
                     {:value     authz-str
                      :on-change #(>evt [::update-field :orgAuthz assoc org-id (util/tval %)])}]]
                   [:td
                    [:button.secondary
                     {:on-click #(>evt [::update-field :orgAuthz dissoc org-id])}
                     (loc :remove)]]]))
           (into [:tbody]))]
     [:button.primary
      {:on-click #(>evt [::update-field :orgAuthz assoc "" ""])}
      (loc :add)]]))

(defn update-form
  "Fields to update the REST API user (or cancel)"
  []
  (let [user (<sub [::user])]
    [:div
     [:div (loc :email) ": " (:email user)]
     [:div (loc :username)  ": " (:username user)]
     [org-authz-form]
     [:div.top-marginL
      [:button.form-input.primary
       {:on-click #(>evt [::save-changes])}
       (loc :save)]
      [:button.form-input.cancel
       {:on-click #(>evt [::set-user nil])}
       (loc :cancel)]]]))

(defn search-bar
  "Search for the user by email and load their info for editing"
  []
  (let [{:keys [email]} (<sub [::fields])]
    [:<>
     [:label.edit-user-info-label (loc :email)
      [:input.form-input
       {:type      "text"
        :value     email
        :on-key-up #(when (= (.-keyCode %) components/KEY-ENTER)
                      (>evt [::fetch-user email]))
        :on-change #(>evt [::set-field :email (util/tval %)])}]]
     [:button
      {:on-click #(>evt [::fetch-user email])}
      (loc :search)]]))

(defn view
  "Dialog for editing the organizations a rest api user belongs to.
  The rest api user has to first be created separately."
  []
  (let [user (<sub [::user])]
    [:div
     [:h2 (loc :admin.edit-rest-api-user.title)]
     (if user
       [update-form]
       [search-bar])]))

(defonce mounter (ui-dom/init :reagent (constantly view)))

(defn ^:export start [dom-id _]
  (ui-dom/mount! mounter dom-id))
