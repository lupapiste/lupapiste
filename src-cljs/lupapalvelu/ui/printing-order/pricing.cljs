(ns lupapalvelu.ui.printing-order.pricing
  (:require [lupapalvelu.ui.printing-order.state :as state]
            [lupapalvelu.ui.common :refer [loc]]
            [rum.core :as rum]
            [lupapalvelu.ui.util :as util]))

(defn vat [total-vat-included]
  (-> total-vat-included
      (/ 124)
      (* 2400)
      Math/floor
      (/ 100)
      double
      ))

(defn price-for-order-amount [amount]
  (let [{:keys [by-volume]} (-> @state/component-state :pricing)
        {:keys [fixed additionalInformation] :as price-class}
          (sade.shared-util/find-first (fn [{:keys [min max]}] (and (>= amount min) (or (nil? max) (<= amount max)))) by-volume)]
    (cond
      (nil? price-class)    nil
      additionalInformation {:additionalInformation additionalInformation}
      fixed                 (let [total fixed]
                              {:total total
                               :vat   (vat total)}))))

(defn footer-price-for-order-amount [amount]
  (let [{:keys [total additionalInformation]} (price-for-order-amount amount)]
    (cond
      total                 (str (loc :printing-order.footer.price) " "
                                 (util/format-currency-value total) " "
                                 (loc :printing-order.footer.includes-vat))
      additionalInformation (get additionalInformation (keyword (.getCurrentLanguage js/loc))))))

(rum/defc order-summary-pricing < rum/reactive []
  (let [order-rows (rum/react (rum/cursor-in state/component-state [:order]))
        total-amount (reduce + (vals order-rows))
        {total-price :total additionalInformation :additionalInformation vat :vat} (price-for-order-amount total-amount)]
    [:div.order-summary-pricing-block
     [:table
      [:thead
       (when total-price
         [:tr
          [:td.first
           [:span.h3 (loc :printing-order.summary.total-amount (str total-amount))]]
          [:td.second
           [:span.h3 (loc :printing-order.summary.total-price)]]
          [:td.third
           [:span.h3 (util/format-currency-value total-price)]]])
       (when additionalInformation
         [:tr
          [:td.half
           [:span.h3 (loc :printing-order.summary.total-amount (str total-amount))]]
          [:td
           [:span.h3 (get additionalInformation (keyword (.getCurrentLanguage js/loc)))]]])
       (when vat
         [:tr
          [:td.first]
          [:td.second
           [:span (loc :printing-order.summary.includes-vat)]]
          [:td.third
           [:span (util/format-currency-value vat)]]])]]]))
