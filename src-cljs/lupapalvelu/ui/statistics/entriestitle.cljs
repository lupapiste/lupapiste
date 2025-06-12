(ns lupapalvelu.ui.statistics.entriestitle
  "Pseudo component for bootstrapping entries section (Käsittelyaika) title. The
  functionality is a simple 'gateway' between backend and entries-title-service."
  (:require [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.common.hub :as hub]
            [reagent.core :as r]
            [lupapalvelu.ui.statistics.data.entriestitle :as data]))

(defn view []
  (r/with-let [_ (>evt [::data/toggle-visible true])
               _ (>evt [::hub/subscribe :application-loaded
                        #(>evt [::data/toggle-visible true])
                        ::application-loaded-hub-id])]
    (finally
      (>evt [::data/toggle-visible false])
      (>evt [::hub/unsubscribe ::application-loaded-hub-id]))))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
