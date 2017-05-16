(ns lupapalvelu.ui.auth-admin.stamp.stamp-row
  (:require [rum.core :as rum]
            [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui.auth-admin.stamp.state :refer [component-state]]
            [lupapalvelu.ui.auth-admin.stamp.stamp-row-field :refer [stamp-row-field]]
            [lupapalvelu.ui.auth-admin.stamp.util :as stamp-util]))

(defn- remove-button [rows row-number stamp-row idx]
  (fn [] (if (< 1 (count @stamp-row))
           (swap! stamp-row stamp-util/drop-at idx)
           (swap! rows stamp-util/drop-at row-number))))

(rum/defcs stamp-row < rum/reactive
                       (rum/local 0 ::drag-event-sum)
  [state {:keys [index rows-cursor drag-source debug-data]}]
  (let [stamp-row-cursor (rum/cursor rows-cursor index)
        drag-event-sum (::drag-event-sum state)
        closest-elem (rum/cursor-in component-state [:editor :closest-element])
        field-buttons (for [[idx _] (stamp-util/indexed (rum/react stamp-row-cursor))]
                        (rum/with-key
                          (stamp-row-field {:data       (rum/cursor-in stamp-row-cursor [idx])
                                            :remove     (remove-button rows-cursor index stamp-row-cursor idx)
                                            :debug-data debug-data
                                            :row-number index
                                            :row-index  idx})
                          idx))
        placeholder-width (or (get-in (rum/react drag-source)
                                      [:source-boundaries :width])
                              110)
        on-drag-enter (fn [e]
                        (.preventDefault e)
                        (swap! drag-event-sum inc))
        on-drag-leave (fn [_]
                        (swap! drag-event-sum dec))
        is-dragged-over? (pos? @drag-event-sum)
        mouse-move-handler (fn [e]
                             (let [dt (-> e .-dataTransfer)
                                   op-type (.-type (aget (.-items dt) 0))
                                   effect (case op-type
                                            "newfield" "copy"
                                            "movefield" "move"
                                            nil)]
                               (when effect
                                 (.preventDefault e)
                                 (set! (-> e .-dataTransfer .-dropEffect) effect)))
                             (let [client-x (aget e "clientX")
                                   client-y (aget e "clientY")
                                   data {:row-mouse   {:client-x client-x
                                                       :client-y client-y}
                                         :current-row index}
                                   dom-data (stamp-util/dom-data)]
                               (reset! closest-elem (stamp-util/closest-with-edge
                                                      (merge data dom-data)))))
        placeholder-location (stamp-util/closest->placeholder-position (rum/react closest-elem))
        placeholder-row? (= index (first placeholder-location))
        split-pos (or (second placeholder-location) 0)
        on-drop (stamp-util/drop-handler rows-cursor index split-pos)
        placeholder-element [:div.stamp-row-placeholder
                             {:key   :placeholder
                              :style {:width (max placeholder-width 110)}}
                             [:span {:style {:pointer-events :none}} (loc "stamp-editor.drop-zone")]]
        [before after] (when (number? split-pos) (split-at split-pos field-buttons))]
    [:div.stamp-row {:on-drag-enter   on-drag-enter
                     :on-drag-leave   on-drag-leave
                     :on-drag-over    mouse-move-handler
                     :on-drop         on-drop
                     :data-row-number index}
     [:div.stamp-row-label [:span (str (loc "stamp-editor.row") " " (inc index))]]
     (if (and placeholder-row? is-dragged-over?)
       (concat before
               [placeholder-element]
               after)
       (concat
         field-buttons
         [[:div.stamp-row-placeholder
           {:key :default-placeholder}
           [:span {:style {:pointer-events :none}} (loc "stamp-editor.drop-zone")]]]))]))
