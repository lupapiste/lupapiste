(ns lupapalvelu.ui.application.util
  (:require ["react" :as react]
            [lupapalvelu.ui.misc]))

(defn js-use-auth-model-result
  "Return a reactive result of auth-model ok check.

  Internally stores the current value in a state hook and the reactivity is implemented
  using an Observable subscription, which is built and destroyed using effect hook."
  [^js auth-model op-name]
  (let [[value set-value] (react/useState (when auth-model (.. auth-model (ok op-name))))]
    (react/useEffect (fn []
                       (if auth-model
                         (let [^js sub (.. auth-model
                                           (okObservable op-name)
                                           (subscribe (fn [x] (set-value x))))]
                           (fn []
                             (.dispose sub)))
                         js/undefined))
                     #js [auth-model op-name])
    value))
