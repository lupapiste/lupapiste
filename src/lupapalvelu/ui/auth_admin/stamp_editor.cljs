(ns lupapalvelu.ui.auth-admin.stamp-editor
  (:require [rum.core :as rum]
            [clojure.string :as string]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :refer [query command loc] :as common]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.ui.util :as jsutil]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.rum-util :as rum-util]))

(defn find-by-id
  [v col]
  (some #(when (= v (:id %)) %) col))

(def empty-stamp   {:name     ""
                    :position {:x 0 :y 0}
                    :page     :first
                    :qr-code  true
                    :rows     [[]]})

(def empty-component-state {:stamps []
                            :view {:bubble-visible false
                                   :selected-stamp-id nil}})

(def component-state  (atom empty-component-state))

(def selected-stamp (rum-util/derived-atom
                     [component-state]
                     (fn [state]
                       (or (-> (get-in state [:view :selected-stamp-id])
                               (find-by-id (:stamps state)))
                           empty-stamp))))

(defn- update-stamp-view [id]
  (swap! component-state assoc-in [:view :selected-stamp-id] id))

(defn- refresh
  ([] (refresh nil))
  ([cb]
   (query :stamp-templates
          (fn [data]
            (swap! component-state assoc :stamps (:stamps data))
            (when cb (cb data))))))

(rum/defc stamp-select < rum/reactive
  [stamps selection]
  (uc/select update-stamp-view
             "stamp-select"
             (rum/react selection)
             (cons ["" (loc "choose")]
                   (map (juxt :id :name) (rum/react stamps)))))

(rum/defc new-stamp-button [bubble-visible selected-stamp-id]
  [:button.positive
   {:on-click (fn [_]
                (reset! selected-stamp-id nil)
                (reset! bubble-visible true))
    :data-test-id "open-create-stamp-bubble"}
   [:i.lupicon-circle-plus]
   [:span (loc "stamp-editor.new-stamp.button")]])

(defn init
  [init-state props]
  (let [[auth-model] (-> (aget props ":rum/initial-state") :rum/args)]
    (swap! component-state assoc :auth-models {:global-auth-model auth-model})
    (when (auth/ok? auth-model :stamp-templates) (refresh))
    init-state))

(rum/defc edit-stamp-bubble < rum/reactive
  [visible?]
  (when (rum/react visible?)
    [:div.edit-stamp-bubble
     ;; TODO: Editor here
     ]))

(rum/defc stamp-editor
  {:init init
   :will-unmount (fn [& _] (reset! component-state empty-component-state))}
  [global-auth-model]
  (let [stamps            (rum/cursor component-state :stamps)
        selected-stamp-id (rum/cursor-in component-state [:view :selected-stamp-id])
        bubble-visible    (rum/cursor-in component-state [:view :bubble-visible])]
    [:div
     [:h1 (loc "stamp-editor.tab.title")]
     [:div
      (stamp-select stamps selected-stamp-id)
      (new-stamp-button bubble-visible selected-stamp-id)]
     [:div.row.edit-sta (edit-stamp-bubble bubble-visible)]]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (stamp-editor (:auth-model @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (mount-component))
