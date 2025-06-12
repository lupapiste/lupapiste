(ns lupapalvelu.ui.invoices.view
  "Application invoicing section."
  (:require [goog.string :as goos]
            [lupapalvelu.invoices.shared.schemas :refer [invoice-row-units]]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.biller.util :as biller-util]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.day-edit :as day-edit]
            [lupapalvelu.ui.components.reselect :as reselect]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.invoices.data :as data]
            [reagent.core :as r]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn field-loc [field-key]
  (case field-key
    :text             :price-catalogue.product
    :units            :invoices.rows.amount
    :unit             :invoices.rows.unit
    :price-per-unit   :invoices.rows.unit-price
    :discount-percent :invoices.rows.discount-percent))

(defn header []
  [:div.flex--wrap.flex--between.flex--gap1.flex--align-center
   [:h2 (loc :billing)]
   (when (<sub [:auth/application? :new-invoice-draft])
     [components/icon-button {:text-loc :invoices.new-invoice
                              :on-click #(>evt [::data/new-invoice])
                              :class :primary
                              :icon :lupicon-circle-plus}])])

(defn label-wrap
  ([text-loc for-id component {:keys [required? horizontal?]}]
   [:div
    {:class (if horizontal? :flex--row :flex--column)}
    [:label.lux.txt--bold
     {:class (common/css-flags :required required?)
      :style (when horizontal? {:display :inline-block})
      :for   for-id} (cond-> (loc text-loc)
                       horizontal? (str ": "))]
    component])
  ([text-loc for-id component]
   [label-wrap text-loc for-id component nil]))

