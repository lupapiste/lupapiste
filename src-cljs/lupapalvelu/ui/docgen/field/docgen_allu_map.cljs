(ns lupapalvelu.ui.docgen.field.docgen-allu-map
  (:require ["react" :as react]
            [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.docgen.feature.application :as application]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [schema.core :as sc]
            [sade.util :as su]))

(def default-zoom 14)

(defn- map-view
  "Functional component for the allu map view using React hooks."
  [{:keys [map-id]}]
  (let [map-ref (react/useRef nil)
        {:keys [x y] :as location} (<sub [::application/location])
        drawings (<sub [::application/drawings])]

    ;; Initialize map when component mounts
    (react/useEffect
      (fn []
        (when (and location ^js (not (.-current map-ref)))
          (let [{:keys [x y]} location
                gis-map (js/gis.makeMap map-id #js {:zoomWheelEnabled false})]
            (.updateSize gis-map)
            (.center gis-map x y default-zoom)
            (set! (.-current map-ref) gis-map)))

        ;; Cleanup function
        (fn []
          (when-let [^js m (.-current map-ref)]
            (.destroy m)
            (set! (.-current map-ref) nil))))
      #js [map-id])

    ; If application location changes center the existing map to the new location
    (react/useEffect
     (fn []
       (when (and x y)
         (when-let [^js m (.-current map-ref)]
           (.center m x y default-zoom)))
       js/undefined)
     #js [x y])

    ;; Update drawings when they change
    (react/useEffect
      (fn []
        (when-let [^js m (.-current map-ref)]
          (.clear m)
          (when (seq drawings)
            (.drawDrawings m
                           (clj->js drawings)
                           #js {} ;; not sure what this param is
                           #js {:fillColor "#3CB8EA"
                                :fillOpacity 0.35
                                :strokeColor "#0000FF"
                                :pointRadius 6})))
        js/undefined)
      #js [drawings])

    ;; Listen for locate drawing events
    (react/useEffect
      (fn []
        (let [handler (fn [msg]
                        (when-let [^js m (.-current map-ref)]
                          (when (= (:mapId msg) map-id)
                            (when-let [drawing (->> drawings
                                                    (su/filter-by-key :id (:drawingId msg))
                                                    first)]
                              (.centerOnDrawing m (clj->js drawing))))))
              hub-key (keyword "allu-map-locate" map-id)]
          (>evt [::hub/subscribe "allu-map-locate" handler hub-key])

          ;; Cleanup function
          (fn []
            (>evt [::hub/unsubscribe hub-key]))))
      #js [map-id drawings])

    [:div.allu-map-container
     [:div.docgen-allu-map-view ; use different class so the knockout component doesn't target it as well
      {:id map-id}]]))

(sc/defn render
  [{:keys [schema]} :- schemas/DocgenFieldArguments]
  (let [map-id (or (:mapId schema) "allu-map")]
    [:f> map-view {:map-id map-id}]))
