(ns lupapalvelu.ui.auth-admin.prices.catalogue
  (:require [cljs-time.coerce :as tcoerce]
            [cljs-time.core :as time]
            [lupapalvelu.invoices.shared.schemas :refer [ProductConstants]]
            [lupapalvelu.ui.auth-admin.prices.service :as service]
            [lupapalvelu.ui.auth-admin.prices.state :as state]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.ui.invoices.util :refer [get-next-no-billing-period-id
                                                  all-periods-set?
                                                  days-between-dates
                                                  should-add-new-no-billing-entry?]]
            [goog.functions :as gfunc]
            [rum.core :as rum]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util :include-macros true]))

(defn can-delete? []
  (let [{:keys [state valid-from]} (rum/react state/selected-catalogue)]
    (and (state/react-auth? :delete-price-catalogue)
         (or (= state "draft")
             (> valid-from (tcoerce/to-long (time/time-now)))))))

(defn can-edit? []
  (state/react-auth? :edit-price-catalogue-draft))

(defn editing? []
  (and (can-edit?)
       (state/draft? (rum/react state/selected-catalogue))))

(defn loc-operation [operation]
  (if operation
    (loc (str "operations." (name operation)))
    operation))

(defn loc-unit [unit]
  (if (ss/blank? unit)
    ""
    (loc (case unit
           "kpl" :unit.piece
           "m2"  :unit.squarem
           "m3"  :unit.cubicm
           "t"   :unit.hour
           "pv"  :unit.day
           "vk"  :unit.week))))

(defn format-catalogue-name-in-select [catalogue]
  (if (state/draft? catalogue)
    (ss/upper-case (loc "price-catalogue.draft"))
    (ss/trim (str
               (js/util.finnishDate (:valid-from catalogue))
               " - "
               (js/util.finnishDate (:valid-until catalogue))))))

(defn- sort-catalogues
  "Draft first, then in descending valid-from order."
  [catalogues]
  (let [{:strs [draft published]} (group-by :state catalogues)]
    (concat draft (reverse (sort-by :valid-from published)))))

(rum/defc catalogue-select  < rum/reactive
  []
  (let [editing-allowed? (can-edit?)
        items            (->> (rum/react state/catalogues)
                              sort-catalogues
                              (map (fn [catalog]
                                     {:value     (:id catalog)
                                      :catalogue catalog
                                      :text      (format-catalogue-name-in-select catalog)})))
        current-id       (:id (rum/react state/selected-catalogue))]
    (uc/dropdown current-id
                 {:test-id "select-catalogue"
                  :items    items
                  :callback (fn [value]
                              (when-not (= value current-id)
                                (if (ss/blank? value)
                                  (state/set-selected nil)
                                  (service/fetch-price-catalogue value
                                                                 (fn [catalogue]
                                                                   (state/set-selected catalogue
                                                                                       (and editing-allowed? (state/draft? catalogue))))))))})))


