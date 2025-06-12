(ns lupapalvelu.ui.biller.invoice
  (:require [goog.functions :as goof]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.biller.data :as data]
            [lupapalvelu.ui.biller.util :as biller-util]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.reselect :as reselect]
            [reagent.core :as r]
            [sade.shared-schemas :refer [e-invoice-operators]]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn field-loc [field-key]
  (case field-key
    :sap-number        :invoices.general.client-number
    :first-name        :etunimi
    :last-name         :sukunimi
    :protected?        :henkilo.turvakieltoKytkin
    :phone             :puhelin
    :email             :email
    :entity-address    :osoite-maksaja
    :postal-code       :postinumero
    :city              :userinfo.city
    :country           :country.country
    :billing-reference :osapuoli.laskuviite
    :netbill           :register.company.form.netbill
    :entity-name       :invoices.general.company-name
    :transfer-batch-id :transfer-batch
    (util/kw-path :invoices.general field-key)))

(defn make-id [invoice-id field-key]
  (str invoice-id "-" (name field-key)))

(defn label-wrap
  ([text-loc for-id component {:keys [col-span required?]}]
   [(util/kw-path :div.flex--column.pad--h2
                  (case col-span
                    2 :w--40rem
                    :w--20rem))
    [:label.lux.txt--bold
     {:class (common/css-flags :required required?)
      :for for-id} (loc text-loc)]
    component])
  ([text-loc for-id component]
   [label-wrap text-loc for-id component nil]))

(defn static-text
  [label-loc for-id text-or-child]
  [label-wrap label-loc for-id
   [:span.lux.w--100 {:id for-id} text-or-child]])

(defn- app-link
  [application-id]
  [:a {:href   (str "#!/application/" application-id "/invoice")
       :target "_blank"}
   application-id])

(defn- application-link-field
  [invoice-id label-loc]
  (let [application-id (<sub [::data/invoice invoice-id :application-id])
        id             (make-id invoice-id :application-id)]
    [static-text label-loc id
     [app-link application-id]]))

(defn text-editor
  ([invoice-id field-key {:keys [disabled?] :as opts}]
   (let [{:keys [value error]} (<sub [::data/invoice-field invoice-id field-key])
         id    (make-id invoice-id field-key)]
     [label-wrap
      (field-loc field-key)
      id
      [components/text-edit
       value
       (merge {:callback (goof/debounce #(>evt [::data/set-invoice-field
                                                invoice-id field-key %])
                                        500)
               :id       id
               :disabled disabled?
               :invalid? error
               :class    :w--100}
              (dissoc opts :col-span :disabled?))]
      opts]))
  ([invoice-id field-key]
   [text-editor invoice-id field-key nil]))

(defn select-editor
  [invoice-id field-key opts]
  (let [field (<sub [::data/invoice invoice-id field-key])
        id    (make-id invoice-id field-key)]
     [label-wrap
      (field-loc field-key)
      id
      [components/dropdown field (merge {:callback #(>evt [::data/set-invoice-field
                                                           invoice-id field-key %])
                                         :id       id
                                         :class    :w--100}
                                        (dissoc opts :col-span))]
      opts]))

