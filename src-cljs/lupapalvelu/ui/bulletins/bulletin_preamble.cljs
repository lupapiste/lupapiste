(ns lupapalvelu.ui.bulletins.bulletin-preamble
  (:require [rum.core :as rum]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.bulletins.state :as state]
            [lupapalvelu.ui.bulletins.bulletin-verdict-data :as verdict-data]))

(defonce args (atom {}))

(defn bulletin-loaded [{:keys [bulletin]}]
  (let [[x y] (:location  bulletin)]
    (js/console.log "bulletin loaded")
    (reset! state/current-bulletin bulletin)
    (swap! args assoc :map  (-> js/gis
                                (.makeMap "bulletin-map")
                                (.updateSize)
                                (.center 404168 6693765 14)))
    (-> (:map @args)
        (.clear)
        (.updateSize)
        (.center x y)
        (.add #js {:x x :y y}))))

(defn init
  [init-state _]
  (common/query :bulletin
                bulletin-loaded
                :bulletinId (:bulletinId @args))
  init-state)

(defn state-title [state]
  (common/loc (str "bulletin.state." state ".title")))

(rum/defc bulletin-preamble < rum/reactive {:init init}
  []
  (let [bulletin (rum/react state/current-bulletin)
        {:keys [address primaryOperation propertyId
                bulletinOpDescription
                municipality _applicantIndex]} bulletin]
    [:div
     [:div.application_summary
      [:div.container
       [:div.bulletin-preamble
        (when bulletin
          [:div.application_summary_info
           [:h4
            {:data-test-id "bulletin-address"}
            address]
           [:h5
            [:span
             {:data-test-id "bulletin-primary-operation"
              :data-test-primary-operation-id (-> primaryOperation :name)}
             (or bulletinOpDescription (common/loc (str "operations." (-> primaryOperation :name))))]]

           [:div
            [:p (common/loc :application.property)]
            [:span.application_summary_text
             (js/util.prop.toHumanFormat propertyId)]]

           [:div
            [:p (common/loc :application.municipality)]
            [:span.application_summary_text
             (common/loc (str "municipality." municipality))]]])

        [:div.application-map-container
         [:div#bulletin-map.map.map-large
          {:style {:width  "320px"
                   :height "280px"}}]
         (when bulletin
           [:div.application-map-actions
            [:a.map-search-button
             {:on-click #(common/open-oskari-map bulletin)}
             (common/loc :map.open)]])]
        [:div.application_actions.stacked
         {:data-test-id "bulletin-actions"}
         #_[:button.function.julkipano {:data-test-id "print-bulletin"} [:i.lupicon-print [:span (common/loc :bulletin.pdf)]]]
         ]]]]
     (verdict-data/verdict-data bulletin)]))

(defn mount-component []
  (rum/mount (bulletin-preamble)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :bulletinId (aget componentParams "bulletinId")
                    :dom-id (name domId))
  (mount-component))