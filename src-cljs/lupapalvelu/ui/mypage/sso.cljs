(ns lupapalvelu.ui.mypage.sso
  "UI for connecting/disconnecting user from single-sign-on."
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.session :as session]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]))

(rf/reg-event-fx
  ::connect-sso.success
  (fn [_ [_ data]]
    (hub/send "login" data) ;; Somewhat of a hack to get mypage to update.
    {:fx [[:dispatch [::session/initialize data]]]}))

(rf/reg-event-fx
  ::disconnect-sso.success
  (fn [{:keys [db]} [_ data]]
    {:db (assoc db ::reset-sent true)
     :fx [[:dispatch [::session/initialize data]]]}))

(rf/reg-sub ::reset-sent
  :-> ::reset-sent)

(rf/reg-event-fx
  ::connect-sso
  (fn [_]
    {:action/command {:name    :connect-sso
                      :success [::connect-sso.success]}}))

(rf/reg-event-fx
  ::disconnect-sso
  (fn [_]
    {:action/command {:name    :disconnect-sso
                      :success [::disconnect-sso.success]}}))

(rf/reg-event-fx
  ::open-connect-dialog
  (fn [_]
    {:dialog/show {:ltitle   :areyousure
                   :text     (common/loc :userinfo.connect-sso-confirm)
                   :type     :yes-no
                   :callback #(>evt [::connect-sso])}}))

(defn sso-setting []
  (let [{:keys [ad-login ad-login-available?]} (<sub [::session/user])
        reset-sent (<sub [::reset-sent])]
    (cond
      ad-login
      [:div
       [:h2 (common/loc :userinfo.changePassword)]
       [:div.form-grid.no-padding
        [:div.row
         [:span (common/loc :userinfo.changePassword-sso)]]
        [:div.row
         (if reset-sent
           [:span (str (common/loc :reset.sent.1) " " (common/loc :reset.sent.2))]
           (components/icon-button {:class    :secondary
                                                 :text-loc :userinfo.disconnect-sso
                                                 :on-click #(>evt [::disconnect-sso])}))]]]
      ad-login-available?
      [:div
       [:h2 (common/loc :userinfo.sso)]
       [:div.form-grid.no-padding
        [:div.row
         (components/icon-button {:class    :secondary
                                  :text-loc :userinfo.connect-sso
                                  :on-click #(>evt [::open-connect-dialog])})]]])))

(defonce mounter (ui-dom/init :reagent (constantly sso-setting)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
