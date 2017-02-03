(ns lupapalvelu.ui.inspection-summaries
  (:require [rum.core :as rum]
            [clojure.string :as string]))

(enable-console-print!)

(def empty-state {:applicationId ""
                  :operations []
                  :summaries []
                  :templates []
                  :view {:bubble-visible false
                         :new {:operation nil
                               :template nil}
                         :summary {:id nil
                                   :targets []}}})

(def state      (atom empty-state))
(def table-rows (rum/cursor-in state [:view :summary :targets]))

(defn find-by-key
  "Return item from sequence col of maps where element k (keyword) matches value v."
  [k v col]
  (some (fn [m] (when (= v (get m k)) m)) col))

(rum/defc summary-row [row-data]
  [:tr
   [:td ""]
   [:td (:target-name row-data)]
   [:td ""]
   [:td ""]
   [:td ""]
   [:td ""]])

(defn update-operations [value]
  (swap! state assoc :operations value))

(defn id-subscription [value]
  (swap! state assoc :applicationId value)
  (when-not (empty? value)
    (-> (js/ajax.query "inspection-summaries-for-application"
                       (js-obj "id" value))
        (.success (fn [data]
                    (update-operations (js->clj (aget data "operations")
                                                :keywordize-keys true))
                    (swap! state assoc :summaries (js->clj (aget data "summaries")
                                                           :keywordize-keys true))))
        .call)))

(defn init
  [init-state props]
  (id-subscription ((-> (aget props ":rum/initial-state")
                                    :rum/args
                                    first
                                    (aget "id"))))
  #_(js/hub.send "XYZ" (js-obj :id (-> @state :applicationId)))
  init-state)

(defn- operation-description-for-select [op]
  (string/join " - " (remove empty? [(:description op) (:op-identifier op)])))

(defn- update-summary-view [id]
  (->> (:summaries @state)
       (find-by-key :id id)
       (swap! state assoc-in [:view :summary])))

(rum/defc select [change-fn value options]
  [:select.form-entry.is-middle
   {:on-change #(change-fn (.. % -target -value))
    :value     value}
   (map (fn [[k v]] [:option {:value k} v]) options)])

(rum/defc operations-select [operations selection]
  (select (partial (swap! state assoc-in [:view :new :operation]))
          selection
          (cons
            [nil (js/loc "choose")]
            (map (fn [op] (let [op-name        (js/loc (str "operations." (:name op)))
                                op-description (operation-description-for-select op)]
                            [(:id op) (str op-description " (" op-name ") ")])) operations))))

(rum/defc summaries-select [summaries operations selection]
  (select update-summary-view
          selection
          (cons
            [nil (js/loc "choose")]
            (map (fn [s] (let [operation      (find-by-key :id (-> s :op :id) operations)
                               op-description (operation-description-for-select operation)]
                           [(:id s) (str (:name s) " - " op-description)])) summaries))))

(rum/defc templates-select [templates selection]
  (select (partial (swap! state assoc-in [:view :new :template]))
          selection
          (cons
            [nil (js/loc "choose")]
            (map (fn [tmpl] [(:id tmpl) (:name tmpl)]) templates))))

(rum/defc inspection-summaries < rum/reactive
                                 {:init init
                                  :will-unmount (fn [& _] (reset! state empty-state))}
  [ko-app]
  (.subscribe (aget ko-app "id") id-subscription)
  (let [summary-in-view (rum/react (rum/cursor-in state [:view :summary]))
        bubble-visible (rum/cursor-in state [:view :bubble-visible])
        visibility (rum/react bubble-visible)]
    [:div
     [:h1 (js/loc "inspection-summary.tab.title")]
     [:div
      [:span (js/loc "inspection-summary.tab.intro.1")]
      [:span (js/loc "inspection-summary.tab.intro.2")]
      [:span (js/loc "inspection-summary.tab.intro.3")]]
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
       [:div.col-1
        {:style {:padding-top "24px"}}
        [:button.positive
         {:on-click (fn [_] (reset! bubble-visible true))}
         [:i.lupicon-circle-plus]
         [:span (js/loc "inspection-summary.new-summary.button")]]]]
      [:div.row
       [:div.container-bubble.half-width.arrow-2nd-col
        {:style {:display (if visibility "block" "none")}}
        [:div.row
         [:span (js/loc "inspection-summary.new-summary.intro.1")]]
        [:div.row
         [:span (js/loc "inspection-summary.new-summary.intro.2")]]
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
          {:on-click (fn [_] (reset! bubble-visible false))}
          [:i.lupicon-check]
          [:span (js/loc "button.ok")]]
         [:button.secondary
          {:on-click (fn [_] (reset! bubble-visible false))}
          [:i.lupicon-remove]
          [:span (js/loc "button.cancel")]]]]]
      [:div.row
       [:table
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
        {:on-click (fn [_] (swap! table-rows conj {:target-name "Faa"}))}
        [:i.lupicon-circle-plus]
        [:span (js/loc "inspection-summary.targets.new.button")]]]]]))

(defn ^:export start [domId ko-app]
  (rum/mount (inspection-summaries ko-app)
             (.getElementById js/document (name domId))))
