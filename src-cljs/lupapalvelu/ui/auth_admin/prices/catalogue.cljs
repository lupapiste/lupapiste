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

(rum/defc catalogue-select < rum/reactive
  [catalogues selected-catalogue-id]
  (uc/select state/set-selected-catalogue-id
             "catalogue-select"
             (rum/react selected-catalogue-id)
             (cons ["" (loc "choose")]
                   (map (juxt :id format-catalogue-name-in-select)
                        (rum/react catalogues)))
             "dropdown"))

(rum/defc operation-catalogue-row
  < {:key-fn (fn [operation row] (str operation))}
  [operation {:keys [text price-per-unit discount-percent min-total-price max-total-price unit] :as row}]
  [:tr
   [:td text]
   [:td price-per-unit]
   [:td discount-percent]
   [:td min-total-price]
   [:td max-total-price]
   [:td unit]])

(rum/defc operation-table
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
     (map operation-catalogue-row operation rows)]]])

(rum/defc catalogue-by-operations [selected-catalogue]
  (let [rows-by-operation (util/rows-by-operation selected-catalogue)]
    [:div
     (for [[operation rows] rows-by-operation]
       (operation-table operation rows))]))

(rum/defc row-operation
  < {:key-fn (fn [operation] (str "operation-" operation))}
  [operation]
  [:span.row-operation
   (str operation " ")])

(rum/defc row-operations [operations]
  [:div
   (map row-operation operations)])

(rum/defc catalogue-row
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
   [:td (row-operations operations)]])

(rum/defc catalogue-by-rows [selected-catalogue]
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
     (map catalogue-row (:rows selected-catalogue))]]])

(rum/defc view-switch  < rum/reactive
  [_]
  (let [view (rum/react state/view)]
    [:div.view-switch
     [:span.view-switch-guide-text
      (loc "price-catalogue.show")]
     [:button  {:className (if  (= view :by-rows)
                             "view-switch-selected"
                             "view-switch-unselected")
                :on-click (fn [] (state/set-view :by-rows))}
      [:span (loc "price-catalogue.by-product-rows")]]

     [:button {:className (if  (= view :by-operations)
                            "view-switch-selected"
                            "view-switch-unselected")
               :on-click (fn [] (state/set-view :by-operations))}
      [:span (loc "price-catalogue.by-operations")]]]))

(rum/defc catalogue < rum/reactive
  [_]
  (let [selected-catalogue (state/get-catalogue (rum/react state/selected-catalogue-id))
        view (rum/react state/view)]
    [:div.price-catalogue
     [:div.catalogue-select-wrapper
      (catalogue-select state/catalogues state/selected-catalogue-id)
      ;;TODO add new taksa button here to the right side
      ]
     [:div (if selected-catalogue (view-switch))]
     [:div
      (cond
        (not selected-catalogue) nil
        (= view :by-rows) (catalogue-by-rows selected-catalogue)
        (= view :by-operations) (catalogue-by-operations selected-catalogue))]]))

(defonce args (atom {}))

(def log (.-log js/console))

(defn mount-component []
  (rum/mount (catalogue)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (reset! state/org-id (js/ko.unwrap (common/oget componentParams "orgId")))
  (service/fetch-price-catalogues)
  (state/set-view :by-rows)
  (mount-component))
