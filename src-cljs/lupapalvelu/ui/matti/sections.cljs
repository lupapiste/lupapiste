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
(declare matti-loc-text)

(defmulti instantiate (fn [_ cell & _]
                      (schema-type cell)))

(defmethod instantiate :docgen
  [{:keys [state path]} {:keys [schema id]} & wrap-label?]
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
  [{:keys [state path]} {:keys [schema id] :as cell} & wrap-label?]
  (let [cell-type    (schema-type cell)
        schema-value (-> schema vals first)
        options      (shared/child-schema {:state  state
                                           :path   (path/extend path id (:id schema-value))
                                           :schema schema-value}
                                          :schema
                                          schema)]
    ;; TODO: label wrap
    ((case cell-type
       :list matti-list) options)))

(defmethod instantiate :loc-text
  [_ {:keys [schema]} & wrap-label?]
  [:span (common/loc (name (:loc-text schema)))])

(defn- sub-options
  "Options for the subschema"
  [{:keys [state path]} subschema]
  (assoc subschema
         :state state
         :path (path/extend path (:id subschema))))

(defn matti-list [{:keys [schema state path] :as options}]
  [:div.matti-list
   {:class (path/css (sub-options options schema))}
   ;; TODO: label wrap
   [:h4.matti-label (path/loc path schema)]
   (for [i    (-> schema :items count range)
         :let [item (nth (:items schema) i)
               component (instantiate options
                                      (shared/child-schema item :schema schema)
                                      false)]]
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
     [:div.row
      (for [{:keys [col align schema id] :as cell} row]
        [:div {:class (path/css (sub-options options cell)
                                (str "col-" (or col 1))
                                (when align (str "col--" (name align))))}
         (when schema
           (instantiate options (shared/child-schema cell :schema grid) true))])])])

(defn section-checkbox [{:keys [state path]} kw & [{:keys [disabled negate?]}]]
  (let [path       (path/extend path kw)
        state*     (path/state path state)
        handler-fn (fn [flag]
                     (reset! state* flag)
                     (path/meta-updated {:path  path
                                         :state state}))]
    (components/checkbox {:label      (str "matti.template-" (name kw))
                          :value      @state*
                          :handler-fn handler-fn
                          :disabled   disabled
                          :negate?    negate?})))

(defn section-header [{:keys [state path] :as options}]
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
   (when-not (path/value path state :removed)
     [:div.section-body (matti-grid (shared/child-schema options :grid options))])])
