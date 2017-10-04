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
            [lupapalvelu.ui.matti.components :as matti-components]
            [lupapalvelu.ui.matti.docgen :as docgen]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.phrases :as phrases]
            [lupapalvelu.ui.matti.placeholder :as placeholder]
            [lupapalvelu.ui.matti.service :as service]
            [lupapalvelu.ui.matti.state :as state]
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

;; -------------------------------
;; Component instantiation
;; -------------------------------

(declare view-component)

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
         :date-delta     matti-components/matti-date-delta
         :reference-list (if (= :select (:type schema-value))
                           matti-components/select-reference-list
                           matti-components/multi-select-reference-list)
         :multi-select   matti-components/matti-multi-select
         :phrase-text    matti-components/matti-phrase-text
         ;; The rest are always displayed as view components
         (partial view-component cell-type)) options wrap-label?)
      (view-component cell-type options wrap-label?))))

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

(defmulti view-component (fn [cell-type & _]
                           cell-type))

(defmethod view-component :reference-list
  [_ {:keys [state path schema ] :as options} & [wrap-label?]]
  (let [values (set (flatten [(path/value path state)]))
        span [:span (->> (matti-components/resolve-reference-list options)
                         (filter #(contains? values (:value %)))
                         (map :text)
                         (s/join (get schema :separator ", ")))]]
    (if (matti-components/show-label? schema wrap-label?)
      (docgen/docgen-label-wrap options span)
      span)))

(defmethod view-component :phrase-text
  [_ {:keys [state path schema] :as options} & [wrap-label?]]
  (let [span [:span.phrase-text (path/value path state)]]
    (if (matti-components/show-label? schema wrap-label?)
      (docgen/docgen-label-wrap options span)
      span)))

(defmethod view-component :reference
  [_ {:keys [state path schema] :as options} & [wrap-label?]]
  (let [span [:span.formatted (path/react (-> schema :path util/split-kw-path)
                                          state)]]
    (if (matti-components/show-label? schema wrap-label?)
      (docgen/docgen-label-wrap options span)
      span)))

(defmethod view-component :placeholder
  [_ {:keys [state path schema] :as options} & [wrap-label?]]
  (let [elem (placeholder/placeholder options)]
    (if (matti-components/show-label? schema wrap-label?)
      (docgen/docgen-label-wrap options elem)
      elem)))

(defmethod view-component :attachments
  [_ {:keys [state path schema] :as options} & [wrap-label?]]
  (let [elem (matti-components/matti-attachments options)]
    (if (matti-components/show-label? schema wrap-label?)
      (docgen/docgen-label-wrap options elem)
      elem)))

;; -------------------------------
;; Containers
;; -------------------------------


(rum/defc matti-list < rum/reactive
  [{:keys [schema] :as options} & [wrap-label?]]
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

(rum/defc matti-grid < rum/reactive
  [{:keys [schema path state] :as options}]
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
      [:div (map (fn [k]
                   (grid (assoc options
                                :schema (dissoc schema :repeating)
                                :path (path/extend path repeating k))))
                 (keys (path/value repeating state)))]
      (grid options))))
