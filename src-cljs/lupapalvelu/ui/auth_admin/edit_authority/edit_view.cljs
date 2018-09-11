(ns lupapalvelu.ui.auth-admin.edit-authority.edit-view
  (:require [lupapalvelu.ui.common :refer [loc query command]]
            [lupapalvelu.ui.auth-admin.edit-authority.edit-info :as info]
            [lupapalvelu.ui.auth-admin.edit-authority.edit-roles :as roles]
            [lupapalvelu.ui.auth-admin.edit-authority.state :as state]
            [lupapalvelu.ui.auth-admin.edit-authority.util :as util]
            [lupapalvelu.ui.util :refer [get-user-field]]
            [rum.core :as rum]))

(defn init [init-state props]
  (let [[authority-id] (-> (aget props ":rum/initial-state") :rum/args)]
    (swap! state/component-state assoc :authority-id authority-id)
    (util/get-organization)
    (util/get-authority)
    init-state))

(rum/defc edit-authority-view < rum/reactive
  {:init         init
   :will-unmount (fn [& _] (reset! state/component-state {}))}
  [_]
  (let [user (rum/react state/authority)]
    [:div
     [:h1 (str (loc "auth-admin.edit-authority.title") ", " (:firstName user) " " (:lastName user))]
     [:div (loc "auth-admin.edit-authority.desc")]
     (roles/edit-authority-roles)
     [:div.hr]
     (info/edit-authority-info)]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (edit-authority-view (:authority-id @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :authority-id (aget componentParams "authorityId")
                    :dom-id (name domId))
  (mount-component))
