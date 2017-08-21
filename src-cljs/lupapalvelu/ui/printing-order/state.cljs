(ns lupapalvelu.ui.printing-order.state
  (:require [schema.core :as sc]))

(sc/defschema FrontendContact {:firstName  sc/Str
                               :lastName   (sc/constrained sc/Str (comp not empty?))
                               (sc/optional-key :companyName) sc/Str
                               :address    sc/Str
                               :postalCode sc/Str
                               :city       sc/Str
                               :email      sc/Str
                               (sc/optional-key :phone)      sc/Str})

(def empty-component-state {:attachments []
                            :tagGroups   []
                            :order       {}
                            :contacts    {:payer-same-as-orderer true
                                          :delivery-same-as-orderer true
                                          :orderer {}
                                          :payer {}
                                          :delivery {}}
                            :billingReference ""
                            :deliveryInstructions ""
                            :conditionsAccepted false
                            :phase       1
                            :id          nil})

(defonce component-state (atom empty-component-state))

(defn valid-contact? [contact]
  (nil? ((sc/checker FrontendContact) contact)))

(defn valid-order? []
  (js/console.log (clj->js (-> @component-state :contacts :orderer)))
  (valid-contact? (-> @component-state :contacts :orderer)))

(defn will-unmount [& _]
  (reset! component-state empty-component-state))

(defn proceed-phase2 []
  (swap! component-state assoc :phase 2))

(defn proceed-phase3 []
  (swap! component-state assoc :phase 3))

(defn back-to-phase1 []
  (swap! component-state assoc :phase 1))

(defn back-to-phase2 []
  (swap! component-state assoc :phase 2))

(defn submit-order []
  (js/console.log (clj->js @component-state)))
