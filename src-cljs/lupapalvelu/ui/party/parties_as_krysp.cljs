(ns lupapalvelu.ui.party.parties-as-krysp
  (:require ["react" :as react]
            [goog.object :as googo]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.party.designers-warning :as designers]
            [re-frame.core :as rf]))

(rf/reg-event-fx ::enabled
  (fn [{:keys [db]} [_ enabled?]]
    {:db (assoc db ::enabled? enabled?)}))

(rf/reg-sub ::enabled?
  :-> (comp boolean ::enabled?))

(defn- subscribe!
  [^js observable on-change]
  (react/useEffect
    (fn []
      (let [^js sub (.subscribe observable on-change)]
        (on-change (.peek observable))
        (fn []
          (.dispose sub))))
    #js [observable]))

(defn- confirmation-dialog
  [send-parties warnings]
  [:<>
   [:span (common/loc :application.designers-not-approved-help)]
   (->> warnings
        (map (fn [text]
               [:li text]))
        (into [:ul]))
   [:div.button-group
    [:button.positive.btn-dialog
     {:on-click (fn [_]
                  (send-parties)
                  (>evt [:dialog/close]))}
     (common/loc :continue)]
    [:button.secondary.btn-dialog.close
     {:on-click (fn [_]
                  (>evt [:dialog/close]))}
     (common/loc :button.cancel)]]])

(rf/reg-event-fx ::send-parties-as-krysp-clicked
  (fn [_ [_ send-parties warnings]]
    {:fx [[:dialog/show {:ltitle           :application.designers-not-approved
                         :size             :medium
                         :type             :react
                         :dialog-component [confirmation-dialog send-parties warnings]
                         :cancel-callback  (fn [_]
                                             (>evt [:dialog/close]))}]]}))

(defn- view*
  [{:keys [enabled-obs send-parties]}]
  (subscribe! enabled-obs
              (fn [enabled?]
                (>evt [::enabled enabled?])))
  (when (<sub [:auth/application? :parties-as-krysp])
    (let [warnings (<sub [::designers/non-approved-designers-texts])]
      [:button.positive.caps
       {:title    (common/loc :tooltip.partiesAsKrysp)
        :disabled (not (<sub [::enabled?]))
        :on-click (fn [_]
                    (if (seq warnings)
                      (>evt [::send-parties-as-krysp-clicked send-parties warnings])
                      (send-parties)))}
       [:i.lupicon-circle-arrow-right
        {:aria-hidden true}]
       [:span
        (common/loc :application.partiesAsKrysp)]])))

(defn- view [args]
  [:f> view* args])

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id params]
  (ui-dom/mount! mounter dom-id {:dom-id       dom-id
                                 :enabled-obs  (googo/get params "enabledObs")
                                 ;; See `sendParties` in `application-model.js`
                                 :send-parties (googo/get params "sendParties")}))
