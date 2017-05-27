(ns lupapalvelu.ui.matti.sections
  (:require [rum.core :as rum]
            [lupapalvelu.ui.matti.service :as service]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.docgen :as docgen]
            [clojure.string :as s]))


(defn schema-type [schema-type]
  (-> schema-type :schema keys first keyword))

(declare matti-list)

(defmulti instantiate (fn [_ cell & _]
                      (schema-type cell)))

(defmethod instantiate :docgen
  [{:keys [state path]} {:keys [schema id]} & wrap-label?]
  (let [schema-name (:docgen schema)
        options     {:state  state
                     :path   (path/extend path id)
                     :schema (service/schema schema-name)}
        editing?    (path/meta? options :editing? )]
    (cond->> options
      editing?       docgen/docgen-component
      (not editing?) docgen/docgen-view
      wrap-label?    (docgen/docgen-label-wrap options ))))

(defmethod instantiate :default
  [{:keys [state path]} {:keys [schema id] :as cell} & wrap-label?]
  (let [cell-type    (schema-type cell)
        schema-value (-> schema vals first)
        options      {:state  state
                      :path   (path/extend path id (:id schema-value))
                      :schema schema-value}]
    ;; TODO: label wrap
    ((case cell-type
       :list matti-list) options)))


(defn matti-list [{:keys [schema state path] :as options}]
  [:div
   ;; TODO: label wrap
   [:h4 (path/loc path)]
   (for [item (:items schema)]
     (instantiate options item false))])

(defn matti-grid [{:keys [grid state path] :as options}]
  [:div {:class (str "matti-grid-" (:columns grid))}
   (for [row (:rows grid)]
     [:div.row
      (for [{:keys [col align schema id] :as cell} row]
        [:div {:class [(str "col-" (or col 1))
                       (when align (str "col--" (name align)))]}
         (when schema
           (instantiate options cell true))])])])

(rum/defc section-buttons < rum/reactive
  [{:keys [state path] :as options}]
  (when (path/latest path state :_meta :can-edit?)
    (let [editing-fn #(swap! (path/state (path/extend path :_meta :editing?)
                                         state)
                             not)]
      [:div.matti-section__buttons
       (when (path/meta? options :editing?)
         [:button.matti-section-button.ghost
          {:on-click editing-fn}
          (common/loc "close")])
       (when-not (path/meta? options :editing?)
         [:button.matti-section-button.ghost
          {:on-click editing-fn}
          (common/loc "matti.edit")])
       (when (path/meta? options :can-remove?)
         [:button.matti-section-button.ghost (common/loc "remove")])])))

(rum/defc section < rum/reactive
  [{:keys [state path id] :as options}]
  [:div.matti-section
   (section-buttons options)
   (matti-grid options)])
