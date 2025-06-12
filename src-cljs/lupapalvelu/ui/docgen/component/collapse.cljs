(ns lupapalvelu.ui.docgen.component.collapse
  (:require ["react" :as react]
            ["react-transition-group" :refer [Transition]]
            [lupapalvelu.ui.components :as components]
            [reagent.core :as r]))

;; Uses similar approach to MUI/Collapse

(defn collapse*
  "Hide/show content based on the `open?` paramater.
  Removes content from the DOM when not visible."
  [{:keys [open? mount-on-enter unmount-on-exit id]
    :or {mount-on-enter true
         unmount-on-exit true}}
   & content]
  (let [wrapper-ref (react/useRef nil)
        node-ref    (react/useRef nil)]
    ;; Could allow specifying the collapsed height, instead of always using 0px
    [:> Transition
     {:in          open?
      ;; Don't use transition on first render (default)
      :appear      false
      :node-ref    node-ref
      ;; Could also use different transition options for enter and exit
      :on-enter    (fn [_is-appearing]
                     (set! (.. node-ref -current -style -height) "0px"))
      :on-entering (fn [_is-appearing]
                     (let [wrapper-size (.. wrapper-ref -current -clientHeight)]
                       (set! (.. node-ref -current -style -height) (str wrapper-size "px"))))
      :on-entered  (fn [_is-appearing]
                     (set! (.. node-ref -current -style -height) "auto"))
      :on-exit     (fn []
                     (let [wrapper-size (.. wrapper-ref -current -clientHeight)]
                       (set! (.. node-ref -current -style -height) (str wrapper-size "px"))))
      :on-exiting  (fn []
                     ;; Needs a immediate timeout to allow the height from on-exit to apply?
                     (js/setTimeout (fn []
                                      (set! (.. node-ref -current -style -height) "0px"))
                                    0))
      :timeout     300
      ;; Remove the content from DOM when not open
      ;; NOTE: Removes also collapse_slider from DOM, not just collapse_content
      ;; Could check for state in the fn to conditionally render collapse_content?
      :mount-on-enter mount-on-enter
      :unmount-on-exit unmount-on-exit}
     (fn [state]
       (r/as-element
         [:div.collapse_slider
          {:ref   node-ref
           :class state
           :id    id}
          [:div.collapse_wrapper
           {:ref wrapper-ref}
           (into [:div.collapse_content] content)]]))]))

(defn collapse [& args]
  (into [:f> collapse*] args))

(defn value-selector-wrapper
  "Wrapper that takes an input component and a value selector component, and
  handles the opening the selector when wrapper is clicked of element inside
  wrapper is focused. Closes selector with ESC, outside click or when element
  outside the wrapper is focused. Passes close function to selector element."
  [_input-component _selector-component]
  (r/with-let [*open?      (r/atom false)
               *ref        (r/atom nil)
               open        #(reset! *open? true)
               close       #(reset! *open? false)
               ref-handler (fn [e] (if (.contains @*ref (.-target e))
                                     (open)
                                     (close)))
               esc-handler (fn [e] (when (= (.-keyCode e) components/KEY-ESC)
                                     (close)))]
    (r/create-class
      {:component-did-mount    #(doto js/document
                                  (.addEventListener "click" ref-handler true)
                                  (.addEventListener "focusin" ref-handler true)
                                  (.addEventListener "keydown" esc-handler true))
       :component-will-unmount #(doto js/document
                                  (.removeEventListener "click" ref-handler true)
                                  (.removeEventListener "focusin" ref-handler true)
                                  (.removeEventListener "keydown" esc-handler true))
       :reagent-render         (fn [input-component selector-component]
                                 [:div
                                  {:ref #(reset! *ref %)}
                                  input-component
                                  [:div
                                   {:class [(when-not @*open? "hidden")]}
                                   [selector-component close]]])})))
