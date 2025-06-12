(ns lupapalvelu.ui.pate.components
  "More or less Pate-specific user interface components. See
  layout.cljs for documentation on the component conventions."
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [goog.string :as goos]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.datepicker :as datepicker]
            [lupapalvelu.ui.components.day-edit :as day-edit]
            [lupapalvelu.ui.components.reselect :as reselect]
            [lupapalvelu.ui.pate.phrases :as phrases]
            [rum.core :as rum]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- reset-state-and-update-meta
  "Updates state according to value."
  [{:keys [state path] :as options} value]
  (reset! (path/state path state) value)
  (path/meta-updated options))


(defn show-label? [{label? :label?} wrap-label?]
  (and wrap-label? (not (false? label?))))

(defn with-empty-label [{:keys [schema wrap-label?]} component]
  (if (show-label? schema wrap-label?)
    [:div.col--vertical
     (common/empty-label)
     component]
    component))

(defn with-label [{:keys [schema path wrap-label?] :as options} component]
  (if (show-label? schema wrap-label?)
    [:div.col--vertical
     [:label.pate-label {:for   (path/id path)
                         :class (common/css-flags :required
                                                  (path/required? options))}
      (path/loc options)]
     component]
    component))

(defn test-id [{:keys [path schema]} & extras]
  (common/test-id (get schema :test-id path) extras))

(defn aria-label [{:keys [schema]}]
  (some-> schema :aria-label common/loc))

(defn pate-attr
  "Fills attribute map with :id, :key, :class, :test-id and :aria-label. Also merges
  extras if given."
  [{:keys [path] :as options} & extras]
  (let [id (path/id path)]
    (apply merge
           (common/add-test-id
             {:key        id
              :id         id
              :class      (path/css options)
              :aria-label (aria-label options)}
             (test-id options))
           extras)))

