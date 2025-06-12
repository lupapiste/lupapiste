(ns lupapalvelu.ui2.card
  (:require [lupapalvelu.ui2.util :refer [defuic]]
            [lupapalvelu.ui2.variant-builder :refer [builder merge-props-classes]]))

(def classes
  (builder
    {:base {:root ["bg-ui-white"
                   "text-text"
                   "flex"
                   "flex-col"

                   ;; TODO: Use component variables?
                   "hover:bg-ui-blue-10"
                   "hover:border-ui-blue-100"
                   "focus:bg-ui-blue-20"
                   "focus:border-ui-blue-100"

                   "rounded-sm"
                   "border-1"
                   "border-card-border"]
            ;; Using a variable here seems like simplest way to set the title/number
            ;; font-size based on a variant without duplicating font-size utils.
            :title ["text-(length:--card-title)" "font-bold" "leading-none"
                    ;; Prevent text wrap and show ellipsis if the text is too long
                    "overflow-hidden" "text-ellipsis" "text-nowrap"]
            :huge ["text-(length:--card-huge)" "leading-none" "font-mono"]
            :content ["flex-1"
                      "flex"
                      "flex-col"]}
     ;; 1 row variant "huge" number is text-xl
     ;; 2 row variant "huge" number is text-5xl
     :defaults {}
     :variants {:rows {"1" {:root ["row-span-1"
                                   "p-2"
                                   "gap-2"
                                   ;; Why doesn't --text-md work here? Aren't those also css variables.
                                   "[--card-title:var(--figma-text-md)]"
                                   "[--card-huge:var(--figma-text-xl)]"]}
                       "2" {:root ["row-span-2"
                                   "p-4"
                                   "gap-4"
                                   "[--card-title:var(--figma-text-xl)]"
                                   "[--card-huge:var(--figma-text-5xl)]"]}
                       "4" {:root ["row-span-4"
                                   "p-4"
                                   "gap-4"
                                   "[--card-title:var(--figma-text-xl)]"
                                   "[--card-huge:var(--figma-text-5xl)]"]}}
                :cols {"1" {:root ["col-span-1"]}
                       "2" {:root ["col-span-1 md:col-span-2"]}
                       "4" {:root ["col-span-1 md:col-span-4"]}}}}))

(defuic card
  [props children]
  (into [(:el props :div)
         (merge-props-classes (dissoc props :rows :cols :el)
                              (classes props :root))]
        children))

(defuic title
  [props children]
  (into [:h2
         (merge-props-classes props
                              (classes props :title))]
        children))

(defuic huge
  [props children]
  (into [:span
         (merge-props-classes props
                              (classes props :huge))]
        children))

(defuic content
  [props children]
  (into [:div
         (merge-props-classes props
                              (classes props :content))]
        children))
