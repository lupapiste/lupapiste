(ns lupapalvelu.ui.admin.users.change-user-email
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.ui.common :refer [command loc]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]
            [rum.core :as rum]))

(def inputs
  [{:label-loc "admin.change-user-email.old-email"
    :input-id  "admin.change-user-email.old-email"
    :input-type "text"
    :atom-key  :old-email}
   {:label-loc "admin.change-user-email.new-email"
    :input-id  "admin.change-user-email.new-email"
    :input-type "text"
    :atom-key  :new-email}])

(defn submit [state event]
  (.preventDefault event)
  (command {:command               :admin-change-user-email
            :success               #(hub/send "admin::changeEmailFinished")
            :show-saved-indicator? true}
           :old-email @(:old-email state)
           :new-email @(:new-email state)))

(rum/defc change-user-email-form
  [parent-state]
  [:form {:on-submit (partial submit parent-state)}
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
      (loc "admin.change-user-email.submit")]]]])

(rum/defcs change-user-email <
  rum/reactive
  (rum/local "" :old-email)
  (rum/local "" :new-email)
  [state]
  [:div.bubble-dialog
   [:h3 (loc "admin.change-user-email")]
   (change-user-email-form state)
   [:button.secondary {:on-click #(hub/send "admin::changeEmailFinished")}
    (loc "cancel")]])

(defonce mounter (ui-dom/init :rum #(change-user-email)))

(defn ^:export start [domId & _]
  (ui-dom/mount! mounter domId))
