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
  [{:keys [id contents modified latestVersion type] :as att} opts]
  (let [order-cursor (rum/cursor-in state/component-state [:order id])
        in-printing-order? (pos? (rum/react order-cursor))
        type-group-and-id (str (-> type :type-group) "." (-> type :type-id))
        type-id-text (loc (str "attachmentType." type-group-and-id))
        type-and-contents (cond
                            (= type-id-text contents) type-id-text
                            (not (empty? contents))   (str type-id-text " (" contents ")")
                            :default                  type-id-text)]
    [:tr
     {:class [(when-not in-printing-order?
                "not-in-printing-order")]
      :data-test-type type-group-and-id}
     [:td type-and-contents]
     [:td (attc/view-with-download-small-inline att)]
     [:td
      (common/format-timestamp modified)
      " (" (-> latestVersion :user :firstName) " " (-> latestVersion :user :lastName) ")"]
     [:td (if (some #{:read-only} opts)
            @order-cursor
            (amount-controls order-cursor))]]))

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
  (rum/local true ::group-open)
  [_ {:keys [path children]}]
  (let [attachments          (rum/react (rum/cursor-in state/component-state [:attachments]))
        attachments-in-group (filter (partial attachment-in-group? path) attachments)]
   (when (seq attachments-in-group)
     [:div.rollup.rollup--open
      [:button
       {:class ["rollup-button" "rollup-status" "attachments-accordion" "toggled" "secondary"]}
       [:span (accordion-name (last path))]]
      [:div.attachments-accordion-content
       (for [[child-key & _] children]
         (rum/with-key
           (files-accordion-group {:path (conj path child-key)})
           (util/unique-elem-id "accordion-sub-group")))
       (when (empty? children)
         [:div.rollup-accordion-content-part
          (files-table attachments-in-group)])]])))