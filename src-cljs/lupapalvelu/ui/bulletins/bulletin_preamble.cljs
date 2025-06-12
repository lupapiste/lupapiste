(ns lupapalvelu.ui.bulletins.bulletin-preamble
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.bulletins.appeals :as appeals]
            [lupapalvelu.ui.bulletins.bulletin-verdict-data :as verdict-data]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defonce args (atom {}))

(defn update-map []
  (let [{:keys [x y]} @args]
    (-> ^js/gis (:map @args)
        (.clear)
        (.updateSize)
        (.center x y)
        (.add #js {:x x :y y}))))

(defn bulletin-loaded [{:keys [bulletin]}]
  (let [[x y] (:location bulletin)]
    (>evt [::set-bulletin bulletin])
    (swap! args assoc :x x :y y)
    (update-map)))

(rf/reg-event-fx
  ::bulletin-error
  (fn [_ [_ {:keys [text]}]]
    {:dialog/show {:ltitle   :error.dialog.title
                   :ltext    text
                   :callback (fn []
                               (>evt [::set-bulletin nil])
                               (common/open-page :bulletins))}}))

(rf/reg-event-fx
  ::fetch-bulletin
  (fn [{:keys [db]} [_ bulletin-id]]
    {:action/query {:name    (if (::user-info db) :vetuma-bulletin :public-bulletin)
                    :params  {:bulletinId bulletin-id}
                    :success bulletin-loaded
                    :error   ::bulletin-error}}))

(rf/reg-event-db
  ::set-params
  (fn [db [_ params]]
    (assoc db ::params params)))

(rf/reg-event-db
  ::set-bulletin
  (fn [db [_ bulletin]]
    (assoc db ::bulletin bulletin)))

(rf/reg-sub
  ::bulletin
  :-> ::bulletin)

(rf/reg-sub
  ::appeal-enabled?
  :<- [::bulletin]
  :-> :can-appeal?)

(rf/reg-event-fx
  ::set-user-info
  (fn [{:keys [db]}]
    (let [user-info (.-userInfo ^js/LUPAPISTE.VetumaService (:vetumaService @args))]
      {:db (assoc db ::user-info {:first-name (.firstName user-info)
                                  :last-name  (.lastName user-info)
                                  :street     (.street user-info)
                                  :zip        (.zip user-info)
                                  :city       (.city user-info)})
       :fx [(let [bulletin (::bulletin db)]
              (when (or (nil? bulletin)
                        (:public? bulletin))
                [:dispatch [::fetch-bulletin (:bulletinId @args)]]))]})))

(rf/reg-sub
  ::user-info
  :-> ::user-info)

(rf/reg-event-fx
  ::start-appeal
  (fn [{db :db}]
    (if (::user-info db)
      {:scroll/id "appeal-form"}
      {:click/id "vetuma-link"})))

(defn state-title [state]
  (common/loc (str "bulletin.state." state ".title")))

(defn bulletin-map []
  (r/create-class
    {:component-did-mount (fn []
                            (swap! args assoc :map (-> js/gis
                                                       (.makeMap "bulletin-map")
                                                       (.updateSize)
                                                       (.center 404168 6693765 14)))
                            (update-map))
     :reagent-render      (fn []
                            [:div#bulletin-map.map.map-large
                             {:style {:width  "320px"
                                      :height "280px"}}])}))

(defn verdict-content []
  (let [bulletin        (<sub [::bulletin])
        user-info       (<sub [::user-info])
        appeal-enabled? (<sub [::appeal-enabled?])]
    (cond
      (not appeal-enabled?) [:div
                             [verdict-data/verdict-data bulletin]
                             [verdict-data/doc-download-link bulletin]]
      user-info [:<>
                 [verdict-data/detailed-verdict-data bulletin]
                 [verdict-data/doc-download-link bulletin]
                 [appeals/appeal-form (:bulletinId @args) user-info]]
      :else [:div
             [verdict-data/verdict-data bulletin]
             [verdict-data/doc-download-link bulletin]
             [verdict-data/identification-link bulletin]])))

(defn appeal-button [bulletin]
  (let [appeal-enabled?   (<sub [::appeal-enabled?])
        appeal-submitted? (<sub [::appeals/submitted? (:id bulletin)])]
    (when (and appeal-enabled?
               (not appeal-submitted?))
      (components/icon-button {:class    :primary
                               :on-click #(>evt [::start-appeal])
                               :icon     :lupicon-comment
                               :text-loc :bulletin.actions.appeal}))))

(defn bulletin-preamble []
  (r/with-let [_ (>evt [::hub/subscribe "vetumaService::authStatusChanged" #(>evt [::set-user-info]) ::automatic-hubscription])]
    (let [bulletin          (<sub [::bulletin])
          {:keys [address primaryOperation propertyId bulletinOpDescription
                  markup? municipality _applicantIndex
                  note]}    bulletin]
      [:div
       [:div.application_summary
        [:div.container
         [:a.btn.btn-primary
          {:href "#"}
          [:i.lupicon-chevron-small-left]
          [:span (common/loc :back)]]
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
               (common/loc (str "municipality." municipality))]]

             (when (seq _applicantIndex)
               [:div
                [:p (common/loc :bulletin.applicants)]
                (into [:ul]
                      (for [applicant _applicantIndex]
                        [:li applicant]))])])

          [:div.application-map-container
           [bulletin-map]]
          [:div.application_actions.stacked
           {:data-test-id "bulletin-actions"}
           [:div.flex--column.flex--gap1
            [appeal-button bulletin]]]]
         [:div {:style {:clear :both}}]]]
       [verdict-content]])
    (finally (>evt [::hub/unsubscribe ::automatic-hubscription]))))

(defonce mounter (ui-dom/init :reagent (constantly bulletin-preamble)))

(defn ^:export start [domId componentParams]
  (reset! args
          {:bulletinId    (aget componentParams "bulletinId")
           :vetumaService (aget componentParams "vetumaService")})
  (>evt [::fetch-bulletin (:bulletinId @args)])
  (ui-dom/mount! mounter domId))
