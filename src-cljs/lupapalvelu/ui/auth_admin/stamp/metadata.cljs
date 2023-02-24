(ns lupapalvelu.ui.auth-admin.stamp.metadata
  (:require [rum.core :as rum]
            [lupapalvelu.ui.auth-admin.stamp.state :as state]
            [lupapalvelu.ui.common :refer [loc command]]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.attachment.stamp-schema :as sts]))

(defn- delete-stamp [stamp-id]
  (command :delete-stamp-template
           (fn [{ok :ok}]
             (when ok (state/update-stamp-view nil))
             (state/refresh))
           :stamp-id stamp-id))

(defn- save-stamp [{:keys [name position background page qrCode rows]} stamp-id]
  (command :upsert-stamp-template
           (fn [{id :stamp-id}]
             (state/refresh (fn [_] (state/update-stamp-view id))))
           :stamp-id stamp-id
           :name name
           :position position
           :background background
           :page page
           :qrCode qrCode
           :rows rows))

(rum/defc header-component < rum/reactive
  [stamp-id stamp]
  [:div.stamp-editor-header.form-grid
   [:div.row
    [:div.col-2
     [:label (loc "stamp.name")]
     [:input
      {:type      "text"
       :value     (:name (rum/react stamp))
       :on-change #(swap! stamp assoc :name (-> % .-target .-value))}]]
    [:div.col-2.buttons
     [:button.secondary
      {:on-click (fn [_] (delete-stamp @stamp-id))
       :disabled (not @stamp-id)}
      [:i.lupicon-remove]
      [:span (loc "remove")]]]]])

(rum/defc control-buttons < rum/reactive
  [stamp-id stamp valid-stamp?]
  [:div.stamp-editor-control-buttons.group-buttons
   [:button.positive
    {:on-click (fn [_] (save-stamp @stamp @stamp-id))
     :disabled (not valid-stamp?)}
    [:i.lupicon-save]
    [:span (loc "save")]]
   [:button.secondary
    {:on-click (fn [_] (state/refresh (fn [_] (state/update-stamp-view nil))))}
    [:i.lupicon-remove]
    [:span (loc "cancel")]]])

(rum/defc number-input < rum/reactive
  [label-key cursor max-value]
  [:span
   [:label (str (loc label-key) " (" (loc "unit.mm") ")")]
   [:input
    {:type      "number"
     :min       0
     :max       max-value
     :value     (rum/react cursor)
     :on-change #(reset! cursor (js/parseInt (-> % .-target .-value)))}]])

(rum/defc metadata-select < rum/reactive
  [label-key cursor options parse-fn to-value-fn]
  [:span
   [:label (loc label-key)]
   (uc/select (or (to-value-fn (rum/react cursor)) "")
              options
              {:callback #(reset! cursor (parse-fn %))
               :test-id (str (name label-key) "-select")
               :class "dropdown"})])

(defn transparency-options []
  (map (fn [x]
         [x (loc (str "stamp.transparency." x))]) sts/transparency-options))

(defn page-options []
  (map (fn [x]
         [x (loc (str "stamp.page." (name x)))]) sts/pages))

(rum/defc metadata-component < rum/reactive
  []
  [:div
   [:div.col-1
    (number-input "stamp.xMargin" (rum/cursor-in state/component-state [:editor :stamp :position :x]) 200)
    (metadata-select
      "stamp.transparency"
      (rum/cursor-in state/component-state [:editor :stamp :background])
      (transparency-options)
      js/parseInt
      identity)]
   [:div.col-1
    (number-input "stamp.yMargin" (rum/cursor-in state/component-state [:editor :stamp :position :y]) 200)
    (metadata-select
      "stamp.page"
      (rum/cursor-in state/component-state [:editor :stamp :page])
      (page-options)
      keyword
      name)]])
