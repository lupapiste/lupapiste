(ns lupapalvelu.ui.pate.layout
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
  (:require [clojure.string :as s]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.pate.shared-schemas :as schemas]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.attachments :as pate-att]
            [lupapalvelu.ui.pate.components :as pate-components]
            [lupapalvelu.ui.pate.placeholder :as placeholder]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defn schema-type [options]
  (-> options :schema (select-keys schemas/schema-type-keys) keys first keyword))

(declare pate-list)

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
             Default is common/nbsp (non-breaking space)"
  ([component]
   (vertical {} component))
  ([{:keys [col align label]} component]
   [:div
    {:class (cond->> [(str "col-" (or col 1))]
              align (cons (str "col--" (name align))))}
    [:div.col--vertical
     [:label.pate-label (cond
                           (keyword? label) (common/loc label)
                           (string? label) label
                           :default common/nbsp)]
     component]]))

;; -------------------------------
;; Component instantiation
;; -------------------------------

(declare wrap-view-component)

(rum/defc  instantiate-default < rum/reactive
  [{:keys [schema] :as options} wrap-label?]
  (let [cell-type    (schema-type options)
        schema-value (cell-type schema)
        options      (path/schema-options options schema-value)]
    (if (and (or (path/react-meta options :editing?)
                 (:always-editing? schema-value))
             (not (:read-only? schema-value)))
      ((case cell-type
         :date-delta     pate-components/pate-date-delta
         :reference-list (case (:type schema-value)
                           :select       pate-components/select-reference-list
                           :multi-select pate-components/multi-select-reference-list
                           :list         pate-components/list-reference-list)
         :multi-select   pate-components/pate-multi-select
         :phrase-text    pate-components/pate-phrase-text
         :button         pate-components/pate-button
         :application-attachments pate-att/pate-select-application-attachments
         :toggle         pate-components/pate-toggle
         :text           pate-components/pate-text
         :date           pate-components/pate-date
         :select         pate-components/pate-select
         ;; The rest are always displayed as view components
         (partial wrap-view-component cell-type)) options wrap-label?)
      (wrap-view-component cell-type options wrap-label?))))

(defmulti instantiate (fn [options & _]
                        (schema-type options)))

(defmethod instantiate :default
  [options & [wrap-label?]]
  (instantiate-default options wrap-label?))

(defmethod instantiate :loc-text
  [{:keys [schema] :as options} & _]
  [:span
   {:class (path/css options)}
   (common/loc (name (:loc-text schema)))])


;; -------------------------------
;; View layout components
;; -------------------------------

(defmulti view-component (fn [cell-type _] cell-type))

(defmethod view-component :default [& _])

