(ns lupapalvelu.ui.bulletins.bulletin-preamble
  (:require [lupapalvelu.ui.bulletins.bulletin-verdict-data :as verdict-data]
            [lupapalvelu.ui.bulletins.state :as state]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [rum.core :as rum]))

(defonce args (atom {}))

(defn bulletin-loaded [{:keys [bulletin]}]
  (let [[x y] (:location  bulletin)]
    (reset! state/current-bulletin bulletin)
    (swap! args assoc :map  (-> js/gis
                                (.makeMap "bulletin-map")
                                (.updateSize)
                                (.center 404168 6693765 14)))
    (-> ^js/gis (:map @args)
        (.clear)
        (.updateSize)
        (.center x y)
        (.add #js {:x x :y y}))))

(defn bulletin-error [{:keys [text]}]
  (common/show-dialog {:ltitle   :error.dialog.title
                       :ltext    text
                       :callback (fn []
                                   (reset! state/current-bulletin nil)
                                   (common/open-page :bulletins))}))

(defn init
  [init-state _]
  (common/query-with-error-fn :bulletin
                              bulletin-loaded
                              bulletin-error
                              :bulletinId (:bulletinId @args))
  init-state)

(defn state-title [state]
  (common/loc (str "bulletin.state." state ".title")))

(rum/defc bulletin-preamble < rum/reactive {:init init}
  []
  (let [bulletin        (rum/react state/current-bulletin)
        {:keys [address primaryOperation propertyId bulletinOpDescription
                markup? municipality _applicantIndex
                note]}  bulletin
        appeal-enabled? (common/feature? :rakval-bulletin-appeal)]
    [:div
     [:div.application_summary
      [:div.container
       [:div.bulletin-preamble
        (when bulletin
          [:div.application_summary_info
           (when note
             [:div.gap--b4.bg--white
              [:div.danger-note (common/loc note)]])
           [:h4
            {:data-test-id "bulletin-address"}
            address]
           [:h5
            [:span
             {:data-test-id                   "bulletin-primary-operation"
              :data-test-primary-operation-id (-> primaryOperation :name)}
             (if markup?
               (components/markup-span bulletinOpDescription)
               [:span.formatted (or bulletinOpDescription
                                    (common/loc (str "operations."
                                                     (-> primaryOperation :name))))])]]

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

         ; FIXME: embed new map component here
         ; Hmm, not sure yet how to do that in CLJS, most likely something like this:
         ; [NameOfComopnent {:prop-key "value"}]
         ]
        [:div.application_actions.stacked
         {:data-test-id "bulletin-actions"}]]
       [:div {:style {:clear :both}}]]]
     (cond
       (not appeal-enabled?)    [:div (verdict-data/verdict-data bulletin markup?)]
       ((:authenticated @args)) [:div (verdict-data/detailed-verdict-data bulletin markup?)]
       :else                    [:div
                                 (verdict-data/verdict-data bulletin markup?)
                                 (verdict-data/init-identification-link bulletin)])]))

(defn mount-component []
  (rum/mount (bulletin-preamble)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :bulletinId (aget componentParams "bulletinId")
                    :authenticated (aget componentParams "authenticated")
                    :dom-id (name domId))
  (mount-component))
