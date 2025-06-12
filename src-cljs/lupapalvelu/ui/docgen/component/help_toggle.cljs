(ns lupapalvelu.ui.docgen.component.help-toggle
  "The `help-toggle` component draws a little '?' symbol,
  which can be clicked to show a help text to the user.
  See the corresponding `help-toggle-[model.js, template.html]`."
  (:require ["react" :as react]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.collapse :as collapse]))

;; Arrow position:
;; - We want to position the arrow according to the toggle button position
;; - it is best to place the arrow DOM element inside help content collapse,
;;   so the arrow becomes visible with the same transition.
;; - we can use ref callback to get the x position of button on mount
;; - the position can be used to position the arrow element inside collapse
;; - use same logic to get the left corner of the collapse content so
;;   window coordinates can be converted to element relative position.
;; NOTE: It is possible the position becomes invalid after window resize or such.
;; With the current layout (docgen-group-info) this doesn't seem to happen.

(defn help-toggle*
  [{:keys [help-content toggle-class]}]
  (let [[show set-show] (react/useState false)
        ;; left corner of the collapse content (window coordinates)
        [content-pos set-content-pos] (react/useState 0)
        content-ref-fn (react/useCallback (fn [^js el]
                                            (when el
                                              (set-content-pos (.-x (.getBoundingClientRect el)))))
                                          #js [])
        ;; middle x of the toggle button (window coordinates)
        [pos set-pos] (react/useState 0)
        el-ref-fn (react/useCallback (fn [^js el]
                                       (when el
                                         (set-pos (+ (.-x (.getBoundingClientRect el))
                                                     (/ (.-scrollWidth el) 2)))))
                                     #js [])]
    (react/useEffect (fn []
                       (let [f (fn [e]
                                 (when (= "Escape" (.-key e))
                                   (set-show false)))]
                         (.addEventListener js/window "keydown" f)
                         (fn []
                           (.removeEventListener js/window "keydown" f))))
                     #js [])
    (when (seq help-content)
      [:<>
       [:div.stacked.help-toggle
        {:class toggle-class}
        [:button.help
         {:ref        el-ref-fn
          :on-click   (fn [] (set-show not))
          :aria-label (common/loc :info.button)
          :class      (when show "expanded")}
         [:i.lupicon-circle-question
          {:aria-hidden true}]]]
       [collapse/collapse
        {:open? show
         ;; Keep the help text on DOM always. Arrow position seems to work either way.
         :unmount-on-exit false
         :mount-on-enter false}
        [:div {:ref content-ref-fn}]
        [:div.help-arrow
         ;; Position the arrow according to toggle button x position
         {:style {:margin-top 15
                  :margin-bottom -1
                  :width 30
                  :margin-left -15
                  :left (- pos content-pos)}}]
        [:div.cljs-help-text
         [:div.help-text
          help-content]]]
       [:div.help-reset
        common/nbsp]])))

(defn help-toggle [& args]
  (into [:f> help-toggle*] args))
