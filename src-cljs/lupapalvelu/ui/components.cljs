(ns lupapalvelu.ui.components
  (:require [clojure.string :as s]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.hub :as hub]
            [rum.core :as rum]
            [sade.shared_util :as util])
  (:import [goog.async Delay]))

(rum/defc select [change-fn data-test-id value options & [classes]]
  [:select
   {:class (or classes "form-entry is-middle")
    :on-change    #(change-fn (.. % -target -value))
    :data-test-id data-test-id
    :value        value}
   (map (fn [[k v]] [:option {:key k :value k} v]) options)])

(rum/defc autofocus-input-field < rum/reactive
                                  {:did-mount #(-> % rum/dom-node .focus)}
  [value data-test-id commit-fn]
  (let [val (atom value)]
    [:input {:type          "text"
             :value         @val
             :on-change     #(reset! val (-> % .-target .-value))
             :on-blur       #(commit-fn @val)
             :on-key-press  #(when (= "Enter" (.-key %))
                               (do
                                 (.preventDefault %)
                                 (.stopPropagation %)
                                 (commit-fn @val)))
             :data-test-id  data-test-id}]))

(defn confirm-dialog [titleKey messageKey callback]
  (hub/send "show-dialog"
           {:ltitle titleKey
            :size   "medium"
            :component "yes-no-dialog"
            :componentParams #js {:ltext     (name messageKey)
                                  :yesFn     callback
                                  :lyesTitle "ok"
                                  :lnoTitle  "cancel"}}))

(rum/defcs pen-input < (rum/local "" ::name)
  (rum/local false ::editing?)
  "Editable text via pen button."
  [{name ::name editing? ::editing?} {:keys [value handler-fn]}]
  (if @editing?
    (letfn [(save-fn []
              (reset! editing? false)
              (handler-fn @name))]
      [:span.pen-input--edit
       [:input.grid-style-input.row-text
        {:type      "text"
         :value     @name
         :on-change (common/event->state name)
         :on-key-up #(when-not (s/blank? @name)
                       (case (.-keyCode %)
                         13 (save-fn)               ;; Save on Enter
                         27 (reset! editing? false) ;; Cancel on Esc
                         :default))}]
       [:button.primary
        {:disabled (s/blank? @name)
         :on-click save-fn}
        [:i.lupicon-save]]])
    (do (common/reset-if-needed! name value)
        [:span.pen-input--view @name
         [:button.ghost.no-border
          {:on-click #(swap! editing? not)}
          [:i.lupicon-pen]]])))

