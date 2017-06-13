(ns lupapalvelu.ui.matti.sections
  (:require [clojure.string :as s]
            [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.matti.docgen :as docgen]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.service :as service]
            [rum.core :as rum]))


(defn schema-type [schema-type]
  (-> schema-type :schema keys first keyword))

(declare matti-list)

(rum/defc matti-date-delta < rum/reactive
  [{:keys [state path schema] :as options}  & [wrap-label?]]
  (let [enabled-path (path/extend path :enabled)]
    [:div.matti-date-delta
     (when wrap-label?
       [:div.delta-label (path/loc path)])
     [:div.delta-editor
      (docgen/docgen-checkbox (assoc options
                                     :path enabled-path
                                     :schema (assoc schema :label false)))
      "+"
      (docgen/text-edit (assoc options :path (path/extend path :delta))
                        :input.grid-style-input
                        {:type "number"
                         :disabled (not (path/react enabled-path state))})
      (common/loc (str "matti-date-delta." (-> schema :unit name)))]]))


(rum/defc matti-multi-select < rum/reactive
  [{:keys [state path schema] :as options}  & [wrap-label?]]
  (println "Items:" (:items schema))
  [:div.matti-multi-select
   (when wrap-label?
     [:h4.matti-label (path/loc path)])
   (let [state (path/state path state)]
     (for [item (map name (:items schema))
           :let [item-id (path/id (path/extend path item))
                 checked (contains? (set (rum/react state)) item)]]
       [:div.matti-checkbox-wrapper
        {:key     item-id}
        [:input {:type "checkbox"
                 :id      item-id
                 :checked checked}]
        [:label.matti-checkbox-label
         {:for item-id
          :on-click (fn [_]
                      (swap! state
                            (fn [xs]
                              (distinct (if checked
                                          (remove #(= item %) xs)
                                          (cons item xs)))))
                      (path/meta-updated options))}
         (path/loc path options item)]]))])

(rum/defc select-from-settings < rum/reactive
  [{:keys [state path schema settings] :as options} & [wrap-label?]]
  (let [options   (assoc options
                         :schema (assoc schema
                                        :body [{:body (map #(hash-map :name %)
                                                           (distinct (path/react (:path schema)
                                                                                 settings)))}]))
        component (docgen/docgen-select options)]
    (if wrap-label?
      (docgen/docgen-label-wrap options component)
      component)))

(rum/defc multi-select-from-settings < rum/reactive
  [{:keys [state path schema settings] :as options} & [wrap-label?]]
  (matti-multi-select (assoc-in options [:schema :items] (distinct (path/react (:path schema)
                                                                               settings)))
                      wrap-label?))

(defmulti instantiate (fn [_ cell & _]
                      (schema-type cell)))

(defmethod instantiate :docgen
  [{:keys [state path]} {:keys [schema id]} & [wrap-label?]]
  (let [schema-name (:docgen schema)
        options     (shared/child-schema {:state  state
                                          :path   (path/extend path id)
                                          :schema (service/schema schema-name)}
                                         :schema
                                         schema)
        editing?    (path/meta? options :editing? )]
    (cond->> options
      editing?       docgen/docgen-component
      (not editing?) docgen/docgen-view
      wrap-label?    (docgen/docgen-label-wrap options ))))

(defmethod instantiate :default
  [{:keys [state path] :as options} {:keys [schema id] :as cell} & [wrap-label?]]
  (let [cell-type    (schema-type cell)
        schema-value (-> schema vals first)
        options      (shared/child-schema (assoc options
                                                 :state  state
                                                 :path   (path/extend path id (:id schema-value))
                                                 :schema schema-value)
                                          :schema
                                          schema)]
    ((case cell-type
       :list          matti-list
       :date-delta    matti-date-delta
       :from-settings (if (= :select (:type schema-value))
                        select-from-settings
                        multi-select-from-settings)
       :multi-select  matti-multi-select) options wrap-label?)))

(defmethod instantiate :loc-text
  [_ {:keys [schema]} & wrap-label?]
  [:span
   (common/loc (name (:loc-text schema)))])

(defn- sub-options
  "Options for the subschema"
  [{:keys [state path]} subschema]
  (assoc subschema
         :state state
         :path (path/extend path (:id subschema))))

(defn matti-list [{:keys [schema state path] :as options} & [wrap-label?]]
  [:div.matti-list
   {:class (path/css (sub-options options schema))}
   (when wrap-label?
     [:h4.matti-label (path/loc path schema)])
   (for [i    (-> schema :items count range)
         :let [item (nth (:items schema) i)
               component (instantiate options
                                      (shared/child-schema item :schema schema)
                                      wrap-label?)]]
     (when component
       [:div.item {:key   (str "item-" i)
                   :class (path/css (sub-options options item)
                                    (when (:align item)
                                      (str "item--" (-> item :align name))))}
        component]))])

(defn matti-grid [{:keys [grid state path] :as options}]
  [:div
   {:class (path/css (sub-options options grid)
                     (str "matti-grid-" (:columns grid)))}
   (for [row (:rows grid)]
     [:div.row {:class (some->> row :css (map name) s/join )}
      (for [{:keys [col align schema id] :as cell} (get row :row row)]
        [:div {:class (path/css (sub-options options cell)
                                (str "col-" (or col 1))
                                (when align (str "col--" (name align))))}
         (when schema
           (instantiate options (shared/child-schema cell :schema grid) true))])])])

(rum/defc section-checkbox < rum/reactive
  [{:keys [state path]} kw & [{:keys [disabled negate?]}]]
  (let [path       (path/extend path kw)
        state*     (path/state path state)
        handler-fn (fn [flag]
                     (reset! state* flag)
                     (path/meta-updated {:path  path
                                         :state state}))]
    (components/checkbox {:label      (str "matti.template-" (name kw))
                          :value      (rum/react state*)
                          :handler-fn handler-fn
                          :disabled   disabled
                          :negate?    negate?})))

(defn section-header
  [{:keys [state path] :as options}]
  [:div.section-header.matti-grid-2
   [:div.row.row--tight
    [:div.col-1
     [:span.row-text.section-title (path/loc path)]]
    [:div.col-1.col--right
     (if (path/meta? options :can-remove?)
       [:span
        (when-not (path/value path state :removed)
          (section-checkbox options :pdf))
        (section-checkbox options :removed {:negate? true})]
       [:span.row-text (common/loc :matti.always-in-verdict)])]]])

(rum/defc section < rum/reactive
  {:key-fn (fn [{path :path}] (path/id path))}
  [{:keys [state path id css] :as options}]
  [:div.matti-section
   {:class (path/css options)}
   (section-header options)
   (when-not (path/react path state :removed)
     [:div.section-body (matti-grid (shared/child-schema options :grid options))])])
