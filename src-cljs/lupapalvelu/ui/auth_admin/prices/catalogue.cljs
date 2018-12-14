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

(defn loc-operation [operation]
  (if operation
    (loc (str "operations." (name operation)))
    operation))

(defn ->date [timestamp]
  (tcoerce/from-long timestamp))

(defmulti ->date-str (fn [x] (cond
                               (nil? x)       :default
                               (number? x)    :timestamp
                               (time/date? x) :date)))

(defmethod ->date-str :timestamp [timestamp]
  (tformat/unparse invoice-date-formatter (->date timestamp)))

(defmethod ->date-str :date [date]
  (tformat/unparse invoice-date-formatter date))

(defmethod ->date-str :default [_]
  nil)

(defn format-catalogue-name-in-select [catalogue]
  (str
   (->date-str (:valid-from catalogue))
   " - "
   (->date-str (:valid-until catalogue))
   (if (= "draft" (:state catalogue)) (js/sprintf " [%s]" (loc "price-catalogue.draft")))))

(rum/defc catalogue-select < rum/reactive
  [catalogues selected-catalogue-id]
  (let [options (cons ["" (loc "choose")]
                      (map (juxt :id format-catalogue-name-in-select)
                           (rum/react catalogues)))
        value (rum/react selected-catalogue-id)]
    (uc/select state/set-selected-catalogue-id
               "catalogue-select"
               value
               options
               "dropdown catalogue-select")))

(rum/defc operation-catalogue-row
  < {:key-fn (fn [operation row] (str operation "-" (:index row)))}
  [operation {:keys [text price-per-unit discount-percent min-total-price max-total-price unit] :as row}]
  [:tr
   [:td text]
   [:td price-per-unit]
   [:td discount-percent]
   [:td min-total-price]
   [:td max-total-price]
   [:td unit]
   [:td ""]])

(rum/defc operation-table
  < {:key-fn (fn [operation rows] (str operation))}
  [operation rows]
  [:div
   [:table {:class-name "invoice-operations-table"}
     [:thead
      [:tr
       [:th.operation-header-operation        (loc-operation operation)]
       [:th.operation-header-unit-price       (loc "price-catalogue.unit-price")]
       [:th.operation-header-discount-percent (loc "price-catalogue.discount-percent")]
       [:th.operation-header-minimum          (loc "price-catalogue.minimum")]
       [:th.operation-header-maximum          (loc "price-catalogue.maximum")]
       [:th.operation-header-unit             (loc "price-catalogue.unit")]
       [:th.operation-header-remove            ""]]]
    [:tbody
     (map operation-catalogue-row operation rows)]]])

(rum/defc catalogue-by-operations  < rum/reactive
  [selected-catalogue]
  (let [rows-by-operation (util/rows-with-index-by-operation selected-catalogue)
        rows-by-operation (merge
                           (util/empty-rows-by-operation (rum/react state/org-operations))
                           (util/rows-with-index-by-operation selected-catalogue))]
    [:div
     (for [[operation rows] rows-by-operation]
       (operation-table operation rows))]))

(rum/defc row-operation
  < {:key-fn (fn [operation] (str "operation-" operation))}
  [operation]
  [:span.row-operation
   (str (loc-operation operation) " ")])

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

(rum/defc operation-product-select < rum/reactive
  [operation rows]
  (let [catalogue  (rum/react state/catalogue-in-edit)
        options (map (fn [{:keys [index text]}]
                       [:option {:key (str operation "-" index) :value index} text])
                     rows)]
    [:select.operation-product-select {:on-change (fn [e]
                                                    (let [value (.. e -target -value)]
                                                      (if (not (= value "empty"))
                                                        (state/add-operation-to-row! operation (js/Number value)))))}
     (conj options [:option {:key "empty" :value "empty"} (loc "selectone")])]))

(rum/defc edit-operation-catalogue-row
  < {:key-fn (fn [operation row] (str operation "-" (:index row)))}
  [operation {:keys [index text price-per-unit discount-percent min-total-price max-total-price unit] :as row}]
  [:tr
   [:td text]
   [:td price-per-unit]
   [:td discount-percent]
   [:td min-total-price]
   [:td max-total-price]
   [:td unit]
   [:td [:div.remove-icon-container {:on-click (fn [] (state/remove-operation-from-row! operation (:index row)))}
         [:i.lupicon-remove]]]])

(rum/defc edit-operation-table
  < rum/reactive
  < {:key-fn (fn [operation rows] (str "edit-" operation))}
  [operation indexed-product-rows]
  (let [all-indexed-product-rows (util/maps-with-index-key (:rows (rum/react state/catalogue-in-edit)))
        already-selected-indexes (map :index indexed-product-rows)
        selectable-indexed-product-rows (util/remove-maps-with-value all-indexed-product-rows :index already-selected-indexes)]
    [:div.operations-container
     [:table.operations-table
      [:thead
       [:tr
       [:th.operation-header-operation        (loc-operation operation)]
       [:th.operation-header-unit-price       (loc "price-catalogue.unit-price")]
       [:th.operation-header-discount-percent (loc "price-catalogue.discount-percent")]
       [:th.operation-header-minimum          (loc "price-catalogue.minimum")]
       [:th.operation-header-maximum          (loc "price-catalogue.maximum")]
       [:th.operation-header-unit             (loc "price-catalogue.unit")]
       [:th.operation-header-remove            ""]]]
      [:tbody
       (map (partial edit-operation-catalogue-row operation) indexed-product-rows)]]
     [:div.add-row-to-operation
      (operation-product-select operation selectable-indexed-product-rows)]]))

