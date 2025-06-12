(ns lupapalvelu.ui.entries.dev-reload
  (:require [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.subs :as rf-subs]))

(defonce *components (atom {}))

(defn register!
  "Individual entry-namespaces should register their views and other cljs components"
  [id components]
  (when ^boolean js/goog.DEBUG
    (swap! *components assoc id components)))

(defn ^:dev/after-load reload-hook []
  (rf-subs/clear-subscription-cache!)
  ;; (re-)mount all the cljs components/views
  (->> (deref *components)
       (mapcat val)
       (run! ui-dom/reload!)))
