(ns lupapalvelu.ui.components
  (:require [rum.core :as rum]))

(rum/defc select [change-fn data-test-id value options]
          [:select.form-entry.is-middle
           {:on-change #(change-fn (.. % -target -value))
            :data-test-id data-test-id
            :value     value}
           (map (fn [[k v]] [:option {:value k} v]) options)])

(defn confirm-dialog [title message callback]
          (.send js/hub
                 "show-dialog"
                 #js
                   {:ltitle title
                    :size   "medium"
                    :component "yes-no-dialog"
                    :componentParams #js {:text      message
                                          :yesFn     callback
                                          :lyesTitle "ok"
                                          :lnoTitle  "cancel"}}))