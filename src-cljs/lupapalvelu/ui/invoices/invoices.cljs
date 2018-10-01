(ns lupapalvelu.ui.invoices.invoices
  (:require [clojure.set :as set]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.accordion :refer [accordion caret-toggle]]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.invoices.layout :as layout]
            [lupapalvelu.ui.invoices.service :as service]
            [lupapalvelu.ui.invoices.state :as state]
            [rum.core :as rum]
            [sade.shared-util :as util]
            [lupapalvelu.ui.components :refer [autocomplete]]))

(enable-console-print!)

(defonce args (atom {}))

(rum/defc autosaving-input [invoice-row value-key invoice-id]
  [:input {:class-name "invoice-autosaving-input" :value (value-key invoice-row)}])

(rum/defc invoice-table-row [invoice-row invoice-id]
  [:tr
   [:td (:text invoice-row)]
   [:td (autosaving-input invoice-row :units invoice-id)]
   [:td (str (:unit invoice-row))]
   [:td (:price-per-unit invoice-row)]
   [:td (autosaving-input {:ale "20"} :ale invoice-id)]
   [:td "0.00"]
   [:td (* (:units invoice-row) (:price-per-unit invoice-row))]])

(rum/defc filterable-select-row []
  [:tr {:style {:border-style "dashed" :border-width "1px" :border-color "#dddddd"}}
   [:td {:col-span 7} (autocomplete "groo" {:items [{:text "foo" :value "foo value"} {:text "faa" :value "groo"}] })]])

(rum/defc operations-component [operation invoice-id]
  (let [invoice-rows (map (fn [row] (invoice-table-row row invoice-id)) (:invoice-rows operation))]
    [:table {:class-name "invoice-operations-table"}
     [:thead
      [:tr
       [:th (:name operation)]
       [:th "määrä"]
       [:th "Yksikkö"]
       [:th "A-Hinta (€)"]
       [:th "Ale (%)"]
       [:th "Alv (€)"]
       [:th "Yhteensä (€)"]]]
     [:tbody
      invoice-rows
      (filterable-select-row)]]))

(rum/defcs invoice-add-operation-row < (rum/local false ::is-open?)
  [state invoice]
  (let [is-open? (::is-open? state)]
    [:div {:class-name "button-row-left"}
     [:button.secondary {:on-click #(reset! is-open? (not @is-open?))}
      [:i.lupicon-circle-plus]
      [:span "Lisää toimenpide"]]
     (if @is-open?
       (autocomplete :uusi-talo {:items [{:text "Uusi talo" :value :uusi-talo}
                                         {:text "Linjasaneeraus" :value :linjasaneeraus}]
                                 :callback (fn [value]
                                             (reset! is-open? false)
                                             (println value)
                                             (service/add-operation-to-invoice (:id invoice) value))}))])
  )

(rum/defc invoice-data
  [invoice]
  [:div {:class-name "invoice-operations-table-wrapper"}
   (map (fn [operation]
          (operations-component operation (:id invoice))) (:operations invoice))
   (invoice-add-operation-row invoice)
   ])

(rum/defc invoice-component
  [invoice]
  (let []
    (accordion {:accordion-toggle-component caret-toggle
                :accordion-content-component (invoice-data invoice)
                :extra-class "invoice-accordion"})))

(rum/defc invoice-table < rum/reactive
  [invoices]
  (let [new-invoice  @state/new-invoice]
    [:div
     (if new-invoice
       (invoice-component new-invoice))
     (map (fn [invoice]
            (invoice-component invoice)) invoices)]))

(rum/defc new-invoice-button < rum/reactive
  [new-invoice]
  [:button.primary {:disabled (not (nil? new-invoice))
                    :on-click #(service/create-invoice)}
      [:i.lupicon-circle-plus]
      [:span "Uusi lasku"]])

(rum/defc invoice-list < rum/reactive
  [invoices]
  [:div {:class-name "invoice-list-wrapper"}
   [:div.operation-button-row
    [:div
     [:h2 "LASKUTUS"]]
    [:div {:class-name "new-invoice-button-container"}
     (new-invoice-button (rum/react state/new-invoice))]
    [:div {:class-name "clear"}]]
   [:div
     (invoice-table invoices)]])

(def dummy-price-catalog {:rows [{:id "id1" :text "price row 1" :unit :m2 :price-per-unit 30}
                                 {:id "id2" :text "price row 2" :unit :m2 :price-per-unit 30}]})

(defn bootstrap-invoices []
  (println ">> bootstrap-invoices")
  (when-let [app-id (js/pageutil.hashApplicationId)]
    (reset! state/price-catalogue dummy-price-catalog)
    (reset! state/invoices [])
    (reset! state/new-invoice nil)
    (state/refresh-verdict-auths app-id)
    (state/refresh-application-auth-model app-id
                                          (fn []
                                            (service/insert-invoice app-id
                                                                    {:operations [{:operation-id :linjasaneeraus
                                                                                   :name "linjasaneeraus"
                                                                                   :invoice-rows [{:text "Laskurivi1 kpl"
                                                                                                   :unit :kpl
                                                                                                   :price-per-unit 10
                                                                                                   :units 2}
                                                                                                  {:text "Laskurivi2 m2 "
                                                                                                   :unit :m2
                                                                                                   :price-per-unit 20.5
                                                                                                   :units 15.8}
                                                                                                  {:text "Laskurivi3 m3 "
                                                                                                   :unit :m3
                                                                                                   :price-per-unit 20.5
                                                                                                   :units 15.8}]}]})
                                            (service/fetch-invoices app-id)))    ))


(rum/defc invoices < rum/reactive
  []
  [:div
   (invoice-list (rum/react state/invoices))])

(defn mount-component []
  (when (common/feature? :invoices)
    (rum/mount (invoices)
               (.getElementById js/document (:dom-id @args)))))

(defn ^:export start [domId params]
  (println "invoices.cljs start  domId:" domId " params: " params)
  (when (common/feature? :invoices)
    (swap! args assoc
           :contracts? (common/oget params :contracts)
           :dom-id (name domId))
    (bootstrap-invoices)
    (mount-component)))
