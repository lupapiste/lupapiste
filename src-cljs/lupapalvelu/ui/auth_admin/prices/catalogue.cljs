(ns lupapalvelu.ui.auth-admin.prices.catalogue
  (:require [rum.core :as rum]
            [lupapalvelu.ui.auth-admin.prices.service :as service]
            [lupapalvelu.ui.auth-admin.prices.state :as state]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.invoices.shared.util :as util]
            [schema.core :as sc]))

(rum/defc CatalogueSelect < rum/reactive
  [catalogues selected-catalogue-id]
  (uc/select state/set-selected-catalogue-id
             "catalogue-select"
             (rum/react selected-catalogue-id)
             (cons ["" (loc "choose")]
                   (map (juxt :id :id) (rum/react catalogues)))
             "dropdown"))

(rum/defc OperationCatalogueRow
  < {:key-fn (fn [operation row] (str operation))}
  [operation {:keys [text price-per-unit discount-percent min-total-price max-total-price unit] :as row}]
  [:tr
   [:td text]
   [:td price-per-unit]
   [:td discount-percent]
   [:td min-total-price]
   [:td max-total-price]
   [:td unit]])

(rum/defc OperationTable
  < {:key-fn (fn [operation rows] (str operation))}
  [operation rows]
  [:div operation " no of rows " (count rows)]
  [:div
   [:table {:class-name "invoice-operations-table"}
     [:thead
      [:tr
       [:th operation]
       [:th "A-hinta"]
       [:th "Ale %"]
       [:th "Vähintään"]
       [:th "Enintään"]
       [:th "Yksikkö"]]]
    [:tbody
     (map OperationCatalogueRow operation rows)]]])

(rum/defc CatalogueByOperations [selected-catalogue]
  (let [rows-by-operation (util/rows-by-operation selected-catalogue)]
    [:div
     (println "rows-by-operation: " )
     (for [[operation rows] rows-by-operation]
       (OperationTable operation rows))]))

(rum/defc RowOperation
  < {:key-fn (fn [operation] (str "operation-" operation))}
  [operation]
  [:span (str operation " ")])

(rum/defc RowOperations [operations]
  [:div
   (map (fn [operation]
          (RowOperation operation))
        operations)])

(rum/defc CatalogueRow
  < {:key-fn (fn [row] (str (:code row) "-" (:text row)))}
  [{:keys [code text price-per-unit discount-percent min-total-price max-total-price unit operations] :as row}]
  [:tr
   [:td code]
   [:td text]
   [:td price-per-unit]
   [:td discount-percent]
   [:td min-total-price]
   [:td max-total-price]
   [:td unit]
   [:td (RowOperations operations)]])

(rum/defc CatalogueByRows [selected-catalogue]
  [:div
   [:table
     [:thead
      [:tr
       [:th "Koodi"]
       [:th "Tuote"]
       [:th "A-hinta"]
       [:th "%"]
       [:th "Vähintään"]
       [:th "Enintään"]
       [:th "Yksikkö"]
       [:th "Kuuluu toimenpiteisiin"]]]
    [:tbody
     (map CatalogueRow (:rows selected-catalogue))]]])

(rum/defc ViewSwitch  < rum/reactive
  [_]
  (let [view (rum/react state/view)
        selected-style {:backgroundColor "#f39129"
                        :color "white"
                        :border "1px solid #f39129"}
        unselected-style {:backgroundColor "white"
                          :color "#f39129"
                          :border "1px solid #f39129"}
        row-button-style (if  (= view :by-rows) selected-style unselected-style)
        operation-button-style (if  (= view :by-operations) selected-style unselected-style)]
    [:div {:style {:margin-bottom "2em"}}
     [:span {:style {:padding-right "1em"}} "Näytä:"]
     [:button  {:style row-button-style
                :on-click (fn [] (state/set-view :by-rows))}
      [:span "Tuoteriveittäin"]]

     [:button {:style operation-button-style
               :on-click (fn [] (state/set-view :by-operations))}
      [:span "Toimenpiteittäin"]]]))

(rum/defc Catalogue < rum/reactive
  [_]
  (let [selected-catalogue (state/get-catalogue (rum/react state/selected-catalogue-id))
        view (rum/react state/view)]
    [:div
     ;; [:div (str "selected-catalogue: " selected-catalogue)]
     ;; [:div (str "selected-catalogue-id: " (rum/react state/selected-catalogue-id))]
     ;;[:h2 (loc "price-catalogue.tab.title")]
     [:div {:style {:margin-bottom "2em"}}
      (CatalogueSelect state/catalogues
                        state/selected-catalogue-id)
      ;;TAHAN EHKA PVM
      ;;TAHAN UUSI taksa nappi oikeaan laitaan
      ]
     [:div (ViewSwitch)]

     [:div
      (if (= view :by-rows)
        (CatalogueByRows selected-catalogue)
        (CatalogueByOperations selected-catalogue))]]))

(defonce args (atom {}))

(def log (.-log js/console))

(defn mount-component []
  (rum/mount (Catalogue)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (println "catalogue start")
  (swap! args assoc :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (reset! state/org-id (js/ko.unwrap (common/oget componentParams "orgId")))
  (service/fetch-price-catalogues)
  (state/set-view :by-rows)
  (mount-component))
