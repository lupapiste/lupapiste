(ns lupapalvelu.ui.auth-admin.stamp.stamp-row-field
  (:require [lupapalvelu.ui.util :as util]
            [rum.core :as rum]
            [lupapalvelu.ui.auth-admin.stamp.state :refer [component-state]]
            [lupapalvelu.ui.auth-admin.stamp.util :as stamp-util]
            [lupapalvelu.ui.common :refer [loc]]))

(rum/defcs stamp-row-field < rum/reactive
                             (rum/local "inline-block" ::display)
  [local-state {:keys [data remove row-number row-index]}]
  (let [drag-element (rum/cursor-in component-state [:editor :drag-element])
        closest-elem (rum/cursor-in component-state [:editor :closest-element])
        {:keys [type text]} (rum/react data)
        rendered-content (if (= (keyword type) :custom-text)
                           [:input {:placeholder (loc :stamp.custom-text)
                                    :value       text
                                    :on-change   #(swap! data assoc :text (-> % .-target .-value))
                                    :style       {:width (max 150 (Math/floor (* 8 (count text))))}
                                    :draggable   false}]
                           [:span (loc (str "stamp." (name type)))])
        display-style (::display local-state)
        draggable? (or (not= (keyword type) :custom-text)
                       (and (not util/is-ie?)
                            (not util/is-edge?)))]
    [:div.stamp-row-btn.draggable
     {:style          {:display @display-style :cursor (if draggable? "move" "default")}
      :data-row       row-number
      :data-row-index row-index
      :draggable      draggable?
      :on-drag-start  (fn [e]
                        (reset! drag-element
                                {:type :move
                                 :source-boundaries (stamp-util/boundaries-from-component local-state)})
                        (let [data-transfer (.-dataTransfer e)]
                          (.setData data-transfer "text" (util/clj->json {:row   row-number
                                                                          :index row-index})))
                        (reset! display-style "none"))
      :on-drag-end    (fn [_]
                        (reset! closest-elem [])
                        (reset! display-style "inline-block")
                        (reset! drag-element nil))}
     [:div.btn-content
      rendered-content
      [:i.lupicon-circle-remove
       {:on-click (fn [_] (remove))}]]]))
