(ns lupapalvelu.ui.auth-admin.edit-authority.edit-info
  (:require [clojure.string :as s]
            [lupapalvelu.ui.auth-admin.edit-authority.state :as state]
            [lupapalvelu.ui.auth-admin.edit-authority.util :as util]
            [lupapalvelu.ui.common :refer [loc query command]]
            [lupapalvelu.ui.util :refer [get-user-field]]
            [rum.core :as rum]))

(defn- save-change-in-field [user-info attr event]
  (swap! user-info assoc attr (.. event -target -value)))

(defn- info-textfield [user-info attr value]
  [:div {:key (str "info-textfield-" attr)}
   [:label.edit-user-info-label (loc (str "userinfo." (name attr)))
    [:input.form-input {:type      "text"
                        :name      attr
                        :value     value
                        :on-change (partial save-change-in-field user-info attr)}]]])

(defn- update-user-info [user-info]
  (let [success-fn (fn []
                     (reset! state/saving-info? false)
                     (util/get-authority))
        params {:command               "update-auth-info"
                :show-saved-indicator? true
                :success               success-fn}
        firstName (:firstName @user-info)
        lastName (:lastName @user-info)
        email (:email @state/authority)
        new-email (:email @user-info)]
    (reset! state/saving-info? true)
    (command params :firstName firstName
             :lastName lastName
             :email email
             :new-email new-email)))

(defn- admin-and-user-have-same-email-domain [authority]
  (let [auth-admin-email (get-user-field "email")
        authority-email (:email authority)
        email-domain-picker (fn [email] (-> email (s/split #"@") (last)))]
    (= (email-domain-picker authority-email)
       (email-domain-picker auth-admin-email))))

(defn- auth-admin-can-edit-authority-info? [authority]
  (let [user-has-one-org? (-> authority :orgAuthz (keys) (count) (= 1))
        same-domain? (admin-and-user-have-same-email-domain authority)]
    (not (and user-has-one-org? same-domain?))))

(rum/defc edit-authority-info < rum/reactive
  []
  (let [user (rum/react state/authority)
        saving? (rum/react state/saving-info?)
        user-info (atom {:firstName (:firstName user)
                         :lastName  (:lastName user)
                         :email     (:email user)})
        disabled (auth-admin-can-edit-authority-info? user)]
    [:div
     [:h2 (loc "edit-authority.user-info-title")]
     (map #(info-textfield user-info % (get user %)) [:firstName :lastName :email])
     [:button.primary {:disabled disabled
                       :on-click #(update-user-info user-info)}
      (if saving?
        [:i.wait.spin.lupicon-refresh]
        [:i.lupicon-save])
      [:span (loc "save")]]]))