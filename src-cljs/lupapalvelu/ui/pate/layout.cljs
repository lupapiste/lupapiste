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
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.components :as pate-components]
            [lupapalvelu.ui.pate.attachments :as pate-att]
            [lupapalvelu.ui.pate.docgen :as docgen]
            [lupapalvelu.ui.pate.path :as path]
            [lupapalvelu.ui.pate.phrases :as phrases]
            [lupapalvelu.ui.pate.placeholder :as placeholder]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defn schema-type [options]
  (-> options :schema (dissoc :required?) keys first keyword))

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

(rum/defc instantiate-docgen < rum/reactive
  [{:keys [schema] :as options} wrap-label?]
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

(rum/defc  instantiate-default < rum/reactive
  [{:keys [schema] :as options} wrap-label?]
  (let [cell-type    (schema-type options)
        schema-value (cell-type schema)
        options      (path/schema-options options schema-value)]
    (if (path/react-meta options :editing?)
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

(defmethod instantiate :docgen
  [options & [wrap-label?]]
  (instantiate-docgen options wrap-label?))

(defmethod instantiate :default
  [options & [wrap-label?]]
  (instantiate-default options wrap-label?))

(defmethod instantiate :loc-text
  [{:keys [schema]} & _]
  [:span
   (common/loc (name (:loc-text schema)))])


;; -------------------------------
;; View layout components
;; -------------------------------

(defmulti view-component (fn [cell-type _]
                           cell-type))

(defmethod view-component :default
  [& _])

(defmethod view-component :reference-list
  [_ {:keys [state path schema ] :as options}]
  (let [values (set (flatten [(path/value path state)]))]
    [:span (->> (pate-components/resolve-reference-list options)
                (filter #(contains? values (:value %)))
                (map :text)
                (s/join (get schema :separator ", ")))]))

(defmethod view-component :phrase-text
  [_ {:keys [state path schema] :as options}]
  [:span.phrase-text (path/value path state)])

(defmethod view-component :reference
  [_ {:keys [state schema references] :as options}]
  (let [[x & xs :as path] (-> schema :path util/split-kw-path)]
    [:span.formatted
     (if (util/=as-kw x :*ref)
       (path/react xs references)
       (path/react path state))]))

(defmethod view-component :placeholder
  [_ {:keys [state path schema] :as options}]
  (placeholder/placeholder options))

(defmethod view-component :attachments
  [_ {:keys [state path schema] :as options}]
  (pate-att/pate-attachments options))

(defmethod view-component :link
  [_ {:keys [schema] :as options}]
  (pate-components/pate-link options))

(defmethod view-component :application-attachments
  [_ {:keys [schema info] :as options}]
  ((if (path/value :published info)
     pate-att/pate-frozen-application-attachments
     pate-att/pate-application-attachments) options))

(defmethod view-component :text
  [_ {:keys [schema state path] :as options}]
  (pate-components/sandwich (assoc schema
                                   :class :sandwich__view)
                            [:span (path/value path state)]))

(defmethod view-component :date
  [_ {:keys [schema state path] :as options}]
  (pate-components/sandwich (assoc schema
                                   :class :sandwich__view)
                            [:span (path/value path state)]))

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
  [:div.pate-list
   {:class (path/css options)}
   (when (and wrap-label? (:title schema))
     [:h4.pate-label (common/loc (:title schema))])
   (map-indexed (fn [i item-schema]
                  (let [item-options (path/schema-options options item-schema)]
                    (when (path/visible? item-options)
                      [:div.item {:key   (str "item-" i)
                                  :class (path/css item-options
                                                   (when-let [item-align (:align item-schema)]
                                                     (str "item--" (name item-align))))}
                       (when (:dict item-schema)
                         (instantiate (path/dict-options item-options)
                                      (-> schema :labels? false? not)))])))
                (:items schema))])

(defn- repeating-keys
  "The repeating keys (keys within the state that correspond to a
  repeating schema). Sorted by :sort-by if given within schema."
  [{:keys [dictionary path state] :as options} repeating]
  (let [r-map    (path/react repeating state)
        sort-key (get-in dictionary (path/extend path repeating :sort-by))]
    (if sort-key
      (->> r-map
           (sort-by (fn [[_ a] [_ b]]
                      (compare (sort-key a) (sort-key b))))
           (map first))
      (keys r-map))))

(rum/defc pate-grid < rum/reactive
  {:key-fn #(-> % :path path/id)}
  [{:keys [schema path state] :as options}]
  (letfn [(grid [{:keys [schema] :as options}]
            [:div
             {:class (path/css options
                               (str "pate-grid-" (:columns schema)))}
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
                                       :>> #(pate-list (path/schema-options cell-options %)
                                                        true)

                                       :grid
                                       :>> #(pate-grid (path/schema-options cell-options %))

                                       nil)])))

                              (get row-schema :row row-schema))])))
                  (-> schema :rows))])]
    (if-let [repeating (:repeating schema)]
      [:div (map (fn [k]
                   (pate-grid (assoc options
                                     :schema (dissoc schema :repeating)
                                     :path (path/extend path repeating k))))
                 (repeating-keys options repeating))]
      (grid options))))
