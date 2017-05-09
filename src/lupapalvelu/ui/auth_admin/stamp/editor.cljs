(ns lupapalvelu.ui.auth-admin.stamp.editor
  (:require [rum.core :as rum]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.auth-admin.stamp.state :refer [component-state empty-component-state update-stamp-view]]
            [lupapalvelu.ui.common :refer [query command loc]]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.ui.auth-admin.stamp.metadata :refer [header-component metadata-component]]
            [lupapalvelu.ui.auth-admin.stamp.preview :refer [preview-component]]
            [lupapalvelu.ui.auth-admin.stamp.field-types :refer [field-types-component]]
            [lupapalvelu.ui.auth-admin.stamp.stamp-row :refer [stamp-row]]
            [lupapalvelu.ui.auth-admin.stamp.util :as stamp-util]))

(defn- refresh
  ([] (refresh nil))
  ([cb]
   (query :stamp-templates
          (fn [data]
            (swap! component-state assoc :stamps (:stamps data))
            (when cb (cb data))))))


(defn init
  [init-state props]
  (let [[auth-model] (-> (aget props ":rum/initial-state") :rum/args)]
    (swap! component-state assoc :auth-models {:global-auth-model auth-model})
    (when (auth/ok? auth-model :stamp-templates) (refresh))
    init-state))

(rum/defc new-stamp-button [bubble-visible selected-stamp-id]
  [:button.positive
   {:on-click (fn [_]
                (reset! selected-stamp-id nil)
                (reset! bubble-visible true))
    :data-test-id "open-create-stamp-bubble"}
   [:i.lupicon-circle-plus]
   [:span (loc "stamp-editor.new-stamp.button")]])

(rum/defc stamp-select < rum/reactive
  [stamps selection]
  (uc/select update-stamp-view
             "stamp-select"
             (rum/react selection)
             (cons ["" (loc "choose")]
                   (map (juxt :id :name) (rum/react stamps)))))

(rum/defc edit-stamp-bubble < rum/reactive
          [visible? editor-state]
          (let [drag-element (rum/cursor editor-state :drag-element)
                debug-data (rum/cursor editor-state :debug-data)
                rows (rum/cursor editor-state :rows)]
            (when (rum/react visible?)
              [:div.edit-stamp-bubble
               (header-component)
               [:div.form-group {:style {:display :block}}
                (metadata-component)
                (preview-component)
                [:div.form-group
                 [:label.form-label.form-label-group "Leiman sisältö"]
                 (field-types-component)
                 [:div "Raahaa ylläolevia leiman sisältökenttiä..."]
                 [:div ;;many rows
                  (for [[idx _] (stamp-util/indexed (rum/react rows))]
                    (rum/with-key
                      (stamp-row {:index idx
                                  :rows-cursor rows
                                  :debug-data debug-data
                                  :drag-source drag-element})
                      idx))]]]])))

(rum/defc stamp-editor
  [global-auth-model]
  {:init init
   :will-unmount (fn [& _] (reset! component-state empty-component-state))}
  (let [stamps            (rum/cursor component-state :stamps)
        selected-stamp-id (rum/cursor-in component-state [:view :selected-stamp-id])
        bubble-visible    (rum/cursor-in component-state [:view :bubble-visible])
        editor-state      (rum/cursor-in component-state [:editor])]
    [:div
     [:h1 (loc "stamp-editor.tab.title")]
     [:div
      (stamp-select stamps selected-stamp-id)
      (new-stamp-button bubble-visible selected-stamp-id)]
     [:div.row.edit-sta (edit-stamp-bubble bubble-visible
                                           editor-state)]]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (stamp-editor (:auth-model @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (mount-component))
