(ns lupapalvelu.ui.auth-admin.stamp-editor
  (:require [rum.core :as rum]
            [clojure.string :as string]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :refer [query command loc] :as common]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.ui.util :as jsutil]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.rum-util :as rum-util]))

(defn find-by-key
  "Return item from sequence col of maps where element k (keyword) matches value v."
  [k v col]
  (some (fn [m] (when (= v (get m k)) m)) col))

(def empty-component-state {:stamps []
                            :view {:bubble-visible false
                                   :selected-stamp-id nil}})

(def component-state  (atom empty-component-state))

(def selected-stamp (rum-util/derived-atom
                        [component-state]
                        (fn [state]
                          (when-let [selected-id (get-in state [:view :selected-stamp-id])]
                            (->> (:stamps state)
                                 (find-by-key :id selected-id))))))

(defn- update-stamp-view [id]
  (swap! component-state assoc-in [:view :selected-stamp-id] id))

(defn- refresh
  ([] (refresh nil))
  ([cb]
   (query :stamp-templates
          (fn [data]
            (swap! component-state assoc :stamps (:stamps data))
            (when cb (cb data))))))

(rum/defc stamp-select [stamps selection]
  (uc/select update-stamp-view
             "stamp-select"
             selection
             (cons ["" (loc "choose")]
                   (map (juxt :id :name) stamps))))

(defn init
  [init-state props]
  (let [[auth-model] (-> (aget props ":rum/initial-state") :rum/args)]
    (swap! component-state assoc :auth-models {:global-auth-model auth-model})
    (when (auth/ok? auth-model :stamp-templates) (refresh))
    init-state))

(rum/defc stamp-editor < rum/reactive
  {:init init
   :will-unmount (fn [& _] (reset! component-state empty-component-state))}
  [global-auth-model]
  [:div
   [:h1 (loc "stamp-editor.tab.title")]
   [:div (stamp-select (rum/react (rum/cursor-in component-state [:stamps])) (rum/react (rum/cursor-in component-state [:view :selected-stamp-id])))]])

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (stamp-editor (:auth-model @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (mount-component))
