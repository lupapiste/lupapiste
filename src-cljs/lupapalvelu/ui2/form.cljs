(ns lupapalvelu.ui2.form 
  (:require [lupapalvelu.ui2.util :refer [defuic]]
            [lupapalvelu.ui2.variant-builder :refer [builder merge-props-classes]]))

(def variants
  {:base {:root ["flex" "flex-col"
                 "my-2"
                 "[&>label]:font-semibold"]}
   :defaults {}
   :variants {:intent {}}})

(def classes
  (builder variants))

(defuic form-field
  [props children]
  (into [:div
         (merge-props-classes (dissoc props :size)
                              (classes props :root))]
        children))
