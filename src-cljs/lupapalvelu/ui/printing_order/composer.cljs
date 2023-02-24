(ns lupapalvelu.ui.printing-order.composer
  (:require [goog.object :as googo]
            [lupapalvelu.ui.common :refer [loc loc-html] :as common]
            [lupapalvelu.ui.printing-order.components :as poc]
            [lupapalvelu.ui.printing-order.files :as files]
            [lupapalvelu.ui.printing-order.pricing :as pricing]
            [lupapalvelu.ui.printing-order.state :as state]
            [lupapalvelu.ui.printing-order.transitions :as transitions]
            [lupapalvelu.ui.util :as util]
            [rum.core :as rum]))

(defn- init-user-details
  ([]
   (init-user-details nil))
  ([company]
   (letfn [(get-user-field [fieldName] (js/util.getIn js/lupapisteApp.models.currentUser #js [(name fieldName)]))]
     (swap! state/component-state assoc-in [:contacts :orderer]
            (merge
              {:firstName     (get-user-field :firstName)
               :lastName      (get-user-field :lastName)
               :streetAddress (get-user-field :street)
               :postalCode    (get-user-field :zip)
               :city          (get-user-field :city)
               :email         (get-user-field :email)}
              (when company
                {:companyName   (:name company)
                 :streetAddress (:address1 company)
                 :postalCode    (:zip company)
                 :city          (:po company)}))))))

(defn init
  [init-state props]
  (let [app-id   (-> init-state :rum/args first)
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
    (if (and company-id (js/lupapisteApp.models.application.userHasRole #js {:id company-id} "writer"))
      (common/query "company"
        (fn [result]
          (init-user-details (:company result)))
        :company company-id)
      (init-user-details))
    init-state))

(rum/defc pricelist-panel
  []
  (let [pricing-config (-> @state/component-state :pricing)]
    [:div
     [:div.h3 (loc :a11y.pricelist)]
     (for [row (:by-volume pricing-config)]
       [:div {:key (util/unique-elem-id "pricelist-")} (get-in row [:pricelist-label (keyword (.getCurrentLanguage js/loc))])])
     [:div {:key "pricelist-last-row"} (loc :printing-order.show-pricing.includes-vat)]]))

(rum/defc order-composer-footer < rum/reactive
  []
  (let [order-rows (rum/react (rum/cursor-in state/component-state [:order]))
        number-of-printouts (reduce + (vals order-rows))
        pricelist-panel-open? (atom false)]
    [:div.flex--between.flex--start.flex--wrap.flex--gap2
     [:div.dsp--flex.flex--gap2.flex--wrap.flex--g1
      [:div.h3 (loc :printing-order.footer.total-amount (str number-of-printouts))]
      (when (pos-int? number-of-printouts)
        [:div.h3 (pricing/footer-price-for-order-amount number-of-printouts)])]
     (pricelist-panel pricelist-panel-open?)]))

(rum/defc composer-phase1 < rum/reactive []
  (let [tag-groups (rum/react (rum/cursor-in state/component-state [:tagGroups]))]
    (into
      [:div.attachments-accordions]
      (for [[path-key & children] tag-groups]
        (rum/with-key
          (files/files-accordion-group {:path       [path-key]
                                        :children   children})
          (str "accordion-group-" path-key))))))

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
       (poc/grid-radio-button payer-option true  :col-1 :printing-order.payer.same-as-orderer :payer)
       (poc/grid-radio-button payer-option false :col-1 :printing-order.payer.other-than-orderer :payer)]
      (when (false? (rum/react payer-option))
        (poc/contact-form [:contacts :payer]))]
     [:div.order-section
      (poc/section-header :printing-order.delivery-details)
      [:div.row
       (poc/grid-radio-button delivery-option true  :col-1 :printing-order.delivery.same-as-orderer :delivery)
       (poc/grid-radio-button delivery-option false :col-1 :printing-order.delivery.other-than-orderer :delivery)]
      (when (false? (rum/react delivery-option))
        (poc/contact-form [:contacts :delivery]))
      [:div.row
       [:div.boxgrid
        (poc/grid-text-input [:contacts :billingReference]
                             :boxitem.box--2.pad--r2
                             :printing-order.billing-reference false)]]
      [:div.row
       [:div.boxgrid
        (poc/grid-textarea-input [:contacts :deliveryInstructions]
                                 :boxitem.box--2.pad--r2
                                 :printing-order.delivery-instructions false)]]]
     [:div.order-section
      (poc/section-header :printing-order.conditions.heading)
      [:div.row
       [:div.col-4
        [:p (loc-html :span :printing-order.conditions.text)]
        [:p (loc :printing-order.conditions.data-processing-notice)]]]
      [:div.row
       (poc/grid-checkbox conditions-accepted-option [:conditions-accepted] :col-2 :printing-order.conditions.accept true)]]]))

(rum/defc order-summary [order]
  (let [attachments-selected (->> @state/component-state
                                  :attachments
                                  (filter (fn [{id :id}]
                                            (pos? (get order id)))))]
    [:div
     (when (= (-> @state/component-state :phase) 4)
       [:div.order-section
        [:div.row
         [:div.col-4
          (loc-html :span :printing-order.phase4.order-reference)]]])
     [:div.order-section
      (poc/section-header :printing-order.summary.documents)
      (files/files-table attachments-selected :read-only)]
     (pricing/order-summary-pricing)
     [:div.order-section " "]
     [:div.order-summary-contacts-block
      [:div.row
       [:div.col-1
        [:span.order-grid-header (loc :printing-order.orderer-details)]
        (for [line (state/orderer-summary-lines)]
          [:span.order-summary-line
           {:key (util/unique-elem-id)}
           line])]
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
        [:span.order-summary-line (-> @state/component-state :contacts :billingReference)]]
       [:div.col-1
        [:span.order-grid-header (loc :printing-order.delivery-instructions)]
        [:span.order-summary-line (-> @state/component-state :contacts :deliveryInstructions)]]]]]))

