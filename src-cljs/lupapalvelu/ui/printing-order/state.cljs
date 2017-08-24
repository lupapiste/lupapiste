(ns lupapalvelu.ui.printing-order.state
  (:require [schema.core :as sc]
            [sade.shared-schemas :as ssc]))

(sc/defschema FrontendContact {:firstName                     (sc/constrained sc/Str (comp not empty?))
                               :lastName                      (sc/constrained sc/Str (comp not empty?))
                               (sc/optional-key :companyName) sc/Str
                               :address                       (sc/constrained sc/Str (comp not empty?))
                               :postalCode                    (sc/constrained sc/Str (comp not empty?))
                               :city                          (sc/constrained sc/Str (comp not empty?))
                               :email                         ssc/Email
                               (sc/optional-key :phoneNumber) sc/Str})

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
                            :conditions-accepted false
                            :phase       1
                            :id          nil})

(defonce component-state (atom empty-component-state))

(defn valid-contact? [contact]
  (let [checker (sc/checker FrontendContact)]
    (->> contact
         checker
         nil?)))

(defn valid-order? []
  (let [{:keys [orderer payer delivery conditions-accepted
                payer-same-as-orderer delivery-same-as-orderer ]} (:contacts @component-state)]
    (and (valid-contact? orderer)
         (or payer-same-as-orderer (valid-contact? payer))
         (or delivery-same-as-orderer (valid-contact? delivery))
         conditions-accepted)))

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
