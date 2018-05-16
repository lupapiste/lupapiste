(ns lupapalvelu.ui.pate.components
  "More or less Pate-specific user interface components. See
  layout.cljs for documentation on the component conventions."
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.path :as path]
            [lupapalvelu.ui.pate.phrases :as phrases]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defn- change-state
  "Updates state according to value."
  [{:keys [state path] :as options} value]
  (when (common/reset-if-needed! (path/state path state)
                                 value)
    (path/meta-updated options)))

(defn- state-change-callback
  [{:keys [state path] :as options}]
  (partial change-state options))

(defn show-label? [{label? :label?} wrap-label?]
  (and wrap-label? (not (false? label?))))

(defn label-wrap
  [{:keys [schema path] :as options} {:keys [component empty-label?]}]
  [:div.col--vertical
   (if empty-label?
     (common/empty-label)
     [:label.pate-label {:for (path/id path)
                         :class (common/css-flags :required
                                                  (path/required? options))}
      (path/loc options)])
   component])

(defn label-wrap-if-needed
  [{:keys [schema] :as options} {:keys [component wrap-label? empty-label?]
                                 :as extra}]
  (if (show-label? schema wrap-label?)
    (label-wrap options extra)
    component))

(rum/defc pate-multi-select < rum/reactive
  [{:keys [state path schema] :as options}  & [wrap-label?]]
  [:div.pate-multi-select
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
                 (not (-> schema :sort? false? )) (sort-by :text))]

     (for [{:keys [value text]} items
           :let                 [item-id (common/unique-id "multi")
                                 checked (util/includes-as-kw? (set (rum/react state)) value)]]
       [:div.pate-checkbox-wrapper
        {:key item-id}
        [:input {:type    "checkbox"
                 :id      item-id
                 :disabled (path/disabled? options)
                 :checked checked}]
        [:label.pate-checkbox-label
         {:for      item-id
          :on-click (fn [_]
                      (swap! state
                             (fn [xs]
                               ;; Sanitation on the client side.
                               (util/intersection-as-kw (if checked
                                                          (remove #(util/=as-kw value %) xs)
                                                          (cons value xs))
                                                        (map :value items))))
                      (path/meta-updated options))}
         text]]))])

(defn- resolve-reference-list
  "List of :value, :text maps"
  [{:keys [path schema references] :as options}]
  (let [{:keys [item-key item-loc-prefix term]}        schema
        {:keys [extra-path match-key] term-path :path} term
        extra-path                                     (path/pathify extra-path)
        term-path                                      (path/pathify term-path)
        match-key                                      (or match-key item-key)
        target                                         (path/value (path/pathify (:path schema))
                                                                   references )]
    (->> (cond->> target
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
                            item-loc-prefix           (path/loc [item-loc-prefix v])
                            :else                     (path/loc options v))})))
         distinct
         (sort-by :text  (if (:sort? schema)
                           js/util.localeComparator
                           identity)))))

(rum/defc select-reference-list < rum/reactive
  [{:keys [schema references path state] :as options} & [wrap-label?]]
  (label-wrap-if-needed
   options
   {:component   (components/dropdown
                  (path/value path state)
                  {:items     (resolve-reference-list options)
                   :sort-by   :text
                   :callback  (state-change-callback options)
                   :disabled? (path/disabled? options)
                   :required? (path/required? options)})
    :wrap-label? wrap-label?}))

(rum/defc multi-select-reference-list < rum/reactive
  [{:keys [schema] :as options} & [wrap-label?]]
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
                [:li {:key (common/unique-id "li")}text])
              ))]])

(rum/defc last-saved < rum/reactive
  [{info* :info}]
  [:span.saved-info
   (when-let [ts (path/react [:modified] info*)]
     (common/loc :pate.last-saved (js/util.finnishDateAndTime ts)))])

(rum/defc required-fields-note < rum/reactive
  ([{info* :info} note]
   (when (false? (path/react [:filled?] info*))
     [:div.pate-required-fields-note
      note]))
  ([options]
   (required-fields-note options (common/loc :pate.required-fields))))