(rum/defc edit-catalogue-by-operations < rum/reactive
  [selected-catalogue]
  (let [rows-by-operation (merge
                           (util/empty-rows-by-operation (rum/react state/org-operations))
                           (util/rows-with-index-by-operation selected-catalogue))]
    [:div
     (for [[operation rows] rows-by-operation]
       (edit-operation-table operation rows))]))

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
  (let [discount-percent (or discount-percent 0)]
    [:tr
     [:td (field code             (field-setter :code row-index)  {:size "6"})]
     [:td (field text             (field-setter :text row-index) {:size "25"})]
     [:td (field price-per-unit   (field-setter :price-per-unit row-index :number) {:size "6"})]
     [:td (field discount-percent (field-setter :discount-percent row-index :number) {:size "3"})]
     [:td (field min-total-price  (field-setter :min-total-price row-index :number) {:size "6"})]
     [:td (field max-total-price  (field-setter :max-total-price row-index :number) {:size "6"})]
     [:td (unit-select unit       (field-setter :unit row-index) )]
     [:td (row-operations operations)]
     [:td [:div.remove-icon-container {:on-click (fn [] (state/remove-row! row-index))}
           [:i.lupicon-remove]]]]))

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
      [:span (loc "price-catalogue.new")]]]))

(rum/defc cancel-button  < rum/reactive
  [_]
  [:div.right-button.catalogue-button
   [:button  {:className "primary"
              :on-click (fn []
                          (state/set-catalogue-in-edit nil)
                          (state/set-mode :show))}
    [:i.lupicon-remove]
    [:span (loc "price-catalogue.remove")]]])

(rum/defc publish-button  < rum/reactive
  [_]
  (let [catalogue-in-edit (rum/react state/catalogue-in-edit)
        now-in-millis (tcoerce/to-long (time/now))]
    [:div.right-button.catalogue-button
     [:button  {:className "positive"
                :on-click (fn [] (service/publish-catalogue catalogue-in-edit))}
      [:i.lupicon-check]
      [:span (loc "price-catalogue.publish")]]]))

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
    [:span (loc "price-catalogue.add-product-row")]]])

(defn tomorrow []
  (time/plus (time/today-at-midnight) (time/days 1)))

(defn tomorrow-or-later? [timestamp]
  (if timestamp
    (not (time/before? (->date timestamp) (tomorrow)))))

(rum/defc catalogue-date-picker [{state :state
                                  catalogue-timestamp :valid-from
                                  date-chosen-on-ui :valid-from-str}]
  [:div.date-picker
   (let [draft-catalogue-date (if (and catalogue-timestamp (= state "draft"))
                                    (if (tomorrow-or-later? catalogue-timestamp)
                                      (->date-str catalogue-timestamp)
                                      (->date-str (tomorrow))))
         value (or date-chosen-on-ui draft-catalogue-date)]
     (uc/date-edit value {:callback state/set-valid-from-date-str}))])

(defn get-render-component [mode view]
  (let [components {:show {:by-rows catalogue-by-rows
                           :by-operations catalogue-by-operations}
                    :edit {:by-rows edit-catalogue-by-rows
                           :by-operations edit-catalogue-by-operations}}]
    (get-in components [mode view])))

(rum/defc catalogue < rum/reactive
  [_]
  (let [view (rum/react state/view)
        mode (rum/react state/mode)
        edit-rows? (and (= mode :edit) (= view :by-rows))
        selected-catalogue (state/get-catalogue (rum/react state/selected-catalogue-id))
        catalogue-in-edit  (rum/react state/catalogue-in-edit)
        active-catalogue (case mode
                           :show selected-catalogue
                           :edit catalogue-in-edit)
        render-catalogue (get-render-component mode view)]
    [:div.price-catalogue
     [:div.catalogue-select-and-buttons
      (case mode
        :show [:div
               (catalogue-select state/catalogues state/selected-catalogue-id)
               (new-catalogue-button)]
        :edit [:div
               [:h3.draft-title (loc "price-catalogue.catalogue-draft")]
               (catalogue-date-picker catalogue-in-edit)
               (edit-buttons)])]

     (when active-catalogue
       [:div.switch-and-catalogue
        [:div
         (view-switch)
         (when edit-rows? (add-row-button))]
        (render-catalogue active-catalogue)])]))

(defonce args (atom {}))

(def log (.-log js/console))

(defn mount-component []
  (rum/mount (catalogue)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (reset! state/org-id (js/ko.unwrap (common/oget componentParams "orgId")))
  (service/fetch-organization-operations ["Rakentaminen ja purkaminen"
                                          "Poikkeusluvat ja suunnittelutarveratkaisut"])
  (service/fetch-price-catalogues)
  (state/set-view :by-rows)
  (mount-component))
