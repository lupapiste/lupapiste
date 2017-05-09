(ns lupapalvelu.ui.auth-admin.stamp.metadata
  (:require [rum.core :as rum]
            [lupapalvelu.ui.auth-admin.stamp.form-entry :refer [form-entry]]
            [lupapalvelu.ui.auth-admin.stamp.state :as state]
            [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.attachment.stamp-schema :as sts]))

(rum/defc header-component < rum/reactive
          [cursor valid-stamp?]
          [:div.group-buttons
           {:style {:background-color "#f6f6f6"
                    :border "1px solid #dddddd"}}
           ;;TODO: onks joku otsikkorivicontainer-luokka josta tulis toi oikee harmaa taustaväri ja muut tyylit niinku haitareissa?
           [:span.form-entry
            [:label.form-label.form-label-string (loc "stamp.name")]
            [:input.form-input.text
             {:value (rum/react cursor)
              :on-change #(reset! cursor (-> % .-target .-value))}]]
           [:button.secondary.is-right
            [:i.lupicon-remove]
            [:span (loc "remove")]]
           [:button.positive.is-right
            {:disabled (not valid-stamp?)}
            (loc "save")]])

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
