(ns lupapalvelu.ui.printing-order.files
  (:require [lupapalvelu.ui.attachment.components :as attc]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.printing-order.state :as state]
            [lupapalvelu.ui.util :as util]
            [rum.core :as rum]))

(rum/defc amount-controls
  [default-value callback-fn]
  (let [[amount set-amount!] (rum/use-state 0)]
    (rum/use-effect! (fn []
                       (set-amount! (or default-value 0)))
                     [default-value])

    [:div.flex--align-right.flex--center
     (components/icon-button {:text       "-"
                              :icon       :lupicon-circle-minus
                              :class      :tertiary.gap--h05
                              :icon-only? true
                              :on-click   (fn []
                                            (callback-fn (max 0 (dec amount))))})
     (components/text-edit amount
                           {:class      :w--max-4em
                            :type       :number
                            :min        0
                            :aria-label (loc :printing-order.files-table.copy-amount)
                            :callback   (fn [v]
                                          (let [n (js/parseInt v)]
                                         (when (nat-int? n)
                                           (callback-fn n))))})
     (components/icon-button {:text       "+"
                              :icon       :lupicon-circle-plus
                              :class      :tertiary.gap--h05
                              :icon-only? true
                              :on-click   (fn []
                                            (callback-fn (inc amount)))})]))

(rum/defc file-row < rum/reactive
  [{:keys [id contents modified latestVersion type] :as att} opts]
  (let [order-cursor       (rum/cursor-in state/component-state [:order id])
        in-printing-order? (pos? (rum/react order-cursor))
        type-group-and-id  (str (-> type :type-group) "." (-> type :type-id))
        type-id-text       (loc (str "attachmentType." type-group-and-id))
        type-and-contents  (cond
                             (= type-id-text contents) type-id-text
                             (seq contents)            (str type-id-text " (" contents ")")
                             :else                     type-id-text)]
    [:tr
     {:class          [(when-not in-printing-order?
                         "not-in-printing-order")]
      :data-test-type type-group-and-id}
     [:td type-and-contents]
     [:td (attc/view-with-download-small-inline att)]
     [:td
      [:div (common/format-timestamp modified)]
      [:div.ws--nowrap
       (-> latestVersion :user :firstName) " " (-> latestVersion :user :lastName)]]
     [:td (if (some #{:read-only} opts)
            @order-cursor
            (amount-controls @order-cursor
                             (fn [n]
                               (reset! order-cursor n))))]]))

(rum/defc files-table < rum/reactive
  [files & opts]
  [:div.attachments-table-container
   {:data-test-id "files-table"}
   [:table.attachments-table.table-even-odd
    [:thead
     [:tr
      [:th.attachments-table--wide
       (loc :printing-order.files-table.type-and-content)]
      [:th (loc :printing-order.files-table.filename)]
      [:th (loc :printing-order.files-table.modified)]
      [:th
       {:style {:width "15%"}}
       (loc :printing-order.files-table.copy-amount)]]]
    [:tbody
     (for [file files]
       (rum/with-key (file-row file opts) (util/unique-elem-id "file-row")))]]])

(defn- accordion-name [path]
  (.attachmentAccordionName js/lupapisteApp.services.accordionService path))

(defn- attachment-in-group? [path attachment]
  (every? (fn [pathKey] (some #(= pathKey %) (:tags attachment))) path))

(rum/defcs files-accordion-group < rum/reactive
  [_ {:keys [path children]}]
  (let [attachments          (rum/react (rum/cursor-in state/component-state [:attachments]))
        attachments-in-group (filter (partial attachment-in-group? path) attachments)]
   (when (seq attachments-in-group)
     [:div
      [:h3 (accordion-name (last path))]
      (for [[child-key & _] children]
        (rum/with-key
          (files-accordion-group {:path (conj path child-key)})
          (util/unique-elem-id "accordion-sub-group")))
      (when (empty? children)
        (files-table attachments-in-group))])))
