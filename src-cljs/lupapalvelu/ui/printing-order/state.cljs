(ns lupapalvelu.ui.printing-order.state)

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
