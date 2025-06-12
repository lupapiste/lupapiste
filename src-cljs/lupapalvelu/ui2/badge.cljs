(ns lupapalvelu.ui2.badge
  (:require [lupapalvelu.ui2.variant-builder :refer [builder merge-props-classes]]))

(def classes
  (builder
    {:base {:root ["px-2" "py-1" "gap-2"
                   "flex" "flex-row"
                   "items-center"
                   "rounded-full"]}
     :defaults {:intent :primary}
     :variants {:intent {:primary {:root ["bg-primary"]}
                         :attention {:root ["bg-brand-orange"]}}}}))

(defn badge
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
