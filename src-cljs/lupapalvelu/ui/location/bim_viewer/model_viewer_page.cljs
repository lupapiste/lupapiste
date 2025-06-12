(ns lupapalvelu.ui.location.bim-viewer.model-viewer-page
  "Page for viewing one or more BIM files in the same fullscreen viewer.
  The viewer is on a separate page and gets the list of shown models from the URL;
  the models must be from a single application and the user must still have permission to view the models of
  that application."
  (:require [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.lazy :as lazy]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [shadow.lazy :as shadow-lazy]))

(rf/reg-event-fx ::exit-model-viewer
  ;; Goes back to the application tab where the models are listed when exiting fullscreen viewer
  (fn [_ [_ application-id]]
    {:fx [[:dispatch [:application/open application-id :suffix :structures]]]}))

(rf/reg-sub ::app-id
  :<- [:route/path-parameters]
  :-> :app-id)

(rf/reg-sub ::ifc-version-ids
  :<- [:route/path-parameters]
  :-> (comp #(ss/split % #",") :ifc-version-ids-csv))

(rf/reg-sub ::ifc-info
  :<- [::app-id]
  :<- [::ifc-version-ids]
  (fn [[app-id ifc-version-ids] _]
    {:application-id  app-id
     :ifc-version-ids ifc-version-ids
     :default-values  {:fullscreen-exit-fn #(>evt [::exit-model-viewer app-id])}}))

(defn page
  "Use Shadow CLJS's and React's lazy loading for the BIM viewer to break it off from the rest of the UI code
  and therefore allowing the 2 MB xeokit-sdk library it uses to be loaded only when needed"
  []
  [lazy/lazy-component
   #_{:clj-kondo/ignore [:unresolved-namespace]}
   {:loadable (shadow-lazy/loadable lupapalvelu.ui.location.bim-viewer.viewer/bim-viewer)
    :params   [(<sub [::ifc-info])]}])

(defonce mounter (ui-dom/init :reagent (fn [] page)))

(defn ^:export start [dom-id _]
  (ui-dom/mount! mounter dom-id))