(defn toggle-editor
  [invoice-id field-key {:keys [disabled?]}]
  (let [field? (boolean (<sub [::data/invoice invoice-id field-key]))]
    [components/toggle
     field?
     {:id       (make-id invoice-id field-key)
      :prefix   :blockbox
      :disabled? disabled?
      :text-loc (field-loc field-key)
      :callback #(>evt [::data/set-invoice-field invoice-id field-key %])}]))

(defn transfer-batch-editor
  [invoice-id]
  (let [{:keys [state transfer-batch-id]} (<sub [::data/invoice invoice-id])]
    (when transfer-batch-id
      (let [items (<sub [::data/batch-candidates invoice-id])]
        (if (= state "confirmed")
          [select-editor invoice-id :transfer-batch-id
           {:col-span 2
            :items    items
            :choose?  false
            :disabled? (< (count items) 2)}]
          [static-text :transfer-batch
           (make-id invoice-id :transfer-batch-id)
           (:text (util/find-by-key :value transfer-batch-id items))])))))

(defn backend-code [invoice-id]
  (let [{:keys [backend-code backend-id]} (<sub [::data/invoice invoice-id])
        {:keys [backend-codes]}           (<sub [::data/current-organization])]
    (cond
      (not backend-codes) nil

      backend-id [static-text
                  (field-loc :backend-code)
                  (make-id invoice-id :backend-id)
                  (biller-util/backend-text {:text (->> backend-codes
                                                        (util/find-by-key :code backend-code)
                                                        :text)}
                                            backend-id)]
      :else      [select-editor invoice-id :backend-code
                  {:sort-by :text
                   :items   (map (fn [{:keys [code] :as item}]
                                   {:value code
                                    :text  (biller-util/backend-text item)})
                                 backend-codes)} ])))

(defn basic-info-editor [invoice-id]
  (let [{:keys [client-number-required?]} (<sub [::data/current-organization])]
    [:div.flex--wrap.flex--row-gap2.flex--align-start
     [application-link-field invoice-id :invoices.general.permit-number]
     [static-text :common.created (make-id invoice-id :created)
      (<sub [::data/invoice-created-title invoice-id])]
     [text-editor invoice-id :sap-number
      {:required? client-number-required?}]
     [:div.pad--h2.w--20rem.flex--self-end
      [toggle-editor invoice-id :organization-internal-invoice?]]
     [text-editor invoice-id :letter]
     [text-editor invoice-id :case-reference]
     [text-editor invoice-id :partner-code]
     [backend-code invoice-id]
     [text-editor invoice-id :description {:col-span 2}]
     [text-editor invoice-id :internal-info {:lines 5 :col-span 2}]
     [transfer-batch-editor invoice-id]]))

(defn- just-disabled [options]
  (select-keys options [:disabled?]))

(defn subtitle [loc-key]
  [:h3.w--100.pad--h2.gap--t2 (loc loc-key)])

(defn name-editor [invoice-id {:keys [contact?] :as options}]
  (let [options (just-disabled options)]
    [:div.flex--wrap.flex--row-gap2
     (when contact?
       [subtitle :invoices.general.company-contact-person])
     [text-editor invoice-id :first-name options]
     [text-editor invoice-id :last-name options]
     [:div.flex--self-end.pad--h2
      [toggle-editor invoice-id :protected? options]]]))

(defn contact-editor [invoice-id {:keys [contact?] :as options}]
  (let [options (just-disabled options)]
    [:div.flex--wrap.flex--row-gap2
     [text-editor invoice-id :phone options]
     [text-editor invoice-id :email options]
    (when-not contact?
      [text-editor invoice-id :person-id options])]))

(def ^:private country-items (update-in (vec (common/country-items)) [1]
                                        merge {:flags #{:divide}}))

(defn country-editor
  [invoice-id options]
  (let [field (<sub [::data/invoice invoice-id :country])
        id    (make-id invoice-id :country)]
    [label-wrap
     (field-loc :country)
     id
     [reselect/reselect
      field
      (merge {:callback #(>evt [::data/set-invoice-field
                                invoice-id :country %])
              :id       id
              :items    country-items
              :sort?    false
              :class    :w--100}
             options)]]))

(defn address-editor [invoice-id options]
  (let [options (just-disabled options)]
    [:div.flex--wrap.flex--row-gap2
     [text-editor invoice-id :entity-address (assoc options :col-span 2)]
     [text-editor invoice-id :postal-code options]
     [text-editor invoice-id :city options]
     [country-editor invoice-id options]]))

(defn person-payer-editor [invoice-id options]
  (let [options (just-disabled options)]
    [:div.flex--column.flex--row-gap2
     [name-editor invoice-id options]
     [contact-editor invoice-id options]
     [address-editor invoice-id options]
     [text-editor invoice-id :billing-reference options]]))

(defn netbill-editor [invoice-id options]
  (let [options (just-disabled options)]
    [:div.flex--wrap.flex--row-gap2
     [subtitle :osapuoli.yritys.verkkolaskutustieto._group_label]
     [text-editor invoice-id :netbill options]
     [text-editor invoice-id :ovt options]
     [select-editor invoice-id :operator
      (merge options
             {:col-span 2
              :class    :w--30rem
              :sort-by  :text
              :items    (map #(hash-map :value %
                                        :text (biller-util/operator-string %))
                             e-invoice-operators)})]]))

(defn company-payer-editor [invoice-id options]
  (let [options (just-disabled options)
        contact (assoc options :contact? true)]
   [:div.flex--column.flex--row-gap2
    [:div.flex--wrap.flex--row-gap2
     [text-editor invoice-id :entity-name (assoc options :col-span 2)]
     [text-editor invoice-id :company-id options]]
    [address-editor invoice-id options]
    [name-editor invoice-id contact]
    [contact-editor invoice-id contact]
    [netbill-editor invoice-id options]
    [text-editor invoice-id :billing-reference options]]))

(defn payer-editor [invoice-id]
  (let [{:keys [payer-type application-payer?]} (<sub [::data/invoice invoice-id])]
    [:div.flex--wrap.flex--row-gap2.flex--align-start.bd--t-gray.gap--t2.pad--t2
     (when application-payer?
       [common/loc-html-reagent
        :div.primary-note.gap--h2
        :invoice.application-payer.note])
     [:div.pad--l2.flex--wrap.flex--row-gap2
      [:div.flex.pad--r4.w--40rem.flex--column-gap1
       [components/toggle-group
        #{payer-type}
        {:items    [{:text-loc :osapuoli._selected.henkilo
                     :value    "person"
                     :icon     :lupicon-user}
                    {:text-loc :osapuoli._selected.yritys
                     :value    "company"
                     :icon     :lupicon-building}]
         :prefix   :person-vs-company
         :disabled? application-payer?
         :radio?   true
         :callback #(>evt [::data/set-invoice-field invoice-id :payer-type %])}]]
      [:div.w--20rem
       [toggle-editor invoice-id :application-payer?]]]
     (if (= payer-type "person")
       [person-payer-editor invoice-id {:disabled? application-payer?}]
       [company-payer-editor invoice-id {:disabled? application-payer?}])]))

(defn invoice-edit-bar [invoice-id]
  (let [can-edit? (<sub [:auth/global? :set-invoice-field])
        editing? (<sub [::data/invoice-meta-value invoice-id :editing?])]
    (when can-edit?
      [:div.flex--g1.gap--t1.flex--align-end.flex--right
       [:button
        {:class (if editing? :secondary :primary)
         :on-click #(>evt [::data/set-invoice-meta invoice-id :editing? (not editing?)])}
        (loc (if editing? :close :edit))]])))

(defn note-bar [& notes]
  (some->> (for [[cls txt] notes
                 :when txt]
             [:div.dsp--inline-block.pad--r2 {:class cls} txt])
           seq
           (into [:div.flex--wrap.flex--gap1])))

(defn view-block [loc-key value {:keys [class value-class]}]
  (when-not (ss/blank? value)
    [:div.flex.flex--gap1
     {:class class}
     [:span.dsp--inline-block.w--min-12rem (when-not (= loc-key :empty)
                                             (str (loc loc-key) ":"))]
     [:span.dsp--inline-block
      {:class value-class}
      value]]))

(defn info-view
  "Expanded view with all the essential basic/payer information and edit button (if authed)."
  [invoice-id]
  (let [{:keys [payer-type entity-name company-id person-id first-name last-name
                entity-address city postal-code country netbill ovt operator
                billing-reference application-id phone email]
         :as   invoice}         (util/strip-blanks (<sub [::data/invoice invoice-id]))
        {:keys [client-number-required?
                backend-codes]} (<sub [::data/current-organization])
        internal-note           (when (:organization-internal-invoice? invoice)
                                  [:primary-note (loc :invoice.pdf.title.internal)])
        client-note             (when (and client-number-required? (not (:sap-number invoice)))
                                  [:danger-note (loc :invoice.field-missing
                                                     (loc :invoices.batch.client-number))])
        payer-note              (when (:application-payer? invoice)
                                  [:primary-note (loc :invoice.payer.use-application)])
        payer-error-note        (when (some-> invoice :errors not-empty)
                                  [:danger-note (loc :invoice.payer.errors)])
        protected               (when (:protected? invoice)
                                  (loc :invoice.protected))
        address                 (fn []
                                  [view-block :osoite
                                   (ss/trim-join ", "
                                                 entity-address
                                                 (ss/join-non-blanks " " [postal-code city])
                                                 (some->> country (path/loc :country)))])
        person-name             (ss/trim-join " " first-name last-name)
        contact                 (ss/trim-join ", " phone email)]
    [:div.flex--wrap.flex--gap1
     (some->> (for [k     [:sap-number :letter :case-reference :partner-code :backend-code :description
                           :transfer-batch-id]
                    :let  [v (k invoice)]
                    :when v]
                [(field-loc k)
                 (case k
                   :backend-code
                   (if-let [item (util/find-by-key :code v backend-codes)]
                     (biller-util/backend-text item (:backend-id invoice))
                     (biller-util/unknown-backend-code v))

                   :transfer-batch-id
                   (<sub [::data/transfer-batch-string v])
                   v)])
              (cons [:invoices.general.permit-number
                     [app-link application-id]])
              (map (fn [[k v]]
                     [view-block k v {:value-class :w--12rem.ws--nowrap}]))
              (into [:div.flex--column
                     [:div.flex--wrap.flex--column-gap2.flex--align-center
                      [:h4.pad--v2 (loc :invoice.pdf.title)]
                      [note-bar client-note internal-note]]]))

     (into [:div.flex--column
            [:div.flex--wrap.flex--column-gap2.flex--align-center
             [:h4.pad--v2 (loc :notice-form.payer)]
             [note-bar payer-error-note payer-note]]]
           (if (= payer-type "company")
             [[view-block :company (ss/trim-join " " entity-name (biller-util/id-string company-id))]
              [address]
              [view-block :verdict.yhteyshenkilo (ss/trim-join " " person-name protected)]
              [view-block :empty contact]
              [view-block :osapuoli.yritys.verkkolaskutustieto._group_label
               (ss/join-non-blanks ", "
                                   [netbill ovt
                                    (some->> (util/find-first #(= operator %) e-invoice-operators)
                                             biller-util/operator-string)])]
              [view-block (field-loc :billing-reference) billing-reference]]
             [[view-block :osapuoli._selected.henkilo
               (ss/trim-join " " person-name (biller-util/id-string person-id) protected)]
              [view-block :empty contact]
              [address]
              [view-block (field-loc :billing-reference) billing-reference]]))
     [invoice-edit-bar invoice-id]]))

(defn invoice-table-rows [invoice-id idx {:keys [discount-percent comment] :as row}]
  (let [extra?          (data/extra-info? row)
        expanded?       (<sub [::data/invoice-meta-value invoice-id :show-extra? (:id row)])
        extra-expanded? (and extra? expanded?)
        color-cls       (if (even? idx) :bg--white :bg--gray)
        data-row        [:tr.bd--h-black.bd--t-black
                         {:class (common/css color-cls
                                             (when-not extra-expanded?
                                               :bd--b-black) )}
                         [:td.pad--h2 (ss/join-non-blanks " " [(:code row) (:text row)])]
                         [:td.pad--h2.txt--right (:units row)]
                         [:td.pad--h2 (path/loc :unit (:unit row))]
                         [:td.pad--h2.txt--right (biller-util/euro-format (:price-per-unit row))]
                         [:td.pad--h2.txt--right discount-percent]
                         [:td.pad--h2.txt--right (biller-util/cents->eur (:total-cents row))]
                         [:td.pad--h1.txt--center
                          (when extra?
                            [components/click-link
                             {:text-loc (if expanded?
                                          :price-catalogue.product-constants.close
                                          :price-catalogue.product-constants.open)
                              :click    #(>evt [::data/set-in-invoice-meta invoice-id :show-extra?
                                                (:id row) (not expanded?)])}])]]
        info-row        (when extra-expanded?
                          [:tr.bd--h-black.bd--b-black.bd--t-gray
                           {:class color-cls}
                           [:td.pad--h1 {:col-span 7}
                            [:div.flex--column.pad--1
                             (when (and discount-percent (ss/not-blank? comment))
                               [view-block :invoices.rows.comment comment])
                             (some->> (util/strip-blanks (:product-constants row))
                                      not-empty
                                      (map (fn [[k v]]
                                             [view-block (util/kw-path :invoices.rows.product-constants.names k) v]))
                                      (sort-by (comp loc second))
                                      (into [:<> [:h4.gap--t1 (loc :invoices.rows.product-constants)]]))]]])]
    [data-row info-row]))

(defn invoice-operation-table [invoice-id op]
  (let [rows (<sub [::data/invoice-rows invoice-id (:id op)])]
    [:table.reset
     [:caption.txt--left.txt--bold (path/loc :operations (:name op))]
     [:thead
      [:tr
       [:th.pad--h2.ws--nowrap.w--100.txt--left (loc :price-catalogue.product)]
       [:th.pad--h2.ws--nowrap (loc :invoices.rows.amount)]
       [:th.pad--h2.ws--nowrap (loc :invoices.rows.unit)]
       [:th.pad--h2.ws--nowrap (loc :invoices.rows.unit-price)]
      [:th.pad--h2.ws--nowrap (loc :invoices.rows.discount-percent)]
       [:th.pad--h2.ws--nowrap (loc :invoices.rows.total)]
       [:th.pad--h2.ws--nowrap.w--min-12rem
        (if (some data/extra-info? rows)
         [components/click-link
          {:text-loc :lisatiedot._group_label
           :click    #(>evt [::data/toggle-operation-extra-info invoice-id (:id op)])}]
         [:span (loc :lisatiedot._group_label)])]]]
     (->> rows
          (map-indexed (fn [i row] [i row]))
          (mapcat (fn [[i row]]
                    (invoice-table-rows invoice-id i row)))
          (filter identity)
          (into [:tbody]))]))

(defn state-change-button [invoice-id new-state loc-key & [fwd?]]
  [components/icon-button
   (-> (if fwd?
         {:class    :primary
          :icon     :lupicon-chevron-right
          :right?   true
          :enabled? (<sub [::data/invoice-fields-ok? invoice-id])}
         {:class :secondary
          :icon  :lupicon-chevron-left})
       (assoc :text-loc loc-key
              :on-click #(>evt [::data/change-invoice-state invoice-id new-state])))])

(defn invoice-operations [invoice-id]
  (let [{:keys [operations sum-total-cents
                state]} (<sub [::data/invoice invoice-id])
        can-edit?       (<sub [:auth/global? :set-invoice-field])
        op-divs         (for [op operations]
                          [invoice-operation-table invoice-id op])]
    [:div.flex--column.flex--row-gap2.pad--2.gap--b2.bd--t-night-sky
     (into [:<>] op-divs)
     [:div.flex--between.flex--gap1.flex--wrap
      [:div.flex.txt--bold.flex--column-gap2
       [:span (str (loc :invoice.end-sum) ":")]
       [:span (biller-util/cents->eur sum-total-cents)]]
      (when can-edit?
        (case state
          "checked"
          [:div.flex--wrap.flex--gap1
           [state-change-button invoice-id "draft" :pate-proposal.revert]
           [state-change-button invoice-id "confirmed" :invoices.state.confirmed.next true]]
          "confirmed"
          [state-change-button invoice-id "checked" :invoices.state.checked.previous]
          nil))]]))

(defn invoice-content [invoice-id expanded?]
  (let [{:keys [state internal-info modified]} (<sub [::data/invoice invoice-id])
        content-id                             (make-id invoice-id :content)
        {:keys [editing?]}                     (<sub [::data/invoice-meta-value invoice-id])]
    [:<>
     (if (and expanded? editing?)
       [:div.flex--column.flex--row-gap2.pad--2
        {:id content-id}
        [:div.flex--right.fg--gray
         (biller-util/datetime-format modified :medium-datetime)]
        [basic-info-editor invoice-id]
        [payer-editor invoice-id]
        [invoice-edit-bar invoice-id]]
       [:div.pad--1
        [:div.flex--between.flex--row-gap2
         [:div.flex--wrap.flex--column-gap1
          [:span.dsp--inline-block.w--20rem
           (str (loc :common.created) " " (<sub [::data/invoice-created-title invoice-id]))]
          [:span.ws--pre-wrap internal-info]]
         [:span (path/loc :invoices.state state)]]
        (when expanded?
          [info-view invoice-id])])
     (when expanded?
       [invoice-operations invoice-id])]))

(defn invoice [invoice-id]
  (let [{:keys [application-id enriched-data
                state]}     (<sub [::data/invoice invoice-id])
        content-id          (make-id invoice-id :content)
        {:keys [expanded?]} (<sub [::data/invoice-meta-value invoice-id])
        payer-name          (<sub [::data/payer-name invoice-id])]
    [:div
     [:button.no-bottom-radius.flex--align-center.w--100.pad--1.of--hidden-x
      {:style         {:max-height  :none
                       :line-height :initial}
       :class         (case (keyword state)
                        :checked     :primary
                        :confirmed   :plain
                        :transferred :plain-secondary)
       :aria-expanded expanded?
       :aria-controls content-id
       :on-click      #(>evt [::data/set-invoice-meta invoice-id
                              :expanded? (not expanded?)
                              :editing? false])}
      [:i {:class       (if expanded?
                          :lupicon-chevron-up
                          :lupicon-chevron-down)
           :aria-hidden true}]
      [:div.flex--wrap.w--100.flex--between
       [:div
        [:span.w--12rem.pad--r2 application-id]
        [:span.of--hidden-x (-> enriched-data :application :address)]]
       [:span.pad--r2.of--hidden-x.text-ellipsis payer-name]
       [:span.pad--r2.of--hidden-x (->> enriched-data :application :operation
                                        (path/loc :operations))]]]
     [:div.bd--h-night-sky.bd--b-night-sky.bg--gray
      [invoice-content invoice-id expanded?]]]))


(defn fetch-button []
  (r/with-let [waiting?* (atom false)]
    [components/icon-button
     {:class    :secondary
      :icon     :lupicon-circle-arrow-down
      :text-loc :invoices.general.fetch-older-invoices
      :wait?    waiting?*
      :on-click (fn []
                  ;; Fake wait to dissuade double clicking and give some feedback.
                  (reset! waiting?* true)
                  (common/start-delay #(reset! waiting?* false) 2000)
                  (>evt [::data/fetch-more-invoices]))}]))

(defn invoice-group [state]
  (let [invoice-ids (<sub [::data/invoice-ids state])]
    [:div.flex--column.flex--gap1
     [:h1 (loc (util/kw-path :invoices.title state))]
     (into [:div.flex--column.flex--row-gap1]
           (if invoice-ids
             (for [id invoice-ids]
               [invoice id])
             [[:span (loc :invoices.no-invoices)]]))
     ;; If no ids, the fetch would not bring new ones either. Or at least it would be very
     ;; unlikely.
     (when (and (= state :transferred) invoice-ids)
       [fetch-button])]))

(defn view []
  [:div.flex--column.flex--row-gap4
   [invoice-group :checked]
   [invoice-group :confirmed]
   [invoice-group :transferred]])
