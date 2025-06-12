(ns lupapalvelu.ui.suomifi-login.success
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.next.router-api :as router]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.suomifi-login.change-email :as ch-email]
            [lupapalvelu.ui.suomifi-login.components :refer [logout-button]]
            [lupapalvelu.ui.suomifi-login.subs :as subs]
            [re-frame.core :as rf]
            [reagent.core :as reagent]
            [reitit.frontend.easy :as rfe]))

(defn- redirect-to-login [email]
  (let [input (js/document.getElementById "passwordless-login-email")
        lang  (js/document.getElementById "passwordless-login-lang")
        form  (js/document.getElementById "passwordless-login-form")]
    (set! (.-value input) email)
    (set! (.-value lang) js/loc.currentLanguage)
    (.submit form)))

(rf/reg-fx ::submit-form
  (fn [email]
    ;; Submit hidden form _after_ reagent has finished rendering
    (reagent/after-render (fn [] (redirect-to-login email)))))

(defn- params->password-reset?
  [{:keys [reset-pw]}]
  (true? reset-pw))

(defmethod router/route-init-fn :suomifi-success
  [_]
  (fn [{:keys [db]} [_ visible?]]
    (if visible?
      {:fx [[:dispatch [::subs/fetch-users {:on-success (fn [resp]
                                                          (rf/dispatch [::fetch-users.success resp]))}]]]}
      {:db (dissoc db ::visible?)})))

(rf/reg-event-fx ::fetch-users.success
  (fn [{:keys [db]} [_  {:keys [accounts]}]]
    (let [reset? (-> db router/db->query-parameters params->password-reset?)]
      (if (and (not reset?) (= (count accounts) 1))
        {:fx [[::submit-form (-> accounts first :email)]]}
        {:db (assoc db ::visible? true)}))))

(rf/reg-sub ::visible?
  :-> ::visible?)

(rf/reg-sub ::email-change-requested?
  :<- [::ch-email/changed-emails]
  (fn [emails [_ email]]
    (contains? emails email)))

(defn- users-table []
  (let [users (<sub [::subs/users])]
    [:div
     [:h1 (loc :userinfo.accounts)]
     [:p
      (when (next users)
        [:<>
         [:strong (loc :userinfo.success-accounts (count users))]
         [:br]])
      (loc :userinfo.success-help)]
     [:table
      [:thead
       [:tr
        [:th (loc :userinfo.firstName)]
        [:th (loc :userinfo.lastName)]
        [:th (loc :users.table.email)]
        [:th ""]]]
      (->> users
           (map (fn [{:keys [id firstName lastName email]}]
                  [:tr {:key   (str "user-row-" id)
                        :style {:height "50px"}}
                   [:td firstName]
                   [:td lastName]
                   [:td
                    email
                    (when (<sub [::email-change-requested? email])
                      [:<>
                       [:br]
                       [:i (loc :userinfo.change-email-submitted)]])]
                   [:td.flex.flex--gap2
                    [:button.primary {:on-click #(redirect-to-login email)}
                     (loc :welcome.login)]
                    [:a {:href (rfe/href :suomifi-change-password {:user-id id})}
                     [:button.secondary {}
                      (loc :userinfo.changePassword)]]
                    [:a {:href (rfe/href :suomifi-change-email {:user-id id})}
                     [:button.secondary {}
                      (loc :userinfo.change-email-submit)]]]]))
           (into [:tbody]))]]))

(defn- login-form
  []
  [:form
   {:id     "passwordless-login-form"
    :action "/login/passwordless"
    :method "POST"
    :hidden true}
   ;;  autocomplete=off + readonly prevents browser from trying to save login details
   [:input {:id           "passwordless-login-email"
            :name         "email"
            :type         "text"
            :readOnly     true
            :autoComplete "off"}]
   [:input {:id   "passwordless-login-lang"
            :name "lang"}]])

(defn view []
  [:<>
   [login-form]
   (when (<sub [::visible?])
     [:<>
      [users-table]
      [logout-button]])])
