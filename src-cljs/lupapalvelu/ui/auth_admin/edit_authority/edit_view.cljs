(ns lupapalvelu.ui.auth-admin.edit-authority.edit-view
  (:require [lupapalvelu.ui.common :refer [loc query command]]
            [lupapalvelu.ui.auth-admin.edit-authority.edit-info :as info]
            [lupapalvelu.ui.auth-admin.edit-authority.edit-roles :as roles]
            [lupapalvelu.ui.auth-admin.edit-authority.state :as state]
            [lupapalvelu.ui.auth-admin.edit-authority.util :as util]
            [lupapalvelu.ui.util :refer [get-user-field]]
            [goog.object :as googo]
            [rum.core :as rum]))

(defn init [state]
  (let [[authority-id] (-> state :rum/args)]
    (swap! state/component-state assoc :authority-id authority-id)
    (util/get-organization)
    (util/get-authority)
    state))

(rum/defc edit-authority-view < rum/reactive
  {:did-mount   init
   :will-unmount (fn [& _] (reset! state/component-state {}))}
  [_]
  (if-let [user (rum/react state/authority)]
    [:div
     [:h1 (str (loc "auth-admin.edit-authority.title") ", " (:firstName user) " " (:lastName user))]
     [:div (loc "auth-admin.edit-authority.desc")]
     (roles/edit-authority-roles user (rum/react state/org-id) (rum/react state/allowed-roles))
     [:div.hr]
     (info/edit-authority-info user)]
    [:div (str (loc "loading") "...")]))


(defonce args (atom {}))

(defn mount-component []
  (rum/mount (edit-authority-view (:authority-id @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :authority-id ((googo/get componentParams "authorityId"))
                    :dom-id (name domId))
  (mount-component))
