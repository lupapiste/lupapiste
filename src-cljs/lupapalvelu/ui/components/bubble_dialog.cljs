(ns lupapalvelu.ui.components.bubble-dialog
  "CLJS variant of `private/ui-components/bubble-dialog/*`."
  (:require [lupapalvelu.ui.common :as common]))

(defn bubble-dialog
  [{:keys [visible? class error button-icon
           ok-visible? ok-enabled? ok-waiting? ok-text ok-fn
           remove-visible? remove-enabled? remove-waiting? remove-text remove-fn
           cancel-fn cancel-text]
    :or   {class           "bubble-dialog"
           ok-text         "ok"
           ok-visible?     false
           ok-enabled?     true
           ok-waiting?     false
           remove-text     "remove"
           remove-visible? false
           remove-enabled? false
           remove-waiting? false
           cancel-text     "cancel"}}
   & children]
  (when visible?
    [:div {:class class}
     (into [:div]
           children)
     (when error
       [:div.error (common/loc error)])
     (when ok-visible?
       [:div.left-buttons
        [:button.positive {:on-click ok-fn
                           :disabled (not ok-enabled?)
                           :class    (when ok-waiting? "waiting")}
         (when button-icon
           [:i {:aria-hidden true
                :class       button-icon}])
         [:i {:aria-hidden true
              :class       "wait spin lupicon-refresh"}]
         [:span (common/loc ok-text)]]
        (when remove-visible?
          [:button.negative {:on-click remove-fn
                             :disabled (not remove-enabled?)
                             :class    (when remove-waiting? "waiting")}
           [:i {:aria-hidden true
                :class       "lupicon-remove"}]
           [:i {:aria-hidden true
                :class       "wait spin lupicon-refresh"}]
           [:span (common/loc remove-text)]])
        [:button.secondary {:on-click cancel-fn}
         [:i {:aria-hidden true
              :class       "lupicon-remove"}]
         [:span (common/loc cancel-text)]]])]))
