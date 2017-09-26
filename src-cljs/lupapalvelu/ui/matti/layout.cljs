(ns lupapalvelu.ui.matti.layout
  "General guidance on the layout component parameters:

  schema (schema): The schema of the current component.

  dictionary (schema): Dictionary for the whole schema (e.g., verdict)

  state (atom): The whole current state atom. Structurally corresponds
  to the dictionary.

  info (atom): State values outside of the current schema (and
  dictionary). Typical example is the modified timestamp that is
  updated separately.

  _meta (atom): Runtime 'meta-state' that supports
  partly/hierarchically resolved path values. See path/meta-value for
  details.

  _parent (map): Options of the parent component. The parent can also
  be a part of a component (cell for example).

  path (list): Current component's value path within the state. For
  simple components this is just the dictionary key wrapped in a list.

  id-path (list): List of ids for the component schema path. Each id
  that is on the schema path. id-path is used when resolving _meta queries.

  loc-path (list): Latest loc-prefix in the schema path and the later ids.

  references (atom): Data for external reference resolution (see
  reference-list components)"
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.matti.docgen :as docgen]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.service :as service]
            [lupapalvelu.ui.matti.state :as state]
            [lupapalvelu.ui.matti.phrases :as phrases]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defn schema-type [options]
  (-> options :schema keys first keyword))

(declare matti-list)

;; -------------------------------
;; Miscellaneous components
;; -------------------------------

(defn vertical
  "Convenience wrapper for vertical column cell.
  Parameters [optional]: [options] component
  Options [optional]:
    col:  column width (default 1)
    [align] column alignment (:left, :right, :center or :full)
    [label]  keyword -> localization key
             string  -> label string
             Default is nbsp (see above)"
  ([component]
   (vertical {} component))
  ([{:keys [col align label]} component]
   [:div
    {:class (cond->> [(str "col-" (or col 1))]
              align (cons (str "col--" (name align))))}
    [:div.col--vertical
     [:label.matti-label (cond
                           (keyword? label) (common/loc label)
                           (string? label) label
                           :default common/nbsp)]
     component]]))

(defn show-label? [{label? :label?} wrap-label?]
  (and wrap-label? (not (false? label?))))

(rum/defc matti-date-delta < rum/reactive
  [{:keys [state path schema] :as options}  & [wrap-label?]]
  (let [enabled-path (path/extend path :enabled)]
    [:div.matti-date-delta
     (when (show-label? schema wrap-label?)
       [:div.delta-label (path/loc options)])
     [:div.delta-editor
      (docgen/docgen-checkbox (assoc options
                                     :path enabled-path
                                     :schema (assoc schema :label false)))
      "+"
      (docgen/text-edit (assoc options :path (path/extend path :delta))
                        :input.grid-style-input
                        {:type "number"
                         :disabled (or (not (path/react enabled-path state))
                                       (path/disabled? options))})
      (common/loc (str "matti-date-delta." (-> schema :unit name)))]]))