(rum/defc composer-phase3 < rum/reactive []
  (let [order (rum/react (rum/cursor-in state/component-state [:order]))]
    [:div.order-grid-3
     (order-summary order)
     [:div.order-section " "]
     [:div.order-section
      (poc/section-header :printing-order.conditions.heading)
      [:div.row
       [:div.col-4
        [:p (loc-html :span :printing-order.conditions.text)]
        [:p (loc :printing-order.conditions.data-processing-notice)]]]
      [:div.row
       [:div.col-4
        [:label.like-btn
         [:i.lupicon-circle-check.positive {:aria-hidden true}]
         [:span (loc :printing-order.conditions.accepted)]]]]]]))

(rum/defc composer-phase4 < rum/reactive []
  (let [order (rum/react (rum/cursor-in state/component-state [:order]))]
    [:div.order-grid-3
     (order-summary order)
     [:div.order-section " "]]))

(rum/defc order-composer < rum/reactive
                           {:init         init
                            :will-unmount state/will-unmount}
  [app-id]
  (let [phase (rum/react (rum/cursor-in state/component-state [:phase]))]
    [:div
     [:h1 {:data-test-id "order-composer-title"}
      (loc (str "printing-order.phase" phase ".title"))]
     [:h2 app-id]
     [:span (loc (str "printing-order.phase" phase ".intro-text"))]
     [:div.bottom-marginM]
     (case phase
       1 (rum/fragment
           (composer-phase1)
           (order-composer-footer))
       2 (rum/fragment
           (composer-phase2)
           (order-composer-footer))
       3 (composer-phase3)
       4 (composer-phase4))
     [:div.gap--v2.txt--center (loc-html :div.flex--g1 :printing-order.mylly.provided-by)]
     [:div.flex--wrap.flex--gap2.flex--center
      (transitions/transition-buttons phase)
      ]]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (order-composer (:appId @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :appId ((googo/get componentParams "appId")) :dom-id (name domId))
  (mount-component))
