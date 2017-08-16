(ns lupapalvelu.ui.printing-order.composer
  (:require [rum.core :as rum]
            [lupapalvelu.ui.attachment.components :as attc]
            [lupapalvelu.ui.util :as util]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.printing-order.files :as files]
            [lupapalvelu.ui.printing-order.state :as state]))

(def log (.-log js/console))

(defn init
  [init-state props]
  (let [[ko-app] (-> (aget props ":rum/initial-state") :rum/args)
        app-id   (aget ko-app "_js" "id")]
    (common/query "attachments-for-printing-order"
      #(swap! state/component-state assoc :id app-id
                                    :attachments (:attachments %)
                                    :order       (zipmap (map :id (:attachments %)) (repeatedly (constantly 0)))
                                    :tagGroups   (:tagGroups %))
      :id app-id)
    init-state))

(defn accordion-name [path]
  (js/lupapisteApp.services.accordionService.attachmentAccordionName path))

(defn attachment-in-group? [path attachment]
  (every? (fn [pathKey] (some #(= pathKey %) (:tags attachment))) path))

(rum/defcs accordion-group < rum/reactive
  (rum/local true ::group-open)
  [{group-open ::group-open} {:keys [level path children]}]
  (let [attachments          (rum/react (rum/cursor-in state/component-state [:attachments]))
        attachments-in-group (filter (partial attachment-in-group? path) attachments)]
    (when (seq attachments-in-group)
      [:div.rollup.rollup--open
       [:button
        {:class (conj ["rollup-button" "rollup-status" "attachments-accordion" "toggled"]
                      (if (seq children)
                        "secondary"
                        "tertiary"))}
        [:span (accordion-name (last path))]]
       [:div.attachments-accordion-content
        (for [[child-key & _] children]
          (rum/with-key
            (accordion-group {:path (conj path child-key)})
            (util/unique-elem-id "accordion-sub-group")))
        (when (empty? children)
          [:div.rollup-accordion-content-part
           (files/files-table attachments-in-group)])]])))

(rum/defc order-composer-footer < rum/reactive
  [total-amount]
  [:div.printing-order-footer
   [:div
    [:button.tertiary.rollup-button
     [:h2 (str (loc "printing-order.footer.total-amount") " " total-amount " " (loc "unit.kpl"))]]
    [:button.tertiary.rollup-button
     [:h2 (str (loc "printing-order.footer.price") " " 0 "€")]]
    [:button.tertiary.rollup-button
     [:h2 (loc "printing-order.show-pricing")]]
    [:button.tertiary.rollup-button
     [:h2 (loc "printing-order.mylly.provided-by")]]]])

(rum/defc composer-phase1 < rum/reactive
  [total-amount]
  (let [tag-groups (rum/react (rum/cursor-in state/component-state [:tagGroups]))]
    [:div
     [:div.attachments-accordions
      (for [[path-key & children] tag-groups]
        (rum/with-key
          (accordion-group {:path       [path-key]
                            :children   children})
          (util/unique-elem-id "accordion-group")))]
     [:div.operation-button-row
      [:button.positive
       {:on-click #(state/proceed-phase2)
        :disabled (not (pos-int? total-amount))}
       [:span (loc "printing-order.phase1.button.next")]
       [:i.lupicon-chevron-right]]]]))

(rum/defc composer-phase2 < rum/reactive
  []
  (let []
    [:div.order-grid-4
     [:div.row
      [:div.col-4
       [:span.row-text.order-grid-header (loc "printing-order.orderer-details")]]]
     [:div.row
      [:div.col-1
       [:div "Etunimi"]
       [:input.grid-style-input--wide
        {:type "text"}]]
      [:div.col-1
       [:div "Etunimi"]
       [:input.grid-style-input--wide
        {:type "text"}]]
      [:div.col-2
       [:div "Etunimi"]
       [:input.grid-style-input--wide
        {:type "text"}]]]
     [:div.row
      [:div.col-1
       [:div "Etunimi"]
       [:input.grid-style-input--wide
        {:type "text"}]]
      [:div.col-1
       [:div "Etunimi"]
       [:input.grid-style-input--wide
        {:type "text"}]]
      [:div.col-2
       [:div "Etunimi"]
       [:input.grid-style-input--wide
        {:type "text"}]]]
     [:div.operation-button-row
      [:button.secondary
       [:i.lupicon-chevron-left]
       [:span (loc "printing-order.phase2.button.prev")]]
      [:button.positive
       [:span (loc "printing-order.phase2.button.next")]
       [:i.lupicon-chevron-right]]]]))

(rum/defc order-composer < rum/reactive
                           {:init         init
                            :will-unmount state/will-unmount}
  [ko-app]
  (let [phase      (rum/react (rum/cursor-in state/component-state [:phase]))
        order-rows (rum/react (rum/cursor-in state/component-state [:order]))
        total-amount (reduce + (vals order-rows))]
    [:div
     [:h1 (loc (str "printing-order.phase" phase ".title"))]
     [:span (loc (str "printing-order.phase" phase ".intro-text"))]
     [:div.bottom-marginM]
     (when (= phase 1)
       (composer-phase1 total-amount))
     (when (= phase 2)
       (composer-phase2))
     (order-composer-footer total-amount)]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (order-composer (:app @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :app (aget componentParams "app") :dom-id (name domId))
  (mount-component))
