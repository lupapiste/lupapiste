(ns lupapalvelu.ui.auth-admin.stamp.metadata
  (:require [rum.core :as rum]
            [lupapalvelu.ui.auth-admin.stamp.form-entry :refer [form-entry]]
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

(defn- save-stamp [{:keys [name position background page qrCode rows] :as stamp} stamp-id]
  (command :upsert-stamp-template
           (fn [{ok :ok id :stamp-id}]
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
          [:div.stamp-editor-header.group-buttons
           [:span.form-entry
            [:label.form-label.form-label-string (loc "stamp.name")]
            [:input.form-input.text
             {:value (:name (rum/react stamp))
              :on-change #(swap! stamp assoc :name (-> % .-target .-value))}]]
           [:button.secondary.is-right
            {:on-click (fn [_] (delete-stamp @stamp-id))
             :disabled (not @stamp-id)}
            [:i.lupicon-remove]
            [:span (loc "remove")]]])

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
          [:span.form-entry
           [:label.form-label.form-label-string (str (loc label-key) " (" (loc "unit.mm") ")")]
           [:input.form-input.text
            {:type "number"
             :min 0
             :max max-value
             :value (rum/react cursor)
             :on-change #(reset! cursor (js/parseInt (-> % .-target .-value)))}]])

(rum/defc metadata-select < rum/reactive
          [label-key cursor options parse-fn to-value-fn]
          [:span.form-entry
           [:label.form-label.form-label-string (loc label-key)]
           (uc/select #(reset! cursor (parse-fn %))
                      (str (name label-key) "-select")
                      (to-value-fn (rum/react cursor))
                      options)])

(defn transparency-options []
  (map (fn [x]
         [x (loc (str "stamp.transparency." x))]) sts/transparency-options))

(defn page-options []
  (map (fn [x]
         [x (loc (str "stamp.page." (name x)))]) sts/pages))

(rum/defc metadata-component < rum/reactive
          []
          [:div.form-group {:style {:width "60%"
                                    :display :inline-block}}
           [:label.form-label.form-label-group (loc "stamp.margin")]
           [:div
            (number-input "stamp.xMargin" (rum/cursor-in state/component-state [:editor :stamp :position :x]) 200)
            (number-input "stamp.yMargin" (rum/cursor-in state/component-state [:editor :stamp :position :y]) 200)]
           [:div
            (metadata-select
              "stamp.transparency"
              (rum/cursor-in state/component-state [:editor :stamp :background])
              (transparency-options)
              js/parseInt
              identity)
            (metadata-select
              "stamp.page"
              (rum/cursor-in state/component-state [:editor :stamp :page])
              (page-options)
              keyword
              name)]])
