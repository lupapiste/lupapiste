(ns lupapalvelu.ui.inspection-summaries
  (:require [rum.core :as rum]
            [clojure.string :as string]
            [lupapalvelu.ui.common :refer [query command]]))

(enable-console-print!)

(defn find-by-key
  "Return item from sequence col of maps where element k (keyword) matches value v."
  [k v col]
  (some (fn [m] (when (= v (get m k)) m)) col))

(defn save-indicator [visible-atom]
  [:span.form-indicator.form-input-saved
   {:style {:display (when-not (rum/react visible-atom) "none")}}
   [:span.icon]])

(def empty-state {:applicationId ""
                  :operations []
                  :summaries []
                  :templates []
                  :view {:bubble-visible false
                         :new {:operation nil
                               :template nil}
                         :summary {:id nil
                                   :targets []}}})

(def state         (atom empty-state))
(def table-rows    (rum/cursor-in state [:view :summary :targets]))

(rum/defc summary-row [row-data]
  [:tr
   [:td ""]
   [:td
    {:data-test-id (str "target-name-" (:target-name row-data))}
    (:target-name row-data)]
   [:td ""]
   [:td ""]
   [:td ""]
   [:td ""]])

(defn- refresh [cb]
  (query "inspection-summaries-for-application"
         (fn [data]
           (swap! state assoc
                  :operations (:operations data)
                  :templates  (:templates data)
                  :summaries  (:summaries data))
           (when cb
             (cb)))
         "id" (-> @state :applicationId)))

(defn init
  [init-state props]
  (let [[ko-app auth-model] (-> (aget props ":rum/initial-state") :rum/args)
        id-computed (aget ko-app "id")
        id          (id-computed)]
    (swap! state assoc :applicationId id)
    (when (and (not (empty? id)) (.ok auth-model "inspection-summaries-for-application"))
      (refresh nil))
    init-state))

(defn- operation-description-for-select [op]
  (string/join " - " (remove empty? [(:description op) (:op-identifier op)])))

(defn- update-summary-view [id]
  (->> (:summaries @state)
       (find-by-key :id id)
       (swap! state assoc-in [:view :summary])))

(rum/defc select [change-fn data-test-id value options]
  [:select.form-entry.is-middle
   {:on-change #(change-fn (.. % -target -value))
    :data-test-id data-test-id
    :value     value}
   (map (fn [[k v]] [:option {:value k} v]) options)])

(rum/defc operations-select [operations selection]
  (select #(swap! state assoc-in [:view :new :operation] %)
          "operations-select"
          selection
          (cons
            [nil (js/loc "choose")]
            (map (fn [op] (let [op-name        (js/loc (str "operations." (:name op)))
                                op-description (operation-description-for-select op)]
                            [(:id op) (str op-description " (" op-name ") ")])) operations))))

(rum/defc summaries-select [summaries operations selection]
  (select update-summary-view
          "summaries-select"
          selection
          (cons
            [nil (js/loc "choose")]
            (map (fn [s] (let [operation      (find-by-key :id (-> s :op :id) operations)
                               op-description (operation-description-for-select operation)]
                           [(:id s) (str (:name s) " - " op-description)])) summaries))))

(rum/defc templates-select [templates selection]
  (select #(swap! state assoc-in [:view :new :template] %)
          "templates-select"
          selection
          (cons
            [nil (js/loc "choose")]
            (map (fn [tmpl] [(:id tmpl) (:name tmpl)]) templates))))

