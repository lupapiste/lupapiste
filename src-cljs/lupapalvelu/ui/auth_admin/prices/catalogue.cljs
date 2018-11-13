(ns lupapalvelu.ui.auth-admin.prices.catalogue
  (:require [rum.core :as rum]
            [lupapalvelu.ui.auth-admin.prices.service :as service]
            [lupapalvelu.ui.auth-admin.prices.state :as state]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.components :as uc]
            [schema.core :as sc]))

(rum/defc catalogue-select < rum/reactive
  [catalogues selected-catalogue-id]
  ;;(println "catalogue-select selected-id: " @selected-catalogue-id)
  (uc/select state/set-selected-catalogue-id ;;update-stamp-view
             "catalogue-select"
             (rum/react selected-catalogue-id)
             (cons ["" (loc "choose")]
                   (map (juxt :id :id) (rum/react catalogues)))
             "dropdown"))

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
  [{:keys [code text price-per-unit discount-percent min-total-price max-total-price unit operations]
                         :as row}]
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
   [:table {:class-name "invoice-operations-table"}
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

(rum/defc Catalogue < rum/reactive
  [_]
  (let [selected-catalogue (state/get-catalogue (rum/react state/selected-catalogue-id))]
    [:div
     ;; [:div (str "selected-catalogue: " selected-catalogue)]
     ;; [:div (str "selected-catalogue-id: " (rum/react state/selected-catalogue-id))]
     ;;[:h2 (loc "price-catalogue.tab.title")]
     [:div {:style {:margin-bottom "2em"}}
      (catalogue-select state/catalogues
                        state/selected-catalogue-id)
      ;;TAHAN EHKA PVM
      ;;TAHAN UUSI taksa nappi oikeaan laitaan
      ]
     [:div
      (CatalogueByRows selected-catalogue)
      ]]))

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
  (mount-component))
