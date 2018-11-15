(ns lupapalvelu.ui.auth-admin.prices.catalogue
  (:require [cljs-time.format :as tformat]
            [cljs-time.coerce :as tcoerce]
            [lupapalvelu.ui.auth-admin.prices.service :as service]
            [lupapalvelu.ui.auth-admin.prices.state :as state]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.invoices.shared.util :as util]
            [rum.core :as rum]
            [schema.core :as sc]))

(def invoice-date-formatter (tformat/formatter "dd.MM.yyyy"))

(defn ->date-str [timestamp]
  (when timestamp
    (->> timestamp
         tcoerce/from-long
         (tformat/unparse invoice-date-formatter))))

(defn format-catalogue-name-in-select [catalogue]
  (str
   (->date-str (:valid-from catalogue))
   " - "
   (->date-str (:valid-until catalogue))
   (if (= "draft" (:state catalogue)) (loc "price-catalogue.draft"))))

(rum/defc CatalogueSelect < rum/reactive
  [catalogues selected-catalogue-id]
  (uc/select state/set-selected-catalogue-id
             "catalogue-select"
             (rum/react selected-catalogue-id)
             (cons ["" (loc "choose")]
                   (map (juxt :id format-catalogue-name-in-select)
                        (rum/react catalogues)))
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
  [:div
   [:table {:class-name "invoice-operations-table"}
     [:thead
      [:tr
       [:th operation]
       [:th (loc "price-catalogue.unit-price")]
       [:th (loc "price-catalogue.discount-percent")]
       [:th (loc "price-catalogue.minimum")]
       [:th (loc "price-catalogue.maximum")]
       [:th (loc "price-catalogue.unit")]]]
    [:tbody
     (map OperationCatalogueRow operation rows)]]])

(rum/defc CatalogueByOperations [selected-catalogue]
  (let [rows-by-operation (util/rows-by-operation selected-catalogue)]
    [:div
     (for [[operation rows] rows-by-operation]
       (OperationTable operation rows))]))

(rum/defc RowOperation
  < {:key-fn (fn [operation] (str "operation-" operation))}
  [operation]
  [:span {:style {:backgroundColor "#f39129"
                  :box-sizing "border-box"
                  :color "white"
                  :display "inline-block"
                  :margin-right "0.5em"
                  :padding-right "0.5em"
                  :padding-left "1em"}} (str operation " ")])

(rum/defc RowOperations [operations]
  [:div
   (map RowOperation operations)])

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
       [:th (loc "price-catalogue.code")]
       [:th (loc "price-catalogue.product")]
       [:th (loc "price-catalogue.unit-price")]
       [:th (loc "price-catalogue.discount-percent")]
       [:th (loc "price-catalogue.minimum")]
       [:th (loc "price-catalogue.maximum")]
       [:th (loc "price-catalogue.unit")]
       [:th (loc "price-catalogue.row-operations")]]]
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
     [:span {:style {:padding-right "1em"}} (loc "price-catalogue.show")]
     [:button  {:style row-button-style
                :on-click (fn [] (state/set-view :by-rows))}
      [:span (loc "price-catalogue.by-product-rows")]]

     [:button {:style operation-button-style
               :on-click (fn [] (state/set-view :by-operations))}
      [:span (loc "price-catalogue.by-operations")]]]))

(rum/defc Catalogue < rum/reactive
  [_]
  (let [selected-catalogue (state/get-catalogue (rum/react state/selected-catalogue-id))
        view (rum/react state/view)]
    [:div
     [:div {:style {:margin-bottom "2em"}}
      (CatalogueSelect state/catalogues
                       state/selected-catalogue-id)
      ;;TODO add new taksa button here to the right side
      ]
     [:div (if selected-catalogue (ViewSwitch))]
     [:div
      (cond
        (not selected-catalogue) nil
        (= view :by-rows) (CatalogueByRows selected-catalogue)
        (= view :by-operations) (CatalogueByOperations selected-catalogue))]]))

(defonce args (atom {}))

(def log (.-log js/console))

(defn mount-component []
  (rum/mount (Catalogue)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (reset! state/org-id (js/ko.unwrap (common/oget componentParams "orgId")))
  (service/fetch-price-catalogues)
  (state/set-view :by-rows)
  (mount-component))
