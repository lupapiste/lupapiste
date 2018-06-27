(ns lupapalvelu.ui.admin.users.create-authority-user
  (:require [rum.core :as rum]
            [lupapalvelu.ui.common :refer [loc query command]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.hub :as hub]
            [clojure.string :as s]))

(def inputs
  [{:label-loc "admin.add-dialog.organization-code"
    :input-id  "admin.create-auth-admin.organizationCode"
    :input-type "text"
    :atom-key  :organization}
   {:label-loc "userinfo.username"
    :input-id  "admin.create-auth-admin.username"
    :input-type "email"
    :atom-key  :username}
   {:label-loc "userinfo.firstName"
    :input-id  "admin.create-auth-admin.firstName"
    :input-type "text"
    :atom-key  :firstname}
   {:label-loc "userinfo.lastName"
    :input-id  "admin.create-auth-admin.lastName"
    :input-type "text"
    :atom-key  :lastname}])

(defn create-authority-admin [state event]
  (.preventDefault event)
  (command
    "create-user"
    (fn [resp]
      (-> state
          (update :linkFi reset! (:linkFi resp))
          (update :linkSv reset! (:linkSv resp))))
    :email (s/lower-case @(:username state))
    :role "authorityAdmin"                                  ;; TODO fix :role usage
    :organization  @(:organization state),
    :firstName @(:firstname state),
    :lastName @(:lastname state)))

(rum/defc auth-admin-form
  [parent-state]
  [:form {:on-submit (partial create-authority-admin parent-state)}
   [:div.pate-grid-1
    (for [input-conf inputs]
      [:div.row {:key (:input-id input-conf)}
       [:label.form-label {:for (:input-id input-conf)}
        (loc (:label-loc input-conf))]
       [:input.form-input
        (merge {:id   (:input-id input-conf)
                :type (:input-type input-conf)
                :name (:atom-key input-conf)
                :required true}
               (components/atom-on-change (get parent-state (:atom-key input-conf))))]])
    [:div.row
     [:button.positive {:type "submit"}
      (loc "admin.add-dialog.continue")]]]])

(rum/defcs create-auth-admin <
  rum/reactive
  (rum/local "" :username)
  (rum/local "" :firstname)
  (rum/local "" :lastname)
  (rum/local "" :organization)
  (rum/local "" :linkFi)
  (rum/local "" :linkSv)
  [state]
  (let [{:keys [linkSv linkFi username]} state]
    [:div.bubble-dialog
     [:h3 (loc "admin.add-dialog.title")]
     (if (and (s/blank? @linkFi) (s/blank? @linkSv))
       (auth-admin-form state)
       [:div
        [:p (loc "admin.add-dialog.activationLinks")]
        [:div.bottom-marginM [:span (s/lower-case @username)]]
        [:pre @linkFi]
        [:pre @linkSv]
        [:button.secondary {:on-click #(hub/send "admin::authAdminFinished")}
         (loc "close")]])]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (create-auth-admin) (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId & component-args]
  (swap! args
         assoc
         :dom-id (name domId))
  (mount-component))
