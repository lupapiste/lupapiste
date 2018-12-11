(ns lupapalvelu.ui.invoices.invoices
  (:require [clojure.set :as set]
            [clojure.string :refer [trim]]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.accordion :refer [accordion caret-toggle]]
            [lupapalvelu.ui.hub :as hub]
            [lupapiste-invoice-commons.states :as invoice-states]
            [lupapalvelu.ui.invoices.service :as service]
            [lupapalvelu.ui.invoices.state :as state]
            [cljs.lupapalvelu.ui.invoices.util :refer [num num?]]
            [lupapalvelu.invoices.shared.util :as inv-util]
            [rum.core :as rum]
            [sade.shared-util :as util]
            [lupapalvelu.ui.components :refer [autocomplete]]))

(enable-console-print!)

(defonce args (atom {}))

(defn translate-state [state]
  (common/loc (str "invoices.state." state)))

(defn translate-next-state [state]
  (common/loc (str "invoices.state." state ".next")))

(defn translate-previous-state [state]
  (common/loc (str "invoices.state." state ".previous")))

(defn translate-operation [operation]
  (common/loc (str "operations." operation)))

(def currency-formatters
  {"EUR" (fn [money]
           (.toFixed (/ (:minor money) 100) 2))})

(defn MoneyResponse->text [money]
  ((get currency-formatters (:currency money)) money))

(defn discounted-price-from-invoice-row [row]
  (MoneyResponse->text (:with-discount (:sums row))))

(defn calc-alv [price]
  (* 0.24 price))


(def state-icons {"draft" "lupicon-eye primary"
                  "checked" "lupicon-check"})

(defn update-invoice! [invoice cb!]
  (cb! invoice)
  (service/upsert-invoice!
   @state/application-id
   @invoice
   (fn [invoice_] (reset! invoice invoice_))))

(rum/defc autosaving-input [value on-blur]
  (let [value-atom (atom value)]
    (components/text-edit value-atom {:callback (fn [event] (on-blur @value-atom))
                                      :class ["full-width"]})))

(rum/defc autosaving-select [value options on-blur]
  (let [value-atom (atom value)]
    (components/dropdown value-atom {:items options
                                     :callback (fn [event] (on-blur @value-atom))
                                     :class ["full-width"]})))

(defn update-invoice-row-value! [invoice operation-index invoice-row-index field value]
  (update-invoice! invoice
                   (fn [_]
                     (reset!
                      invoice
                      (assoc-in @invoice [:operations operation-index :invoice-rows invoice-row-index field] value)))))

(defn set-local-invoice-row-value! [invoice operation-index invoice-row-index field value]
  (reset! invoice (assoc-in @invoice [:operations operation-index :invoice-rows invoice-row-index field] value)))

(defn field-setter [converter save-in-backend! save-only-locally! & [can-be-saved-in-backend?]]
  (fn [field value old-value]
    (let [converted-value (converter value)
          equals-old-value? (= converted-value old-value)
          can-be-saved-in-backend? (or can-be-saved-in-backend? (constantly true))]
      (if (and (not equals-old-value?)
               (can-be-saved-in-backend? value))
        (save-in-backend! field converted-value)
        (save-only-locally! field old-value)))))

(rum/defc fully-editable-invoice-row < rum/reactive
  < {:key-fn (fn [invoice-row invoice-row-index operation-index invoice]
               (str operation-index "-" invoice-row-index))}
  [{:keys [text units unit discount-percent price-per-unit] :as invoice-row} invoice-row-index operation-index invoice]
  (let [discounted-price (discounted-price-from-invoice-row invoice-row)
        alv (calc-alv discounted-price)
        save-in-backend!   (partial update-invoice-row-value!    invoice operation-index invoice-row-index)
        save-only-locally! (partial set-local-invoice-row-value! invoice operation-index invoice-row-index)
        update-text-field! (field-setter trim save-in-backend! save-only-locally!)
        update-num-field!  (field-setter num save-in-backend! save-only-locally! num?)]
    [:tr
     [:td (autosaving-input  text  (fn [value] (update-text-field! :text value text)))]
     [:td (autosaving-input  units (fn [value] (update-num-field! :units value units)))]
     [:td (autosaving-select unit (rum/react state/valid-units) (fn [value] (update-text-field! :unit value)))]
     [:td (autosaving-input  price-per-unit (fn [value] (update-num-field! :units value price-per-unit)))]
     [:td (autosaving-input discount-percent (fn [value] (update-num-field! :discount-percent value discount-percent)))]
     [:td alv]
     [:td discounted-price]]))