(rum/defcs pate-phrase-text < rum/reactive
  (rum/local nil ::category)
  (rum/local "" ::selected)
  (rum/local "" ::replaced)
  (rum/local ::edit ::tab)
  [{category* ::category
    selected* ::selected
    replaced* ::replaced
    tab*      ::tab :as local-state}
   {:keys [state path schema] :as options} & [wrap-label?]]
  (letfn [(set-category [category]
            (when (util/not=as-kw category @category*)
              (reset! category* (name (or category "")))
              (reset! selected* "")))
          (update-text [text]
            (reset! (path/state path state) text)
            (path/meta-updated options))]
    (when-not @category*
      (set-category (:category schema)))
    (let [ref-id    (common/unique-id "-ref")
          tab       (rum/react tab*)
          disabled? (or (path/disabled? options)
                        (= tab ::preview))
          required? (path/required? options)]
      [:div.pate-grid-12
       (when (show-label? schema wrap-label?)
         [:h4.pate-label
          {:class (common/css-flags :required required?)}
          (path/loc options)])
       (when (seq (phrases/non-empty-categories))
         [:div.row
          [:div.col-3.col--full
           [:div.col--vertical
            [:label (common/loc :phrase.add)]
            (phrases/phrase-category-select @category*
                                            set-category
                                            {:disabled? disabled?})]]
          [:div.col-5
           [:div.col--vertical
            (common/empty-label)
            (components/autocomplete
             selected*
             {:items     (phrases/phrase-list-for-autocomplete (rum/react category*))
              :callback  #(let [text-node (.-firstChild (rum/ref local-state ref-id))
                                sel-start (.-selectionStart text-node)
                                sel-end   (.-selectionEnd text-node)
                                old-text  (or (path/value path state) "")]
                            (reset! replaced* (subs old-text sel-start sel-end))
                            (update-text (s/triml (s/join (concat (take sel-start old-text)
                                                                  (str "\n" % "\n")
                                                                  (drop sel-end old-text))))))
              :disabled? disabled?
              :clear?    true})]]
          [:div.col-4.col--right
           [:div.col--vertical
            (common/empty-label)
            [:div.inner-margins
             [:button.primary.outline
              {:on-click (fn []
                           (update-text "")
                           (reset! selected* ""))
               :disabled disabled?}
              (common/loc :pate.clear)]
             [:button.primary.outline
              {:disabled (let [phrase (rum/react selected*)]
                           (or disabled?
                               (s/blank? phrase)
                               (not (re-find (re-pattern (goog.string/regExpEscape phrase))
                                             (or (path/react path state) "")))))
               :on-click (fn []
                           (update-text (s/replace-first (path/value path state)
                                                         @selected*
                                                         @replaced*))
                           (reset! selected* ""))}
              (common/loc :phrase.undo)]]]]])
       [:div.row
        [:div.col-12.col--full
         (components/tabbar {:selected* tab*
                             :tabs     [{:id       ::edit
                                         :text-loc :pate.edit-tab}
                                        {:id       ::preview
                                         :text-loc :pdf.preview}]})
         (if (= tab ::edit)
           [:div.phrase-edit
            {:ref ref-id}
            (components/textarea-edit (path/value path state)
                                      {:callback  update-text
                                       :disabled  disabled?
                                       :required? required?})]
           [:div.phrase-edit
            (components/markup-span (path/value path state)
                                    :phrase-preview)])]]])))

(rum/defc pate-link < rum/reactive
  [{:keys [schema] :as options}]
  (components/text-and-link {:text-loc (:text-loc schema)
                             :click    #((path/meta-value options (:click schema))
                                         options)}))

(rum/defc pate-button < rum/reactive
  [{:keys [schema] :as options} & [wrap-label?]]
  (let [{:keys [icon text? click
                add remove]} schema
        text?                (-> text? false? not)
        button               (when (path/visible? options)
                               [:button {:disabled (path/disabled? options)
                                         :class    (path/css options)
                                         :on-click (fn [_]
                                                     (cond
                                                       click
                                                       (path/meta-value options click)

                                                       (or add remove)
                                                       (path/meta-updated options true)))}
                                (when icon
                                  [:i {:class icon}])
                                (when text?
                                  [:span (path/loc options)])])]
    (if (show-label? schema wrap-label?)
      [:div.col--vertical
       (common/empty-label :pate-label)
       button]
      button)))

