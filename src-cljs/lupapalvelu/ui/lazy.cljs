(ns lupapalvelu.ui.lazy
  "A wrapper component for lazily loading UI components that are attached to large Shadow CLJS modules and
  only rarely needed; the lazy loading of them allows the JS to only be loaded by users who need it."
  (:require [lupapalvelu.ui.common :as common]
            [reagent.core :as r]
            [shadow.lazy :as shadow-lazy]))

(defn lazy-component
  "A wrapper for a lazily loaded UI component.
  Opts:
  loadable      The Shadow-CLJS loadable component
                E.g. (shadow-lazy/loadable lupapalvelu.ui.location.bim-viewer.viewer/bim-viewer)
                Note that there should be no :require for the namespace, and therefore
                 - the resulting error should be disabled with #_{:clj-kondo/ignore [:unresolved-namespace]}
                 - the namespace should be defined as its own module in `shadow-cljs.edn`
  placeholder   [Optional] A hiccup form that should be displayed while the component is being loaded
  params        [Optional] Parameters (vector/collection) that should be given to the component once it is loaded"
  [{:keys [loadable placeholder params]}]
  (r/with-let [loaded (r/atom (shadow-lazy/ready? loadable))
               _ (-> (shadow-lazy/load loadable)
                     (.then (fn [_loaded-component]
                              (reset! loaded true))))]
    (if @loaded
      (into [@loadable] params)
      (or placeholder
          [:div
           [:i.icon-spin.lupicon-refresh]
           " "
           (common/loc :applications.processing)]))))
