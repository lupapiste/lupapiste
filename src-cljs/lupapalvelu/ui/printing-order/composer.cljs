(ns lupapalvelu.ui.printing-order.composer
  (:require [rum.core :as rum]
            [lupapalvelu.ui.util :as util]
            [lupapalvelu.ui.common :as common]))

(def empty-component-state {:attachments []
                            :tagGroups   []
                            :id          nil})

(defonce component-state (atom empty-component-state))

(defonce args (atom {}))

(def log (.-log js/console))

(defn init
  [init-state props]
  (let [[ko-app] (-> (aget props ":rum/initial-state") :rum/args)
        app-id   (aget ko-app "_js" "id")]
    (common/query "attachments-for-printing-order"
                  #(swap! component-state assoc :id app-id
                                                :attachments (:attachments %)
                                                :tagGroups   (:tagGroups %))
                  :id app-id)
    init-state))

(defn accordion-name [path]
  (js/lupapisteApp.services.accordionService.attachmentAccordionName path))

(defn attachment-in-group? [path attachment]
  (every? (fn [pathKey] (some #(= pathKey %) (:tags attachment))) path))

(rum/defc attachment-row < rum/reactive [att]
  [:tr
   [:td (str "(" (:contents att) ")")]
   [:td (-> att :latestVersion :filename)]
   [:td "123456"]
   [:td 1]])

(rum/defcs accordion-group < rum/reactive
  (rum/local true ::group-open)
  [{group-open ::group-open} {:keys [level path children]}]
  (let [attachments          (rum/react (rum/cursor-in component-state [:attachments]))
        attachments-in-group (filter (partial attachment-in-group? path) attachments)]
    [:div.rollup.rollup--open
     [:button.rollup-button.rollup-status.secondary.attachments-accordion.toggled
      [:span (accordion-name (last path))]]
     [:div.attachments-accordion-content
      (for [[child-key & _] children]
        (rum/with-key
          (accordion-group {:path (conj path child-key)})
          (util/unique-elem-id "accordion-sub-group")))
      (when (and (empty? children) (seq attachments-in-group))
        [:div.rollup-accordion-content-part
         [:div.attachments-table-container
          [:table.attachments-table.table-even-odd
           [:thead
            [:tr
             [:th (js/loc "printing-order.composer.attachment-table.type-and-content")]
             [:th (js/loc "printing-order.composer.attachment-table.filename")]
             [:th (js/loc "printing-order.composer.attachment-table.modified")]
             [:th (js/loc "printing-order.composer.attachment-table.copy-amount")]]]
           [:tbody
            (for [att attachments-in-group]
              (rum/with-key (attachment-row att) (str "attachment-row" (:id att))))]]]])]]))

(rum/defc order-composer < rum/reactive
                           {:init         init
                            :will-unmount (fn [& _] (reset! component-state empty-component-state))}
  [ko-app]
  (let [tag-groups (rum/react (rum/cursor-in component-state [:tagGroups]))]
    [:div
     (for [[path-key & children] tag-groups]
       (rum/with-key
         (accordion-group {:path       [path-key]
                           :children   children})
         (util/unique-elem-id "accordion-group")))]))

(defn mount-component []
  (rum/mount (order-composer (:app @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :app (aget componentParams "app") :dom-id (name domId))
  (mount-component))
