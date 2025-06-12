(ns lupapalvelu.ui2.status-dot
   (:require [lupapalvelu.ui2.variant-builder :refer [builder merge-props-classes]]))

 (def classes
   (builder
     {:base {:root ["w-[12px]" "h-[12px]"
                    "rounded-full"]}
      :defaults {:intent :primary}
      :variants {:intent {:primary {:root ["bg-primary"]}
                          :info {:root ["bg-ui-blue-100"]}}}}))

(defn status-dot
  {:arglists '([& children]
               [props & children])}
  [& args]
  (let [[props children] (if (map? (first args))
                           [(first args) (rest args)]
                           [nil args])]
    (into [:div
           (merge-props-classes props
                                (classes props :root))]
          children)))
