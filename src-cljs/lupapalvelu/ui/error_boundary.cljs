(ns lupapalvelu.ui.error-boundary
  (:require [reagent.core :as r]))

(defn error-boundary
  "Generic error boundary
  See https://github.com/reagent-project/reagent/blob/master/doc/ReactFeatures.md#error-boundaries"
  [_options _child]
  (r/create-class
    {:constructor                  (fn [this _props]
                                     (set! (.-state this) #js {:error nil}))
     :component-did-catch          (fn [_this _e _info])
     :get-derived-state-from-error (fn [error]
                                     #js {:error error})
     :render                       (fn [this]
                                     (r/as-element
                                       ;; Get fresh arguments to make sure `child` is up-to-date
                                       (let [[_ {:keys [on-error]} child] (r/argv this)]
                                         (if-let [error (.. this -state -error)]
                                           (on-error {:error error})
                                           child))))}))
