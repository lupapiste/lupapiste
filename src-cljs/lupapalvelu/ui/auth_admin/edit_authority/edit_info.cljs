(ns lupapalvelu.ui.auth-admin.edit-authority.edit-info
  (:require [clojure.string :as s]
            [lupapalvelu.ui.auth-admin.edit-authority.state :as state]
            [lupapalvelu.ui.auth-admin.edit-authority.util :as util]
            [lupapalvelu.ui.common :refer [loc query command]]
            [lupapalvelu.ui.components :as components]
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
        error-fn   (fn [call]
                     (reset! state/saving-info? false)
                     (.ajaxError js/notify (clj->js call)))
        params     {:command               "update-auth-info"
                    :show-saved-indicator? true
                    :success               success-fn
                    :error                 error-fn}
        firstName  (:firstName @user-info)
        lastName   (:lastName @user-info)
        email      (:email @state/authority)
        new-email  (:email @user-info)]
    (reset! state/saving-info? true)
    (command params :firstName firstName
                    :lastName lastName
                    :email email
                    :new-email new-email)))

(defn- admin-and-user-have-different-email-domain [authority]
  (let [auth-admin-email    (get-user-field "email")
        authority-email     (:email authority)
        email-domain-picker (fn [email] (-> email (s/split #"@") (last)))]
    (not= (email-domain-picker authority-email)
          (email-domain-picker auth-admin-email))))

(rum/defc edit-authority-info < rum/reactive
  []
  (let [user               (rum/react state/authority)
        user-info          (atom {:firstName (:firstName user)
                                  :lastName  (:lastName user)
                                  :email     (:email user)})
        multiple-org-user? (-> @state/authority :orgAuthz (keys) (count) (> 1))
        not-same-domain?   (admin-and-user-have-different-email-domain @state/authority)
        disabled?          (or multiple-org-user? not-same-domain?)]
    [:div.edit-authority-info
     [:h2 (loc "auth-admin.edit-authority.user-info-title")]
     (map #(info-textfield user-info % (get user %)) [:firstName :lastName :email])
     (components/icon-button {:icon :lupicon-save
                              :wait? state/saving-info?
                              :on-click #(update-user-info user-info)
                              :disabled? disabled?
                              :text-loc "save"
                              :class "primary"})
     (when disabled?
       [:p.error-message.edit-info
        (if multiple-org-user?
          (loc :error.authority-has-multiple-orgs)
          (loc :error.auth-admin-and-authority-have-different-domains))])]))