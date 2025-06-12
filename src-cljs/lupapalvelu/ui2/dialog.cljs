(ns lupapalvelu.ui2.dialog
  "https://base-ui.com/react/components/dialog"
  (:require ["@base-ui/react/dialog" :refer [Dialog]]
            [lupapalvelu.ui2.util :refer [defuic]]
            [lupapalvelu.ui2.variant-builder :refer [builder merge-props-classes]]))

(def classes
  (builder
    {:base {:backdrop ["ui2-dialog-backdrop"]
            :popup ["ui2-dialog-popup"
                    "rounded-md"]}
     :defaults {:size :md
                :position :center}
     :variants {:size {:md {:popup ["[--dialog-width:32rem]"]}}
                :position {:center {:popup ["position-center"]}}}}))

;; TODO: Do we want the JS library which does something
;; with the state handling, or
;; do we just do the re-frame state handling here?

(defuic root
  "Children should be either elements or a function taking the payload of trigger, to React elements.

  For controlled Dialog, the children are only added to the React tree if the Dialog is open.

  NOTE: If using the function, remember that you need to convert Reagent hiccup
  to React elements with r/as-element."
  [props children]
  (into [:> (.-Root Dialog)
         (merge-props-classes props (classes props :root))]
        ;; TODO: If 1 children and it is fn?, wrap it to apply r/as-element
        ;; to the return value?
        children))

(defuic trigger [props children]
  (into [:> (.-Trigger Dialog)
         (merge-props-classes props (classes props :trigger))]
        children))

(defuic portal
  "NOTE: Portal is mounted under body-element so you might need to apply the
  base classes (nobase twp lp-theme) again."
  [props children]
  (into [:> (.-Portal Dialog)
         (merge-props-classes props (classes props :portal))]
        children))

(defuic backdrop [props children]
  (into [:> (.-Backdrop Dialog)
         (merge-props-classes props (classes props :backdrop))]
        children))

(defuic viewport [props children]
  (into [:> (.-Viewport Dialog)
         (merge-props-classes props (classes props :viewport))]
        children))

(defuic popup [props children]
  (into [:> (.-Popup Dialog)
         (merge-props-classes props (classes props :popup))]
        children))

(defuic title [props children]
  (into [:> (.-Title Dialog)
         (merge-props-classes props (classes props :title))]
        children))

(defuic description [props children]
  (into [:> (.-Description Dialog)
         (merge-props-classes props (classes props :description))]
        children))

(defuic close [props children]
  (into [:> (.-Close Dialog)
         (merge-props-classes props (classes props :close))]
        children))

(defn create-handle []
  (.createHandle Dialog))
