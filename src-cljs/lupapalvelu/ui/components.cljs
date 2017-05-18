(ns lupapalvelu.ui.components
  (:require [rum.core :as rum]
            [lupapalvelu.ui.hub :as hub]))

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
            :componentParams #js {:ltext     messageKey
                                  :yesFn     callback
                                  :lyesTitle "ok"
                                  :lnoTitle  "cancel"}}))