(rum/defc checkbox
  [{:keys [label value handler-fn disabled negate?]}]
  (let [input-id (str "input-" (rand-int 10000))
        value-fn (if negate? not identity)
        value (value-fn value)]
    [:div.matti-checkbox-wrapper
     [:input {:type     "checkbox"
              :disabled disabled
              :checked  value
              :id       input-id}]
     [:label.matti-checkbox-label
      {:for      input-id
       :on-click #(handler-fn (not (value-fn value)))}
      (common/loc label)]]))

(defn initial-value-mixin
  "Assocs to component's local state local-key with atom that is
  initialized to the first component argument."
  [local-key]
  {:will-mount (fn [state]
                 (assoc state local-key (-> state
                                            :rum/args
                                            first
                                            atom)))})

(defn- text-options [text* callback {:keys [required? test-id immediate?] :as options}]
  (merge {:value     @text*
          :class     (common/css-flags :required (and required?
                                                      (-> text* rum/react s/trim s/blank?)))
          :on-change (fn [event]
                       (let [value (.. event -target -value)]
                         (common/reset-if-needed! text* value)
                         (when immediate?
                           (callback value))))}
         (when (not immediate?)
           {:on-blur #(callback (.. % -target -value))})
         (when test-id
           {:data-test-id test-id})
         (dissoc options :required? :test-id :immediate?)))

;; Arguments Initial value, callback, options
;; Options [all optional]
;;  required?  truthy if required.
;;  test-id    data-test-id attribute value
;;; immediate? If true, callback is called on change (default on blur).
(rum/defcs text-edit < (initial-value-mixin ::text)
  rum/reactive
  [local-state _ callback & [options]]
  (let [text* (::text local-state)]
    [:input.grid-style-input
     (assoc (text-options text* callback options)
            :type "text")]))

(rum/defcs textarea-edit < (initial-value-mixin ::text)
  rum/reactive
  [local-state _ callback & [options]]
  (let [text* (::text local-state)]
    [:textarea.grid-style-input
     (text-options text* callback options)]))

(defn- default-items-fn [items]
  (fn [term]
    (let [fuzzy (common/fuzzy-re term)]
      (filter #(re-find fuzzy (:text %)) items))))

(defn- scroll-element-if-needed [container elem]
  (let [scroll-top (.-scrollTop container)
        container-top (.-offsetTop container)
        container-height (.-offsetHeight container)
        scroll-bottom (+ scroll-top container-height)
        elem-top (- (.-offsetTop elem) container-top)
        elem-height (.-offsetHeight elem)
        elem-bottom (+ elem-top elem-height)
        scroll         (cond
                         (< elem-top scroll-top)
                         elem-top
                         (> elem-bottom scroll-bottom)
                         (+ (- elem-top container-height)
                            elem-height))]
    (when (integer? scroll)
      (aset container "scrollTop" scroll))))

;; Options [optional]:
;;   items: either list or function. The function argument is the
;;          filtering term. An item is a map with mandatory :text
;;          and :value keys and optional :group key.
;;
;;   [clear?] If truthy, clear button is shown when proper (default
;;            false).
(rum/defcs autocomplete < (initial-value-mixin ::selected)
  rum/reactive
  (rum/local "" ::term)    ; Filtering term
  (rum/local 0 ::current) ; Currently highlighted item
  (rum/local false ::open?)
  [{selected* ::selected
    term*     ::term
    current*  ::current
    open?*    ::open?
    :as       local-state} _ callback {:keys [items clear?]}]
  (let [items-fn (if (fn? items)
                   items
                   (default-items-fn items))
        open?    (rum/react open?*)
        items    (items-fn (rum/react term*))]
    (when-not open?
      (common/reset-if-needed! term* ""))
    [:div.matti-autocomplete
     [:div.like-btn.ac--selected
      {:on-click #(swap! open?* not)}
      [:span (:text (util/find-by-key :value
                                      (rum/react selected*)
                                      (items-fn "")))]
      [:i.primary.ac--chevron
       {:class (common/css-flags :lupicon-chevron-small-down (not open?)
                                 :lupicon-chevron-small-up   open?)}]
      (when (and clear?
                 (not (s/blank? (rum/react selected*))))
        [:i.secondary.ac--clear.lupicon-remove
         {:on-click (fn [event]
                      (reset! selected* "")
                      (.stopPropagation event))}])]
     (when (rum/react open?*)
       (letfn [(close []
                 (reset! open?* false)
                 (reset! current* 0))
               (select [value]
                 (when value
                   (reset! selected* value))
                 (close))
               (inc-current [n]
                 (when (pos? (count items))
                   (do (swap! current* #(rem (+ (or % 0) n) (count items)))
                       (scroll-element-if-needed (rum/ref local-state "autocomplete-items")
                                                 (rum/ref local-state (str "item-" @current*))))))]
         [:div.ac__menu
          [:div.ac__term (text-edit @term*
                                    (fn [text]
                                      (reset! term* text)
                                      (common/reset-if-needed! current* 0))
                                    {:immediate?  true
                                     :auto-focus  true
                                     ;; Delay is needed to make sure
                                     ;; that possible selection will
                                     ;; be processed and the menu will
                                     ;; not accidentally reopen.
                                     :on-blur     #(.start (Delay. close 200))
                                     :on-key-down #(case (.-keyCode %)
                                                     ;; Enter
                                                     13 (do
                                                          (select (some->> @current*
                                                                           (get (vec items))
                                                                           :value))
                                                          (.preventDefault %))
                                                     ;; Esc
                                                     27 (close)
                                                     ;; Up
                                                     38 (inc-current (- (count items) 1))
                                                     ;; Down
                                                     40 (inc-current 1)
                                                     :default)})]
          [:ul.ac__items
           {:ref "autocomplete-items"}
           (loop [[x & xs]   (vec items)
                  index      0
                  last-group ""
                  li-items   []]
             (if x
               (let [{:keys [text value group]} x
                     group (-> (or group "") s/trim)
                     li-items (if (= group last-group)
                              li-items
                              (conj li-items [:li.ac--group group]))]
                 (recur xs
                        (inc index)
                        group
                        (conj li-items
                              [:li
                               {:on-click       #(select value)
                                :ref            (str "item-" index)
                                :on-mouse-enter #(reset! current* index)
                                :class          (common/css-flags :ac--current (= (rum/react current*)
                                                                                  index)
                                                                  :ac--grouped (not (s/blank? group)))}
                               text])))
               li-items))]]))]))
