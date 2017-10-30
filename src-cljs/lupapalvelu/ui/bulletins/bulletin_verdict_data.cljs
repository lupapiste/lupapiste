(ns lupapalvelu.ui.bulletins.bulletin-verdict-data
  (:require [rum.core :as rum]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.bulletins.state :as state]
            [clojure.string :as str]))

(defonce args (atom {}))

(rum/defc verdict-data < rum/reactive []
  (let [bulletin (rum/react state/current-bulletin)]
    [:div.container
     [:div.bulletin-tab-content
      [:h3
       [:span (common/loc :application.verdict.title)]]
      [:div.spacerL
       [:div.key-value-pair
        {:style {:width "80%"}}
        [:label (common/loc :verdict.id)]
        [:span.value  (-> bulletin :verdicts first :kuntalupatunnus)]]
       [:div.key-value-pair
        {:style {:width "80%"}}
        [:label (common/loc :verdict.anto)]
        [:span.value  (common/format-timestamp (:verdictGivenAt bulletin))]]
       [:div.key-value-pair
        {:style {:width "80%"}}
        [:label (common/loc :verdict.muutoksenhaku.paattyy)]
        [:span.value  (common/format-timestamp (:appealPeriodEndsAt bulletin))]]]
      [:div.spacerL
       [:pre.wrapped_text (-> bulletin :verdictData :text)]]]]))

(defn mount-component []
  (rum/mount (verdict-data)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :bulletinId (aget componentParams "bulletinId")
         :dom-id (name domId))
  (mount-component))