(defmethod view-component :reference-list [_ {:keys [state path schema ] :as options}]
  (let [values (set (flatten [(path/value path state)]))]
    [:span (->> (pate-components/resolve-reference-list options)
                (filter #(contains? values (:value %)))
                (map :text)
                (s/join (get schema :separator ", ")))]))

(defmethod view-component :phrase-text [_ {:keys [state path]}]
  (components/markup-span (path/value path state)))

(defmethod view-component :reference [_ {:keys [state schema references]}]
  (let [[x & xs :as path] (-> schema :path util/split-kw-path)]
    (components/markup-span (if (util/=as-kw x :*ref)
                                (path/react xs references)
                                (path/react path state)))))

(defmethod view-component :placeholder [_ options]
  (placeholder/placeholder options))

(defmethod view-component :attachments [_ options]
  (pate-att/pate-attachments options))

(defmethod view-component :link [_ options]
  (pate-components/pate-link options))

(defmethod view-component :application-attachments [_ options]
  (pate-att/pate-attachments-view options))

(defmethod view-component :text [_ {:keys [schema state path]]
  (let [value (path/value path state)]
    (when-not (s/blank? value)
      (pate-components/sandwich (assoc schema
                                       :class :sandwich__view)
                                [:span value]))))

(defmethod view-component :date [_ {:keys [schema state path]}]
  (pate-components/sandwich (assoc schema
                                   :class :sandwich__view)
                            [:span (js/util.finnishDate (path/value path state))]))

(defmethod view-component :select [_ {:keys [state path] :as options}]
  (let [value (path/value path state)]
    [:span (when-not (s/blank? value)
             (pate-components/pate-select-item-text options value))]))

(defn wrap-view-component [cell-type options wrap-label?]
  (pate-components/label-wrap-if-needed
   options
   {:component (view-component cell-type options)
    :wrap-label? wrap-label?}))

;; -------------------------------
;; Containers
;; -------------------------------


(rum/defc pate-list < rum/reactive
  [{:keys [schema] :as options} & [wrap-label?]]
  (let [items (map-indexed
               (fn [i item-schema]
                 (let [item-options (path/schema-options options item-schema)]
                   (when (path/item-visible? item-options)
                      {:component [:div.item
                                   {:key   (str "item-" i)
                                    :class (path/css item-options
                                                     (when-let [item-align (:align item-schema)]
                                                       (str "item--" (name item-align))))}
                                   (when (:dict item-schema)
                                     (instantiate (path/dict-options item-options)
                                                  (-> schema :labels? false? not)))]
                       :required? (-> item-options
                                      path/dict-options
                                      :schema
                                      :required? )})))
                           (:items schema))]
    [:div.pate-list
     {:class (path/css options)}
     (when (and wrap-label? (:title schema))
       [:h4.pate-label
        {:class (common/css-flags :required (some :required? items))}
        (common/loc (:title schema))])
     (mapv :component items)]))

(defn- repeating-keys
  "The repeating keys (keys within the state that correspond to a
  repeating schema). Sorted by :sort-by if given within schema."
  [{:keys [dictionary path state]} repeating]
  (let [r-map  (path/react repeating state)
        sorter (get-in dictionary (path/extend path repeating :sort-by))
        sort-key (if-let [prefix (:prefix sorter)]
                   (common/prefix-lang prefix)
                   sorter)]
    (if sort-key
      (->> r-map
           (sort-by (fn [[_ m]]
                      (let [v (sort-key m)]
                        (cond-> v
                          (string? v) s/lower-case))))
           (map first))
      (keys r-map))))

(rum/defc pate-grid < rum/reactive
  {:key-fn #(-> % :path path/id)}
  ([{:keys [schema path] :as options} extras]
   (if-let [repeating (:repeating schema)]
     [:div (map-indexed (fn [i k]
                          (pate-grid (assoc options
                                            :schema (dissoc schema :repeating)
                                            :path (path/extend path repeating k))
                                     (common/add-test-id {:data-repeating-id k }
                                                         repeating (str i))))
                        (repeating-keys options repeating))]
     [:div
      (merge {:class (path/css options
                               (str "pate-grid-" (:columns schema)))}
             extras)
      (map (fn [row-schema]
             (let [row-options (path/schema-options options row-schema)]
               ;; Row visibility
               (when (path/visible? row-options)
                 [:div.row {:class (path/schema-css row-schema)}
                  (map (fn [{:keys [col align] :as cell-schema}]
                         (let [cell-options (path/schema-options row-options
                                                                 cell-schema)]
                           ;; Cell visibility
                           (when (path/item-visible? cell-options)
                             [:div {:class (path/css cell-options
                                                     (str "col-" (or col 1))
                                                     (when align
                                                       (str "col--" (name align))))}
                              (condp #(%1 %2) cell-schema
                                :dict
                                (instantiate (path/dict-options cell-options)
                                             true)

                                :list
                                :>> #(pate-list (path/schema-options cell-options %)
                                                true)

                                :grid
                                :>> #(pate-grid (path/schema-options cell-options %))

                                nil)])))

                       (get row-schema :row row-schema))])))
           (-> schema :rows))]))
  ([options]
   (pate-grid options nil)))