(rum/defc pate-multi-select < rum/reactive
  [{:keys [state path schema] :as options}  & [wrap-label?]]
  [:div.pate-multi-select
   (when (:vertical? schema) {:class "vertical-list"})
   (when (show-label? schema wrap-label?)
     [:h4.pate-label
      {:class (common/css-flags :required (path/required? options))}
      (path/loc options)])
   (let [state (path/state path state)
         items (cond->> (map (fn [item]
                               (if (:value item)
                                 item
                                 (let [item (keyword item)]
                                   {:value item
                                    :text  (if-let [item-loc (:item-loc-prefix schema)]
                                             (path/loc [item-loc item])
                                             (path/loc options item))})))
                             (:items schema))
                 (:sort? schema) (sort-by :text js/util.localeComparator))]
     (for [{:keys [value text]} items
           :let                 [;;item-id (common/unique-id "multi")
                                 checked (util/includes-as-kw? (set (rum/react state)) value)]]
       (rum/with-key
         (components/toggle
           checked
           {:id        (str "pate-multi-select-" value)
            :disabled? (path/disabled? options)
            :callback  (fn [selected]
                         (swap! state
                                (fn [xs]
                                  ;; Sanitation on the client side.
                                  (util/intersection-as-kw (if selected
                                                             (cons value xs)
                                                             (remove #(util/=as-kw value %) xs))
                                                           (map :value items))))
                         (path/meta-updated options))
            :text      text
            :test-id   value})
         value)))])

(defn resolve-reference-list
  "List of :value, :text maps"
  [{:keys [schema references] :as options}]
  (let [{:keys [item-key item-text-key _item-text-fn item-loc-prefix
                term]}    schema
        {:keys     [extra-path match-key]
         term-path :path} term
        extra-path        (path/pathify extra-path)
        term-path         (path/pathify term-path)
        match-key         (or match-key item-key)
        target            (path/value (path/pathify (:path schema))
                                      references )
        items             (->> (cond->> target
                                 (map? target) (map (fn [[k v]]
                                                      (assoc v :MAP-KEY k))))
                               ;; TODO: :ignored in schema
                               (remove :deleted) ; Safe for non-maps
                               (map (fn [x]
                                      (let [v (if item-key (item-key x) x)]
                                        {:value v
                                         :text  (cond
                                                  (and match-key term) (-> (if term-path
                                                                             (util/find-by-key match-key
                                                                                               v
                                                                                               (path/value term-path
                                                                                                           references))
                                                                             x)
                                                                           (get-in (cond->> [(keyword (common/get-current-language))]
                                                                                     extra-path (concat extra-path))))
                                                  item-text-key        (get x item-text-key)
                                                  item-loc-prefix      (path/loc [item-loc-prefix v])
                                                  :else                (path/loc options v))})))
                               distinct)]
    (cond->> items
      (:sort? schema) (sort-by :text js/util.localeComparator))))

(rum/defc select-reference-list < rum/reactive [{:keys [path state] :as options} & [wrap-label?]]
  (with-label
    (assoc options :wrap-label? wrap-label?)
    (components/dropdown
      (path/react path state)
      (pate-attr options
                 {:items     (resolve-reference-list options)
                  :sort-by   :text
                  :callback  (partial reset-state-and-update-meta options)
                  :disabled? (path/disabled? options)
                  :required? (path/required? options)}))))

(rum/defc multi-select-reference-list < rum/reactive
  [options & [wrap-label?]]
  (pate-multi-select (assoc-in options [:schema :items] (resolve-reference-list options))
                     wrap-label?))

(rum/defc list-reference-list < rum/reactive
  [{:keys [schema] :as options} & [wrap-label?]]
  [:div.pate-unordered-list
   (when (show-label? schema wrap-label?)
     [:h4.pate-label
      {:class (common/css-flags :required (path/required? options))}
      (path/loc options)])
   [:ul
    (->> (resolve-reference-list options)
         (map (fn [{text :text}]
                [:li {:key (common/unique-id "li")} text])))]])

(rum/defc last-saved < rum/reactive
  [{info* :info}]
  [:span.saved-info
   (when-let [ts (path/react [:modified] info*)]
     (common/loc :pate.last-saved (js/util.finnishDateAndTime ts)))])

(rum/defc required-fields-note < rum/reactive
  ([{info* :info test-id :test-id} note]
   (when (false? (path/react [:filled?] info*))
     [:div.pate-required-fields-note
      (common/add-test-id {} test-id)
      note]))
  ([options]
   (required-fields-note options (common/loc :pate.required-fields))))

(rum/defc required-fields-note-raw
  "Non-reacting counterpart of required-fields-note component"
  ([{info :info test-id :test-id} note]
   (when (false? (:filled? info))
     [:div.pate-required-fields-note
      (common/add-test-id {} test-id)
      note]))
  ([options]
   (required-fields-note-raw options (common/loc :pate.required-fields))))

(rum/defcs phrase-selection < rum/reactive
  (rum/local nil ::category)
  (rum/local nil ::selected)
  [{selected* ::selected
    category* ::category} textarea-ref disabled? update-text {:keys [path state] :as options}]
  [:div.row
   [:div.col-3.col--full
    [:div.col--vertical
     [:label (common/loc :phrase.add)]
     (phrases/phrase-category-select (or @category* (name (get-in options [:schema :category])))
                                     #(reset! category* %)
                                     {:disabled? disabled?
                                      :test-id   (test-id options :category)})]]
   [:div.col-5
    [:div.col--vertical
     (common/empty-label)
     (reselect/reselect
       (deref selected*)
       {:items      (phrases/phrase-list-for-autocomplete (or @category*
                                                              (get-in options [:schema :category])))
        :callback   #(let [text-node (.-current textarea-ref)
                           sel-start (.-selectionStart text-node)
                           sel-end   (.-selectionEnd text-node)
                           old-text  (or (path/value path state) "")]
                       (update-text (s/triml (s/join (concat (take sel-start old-text)
                                                             (str "\n" % "\n")
                                                             (drop sel-end old-text))))))
        :disabled?  disabled?
        :clear?     true
        :test-id    (test-id options :autocomplete)
        :choose-loc :phrase.phrase})]]
   [:div.col-4.col--right
    [:div.col--vertical
     (common/empty-label)
     [:button.primary.outline
      (common/add-test-id {:on-click (fn []
                                       (update-text "")
                                       (reset! selected* nil))
                           :disabled disabled?}
                          (test-id options :clear))
      (common/loc :pate.clear)]]]])

