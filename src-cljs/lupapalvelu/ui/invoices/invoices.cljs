(ns lupapalvelu.ui.invoices.invoices
  (:require [clojure.set :as set]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.invoices.layout :as layout]
            [lupapalvelu.ui.invoices.service :as service]
            [lupapalvelu.ui.invoices.state :as state]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(enable-console-print!)

(defonce args (atom {}))

(rum/defc invoice-component
  [invoice]
  (let [foo (js/console.log invoice)]
    [:div
     [:h1 (:id invoice)]]))

(rum/defc invoice-table
  [invoices]
  [:div
   (map (fn [invoice]
          [:section {:class-name "accordion" :key (:id invoice)}
           [:div {:class-name "accordion_content" "data-accordion-state" "open"}
            (invoice-component invoice)
            ]]) invoices)])

(rum/defc invoice-list < rum/reactive
  [invoices]
  [:div
   [:div.operation-button-row
    [:button.primary
     {:on-click #(do (js/console.log "Adding new invoice"))}
     [:i.lupicon-circle-plus]
     [:span "Uusi lasku"]]
    [:h2 "Laskutus"]
    [:div
     (invoice-table invoices)
     ]
    ]])


(def dummy-invoices [{:id "1"
                      :operations [:oper1 {:name "foo-operation"
                                           :invoice-rows [{:id "row1" :text "laskurivin teksti 1" :unit :m2 :price-per-unit 30 :units 20}
                                                          {:id "row1" :text "laskurivin teksti 1" :unit :m2 :price-per-unit 30 :units 40}
                                                          {:id "row1" :text "laskurivin teksti 1" :unit :m2 :price-per-unit 30 :units 20}]}
                                   :oper2 {:name "bar-operation"
                                           :invoice-rows [{:id "row1" :text "laskurivin teksti 1" :unit :m2 :price-per-unit 30 :units 20}
                                                          {:id "row1" :text "laskurivin teksti 1" :unit :m2 :price-per-unit 30 :units 20}]}]}
                     {:id "2"
                      :operations [:oper1 {:name "foo-operation"
                                           :invoice-rows {:id "row1" :text "laskurivin teksti 1" :unit :m2 :price-per-unit 30 :units 20}}
                                   :oper2 {:name "bar-operation"
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
                                          #(service/fetch-price-catalogue app-id))

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
  (when (common/feature? :pate)
    (swap! args assoc
           :contracts? (common/oget params :contracts)
           :dom-id (name domId))
    (bootstrap-invoices)
    (mount-component)))