(defn backend-code
  ([invoice-id horizontal?]
   (when-let [items (<sub [::data/backend-code-items])]
     (let [{:keys [backend-id
                   backend-code]} (<sub [::data/invoice invoice-id])
           id                     (str invoice-id "-backend-code")
           edit?                  (<sub [::data/editing? invoice-id])]
       (when (or edit? backend-code)
         [label-wrap
          :invoicing.backend-id.codes.code
          id
          (if (and edit? (ss/blank? backend-id))
            [components/dropdown
             backend-code
             {:items    items
              :id       id
              :class    :w--min-12rem
              :callback #(>evt [::data/set-invoice-field invoice-id :backend-code %])}]
            [:span.lux.w--min-12rem
             (<sub [::data/backend-text invoice-id])])
          {:horizontal? horizontal?}]))))
  ([invoice-id]
   [backend-code invoice-id false]))

(defn add-operation-select [invoice-id]
  (let [edit?  (<sub [::data/editing? invoice-id])
        items  (when edit?
                 (<sub [::data/operation-items invoice-id]))
        for-id (str invoice-id "-add-operation")]
    (when items
      [label-wrap
       :invoice.add-operation
       for-id
       [components/dropdown
        nil
        {:items    items
         :callback (fn [op-id]
                     (when-not (ss/blank? op-id)
                       (>evt [::data/add-operation invoice-id op-id])))}]])))

(defn row-meta-value [invoice-id row-id meta-key]
  (<sub [::data/row-field invoice-id row-id ::data/meta meta-key]))

(defn field-data  [invoice-id row-id field-key]
  [(<sub [::data/row-field invoice-id row-id field-key])
   (<sub [::data/row-field invoice-id row-id ::data/errors field-key])])

(defn text-cell [invoice-id row-id field-key & kws]
  (let [edit?                              (<sub [::data/field-editable? invoice-id row-id field-key])
        [value error]                      (field-data invoice-id row-id field-key)
        {:keys [number currency required]} (zipmap kws (cycle [true]))]
    (if edit?
      [components/text-edit
       value
       {:on-blur?   true
        :invalid?   error
        :required?  required
        :aria-label (loc (field-loc field-key))
        :class      :w--100
        :callback   #(>evt [::data/set-row-field invoice-id row-id field-key (str %)])}]
      (when-not (ss/blank? (str value))
        [:span {:class (common/css-flags :txt--right (or number currency))}
         (cond-> value
           currency (biller-util/euro-format {:zeros? false :symbol? true}))]))))

(defn unit-cell [invoice-id row-id]
  (let [edit? (<sub [::data/field-editable? invoice-id row-id :unit])
        value (<sub [::data/row-field invoice-id row-id :unit])]
    (if edit?
      [components/dropdown
       value
       {:aria-label (loc (field-loc :unit))
        :sort-by    :text
        :items      (map #(hash-map :value % :text (path/loc :unit %))
                         invoice-row-units)
        :choose?    false
        :class      :w--100.w--min-7rem
        :callback   #(>evt [::data/set-row-field invoice-id row-id :unit %])}]
      [:span.lux (path/loc :unit value)])))

(defn total-cell [invoice-id row-id]
  (let [value (<sub [::data/row-field invoice-id row-id :total-cents])]
    [:span.lux.txt--right
     (biller-util/cents->eur value {:zeros? false})]))

(defn product-constants [invoice-id row-id]
  (let [edit?     (<sub [::data/editing? invoice-id])
        constants (<sub [::data/row-field invoice-id row-id :product-constants])]
    (when (or edit? (some ss/not-blank? (vals constants)))
      [:div.flex--column.flex--row-gap1
       [:span.txt--bold.txt--inc1.gap--t2
        (loc :invoices.rows.product-constants)]
       (into [:div.flex--wrap.flex--gap1]
             (for [k     [:alv :kustannuspaikka :laskentatunniste
                          :projekti :kohde :toiminto :muu-tunniste]
                   :let  [v (ss/blank-as-nil (k constants))
                          id (str row-id "-" (name k))]
                   :when (or edit? v)]
               [label-wrap
                (util/kw-path :invoices.rows.product-constants.names k)
                id
                (if edit?
                  [components/text-edit
                   v
                   {:on-blur? true
                    :class :w--12rem
                    :id id
                    :callback #(>evt [::data/set-row-field invoice-id row-id
                                      (util/kw-path :product-constants k) %])}]
                  [:span.lux.w--12rem {:id id} v])
                {:horizontal? (not edit?)}]))])))

(defn extra-info [invoice-id row-id]
  (let [edit?                       (<sub [::data/editing? invoice-id])
        {:keys [discount-percent
                comment]} (<sub [::data/invoice-row invoice-id row-id])
        discount?                   (some->> (str discount-percent)
                                             ss/trim ss/blank-as-nil
                                             (not= "0"))
        comment-id                  (str row-id "-comment")]
    [:div.flex--column.flex--gap1
     (cond
       (and discount? edit?)
       [label-wrap :invoices.rows.comment comment-id
        [components/text-edit
         comment
         {:id       comment-id
          :class    :w--20rem
          :on-blur? true
          :callback #(>evt [::data/set-row-field invoice-id row-id :comment %])}]]

       (and discount? (ss/not-blank? comment))
       [label-wrap :invoices.rows.comment comment-id
        [:span.lux {:id comment-id} comment]
        {:horizontal? true}])
     [product-constants invoice-id row-id]]))

(defn vat-cells [invoice-id row-id]
  (let [{:keys [vat-percentage taxfree-cents vat-cents]
         :as   vat-info} (<sub [::data/row-field invoice-id row-id :vat-info])]
    (if vat-info
      [:<>
       [:td.pad--v05.pad--h2.txt--right.ws--nowrap
        (loc :invoice.vat-info vat-percentage (biller-util/cents->eur vat-cents))]
       [:td.pad--v05.pad--h2.txt--right.ws--nowrap
        (biller-util/cents->eur taxfree-cents)]]
      [:<> [:td] [:td]])))

(defn operation-table-rows [invoice-id idx row-id show-discounts?]
  (let [edit?           (<sub [::data/editing? invoice-id])
        col-count       (cond-> 6
                          edit?           inc
                          show-discounts? inc)
        extra?          (<sub [::data/row-extra-info? invoice-id row-id])
        errors          (not-empty (<sub [::data/row-field invoice-id row-id ::data/errors]))
        vat?            (<sub [::data/show-vat-info? invoice-id])
        expanded?       (row-meta-value invoice-id row-id :expanded?)
        extra-expanded? (and extra? expanded?)
        color-cls       (if (even? idx) :bg--white :bg--gray)
        error-row       (when errors
                          [:tr.bd--h-black.bd--t-black
                           {:class color-cls}
                           [:td.pad--h2
                            {:col-span col-count}
                            (into [:div.flex--wrap.fleg--gap1]
                                  (for [[k v] errors]
                                    [:div.error-note.dsp--inline-block
                                     [:b (str (loc (field-loc k)) ": ")]
                                     [:span (apply loc (flatten [v]))]]))]])
        data-row        [(util/kw-path :tr.bd--h-black color-cls)
                         {:class (common/css-flags :bd--b-black (not extra-expanded?)
                                                   :bd--t-black (not errors))}
                         [:td.pad--v05.pad--h2
                          [text-cell invoice-id row-id :text :required]]
                         [:td.pad--v05.pad--h2.txt--right.ws--nowrap
                          [text-cell invoice-id row-id :units :number :required]]
                         [:td.pad--v05.pad--h2
                          [unit-cell invoice-id row-id]]
                         [:td.pad--v05.pad--h2.txt--right.ws--nowrap
                          [text-cell invoice-id row-id :price-per-unit :currency :required]]
                         (when show-discounts?
                           [:td.pad--v05.pad--h2.txt--right.ws--nowrap
                            [text-cell invoice-id row-id :discount-percent :number]])
                         (when vat?
                           [vat-cells invoice-id row-id])
                         [:td.pad--v05.pad--h2.txt--right.ws--nowrap
                          [total-cell invoice-id row-id]]

                         [:td.pad--v05.pad--h1.txt--center
                          (when extra?
                            [components/click-link
                             {:text-loc (if expanded?
                                          :price-catalogue.product-constants.close
                                          :price-catalogue.product-constants.open)
                              :click    #(>evt [::data/toggle-extra-info invoice-id
                                                :id row-id])}])]
                         (when edit?
                           [:td.pad--v05.pad--h1.txt--center
                            [components/icon-button {:text-loc   :remove
                                                     :icon-only? true
                                                     :icon       :lupicon-remove
                                                     :class      :tertiary
                                                     :on-click   #(>evt [::data/delete-row-confirm
                                                                         invoice-id row-id])}]])]
        info-row (when extra-expanded?
                   [:tr.bd--h-black.bd--b-black.bd--t-gray
                    {:class color-cls}
                    [:td.pad--h1 {:col-span col-count}
                     [:div.flex--column.pad--1
                      [extra-info invoice-id row-id]]]])]
    [error-row data-row info-row]))


(defn product-select [invoice-id operation-id]
  [reselect/reselect
   nil
   {:items      (<sub [::data/product-items invoice-id])
    :choose-loc :price-catalogue.add-product-row
    :render?    false
    :sort?      false
    :class      :w--30rem
    :callback   #(>evt [::data/add-row invoice-id operation-id %])}])

(defn operation-table [invoice-id op]
  (let [edit?           (<sub [::data/editing? invoice-id])
        show-discounts? (<sub [::data/show-discounts? invoice-id (:id op)])
        vat?            (<sub [::data/show-vat-info? invoice-id])
        row-ids         (seq (<sub [::data/operation-row-ids invoice-id (:id op)]))
        col-count       (cond-> 6
                          edit?           inc
                          show-discounts? inc)]
    [:table.reset
     [:caption.txt--left.txt--bold
      [:div.flex--wrap.flex--gap1.flex--align-center.gap--v2
       [:span.txt--bold (:text op)]
       (when edit?
         [components/icon-button {:class      :tertiary
                                  :icon       :lupicon-remove
                                  :icon-only? true
                                  :title-loc  :invoice.remove-operation
                                  :on-click   #(>evt [::data/remove-operation-confirm invoice-id op])}])]]
     (when row-ids
       [:thead
        [:tr
         [:th.pad--h2.ws--nowrap.w--100.txt--left
          (loc :price-catalogue.product)]
         [:th.pad--h2.ws--nowrap.w--min-8rem.txt--right
          (loc :invoices.rows.amount)]
         [:th.pad--h2.ws--nowrap.txt--left
          (loc :invoices.rows.unit)]
        [:th.pad--h2.ws--nowrap.txt--right
         (loc :invoices.rows.unit-price)]
         (when show-discounts?
           [:th.pad--h2.ws--nowrap.txt--right
            (loc :invoices.rows.discount-percent)])
         (when vat?
           [:<>
            [:th.pad--h2.ws--nowrap.txt--right
             (loc :invoices.rows.product-constants.names.alv)]
            [:th.pad--h2.ws--nowrap.txt--right
             (loc :invoices.wo-taxes)]])
         [:th.pad--h2.ws--nowrap.txt--right
          (loc :invoices.rows.total)]
         (when (<sub [::data/operation-extra-info? invoice-id (:id op)])
          [:th.pad--h2.ws--nowrap.w--min-12rem
           [components/click-link
            {:text-loc :lisatiedot._group_label
             :click    #(>evt [::data/toggle-extra-info invoice-id :operation-id (:id op)])}]])
         (when edit?
           [:th.pad--h2.ws--nowrap (loc :remove)])]])
     (->> row-ids
          (map-indexed (fn [i row-id] [i row-id]))
          (mapcat (fn [[i row-id]]
                    (operation-table-rows invoice-id i row-id show-discounts?)))
          (filter identity)
          (into [:tbody]))
     (when edit?
       [:tfoot
        [:tr
         [:td.pad--v1 {:col-span col-count}
          [product-select invoice-id (:id op)]]]])]))

(defn operations [invoice-id]
  (into [:div.flex--column.flex--gap2]
        (for [op (<sub [::data/operations invoice-id])]
          [operation-table invoice-id op])))

(defn delete-invoice-button [invoice-id]
  (when (<sub [::data/editing? invoice-id])
    [components/icon-button {:class    :plain
                             :icon     :lupicon-trash
                             :text-loc :invoice.delete
                             :on-click #(>evt [::data/delete-invoice-confirm invoice-id])}]))

(defn sum-and-state [invoice-id]
  (let [{:keys [state rows
                sum-vat-info]} (<sub [::data/invoice invoice-id])
        vat?                   (<sub [::data/show-vat-info? invoice-id])
        auth?                  (<sub [:auth/application? :set-invoice-field])
        total                  (<sub [::data/invoice-total-cents invoice-id])
        ok?                    (<sub [::data/invoice-ok? invoice-id])
        draft?                 (and (= state "draft") auth?)
        checked?               (and (= state "checked") auth?)]
    [:div.flex--wrap.flex--between.flex--align-center.flex--gap2
     (cond
       (empty? rows) nil

       vat?
       (let [tr (fn [loc-key key-or-num]
                  [:tr
                   {:class (common/css-flags :bd--t-black (number? key-or-num)
                                             :txt--inc2 (number? key-or-num))}
                   [:td.txt--bold.pad--r2 (loc loc-key)]
                   [:td.txt--bold.txt--right
                    (biller-util/cents->eur (cond->> key-or-num
                                              (keyword? key-or-num)
                                              sum-vat-info))]])]
         [:table.reset.gap--l2
          [:tbody
           [tr :invoices.taxfree-total :taxfree-cents]
           [tr :invoices.rows.product-constants.names.alv :vat-cents]
           [tr :invoice.end-sum total]]])

       :else
       [:span.txt--bold.txt--inc2
        (str (loc :invoice.end-sum) " " (biller-util/cents->eur total {:zeros? false}))])
     (when draft?
       [:button.primary
        {:disabled (not ok?)
         :on-click #(>evt [::data/set-invoice-field invoice-id :state "checked"])}
        (loc :invoices.state.checked.next)])
     (when checked?
       [:button.secondary
        {:on-click #(>evt [::data/set-invoice-field invoice-id :state "draft"])}
        (loc :invoices.state.draft.previous)])]))

(defn catalogue-select [invoice-id]
  (let [price-catalogue-id (<sub [::data/invoice invoice-id :price-catalogue-id])
        cat-items          (<sub [::data/catalogue-items])
        cat-id             (str invoice-id "-catalogue")]
    (if cat-items
        [label-wrap :price-catalogue.tab.title cat-id
         [components/dropdown
          price-catalogue-id
          {:id       cat-id
           :items    cat-items
           :class    :w--min-25rem
           :callback #(>evt [::data/set-invoice-field invoice-id :price-catalogue-id %])}]]
        [:div.danger-note (loc :error.price-catalogue.not-available)])))

(defn invoice-toprow
  "Delete and print buttons. Last modified."
  [invoice-id]
  (let [modified (<sub [::data/invoice invoice-id :modified])]
    [:div.flex--wrap.flex--row-reverse.flex--between.flex--align-center.flex--gap2
     [:div.flex--right.flex--align-center.flex--wrap.flex--row-gap1.flex--column-gap2
       [:div.fg--gray
        (str (loc :applications.lastModified) " "
             (biller-util/datetime-format modified :medium-datetime))]
       [:a.btn.secondary
        {:href   (<sub [::data/pdf-link invoice-id])
         :target :_blank}
        [:i.lupicon-print]
        [:span (loc :print)]]]
     [delete-invoice-button invoice-id]]))

(defn internal-invoice [invoice-id]
  (let [edit?     (<sub [::data/editing? invoice-id])
        internal? (<sub [::data/invoice invoice-id :organization-internal-invoice?])]
    (cond
      edit?
      [components/toggle
       internal?
       {:text-loc :invoice.pdf.title.internal
        :prefix   :blockbox
        :class    :flex--self-end
        :callback #(>evt [::data/set-invoice-field invoice-id :organization-internal-invoice? %])}]

      internal?
      [:div.primary-note (loc :invoice.pdf.title.internal)])))

(defn worktime [invoice-id]
  (when (<sub [::data/config :worktime?])
    (let [auth?              (<sub [:auth/application? :update-invoice-worktime])
          {:keys [work-start-ms work-end-ms
                  workdays]} (<sub [::data/invoice invoice-id])
          {:keys [billable-days free-days
                  error]}    workdays
          start-id           (str invoice-id "-starttime")
          end-id             (str invoice-id "-endtime")
          free-id            (str invoice-id "-free-days")
          billable-id        (str invoice-id "-billable-days")
          day-string         #(goos/format "%3d %s" %
                                           (loc (if (= % 1)
                                                  :invoices.ya.day
                                                  :unit.pv)))
          date-callback      (fn [k]
                               #(>evt [::data/set-worktime invoice-id k
                                       (some-> % (.valueOf))]))]
      [:div.flex--wrap.flex--gap1.flex--align-center
       [label-wrap :invoice.worktime.start start-id
        [day-edit/day-edit
         work-start-ms
         {:id       start-id
          :enabled? auth?
          :invalid? (boolean error)
          :callback (date-callback :work-start-ms)}]]
       [label-wrap :invoice.worktime.end end-id
        [day-edit/day-edit
         work-end-ms
         {:id       end-id
          :enabled? auth?
          :invalid? (boolean error)
          :callback (date-callback :work-end-ms)}]]
       (when (some-> free-days pos?)
         [label-wrap
          :invoices.ya.non-billable-days
          free-id
          [:span.lux.txt--center
           {:id free-id}
           (day-string free-days)]])
       (when (some-> billable-days number?)
         [label-wrap
         :invoices.ya.billable-work-time
         billable-id
          [:span.lux.txt--center
           {:id billable-id}
           (day-string billable-days)]])
       (when error
         [:div.error-note.pad--4.flex--self-end
          {:style {:margin 0}}
          [:span.gap--v2.txt--inc1 (loc error)]])])))

(defn invoice-editor [invoice-id]
  (let [content-id (str invoice-id "-content")]
    [:div.flex--column.flex--gap2.pad--2
     {:id content-id}
     [invoice-toprow invoice-id]
     [worktime invoice-id]
     [catalogue-select invoice-id]
     [:div.flex--wrap.flex--between.flex--gap1.flex--align-center
      [add-operation-select invoice-id]
      [backend-code invoice-id]
      [internal-invoice invoice-id]]
     [operations invoice-id]
     [sum-and-state invoice-id]]))

(defn invoice-viewer [invoice-id]
  (let [content-id (str invoice-id "- content")]
    [:div.flex--column.flex--gap2.pad--2
     {:id content-id}
     [invoice-toprow invoice-id]
     [worktime invoice-id]
     [:div.flex--wrap.flex--row-gap1.flex--column-gap2.flex--align-center
      [internal-invoice invoice-id]
      [backend-code invoice-id true]]
     [operations invoice-id]
     [sum-and-state invoice-id]]))

(defn invoice [invoice-id]
  (let [{:keys [created state]} (<sub [::data/invoice invoice-id])
        content-id              (str invoice-id "-content")
        expanded?               (<sub [::data/invoice-meta-value invoice-id :expanded?])
        edit?                   (<sub [::data/editing? invoice-id])]
    [:div
     [:button.no-bottom-radius.flex--align-center.w--100.pad--1.of--hidden-x
      {:style         {:max-height  :none
                       :line-height :initial}
       :class         (case (keyword state)
                        :draft       :secondary
                        :checked     :primary
                        :confirmed   :plain
                        :transferred :plain)
       :aria-expanded expanded?
       :aria-controls content-id
       :on-click      #(>evt [::data/set-invoice-meta-value invoice-id
                              :expanded? (not expanded?)])}
      [:i {:class       (if expanded?
                          :lupicon-chevron-up
                          :lupicon-chevron-down)
           :aria-hidden true}]
      [:div.flex--wrap.w--100.flex--between
       [:span.w--12rem.pad--r2 (str (loc :common.created) " " (biller-util/datetime-format created :medium-datetime))]
       [:span.pad--r2 [:span (path/loc :invoices.state state)]]]]
     (when expanded?
       [:div.bd--h-night-sky.bd--b-night-sky.bg--gray
        [(if edit?
           invoice-editor
           invoice-viewer) invoice-id]])]))

(defn invoices []
  (into [:div.flex--column.flex--gap2]
        (for [{:keys [id]} (<sub [::data/invoices])]
          [invoice id])))

(defn view  []
  (r/with-let [_ (>evt [::data/initialize])
               _ (>evt [::data/fetch-invoices])
               _ (>evt [:auth/refresh])]
    [:div.pad--4.flex--column.flex--row-gap4
     [header]
     [invoices]]
    (finally
      (>evt [::data/clear]))))

(defonce mounter (ui-dom/init :reagent (constantly view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
