(ns lupapalvelu.ui.matti.sections
  (:require [clojure.string :as s]
            [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.layout :as layout]
            [rum.core :as rum]))


(rum/defc remove-section-checkbox < rum/reactive
  [{:keys [state schema]}]
  (let [path       [:removed-sections (:id schema)]
        state*     (path/state path state)
        handler-fn (fn [flag]
                     (reset! state* flag)
                     (path/meta-updated {:path  path
                                         :state state}))]
    (components/checkbox {:label      "matti.template-removed"
                          :value      (rum/react state*)
                          :handler-fn handler-fn
                          :disabled   false ;; TODO: _meta
                          :negate?    true})))

(defn template-section-header
  [{:keys [state dictionary schema] :as options}]
  [:div.section-header.matti-grid-2
   [:div.row.row--tight
    [:div.col-1
     [:span.row-text.section-title (path/new-loc options)]]
    [:div.col-1.col--right
     (if (contains? (-> dictionary :removed-sections :keymap)
                    (keyword (:id schema)))
       [:span
        (remove-section-checkbox options)]
       [:span.row-text (common/loc :matti.always-in-verdict)])]]])

(rum/defc section < rum/reactive
  {:key-fn path/key-fn}
  [{:keys [schema state] :as options} & [header-fn]]
  [:div.matti-section
   {:class (path/css options)}
   ((or header-fn template-section-header) options)
   (when-not (path/react [:removed-sections (:id schema)] state)
     [:div.section-body
      (layout/matti-grid (path/schema-options options
                                              (:grid schema)))])])
