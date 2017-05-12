(ns lupapalvelu.ui.auth-admin.stamp.editor
  (:require [rum.core :as rum]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.auth-admin.stamp.state :refer [component-state empty-component-state update-stamp-view] :as state]
            [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.ui.auth-admin.stamp.metadata :refer [header-component metadata-component]]
            [lupapalvelu.ui.auth-admin.stamp.preview :refer [preview-component]]
            [lupapalvelu.ui.auth-admin.stamp.field-types :refer [field-types-component]]
            [lupapalvelu.ui.auth-admin.stamp.stamp-row :refer [stamp-row]]
            [lupapalvelu.ui.auth-admin.stamp.util :as stamp-util]
            [schema.core :as sc]
            [lupapalvelu.attachment.stamp-schema :as sts]))

(defn init
  [init-state props]
  (let [[auth-model] (-> (aget props ":rum/initial-state") :rum/args)]
    (swap! component-state assoc :auth-models {:global-auth-model auth-model})
    (when (auth/ok? auth-model :stamp-templates) (state/refresh))
    init-state))

(rum/defc new-stamp-button [selected-stamp-id editor-state]
  [:button.positive
   {:on-click (fn []
                (reset! selected-stamp-id nil)
                (swap! editor-state assoc :stamp state/empty-stamp))
    :data-test-id "open-create-stamp-bubble"}
   [:i.lupicon-circle-plus]
   [:span (loc "stamp-editor.new-stamp.button")]])

(rum/defc stamp-select < rum/reactive
  [stamps selection]
  (uc/select state/update-stamp-view
             "stamp-select"
             (rum/react selection)
             (cons ["" (loc "choose")]
                   (map (juxt :id :name) (rum/react stamps)))))

(defn valid-stamp? [stamp-data]
  (try (sc/validate sts/StampTemplate stamp-data)
       true
       (catch js/Error _
         false)))

(rum/defc edit-stamp-bubble < rum/reactive
          [stamp-id editor-state]
          (let [drag-element (rum/cursor editor-state :drag-element)
                debug-data (rum/cursor editor-state :debug-data)
                stamp-in-editor (rum/cursor editor-state :stamp)
                rows (rum/cursor-in editor-state [:stamp :rows])]
            (when-not (empty? (rum/react stamp-in-editor))
              [:div.edit-stamp-bubble
               (header-component stamp-id stamp-in-editor (valid-stamp? (rum/react stamp-in-editor)))
               [:div.form-group {:style {:display :block}}
                (metadata-component)
                (preview-component rows)
                [:div.form-group
                 [:label.form-label.form-label-group (loc "stamp.information")]
                 (field-types-component)
                 [:div (loc "stamp-editor.drag.guide")]
                 [:div ;;many rows
                  (for [[idx _] (stamp-util/indexed (conj (rum/react rows) []))]
                    (rum/with-key
                      (stamp-row {:index idx
                                  :rows-cursor rows
                                  :debug-data debug-data
                                  :drag-source drag-element})
                      idx))]]]])))

(rum/defc stamp-editor < rum/reactive
  {:init init
   :will-unmount (fn [& _] (reset! component-state empty-component-state))}
  [global-auth-model]
  (let [stamps            (rum/cursor component-state :stamps)
        editor-state      (rum/cursor component-state :editor)
        selected-stamp-id (rum/cursor component-state :selected-stamp-id)]
    [:div
     [:h1 (loc "stamp-editor.tab.title")]
     [:div
      (stamp-select stamps selected-stamp-id)
      (new-stamp-button selected-stamp-id editor-state)]
     [:div.row.edit-stamp
      (edit-stamp-bubble selected-stamp-id editor-state)]]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (stamp-editor (:auth-model @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (mount-component))
