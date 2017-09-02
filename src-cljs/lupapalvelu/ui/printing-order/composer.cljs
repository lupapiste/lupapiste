(ns lupapalvelu.ui.printing-order.composer
  (:require [rum.core :as rum]
            [lupapalvelu.ui.attachment.components :as attc]
            [lupapalvelu.ui.components :as comp]
            [lupapalvelu.ui.util :as util]
            [lupapalvelu.ui.rum-util :as rum-util]
            [lupapalvelu.ui.common :refer [loc loc-html] :as common]
            [lupapalvelu.ui.printing-order.components :as poc]
            [lupapalvelu.ui.printing-order.files :as files]
            [lupapalvelu.ui.printing-order.pricing :as pricing]
            [lupapalvelu.ui.printing-order.state :as state]
            [lupapalvelu.ui.printing-order.transitions :as transitions]))

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
    (common/query "printing-order-pricing"
      #(swap! state/component-state assoc :pricing (:pricing %)))
    (if (and company-id (.userHasRole ko-app #js {:id company-id} "writer"))
      (common/query "company"
        (fn [result]
          (init-user-details (:company result)))
        :company company-id)
      (init-user-details))
    init-state))

(rum/defc order-composer-footer < rum/reactive []
  (let [order-rows (rum/react (rum/cursor-in state/component-state [:order]))
        total-amount (reduce + (vals order-rows))]
    [:div.printing-order-footer
     [:div
      [:button.tertiary.rollup-button
       [:h2 (loc :printing-order.footer.total-amount (str total-amount))]]
      [:button.tertiary.rollup-button
       (when (pos-int? total-amount)
         (let [pricing (pricing/price-for-order-amount total-amount)]
           (cond
             (:total pricing) [:h2 (str (loc :printing-order.footer.price) " "
                                        (:total (pricing/price-for-order-amount total-amount)) "€")]
             (:additionalInformation pricing)
                              [:h2 (:additionalInformation pricing)])))]
      [:button.tertiary.rollup-button.right
       (loc-html :h2 :printing-order.mylly.provided-by)]
      [:button.tertiary.rollup-button.right
       [:h2 (loc :printing-order.show-pricing)]]]]))

(rum/defc composer-phase1 < rum/reactive []
  (let [tag-groups (rum/react (rum/cursor-in state/component-state [:tagGroups]))]
    [:div.attachments-accordions
     (for [[path-key & children] tag-groups]
       (rum/with-key
         (files/files-accordion-group {:path       [path-key]
                                       :children   children})
         (util/unique-elem-id "accordion-group")))]))

(rum/defc composer-phase2 < rum/reactive []
  (let [payer-option (rum/cursor-in state/component-state [:contacts :payer-same-as-orderer])
        delivery-option (rum/cursor-in state/component-state [:contacts :delivery-same-as-orderer])
        conditions-accepted-option (rum/cursor-in state/component-state [:conditions-accepted])]
    [:div.order-grid-4
     [:div.order-section
      (poc/section-header :printing-order.orderer-details)
      (poc/contact-form [:contacts :orderer])]
     [:div.order-section
      (poc/section-header :printing-order.payer-details)
      [:div.row
       (poc/grid-radio-button payer-option true  :col-1 :printing-order.payer.same-as-orderer)
       (poc/grid-radio-button payer-option false :col-1 :printing-order.payer.other-than-orderer)]
      (when (false? (rum/react payer-option))
        (poc/contact-form [:contacts :payer]))]
     [:div.order-section
      (poc/section-header :printing-order.delivery-details)
      [:div.row
       (poc/grid-radio-button delivery-option true  :col-1 :printing-order.delivery.same-as-orderer)
       (poc/grid-radio-button delivery-option false :col-1 :printing-order.delivery.other-than-orderer)]
      (when (false? (rum/react delivery-option))
        (poc/contact-form [:contacts :delivery]))
      [:div.row
       (poc/grid-text-input [:billingReference] :col-2 :printing-order.billing-reference)
       (poc/grid-textarea-input [:deliveryInstructions] :col-2 :printing-order.delivery-instructions)]]
     [:div.order-section
      (poc/section-header :printing-order.conditions.heading)
      [:div.row
       [:div.col-4
        (loc-html :span :printing-order.conditions.text)]]
      [:div.row
       (poc/grid-checkbox conditions-accepted-option [:conditions-accepted] :col-2 :printing-order.conditions.accept true)]]]))

(rum/defc composer-phase3 < rum/reactive []
  (let [order (rum/react (rum/cursor-in state/component-state [:order]))
        attachments-selected (->> @state/component-state
                                  :attachments
                                  (filter (fn [{id :id}]
                                            (pos? (get order id)))))]
    [:div.order-grid-3
     [:div.order-section
      (poc/section-header :printing-order.summary.documents)
      (files/files-table attachments-selected :read-only)]
     (pricing/order-summary-pricing)
     [:div.order-section " "]
     [:div.order-summary-contacts-block
      [:div.row
       [:div.col-1
        [:span.order-grid-header (loc :printing-order.orderer-details)]
        [:span.order-summary-line
         (for [line (state/orderer-summary-lines)]
           [:span.order-summary-line
            {:key (util/unique-elem-id)}
            line])]]
       [:div.col-1
        [:span.order-grid-header (loc :printing-order.payer-details)]
        (for [line (state/payer-summary-lines)]
          [:span.order-summary-line
           {:key (util/unique-elem-id)}
           line])]
       [:div.col-1
        [:span.order-grid-header (loc :printing-order.delivery-details)]
        (for [line (state/delivery-address-summary-lines)]
          [:span.order-summary-line
           {:key (util/unique-elem-id)}
           line])]]
      [:div.row
       [:div.col-1
        [:span.order-grid-header (loc :printing-order.billing-reference)]
        [:span.order-summary-line (-> @state/component-state :billingReference)]]
       [:div.col-1
        [:span.order-grid-header (loc :printing-order.delivery-instructions)]
        [:span.order-summary-line (-> @state/component-state :deliveryInstructions)]]]]
     [:div.order-section " "]
     [:div.order-section
      (poc/section-header :printing-order.conditions.heading)
      [:div.row
       [:div.col-4
        (loc-html :span :printing-order.conditions.text)]]
      [:div.row
       [:div.col-4
        [:label.like-btn
         [:i.lupicon-circle-check.positive]
         [:span (loc :printing-order.conditions.accepted)]]]]]]))

(rum/defc order-composer < rum/reactive
                           {:init         init
                            :will-unmount state/will-unmount}
  [ko-app]
  (let [phase (rum/react (rum/cursor-in state/component-state [:phase]))]
    [:div
     [:h1 {:data-test-id "order-composer-title"}
      (loc (str "printing-order.phase" phase ".title"))]
     [:span (loc (str "printing-order.phase" phase ".intro-text"))]
     [:div.bottom-marginM]
     (when (= phase 1)
       (composer-phase1))
     (when (= phase 2)
       (composer-phase2))
     (when (= phase 3)
       (composer-phase3))
     [:div.order-section
      (loc-html :span :printing-order.mylly.provided-by)]
     (transitions/transition-buttons phase)
     [:div (comp/debug-atom (rum/cursor-in state/component-state [:contacts]))]
     (order-composer-footer)]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (order-composer (:app @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :app (aget componentParams "app") :dom-id (name domId))
  (mount-component))
