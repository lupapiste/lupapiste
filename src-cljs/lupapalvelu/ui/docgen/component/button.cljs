(ns lupapalvelu.ui.docgen.component.button
  (:require [lupapalvelu.ui.common :as common]))

(defn button
  [{:keys [id class label on-click icon]}]
  [:button
   {:id         id
    :class      (or class "primary")
    :aria-label (common/loc label)
    :on-click   on-click}
   (when icon
     [:i {:aria-hidden true
          :class       icon}])
   [:span {:aria-hidden true}
    (common/loc label)]])
