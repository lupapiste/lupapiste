(ns lupapalvelu.ui.printing-order.composer
  (:require [rum.core :as rum]
            [lupapalvelu.ui.util :as util]))

(def empty-component-state {:attachments []})

(defonce component-state (atom empty-component-state))

(defonce args (atom {}))

(def log (.-log js/console))

(defn init
  [init-state props]
  (let [_ (-> (aget props ":rum/initial-state") :rum/args)]
    (swap! component-state assoc :attachments (.apply js/lupapisteApp.services.attachmentsService.attachments))
    init-state))

(defn accordion-name [path]
  (js/lupapisteApp.services.accordionService.attachmentAccordionName path))

(rum/defcs accordion-group < rum/reactive
  (rum/local true ::group-open)
  [{group-open ::group-open} {:keys [level path sub-groups]}]
  [:div.rollup.rollup--open
   [:button.rollup-button.rollup-status.secondary.attachments-accordion.toggled
    [:span (accordion-name (last path))]]
   [:div.attachments-accordion-content
    (for [sub-group sub-groups]
      (rum/with-key
        (accordion-group {:path       (aget sub-group "path")
                          :sub-groups (-> (aget sub-group "subGroups") .apply)
                          :level      (inc level)})
        (util/unique-elem-id "accordion-sub-group")))]])

(rum/defc order-composer < rum/reactive
                           {:init         init
                            :will-unmount (fn [& _] (reset! component-state empty-component-state))}
  [_]
  [:div
   (for [top-level-group (-> @args :top-groups .apply)]
     (rum/with-key
       (accordion-group {:path       (aget top-level-group "path")
                         :sub-groups (-> (aget top-level-group "subGroups") .apply)
                         :level      1})
       (util/unique-elem-id "accordion-group")))])

(defn mount-component []
  (rum/mount (order-composer)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :top-groups (aget componentParams "topGroups") :dom-id (name domId))
  (mount-component))
