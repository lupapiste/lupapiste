(ns lupapalvelu.ui.components.day-edit
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.datepicker :as datepicker]
            [lupapalvelu.ui.misc]
            [rum.core :as rum]
            [sade.strings :as ss]))

(rum/defc day-edit
  "Date picker component that consists of text field and calendar button. The calendar
  also opens with Down key.

  Properties:

  default-value Current date. Can be nil, (Finnish) date string, Date, timestamp (int or
                string).

  options:       Some of the following [optional]:

    callback:    Function that receives Date instance or nil. Note that bad dates are _never_
                 passed.
    [format]     Sets the type of the value passed to the callback function.
                   - default -> #inst \"2025-09-09T21:00:00.000-00:00\"
                   - :epoch  -> 1757451600000 (beginning of the day)
                   - :string -> 10.09.2025
    [disabled?]  See `common/resolve-disabled`
    [enabled?]   See `common/resolve-disabled`
    [invalid?]   If truthy, then the component is marked as invalid (aria-invalid and
                 style).
    [class]      Class definitions for the input element. See `common/css`.
                 The default is `day-picker-input`.
    [required?]  Is the value required (default false)
    [read-only?] Is the value read-only (default false).
    [id]         Id for the component text-input. Used with an external label.
    [title]      Optional (hover) title.
    [ref]        Optional ref to assign to the input field."
  [default-value {:keys [callback test-id required? invalid? read-only? format title ref] :as options}]
  (let [[text set-text!]          (rum/use-state (some-> (datepicker/make-date default-value)
                                                         :date
                                                         datepicker/datestring))
        [error? set-error!]       (rum/use-state (:error (datepicker/make-date default-value)))
        [bad? set-bad!]           (rum/use-state invalid?)
        [last-result set-result!] (rum/use-state text)
        [open? _set-open!_]       (rum/use-state false)
        container-ref*            (rum/use-ref nil)
        input-ref*                (rum/use-ref nil)
        input-cb*                 (rum/use-callback
                                    (fn [node]
                                      (rum/set-ref! input-ref* node)
                                      (when ref
                                        (rum/set-ref! ref node)))
                                    [ref])
        disabled?                 (common/resolve-disabled options)
        button-disabled?          (or disabled? read-only?)
        set-open!                 (fn [flag?]
                                    (when-not (= flag? open?)
                                      (_set-open!_ flag?)))
        toggle                    #(set-open! (not open?))
        process-result            (fn [value]
                                    (let [{:keys [date error]} (datepicker/make-date value)
                                          datext               (datepicker/datestring date)]
                                      (set-error! error)
                                      (when (inst? value)
                                        (set-text! datext))
                                      (when-not (or error (= datext last-result))
                                        (case format
                                          :epoch  (callback (datepicker/day-begin-ts date))
                                          :string (callback datext)
                                          (callback date))
                                        (set-result! datext))))
        required?                 (and (not bad?) required? (ss/blank? text))
        info-id                   (common/unique-id "day-edit-info")]
    (rum/use-effect! (fn []
                       (set-text! (some-> (datepicker/make-date default-value)
                                          :date
                                          datepicker/datestring))
                       (set-error! (:error (datepicker/make-date default-value)))
                       js/undefined)
                     [default-value])
    (rum/use-effect! (fn []
                       (set-bad! invalid?)
                       js/undefined)
                     [invalid?])
    ;; Close when clicked outside of the dropdown or Esc
    ;; NOTE: user could also focus another element with keyboard, outside of this el,
    ;; and this dropdown currently stays open.
    (rum/use-effect! (fn []
                       (if open?
                         (let [click-cb (fn [e]
                                          (when-not (.contains (.-current container-ref*) (.-target e))
                                            (set-open! false)))
                               esc-cb   (fn [e]
                                          (when (= components/KEY-ESC (.-keyCode e))
                                            (set-open! false)))]
                           (js/document.addEventListener "click" click-cb)
                           (js/document.addEventListener "keydown" esc-cb)
                           ;; cleanup function
                           (fn []
                             (js/document.removeEventListener "click" click-cb)
                             (js/document.removeEventListener "keydown" esc-cb)))
                         js/undefined))
                     [open?])
    [:div.day-picker-container
     {:ref container-ref*}
     [:span {:style {:display :none}
             :id    info-id}
      (common/loc :day-picker.info)]
     [:div.day-picker-input-and-button
      (-> {:value            text
           :id               (:id options)
           :invalid?         (or error? bad?)
           :aria-describedby info-id
           :ref              input-cb*
           :auto-complete    "off"
           :disabled         disabled?
           :required?        required?
           :read-only        read-only?
           :style            {:border-right 0}
           :title            title
           :class            (common/css [(:class options :day-picker-input)
                                          :no-error-bg])
           :on-key-down      (fn [e]
                               (condp = (.-keyCode e)
                                 components/KEY-DOWN (set-open! true)
                                 components/KEY-ESC  (set-open! false)
                                 nil))
           :on-blur          #(process-result text)
           :on-change        #(set-text! (.. % -target -value))}
          (common/add-test-id test-id)
          components/simple-text-edit)
      (components/icon-button {:icon        (if bad? :lupicon-circle-attention :lupicon-calendar)
                               :tab-index   -1
                               :disabled?   button-disabled?
                               :text-loc    :calendar
                               :class       [:day-picker-toggle
                                             (cond
                                               (or error? bad?) :negative
                                               button-disabled? :primary
                                               required?        :secondary
                                               :else            :secondary.regular)]
                               :icon-only?  true
                               :on-click    toggle
                               :button-type :button})]
     (when open?
       (let [date        (:date (datepicker/make-date text))
             focus-input #(.focus (rum/deref input-ref*))]
         [:div.day-picker-popup
          (datepicker/day-picker {:selected   date
                                  :on-select  (fn [day]
                                                (set-open! false)
                                                (focus-input)
                                                (process-result day))
                                  :auto-focus true})]))]))
