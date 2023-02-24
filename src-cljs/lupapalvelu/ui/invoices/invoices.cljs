(ns lupapalvelu.ui.invoices.invoices
  (:require [lupapalvelu.invoices.shared.schemas :refer [ProductConstants]]
            [lupapalvelu.invoices.shared.util :as inv-util]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components :refer [autocomplete day-edit]]
            [lupapalvelu.ui.components.accordion :refer [accordion caret-toggle]]
            [lupapalvelu.ui.invoices.service :as service]
            [lupapalvelu.ui.invoices.state :as state]
            [lupapalvelu.ui.invoices.util :refer [->float
                                                  num?
                                                  sort-by-fn-nils-last
                                                  ->finnish-date-str
                                                  finnish-date->timestamp]]
            [lupapiste-invoice-commons.states :as invoice-states]
            [goog.functions :as gfunc]
            [rum.core :as rum]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))

(defonce args (atom {}))

(def default-product-constants (->> (keys ProductConstants)
                                    (map (fn [k]
                                           [(get k :k k) ""]))
                                    (into {})))

(defn enrich-invoice-row [row]
  (update row :product-constants (partial merge default-product-constants)))

(defn translate-state [state]
  (common/loc (str "invoices.state." state)))

(defn translate-next-state [state]
  (common/loc (str "invoices.state." state ".next")))

(defn translate-previous-state [state]
  (common/loc (str "invoices.state." state ".previous")))

(defn translate-operation [operation]
  (common/loc (str "operations." operation)))

(defn draft? [invoice]
  (util/=as-kw :draft (:state @invoice)))

(defn can-edit? []
  (state/auth? :update-invoice))

(def disabled? (comp not can-edit?))

(defn price-catalogue-for-invoice [invoice]
  (util/find-by-id (:price-catalogue-id @invoice) @state/price-catalogues))

(def currency-formatters
  {"EUR" (fn [money]
           (.toFixed (/ (:minor money) 100) 2))})

(defn MoneyResponse->text [money]
  (when money
    ((get currency-formatters (:currency money)) money)))

(defn discounted-price-from-invoice-row [row]
  (MoneyResponse->text (:with-discount (:sums row))))

(rum/defc format-product-constant < rum/static
                                    {:key-fn (fn [row-type row-index [name-as-kw value]]
                                               (str (name row-type) "-" row-index "-" name-as-kw))}
  [row-type row-index [name-as-kw value]]
  (when-not (ss/blank? value)
    [:div.product-constant-item
     [:span.name (common/loc (str "invoices.rows.product-constants.names." (name name-as-kw)))]
     (str ": " value)]))

(def state-icons {"draft"   "lupicon-eye primary"
                  "checked" "lupicon-check"})

(def unit-loc-mapping {"kpl" (common/loc :unit.kpl)
                       "t"   (common/loc :unit.hour)
                       "pv"  (common/loc :unit.day)
                       "vk"  (common/loc :unit.week)
                       "m2"  (common/loc :unit.m2)
                       "m3"  (common/loc :unit.m3)})

(defn update-invoice! [invoice prep-fn!]
  (prep-fn! invoice)
  (service/upsert-invoice!
    @state/application-id
    @invoice
    (fn [invoice_] (reset! invoice invoice_))))

(rum/defc autosaving-input
  [value on-blur & [{:keys [placeholder]}]]
  (let [debounced (gfunc/debounce on-blur 1000)]
    (components/text-edit value {:callback    (fn [val]  (debounced val))
                                    :class       ["full-width"]
                                    :disabled    (disabled?)
                                    :placeholder placeholder})))

(rum/defc autosaving-select
  [value options on-blur]
  (components/dropdown value {:items    options
                              :enabled? (can-edit?)
                              :choose?  false
                              :callback on-blur
                              :class    :w--max-8em}))

