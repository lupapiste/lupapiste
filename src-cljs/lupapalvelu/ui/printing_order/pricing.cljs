(ns lupapalvelu.ui.printing-order.pricing
  (:require [goog.string :as goos]
            [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui.printing-order.state :as state]
            [lupapalvelu.ui.util :as util]
            [rum.core :as rum]
            [sade.strings :as ss]))

(defn vat-as-int
  "Returns the VAT as an integer where e.g. 2400 = 24%"
  []
  (or (:vat @state/component-state) 0))

(defn vat-as-string
  "Returns the VAT integer formatted into a percentage usable for localizations, e.g. \"25,5\""
  []
  (ss/replace (goos/format "%.1f" (* (vat-as-int) 0.01)) "." ","))

(defn price-with-vat->vat-portion
  "Given the total price, returns the portion of it that is VAT (value added tax).
  Uses magical number multipliers to preserve precision when doing division."
  [price]
  (let [vat-int    (vat-as-int)
        divisor    (+ 10000 vat-int)
        multiplier 100000]
    (-> price
        (* multiplier)
        (/ divisor)
        (* vat-int)
        Math/floor
        (/ multiplier)
        double)))

(defn price-for-order-amount [amount]
  (let [{:keys [by-volume]} (-> @state/component-state :pricing)
        {:keys [fixed additionalInformation] :as price-class}
          (sade.util/find-first (fn [{:keys [min max]}] (and (>= amount min) (or (nil? max) (<= amount max)))) by-volume)]
    (cond
      (nil? price-class)    nil
      additionalInformation {:additionalInformation additionalInformation}
      fixed                 (let [total fixed]
                              {:total total
                               :vat   (price-with-vat->vat-portion total)}))))

(defn footer-price-for-order-amount [amount]
  (let [{:keys [total additionalInformation]} (price-for-order-amount amount)]
    (cond
      total                 (str (loc :printing-order.footer.price) " "
                                 (util/format-currency-value total) " "
                                 (loc :printing-order.footer.includes-vat [(vat-as-string)]))
      additionalInformation (get additionalInformation (keyword (.getCurrentLanguage js/loc))))))

(rum/defc order-summary-pricing < rum/reactive []
  (let [order-rows                                             (rum/react (rum/cursor-in state/component-state [:order]))
        total-amount                                           (reduce + (vals order-rows))
        {:keys [additionalInformation vat] total-price :total} (price-for-order-amount total-amount)]
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
           [:span (loc :printing-order.summary.includes-vat [(vat-as-string)])]]
          [:td.third
           [:span (util/format-currency-value vat)]]])]]]))
