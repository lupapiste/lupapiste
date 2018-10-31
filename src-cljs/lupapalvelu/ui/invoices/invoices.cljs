(ns lupapalvelu.ui.invoices.invoices
  (:require [clojure.set :as set]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.accordion :refer [accordion caret-toggle]]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.invoices.service :as service]
            [lupapalvelu.ui.invoices.state :as state]
            [rum.core :as rum]
            [sade.shared-util :as util]
            [lupapalvelu.ui.components :refer [autocomplete]]))

(enable-console-print!)

(defonce args (atom {}))

(defn calc-with-discount [discount-int price]
  (*  (/  (- 100 discount-int) 100) price))
(defn calc-alv [price]
  (* 0.24 price))

(defn update-invoice! [invoice cb!]
  (cb! invoice)
  (service/upsert-invoice!
   @state/application-id
   @invoice
   (fn [invoice_] (reset! invoice invoice_))))

(rum/defc autosaving-input [value on-blur]
  (let [value-atom (atom value)]
    (components/text-edit value-atom {:callback (fn [event] (on-blur @value-atom))})))

(rum/defc autosaving-select [value options on-blur]
  (let [value-atom (atom value)]
    (components/dropdown value-atom {:items options
                                     :callback (fn [event] (on-blur @value-atom))})))

(rum/defc fully-editable-invoice-row < rum/reactive
  < {:key-fn (fn [invoice-row invoice-row-index operation-index invoice]
               (str operation-index "-" invoice-row-index))}
  [invoice-row invoice-row-index operation-index invoice]
  (let [discounted-price (calc-with-discount (:discount-percent invoice-row) (* (:units invoice-row) (:price-per-unit invoice-row)))
        alv (calc-alv discounted-price)]
    [:tr
     [:td (autosaving-input (:text invoice-row) (fn [value]
                                                  (update-invoice! invoice
                                                                   (fn [invoice_]
                                                                     (reset!
                                                                      invoice
                                                                      (assoc-in @invoice [:operations operation-index :invoice-rows invoice-row-index :text] value))))))]
     [:td (autosaving-input (:units invoice-row) (fn [value_]
                                                   (let [value (js/parseInt value_)]
                                                     (update-invoice! invoice
                                                                      (fn [invoice_]
                                                                        (reset!
                                                                         invoice_
                                                                         (assoc-in @invoice [:operations operation-index :invoice-rows invoice-row-index :units] value)))))))]
     [:td (autosaving-select (:unit invoice-row) (rum/react state/valid-units) (fn [value]
                                                                                 (update-invoice! invoice
                                                                                                  (fn [invoice_]
                                                                                                    (reset!
                                                                                                     invoice_
                                                                                                     (assoc-in @invoice [:operations operation-index :invoice-rows invoice-row-index :unit] value))))))]
     [:td (:price-per-unit invoice-row)]
     [:td (autosaving-input (:discount-percent invoice-row) (fn [value_]
                                                              (let [value (js/parseInt value_)]
                                                                (update-invoice! invoice
                                                                                 (fn [invoice_]
                                                                                   (reset!
                                                                                    invoice_
                                                                                    (assoc-in @invoice
                                                                                              [:operations operation-index :invoice-rows invoice-row-index :discount-percent]
                                                                                              value)))))))]
     [:td alv]
     [:td discounted-price]]))