(rum/defc editable-catalogue-invoice-row < rum/reactive
  < {:key-fn (fn [invoice-row invoice-row-index operation-index invoice]
               (str operation-index "-" invoice-row-index))}
  [{:keys [text units unit discount-percent price-per-unit] :as invoice-row} invoice-row-index operation-index invoice]
  (let [discounted-price (discounted-price-from-invoice-row invoice-row)
        alv (calc-alv discounted-price)
        save-in-backend!   (partial update-invoice-row-value!    invoice operation-index invoice-row-index)
        save-only-locally! (partial set-local-invoice-row-value! invoice operation-index invoice-row-index)
        update-text-field! (field-setter trim save-in-backend! save-only-locally!)
        update-num-field!  (field-setter num save-in-backend! save-only-locally! num?)]
    [:tr
     [:td text]
     [:td (autosaving-input  units (fn [value] (update-num-field! :units value units)))]
     [:td unit]
     [:td price-per-unit]
     [:td (autosaving-input discount-percent (fn [value] (update-num-field! :discount-percent value discount-percent)))]
     [:td alv]
     [:td discounted-price]]))

(rum/defc invoice-table-row
  < {:key-fn (fn [invoice-row invoice-row-index operation-index invoice]
               (str operation-index "-" invoice-row-index))}
  [invoice-row invoice-row-index operation-index invoice]
  (let [discounted-price (discounted-price-from-invoice-row invoice-row)
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

  (let [discounted-price (discounted-price-from-invoice-row invoice-row)
        alv (calc-alv discounted-price)]
    [:tr
     [:td (:text invoice-row)]
     [:td (:units invoice-row)]
     [:td (str (:unit invoice-row))]
     [:td (:price-per-unit invoice-row)]
     [:td (:discount-percent invoice-row)]
     [:td alv]
     [:td discounted-price]]))

(rum/defc filterable-select-row [on-change items]
  [:tr {:style {:border-style "dashed" :border-width "1px" :border-color "#dddddd"}}
   [:td {:col-span 7}
    (autocomplete ""
                  {:items items ;;[{:text (common/loc :invoices.rows.customrow) :value :freerow}]
                   :callback on-change})]])

(defmulti create-invoice-row-element (fn [row index operation-index invoice]
                                       (if (= (:state @invoice) "draft")
                                         {:type (keyword (:type row)) :state (keyword (:state @invoice))}
                                         {:state :not-draft})))

(defmethod create-invoice-row-element {:type :custom :state :draft}
  [row index operation-index invoice]
  (fully-editable-invoice-row row index operation-index invoice))

(defmethod create-invoice-row-element {:type :from-price-catalogue :state :draft}
  [row index operation-index invoice]
  (editable-catalogue-invoice-row row index operation-index invoice))

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
        invoice-state (keyword (:state @invoice))
        catalogue (rum/react state/price-catalogue)
        catalogue-rows (inv-util/indexed-rows catalogue)
        freerow {:text (common/loc :invoices.rows.customrow) :value :freerow}
        items (->> catalogue-rows
                   (map (fn [{:keys [index text]}] {:value index :text text}))
                   (concat [freerow]))
        on-select (fn [value]
                    (let [row-from-catalogue (inv-util/->invoice-row (inv-util/find-map catalogue-rows :index value))
                          freerow {:text ""
                                   :unit (common/loc :unit.kpl)
                                   :price-per-unit 0
                                   :units 0
                                   :type :custom
                                   :discount-percent 0}
                          row (if (= value :freerow)
                                freerow
                                row-from-catalogue)
                          updated-invoice (update-in @invoice
                                                       [:operations operation-index :invoice-rows]
                                                       (fn [invoice-rows]
                                                         (conj invoice-rows row)))]
                      (reset! invoice updated-invoice)))]
    [:table {:class "invoice-operations-table"}
     [:thead
      [:tr
       [:th.operation  (translate-operation (:name operation))]
       [:th.units      (common/loc :invoices.rows.amount)]
       [:th.unit       (common/loc :invoices.rows.unit)]
       [:th.unit-price (common/loc :invoices.rows.unit-price)]
       [:th.discount   (common/loc :invoices.rows.discount-percent)]
       [:th.vat        (common/loc :invoices.rows.VAT)]
       [:th.total      (common/loc :invoices.rows.total)]]]
     [:tbody
      invoice-rows
      (if (= :draft invoice-state)
        (filterable-select-row on-select items))]]))

