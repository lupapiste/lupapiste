(ns lupapalvelu.ui2.input
  (:require [lupapalvelu.ui2.util :refer [defuic]]
            [lupapalvelu.ui2.variant-builder :refer [builder merge-props-classes]]))

(def variants
  {:base {:root ["border-1" "border-ui-neutral-mid"
                 "rounded-md"
                 "w-full"]}
   :defaults {:size :md}
   :variants {:intent {}
              :size {:md {:root ["px-3" "py-2"]}}
              :type {"text" {}
                     "radio" {}
                     "checkbox" {}
                     "select" {}}
              :disabled {true {:root ["pointer-events-none" "bg-ui-neutral-lightest"]}
                         false {}}}})

(def classes
  (builder variants))

(defuic input
  [props _children]
  [:input
   (merge-props-classes (dissoc props :size)
                        (classes props :root))])

(defuic select
  [props children]
  (into [:select
         (merge-props-classes (-> (dissoc props :size)
                                  (assoc :type "select"))
                              (-> props
                                  (assoc :disabled (boolean (:disabled props)))
                                  (classes :root)))]
        children))
