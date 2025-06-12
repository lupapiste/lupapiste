(ns lupapalvelu.ui.dmcity.states-tab
  "The states tab for the application page which lists the state change updates received from the dmCity integration"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]))

(rf/reg-sub ::states :-> ::states)

(rf/reg-sub ::last-modified
  (fn [db _]
    (some->> (::last-modified db)
             js/util.finnishDate
             (str (common/loc :applications.lastModified) " "))))

(rf/reg-event-fx ::fetch-external-states
  (fn [_ _]
    {:action/query {:name   :external-states
                    :params {:id (common/application-id)}
                    :success [::fetch-external-states.success]}}))

(rf/reg-event-db ::fetch-external-states.success
  (fn [db [_ {:keys [received states]}]]
    (assoc db ::last-modified received ::states states)))

(defn states-view []
  (let [last-modified (<sub [::last-modified])
        states        (<sub [::states])]
    [:div
     [:h1 (common/loc :application.tabStates)]
     ;; Last modified timestamp
     (when last-modified
        [:div.bottom-marginM last-modified])
     ;; State events table
     (if (empty? states)
       [:div (common/loc :dmcity.states.empty)]
       (->> states
            (map (fn [{:keys [date datetime enabled state]}]
                   [:tr
                    [:td (->> state (str "dmcity.external-state.") common/loc)]
                    [:td (or (some-> date js/util.finnishDate)
                             (some-> datetime js/util.finnishDateAndTime)
                             (if enabled
                               (common/loc :yes)
                               (common/loc :no)))]]))
            (into [:tbody])
            (conj [:table [:thead [:tr
                                   [:th (common/loc :dmcity.states.situation)]
                                   [:th (common/loc :dmcity.states.info)]]]])))]))

(defonce mounter (ui-dom/init :reagent (constantly states-view)))

(defn ^:export start [dom-id]
  (>evt [::fetch-external-states])
  (ui-dom/mount! mounter dom-id))
