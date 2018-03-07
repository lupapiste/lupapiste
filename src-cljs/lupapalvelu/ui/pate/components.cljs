(ns lupapalvelu.ui.pate.components
  "More or less Pate-specific user interface components. See
  layout.cljs for documentation on the component conventions. Docgen
  support is in docgen.cljs."
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.docgen :as docgen]
            [lupapalvelu.ui.pate.path :as path]
            [lupapalvelu.ui.pate.phrases :as phrases]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared-util :as util]))

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
        match-key                                      (or match-key item-key)]
    (->> (path/value (path/pathify (:path schema)) references )
         (remove :deleted) ; Safe for non-maps
         (map (fn [x]
                (let [v (if item-key (item-key x) x)]
                  {:value v
                   :text  (cond
                            (and match-key term-path) (-> (util/find-by-key match-key
                                                                            v
                                                                            (path/value term-path
                                                                                        references))
                                                          (get-in (cond->> [(keyword (common/get-current-language))]
                                                                    extra-path (concat extra-path))))
                            item-loc-prefix           (path/loc [item-loc-prefix v])
                            :else                     (path/loc options v))})))
         distinct
         (sort-by :text  (if (:sort? schema)
                           js/util.localeComparator
                           identity)))))

(rum/defc select-reference-list < rum/reactive
  [{:keys [schema references] :as options} & [wrap-label?]]
  (let [options   (assoc options
                         :schema (assoc schema
                                        :body [{:sortBy :displayName
                                                :body (map #(hash-map :name %)
                                                           (distinct (path/react (path/pathify (:path schema))
                                                                                 references)))}]))
        component (docgen/docgen-select options)]
    (if (show-label? schema wrap-label?)
      (docgen/docgen-label-wrap options component)
      component)))

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
  [{category* ::category
    selected* ::selected
    replaced* ::replaced :as local-state}
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
          disabled? (path/disabled? options)
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
              :callback  #(let [text-node (.-firstChild (rum/ref local-state ref-id) )
                                sel-start (.-selectionStart text-node)
                                sel-end   (.-selectionEnd text-node)
                                old-text  (or (path/value path state) "")]
                            (reset! replaced* (subs old-text sel-start sel-end))
                            (update-text (s/join (concat (take sel-start old-text)
                                                         %
                                                         (drop sel-end old-text)))))
              :disabled? disabled?})]]
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
         {:ref ref-id}
         (components/textarea-edit (path/value path state)
                                   {:callback  update-text
                                    :disabled  disabled?
                                    :required? required?})]]])))

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
    (if (show-label? options wrap-label?)
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
                    :text      (path/loc options)
                    :prefix    (:prefix schema)})
    :wrap-label?  wrap-label?
    :empty-label? true}))

(defn- state-change-callback
  "Updates state according to value."
  [{:keys [state path] :as options}]
  (fn [value]
    (when (common/reset-if-needed! (path/state path state) value)
      (path/meta-updated options))))

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
    (->> [:span.pate-sandwich
          {:class class}
          (when before
            [:span.sandwich--before
             (pate-unit before)])
          component
          (when after
            [:span.sandwich--after (pate-unit after)])])
    component))

(defn pate-attr
  "Fills attribute map with :id, :key and :class. Also merges extra if
  given."
  [{:keys [path] :as options} & [extra]]
  (let [id (path/id path)]
    (merge {:key   id
            :id    id
            :class (conj (path/css options)
                         (common/css-flags :warning
                                           (path/error? options)))}
           extra)))

(rum/defc pate-text < rum/reactive
  ;;{:key-fn (fn [_ {path :path} _ & _] (path/id path))}
  "Update the options model state only on blur. Immediate update does
  not work reliably."
  [{:keys [schema state path] :as options} & [wrap-label?]]
  (label-wrap-if-needed
   options
   {:component    (sandwich schema
                            (components/text-edit
                             (path/value path state)
                             (pate-attr options
                                        {:callback  (state-change-callback options)
                                         :disabled  (path/disabled? options)
                                         :required? (path/required? options)
                                         :type      (:type schema)})))
    :wrap-label?  wrap-label?}))

(defn pate-date-delta
  [{:keys [schema] :as options}  & [wrap-label?]]
  (pate-text (-> options
                 (assoc-in [:schema :after] (:unit schema))
                 (assoc-in [:schema :type] :number)
                 (assoc-in [:schema :css] (conj (flatten [(:css schema)]) :max-width-5em)))
             wrap-label?))

#_(rum/defc pate-date-delta < rum/reactive
  [{:keys [state path schema] :as options}  & [wrap-label?]]
  (let [required? (path/required? options)
        delta-path (path/extend path :delta)]
    [:div.pate-date-delta
     (when (show-label? schema wrap-label?)
       [:label.delta-label {:class (common/css-flags :required required?)
                            :for (path/id delta-path)}
        (path/loc options)])
     [:div.delta-editor
      (docgen/text-edit (assoc options
                               :path delta-path
                               :required? required?)
                        :text
                        {:type "number"
                         :disabled (path/disabled? options)})
      (common/loc (str "pate-date-delta." (-> schema :unit name)))]]))

#_(rum/defc pate-date < rum/reactive
  [{:keys [path state] :as options}])
