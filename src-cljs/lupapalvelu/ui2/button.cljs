(ns lupapalvelu.ui2.button
  (:require [lupapalvelu.ui2.util :refer [defuic]]
            [lupapalvelu.ui2.variant-builder :refer [builder merge-props-classes]]))

(def variants
  {:base {:root ["inline-flex" "items-center"
                 "rounded-sm"
                 "uppercase"
                 "font-bold"
                 ;; Visible 3px outline for focused elements
                 "focus-visible:outline-3 focus-visible:outline-focus-ring"
                 ;; Add [--btn-border-width:x] (and a color) to a variant to add a border,
                 ;; and this is automatically subtracted from y padding for size variants.
                 "border-(length:--btn-border-width)"
                 "[--btn-border-width:0px]"]}
   :defaults {:intent :primary
              :size :md}
   :variants {:intent {:primary   {:root ["bg-primary"
                                          "text-white"
                                          "hover:bg-primary-hover"
                                          "active:bg-primary-active"
                                          "focus-visible:bg-primary-focus"]}
                       :secondary {:root ["bg-secondary"
                                          "[--btn-border-width:2px]"
                                          "border-primary"
                                          "text-primary"
                                          "hover:bg-secondary-hover hover:text-white"
                                          "active:bg-secondary-active"
                                          "focus-visible:bg-secondary-focus"]}
                       #_#_
                       :tertiary  {:root ["text-text"
                                          "hover:bg-secondary-hover hover:text-white"
                                          "active:bg-secondary-active"
                                          "focus-visible:bg-secondary-focus"]}
                       :text      {:root [;; TODO: Real styles
                                          "text-text"
                                          "hover:bg-primary-hover/10"]}}
              :size {:xs {:root ["gap-2"
                                 ;; TODO: Real xs size
                                 "leading-[1.2]"
                                 "px-3"
                                 "py-[calc(1.5*var(--spacing)-1px-var(--btn-border-width))]"]}
                     :sm {:root ["gap-2"
                                 "leading-[1.2]"
                                 "px-4"
                                 "py-[calc(2*var(--spacing)-1px-var(--btn-border-width))]"]}
                     :md {:root ["leading-none"
                                 ;; 8px gap
                                 "gap-2"
                                 ;; 16px & 15px
                                 "px-4"
                                 "py-[calc(4*var(--spacing)-1px-var(--btn-border-width))]"]}
                     :lg {:root ["leading-none"
                                 ;; TODO: Real lg size
                                 ;; 8px gap
                                 "gap-2"
                                 "text-lg"
                                 ;; 16px & 15px
                                 "px-6"
                                 "py-[calc(6*var(--spacing)-1px-var(--btn-border-width))]"]}}
              :disabled {true {:root ["pointer-events-none"
                                      ;; TODO: Might need to specify per intent
                                      "text-ui-neutral-dark!"
                                      "border-ui-neutral-light!"
                                      "bg-ui-neutral-lighter!"]}
                         false {}}}})

;; TODO: Should this somehow declare
;; :hover, :active, :focus ? for documentation?
(def classes
  (builder variants))

;; TODO: Util to dissoc variants flags?
;; NOTE: Disabled is both a flag, and a html element property.

(defuic button
  [props children]
  (into [(:el props :button)
         (merge-props-classes (dissoc props :intent :size :el)
                              ;; Coerce the boolean flags for the variants
                              (-> props
                                  (assoc :disabled (boolean (:disabled props)))
                                  (classes :root)))]
        children))
