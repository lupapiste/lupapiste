(ns lupapalvelu.ui.auth-admin.stamp.field-types
  (:require [clojure.string :as string]
            [rum.core :as rum]
            [lupapalvelu.attachment.stamp-schema :as ss]
            [lupapalvelu.ui.util :as util]
            [lupapalvelu.ui.auth-admin.stamp.state :refer [component-state]]
            [lupapalvelu.ui.auth-admin.stamp.util :as stamp-util]
            [lupapalvelu.ui.common :refer [loc]]))

(rum/defcs field-type-selector < (rum/local false)
  [local-state {:keys [key]}]
  (let [drag-source? (:rum/local local-state)
        drag-element (rum/cursor-in component-state [:editor :drag-element])
        closest-elem (rum/cursor-in component-state [:editor :closest-element])
        base-classes "stamp-editor-btn"
        extra-classes (if @drag-source?
                        "drag-source"
                        "")
        all-classes (string/join " " [base-classes extra-classes])]
    [:div
     {:draggable           true
      :on-drag-start       (fn [e]
                             (reset! drag-element
                                     {:type :copy
                                      :source-boundaries (stamp-util/boundaries-from-component local-state)})
                             (reset! drag-source? true)

                             (let [data-transfer (.-dataTransfer e)]
                               (.setData data-transfer "text" (util/clj->json {:type key}))))
      :on-drag-end         (fn [_]
                             (reset! drag-source? false)
                             (reset! closest-elem [])
                             (reset! drag-element nil))
      :data-stamp-btn-name key
      :class               all-classes}
     [:div.btn-content
      [:i.lupicon-circle-plus]
      [:span (loc (str "stamp." (name key)))]]]))

(rum/defc field-types-component < rum/reactive
  []
  [:div
   (for [field-type (concat ss/simple-field-types ss/text-field-types)]
     (rum/with-key
       (field-type-selector {:key field-type})
       field-type))])
