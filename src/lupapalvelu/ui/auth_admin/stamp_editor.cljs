(ns lupapalvelu.ui.auth-admin.stamp-editor
  (:require [rum.core :as rum]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :refer [query command] :as common]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.ui.util :as jsutil]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.rum-util :as rum-util]))

(defn find-by-key
  "Return item from sequence col of maps where element k (keyword) matches value v."
  [k v col]
  (some (fn [m] (when (= v (get m k)) m)) col))

(def empty-component-state {:organization-id ""
                            :stamps []
                            :view {:bubble-visible false
                                   :selected-stamp-id nil}})

(def component-state  (atom empty-component-state))

(def selected-stamp (rum-util/derived-atom
                        [component-state]
                        (fn [state]
                          (when-let [selected-id (get-in state [:view :selected-stamp-id])]
                            (->> (:stamps state)
                                 (find-by-key :id selected-id))))))

(rum/defc stamp-editor < rum/reactive
  {:init (constantly nil)
   :will-unmount (constantly nil) #_(fn [& _] (reset! component-state empty-component-state))}
  [org-id application-auth-model]
  [:div "Hello from stamp editor"])

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (stamp-editor (:org-id @args) (:auth-model @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :org-id (aget componentParams "orgId") :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (mount-component))