(defn update-invoice-row-value! [invoice operation-index invoice-row-index field-path value]
  {:pre [(vector? field-path)]}
  (let [update-path (concat [:operations operation-index :invoice-rows invoice-row-index] field-path)]
    (update-invoice! invoice (fn [_] (reset! invoice (assoc-in @invoice update-path value))))))

(defn set-local-invoice-row-value! [invoice operation-index invoice-row-index field-path value]
  {:pre [(vector? field-path)]}
  (let [update-path (concat [:operations operation-index :invoice-rows invoice-row-index] field-path)]
    (reset! invoice (assoc-in @invoice update-path value))))

(defn field-setter [{:keys [convert-fn save-in-backend-fn save-locally-fn can-be-saved-in-backend?]}]
  (fn [field-path value old-value]
    (let [converted-value (convert-fn value)
          equals-old-value? (= converted-value old-value)
          can-be-saved-in-backend? (or can-be-saved-in-backend? (constantly true))]
      (if (and (not equals-old-value?)
               (can-be-saved-in-backend? converted-value))
        (save-in-backend-fn field-path converted-value)
        (save-locally-fn field-path old-value)))))


(rum/defc invoice-row-remove-cell
  [invoice operation-index invoice-row-index]
  (when (and (can-edit?) (draft? invoice))
    [:td
     [:button.btn-icon-only.no-border.ghost
      {:on-click (fn [e]
                   (let [edited-invoice (service/remove-invoice-row-from-invoice @invoice operation-index invoice-row-index)]
                     (update-invoice! invoice #(reset! invoice edited-invoice))))}
      [:i.lupicon-remove]]]))

(rum/defc product-constant-item-edit < rum/reactive
                                       {:key-fn (fn [_ row-type row-index [constant-name-kw _]]
                                                  (str (name row-type) "-" row-index "-edit-constant-" (name constant-name-kw)))}
  [update-field-fn row-type row-index [constant-name-kw constant-value]]
  [:div.product-constant-item
   [:div.name (common/loc (str "invoices.rows.product-constants.names." (name constant-name-kw)))]
   [:div.value (autosaving-input
                 constant-value
                 (fn [value] (update-field-fn [:product-constants constant-name-kw] value constant-value)))]])

(rum/defcs product-constants-editor < rum/reactive
  (rum/local false ::show?)
  [{show?* ::show?} product-constants update-field-fn row-type row-index]
  (let [show? (rum/react show?*)]
    [:div
     [:a {:on-click #(swap! show?* not)}
      (common/loc (util/kw-path :price-catalogue.product-constants (if show? :close :open)))]
     (when show?
       (some->> product-constants
                (sort-by key)
                (map (partial product-constant-item-edit update-field-fn row-type row-index))))]))

(rum/defc fully-editable-invoice-row < rum/reactive
                                       {:key-fn (fn [invoice-row invoice-row-index operation-index invoice]
                                                  (str "fully-editable-" operation-index "-" invoice-row-index))}
  [{:keys [text units unit discount-percent price-per-unit product-constants comment] :as invoice-row} invoice-row-index operation-index invoice]
  (let [discounted-price (discounted-price-from-invoice-row invoice-row)
        show-comment? (or (> discount-percent 0)
                          (not (ss/blank? comment)))
        save-in-backend!   (partial update-invoice-row-value! invoice operation-index invoice-row-index)
        save-only-locally! (partial set-local-invoice-row-value! invoice operation-index invoice-row-index)
        update-text-field!  (field-setter {:convert-fn ss/trim :save-in-backend-fn save-in-backend! :save-locally-fn save-only-locally!})
        update-float-field! (field-setter {:convert-fn ->float :save-in-backend-fn save-in-backend! :save-locally-fn save-only-locally! :can-be-saved-in-backend? num?})]
    [:tr {:class-name (if show-comment? "with-comment" "")}
     [:td (autosaving-input text (fn [value] (update-text-field! [:text] value text)))]
     [:td (autosaving-input units (fn [value] (update-float-field! [:units] value units)))]
     [:td (autosaving-select unit (rum/react state/valid-units) (fn [value] (update-text-field! [:unit] value unit)))]
     [:td (autosaving-input price-per-unit (fn [value] (update-float-field! [:price-per-unit] value price-per-unit)))]
     [:td
      (if show-comment?
        [:div.invoice-row-comment-wrapper
         (autosaving-input discount-percent (fn [value] (update-float-field! [:discount-percent] value discount-percent)))
         [:span.invoice-row-comment
          (autosaving-input comment (fn [value] (update-text-field! [:comment] value comment)) {:placeholder (common/loc :invoices.rows.comment)})]]

        (autosaving-input discount-percent (fn [value] (update-float-field! [:discount-percent] value discount-percent))))]
     [:td discounted-price]
     [:td (product-constants-editor product-constants update-text-field!
                                    :fully-editable invoice-row-index)]
     (invoice-row-remove-cell invoice operation-index invoice-row-index)]))

(rum/defc editable-catalogue-invoice-row < rum/reactive
                                           {:key-fn (fn [invoice-row invoice-row-index operation-index invoice]
                                                      (str "editable-" operation-index "-" invoice-row-index))}
  [{:keys [units unit discount-percent price-per-unit min-unit-price max-unit-price product-constants comment] :as invoice-row}
   invoice-row-index
   operation-index
   invoice]
  (let [discounted-price (discounted-price-from-invoice-row invoice-row)
        show-comment? (or (> discount-percent 0)
                          (not (ss/blank? comment)))
        save-in-backend!   (partial update-invoice-row-value! invoice operation-index invoice-row-index)
        save-only-locally! (partial set-local-invoice-row-value! invoice operation-index invoice-row-index)
        update-text-field!  (field-setter {:convert-fn ss/trim :save-in-backend-fn save-in-backend! :save-locally-fn save-only-locally!})
        update-float-field! (field-setter {:convert-fn ->float :save-in-backend-fn save-in-backend! :save-locally-fn save-only-locally! :can-be-saved-in-backend? num?})]
    [:tr {:class-name (if show-comment? "with-comment" "")}
     [:td (inv-util/row-title invoice-row)]
     [:td (autosaving-input units (fn [value] (update-float-field! [:units] value units)))]
     [:td (get unit-loc-mapping unit)]
     [:td (if (inv-util/unit-price-editable? invoice-row)
            [:div.invoice-min-max-pop-up-wrapper
             (autosaving-input price-per-unit (fn [value] (update-float-field! [:price-per-unit] value price-per-unit)))
             (when (or (inv-util/non-zero-val? min-unit-price) (inv-util/non-zero-val? max-unit-price))
               [:span.invoice-min-max-pop-up
                (when min-unit-price
                  [:p (str "min: " min-unit-price)])
                (when max-unit-price
                  [:p (str "max: " max-unit-price)])])]
            price-per-unit)]
     [:td
      (if show-comment?
        [:div.invoice-row-comment-wrapper
         (autosaving-input discount-percent (fn [value] (update-float-field! [:discount-percent] value discount-percent)))
         [:span.invoice-row-comment
          (autosaving-input comment (fn [value] (update-text-field! [:comment] value comment)) {:placeholder (common/loc :invoices.rows.comment)})]]

        (autosaving-input discount-percent (fn [value] (update-float-field! [:discount-percent] value discount-percent))))]
     [:td discounted-price]
     [:td (product-constants-editor product-constants update-text-field!
                                    :editable invoice-row-index)]
     (invoice-row-remove-cell invoice operation-index invoice-row-index)]))

(defn fix-minor [minor]
  (.toFixed (/ minor 100) 2))

(defn taxfree-text [total-minor vat-minor]
  (fix-minor (- total-minor vat-minor)))

(defn invoice-table-row-static
  [{:keys [units unit discount-percent price-per-unit product-constants comment row-class
           vat-amount-minor vat-percentage vat-info?] :as invoice-row}
   invoice-row-index
   operations-row-index]
  (let [discounted-price (discounted-price-from-invoice-row invoice-row)
        row-id           (common/unique-id "row")
        show-comment?    (and (> discount-percent 0)
                              (not (ss/blank? comment)))
        row              [:tr {:key   row-id
                               :class row-class}
                          [:td (inv-util/row-title invoice-row)]
                          [:td.number units]
                          [:td (get unit-loc-mapping unit)]
                          [:td.number price-per-unit]
                          [:td.number discount-percent]
                          (when vat-info?
                            (list [:td.number.nowrap {:key (str row-id "-vat1")}
                                   (when vat-amount-minor
                                     (common/loc :invoice.vat-info
                                                 (str vat-percentage)
                                                 (fix-minor vat-amount-minor)))]
                                  [:td.number {:key (str row-id "-vat2")}
                                   (when vat-amount-minor
                                     (taxfree-text (-> invoice-row :sums :with-discount :minor)
                                                   vat-amount-minor))]))
                          [:td.number discounted-price]

                          [:td (when show-comment? {:row-span 2})
                           (for [[index product-constant] (some->> product-constants
                                                                   (sort-by key)
                                                                   (map-indexed #(vector %1 %2)))]
                             (format-product-constant :static index product-constant))]]]
    (cond-> row
      show-comment? (list [:tr {:key   (str row-id "-comment")
                                :class row-class}
                           [:td] [:td {:col-span (if vat-info? 7 5)} [:span.invoice-row-comment.static-comment
                                                     [:p comment]]] #_[:td]]))))

(rum/defc filterable-select-row
  [on-change items]
  (when (can-edit?)
    [:tr
     [:td {:col-span 7}
      (autocomplete ""
                    {:items items
                     :callback on-change})]
     [:td]]))

(defmulti create-invoice-row-element (fn [row index operation-index invoice]
                                       (if (draft? invoice)
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
  {:key-fn (fn [operation-index operation invoice]
             operation-index)}
  [operation-index operation invoice]
  (let [vat-info?      (and (not (draft? invoice)) (:vat-total-minor @invoice))
        invoice-rows   (->> (:invoice-rows operation)
                            (map-indexed (fn [index row]
                                           {:order-number (:order-number row)
                                            :row-index    index
                                            :row          (assoc row :vat-info? vat-info?)}))
                            (sort-by-fn-nils-last :order-number))
        catalogue      (price-catalogue-for-invoice invoice)
        catalogue-rows (inv-util/indexed-rows catalogue)
        freerow        {:text (inv-util/row-title {:text (common/loc :invoices.rows.customrow)}) :value :freerow}
        items          (->> catalogue-rows
                            (map (fn [{:keys [index] :as invoice-row}] {:value index :text (inv-util/row-title invoice-row)}))
                            (concat [freerow]))
        on-select      (fn [value]
                         (let [row-from-catalogue (inv-util/->invoice-row (inv-util/find-map catalogue-rows :index value))
                               freerow            {:text              ""
                                                   :unit              (common/loc :unit.kpl)
                                                   :price-per-unit    0
                                                   :units             0
                                                   :type              :custom
                                                   :discount-percent  0
                                                   :product-constants {}}
                               row                (if (= value :freerow)
                                                    freerow
                                                    row-from-catalogue)
                               updated-invoice    (update-in @invoice
                                                             [:operations operation-index :invoice-rows]
                                                             (fn [invoice-rows]
                                                               (conj invoice-rows (enrich-invoice-row row))))]
                           (reset! invoice updated-invoice)
                           (update-invoice! invoice (fn [_]))))
        number-class   (cond-> {}
                         (not (draft? invoice)) (assoc :class "number"))]
    [:table.invoice-operations-table
     [:thead
      [:tr
       [:th.operation (translate-operation (:name operation))]
       [:th.units number-class (common/loc :invoices.rows.amount)]
       [:th.unit (common/loc :invoices.rows.unit)]
       [:th.unit-price number-class (common/loc :invoices.rows.unit-price)]
       [:th.discount.nowrap number-class (common/loc :invoices.rows.discount-percent)]
       (when vat-info?
         (list [:th.number {:key (common/unique-id "vat")} (common/loc :invoices.rows.product-constants.names.alv)]
               [:th.number.nowrap {:key (common/unique-id "vat")} (common/loc :invoices.wo-taxes)]))
       [:th.total.nowrap number-class (common/loc :invoices.rows.total)]
       [:th.product-constants (common/loc :invoices.rows.product-constants)]
       (when (and (can-edit?) (draft? invoice))
         [:th.remove
          [:button.btn-icon-only.no-border.ghost
           {:on-click (fn [e]
                        (let [invoice-with-operation-removed (service/remove-operation-from-invoice @invoice operation-index)]
                          (update-invoice!
                            invoice
                            #(reset! invoice invoice-with-operation-removed))))}
           [:i.lupicon-remove]]])]]
     [:tbody
      (for [[i {:keys [row row-index]}] (map-indexed #(vector %1 %2) invoice-rows)]
        (create-invoice-row-element (assoc row
                                      :row-class (if (even? i) "even-row" "odd-row")
                                      :vat-info? vat-info?)
                                    row-index operation-index invoice))
      (when (draft? invoice)
        (filterable-select-row on-select items))]]))

(rum/defcs invoice-add-operation-row < (rum/local false ::is-open?)
                                       rum/reactive
  [state invoice operations app-id]
  (let [is-open?                    (::is-open? state)
        catalogue                   (price-catalogue-for-invoice invoice)
        catalogue-rows-by-operation (inv-util/rows-with-index-by-operation catalogue)
        autocomplete-operations     (->> operations
                                         (map (fn [operation]
                                                {:text  (translate-operation (:name operation))
                                                 :value (:id operation)})))]
    [:div {:class "button-row-left"}
     [:button.secondary {:on-click #(reset! is-open? (not @is-open?))}
      [:i.lupicon-circle-plus]
      [:span (common/loc :invoices.operations.add-operation)]]
     (when @is-open?
       (autocomplete "" {:items    autocomplete-operations
                         :callback (fn [operation-id]
                                     (reset! is-open? false)
                                     (let [operation-name  (:name (util/find-by-id operation-id operations))
                                           catalogue-rows  (get catalogue-rows-by-operation operation-name)
                                           invoice-rows    (mapv (comp enrich-invoice-row inv-util/->invoice-row)
                                                                 catalogue-rows)
                                           updated-invoice (update @invoice :operations conj {:operation-id operation-id
                                                                                              :name         operation-name
                                                                                              :invoice-rows invoice-rows})]

                                       (service/upsert-invoice! app-id
                                                                updated-invoice
                                                                (fn [response]
                                                                  (reset! invoice updated-invoice)))))}))]))

(rum/defc change-next-state-button [invoice-atom]
  (let [current-state (:state @invoice-atom)
        next-state    (invoice-states/next-state current-state :admin)
        state-text    (if next-state (translate-next-state next-state) "")
        app-id        @state/application-id]
    (when (and (can-edit?) next-state)
      [:button.positive.invoice-change-state-button
       {:disabled (->> (:operations @invoice-atom)
                       (map (comp count :invoice-rows))
                       (apply +)
                       zero?)
        :on-click (fn [event]
                    (let [move-invoice-result (invoice-states/move-to-state [:state] @invoice-atom next-state :next :admin)
                          updated-invoice     (:value move-invoice-result)]
                      (when (:ok move-invoice-result)
                        (reset! invoice-atom updated-invoice)
                        (service/upsert-invoice! app-id updated-invoice
                                                 #(service/fetch-invoices app-id)))))}
       state-text])))

(rum/defc change-previous-state-button [invoice-atom]
  (let [current-state (:state @invoice-atom)
        previous-state (invoice-states/previous-state current-state :admin)
        state-text (if previous-state (translate-previous-state previous-state) "")
        app-id @state/application-id]
    (when (and (can-edit?) previous-state)
      [:button.positive.invoice-change-state-button
       {:on-click (fn [event]
                    (let [move-invoice-result (invoice-states/move-to-state [:state] @invoice-atom previous-state :previous :admin)
                          updated-invoice (:value move-invoice-result)]
                      (when (:ok move-invoice-result)
                        (reset! invoice-atom updated-invoice)
                        (service/upsert-invoice! app-id updated-invoice
                                                 #(service/fetch-invoices app-id)))))}
       state-text])))

(rum/defc delete-invoice-button [invoice-id]
  (when (can-edit?)
    (let [app-id (js/pageutil.hashApplicationId)]
      [:button {:class (str "delete-invoice-button secondary")
                :on-click #(common/show-dialog {:callback (fn [] (service/delete-invoice app-id invoice-id))
                                                :type :yes-no})}
       (common/loc :remove)])))


(rum/defc invoice-summary-row < rum/reactive
  [invoice-atom]
  (let [vat-total-minor (when-not (draft? invoice-atom)
                          (:vat-total-minor @invoice-atom))
        sum-total-minor (-> @invoice-atom :sum :minor)
        sum-total-text (MoneyResponse->text (:sum @invoice-atom))
        sum-zero-vat (when vat-total-minor
                       (- sum-total-minor vat-total-minor))]
    [:div.invoice-summary-table
     [:div
      [:div
       (when (and (draft? invoice-atom) (not (:is-new @invoice-atom)))
         (delete-invoice-button (:id @invoice-atom)))]
      [:div.invoice-sums-container
       [:table.invoice-summary-sums-table
        [:tbody
         (when sum-zero-vat
           (list [:tr {:key (common/unique-id "vat")}
                  [:td.number
                   (common/loc :invoices.wo-taxes)]
                  [:td.number
                   (fix-minor sum-zero-vat)]]
                 [:tr {:key (common/unique-id "vat")}
                  [:td.number
                   (common/loc :invoices.rows.VAT)]
                  [:td.number
                   (fix-minor vat-total-minor)]]))
         [:tr
          [:td.number (common/loc :invoices.rows.total)]
          [:td.number sum-total-text]]]]]
      [:div
       (change-next-state-button invoice-atom)
       (change-previous-state-button invoice-atom)]]]))

(rum/defc invoice-ya-days < rum/reactive
  [invoice*]
  (let [tr (fn [loc-key n show-always?]
             (when (or show-always? (some-> n pos?))
               [:tr {:key (common/unique-id "days")}
                [:td.text.nowrap (common/loc loc-key)]
                [:td {:colSpan 2} (str (or n 0) " "
                                       (common/loc (if (= n 1)
                                                     :invoices.ya.day
                                                     :invoices.ya.days)))]]))
        {:keys [billable-days free-days error]} (rum/react (rum/cursor-in invoice* [:workdays]))]
    (when-not error
      (list (tr :invoices.ya.non-billable-days free-days false)
            (tr :invoices.ya.billable-work-time billable-days true)))))

(rum/defcs invoice-ya-date-select-component < rum/reactive
  {:init (fn [state _]
           (let [{:keys [work-start-ms work-end-ms]} (-> state :rum/args first deref)]
             (assoc state
                    ::date-start (atom (some-> work-start-ms ->finnish-date-str))
                    ::date-end (atom (some-> work-end-ms ->finnish-date-str)))))}
  [{date-start ::date-start
    date-end   ::date-end} invoice]
  (let [stamper         (fn [datestring]
                          (try
                            (finnish-date->timestamp datestring)
                            (catch js/Error _
                              (swap! invoice update :workdays assoc :error true))))
        date-update-cb! (fn []
                          (when (and @date-start @date-end)
                            (service/update-worktime invoice
                                                     (stamper @date-start)
                                                     (stamper @date-end))))
        date-error?     (rum/react (rum/cursor-in invoice [:workdays :error]))
        edit-options    (fn [a*]
                          {:invalid? date-error?
                           :string?  true
                           :disabled (disabled?)
                           :callback (fn [date-str]
                                       (reset! a* date-str)
                                       (date-update-cb!))})]
    [:div.invoice-ya-date-select-component-wrapper
     [:table.invoice-ya-date-table
      [:tbody
       [:tr
        [:td.text.nowrap (common/loc :invoices.ya.real-work-time)]
        [:td.first (day-edit @date-start (edit-options date-start))]
        [:td.middle.text " - "]
        [:td (day-edit @date-end (edit-options date-end))]]
       (invoice-ya-days invoice)]]]))

(rum/defc price-catalogue-select < rum/reactive
  [invoice]
  (when (draft? invoice)
    (if-let [catalogs (some->> state/price-catalogues
                               rum/react
                               (map (fn [{:keys [id name valid-from valid-until]}]
                                      (let [from  (some-> valid-from ->finnish-date-str)
                                            until (some-> valid-until ->finnish-date-str)]
                                        {:value id
                                         :text  (str name " "
                                                     (when (or from until)
                                                       (str from " - " until)))}))))]
      [:div
       [:label.dsp--block {:for "price-catalogue-select"}
        (common/loc :auth-admin.price-catalogue.title)]
       (components/dropdown (:price-catalogue-id @invoice (-> catalogs first :id))
                            {:items    catalogs
                             :id       "price-catalogue-select"
                             :enabled? (can-edit?)
                             :class    :w--min-20em
                             :callback (fn [cid]
                                         (update-invoice! invoice
                                                          #(swap! % assoc :price-catalogue-id
                                                                  (ss/blank-as-nil cid))))})]
      [:div.pate-note (common/loc :error.price-catalogue.not-available)])))

(defn print-button [invoice]
  (when-let [invoice-id (:id @invoice)]
    (components/link-button {:url      (js/sprintf "/api/raw/invoice-pdf?id=%s&invoice-id=%s"
                                                   @state/application-id
                                                   invoice-id)
                             :text-loc :print})))

(defn internal-toggle [invoice]
  (components/toggle (some-> invoice deref :organization-internal-invoice?)
                     {:id ":invoices.general.organization-internal-invoice?"
                      :text-loc  :invoices.general.organization-internal-invoice?
                      :disabled? (or (disabled?) (not (draft? invoice)))
                      :callback  (fn [internal?]
                                   (update-invoice! invoice
                                                    #(swap! % assoc :organization-internal-invoice?
                                                            internal?)))}))

(rum/defc backend-code
  "Shows either backend-id or code select."
  < rum/reactive
  [invoice*]
  (let [{:keys [backend-id backend-code]} (rum/react invoice*)
        codes                             (seq (rum/react state/backend-id-codes))]
    (cond
      backend-id
      [:div (str (common/loc :invoicing.backend-id.title) " " backend-id)]

      codes
      [:div
       [:label.dsp--block {:for "backend-code"}
        (common/loc :invoicing.backend-id.codes.code)]
       (components/dropdown backend-code
                            {:items    (map (fn [{:keys [code text]}]
                                              {:value code
                                               :text  (js/sprintf "%s (%s)" text code)})
                                            codes)
                             :sort-by  :text
                             :class    :w--min-20em
                             :id       "backend-code"
                             :enabled? (and (draft? invoice*)
                                            (state/auth? :set-invoice-backend-code))
                             :callback #(service/set-backend-id-code invoice* %)})])))

(rum/defc invoice-data < rum/reactive
  [invoice]
  (let [app-operations       (rum/react state/operations)
        invoice-r            (rum/react invoice)
        id-in-invoice?       (fn [{:keys [id]}]
                               (-> (map :operation-id (:operations invoice-r))
                                   (set)
                                   (contains? id)))
        available-operations (remove id-in-invoice? app-operations)]
    [:div
     [:div.invoice-ya-date-select-wrapper {:style {:borderBottom "1px solid"}}
      (when (rum/react (rum/cursor-in invoice [:workdays]))
        (invoice-ya-date-select-component invoice))]
     [:div.invoice-operations-table-wrapper
      (if (draft? invoice)
        [:div.flex.flex--between.flex--gap2.flex--wrap.flex--align-end
         (price-catalogue-select invoice)
         (internal-toggle invoice)
         (backend-code invoice)
         (print-button invoice)]
        [:div.flex.flex--between.flex--gap2.flex--wrap.flex--align-end
         (when (some-> invoice deref :organization-internal-invoice?)
           [:div.pate-note (common/loc :invoice.pdf.title.internal)])
         (backend-code invoice)
         (print-button invoice)])
      (for [[idx operation] (map-indexed #(vector %1 %2) (:operations invoice-r))]
        (operations-component idx operation invoice))

      (when (and (can-edit?)
                 (draft? invoice)
                 (not-empty available-operations))
        (invoice-add-operation-row invoice available-operations @state/application-id))
      (invoice-summary-row invoice)]]))

(rum/defc invoice-title-component < rum/reactive
  [invoice]
  (let [invoice-state (:state @invoice)
        state (translate-state (:state @invoice))
        icon (get state-icons invoice-state)]
    [:div.invoice-title-component
     [:div (common/loc :invoices.state-of-invoice)]
     [:div [:i {:class icon}] state]]))

(rum/defc invoice-component < rum/reactive
                              {:key-fn (fn [invoice] (:id @invoice))}
  [invoice]
  (let [invoice-state-class (if (= "draft" (:state @invoice)) "" "onward-from-draft")]
    (accordion (draft? invoice)
               {:accordion-toggle-component caret-toggle
                :accordion-content-component (invoice-data invoice)
                :header-title-component (invoice-title-component invoice)
                :extra-class (str "invoice-accordion " invoice-state-class)})))

(rum/defc invoice-table < rum/reactive
  [invoices]
  [:div
   (for [invoice invoices]
     (invoice-component (atom invoice)))])

(rum/defc invoice-operation-row < rum/reactive
  []
  [:div.operation-button-row
   [:div
    [:h2 (common/loc :invoices.title)]]
   [:div {:class "new-invoice-button-container"}
    (when (can-edit?)
      [:button.primary {:on-click #(service/create-invoice)}
       [:i.lupicon-circle-plus]
       [:span (common/loc :invoices.new-invoice)]])]
   [:div {:class "clear"}]])

(rum/defc invoices < rum/reactive
  []
  [:div
   [:div {:class "invoice-list-wrapper"}
    (invoice-operation-row)
    [:div
     (invoice-table (rum/react state/invoices))]]])

(defn bootstrap-invoices []
  (when-let [app-id (js/pageutil.hashApplicationId)]
    (reset! state/invoices [])
    (reset! state/valid-units [{:value "kpl" :text (common/loc :unit.kpl) :price 10}
                               {:value "m2"  :text (common/loc :unit.m2) :price 20}
                               {:value "m3"  :text (common/loc :unit.m3) :price 30}
                               {:value "t"   :text (common/loc :unit.hour) :price 40}
                               {:value "pv"  :text (common/loc :unit.day) :price 50}
                               {:value "vk"  :text (common/loc :unit.week) :price 60}])
    (reset! state/application-id app-id)
    (service/fetch-invoices app-id)
    (when (state/auth? :application-operations)
      (service/fetch-operations app-id))
    (when (state/auth? :application-price-catalogues)
      (service/fetch-price-catalogues app-id))
    (service/fetch-backend-id-codes)))

(defn mount-component []
  (rum/mount (invoices)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId params]
  (swap! args assoc
         :contracts? (common/oget params :contracts)
         :dom-id (name domId))
  (bootstrap-invoices)
  (mount-component))
