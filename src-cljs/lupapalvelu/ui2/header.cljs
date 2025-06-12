(ns lupapalvelu.ui2.header
  (:require [lupapalvelu.ui2.variant-builder :refer [builder merge-props-classes]]))

(def classes
  (builder
    {:base {:root [;; Dark purple - should this be the default?
                   ;; maybe (default) variant?
                   "text-text"]}
     :defaults {:size :h1}
     :variants {:size {:h1 {:root ["text-3xl"]}
                       :h2 {:root ["text-2xl"]}
                       :h3 {:root ["text-xl"
                                   "font-bold"]}
                       :lg {}}}}))

(defn header [props & children]
  (into [(:el props :h1)
         (merge-props-classes (dissoc props :el :size)
                              (classes props :root))]
        children))