(rum/defcs pate-phrase-text <
  rum/reactive
  (rum/local ::edit ::tab)
  (path/debounced-meta-updater ::meta-updater 1000)
  {:will-mount
   (fn [{update-meta ::meta-updater :as rum-state}]
     (let [{:keys [state path] :as opts} (first (:rum/args rum-state))
           state-cursor                  (path/state path state)]
       (assoc rum-state
              ::update-text (fn [text]
                              (reset! state-cursor text)
                              (update-meta opts))
              ::reset-tab    (fn [id] (reset! (::tab rum-state) id))
              ::state-cursor state-cursor
              ::textarea-ref (rum/create-ref))))}
  [{tab*         ::tab
    update-text  ::update-text
    the-cursor*  ::state-cursor
    textarea-ref ::textarea-ref
    switch-tab   ::reset-tab}
   {:keys [state path schema] :as options} & [wrap-label?]]
  (let [disabled? (or (path/disabled? options)
                      (= @tab* ::preview))
        required? (path/required? options)]
    [:div.pate-grid-12
     (when (show-label? schema wrap-label?)
       [:h4.pate-label
        {:class (common/css-flags :required required?)}
        (path/loc options)])
     (when (phrases/non-empty-category?)
       (phrase-selection textarea-ref disabled? update-text options))
     [:div.row
      [:div.col-12.col--full
       (components/tabbar {:selected @tab*
                           :on-click switch-tab
                           :tabs     [{:id       ::edit
                                       :text-loc :pate.edit-tab
                                       :test-id  (test-id options :edit-tab)}
                                      {:id       ::preview
                                       :text-loc :pdf.preview
                                       :test-id  (test-id options :preview-tab)}]})
       (if (= @tab* ::edit)
         [:div.phrase-edit
          (components/simple-textarea-edit
            {:ref        textarea-ref
             :on-change  #(update-text (-> % .-target .-value))
             :value      (or (rum/react the-cursor*) "")
             :disabled   disabled?
             :required?  required?
             :aria-label (path/loc options)
             :test-id    (test-id options :edit)})]
         [:div.phrase-edit
          (components/markup-span (path/value path state)
                                  :phrase-preview)])]]]))

(rum/defc pate-link < rum/reactive
  [{:keys [schema] :as options}]
  (components/text-and-link {:text-loc (:text-loc schema)
                             :click    #((path/meta-value options (:click schema))
                                         options)
                             :test-id  (test-id options)}))

(rum/defc pate-button < rum/reactive
  [{:keys [schema] :as options} & [wrap-label?]]
  (let [{:keys [icon text? click add remove
                move]} schema
        text?          (-> text? false? not)
        button         (when (path/visible? options)
                         (components/icon-button
                           {:disabled? (path/disabled? options)
                            :class     (path/css options)
                            :on-click  (fn [_]
                                         (cond
                                           click
                                           (path/meta-value options click)

                                           (or add remove move)
                                           (path/meta-updated options true)))
                            :icon      icon
                            :text      (when text? (path/loc options))
                            :test-id   (test-id options)}))]
    (if (show-label? schema wrap-label?)
      [:div.col--vertical
       (common/empty-label :pate-label)
       button]
      button)))

(rum/defc pate-toggle < rum/reactive
  [{:keys [schema state path] :as options} & [wrap-label?]]
  (with-empty-label
    (assoc options :wrap-label? wrap-label?)
    (components/toggle
      (let [val (path/react path state)]
        (if (:inverse? schema)
          (not val)
          val))
      {:callback  #(do
                     (reset! (path/state path state) (if (:inverse? schema) (not %) %))
                     (path/meta-updated options))
       :disabled? (path/disabled? options)
       :required? (path/required? options)
       :id        (str "pate-toggle-" (s/join "-" (map name path)))
       :text      (if-let [dict (:text-dict schema)]
                    (path/react (butlast path)
                                state
                                (common/prefix-lang dict))
                    (path/loc options))
       :prefix    (:prefix schema)
       :test-id   (test-id options)})))

(defn pate-unit [unit]
  (case unit
    :days    (path/loc :pate-date-delta unit)
    :years   (path/loc :pate-date-delta unit)
    :ha      (path/loc :unit.hehtaaria)
    :cm      [:span "cm"]
    :m       [:span "m"]
    :m2      [:span "m" [:sup 2]]
    :m3      [:span "m" [:sup 3]]
    :kpl     (path/loc :unit.kpl)
    :section "§"
    :eur     "€"
    nil))

