(ns lupapalvelu.ui.matti.sections
  (:require [clojure.string :as s]
            [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.layout :as layout]
            [rum.core :as rum]))


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

(defn template-section-header
  [{:keys [state path i18nkey] :as options}]
  [:div.section-header.matti-grid-2
   [:div.row.row--tight
    [:div.col-1
     [:span.row-text.section-title (path/loc (or i18nkey path))]]
    [:div.col-1.col--right
     (if (path/meta? options :can-remove?)
       [:span
        (when-not (path/value path state :removed)
          (section-checkbox options :pdf))
        (section-checkbox options :removed {:negate? true})]
       [:span.row-text (common/loc :matti.always-in-verdict)])]]])

(rum/defc section < rum/reactive
  {:key-fn path/key-fn}
  [{:keys [state path id css] :as options} & [header-fn]]
  [:div.matti-section
   {:class (path/css options)}
   ((or header-fn template-section-header) options)
   (when-not (path/react path state :removed)
     [:div.section-body (layout/matti-grid (shared/child-schema options
                                                                :grid
                                                                options))])])