(rum/defc matti-multi-select < rum/reactive
  [{:keys [state path schema] :as options}  & [wrap-label?]]
  [:div.matti-multi-select
   (when (show-label? schema wrap-label?)
     [:h4.matti-label (path/loc options)])
   (let [state (path/state path state)
         items (->> (:items schema)
                    (map (fn [item]
                           (if (:value item)
                             item
                             (let [item (keyword item)]
                               {:value item
                                :text  (if-let [item-loc (:item-loc-prefix schema)]
                                         (path/loc [item-loc item])
                                         (path/loc options item))}))))
                    (sort-by :text))]

     (for [{:keys [value text]} items
           :let                 [item-id (path/unique-id "multi")
                                 checked (util/includes-as-kw? (set (rum/react state)) value)]]
       [:div.matti-checkbox-wrapper
        {:key item-id}
        [:input {:type    "checkbox"
                 :id      item-id
                 :disabled (path/disabled? options)
                 :checked checked}]
        [:label.matti-checkbox-label
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
         distinct)))

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
  (matti-multi-select (assoc-in options [:schema :items] (resolve-reference-list options))
                      wrap-label?))

(rum/defc last-saved < rum/reactive
  [{info* :info}]
  [:span.saved-info
   (when-let [ts (path/react [:modified] info*)]
     (common/loc :matti.last-saved (js/util.finnishDateAndTime ts)))])

(rum/defcs matti-phrase-text < rum/reactive
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
    (let [ref-id    (path/unique-id "-ref")
          disabled? (path/disabled? options)]
      [:div.matti-grid-12
       (when (show-label? schema wrap-label?)
         [:h4.matti-label (path/loc options)])
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
          (components/autocomplete selected*
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
            (common/loc :matti.clear)]
           [:button.primary.outline
            {:disabled (let [phrase (rum/react selected*)]
                         (or disabled?
                             (s/blank? phrase)
                             (not (re-find (re-pattern (goog.string/regExpEscape phrase))
                                           (path/react path state)))))
             :on-click (fn []
                         (update-text (s/replace-first (path/value path state)
                                                       @selected*
                                                       @replaced*))
                         (reset! selected* ""))}
            (common/loc :phrase.undo)]]]]]
       [:div.row
        [:div.col-12.col--full
         {:ref ref-id}
         (components/textarea-edit (path/state path state)
                                   {:callback update-text
                                    :disabled disabled?})]]])))

;; -------------------------------
;; Component instantiation
;; -------------------------------

(declare view-component)

(defmulti instantiate (fn [options & _]
                        (schema-type options)))

(defmethod instantiate :docgen
  [{:keys [schema] :as options} & [wrap-label?]]
  (let [docgen      (:docgen schema)
        schema-name (get docgen :name docgen)
        options     (path/schema-options options
                                         (cond-> (service/schema schema-name)
                                           ;; Additional, non-legacy properties
                                           (map? docgen) (merge (dissoc docgen :name))))
        editing?    (path/react-meta options :editing?)]
    (cond->> options
      editing?       docgen/docgen-component
      (not editing?) docgen/docgen-view
      wrap-label?    (docgen/docgen-label-wrap options))))

(defmethod instantiate :default
  [{:keys [schema] :as options} & [wrap-label?]]
  (let [cell-type    (schema-type options)
        schema-value (cell-type schema)
        options      (path/schema-options options schema-value)]
    (if (path/react-meta options :editing?)
      ((case cell-type
         :date-delta     matti-date-delta
         :reference-list (if (= :select (:type schema-value))
                           select-reference-list
                           multi-select-reference-list)
         :multi-select   matti-multi-select
         :phrase-text    matti-phrase-text
         ;; The rest are always displayed as view components
         (partial view-component cell-type)) options wrap-label?)
      (view-component cell-type options wrap-label?))))

(defmethod instantiate :loc-text
  [{:keys [schema]} & _]
  [:span
   (common/loc (name (:loc-text schema)))])


;; -------------------------------
;; View layout components
;; -------------------------------

(defmulti view-component (fn [cell-type & _]
                           cell-type))

