(ns lupapalvelu.ui2.toggle-group
  (:require [reagent.core :as r :refer [defc]]))

;; TODO: Take props for option elements from top-level props (:option-props)
;; or :props under option items?
;; TODO: Docstring
;; TODO: Storybook
(defc toggle-group
  [{:keys [value options on-change] :as props}]
  [:div
   (r/merge-props {:class ["border-primary" "border-1" "rounded-sm"
                           "flex flex-row"]}
                  (dissoc props :options :value :on-change :option-props))
   (for [{:keys [title ] :as option} options
         :let [active (boolean (= value (:value option)))]]
     [:button
      {:key (:key option)
       :type "button"
       :class ["border-primary border-r-1 last:border-r-0"
               "hover:bg-primary hover:text-white"
               "px-2 py-1"
               "first:rounded-l-sm last:rounded-r-sm"
               "text-xs"
               (if active
                 "bg-primary text-white"
                 "bg-white")]
       :aria-pressed active
       :on-click (fn [e]
                   (.stopPropagation e)
                   (on-change option e))}
      title])])