(rum/defc pate-toggle < rum/reactive
  [{:keys [schema state path] :as options} & [wrap-label?]]
  (label-wrap-if-needed
   options
   {:component    (components/toggle
                   (path/state path state)
                   {:callback  #(path/meta-updated options)
                    :disabled? (path/disabled? options)
                    :text      (if-let [dict (:text-dict schema)]
                                 (path/react (butlast path)
                                             state
                                             dict)
                                 (path/loc options))
                    :prefix    (:prefix schema)})
    :wrap-label?  wrap-label?
    :empty-label? true}))

(defn pate-unit [unit]
  (case unit
    :days    (path/loc :pate-date-delta unit)
    :years   (path/loc :pate-date-delta unit)
    :ha      (path/loc :unit.hehtaaria)
    :m2      [:span "m" [:sup 2]]
    :m3      [:span "m" [:sup 3]]
    :kpl     (path/loc :unit.kpl)
    :section "\u00a7"
    :eur     "\u20ac"
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

(defn pate-attr
  "Fills attribute map with :id, :key and :class. Also merges extras if
  given."
  [{:keys [path] :as options} & extras]
  (let [id (path/id path)]
    (apply merge (cons {:key   id
                        :id    id
                        :class (conj (path/css options)
                                     (common/css-flags :warning
                                                       (path/error? options)))}
                       extras))))

(defn- sort-by-schema
  [{sort-key :sort-by} items]
  (cond->> items
    sort-key (sort-by sort-key
                      (if (= sort-key :text)
                        js/util.localeComparator
                        identity))))

(rum/defc pate-text < rum/reactive
  ;;{:key-fn (fn [_ {path :path} _ & _] (path/id path))}
  "Update the options model state only on blur. Immediate update does
  not work reliably."
  [{:keys [schema state path] :as options} & [wrap-label?]]
  (let [attr-fn              (partial pate-attr
                                      options
                                      {:callback  (state-change-callback options)
                                       :disabled  (path/disabled? options)
                                       :required? (path/required? options)})
        {:keys [items lines]} schema
        value                (path/value path state)]
    (label-wrap-if-needed
     options
     {:component (sandwich schema
                           (cond
                             items
                             (components/combobox
                              value
                              (attr-fn {:items (sort-by-schema {:sort-by :text}
                                                               (map #(hash-map :text (path/loc %))
                                                                    items))}))

                             lines
                             (components/textarea-edit
                              value
                              (attr-fn {:class [:pate-textarea]
                                        :rows lines}))

                             :else
                             (components/text-edit
                              value
                              (attr-fn {:type (:type schema)}))))
      :wrap-label? wrap-label?})))

(defn pate-date-delta
  [{:keys [schema] :as options} & [wrap-label?]]
  (pate-text (-> options
                 (assoc-in [:schema :after] (:unit schema))
                 (assoc-in [:schema :type] :number)
                 (assoc-in [:schema :css] (conj (flatten [(:css schema)]) :date-delta)))
             wrap-label?))

(rum/defc pate-date < rum/reactive
  "Pate dates are always timestamps (ms from epoch). Since date-edit
  component handles Finnish date strings, we must do some transforming
  back and forth. Timestamp is marked in the noon of the date."
  [{:keys [path state] :as options} & [wrap-label?]]
  (label-wrap-if-needed
   options
   {:component (components/date-edit
                (js/util.finnishDate (path/react path state))
                (pate-attr options
                           {:callback (fn [datestring]
                                        (change-state options
                                                      (some-> datestring
                                                              (js/util.toMoment "fi")
                                                              .valueOf
                                                              (+ (* 1000 3600 12)))))
                            :disabled (path/disabled? options)}))
    :wrap-label? wrap-label?}))

(rum/defc pate-select < rum/reactive
  [{:keys [path state schema] :as options} & [wrap-label?]]
  (let [attr-fn      (partial pate-attr
                              options
                              {:callback  (state-change-callback options)
                               :disabled? (path/disabled? options)
                               :items     (->> (:items schema)
                                               (map (fn [item]
                                                      {:value item
                                                       :text  (path/loc options item)}))
                                               (sort-by-schema schema))
                               :required? (path/required? options)})
        value        (path/react path state)
        allow-empty? (-> schema :allow-empty false? not)]
    (label-wrap-if-needed
    options
    {:component   (if (= (:type schema) :autocomplete)
                    (components/autocomplete
                     value
                     (attr-fn {:clear? allow-empty?}))
                    (components/dropdown
                     value
                     (attr-fn {:choose? allow-empty?})))
     :wrap-label? wrap-label?})))