(defn sandwich [{:keys [before after class]} component]
  (if (or before after)
    (->> [:div.pate-sandwich
          {:class class}
          (when before
            [:span.sandwich--before
             (pate-unit before)])
          component
          (when after
            [:span.sandwich--after (pate-unit after)])])
    component))

(defn- sort-by-schema
  [{sort-key :sort-by} items]
  (cond->> items
    sort-key (sort-by sort-key
                      (if (= sort-key :text)
                        js/util.localeComparator
                        identity))))

(rum/defcs pate-text <
  rum/reactive
  (path/debounced-meta-updater ::meta-updater 1000)
  [{update-meta ::meta-updater}
   {:keys [state path] {:keys [items prefer-dropdown? lines] :as schema} :schema :as options} & [wrap-label?]]
  (let [attr-fn (partial pate-attr
                         options
                         {:callback    (fn [value]
                                         (reset! (path/state path state) value)
                                         (update-meta options))
                          :placeholder (some-> schema :placeholder path/loc)
                          :required?   (path/required? options)
                          :invalid?    (path/error? options)
                          :disabled    (path/disabled? options)})
        lang    (keyword (common/get-current-language))
        items   (path/items-from-path items options lang)
        value   (path/react path state)]
    (with-label
      (assoc options :wrap-label? wrap-label?)
      (sandwich schema
                (if items
                  (if (and prefer-dropdown?
                           (or (nil? value)
                               (-> (map :value items) set (contains? value))))
                    (components/select (or value "")
                                       (cond->> (map #(vector (:value %) (:text %)) items)
                                         (ss/blank? value) (cons ["" (path/loc :selectone)]))
                                       (-> (attr-fn)
                                           (dissoc :invalid?)
                                           (set/rename-keys {:required? :required})))
                    (components/combobox value
                                         (attr-fn {:raw-items? (not-every? string? items)
                                                   :items      (->> items
                                                                    (map #(if (string? %)
                                                                            (hash-map :text %)
                                                                            %))
                                                                    (sort-by-schema {:sort-by :text}))})))
                  (components/text-edit value
                                        (attr-fn (if lines
                                                   {:element :textarea
                                                    :class   [:pate-textarea]
                                                    :lines   lines}
                                                   {:type (:type schema)}))))))))

(defn pate-date-delta
  [{:keys [schema] :as options} & [wrap-label?]]
  (pate-text (-> options
                 (assoc-in [:schema :after] (:unit schema))
                 (assoc-in [:schema :type] :number)
                 (assoc-in [:schema :css] (conj (flatten [(:css schema)]) :date-delta)))
             wrap-label?))

(rum/defc pate-date < rum/reactive
  "While Pate stores dates as timestamps (ms from epoch), the dates are passed to backend
  as Finnish date strings in order to avoid any discrepancies regarding the time."
  [{:keys [path state] :as options} & [wrap-label?]]
  (with-label
    (assoc options :wrap-label? wrap-label?)
    (day-edit/day-edit
      (path/react path state)
      (dissoc (pate-attr options
                         {:callback  #(reset-state-and-update-meta options
                                                                   (datepicker/datestring %))
                          :required? (path/required? options)
                          :invalid?  (path/error? options)
                          :disabled? (path/disabled? options)})
              :class))))

(defn pate-select-item-text [options item]
  (path/loc (if-let [item-loc (get-in options [:schema :item-loc-prefix])]
              (assoc-in options
                        [:schema :loc-prefix]
                        item-loc)
              options)
            item))

(rum/defc pate-select < rum/reactive
  [{:keys [path state schema] :as options} & [wrap-label?]]
  (let [attr-fn      (partial pate-attr
                              options
                              {:callback  (partial reset-state-and-update-meta options)
                               :disabled? (path/disabled? options)
                               :items     (->> (:items schema)
                                               (map (fn [item]
                                                      {:value item
                                                       :text  (pate-select-item-text options
                                                                                     item)}))
                                               (sort-by-schema schema))
                               :required? (path/required? options)
                               ;; :invalid?  (path/error? options)
                               :test-id   (test-id options)})
        value        (path/react path state)
        allow-empty? (-> schema :allow-empty false? not)]
    (with-label
      (assoc options :wrap-label? wrap-label?)
      (if (= (:type schema) :autocomplete)
        (reselect/reselect
          value
          (attr-fn {:clear? allow-empty?}))
        (components/dropdown
          value
          (attr-fn {:choose? allow-empty?}))))))
