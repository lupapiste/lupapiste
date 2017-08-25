(ns lupapalvelu.ui.printing-order.composer
  (:require [rum.core :as rum]
            [lupapalvelu.ui.attachment.components :as attc]
            [lupapalvelu.ui.components :as comp]
            [lupapalvelu.ui.util :as util]
            [lupapalvelu.ui.rum-util :as rum-util]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.printing-order.files :as files]
            [lupapalvelu.ui.printing-order.components :as poc]
            [lupapalvelu.ui.printing-order.state :as state]))

(def log (.-log js/console))

(defn- init-user-details
  ([]
   (init-user-details nil))
  ([company]
   (swap! state/component-state assoc-in [:contacts :orderer]
          (merge
            {:firstName (.firstName js/lupapisteApp.models.currentUser)
             :lastName  (.lastName js/lupapisteApp.models.currentUser)
             :address   (.street js/lupapisteApp.models.currentUser)
             :postalCode (.zip js/lupapisteApp.models.currentUser)
             :city       (.city js/lupapisteApp.models.currentUser)
             :email      (.email js/lupapisteApp.models.currentUser)}
            (when company
              {:companyName (:name company)
               :address (:address1 company)
               :postalCode (:zip company)
               :city (:po company)})))))

(defn init
  [init-state props]
  (let [[ko-app] (-> (aget props ":rum/initial-state") :rum/args)
        app-id   (.id ko-app)
        company-id (.id js/lupapisteApp.models.currentUser.company)]
    (common/query "attachments-for-printing-order"
      (fn [result]
        (swap! state/component-state assoc
               :id app-id
               :attachments (:attachments result)
               :order       (zipmap (map :id (:attachments result)) (repeatedly (constantly 0)))
               :tagGroups   (:tagGroups result)))
      :id app-id)
    (if (and company-id (.userHasRole ko-app #js {:id company-id} "writer"))
      (common/query "company"
        (fn [result]
          (init-user-details (:company result)))
        :company company-id)
      (init-user-details))
    init-state))

(defn accordion-name [path]
  (.attachmentAccordionName js/lupapisteApp.services.accordionService path))

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

(rum/defc order-composer-footer < rum/reactive []
  (let [order-rows (rum/react (rum/cursor-in state/component-state [:order]))
        total-amount (reduce + (vals order-rows))]
    [:div.printing-order-footer
     [:div
      [:button.tertiary.rollup-button
       [:h2 (str (loc "printing-order.footer.total-amount") " " total-amount " " (loc "unit.kpl"))]]
      [:button.tertiary.rollup-button
       [:h2 (str (loc "printing-order.footer.price") " " 0 "€")]]
      [:button.tertiary.rollup-buttonx
       [:h2 (loc "printing-order.show-pricing")]]
      [:button.tertiary.rollup-button
       [:h2 (loc "printing-order.mylly.provided-by")]]]]))

(rum/defc composer-phase1 < rum/reactive []
  (let [tag-groups (rum/react (rum/cursor-in state/component-state [:tagGroups]))
        order-rows (rum/react (rum/cursor-in state/component-state [:order]))
        total-amount (reduce + (vals order-rows))]
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

(rum/defc proceed-to-phase3-button < rum/reactive []
  (let [valid-order? (rum/react (rum-util/derived-atom [state/component-state] state/valid-order?))]
    [:button.positive
     {:on-click #(state/proceed-phase3)
      :disabled (false? valid-order?)}
     [:span (loc "printing-order.phase2.button.next")]
     [:i.lupicon-chevron-right]]))

(rum/defc composer-phase2 < rum/reactive []
  (let [payer-option (rum/cursor-in state/component-state [:contacts :payer-same-as-orderer])
        delivery-option (rum/cursor-in state/component-state [:contacts :delivery-same-as-orderer])
        conditions-accepted-option (rum/cursor-in state/component-state [:conditions-accepted])]
    [:div.order-grid-4
     [:div.order-section
      (poc/section-header "printing-order.orderer-details")
      (poc/contact-form [:contacts :orderer])]
     [:div.order-section
      (poc/section-header "printing-order.payer-details")
      [:div.row
       (poc/grid-radio-button payer-option true  :col-1 "printing-order.payer.same-as-orderer")
       (poc/grid-radio-button payer-option false :col-1 "printing-order.payer.other-than-orderer")]
      (when (false? (rum/react payer-option))
        (poc/contact-form [:contacts :payer]))]
     [:div.order-section
      (poc/section-header "printing-order.delivery-details")
      [:div.row
       (poc/grid-radio-button delivery-option true  :col-1 "printing-order.delivery.same-as-orderer")
       (poc/grid-radio-button delivery-option false :col-1 "printing-order.delivery.other-than-orderer")]
      (when (false? (rum/react delivery-option))
        (poc/contact-form [:contacts :delivery]))
      [:div.row
       (poc/grid-text-input [:billingReference] :col-2 "printing-order.billing-reference")
       (poc/grid-textarea-input [:deliveryInstructions] :col-2 "printing-order.delivery-instructions")]]
     [:div.order-section
      (poc/section-header "printing-order.conditions.heading")
      [:div.row
       [:div.col-4
        [:span (loc "printing-order.conditions.text")]]]
      [:div.row
       (poc/grid-checkbox conditions-accepted-option :col-2 "printing-order.conditions.accept" true)]]
     [:div.operation-button-row
      [:button.secondary
       {:on-click #(state/back-to-phase1)}
       [:i.lupicon-chevron-left]
       [:span (loc "printing-order.phase2.button.prev")]]
      (proceed-to-phase3-button)]]))

(rum/defc composer-phase3 < rum/reactive []
  (let [order (rum/react (rum/cursor-in state/component-state [:order]))
        attachments-selected (->> @state/component-state
                                  :attachments
                                  (filter (fn [{id :id}]
                                            (pos? (get order id)))))]
    [:div.order-grid-4
     [:div.order-section
      (poc/section-header "printing-order.summary.documents")
      (files/files-table attachments-selected :read-only)]
     [:div.operation-button-row
      [:button.secondary
       {:on-click #(state/back-to-phase2)}
       [:i.lupicon-chevron-left]
       [:span (loc "printing-order.phase3.button.prev")]]
      [:button.positive
       {:on-click #(state/submit-order)}
       [:span (loc "printing-order.phase3.button.submit")]
       [:i.lupicon-chevron-right]]]]))

(rum/defc order-composer < rum/reactive
                           {:init         init
                            :will-unmount state/will-unmount}
  [ko-app]
  (let [phase (rum/react (rum/cursor-in state/component-state [:phase]))]
    [:div
     [:h1 (loc (str "printing-order.phase" phase ".title"))]
     [:span (loc (str "printing-order.phase" phase ".intro-text"))]
     [:div.bottom-marginM]
     (when (= phase 1)
       (composer-phase1))
     (when (= phase 2)
       (composer-phase2))
     (when (= phase 3)
       (composer-phase3))
     [:div (comp/debug-atom (rum/cursor-in state/component-state [:conditions-accepted]))]
     (order-composer-footer)]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (order-composer (:app @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :app (aget componentParams "app") :dom-id (name domId))
  (mount-component))
