(ns lupapalvelu.ui.admin.users.create-authority-user
  (:require [rum.core :as rum]
            [lupapalvelu.ui.common :refer [loc query command]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.hub :as hub]))

(defonce view-state {:username     (atom "")
                     :firstname    (atom "")
                     :lastname     (atom "")
                     :organization (atom "")})

(def inputs
  [{:label-loc "admin.add-dialog.organization-code"
    :input-id  "admin.create-auth-admin.organizationCode"
    :atom-key  :organization}
   {:label-loc "userinfo.username"
    :input-id  "admin.create-auth-admin.username"
    :atom-key  :username}
   {:label-loc "userinfo.firstName"
    :input-id  "admin.create-auth-admin.firstName"
    :atom-key  :firstname}
   {:label-loc "userinfo.lastName"
    :input-id  "admin.create-auth-admin.lastName"
    :atom-key  :lastname}])

(defn create-authority-admin []
  (command
    "create-user"
    (fn [_] (hub/send "admin::authAdminCreated"))           ;; TODO show link
    :email @(:username view-state)
    :role "authorityAdmin"                                  ;; TODO fix :role usage
    :organization  @(:organization view-state),
    :enabled "true",
    :firstName @(:firstname view-state),
    :lastName @(:lastname view-state)))

(rum/defc auth-admin-form
  [datamap]
  [:form {:on-submit create-authority-admin}
   [:div.pate-grid-1
    (for [input-conf inputs]
      [:div.row {:key (:input-id input-conf)}
       [:label.form-label {:for (:input-id input-conf)}
        (loc (:label-loc input-conf))]
       [:input.form-input
        (merge {:id   (:input-id input-conf)
                :type "text"}
               (components/atom-on-change (get datamap (:atom-key input-conf))))]])
    [:div.row
     [:button.positive {:type "submit"}
      (loc "admin.add-dialog.continue")]]]])

(rum/defc create-auth-admin < rum/reactive
  []
  [:div.bubble-dialog
   [:h3 (loc "admin.add-dialog.title")]
   (auth-admin-form view-state)])

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (create-auth-admin) (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId & component-args]
  (swap! args
         assoc
         :dom-id (name domId))
  (mount-component))