(rum/defcs invoice-add-operation-row < (rum/local false ::is-open?)
  rum/reactive
  [state invoice app-id]
  (let [is-open? (::is-open? state)
        catalogue (rum/react state/price-catalogue)
        catalogue-rows-by-operation (inv-util/rows-with-index-by-operation catalogue)
        operations_ (rum/react state/operations)
        operations (map (fn [operation]
                         {:text (translate-operation (:name operation)) :value (:name operation)}) operations_)]
    [:div {:class "button-row-left"}
     [:button.secondary {:on-click #(reset! is-open? (not @is-open?))}
      [:i.lupicon-circle-plus]
      [:span (common/loc :invoices.operations.add-operation)]]
     (if @is-open?
       (autocomplete "" {:items operations
                         :callback (fn [operation]
                                     (reset! is-open? false)
                                     (let [catalogue-rows (get catalogue-rows-by-operation operation)
                                           invoice-rows (mapv inv-util/->invoice-row catalogue-rows)
                                           updated-invoice (service/add-operation-to-invoice @invoice operation invoice-rows)]

                                       (service/upsert-invoice! app-id
                                                                updated-invoice
                                                                (fn [response]
                                                                  (reset! invoice updated-invoice)))))}))]))

(rum/defc change-next-state-button [invoice-atom]
  (let [current-state (:state @invoice-atom)
        next-state (invoice-states/next-state current-state :admin)
        state-text (if next-state (translate-next-state next-state) "")
        app-id @state/application-id]
    (if next-state [:button {:class (str "invoice-change-state-button " current-state)
                             :on-click (fn [event]
                                         (let [move-invoice-result (invoice-states/move-to-state [:state] @invoice-atom next-state :next :admin)
                                               updated-invoice (:value move-invoice-result)]
                                           (if (:ok move-invoice-result)
                                             (do
                                               (reset! invoice-atom updated-invoice)
                                               (service/upsert-invoice! app-id updated-invoice
                                                                    (fn [response]
                                                                      (service/fetch-invoices app-id)))))))} state-text])))

(rum/defc change-previous-state-button [invoice-atom]
  (let [current-state (:state @invoice-atom)
        previous-state (invoice-states/previous-state current-state :admin)
        state-text (if previous-state (translate-previous-state previous-state) "")
        app-id @state/application-id]
    (if previous-state [:button {:class (str "invoice-change-state-button " current-state)
                             :on-click (fn [event]
                                         (let [move-invoice-result (invoice-states/move-to-state [:state] @invoice-atom previous-state :previous :admin)
                                               updated-invoice (:value move-invoice-result)]
                                           (if (:ok move-invoice-result)
                                             (do
                                               (reset! invoice-atom updated-invoice)
                                               (service/upsert-invoice! app-id updated-invoice
                                                                    (fn [response]
                                                                      (service/fetch-invoices app-id)))))))} state-text])))

(rum/defc invoice-summary-row [invoice-atom]
  (let [operations (:operations @invoice-atom)
        invoice-rows-all (mapcat :invoice-rows operations)

        sums {:sum-zero-vat (MoneyResponse->text (:sum @invoice-atom))
              :sum-vat "not counted yet"
              :sum-total (MoneyResponse->text (:sum @invoice-atom))}]
    [:div {:style {:text-align "right"}}
     [:div {:style {:display "inline-block"}}
      [:div
       [:div {:style {:text-align "left" :display "inline" :padding "5em"}} (common/loc :invoices.wo-taxes)]
       [:div {:style {:text-align "right" :display "inline"}} (:sum-zero-vat sums)]]]
     [:div
       [:div {:style {:text-align "left" :display "inline" :padding "5em"}} (common/loc :invoices.vat24)]
      [:div {:style {:text-align "right" :display "inline"}} (:sum-vat sums)]]
     [:div
       [:div {:style {:text-align "left" :display "inline" :padding "5em"}} (common/loc :invoices.rows.total)]
       [:div {:style {:text-align "right" :display "inline"}} (:sum-total sums)]]
     [:div {:style {:display "inline-block"}}
      (change-next-state-button invoice-atom)
      (change-previous-state-button invoice-atom)]]))

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
  (let [invoice-state (:state @invoice)
        state (translate-state (:state @invoice))
        icon (get state-icons invoice-state)]
    [:div.invoice-title-component
     [:div (common/loc :invoices.state-of-invoice)]
     [:div [:i {:class icon}] state]]))

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
      [:span (common/loc :invoices.new-invoice)]])

(rum/defc invoice-list < rum/reactive
  [invoices]
  [:div {:class "invoice-list-wrapper"}
   [:div.operation-button-row
    [:div
     [:h2 (common/loc :invoices.title)]]
    [:div {:class "new-invoice-button-container"}
     (new-invoice-button (rum/react state/new-invoice))]
    [:div {:class "clear"}]]
   [:div
     (invoice-table invoices)]])

(rum/defc invoices < rum/reactive
  []
  [:div
   (invoice-list (rum/react state/invoices))])

(defn bootstrap-invoices []
  (when-let [app-id (js/pageutil.hashApplicationId)]
    (reset! state/invoices [])
    (reset! state/valid-units [{:value "kpl" :text (common/loc :unit.kpl) :price 10}
                               {:value "m2" :text (common/loc :unit.m2)  :price 20}
                               {:value "m3" :text (common/loc :unit.m3) :price 30}])
    (reset! state/application-id app-id)
    (reset! state/new-invoice nil)
    (service/fetch-invoices app-id)
    (service/fetch-operations app-id)
    (service/fetch-price-catalogue app-id)))

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
