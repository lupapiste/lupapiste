(ns lupapalvelu.ui.printing-order.state
  (:require [schema.core :as sc]
            [sade.shared-schemas :as ssc]
            [lupapalvelu.ui.common :as common]))

(sc/defschema FrontendContact {:firstName                     (sc/constrained sc/Str (comp not empty?))
                               :lastName                      (sc/constrained sc/Str (comp not empty?))
                               (sc/optional-key :companyName) sc/Str
                               :streetAddress                 (sc/constrained sc/Str (comp not empty?))
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
                                          :delivery {}
                                          :billingReference ""
                                          :deliveryInstructions ""}
                            :conditions-accepted false
                            :submit-pending? false
                            :phase       1
                            :id          nil})

(defonce component-state (atom empty-component-state))

(defn valid-contact? [contact]
  (let [checker (sc/checker FrontendContact)]
    (->> contact
         checker
         nil?)))

(defn valid-order? []
  (let [{:keys [orderer payer delivery
                payer-same-as-orderer delivery-same-as-orderer]} (:contacts @component-state)
        conditions-accepted (:conditions-accepted @component-state)]
    (and (valid-contact? orderer)
         (or payer-same-as-orderer (valid-contact? payer))
         (or delivery-same-as-orderer (valid-contact? delivery))
         (true? conditions-accepted))))

(defn contact-summary-lines [contact]
  (let [{:keys [firstName lastName companyName streetAddress postalCode city email phoneNumber]} contact]
    (remove nil? [companyName
                  (str firstName " " lastName)
                  streetAddress
                  (str postalCode " " city)
                  email
                  phoneNumber])))

(defn- payer []
  (let [{:keys [payer-same-as-orderer orderer payer]} (-> @component-state :contacts)]
    (if payer-same-as-orderer
      orderer
      payer)))

(defn- delivery-address []
  (let [{:keys [delivery-same-as-orderer orderer delivery]} (-> @component-state :contacts)]
    (if delivery-same-as-orderer
      orderer
      delivery)))

(defn orderer-summary-lines []
  (contact-summary-lines (-> @component-state :contacts :orderer)))

(defn payer-summary-lines []
  (contact-summary-lines (payer)))

(defn delivery-address-summary-lines []
  (contact-summary-lines (delivery-address)))

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
  (swap! component-state assoc :submit-pending? true)
  (common/command {:command "submit-printing-order"
                   :show-saved-indicator? false
                   :success
                     (fn [{order-number :order-number}]
                       (swap! component-state assoc
                              :phase 4
                              :order-number order-number
                              :submit-pending? false)
                       (js/scrollTo 0 0))
                   :error
                     (fn [e]
                       (js/notify.ajaxError (clj->js e))
                       (swap! component-state assoc :phase 3 :submit-pending? false))}
                  :id (:id @component-state)
                  :order (:order @component-state)
                  :contacts (:contacts @component-state)))

(defn back-to-application []
  (.openPage js/pageutil (str "application/" (:id @component-state)) "attachments"))