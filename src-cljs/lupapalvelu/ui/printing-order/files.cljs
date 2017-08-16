(ns lupapalvelu.ui.printing-order.files
  (:require [rum.core :as rum]
            [lupapalvelu.ui.attachment.components :as attc]
            [lupapalvelu.ui.util :as util]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.printing-order.state :as state]))

(rum/defcs amount-controls < rum/reactive (rum/local 0 ::amount)
  [local-state state]
  (let [amount* (::amount local-state)
        commit-fn #(reset! state @amount*)]
   (common/reset-if-needed! (::amount local-state) @state)
   [:div.amount-controls
    [:i.lupicon-circle-minus
     {:on-click (fn [_]
                  (swap! amount* #(max 0 (dec %)))
                  (commit-fn))}]
    [:input
     {:type "text"
      :class "input-default"
      :value @amount*
      :on-change identity
      :on-blur   (fn [v]
                   (let [n (js/parseInt (-> v .-target .-value))]
                     (if (nat-int? n)
                       (do
                         (reset! amount* n)
                         (commit-fn))
                       (reset! amount* @state))))}]
    [:i.lupicon-circle-plus
     {:on-click (fn [_]
                  (swap! amount* inc)
                  (commit-fn))}]]))

(rum/defc file-row < rum/reactive
  [{:keys [id contents modified latestVersion type] :as att}]
  (let [order-cursor (rum/cursor-in state/component-state [:order id])
        in-printing-order? (pos? (rum/react order-cursor))
        type-id-text (loc (str "attachmentType." (-> type :type-group) "." (-> type :type-id)))
        type-and-contents (cond
                            (= type-id-text contents) type-id-text
                            (not (empty? contents))   (str type-id-text " (" contents ")")
                            :default                  type-id-text)]
    [:tr
     {:class [(when-not in-printing-order?
                "not-in-printing-order")]}
     [:td type-and-contents]
     [:td (attc/view-with-download-small-inline latestVersion)]
     [:td
      (common/format-timestamp modified)
      " (" (-> latestVersion :user :firstName) " " (-> latestVersion :user :lastName) ")"]
     [:td (amount-controls order-cursor)]]))

(rum/defc files-table < rum/reactive
  [files]
  [:div.attachments-table-container
   [:table.attachments-table.table-even-odd
    [:thead
     [:tr
      [:th.attachments-table--wide
       (loc "printing-order.files-table.type-and-content")]
      [:th (loc "printing-order.files-table.filename")]
      [:th (loc "printing-order.files-table.modified")]
      [:th (loc "printing-order.files-table.copy-amount")]]]
    [:tbody
     (for [file files]
       (rum/with-key (file-row file) (util/unique-elem-id "file-row")))]]])
