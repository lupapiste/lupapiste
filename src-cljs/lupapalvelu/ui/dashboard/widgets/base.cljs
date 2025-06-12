(ns lupapalvelu.ui.dashboard.widgets.base
  (:require ["@dnd-kit/collision" :refer [closestCenter]]
            ["@dnd-kit/react/sortable" :refer [useSortable]]
            ["react" :as react]
            [cljs-bean.core :refer [->clj]]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.dashboard.state :as state]
            [lupapalvelu.ui2.card :as card]
            [reagent.core :refer [defc]]))

(defc base-widget [props & children]
  (let [editing? (<sub [::state/editing?])
        {:keys [ref isDragging]}
        (->clj (useSortable #js {:id (:id props)
                                 :index (:index props)
                                 :disabled (not editing?)
                                 :collisionDetection closestCenter
                                 :optimistic false
                                 :transition #js {:duration 500
                                                  ;; Using slower to start easing fn
                                                  ;; will reduce flickering.
                                                  :easing "ease-in-out"
                                                  :idle true}}))]
    (into [card/card
           {:rows (:height props "1")
            :cols (:width props "1")
            ;; TODO: Also consider using :button el for card as this is interactive,
            ;; or otherwise set role, tabIndex etc.
            :on-click (fn [_e]
                        (if editing?
                          (>evt [::state/open-modal (:id props)])
                          (>evt [::state/open-drawer (:id props)])))
            :ref ref
            :class ["dashboard-card"
                    (when editing?
                      "cursor-all-scroll select-none")]
            :data-dragging isDragging}]
          children)))