(rum/defc create-summary-bubble < rum/reactive
  [visible?]
  (let [visibility (rum/react visible?)]
    [:div.container-bubble.half-width.arrow-2nd-col
     {:style {:display (when-not visibility "none")}}
     [:div.row
      [:label (js/loc "inspection-summary.new-summary.intro.1")]
      [:br]
      [:label (js/loc "inspection-summary.new-summary.intro.2")]]
     [:div.row
      [:label (js/loc "inspection-summary.new-summary.operation")]
      [:div.col-4.no-padding
       [:span.select-arrow.lupicon-chevron-small-down
        {:style {:z-index 10}}]
       (operations-select (rum/react (rum/cursor-in state [:operations]))
                          (rum/react (rum/cursor-in state [:view :new :operation])))]]
     [:div.row
      [:label (js/loc "inspection-summary.new-summary.template")]
      [:div.col-4.no-padding
       [:span.select-arrow.lupicon-chevron-small-down
        {:style {:z-index 10}}]
       (templates-select (rum/react (rum/cursor-in state [:templates]))
                         (rum/react (rum/cursor-in state [:view :new :template])))]]
     [:div.row.left-buttons
      [:button.positive
       {:on-click (fn [_] (command "create-inspection-summary"
                                   (fn [result]
                                     (println "command result" result)
                                     (refresh #(update-summary-view (:id result)))
                                     (reset! visible? false))
                                   "id"          (-> @state :applicationId)
                                   "operationId" (-> @state :view :new :operation)
                                   "templateId"  (-> @state :view :new :template)))}
       [:i.lupicon-check]
       [:span (js/loc "button.ok")]]
      [:button.secondary
       {:on-click (fn [_] (reset! visible? false))}
       [:i.lupicon-remove]
       [:span (js/loc "button.cancel")]]]]))

(rum/defc inspection-summaries < rum/reactive
                                 {:init init
                                  :will-unmount (fn [& _] (reset! state empty-state))}
  [ko-app auth-model]
  (let [summary-in-view (rum/react (rum/cursor-in state [:view :summary]))
        bubble-visible (rum/cursor-in state [:view :bubble-visible])]
    [:div
     [:h1 (js/loc "inspection-summary.tab.title")]
     [:div
      [:label (js/loc "inspection-summary.tab.intro.1")]
      [:br]
      [:label (js/loc "inspection-summary.tab.intro.2")]
      [:br]
      [:label (js/loc "inspection-summary.tab.intro.3")]]
     [:div.form-grid.no-top-border.no-padding
      [:div.row
       [:div.col-1
        [:div.col--vertical
         [:label (js/loc "inspection-summary.select-summary.label")]
         [:span.select-arrow.lupicon-chevron-small-down
          {:style {:z-index 10}}]
         (summaries-select (rum/react (rum/cursor-in state [:summaries]))
                           (rum/react (rum/cursor-in state [:operations]))
                           (:id summary-in-view))]]
       (if (.ok auth-model "create-inspection-summary")
         [:div.col-1.create-new-summary-button
          [:button.positive
           {:on-click (fn [_] (reset! bubble-visible true))}
           [:i.lupicon-circle-plus]
           [:span (js/loc "inspection-summary.new-summary.button")]]])]
      (if (.ok auth-model "create-inspection-summary")
        [:div.row.create-summary-bubble (create-summary-bubble bubble-visible)])
      [:div.row
       [:table
        {:id "targets-table"}
        [:thead
         [:tr
          [:th (js/loc "inspection-summary.targets.table.state")]
          [:th (js/loc "inspection-summary.targets.table.target-name")]
          [:th (js/loc "inspection-summary.targets.table.attachment")]
          [:th (js/loc "inspection-summary.targets.table.date")]
          [:th (js/loc "inspection-summary.targets.table.marked-by")]
          [:th ""]]]
        [:tbody
         (doall
           (for [row (rum/react table-rows)]
             (summary-row row)))]]]
      [:div.row
       [:button.positive
        {:on-click (fn [_] (swap! table-rows conj {:target-name "Faas"}))}
        [:i.lupicon-circle-plus]
        [:span (js/loc "inspection-summary.targets.new.button")]]]]]))

(defn ^:export start [domId componentParams]
  (rum/mount (inspection-summaries (aget componentParams "app") (aget componentParams "authModel"))
             (.getElementById js/document (name domId))))
