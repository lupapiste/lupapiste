(ns lupapalvelu.ui2.drawer
  "https://base-ui.com/react/components/drawer"
  (:require ["@base-ui/react/drawer" :refer [Drawer]]
            [lupapalvelu.ui2.util :refer [defuic]]
            [lupapalvelu.ui2.variant-builder :refer [builder merge-props-classes]]))

;; Drawer extends Dialog,
;; could extend the classes definition?

(def classes
  (builder
    {:base {:root     []
            :backdrop ["ui2-dialog-backdrop"]
            :viewport ["[--viewport-padding:0px]" "ui2-drawer-viewport"]
            :popup    ["ui2-dialog-popup"]
            :content  ["p-6 mx-auto w-full"]
            :title    ["font-semibold" "text-lg"]}
     :defaults {:from  :left
                :modal true
                :width :md}
     :variants {:modal {;; Modal prevents clicks outside of the panel, and displays the backdrop
                        ;; Non modal allows clicks outside of the panel
                        true {}
                        false {}}
                :width {:md {:popup ["[--dialog-width:30vw]"]}}
                :from {:left {:viewport ["justify-start"]
                              :popup ["from-left"]}
                       :right {:viewport ["justify-end"]
                               :popup ["from-right"]}}}}))

;; TODO: Do we want the JS library which does something
;; with the state handling, or
;; do we just do the re-frame state handling here?

(defuic root
  "Children should be either elements or a function taking the payload of trigger, to React elements.

  For controlled Dialog, the children are only added to the React tree if the Dialog is open.

  NOTE: If using the function, remember that you need to convert Reagent hiccup
  to React elements with r/as-element."
  [props children]
  (into [:> (.-Root Drawer)
         (merge-props-classes props (classes props :root))]
        children))

(defuic trigger [props children]
  (into [:> (.-Trigger Drawer)
         (merge-props-classes props (classes props :trigger))]
        children))

(defuic portal
  "NOTE: Portal is mounted under body-element so you might need to apply the
  base classes (nobase twp lp-theme) again."
  [props children]
  (into [:> (.-Portal Drawer)
         (merge-props-classes props (classes props :portal))]
        children))

(defuic backdrop [props children]
  (into [:> (.-Backdrop Drawer)
         (merge-props-classes props (classes props :backdrop))]
        children))

(defuic viewport [props children]
  (into [:> (.-Viewport Drawer)
         (merge-props-classes props (classes props :viewport))]
        children))

(defuic popup [props children]
  (into [:> (.-Popup Drawer)
         (merge-props-classes props (classes props :popup))]
        children))

(defuic content [props children]
  (into [:> (.-Content Drawer)
         (merge-props-classes props (classes props :content))]
        children))

(defuic title [props children]
  (into [:> (.-Title Drawer)
         (merge-props-classes props (classes props :title))]
        children))

(defuic description [props children]
  (into [:> (.-Description Drawer)
         (merge-props-classes props (classes props :description))]
        children))

(defuic close [props children]
  (into [:> (.-Close Drawer)
         (merge-props-classes props (classes props :close))]
        children))

;; Drawer.Indent: Wraps the main UI, adds data-active
;; would we need that? For accessibility?
