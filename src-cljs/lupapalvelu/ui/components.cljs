(ns lupapalvelu.ui.components
  (:require [cljs.pprint :refer [pprint]]
            [clojure.walk :as walk]
            [goog.object]
            [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.pate.markup :as markup]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.datepicker :as datepicker]
            [lupapalvelu.ui.misc]
            [reagent.core :as r]
            [rum.core :as rum]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util])
  (:import [goog.async Delay]))

(def ^:const KEY-ENTER 13)
(def ^:const KEY-ESC 27)
(def ^:const KEY-UP 38)
(def ^:const KEY-DOWN 40)

(defn atom-on-change
  "Common 'on-change' event option: value atom is reset with element value."
  [value*]
  {:on-change #(reset! value* (.. % -target -value))})

(rum/defc select [value options {:keys [callback test-id class] :as opts}]
  (into
    [:select.lux
     (merge
       {:class        (cond-> "is-middle" class (str " " class))
        :on-change    #(callback (.. % -target -value))
        :data-test-id test-id
        :value        value}
       (dissoc opts :class :callback :test-id))]
    (for [[k v] options]
      [:option {:key k :value k} v])))

(defn confirm-dialog [titleKey messageKey callback]
  (hub/send "show-dialog"
            {:ltitle          titleKey
             :size            "medium"
             :component       "yes-no-dialog"
             :componentParams #js {:ltext     (name messageKey)
                                   :yesFn     callback
                                   :lyesTitle "ok"
                                   :lnoTitle  "cancel"}}))

(defn initial-value-mixin
  "Assocs to component's local state local-key with atom that is
  initialized to the first component argument. If the argument is an
  atom or cursor it is used as is (aka poor man's two-way binding)"
  [local-key]
  (letfn [(update-state
            ([state props] (update-state state))
            ([state]
             (let [value (-> state :rum/args first)]
               (assoc state local-key (common/atomize value)))))]
    {:init         update-state
     :will-remount #(update-state %2)}))

(defn default-arg->local-atom
  "Kind of similar to initial-value mixin, but does not do atomizing or anything special.
  Just takes first of :rum/args and uses it as default value for an atom that is created to local state.
  Idea is similar to 'useState(default)' hook.

  Implementation is slight modification of `rum.core/local` mixin."
  [key]
  {:will-mount
   (fn [state]
     (let [local-state (atom (first (:rum/args state)))
           component   ^js (:rum/react-component state)]
       (add-watch local-state key
                  (fn [_ _ p n]
                    (when (not= p n)
                      (.forceUpdate component))))
       (assoc state key local-state)))})

(rum/defc simple-text-edit
  "Simpler version of text-edit. Value and on-change attributes should be controlled by parent!
  Only _special_ attributes for input handled by this component are (all optional):
    * required? - See `common/required-invalid-attributes`
    * invalid? - See `common/required-invalid-attributes`
    * test-id - adds data-test-id if given
    * class - sequence of classes to apply
    * lines - turns the component into <textarea> element instead of the default <input>
    * enter-callback - function (no arguments) that is to be called when Enter key has
      been pressed. Note that if lines is given, the callback is called after every
      newline, which is probably not wanted.

  Rest of the options are merged into the underlying HTML element attributes."
  < rum/reactive
  [{:keys [required? test-id class lines value enter-callback] :as options}]
  [(if lines :textarea :input)
   (merge
     (util/assoc-when
       {:class (common/css [:lux class])}
       ;; :type can be nil in options
       :type (when-not lines (or (:type options) :text))
       :rows lines
       :value (or value "")
       :on-key-up (fn [evt]
                    (when (and enter-callback
                               (= (-> evt .-keyCode) KEY-ENTER))
                      (enter-callback))))
     (common/add-test-id {} test-id)
     (common/required-invalid-attributes options value)
     (dissoc options :value :type :required? :invalid? :test-id :class :lines
             :enter-callback))])

(rum/defc text-edit
  "Wrapper for simple-text-edit component that uses useState hook to keep local input state.
  Turns into textarea if :lines options is given.
  Calls :callback 'onChange', thus debouncing etc needs to be done in parent.
  And thus is 'always immediate' by default.

  Excluding `:callback` all the other options are passed to `simple-text-edit`."
  [default-value {:keys [callback] :as options}]
  (let [[text set-text!] (rum/use-state (or default-value ""))]
    (rum/use-effect! #(set-text! (or default-value "")) [default-value])
    (simple-text-edit (-> options
                          (assoc :value text
                                 :on-change (fn [evt]
                                              (set-text! (.. evt -target -value))
                                              (when callback
                                                (callback (.. evt -target -value)))))
                          (dissoc :callback)))))

(rum/defc textarea-edit
  "Wrapper for text-edit. The default `:lines` value is 5."
  [default-value options]
  (text-edit default-value (update options :lines (fnil identity 5))))


(rum/defc simple-textarea-edit
  "Wrapper simple-text-edit. The default `:lines` value is 5."
  < rum/reactive
  [options]
  (simple-text-edit (update options :lines (fnil identity 5))))

(declare icon-button)

(rum/defc search-bar
  "A textfield with a search icon attached
  :callback is required, it is called :on-change event with the value.
  If you want to make search-bar controlled, provide :value in options."
  [{:keys [value callback commit-callback clear-callback] :as options}]
  (let [[text set-text!] (rum/use-state nil)]
    (rum/use-effect! (fn []
                       (set-text! value))
                     [value])
    [:div.dsp--flex
     (-> options
         (dissoc :value :callback :commit-callback :clear-callback)
         (merge {:value          text
                 :class          :search.flex--g1
                 :on-change      (fn [evt]
                                   (let [value (-> evt .-target .-value)]
                                     (set-text! value)
                                     (callback value)))
                 :enter-callback commit-callback
                 })
         simple-text-edit)
     (when-not (ss/blank? text)
       (icon-button {:icon       :lupicon-remove
                     :text-loc   :pate.clear
                     :icon-only? true
                     :class      :tertiary.flex--g0
                     :on-click   (fn []
                                   (when clear-callback
                                     (clear-callback))
                                   (set-text! nil))}))]))

(defn delayed-search-bar
  "Reagent wrapper for `search-bar` that introduces on automatic, delayed search term
  activation. Called with initial value and options [optional]:

  callback: Called when text has been changed and delay passed.

  [delay-ms]: Delay in milliseconds (default 500). The search term is activated (and the
  callback called) when the text is changed and the delay amount of time has passed
  without further changes. When enter is pressed the term is activated without delay.

  The other options are passed to underlying `search-bar`. Note that commit and clear
  callbacks are not supported."
  [initial-value {:keys [callback delay-ms] :as options}]
  (r/with-let [text* (r/atom initial-value)
               delay (Delay. #(callback @text* [::commit-search-text])
                             (or delay-ms 500))]
    [search-bar (merge (dissoc options :delay-ms)
                       {:value          initial-value
                        :callback        (fn [text]
                                           (reset! text* text)
                                           (.start delay))
                        :commit-callback (fn []
                                           (.stop delay)
                                           (callback @text*))
                        :clear-callback  (fn []
                                           (.stop delay)
                                           (reset! text* "")
                                           (callback @text*))})]
    (finally
      (.dispose ^Delay delay))))

(defn fuzzy-filter [items term]
  (let [fuzzy (common/fuzzy-re term)]
    (filter #(some->> (:text %) str ss/blank-as-nil (re-find fuzzy)) items)))

(defn default-items-fn [items]
  (partial fuzzy-filter items))

(defn- scroll-element-if-needed [container elem]
  (when container
    (let [scroll-top       (.-scrollTop container)
          container-top    (.-offsetTop container)
          container-height (.-offsetHeight container)
          scroll-bottom    (+ scroll-top container-height)
          elem-top         (- (.-offsetTop elem) container-top)
          elem-height      (.-offsetHeight elem)
          elem-bottom      (+ elem-top elem-height)
          scroll           (cond
                             (< elem-top scroll-top)
                             elem-top
                             (> elem-bottom scroll-bottom)
                             (+ (- elem-top container-height)
                                elem-height))]
      (when (integer? scroll)
        (aset container "scrollTop" scroll)))))

(defn- set-selected [selected* value callback]
  (when (common/reset-if-needed! selected* value)
    (when callback (callback value))))

(defn- complete-parts
  "Very (too?) straight-forward DRY solution for sharing code between
  autocomplete and combobox. Returns map with :items-fn :text-edit
  and :menu-items. Nowadays only used by autocomplete."
  [local-state
   {:keys [items callback]}
   text-edit-options
   tags?]
  (let [{selected* ::selected
         term*     ::term
         current*  ::current
         open?*    ::open?
         }          local-state
        selected    (some-> selected* deref)
        items-fn    (if (fn? items)
                      items
                      (default-items-fn items))
        items       (vec (items-fn (rum/react term*)))
        text-id     (:id text-edit-options (common/unique-id "text-edit"))
        menu-id     (common/unique-id "menu")
        close       (fn []
                      (reset! open?* false)
                      (reset! current* 0))
        select      (fn [value]
                      (when value
                        (if tags?
                          ;; Don't allow duplicate tags in the selection
                          (when-not (some #{value} selected)
                            (set-selected selected* (conj (if (vector? selected) selected []) value) callback))
                          (set-selected (or selected* term*) value callback)))
                      (close))
        inc-current (fn [n]
                      (when (pos? (count items))
                        (swap! current* #(rem (+ (or % 0) n) (count items)))))
        close-delay (Delay. close 200)
        text-ref    (rum/create-ref)]
    {:items-fn   items-fn
     :text-id    text-id
     :text-edit  (text-edit
                   @term*
                   (merge {:callback          (fn [text]
                                                (reset! term* text)
                                                (common/reset-if-needed! current* 0))
                           :id                text-id
                           :ref               text-ref
                           :aria-controls     menu-id
                           :auto-complete     "off"
                           :aria-autocomplete :list
                           :auto-focus        true
                           :on-blur           (fn []
                                                ;; Delay is needed to make sure
                                                ;; that possible selection will
                                                ;; be processed and the menu will
                                                ;; not accidentally reopen.
                                                (.start close-delay))
                           :on-key-down       #(case (.-keyCode %)
                                                 KEY-ENTER
                                                 (do
                                                   (select (some->> @current*
                                                                    (get items)
                                                                    ((fn [item] (if tags?
                                                                                  item
                                                                                  (:value item))))))
                                                   (.preventDefault %))

                                                 KEY-ESC  (close)
                                                 KEY-UP   (inc-current (- (count items) 1))
                                                 KEY-DOWN (inc-current 1)
                                                 :default)}
                          text-edit-options))
     :menu-id    menu-id
     :menu-items [:ul.ac__items
                  {:id   menu-id
                   :role :listbox}
                  (loop [[x & xs]   items
                         index      0
                         last-group ""
                         li-items   []]
                    (if x
                      (let [{:keys [text value group]} x
                            group                      (-> (or group "") ss/trim ss/blank-as-nil)
                            li-items                   (if (or (nil? group) (= group last-group))
                                                         li-items
                                                         (conj li-items
                                                               [:li.ac--group
                                                                {:on-click (fn []
                                                                             (.stop close-delay)
                                                                             (.focus (rum/deref text-ref))
                                                                             false)}
                                                                group]))
                            current?                   (= (rum/react current*) index)]
                        (recur xs
                               (inc index)
                               group
                               (conj li-items
                                     [:li
                                      {:on-click       #(select (if tags? x value))
                                       :on-mouse-enter #(reset! current* index)
                                       :class          (common/css-flags :ac--current current?
                                                                         :ac--grouped group)
                                       :role           :option
                                       :aria-selected  current?}
                                      text])))
                      li-items))]}))


(rum/defc combobox
  "Combobox is a text-edit with additional, non-binding content alternatives.

   Parameters:
     initial-value
     options
   Initial value should be string.
   Options consist of every simple-text-edit option and

     items     Sequence of items. An item is a map with a mandatory :text and
               optional :group key.

     callback  Function that is called with the contents of the text field."
  [value {:keys [callback items disabled placeholder test-id]
          :as   options}]
  (let [[current-idx
         set-index!] (rum/use-state 0)
        [open? set-open!] (rum/use-state false)
        items       (->> (fuzzy-filter items value)
                         (mapv #(assoc % :value (:text %))))
        inc-current (fn [n]
                      (when (pos? (count items))
                        (set-index! (rem (+ (or current-idx 0) n) (count items)))))
        select      (fn [new-val]
                      (callback new-val))
        text-id     (:id options (common/unique-id "text"))
        menu-id     (common/unique-id "menu")]
    [:div.pate-autocomplete
     (cond-> {:role          :combobox
              :aria-expanded open?
              :aria-label    (:common/loc :document.edit)
              :aria-haspopup :listbox
              :aria-controls text-id}
             open? (assoc :aria-owns menu-id))
     [:div.ac--combobox
      (simple-text-edit (merge
                          (dissoc options :items :callback)
                          (when open?
                            {:aria-controls menu-id})
                          {:aria-autocomplete :list
                           :value             value
                           :id                text-id
                           :auto-complete     "off"
                           :on-change         #(select (-> % .-target .-value))
                           :on-key-down       #(case (.-keyCode %)
                                                 KEY-ENTER
                                                 (let [selected-val (get-in items [current-idx :value])]
                                                   (select selected-val)
                                                   (.preventDefault %))

                                                 KEY-UP (inc-current (- (count items) 1))
                                                 KEY-DOWN (inc-current 1)
                                                 :default)
                           :on-focus          #(set-open! true)
                           ;; See `complete-parts` for explanation
                           :on-blur           #(common/start-delay (partial set-open! false) 200)}))]
     (when (and open?
                (->> items
                     (filter (fn [{:keys [text group]}]
                               (and (ss/blank? group)
                                    (not= (ss/trim value) text))))
                     (seq)))
       [:div.ac__menu
        (into
          [:ul.ac__items {:id   menu-id
                          :role :listbox}]
          (loop [[x & xs] items
                 index      0
                 last-group ""
                 li-items   []]
            (if x
              (let [{:keys [text value group]} x
                    group    (-> (or group "") ss/trim)
                    li-items (if (or (ss/blank? group) (= group last-group))
                               li-items
                               (conj li-items [:li.ac--group group]))
                    current? (= current-idx index)]
                (recur xs
                       (inc index)
                       group
                       (conj li-items
                             [:li
                              {:on-click       #(select value)
                               :on-mouse-enter #(set-index! index)
                               :class          (common/css-flags :ac--current current?
                                                                 :ac--grouped (not (ss/blank? group)))
                               :role           :option
                               :aria-selected  current?}
                              text])))
              li-items)))])]))


(defn- make-tag
  "A helper function for creating tag elements"
  [item selected* disabled? callback]
  [:li.tag {:on-click #(do (when-not disabled?
                             (set-selected selected*
                                           (vec (remove (partial = item) @selected*))
                                           callback))
                           (.stopPropagation %))}
   [:i.tag-remove.lupicon-remove]
   [:span.primary.tag-label (if (map? item) (:text item) (str item))]])

(defn- make-open-close-chevron
  "A helper function for making a chevron for autocomplete"
  [open?]
  [:i.primary.ac--chevron
   {:aria-hidden true
    :class       (common/css-flags :lupicon-chevron-small-down (not open?)
                                   :lupicon-chevron-small-up open?)}])

(defn- make-clear-button
  "A helper function for making a butto n that clears the autocomplete"
  [selected* callback tags?]
  (icon-button {:on-click   (fn [event] (set-selected selected* (if tags? [] "") callback)
                              (.stopPropagation event))
                :class      :secondary.no-border
                :icon       :lupicon-remove
                :icon-only? true
                :text-loc   :pate.clear-selection}))

(rum/defc tag
  "Text with icon-button"
  < {:key-fn #(common/unique-id "tag")}
  [text-options icon-button-options]
  (let [text (common/resolve-text text-options)]
    [:span.dsp--flex.flex--align-center.gap--r1
     text
     (when-not (:disabled? icon-button-options)
       (icon-button (merge {:icon       :lupicon-remove
                            :text-loc   :remove
                            :aria-label (str (common/loc :remove) " " text)
                            :icon-only? true
                            :class      :btn-small.tertiary}
                           icon-button-options)))]))

(declare autocomplete)

(rum/defc autocomplete-tags
  "Lists the selected tags/items and shows an autocomplete for adding to the selection.

   Parameters: initial-value, options

   Options [optional]:
     items          An item is a map with mandatory :text and :value
                    keys and optional :group key.
     [id]           Passed to underlying autocomplete
     [callback]     The function called on selection change.
                    Receives a set of values as argument.
     [disabled?]    Is the component disabled? (defaults to false)
     [required?]    Is the component required? (defaults to false)
     [placeholder]  Text visible if initial value is empty.
     [text-edit-options] Passed to underlying `autocomplete`.
"
  [default-value {:keys [items id callback disabled?
                         required? placeholder]
                  :as   options}]
  (let [[selected set-selected!] (rum/use-state #{})
        update-and-notify        (fn [selected']
                                   (when-not (= selected' selected)
                                     (callback selected')
                                     (set-selected! selected')))]
    (rum/use-effect! (fn []
                       (set-selected! (set default-value)))
                     [default-value])
    [:div
     (when-let [items (some->> items
                               (filter #(selected (:value %)))
                               (sort-by :text)
                               seq)]
       [:div.gap--v1.dsp--flex.flex--wrap
        (for [{:keys [value] :as item} items]
          (tag item {:on-click #(update-and-notify (disj selected value))}))])
     (when-let [items (seq (remove #(selected (:value %)) items))]
       (autocomplete nil
                     (assoc (select-keys options
                                         [:id :disabled? :required?
                                          :placeholder :text-edit-options])
                            :items       items
                            :callback    #(update-and-notify (conj selected %)))))]))

(rum/defcs autocomplete
  "Parameters: initial-value, options
     Initial value can be atom for two-way binding (see the mixin).
     Options [optional]:
       items               Either a list or a function.
                           The function argument is the filtering term.
                           An item is a map with mandatory :text and :value
                           keys and optional :group key.
       [callback]          The function called on component change.
       [clear?]            Is a 'clear' button shown? (defaults to false)
       [disabled?]         Is the component disabled? (defaults to false)
       [required?]         Is the component required? (defaults to false)
       [text
        text-loc
        aria-label]        Text for (non-visible) autocomplete aria-label.  Should simply
                           denote the autocomplete context (e.g., :phrase.phrase).
                           Defaults to :pate.autocomplete.
       [id]                Id for the whole autocomplete. In practise, the id is assigned to
                           the top-level button. Thus, the focus via label works.
       [placeholder]       Text visible if initial value is empty.
       [test-id]           The 'data-test-id' attribute for the top div.
       [text-edit-options] The options map that is passed to the search/filter
                           text-edit. The default aria-label is :pate.autocomplete.search"
  < (initial-value-mixin ::selected)
    rum/reactive
    (rum/local "" ::term) ;; Filtering term
    (rum/local 0 ::current) ;; Currently highlighted item
    (rum/local false ::open?)
  [{selected* ::selected
    term*     ::term
    open?*    ::open?
    :as       local-state} _ {:keys [clear? callback disabled? required? placeholder test-id
                                     aria-label text-edit-options]
                              :as   options}]
  (let [menu-id  (common/unique-id "autocomplete-menu")
        {:keys [text-edit menu-items
                items-fn]} (complete-parts local-state
                                           options
                                           (merge {:aria-label (common/loc :pate.autocomplete.search)}
                                                  text-edit-options)
                                           false)
        open?    (rum/react open?*)
        selected (rum/react selected*)]
    [:div.pate-autocomplete
     (cond-> {:role          :combobox
              :aria-expanded open?
              :aria-label    (or aria-label
                                 (common/resolve-text options)
                                 (common/loc :pate.autocomplete))
              :aria-haspopup :listbox
              :aria-owns     menu-id}
             test-id (common/add-test-id test-id))
     [:div.ac__tabby
      [:div.ac__row
       [:div.ac__cell
        [:button.autocomplete
         {:on-click (fn []
                      (common/reset-if-needed! term* "")
                      (swap! open?* not))
          :disabled disabled?
          :id       (:id options)}
         [:div.ac--selected
          {:class (common/css-flags :disabled disabled?
                                    :required (and required? (ss/blank? selected)))}
          [:span (if (ss/blank? selected)
                   (or placeholder (common/loc :choose))
                   (:text (util/find-first #(util/=as-kw (:value %) selected)
                                           (items-fn ""))))]
          (make-open-close-chevron open?)]]]
       (when (and clear? (not (ss/blank? selected)) (not disabled?))
         [:div.ac__cell (make-clear-button selected* callback false)])]]
     [:div.ac__menu (when-not open?
                      {:style {:display :none}})
      (when open?
        (rum/fragment [:div.ac__term text-edit]
                      menu-items))]]))

(rum/defc dropdown
  "Simple atomless version of dropdown:
  Takes selected as prop. Calls callback onChange.

   Parameters: selected options
   Options [optional]:

     items: list of :value, :text maps.

     [sort-by] Typically either :text or :value, but a function is accepted as well. If
     not given the items are listed in their given order.

     [choose?] If true, the select caption (empty selection) is included. (default true)

     [choose-loc] Empty selection text-loc (default :select-one)

     [callback] Selection callback
     [disabled?] See `common/resolve-disabled`
     [enabled?]  See `common/resolve-disabled`
     [required?] See `common/required-invalid-attributes`
     [invalid?]  See `common/required-invalid-attributes`

     [test-id]   Data-test-id attribute value

     [class] Additional classes. See `common/css`

   The rest of the options are passed to the underlying select."
  < rum/static
  [selected {sort-key :sort-by
             :keys    [items choose? callback required? test-id class]
             :as      options}]
  (let [choose? (-> choose? false? not)]
    [:select.lux
     (merge {:value     (or selected "")
             :class     (common/css class)
             :on-change (when callback #(callback (.. % -target -value)))
             :disabled  (common/resolve-disabled options)}
            (common/add-test-id {} test-id)
            (common/required-invalid-attributes options selected)
            (dissoc options
                    :items :choose? :choose-loc :callback
                    :disabled? :enabled? :required? :invalid?
                    :sort-by :test-id :class))
     (cond->> items
       sort-key (sort-by sort-key
                         (if (= sort-key :text)
                           js/util.localeComparator
                           identity))
       choose?  (cons {:text  (common/loc (:choose-loc options :selectone))
                       :value ""})
       true     (map (fn [{:keys [text value]}]
                       [:option {:key   value
                                 :value value} text])))]))

(def log (.-log js/console))

;; Prettyprints the contents of the given atom.
(rum/defc debug-atom < rum/reactive
  ([atom* title]
   (if ^boolean js/goog.DEBUG
     [:div.pprint
      [:div.title [:h4 title]]
      [:a {:on-click #(log @atom*)} "console"]
      [:div.code (with-out-str (pprint (rum/react atom*)))]]
     (do
       (js/console.error "Debug atom used outside debug mode")
       [:span])))
  ([atom*] (debug-atom atom* "debug")))

(rum/defc day-edit
  "Date picker component that consists of text field and calendar button. The calendar
  also opens with Down key.

  Properties:

  default-value  Current date. Can be nil/(Finnish) date string/Date/timestamp.
  options:       Some of the following [optional]:

    callback: Function that receives Date instance or nil. Note that bad dates are _never_
              passed.
    [string?]   If true, the callback function will instead return the inputted string instead of a Date instance
    [disabled?] See `common/resolve-disabled`
    [enabled?]  See `common/resolve-disabled`
    [invalid?]  If truthy, then the component is marked as invalid (aria-invalid and
                style).
    [class]     Class definitions for the input element. See `common/css`.
                The default is `day-picker-input`.
    [required?] Is the value required (default false)
    [id]        Id for the component text-input. Used with an external label."
  [default-value {:keys [callback test-id required? invalid? string?] :as options}]
  (let [[text set-text!]          (rum/use-state (some-> (datepicker/make-date default-value)
                                                         :date
                                                         datepicker/datestring))
        [error? set-error!]       (rum/use-state (:error (datepicker/make-date default-value)))
        [bad? set-bad!]           (rum/use-state  invalid?)
        [last-result set-result!] (rum/use-state text)
        [open? _set-open!_]       (rum/use-state false)
        focus*                    (rum/use-ref 0)
        input-ref*                (rum/use-ref nil)
        disabled?                 (common/resolve-disabled options)
        set-open!                 (fn [flag?]
                                    (when-not (= flag? open?)
                                      (_set-open!_ flag?)
                                      (when-not open?
                                        ;; https://github.com/gpbl/react-day-picker/issues/1236#issuecomment-864193445
                                        (common/start-delay #(.focus (js/document.querySelector ".DayPicker-Day:not(.DayPicker-Day--disabled):not(.DayPicker-Day--outside)"))
                                                            200))))
        toggle                    #(set-open! (not open?))
        focus-missed-check        (fn [last-count]
                                    (when (= last-count (rum/deref focus*))
                                      (set-open! false)))
        process-result            (fn [value]
                                    (let [{:keys [date error]} (datepicker/make-date value)
                                          datext               (datepicker/datestring date)]
                                      (set-error! error)
                                      (when (inst? value)
                                        (set-text! datext))
                                      (when-not (or error (= datext last-result))
                                        (if string?
                                          (callback datext)
                                          (callback date))
                                        (set-result! datext))))
        required?                 (and (not bad?) required? (ss/blank? text))
        info-id                   (common/unique-id "day-edit-info")]
    (rum/use-effect! (fn []
                       (set-text! (some-> (datepicker/make-date default-value)
                                          :date
                                          datepicker/datestring)))
                     [default-value])
    (rum/use-effect! (fn []
                       (set-bad! invalid?))
                     [invalid?])

    [:div.day-picker-container
     [:span {:style {:display :none}
             :id    info-id} (common/loc :day-picker.info)]
     [:div
      (-> {:value            text
           :id               (:id options)
           :invalid?         (or error? bad?)
           :aria-describedby info-id
           :ref              input-ref*
           :auto-complete    "off"
           :disabled         disabled?
           :required?        required?
           :style            {:border-right 0}
           :class            (common/css [(:class options :day-picker-input)
                                          :no-error-bg])
           :on-key-down      #(case (.-keyCode %)
                                KEY-DOWN (set-open! true)
                                KEY-ESC  (set-open! false)
                                nil)
           :on-blur          #(process-result text)
           :on-change        #(set-text! (.. % -target -value))}
          (common/add-test-id test-id)
          simple-text-edit)
      (icon-button {:icon       (if bad? :lupicon-circle-attention :lupicon-calendar)
                    :tab-index  -1
                    :disabled?  disabled?
                    :text-loc   :calendar
                    :class      [:day-picker-toggle
                                 (cond
                                   (or error? bad?) :negative
                                   disabled?        :primary
                                   required?        :secondary
                                   :else            :secondary.regular)]
                    :icon-only? true
                    :on-click   toggle})]
     (when open?
       (let [date        (:date (datepicker/make-date text))
             focus-input #(.focus (rum/deref input-ref*))]
         [:div.day-picker-popup
          (datepicker/day-picker {:on-focus      #(rum/set-ref! focus* (inc (rum/deref focus*)))
                                  :selected-days date
                                  :on-key-down   #(when (= (.-keyCode %) KEY-ESC)
                                                    (focus-input))
                                  :on-blur       (fn []
                                                   (let [focus (rum/deref focus*)]
                                                     (common/start-delay (partial focus-missed-check focus)
                                                                         200)))
                                  :on-day-click  (fn [day]
                                                   (set-open! false)
                                                   (focus-input)
                                                   (process-result day))})]))]))

(rum/defc click-link
  "Link (A) element with click handler and keyboard support. The link is focusable and can
  be triggered with Enter. The component is needed since the straightforward href='#'
  approach does not work with cljs.

  Options (text and text-loc are mutually exclusive) [optional]:
   text Link text.
   text-loc Localization key for text.
   click Function to be called when the link is clicked.
   [test-id] Test-id for the link element

 Other options are passed as-is to the link element."
  [{:keys [click test-id] :as options}]
  [:a (-> (dissoc options :text :text-loc :click :test-id)
          (merge {:tab-index   0 ;; Makes focusable
                  :on-click    click
                  :on-key-down #(when (= (.-keyCode %) KEY-ENTER)
                                  (click))})
          (common/add-test-id test-id))
   (common/resolve-text options)])

(rum/defc text-and-link
  "Renders text with included link
  Options (text and text-loc are mutually exclusive) [optional]:
  text Text where link is in brackets: 'Press [me] for details'
  text-loc Localization key for text.
  click Function to be called when the link is clicked
  [test-id] Test-id for the link element

  [disabled?] See `common/resolve-disabled`
  [enabled?]  See `common/resolve-disabled`"
  < rum/reactive
  [{:keys [click test-id] :as options}]
  (let [regex     #"\[(.*)\]"
        text      (common/resolve-text options)
        link      (last (re-find regex text))
        ;; Split results include the link
        [before after] (remove #(= link %) (ss/split text regex))
        disabled? (common/resolve-disabled-reactive options)]
    [:span {:class (common/css-flags :disabled disabled?)}
     before
     (common/add-test-id (if disabled?
                           link
                           [:a {:on-click click} link])
                         test-id)
     after]))

(rum/defc icon-button
  "Button with optional icon and waiting support
   Options (text and text-loc are mutually exclusive) [optional]

   [text or text-loc] See `common/resolve-text`.
   [title or title-loc] Localization key for button title

   [icon] Icon class for the button (e.g., :lupicon-save)

   [wait?] If true the button is disabled and the wait icon is
   shown (if the icon has been given). Can be either value or
   atom. Atom makes the most sense for the typical use cases.

   [disabled?] See `common/resolve-disabled`
   [enabled?]  See `common/resolve-disabled`

   [class] Class definitions that are processed with `common/css`.

   [test-id] Test id for the button.

   [icon-only?] If true, the button does not have text. However, the aria-label is still resolved.

   [right?] If true, the icon is on the right side of the button.

   Any other options are passed to the :button tag (e.g, :on-click). The only exception
   is :disabled, since it is overridden with :disabled?

   Aria-label resolution order: 1. aria-label 2. text(-loc), 3. title(-loc)."
  < rum/reactive
  [{:keys [icon wait? class test-id title title-loc aria-label icon-only? right?]
    :as   options}]
  (let [waiting? (rum/react (common/atomize wait?))
        text     (common/resolve-text options)
        title    (or (common/loc title-loc) title)
        class    (cond->> class
                   icon-only? (conj [:btn-icon-only]))
        span     (when (and text (not icon-only?))
                   [:span {:aria-hidden true} text])]
    [:button
     (-> (dissoc options
                 :text :text-loc :title-loc :icon :wait? :right?
                 :disabled? :enabled? :class :test-id :icon-only?)
         (assoc :disabled (or (common/resolve-disabled-reactive options)
                              waiting?)
                :class (common/css class))
         (util/assoc-when :title title
                          :aria-label (or aria-label text title))
         (common/add-test-id test-id))
     (when right? span)
     (when icon
       [:i {:aria-hidden true
            :class       (common/css (if waiting?
                                       [:icon-spin :lupicon-refresh]
                                       icon))}])
     (when-not right? span)]))

(rum/defc link-button
  "Link that is rendered as button.
   Options:
      url: Link url

   In addition, text, text-loc, test-id, enabled? and disabled? options are
   supported. All the other options are passed as is (excluding :disabled)."
  < rum/reactive
  [{:keys [url test-id] :as options}]
  (let [disabled? (common/resolve-disabled-reactive options)
        text      (common/resolve-text options)
        options   (-> options
                      (dissoc :url :test-id :disabled? :enabled?
                              :text :text-loc :disabled)
                      (common/add-test-id test-id))]
    [:a.btn.secondary
     (merge options
            (if disabled?
              {:class         :disabled
               :aria-disabled true}
              {:href   url
               :target :_blank}))
     text]))

(rum/defc toggle
  "Toggle component aka controlled checkbox.
   Parameters: value, options
   Options [optional]:

   [text or text-loc] See `common/resolve-text`
   callback           Toggle callback function, is called either true
                      or false in :on-change event. MUST update value
                      upstream as this component is controlled.
   [id]               id attribute for input
   [disabled?]        See common/resolve-disabled
   [enabled?]         See common/resolve-disabled
   [radio]            Radio group name. If given, the input type is changed to radio.
   [required?]        Must be checked (default false)
   [prefix]           Wrapper class prefix (default :pate-checkbox or :pate-radio)
   [class]            Extra classes for the wrapper. See `common/css`.
   [test-id]          Test id prefix for input and label.

   [aria-label or aria-label-loc] Aria-label attribute value. The default value is the
   same as text/text-loc."
  [value {:keys [callback prefix test-id radio] :as options}]
  (when (and ^boolean js/goog.DEBUG (instance? rum.cursor.Cursor value))
    (throw (js/Error (str "Do not give cursor as parameter, from:" options))))
  (let [[checked?
         set-checked!] (rum/use-state (boolean value))
        id             (:id options (common/unique-id "toggle"))
        prefix         (name (cond
                               prefix prefix
                               radio  :pate-radio
                               :else  :pate-checkbox))
        text           (common/resolve-text options)
        cb-fn          (fn []
                         (callback (not checked?))
                         (set-checked! (boolean (not checked?))))]
    (rum/use-effect! (fn []
                       (set-checked! (boolean value)))
                     [value])
    [:div
     {:class (common/css (str prefix "-wrapper")
                         (:class options)
                         (when-not text :wrapper--no-border.wrapper--no-label))}
     [:input (merge {:id         id
                     :type       "checkbox"
                     :disabled   (common/resolve-disabled options)
                     :checked    checked?
                     :aria-label (common/resolve-aria-label options)
                     :on-change  cb-fn}
                    (when radio
                      {:name radio :type "radio"})
                    (common/add-test-id {} test-id :input)
                    (common/required-invalid-attributes options (or checked? "")))]
     [:label
      (common/add-test-id {:class       (str prefix "-label")
                           :for         id
                           :aria-hidden true}
                          test-id :label)
      text]]))

(rum/defc tri-state-button
  "Button/toggle hybrid that represent three different states (nil, true or false). Nil is
  the default."
  [state {:keys [class class-nil class-true class-false callback]
                  :as   options}]
  (let [[value set-value!] (rum/use-state state)
        class-nil          (or class-nil :tri-state-nil)
        class-true         (or class-true :tri-state-true)
        class-false        (or class-false :tri-state-false)]
    (rum/use-effect! (fn []
                       (set-value! state))
                     [state])
    [:button
     {:role         "checkbox"
      :aria-checked (case value
                      nil   false
                      true  true
                      false "mixed")
      :disabled     (common/resolve-disabled options)
      :aria-label   (common/resolve-aria-label options)
      :class        (concat (common/css class)
                            (common/css-flags class-nil (nil? value)
                                              class-true (true? value)
                                              class-false (false? value)))
      :on-click     (fn []
                      (let [new-value (case value
                                        nil true
                                        true  false
                                        false nil)]
                        (set-value! new-value)
                        (when callback
                          (callback new-value))))}
     (common/resolve-text options)]))

(rum/defc toggle-group
  "Multiselect toggle group. The value is a collection of checked values.
   Parameters: initial-value options

   Options [optional]:

   items        List of text/text-loc and value maps.
                The toggles are laid out in the items order.
   callback     Callback function which is called with the selected value(s).
   [disabled?]  See common/resolve-disabled
   [enabled?]   See common/resolve-disabled
   [radio?]     If true, the group is a radio group (default false).
   [pseudo-radio?]  If true, the group is a pseudo-radio group: it is rendered as a
                    checkbox group, but only maximum of one toggle can be checked.
   [prefix]     Wrapper class prefix (default :pate-checkbox)
   [class]      Extra classes for the toggle.
   [test-id]    Test id prefix. The actual prefix for an individual
                toggle consists of the prefix and value. "
  [selected-opts {:keys [items callback test-id radio? pseudo-radio?] :as options}]
  (let [[selected set-selected!] (rum/use-state (set selected-opts))
        radio                    (when radio?
                                   (common/unique-id "radio-group"))]
    (rum/use-effect! (fn []
                       (set-selected! (set selected-opts)))
                     [selected-opts])
    [:<>
     (for [{:keys [value] :as item} items
           :let                     [uniq-id (common/unique-id "toggle")]]
       (rum/with-key
         (toggle (contains? selected value)
                 (merge (select-keys item [:text :text-loc])
                        (select-keys options [:disabled? :enabled? :prefix :class])
                        {:id    uniq-id
                         :radio radio}
                        (when test-id
                          {:test-id (util/kw-path test-id :- value)})
                        {:callback (fn [flag]
                                     (let [selected' (cond
                                                       radio?        #{value}
                                                       pseudo-radio? (if flag #{value} #{})
                                                       :else         ((if flag conj disj)
                                                                      selected
                                                                      value))]
                                       (set-selected! selected')
                                       (callback (cond-> selected'
                                                   (or radio? pseudo-radio?) first))))}))
         uniq-id))]))

(rum/defc pen-input
  "Editable text via pen button. Options [optional]:

  value:       Initial input value
  callback:    Callback to be called with new value.
  [disabled?]: Is the editing disabled.
  [editing?]:  If component should be initialized in editing? state (boolean, default false)
  [test-id]:   Prefix for test-ids. If given the test-ids are:
               prefix-input, prefix-text, prefix-edit and prefix-save,
               where prefix is the given test-id.
  [on-click]   callback when edit pen is clicked
  [on-cancel]   Callback when editing is canceled."
  < rum/static
  [{:keys [value callback disabled? test-id editing? on-click on-cancel] :as options}]
  (let [[editing? set-editing!] (rum/use-state (or editing? false))
        [name set-name!]        (rum/use-state "")
        save-fn                 (fn []
                                  (set-editing! false)
                                  (callback name))
        cancel-fn               (fn []
                                  (set-editing! false)
                                  (set-name! value)
                                  (when on-cancel
                                    (on-cancel)))
        tid                     (fn [target k]
                                  (cond-> target
                                    test-id (common/add-test-id test-id k)))]
    (rum/use-effect! #(set-name! value) [value])
    (if editing?
      [:span.pen-input--edit.ws--nowrap
       (simple-text-edit
         (tid {:type       "text"
               :value      name
               :id         (:id options)
               :class      :row-text
               :auto-focus true
               :on-change  #(set-name! (.. % -target -value))
               :on-key-up  #(let [key (.-keyCode %)]
                              (cond
                                ;; Save on Enter
                                (and (= key KEY-ENTER)
                                     (not (ss/blank? name)))
                                (save-fn)

                                ;; Cancel on Esc
                                (= key KEY-ESC)
                                (cancel-fn)))}
              :input))
       (icon-button (tid {:class      :primary.no-radius
                          :text-loc   :save
                          :icon-only? true
                          :disabled?  (ss/blank? name)
                          :icon       :lupicon-save
                          :on-click   save-fn}
                         :save))
       (icon-button (tid {:class      :tertiary
                          :icon       :lupicon-remove
                          :text-loc   :cancel
                          :icon-only? true
                          :on-click   cancel-fn}
                         :cancel))]
      [:span.pen-input--view
       (tid {} :text)
       value
       (when-not disabled?
         (icon-button (tid {:class      :tertiary
                            :text-loc   :edit
                            :icon-only? true
                            :icon       :lupicon-pen
                            :on-click   (fn []
                                          (let [new-state (not editing?)]
                                            (set-editing! new-state)
                                            (when on-click
                                              (on-click new-state))))}
                           :edit)))])))


(defn add-key-attrs
  "Adds unique key attribute to every element. Note: the attribute is
  added only if the element has an attribute map (true for
  markup/markup->tags result)."
  [form prefix]
  (walk/prewalk (fn [x]
                  (cond-> x
                          (map? x) (assoc :key (common/unique-id prefix))))
                form))


(rum/defc markup-span
  "Displays formatted markup. Span class is :markdown and (optional)
  other given classes."
  < rum/static
  [markup & class]
  [:span.markup
   (merge
     {:key (common/unique-id "markup-")}
     (when class
       {:class (map name class)}))
   (some-> (markup/markup->tags markup)
           not-empty
           (add-key-attrs "markup-"))])

(rum/defc link-label
  "Component that alternates between link and label representation
   based on the given flag atom. The link toggles the
   atom. Options [optional]:

   text or text-loc  See common/resolve-text
   [required?]  Whether label/link is marked as required (default false)
   flag*        Flag atom
   [negate?]    By default, the label is shown when flag is true.
   Negate? negates that."
  < rum/reactive
  [{:keys [required? flag* negate?] :as options}]
  (let [fun    (if negate? not identity)
        label? (-> flag* rum/react fun)
        class  (common/css-flags :required required?)
        text   (common/resolve-text options)]
    (if label?
      [:label {:class class} text]
      (click-link {:click #(swap! flag* not)
                   :text  text}))))

(rum/defc tabbar
  "Tabbar representation (only the bar). Options:

  selected Value that holds the selected :id. The default id is the
            first tab id if none is given.

  tabs Sequence of :id, :text-loc (or :text, see
        `common/resolve-text`) and optional :test-id maps.

  on-click Provide an on-click handler. It is invoked with the tab id when tab is clicked."
  < rum/static
  [{:keys [selected tabs on-click]}]
  (let [selected (or selected (-> tabs first :id))]
    [:div.pate-tabbar
     (let [divider [:div.pate-tab__divider {}]
           buttons (for [{:keys [id test-id]
                          :as   tab} tabs
                         :let [text (common/resolve-text tab)
                               attr (common/add-test-id {} test-id)]]
                     (if (= id selected)
                       [:div.pate-tab.pate-tab--active
                        attr text]
                       [:div.pate-tab
                        attr
                        (click-link {:click #(on-click id)
                                     :text  text})]))]
       (map #(assoc-in % [1 :key] (common/unique-id "tabbar-"))
            (concat (interpose divider buttons)
                    [[:div.pate-tab__space]])))]))

(rum/defcs markup-edit
  "Convenience component for simple markup editors."
  < (rum/local ::edit ::tab)
    {:will-mount (fn [state] (assoc state ::reset-tab (fn [id] (reset! (::tab state) id))))}
  [{tab* ::tab switch-tab ::reset-tab} markup {:keys [test-id] :as options}]
  [:div (common/add-test-id {} test-id)
   (tabbar {:selected @tab*
            :on-click switch-tab
            :tabs     [{:id       ::edit
                        :text-loc :pate.edit-tab
                        :test-id  (common/test-id test-id :edit-tab)}
                       {:id       ::preview
                        :text-loc :pdf.preview
                        :test-id  (common/test-id test-id :preview-tab)}]})
   (if (= @tab* ::edit)
     [:div.phrase-edit
      (textarea-edit markup (update options :test-id common/test-id :edit))]
     [:div.phrase-edit (common/add-test-id {} test-id :preview)
      (markup-span markup :phrase-preview)])])

(rum/defcs help-toggle
  "Clickable help icon that expands into a help text.
   Options:
   `text` or `text-loc` Help text.
   `html?` (optional, default false) If true, the help text is not escaped, but shown as a raw HTML.

   Note: A title (e.g., H2) element laid out next to the help, must have `help-adjacent-title` class."
  < (rum/local false ::open?)
  [{open?* ::open?} {:keys [html?] :as options}]
  (let [text      (common/resolve-text options)
        text-opts {:key   "help-toggle-text"
                   :class (common/css-flags :help-text--show @open?*)}]
    (list [:div.stacked.help-toggle {:key "help-toggle-stacker"}
           [:span.lupicon-circle-question {:class    (common/css-flags :expanded @open?*)
                                           :on-click #(swap! open?* not)}]
           [:div {:class (common/css-flags :help-arrow @open?*)}]]
          (if html?
            [:div.help-text (assoc text-opts :dangerouslySetInnerHTML {:__html text})]
            [:div.help-text text-opts text])
          [:div.help-reset (assoc common/nbsp :key "help-toggle-reset")])))

(defn debug-db
  "Prints the current db to console."
  []
  (when ^boolean js/goog.DEBUG
    [icon-button {:class :danger.gap--v2
                  :text  "Log db"
                  :icon  :lupicon-hammer
                  :on-click #(>evt [:debug/log])}]))
