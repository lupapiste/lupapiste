(ns lupapalvelu.ui.components
  (:require [clojure.string :as s]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.hub :as hub]
            [rum.core :as rum]))

(rum/defc select [change-fn data-test-id value options & [classes]]
  [:select
   {:class (or classes "form-entry is-middle")
    :on-change    #(change-fn (.. % -target -value))
    :data-test-id data-test-id
    :value        value}
   (map (fn [[k v]] [:option {:key k :value k} v]) options)])

(rum/defc autofocus-input-field < rum/reactive
                                  {:did-mount #(-> % rum/dom-node .focus)}
  [value data-test-id commit-fn]
  (let [val (atom value)]
    [:input {:type          "text"
             :value         @val
             :on-change     #(reset! val (-> % .-target .-value))
             :on-blur       #(commit-fn @val)
             :on-key-press  #(when (= "Enter" (.-key %))
                               (do
                                 (.preventDefault %)
                                 (.stopPropagation %)
                                 (commit-fn @val)))
             :data-test-id  data-test-id}]))

(defn confirm-dialog [titleKey messageKey callback]
  (hub/send "show-dialog"
           {:ltitle titleKey
            :size   "medium"
            :component "yes-no-dialog"
            :componentParams #js {:ltext     (name messageKey)
                                  :yesFn     callback
                                  :lyesTitle "ok"
                                  :lnoTitle  "cancel"}}))

(rum/defcs pen-input < (rum/local "" ::name)
  (rum/local false ::editing?)
  "Editable text via pen button."
  [{name ::name editing? ::editing?} {:keys [value handler-fn]}]
  (if @editing?
    (letfn [(save-fn []
              (reset! editing? false)
              (handler-fn @name))]
      [:span.pen-input--edit
       [:input.grid-style-input.row-text
        {:type      "text"
         :value     @name
         :on-change (common/event->state name)
         :on-key-up #(when-not (s/blank? @name)
                       (case (.-keyCode %)
                         13 (save-fn)               ;; Save on Enter
                         27 (reset! editing? false) ;; Cancel on Esc
                         :default))}]
       [:button.primary
        {:disabled (s/blank? @name)
         :on-click save-fn}
        [:i.lupicon-save]]])
    (do (common/reset-if-needed! name value)
        [:span.pen-input--view @name
         [:button.ghost.no-border
          {:on-click #(swap! editing? not)}
          [:i.lupicon-pen]]])))

(rum/defc checkbox
  [{:keys [label value handler-fn disabled negate?]}]
  (let [input-id (str "input-" (rand-int 10000))
        value-fn (if negate? not identity)
        value (value-fn value)]
    [:div.matti-checkbox-wrapper
     [:input {:type     "checkbox"
              :disabled disabled
              :checked  value
              :id       input-id}]
     [:label.matti-checkbox-label
      {:for      input-id
       :on-click #(handler-fn (not (value-fn value)))}
      (common/loc label)]]))

(defn initial-value-mixin
  "Assocs to component's local state local-key with atom that is
  initialized to the first component argument."
  [local-key]
  {:will-mount (fn [state]
                 (assoc state local-key (-> state
                                            :rum/args
                                            first
                                            atom)))})

(defn- text-options [text* callback {:keys [required? test-id immediate?]}]
  (merge {:value     @text*
          :class     (common/css-flags :required (and required?
                                                      (-> text* rum/react s/trim s/blank?)))
          :on-change (fn [event]
                       (let [value (.. event -target -value)]
                         (common/reset-if-needed! text* value)
                         (when immediate?
                           (callback value))))}
         (when (not immediate?)
           {:on-blur #(callback (.. % -target -value))})
         (when test-id
           {:data-test-id test-id})))

;; Arguments Initial value, callback, options
;; Options [all optional]
;;  required?  truthy if required.
;;  test-id    data-test-id attribute value
;;; immediate? If true, callback is called on change (default on blur).
(rum/defcs text-edit < (initial-value-mixin ::text)
  rum/reactive
  [local-state _ callback & [options]]
  (let [text* (::text local-state)]
    [:input.grid-style-input
     (assoc (text-options text* callback options)
            :type "text")]))

(rum/defcs textarea-edit < (initial-value-mixin ::text)
  rum/reactive
  [local-state _ callback & [options]]
  (let [text* (::text local-state)]
    [:textarea.grid-style-input
     (text-options text* callback options)]))
