(ns lupapalvelu.ui.map-wrapper
  "Pure React loader for ApplicationMap, which is loaded as separate
  Shadow-cljs module."
  (:require ["react" :as react]
            [lupapalvelu.ui.common :as common]
            [reagent.core :as r]
            [shadow.lazy :as shadow-lazy]))

(def Map
  (react/lazy
    (fn []
      #_{:clj-kondo/ignore [:unresolved-namespace]}
      (-> (shadow-lazy/load (shadow-lazy/loadable lupapalvelu.ui.map-wrapper.impl/ApplicationMap))
          (.then (fn [m]
                   #js {:default m}))))))

(defn ^:export LazyApplicationMap [^js props]
  (react/createElement react/Suspense
                       #js {:fallback (r/as-element [:div [:i.icon-spin.lupicon-refresh]
                                                     " "
                                                     (common/loc :loading)])}
                       (react/createElement Map props)))
