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

(rum/defc invoice-data
  [invoice]
  [:div {:class-name "invoice-operations-table-wrapper"}
   (map (fn [operation]
          (operations-component operation (:id invoice))) (:operations invoice))])

(rum/defc invoice-component
  [invoice]
  (let []
    (accordion {:accordion-toggle-component caret-toggle
                :accordion-content-component (invoice-data invoice)
                :extra-class "invoice-accordion"})))

(rum/defc invoice-table
  [invoices]
  [:div
   (map (fn [invoice]
          (invoice-component invoice)) invoices)])

(rum/defc invoice-list < rum/reactive
  [invoices]
  [:div
   [:div.operation-button-row
    [:button.primary
     {:on-click #(do (js/console.log "Adding new invoice"))}
     [:i.lupicon-circle-plus]
     [:span "Uusi lasku"]]
    [:h2 "Laskutus"]]
   [:div
     (invoice-table invoices)]])


(def dummy-invoices [{:id "1"
                      :operations [{:name "foo-operation"
                                           :invoice-rows [{:id "row1" :text "laskurivin teksti 1" :unit :m2 :price-per-unit 30 :units 20}
                                                          {:id "row1" :text "laskurivin teksti 1" :unit :m2 :price-per-unit 30 :units 40}
                                                          {:id "row1" :text "laskurivin teksti 1" :unit :m2 :price-per-unit 30 :units 20}]}
                                   {:name "bar-operation"
                                           :invoice-rows [{:id "row1" :text "laskurivin teksti 1" :unit :m2 :price-per-unit 30 :units 20}
                                                          {:id "row1" :text "laskurivin teksti 1" :unit :m2 :price-per-unit 30 :units 20}]}]}
                     {:id "2"
                      :operations [{:name "foo-operation"
                                           :invoice-rows {:id "row1" :text "laskurivin teksti 1" :unit :m2 :price-per-unit 30 :units 20}}
                                   {:name "bar-operation"
                                           :invoice-rows [{:id "row1" :text "laskurivin teksti 1" :unit :m2 :price-per-unit 30 :units 20}
                                                          {:id "row1" :text "laskurivin teksti 1" :unit :m2 :price-per-unit 30 :units 20}]}]}])

(def dummy-price-catalog {:rows [{:id "id1" :text "price row 1" :unit :m2 :price-per-unit 30}
                                 {:id "id2" :text "price row 2" :unit :m2 :price-per-unit 30}]})

(defn bootstrap-invoices []
  (println ">> bootstrap-invoices")

  ;; (let [app-id (js/pageutil.hashApplicationId)]
  ;;    (println "app-id: " app-id)
  ;;    (service/fetch-invoices-list app-id)
  ;;    )

  (when-let [app-id (js/pageutil.hashApplicationId)]
    (reset! state/price-catalogue dummy-price-catalog)
    (reset! state/invoices dummy-invoices)
    (reset! state/template-list [])
    (reset! state/verdict-list nil)
    (reset! state/replacement-verdict nil)
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
                                            (service/fetch-invoices app-id)))

    (println "state/auth? :pate-verdicts:" (state/auth? :pate-verdicts))

    ;;(service/fetch-verdict-list app-id)

    ;; (state/refresh-application-auth-model app-id

    ;;                                       #(when (state/auth? :pate-verdicts)
    ;;                                          (service/fetch-verdict-list app-id)
    ;;                                          (when (state/auth? :application-verdict-templates)
    ;;                                            (service/fetch-application-verdict-templates app-id)))
    ;;                                       ;; (fn []
    ;;                                       ;;   (service/fetch-verdict-list app-id)
    ;;                                       ;;   (service/fetch-application-verdict-templates app-id))
    ;;                                       )
    ))


(rum/defc invoices < rum/reactive
  []
  [:div
   (invoice-list @state/invoices)])

(defn mount-component []
  (when (common/feature? :pate)
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