(rum/defc invoice-table-row
  < {:key-fn (fn [invoice-row invoice-row-index operation-index invoice]
               (str operation-index "-" invoice-row-index))}
  [invoice-row invoice-row-index operation-index invoice]
  (let [discounted-price (calc-with-discount (:discount-percent invoice-row) (* (:units invoice-row) (:price-per-unit invoice-row)))
       alv (calc-alv discounted-price)]
    [:tr
     [:td (:text invoice-row)]
     [:td (autosaving-input (:units invoice-row) (fn [value_]
                                                   (let [value (js/parseInt value_)]
                                                     (reset! invoice (assoc-in @invoice [:operations operation-index :invoice-rows invoice-row-index :units] value))
                                                     (service/upsert-invoice! @state/application-id @invoice #()))))]
     [:td (str (:unit invoice-row))]
     [:td (:price-per-unit invoice-row)]
     [:td (autosaving-input (:discount-percent invoice-row) (fn [value_]
                                                              (let [value (js/parseInt value_)]
                                                                (reset! invoice
                                                                        (assoc-in @invoice
                                                                                  [:operations operation-index :invoice-rows invoice-row-index :discount-percent]
                                                                                  value))
                                                                (service/upsert-invoice! @state/application-id @invoice #()))))]
     [:td alv]
     [:td discounted-price]]))

(rum/defc invoice-table-row-static
  < {:key-fn (fn [invoice-row invoice-row-index operations-row-index]
               (str operations-row-index "-" invoice-row-index))}
  [invoice-row invoice-row-index operations-row-index]

  (let [discounted-price (calc-with-discount (:discount-percent invoice-row) (* (:units invoice-row) (:price-per-unit invoice-row)))
        alv (calc-alv discounted-price)]
    [:tr
     [:td (:text invoice-row)]
     [:td (:units invoice-row)]
     [:td (str (:unit invoice-row))]
     [:td (:price-per-unit invoice-row)]
     [:td (:discount-percent invoice-row)]
     [:td alv]
     [:td discounted-price]]))

(rum/defc filterable-select-row [on-change]
  [:tr {:style {:border-style "dashed" :border-width "1px" :border-color "#dddddd"}}
   [:td {:col-span 7}
    (autocomplete ""
                  {:items [{:text (common/loc :invoices.rows.customrow) :value :freerow}]
                   :callback on-change})]])

(defmulti create-invoice-row-element (fn [row index operation-index invoice]
                                       (if (= (:state @invoice) "draft")
                                         {:type (keyword (:type row)) :state (keyword (:state @invoice))}
                                         {:state :not-draft})))

(defmethod create-invoice-row-element {:type :custom :state :draft}
  [row index operation-index invoice]
  (fully-editable-invoice-row row index operation-index invoice))

(defmethod create-invoice-row-element {:state :not-draft}
  [row index operation-index invoice]
  (invoice-table-row-static row index operation-index))


(rum/defc operations-component < rum/reactive
  < {:key-fn (fn [operation operation-index invoice]
               operation-index)}
  [operation operation-index invoice]
  (let [invoice-rows (map-indexed (fn [index row]
                                    (create-invoice-row-element row index operation-index invoice))
                                  (:invoice-rows operation))
        invoice-state (keyword (:state @invoice))]
    [:table {:class-name "invoice-operations-table"}
     [:thead
      [:tr
       [:th (:name operation)]
       [:th (common/loc :invoices.rows.amount)]
       [:th (common/loc :invoices.rows.unit)]
       [:th (common/loc :invoices.rows.unit-price)]
       [:th (common/loc :invoices.rows.discount-percent)]
       [:th (common/loc :invoices.rows.VAT)]
       [:th (common/loc :invoices.rows.total)]]]
     [:tbody
      invoice-rows
      (if (= :draft invoice-state)
        (filterable-select-row (fn [value]
                                 (if (= value :freerow)
                                   (let [updated-invoice (update-in @invoice
                                                                    [:operations operation-index :invoice-rows]
                                                                    (fn [invoice-rows]
                                                                      (conj invoice-rows {:text ""
                                                                                          :unit (common/loc :unit.kpl) ;; Note that these values
                                                                                          :price-per-unit 20           ;; Should come from taksa in the
                                                                                          :units 0                     ;; not so far future
                                                                                          :type :custom
                                                                                          :discount-percent 0})))]
                                     (reset! invoice updated-invoice))))))]]))

(rum/defcs invoice-add-operation-row < (rum/local false ::is-open?)
  rum/reactive
  [state invoice app-id]
  (let [is-open? (::is-open? state)
        operations_ (rum/react state/operations)
        operations(map (fn [operation]
                              {:text (:name operation) :value (:name operation)}) operations_)]
    [:div {:class-name "button-row-left"}
     [:button.secondary {:on-click #(reset! is-open? (not @is-open?))}
      [:i.lupicon-circle-plus]
      [:span (common/loc :invoices.operations.add-operation)]]
     (if @is-open?
       (autocomplete "" {:items operations
                                 :callback (fn [value]
                                             (reset! is-open? false)
                                             (let [updated-invoice (service/add-operation-to-invoice @invoice value)]

                                               (service/upsert-invoice! app-id
                                                               updated-invoice
                                                               (fn [response]
                                                                 (reset! invoice updated-invoice)))))}))]))

(rum/defc change-state-button [invoice-atom]
  (let [current-state (:state @invoice-atom)
        next-state (:next-state ((keyword current-state) @state/invoice-states))
        state-text (if next-state (:action-text (next-state @state/invoice-states)))
        app-id @state/application-id]
    (if next-state [:button {:class-name (str "invoice-change-state-button " current-state)
                             :on-click (fn [event]
                                         (let [updated-invoice (assoc @invoice-atom :state next-state)]
                                           (reset! invoice-atom updated-invoice)
                                           (service/upsert-invoice! app-id updated-invoice
                                                                    (fn [response]
                                                                      (service/fetch-invoices app-id)))))} state-text])))

(rum/defc invoice-summary-row [invoice-atom]
  (let [operations (:operations @invoice-atom)
        invoice-rows-all (mapcat :invoice-rows operations)
        sums (reduce (fn [memo row]
                       (let [with-discount-price (calc-with-discount
                                                  (:discount-percent row)
                                                  (* (:units row)
                                                     (:price-per-unit row)))
                             alv (calc-alv with-discount-price)]
                         (-> memo
                             (assoc :sum-zero
                                    (+ (:sum-zero memo)
                                       with-discount-price))
                             (assoc :sum-alv (+ (:sum-alv memo) alv))
                             (assoc :sum-total (+ (:sum-total memo) (+ with-discount-price alv))))))
                     {:sum-zero 0 :sum-alv 0 :sum-total 0} invoice-rows-all)]
    [:div {:style {:text-align "right"}}
     [:div {:style {:display "inline-block"}}
      [:div
       [:div {:style {:text-align "left" :display "inline" :padding "5em"}} "Veroton"]
       [:div {:style {:text-align "right" :display "inline"}} (:sum-zero sums)]]]
     [:div
       [:div {:style {:text-align "left" :display "inline" :padding "5em"}} "Alv 24%"]
      [:div {:style {:text-align "right" :display "inline"}} (:sum-alv sums)]]
     [:div
       [:div {:style {:text-align "left" :display "inline" :padding "5em"}} "Yhteensä"]
       [:div {:style {:text-align "right" :display "inline"}} (:sum-total sums)]]
     [:div {:style {:display "inline-block"}}
      (change-state-button invoice-atom)]]))

(rum/defc invoice-data < rum/reactive
  [invoice]
  (let [invoice-state (keyword (:state @invoice))]
    [:div.invoice-operations-table-wrapper
     (map-indexed (fn [index operation]
                    (operations-component operation index invoice)) (:operations (rum/react invoice)))

     (if (= :draft invoice-state)
       (invoice-add-operation-row invoice @state/application-id))
     (invoice-summary-row invoice)
     ]))

(rum/defc invoice-title-component < rum/reactive [invoice]
  (let [invoice-state (keyword (:state @invoice))
        state-entry (invoice-state @state/invoice-states)
        state (:text state-entry)
        icon (:icon state-entry)]
    [:div.invoice-title-component
     [:div "Laskun tila: "]
     [:div [:i {:class-name (:name icon) :style (:style icon)}] state]]))

(rum/defc invoice-component < rum/reactive
  < {:key-fn (fn [invoice]
               (:id @invoice))}
  [invoice]
  (let [invoice-state-class (if (= "draft" (:state @invoice)) "" "onward-from-draft")]
    (accordion (if (= :draft (keyword (:state @invoice))) true false)
               {:accordion-toggle-component caret-toggle
                :accordion-content-component (invoice-data invoice)
                :header-title-component (invoice-title-component invoice)
                :extra-class (str "invoice-accordion " invoice-state-class)})))

(rum/defc invoice-table < rum/reactive
  [invoices]
  (let [new-invoice  state/new-invoice]
    [:div
     (if @new-invoice
       (invoice-component new-invoice))
     (map (fn [invoice]
            (invoice-component (atom invoice))) invoices)]))

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
     [:h2 (common/loc :invoices.title)]]
    [:div {:class-name "new-invoice-button-container"}
     (new-invoice-button (rum/react state/new-invoice))]
    [:div {:class-name "clear"}]]
   [:div
     (invoice-table invoices)]])

(def dummy-price-catalog {:rows [{:id "id1" :text "price row 1" :unit :m2 :price-per-unit 30}
                                 {:id "id2" :text "price row 2" :unit :m2 :price-per-unit 30}]})

(rum/defc invoices < rum/reactive
  []
  [:div
   (invoice-list (rum/react state/invoices))])

(defn bootstrap-invoices []
  (when-let [app-id (js/pageutil.hashApplicationId)]
    (reset! state/price-catalogue dummy-price-catalog)
    (reset! state/invoices [])
    (reset! state/valid-units [{:value "kpl" :text (common/loc :unit.kpl) :price 10}
                               {:value "m2" :text (common/loc :unit.m2)  :price 20}
                               {:value "m3" :text (common/loc :unit.m3) :price 30}])
    (reset! state/invoice-states {:draft
                                  {:next-state :checked
                                   :action-text (common/loc :invoices.state.draft.action-text)
                                   :text (common/loc :invoices.state.draft.action-text)
                                   :icon {:name "lupicon-eye primary" :style {}}}
                                  :checked
                                  {:previous-state
                                   :draft
                                   :action-text (common/loc :invoices.state.checked.action-text)
                                   :text (common/loc :invoices.state.checked.text)
                                   :icon {:name "lupicon-check" :style {}} }})
    (reset! state/application-id app-id)
    (reset! state/new-invoice nil)
    (service/fetch-invoices app-id)
    (service/fetch-operations app-id)))

(defn mount-component []
  (when (common/feature? :invoices)
    (rum/mount (invoices)
               (.getElementById js/document (:dom-id @args)))))

(defn ^:export start [domId params]
  (when (common/feature? :invoices)
    (swap! args assoc
           :contracts? (common/oget params :contracts)
           :dom-id (name domId))
    (bootstrap-invoices)
    (mount-component)))
