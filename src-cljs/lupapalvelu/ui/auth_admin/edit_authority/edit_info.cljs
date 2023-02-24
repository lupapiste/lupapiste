(ns lupapalvelu.ui.auth-admin.edit-authority.edit-info
  (:require [clojure.string :as s]
            [lupapalvelu.ui.auth-admin.edit-authority.state :as state]
            [lupapalvelu.ui.auth-admin.edit-authority.util :as util]
            [lupapalvelu.ui.common :refer [loc command]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.common.hub :as hub]
            [lupapalvelu.ui.util :refer [get-user-field]]
            [rum.core :as rum]))

(defn- save-change-in-field [user-info attr event]
  (swap! user-info assoc attr (.. event -target -value)))

(defn- info-textfield [attr value on-change]
  [:div {:key (str "info-textfield-" attr)}
   [:label.edit-user-info-label (loc (str "userinfo." (name attr)))
    [:input.form-input {:type      "text"
                        :name      attr
                        :value     value
                        :on-change on-change}]]])

(defn- update-user-info [user-info*]
  (let [success-fn (fn []
                     (reset! state/saving-info? false)
                     (util/get-authority)
                     (hub/send "organization-user-change"))
        error-fn   (fn [call]
                     (reset! state/saving-info? false)
                     (.ajaxError js/notify (clj->js call)))
        params     {:command               "update-auth-info"
                    :show-saved-indicator? true
                    :success               success-fn
                    :error                 error-fn}
        firstName  (:firstName @user-info*)
        lastName   (:lastName @user-info*)
        email      (:email @state/authority)
        new-email  (:email @user-info*)]
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

(rum/defcs edit-authority-info <
  rum/reactive
  (components/default-arg->local-atom ::user)
  [{user* ::user} user]
  (let [multiple-org-user? (-> user :orgAuthz (keys) (count) (> 1))
        not-same-domain?   (admin-and-user-have-different-email-domain user)
        disabled?          (or multiple-org-user? not-same-domain?)]
    [:div.edit-authority-info
     [:h2 (loc "auth-admin.edit-authority.user-info-title")]
     (for [field [:firstName :lastName :email]]
       (info-textfield field (get @user* field "") (partial save-change-in-field user* field)))
     (components/icon-button {:icon :lupicon-save
                              :wait? state/saving-info?
                              :on-click #(update-user-info user*)
                              :disabled? disabled?
                              :text-loc "save"
                              :class "primary"})
     (when disabled?
       [:p.error-message.edit-info
        (if multiple-org-user?
          (loc :error.authority-has-multiple-orgs)
          (loc :error.auth-admin-and-authority-have-different-domains))])]))