(defmethod view-component :reference-list
  [_ {:keys [state path schema ] :as options} & [wrap-label?]]
  (let [values (set (flatten [(path/value path state)]))
        span [:span (->> (resolve-reference-list options)
                         (filter #(contains? values (:value %)))
                         (map :text)
                         (s/join (get schema :separator ", ")))]]
    (if (show-label? schema wrap-label?)
      (docgen/docgen-label-wrap options span)
      span)))

(defmethod view-component :phrase-text
  [_ {:keys [state path schema] :as options} & [wrap-label?]]
  (let [span [:span.phrase-text (path/value path state)]]
    (if (show-label? schema wrap-label?)
      (docgen/docgen-label-wrap options span)
      span)))

(defmethod view-component :reference
  [_ {:keys [state path schema] :as options} & [wrap-label?]]
  (let [span [:span.formatted (path/react (-> schema :path util/split-kw-path)
                                          state)]]
    (if (show-label? schema wrap-label?)
      (docgen/docgen-label-wrap options span)
      span)))

(defmulti placeholder (fn [options & _]
                        (-> options :schema :type)))

;; Neighbors value is a list of property-id, timestamp maps.  Before
;; publishing the verdict, the neighbors are taken from the
;; applicationModel. On publishing the neighbor states are frozen into
;; mongo.
(defmethod placeholder :neighbors
  [{:keys [state path] :as options}]
  [:div.tabby.neighbor-states
   (map (fn [{:keys [property-id done]}]
          [:div.tabby__row.neighbor
           [:div.tabby__cell.property-id (js/util.prop.toHumanFormat property-id)]
           [:div.tabby__cell
            (if done
              (common/loc :neighbors.closed
                          (js/util.finnishDateAndTime done
                                                      "D.M.YYYY HH:mm"))
              (common/loc :neighbors.open))]])
        (path/value path state))])

(defmethod placeholder :application-id
  [_]
  [:span.formatted (lupapisteApp.services.contextService.applicationId)])

(defmethod placeholder :building
  [{:keys [state path]}]
  (let [{:keys [operation building-id tag description]} (path/value (butlast path) state)]
    [:span.formatted (->> [(path/loc :operations operation)
                           (s/join ": " (remove nil? [tag description]))
                           building-id]
                          (remove s/blank?)
                          (s/join " \u2013 "))]))



(defmethod view-component :placeholder
  [_ {:keys [state path schema] :as options} & [wrap-label?]]
  (let [elem (placeholder options)]
    (if (show-label? schema wrap-label?)
      (docgen/docgen-label-wrap options elem)
      elem)))

(defn matti-list [{:keys [schema] :as options} & [wrap-label?]]
  [:div.matti-list
   {:class (path/css options)}
   (when (and wrap-label? (:title schema))
     [:h4.matti-label (common/loc (:title schema))])
   (map-indexed (fn [i item-schema]
                  (let [item-options (path/schema-options options item-schema)]
                    (when (path/visible? item-options)
                      [:div.item {:key   (str "item-" i)
                                  :class (path/css item-options
                                                   (when-let [item-align (:align item-schema)]
                                                     (str "item--" (name item-align))))}
                       (when (:dict item-schema)
                         (instantiate (path/dict-options item-options)
                                      true))])))
                (:items schema))])

(defn matti-grid [{:keys [schema path state] :as options}]
  (letfn [(grid [{:keys [schema] :as options}]
            [:div
             {:class (path/css options
                               (str "matti-grid-" (:columns schema)))}
             (map (fn [row-schema]
                    (let [row-options (path/schema-options options row-schema)]
                      ;; Row visibility
                      (when (path/visible? row-options)
                        [:div.row {:class (some->> row-schema :css (map name) s/join )}
                         (map (fn [{:keys [col align] :as cell-schema}]
                                (let [cell-options (path/schema-options row-options
                                                                        cell-schema)]
                                  ;; Cell visibility
                                  (when (path/visible? cell-options)
                                    [:div {:class (path/css cell-options
                                                            (str "col-" (or col 1))
                                                            (when align
                                                              (str "col--" (name align))))}
                                     (condp #(%1 %2) cell-schema
                                       :dict
                                       (instantiate (path/dict-options cell-options)
                                                    true)

                                       :list
                                       :>> #(matti-list (path/schema-options cell-options %)
                                                        true)

                                       :grid
                                       :>> #(matti-grid (path/schema-options cell-options %))

                                       nil)])))

                              (get row-schema :row row-schema))])))
                  (-> schema :rows))])]
    (if-let [repeating (:repeating schema)]
      (map (fn [k]
             (grid (assoc options
                          :schema (dissoc schema :repeating)
                          :path (path/extend path repeating k))))
           (keys (path/value repeating state)))
      (grid options))))
