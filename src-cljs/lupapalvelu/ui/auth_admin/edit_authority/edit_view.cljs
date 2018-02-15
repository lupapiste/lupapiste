(ns lupapalvelu.ui.auth-admin.edit-authority.edit-view
  (:require [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :refer [loc query command]]
            [lupapalvelu.ui.auth-admin.edit-authority.edit-info :as info]
            [lupapalvelu.ui.auth-admin.edit-authority.edit-roles :as roles]
            [lupapalvelu.ui.auth-admin.edit-authority.state :as state]
            [lupapalvelu.ui.auth-admin.edit-authority.util :as util]
            [lupapalvelu.ui.util :refer [get-user-field]]
            [rum.core :as rum]))

(defn init [init-state props]
  (let [[                                                   ;auth-model
         authority-id-observable] (-> (aget props ":rum/initial-state") :rum/args)]
    (swap! state/component-state assoc                      ;:auth-model {:global-auth-model auth-model}
                                       :authority-id-observable authority-id-observable)
    (util/get-organization)
    (util/get-authority)
    init-state))

(rum/defc edit-authority-view < rum/reactive
  {:init         init
   :will-unmount (fn [& _] (reset! state/component-state {}))}
  [authority-id-observable                                  ;auth-model
   ]
  (let [user (rum/react state/authority)]
    [:div
     [:h1 (str (loc "auth-admin.edit-authority.title") ", " (:firstName user) " " (:lastName user))]
     [:div (loc "auth-admin.edit-authority.desc")]
     (roles/edit-authority-roles)
     [:div.hr]
     (info/edit-authority-info)]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (edit-authority-view (:authority-id-observable @args)
                                  ;   (:auth-model @args)
                                  )
             (.getElementById js/document (:dom-id @args))))

(defn ^export start [domId componentParams]
  (swap! args assoc                                         ;:auth-model (aget componentParams "authModel")
                    :authority-id-observable (aget componentParams "authorityIdObservable")
                    :dom-id (name domId))
  (mount-component))
