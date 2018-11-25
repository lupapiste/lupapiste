(ns lupapalvelu.ui.auth-admin.prices.catalogue
  (:require [cljs-time.coerce :as tcoerce]
            [cljs-time.core :as time]
            [cljs-time.format :as tformat]
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
  < {:key-fn (fn [_ row-index] row-index)}
  [{:keys [code text price-per-unit discount-percent min-total-price max-total-price unit operations] :as row} row-index]
  [:tr
   [:td code]
   [:td text]
   [:td price-per-unit]
   [:td discount-percent]
   [:td min-total-price]
   [:td max-total-price]
   [:td unit]
   [:td (row-operations operations)]])

(rum/defc field < {:key-fn (fn [row] )}
  [value on-change & [props]]
  (let [value-atom (atom value)]
    (uc/text-edit value-atom
                  (merge {:callback on-change}
                         props))))

(rum/defc unit-select < rum/reactive
  [value on-change]
  (let [value-atom (atom value)]
    (uc/dropdown value-atom
                 {:items  [{:value "kpl" :text "kpl"}
                           {:value "m2" :text "m2"}
                           {:value "m3" :text "m3"}]
                  :callback on-change
                  :choose? (not value)})))

(defn field-setter [field-name row-index & [type]]
  (fn [value]
    (let [converters {:number js/Number
                      :text str}
          convert (converters (or type :text))]
      (state/update-field-in-catalogue-in-edit! row-index field-name (convert value)))))

(rum/defc edit-catalogue-row
  < {:key-fn (fn [_ row-index] row-index)}
  [{:keys [code text price-per-unit discount-percent min-total-price max-total-price unit operations] :as row} row-index]
  [:tr
   [:td (field code             (field-setter :code row-index)  {:size "6"})]
   [:td (field text             (field-setter :text row-index) {:size "25"})]
   [:td (field price-per-unit   (field-setter :price-per-unit row-index :number) {:size "6"})]
   [:td (field discount-percent (field-setter :discount-percent row-index :number) {:size "3"})]
   [:td (field min-total-price  (field-setter :min-total-price row-index :number) {:size "6"})]
   [:td (field max-total-price  (field-setter :max-total-price row-index :number) {:size "6"})]
   [:td (unit-select unit       (field-setter :unit row-index) )]
   [:td (row-operations operations)]
   [:td [:div {:on-click (fn [] (state/remove-row! row-index))} [:i.lupicon-remove]]]])

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
     (map-indexed (fn [row-index row]
                    (catalogue-row row row-index))
                  (:rows selected-catalogue))]]])

(rum/defc edit-catalogue-by-rows < rum/reactive
  [selected-catalogue]
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
      [:th (loc "price-catalogue.row-operations")]
      [:th ""]]]
    [:tbody
     (map-indexed (fn [row-index row]
                    (edit-catalogue-row row row-index))
                  (:rows selected-catalogue))]]])

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

(rum/defc new-catalogue-button  < rum/reactive
  [_]
  (let [view (rum/react state/view)
        selected-catalogue (state/get-catalogue (rum/react state/selected-catalogue-id))
        catalogue-to-edit (or selected-catalogue {})]
    [:div.right-button
     [:button  {:className "positive"
                :on-click (fn []
                            (state/set-mode :edit)
                            (state/set-catalogue-in-edit catalogue-to-edit))}
      [:i.lupicon-circle-plus]
      [:span (str "Uusi taksa")] ;;TODO localize
      ]]))

(rum/defc cancel-button  < rum/reactive
  [_]
  [:div.right-button.catalogue-button
   [:button  {:className "primary"
              :on-click (fn []
                          (state/set-catalogue-in-edit nil)
                          (state/set-mode :show))}
    [:i.lupicon-remove]
    [:span (str "Poista")] ;;TODO localize
    ]])

(rum/defc publish-button  < rum/reactive
  [_]
  (let [catalogue-in-edit (rum/react state/catalogue-in-edit)
        now-in-millis (tcoerce/to-long (time/now))]
    [:div.right-button.catalogue-button
     [:button  {:className "positive"
                :on-click (fn []
                            (service/publish-catalogue
                             (assoc catalogue-in-edit :valid-from now-in-millis)))}
      [:i.lupicon-check]
      [:span (str "Julkaise")] ;;TODO localize
      ]]))

(rum/defc edit-buttons  < rum/reactive
  [_]
  [:div.edit-buttons
   (publish-button)
   (cancel-button)])

(rum/defc add-row-button  < rum/reactive
  [_]
  [:div.right-button
   [:button  {:className "positive"
              :on-click state/add-empty-row}
    [:i.lupicon-circle-plus]
    [:span (str "Lisää tuoterivi")] ;;TODO localize
    ]])

(defn get-render-component [mode view]
  (println "get-render-component view " view " mode: " mode)
  (let [components {:show {:by-rows catalogue-by-rows
                           :by-operations catalogue-by-operations}
                    :edit {:by-rows edit-catalogue-by-rows
                           :by-operations catalogue-by-operations}}]
    (get-in components [mode view])))

(rum/defc catalogue < rum/reactive
  [_]
  (let [view (rum/react state/view)
        mode (rum/react state/mode)
        edit? (= mode :edit)
        selected-catalogue (state/get-catalogue (rum/react state/selected-catalogue-id))
        catalogue-in-edit  (rum/react state/catalogue-in-edit)
        active-catalogue (case mode
                           :show selected-catalogue
                           :edit catalogue-in-edit)
        render-catalogue (get-render-component mode view)]
    [:div.price-catalogue
     [:div.catalogue-select-and-buttons
      (println "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")
      (println "catalogue catalogue-in-edit: " catalogue-in-edit)
      (println "catalogue active-catalogue: " active-catalogue)

      (case mode
        :show [:div
               (catalogue-select state/catalogues state/selected-catalogue-id)
               (new-catalogue-button)]
        :edit (edit-buttons))]

     (when active-catalogue
       [:div.switch-and-catalogue
        [:div
         (view-switch)
         (when edit? (add-row-button))]
        (render-catalogue active-catalogue)])]))

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