(rum/defc operation-table < rum/reactive
  {:key-fn (fn [operation _] (str "operation-table-" operation))}
  [operation rows]
  (let [edit-fn (fn [row-id ops]
                  (state/update-field row-id [:operations] ops)
                  (service/edit-price-catalogue-draft {:row {:id         row-id
                                                             :operations ops}}))]
    [:div.price-catalogue-operations
     {:data-test-id operation}
     [:table
      [:thead
       (into [:tr
              [:th.operation-header-operation (loc-operation operation)]]
             (when (seq rows)
               (map (fn [k]
                      [:th {:class (str "operation-header-" (name k))}
                       (if (= k :remove)
                         ""
                         (loc (util/kw-path :price-catalogue k)))])
                    [:unit-price :discount-percent :minimum :maximum :unit :remove])))]
      (when (seq rows)
        (into [:tbody]
              (for [{:keys [id code text price-per-unit discount-percent
                            min-total-price max-total-price unit]} rows]
                [:tr {:data-test-id code}
                 [:td {:data-test-id "code-text"} (str code " " text)]
                 [:td {:data-test-id "price-per-unit"} price-per-unit]
                 [:td {:data-test-id "discount-percent"} discount-percent]
                 [:td {:data-test-id "min-total-price"} min-total-price]
                 [:td {:data-test-id "max-total-price"} max-total-price]
                 [:td {:data-test-id "unit"} (loc-unit unit)]
                 [:td.action-buttons
                  (when (editing?)
                    [:button.ghost.remove
                     {:data-test-id (str "delete-" code)
                      :on-click     (fn []
                                      (edit-fn id (->> (get @state/row-map id)
                                                       :operations
                                                       (remove #(= % operation)))))}
                     [:i.lupicon-remove]])]])))]
     (when (editing?)
       (let [row-ids (set (map :id rows))
             items   (for [row-id @state/row-order
                           :let   [{:keys [code text]} (get (rum/react state/row-map) row-id)]
                           :when  (not (contains? row-ids row-id))]
                       {:text  (ss/join-non-blanks " " (map ss/trim [code text]))
                        :value row-id})]
         (uc/dropdown nil {:items     (doall items)
                           :test-id   (str operation "-select")
                           :disabled? (empty? items)
                           :callback  (fn [value]
                                        (->> (get @state/row-map value)
                                             :operations
                                             (cons operation)
                                             (edit-fn value)))})))]))

(rum/defc catalogue-by-operations  < rum/reactive
  []
  (let [rm          (rum/react state/row-map) ;; Rendering dependency
        rows        (if (editing?)
                      (map #(get rm %) (rum/react state/row-order))
                      (:rows (rum/react state/selected-catalogue)))
        rows-by-ops (->> @state/org-operations
                         (map (fn [op]
                                [op (filter #(util/includes-as-kw? (:operations %) op)
                                            rows)]))
                         (into {}))]
    [:div
     (for [op    (sort-by loc-operation @state/org-operations)
           :let  [rows (get rows-by-ops op)]
           :when (or (editing?) (seq rows))]
       (operation-table op rows))]))

(rum/defc row-move-buttons
  [row-id]
  [:div.row-move-buttons
   [:button.ghost.btn-small {:data-test-id "move-up"
                             :on-click #(service/move-row row-id "up")}
    [:i.lupicon-chevron-up.pointer]]
   [:button.ghost.btn-small {:data-test-id "move-down"
                             :on-click #(service/move-row row-id "down")}
    [:i.lupicon-chevron-down.pointer]]])

(defn field-test-id [field-path]
  (ss/join "-" (map name field-path)))

(rum/defcs field < rum/reactive
                  {:will-mount
                   (fn [rum-state]
                     (let [[_ _ on-change] (:rum/args rum-state)]
                       (assoc rum-state ::on-change (gfunc/debounce on-change 1000))))}
  [{::keys [on-change]} row-id field-path _ & [props]]
  (let [{:keys [value validation]} (state/field-status row-id field-path)]
    (uc/text-edit value
                     (merge {:callback  on-change
                             :test-id   (field-test-id field-path)
                             :required? (= validation :required)
                             :class     (common/css-flags :warning (= validation :error))}
                            props))))

(rum/defc unit-select  < rum/reactive
  [row-id on-change]
  (let [{:keys [value]} (state/field-status row-id [:unit])]
    (uc/dropdown value
                 {:items (map #(hash-map :value % :text (loc-unit %))
                              ["kpl" "m2" "m3" "t" "pv" "vk"])
                  :callback  on-change
                  :test-id   "unit"
                  :required? true
                  :choose?   (not value)
                  :class     "unit-select"})))


(defn field-setter [row-id field-path & [type]]
  (fn [value]
    (let [value         (ss/trim value)
          backend-value (if (= type :number)
                          (util/pcond->> (js/util.parseFloat value)
                            js/Number.isNaN ((constantly nil)))
                          value)]
      (when (state/update-field row-id field-path (or backend-value value))
        (service/edit-price-catalogue-draft {:row (assoc-in {:id   row-id}
                                                            field-path backend-value)})))))

(rum/defc product-constants-editor < rum/reactive
  [row-or-id]
  (let [row-id            (:id row-or-id row-or-id)
        product-constants (:product-constants row-or-id)]
    (when (or (editing?) (some ss/not-blank? (vals product-constants)))
      (let [open? (get (rum/react state/product-constant-toggles) row-id)]
        [:div.tabby
         [:a {:on-click     #(swap! state/product-constant-toggles update row-id not)
              :data-test-id "toggle-product-constants"}
          (common/loc (util/kw-path :price-catalogue.product-constants
                                    (if open? :close :open)))]
         (when open?
           (for [code  (map #(get % :k %) (keys ProductConstants))
                 :let  [input-id (ss/join "-" [row-id (name code)])
                        field-path [:product-constants code]]
                 :when (or (editing?) (ss/not-blank? (code product-constants)))]
             [:div.tabby__row {:key (name code)}
              [:div.tabby__cell
               [:label.product-constant-label {:for input-id}
                (common/loc (util/kw-path :invoices.rows.product-constants.names code))]]
              [:div.tabby__cell
               (if (editing?)
                 (field row-id field-path (field-setter row-id field-path))
                 [:span {:data-test-id (field-test-id field-path)}
                  (code product-constants)])]]))]))))

(rum/defc catalogue-row
  [can-move? index {:keys [id code text price-per-unit discount-percent
                           min-total-price max-total-price unit]
                    :as   row}]
  (into [:tr {:key          id
              :data-test-id (str "catalogue-row-" index)}]
        (concat (map (fn [k] [:td {:data-test-id (name k)} (k row)])
                     [:code :text :price-per-unit :discount-percent
                      :min-total-price :max-total-price])
                [[:td {:data-test-id "unit"} (loc-unit unit)]
                 [:td (product-constants-editor row)]
                 [:td.action-buttons
                  (when can-move?
                    (row-move-buttons id))]])))

(rum/defc edit-catalogue-row < rum/reactive
  [can-move? index row-id]
  [:tr {:key          row-id
        :data-test-id (str "catalogue-row-" index)}
   [:td (field row-id [:code] (field-setter row-id [:code]) {:size "6"})]
   [:td (field row-id [:text] (field-setter row-id [:text]) {:size "25"})]
   [:td (field row-id [:price-per-unit]
               (field-setter row-id [:price-per-unit] :number) {:size "6"})]
   [:td (field row-id [:discount-percent]
               (field-setter row-id [:discount-percent] :number) {:size "4"})]
   [:td (field row-id [:min-total-price]
               (field-setter row-id [:min-total-price] :number) {:size "6"})]
   [:td (field row-id [:max-total-price]
               (field-setter row-id [:max-total-price] :number) {:size "6"})]
   [:td (unit-select row-id (field-setter row-id [:unit]))]
   [:td (product-constants-editor row-id)]
   [:td.action-buttons
    (when can-move?
      (row-move-buttons row-id))
    [:button.ghost.remove {:data-test-id (str "delete-row-" index)
                           :on-click     (fn []
                                           (common/show-dialog {:type     :yes-no
                                                                :ltext    :price-catalogue.remove-row.confirmation
                                                                :callback #(service/edit-price-catalogue-draft {:delete-row row-id})}))}
     [:i.lupicon-remove]]]])

(rum/defc catalogue-table  < rum/reactive
  []
  (let [required  {:class (common/css-flags :required (editing?))}
        rows      (rum/react (if (editing?)
                               state/row-order
                               (rum/cursor-in state/selected-catalogue [:rows])))
        can-move? (> (count rows) 1)
        row-fn    (if (editing?) edit-catalogue-row catalogue-row)]
    [:div
     [:table.price-catalogue-table
      [:thead
       [:tr
        [:th (loc "price-catalogue.code")]
        [:th required (loc "price-catalogue.product")]
        [:th required (loc "price-catalogue.unit-price")]
        [:th required (loc "price-catalogue.discount-percent")]
        [:th (loc "price-catalogue.minimum")]
        [:th (loc "price-catalogue.maximum")]
        [:th required (loc "price-catalogue.unit")]
        [:th (loc "price-catalogue.product-constants")]
        [:th ""]]]
      (into
        [:tbody]
        (for [[idx row] (map-indexed #(vector %1 %2) rows)]
          (row-fn can-move? idx row)))]]))

(defn add-empty-period-if-needed [no-billing-periods]
  (util/pcond-> no-billing-periods
    should-add-new-no-billing-entry? (assoc (get-next-no-billing-period-id no-billing-periods) {})))

(defn- invalid-period? [[_ {:keys [start end]}]]
  (when (and (ss/not-blank? start)  (ss/not-blank? end))
    (try
      (days-between-dates start end)
      false
      (catch js/Error _ true))))

(rum/defc no-billing-periods < rum/reactive
  []
  (let [periods (add-empty-period-if-needed (rum/react state/no-billing-periods))]
    [:div
     [:table.no-billing-periods-table
      [:thead
       [:tr
        [:th (loc :price-catalogue.no-billing-period.start)]
        [:th (loc :price-catalogue.no-billing-period.end)]
        [:th]]]

      (into [:tbody]
            (for [[period-id {:keys [start end]}
                   :as   period] periods
                  :let           [invalid? (invalid-period? period)]]
              [:tr {:key (common/unique-id)} ;; Needed for the correct render after delete
               [:td (uc/day-edit start {:string?  true
                                        :invalid? invalid?
                                        :callback #(state/set-no-billing-period-start period-id %)})]
               [:td (uc/day-edit end {:string?  true
                                      :invalid? invalid?
                                      :callback #(state/set-no-billing-period-end period-id %)})]
               [:td.action-buttons
                [:button.ghost.remove {:data-test-id (str "delete-" (name period-id))
                                       :on-click     #(state/remove-no-billing-period period-id)
                                       :disabled     (or (= (count periods) 1)
                                                         (should-add-new-no-billing-entry? (dissoc periods period-id)))}
                 [:i.lupicon-remove]]]]))]
     (uc/icon-button {:text-loc  :price-catalogue.no-billing-period.save
                      :class     :positive
                      :disabled? (or (not (all-periods-set? periods))
                                     (some invalid-period? periods))
                      :icon      :lupicon-save
                      :test-id   "save-no-billing-periods"
                      :on-click  #(service/save-no-billing-periods periods)})]))

(rum/defc view-switch  < rum/static
  [view catalogue-type]
  [:div.view-switch
   [:button {:data-test-id "view-by-rows"
             :className    (if (= view :by-rows)
                             "view-switch-selected"
                             "view-switch-unselected")
             :on-click     (fn [] (state/set-view :by-rows))}
    [:span (loc "price-catalogue.by-product-rows")]]

   [:button {:data-test-id "view-by-operations"
             :className    (if (= view :by-operations)
                             "view-switch-selected"
                             "view-switch-unselected")
             :on-click     (fn [] (state/set-view :by-operations))}
    [:span (loc "price-catalogue.by-operations")]]

   (when (= catalogue-type "YA")
     [:button {:data-test-id "view-no-billing-periods"
               :className    (if (= view :no-billing-periods)
                               "view-switch-selected"
                               "view-switch-unselected")
               :on-click     (fn [] (state/set-view :no-billing-periods))}
      [:span (loc "price-catalogue.no-billing-periods")]])])

(rum/defc last-saved < rum/reactive
  []
  (when (editing?)
    [:span.saved-info
     {:data-test-id "catalogue-saved-info"}
     (->> (rum/react state/edit-state)
          :meta :modified
          js/util.finnishDateAndTime
          (common/loc :pate.last-saved))]))

(rum/defc remove-button  < rum/reactive
  []
  (when (can-delete?)
    [:div.right-button.catalogue-button
     (uc/icon-button {:class    :secondary
                      :test-id  "delete-catalogue"
                      :on-click (fn []
                                  (let [catalog @state/selected-catalogue]
                                    (common/show-dialog {:type     :yes-no
                                                         :text     (if (state/draft? catalog)
                                                                     (common/loc :price-catalogue.remove-draft.confirmation)
                                                                     (common/loc :price-catalogue.remove.confirmation (format-catalogue-name-in-select catalog)))
                                                         :callback service/delete-catalogue})))
                      :icon     :lupicon-remove
                      :text-loc :price-catalogue.remove})]))



(rum/defc publish-button  < rum/reactive
  []
  (when (editing?)
    [:div.right-button.catalogue-button
     (uc/icon-button {:class    :positive
                      :test-id  "publish-catalogue"
                      :on-click #(common/show-dialog {:type     :yes-no
                                                      :text     (loc :price-catalogue.publish.confirmation)
                                                      :callback service/publish-price-catalogue})
                      :icon     :lupicon-megaphone
                      :enabled? (state/edit-state-valid?)
                      :text-loc :price-catalogue.publish})]))

(rum/defc revert-button  < rum/reactive
  []
  (when (and (can-edit?) (not (editing?)))
    [:div.right-button.catalogue-button
     (uc/icon-button {:class    :ghost
                      :test-id  "revert-catalogue"
                      :on-click service/revert-catalogue
                      :icon     :lupicon-undo
                      :text-loc :pate-proposal.revert})]))

(rum/defc add-row-button  < rum/reactive
  []
  [:div.right-button
   (uc/icon-button {:class    :positive
                    :on-click #(service/edit-price-catalogue-draft {:row {:discount-percent 0}})
                    :icon     :lupicon-circle-plus
                    :test-id  "add-row"
                    :text-loc :price-catalogue.add-product-row})])

(defn edit-valid-period []
  (let [{:keys [valid-from valid-until]} @state/edit-state]
    (service/edit-price-catalogue-draft {:valid (util/assoc-when {}
                                                                 :from valid-from
                                                                 :until valid-until)})))

(rum/defc catalogue-date-picker  < rum/reactive
  [datekey]
  (let [date (rum/react (rum/cursor-in state/edit-state [datekey]))]
    [:div.date-picker
     (uc/day-edit (js/util.finnishDate date)
                  {:required? true
                   :string?   true
                   :invalid?  (get (rum/react state/valid-period-errors)
                                   datekey)
                   :callback  (fn [datestring]
                                ;; See `pate-date` for rationale.
                                (let [ts (some-> datestring
                                                 (js/util.toMoment "fi")
                                                 .valueOf
                                                 (+ (* 1000 3600 12)))]
                                  (state/update-valid-period datekey ts datestring)
                                  (when (state/validate-valid-period?)
                                    (edit-valid-period))))})]))

(defn valid-period-text [catalogue]
  (let [valid-from  (some-> catalogue :valid-from js/util.finnishDate)
        valid-until (some-> catalogue :valid-until js/util.finnishDate)]
    (when (or valid-from valid-until)
      (str valid-from " - " valid-until))))

(defn get-render-component [view]
  (case view
    :by-rows            catalogue-table
    :by-operations      catalogue-by-operations
    :no-billing-periods no-billing-periods))

(rum/defc catalogue  < rum/reactive
  [selected-catalogue view]
  [:div.price-catalogue
   [:div.pate-grid-8
    [:div.row
     [:div.col-4
      (uc/icon-button {:class    :ghost
                       :on-click (fn []
                                   (state/set-selected nil)
                                   (service/fetch-price-catalogues))
                       :icon     :lupicon-chevron-left
                       :text-loc :back
                       :test-id  :back})]]
    [:div.row
     [:div.col-4.verdict-template
      [:span.row-text.header
       (when (editing?)
         (loc :price-catalogue.edit))
       (uc/pen-input {:value     (:name (if (editing?)
                                          (rum/react state/edit-state)
                                          selected-catalogue))
                      :callback  #(service/edit-price-catalogue-draft {:name %})
                      :disabled? (not (editing?))
                      :test-id   :catalogue-name})]]
     [:div.col-4.col--right
      (publish-button) (remove-button) (revert-button)]]
    (if (editing?)
      [:div.row
       [:div.col-2
        [:div.col--vertical
         [:label.pate-label (loc :price-catalogue.valid-from)]
         (catalogue-date-picker :valid-from)]]
       [:div.col-2
        [:div.col--vertical
         [:label.pate-label (loc :price-catalogue.valid-until)]
         (catalogue-date-picker :valid-until)]]
       [:div.col-4.col--right
        [:div.col--vertical
         [:label common/nbsp]
         (last-saved)]]]
      [:div.row
       [:div.col-4 (when-let [txt (valid-period-text selected-catalogue)]
                     (js/sprintf "%s %s" (loc :suti.display-valid) txt))]])
    (when selected-catalogue
      [:div.row
       [:div.col-4
        [:div.col--vertical
         [:label.pate-label (loc "price-catalogue.show")]
         (view-switch view (rum/react state/org-catalogue-type))]]])]
   (when selected-catalogue
     [:div
      [:div.catalogue-content
       (when (and (= view :by-rows) (editing?))
         (add-row-button))
       ((get-render-component view) selected-catalogue)]])])

(defn open-catalogue [catalogue-id]
  (service/fetch-price-catalogue catalogue-id
                                 (fn [catalogue]
                                   (state/set-selected catalogue
                                                       (and (state/auth? :edit-price-catalogue-draft)
                                                            (state/draft? catalogue))))))


(rum/defc catalogue-list < rum/reactive
  []
  (let [edit? (can-edit?)]
    [:div
     [:h2 (loc :auth-admin.price-catalogue.title)]
     (when-let [catalogs (seq (rum/react state/catalogues))]
       [:div.pate-template-list
        [:table.pate-templates-table
         [:thead
          [:tr
           [:th (loc :auth-admin.price-catalogue.title)]
           [:th (loc :matti.status)]
           [:th (loc :suti.display-valid)]
           [:th]]]
         [:tbody
          (for [[idx
                 {:keys [name state id]
                 :as   catalog}] (map-indexed vector catalogs)
                :let            [draft?  (= state "draft")
                                 open-fn #(open-catalogue id)]]
            [:tr {:key id :data-test-id (str "catalogue-" idx)}
             [:td
              [:a {:on-click open-fn} name]]
             [:td (loc (if draft?
                         :price-catalogue.draft
                         :pate-verdict-template.published))]
             [:td (when-not draft?
                    (valid-period-text catalog))]
             [:td
              [:div.pate-buttons
               (uc/icon-button
                 {:class    [:primary :outline]
                  :on-click open-fn
                  :enabled? edit?
                  :text-loc :price-catalogue.open
                  :icon     (if draft? :lupicon-pen :lupicon-circle)
                  :test-id  :open-catalogue})
               (uc/icon-button
                 {:class    [:primary :outline]
                  :on-click #(service/new-price-catalogue-draft id)
                  :text-loc :pate.copy
                  :enabled? edit?
                  :icon     :lupicon-copy
                  :test-id  :copy-catalogue})
               ]]])]]])
     (uc/icon-button
       {:class    :positive
        :on-click #(service/new-price-catalogue-draft)
        :text-loc :price-catalogue.new
        :test-id  "new-catalogue"
        :enabled? (state/react-auth? :new-price-catalogue-draft)
        :icon     :lupicon-circle-plus})]))

(rum/defc main-view < rum/reactive
  []
  (if-let [selected (rum/react state/selected-catalogue)]
    (catalogue selected (rum/react state/view))
    (catalogue-list)))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (main-view)
             (.getElementById js/document (:dom-id @args))))

(defn contains-permit-type? [permit-types permit-type]
  (some #{permit-type} permit-types))

(defn ->catalogue-type [permit-types]
  (cond
    (contains-permit-type? permit-types "R")  "R"
    (contains-permit-type? permit-types "YA") "YA"))

(defn ^:export start [domId componentParams]
  (swap! args assoc :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (reset! state/org-id (js/ko.unwrap (common/oget componentParams "orgId")))
  (reset! state/org-catalogue-type (-> (js/ko.unwrap (common/oget componentParams "permitTypes"))
                                       ->catalogue-type))
  (let [operation-categories {"R" ["Rakentaminen ja purkaminen"
                                   "Poikkeusluvat ja suunnittelutarveratkaisut"]
                              "YA" ["yleisten-alueiden-luvat"]}]
    (service/fetch-organization-operations (get operation-categories @state/org-catalogue-type)))
  (service/fetch-price-catalogues)
  (state/set-selected nil)
  (mount-component))
