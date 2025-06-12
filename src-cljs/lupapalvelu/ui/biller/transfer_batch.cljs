(ns lupapalvelu.ui.biller.transfer-batch
  (:require [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.biller.data :as data]
            [lupapalvelu.ui.biller.invoice :as invoice ]
            [lupapalvelu.ui.biller.util :as biller-util]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [reagent.core :as r]
            [sade.util :as util]))

(defn column-loc [column-key]
  (case column-key
    :application-id  :pate-verdict.application-id
    :address         :application.address
    :payer-name      :invoice.pdf.payer
    :payer-id        :transfer-batch.payer-id
    :sum             :sum
    :sap-number      :invoices.general.client-number
    :payer-address   :osoite-maksaja
    :payer-reference :osapuoli.laskuviite
    :payer-netbill   :osapuoli.yritys.verkkolaskutustieto._group_label
    :payer-contact   :verdict.yhteyshenkilo
    :internal?       :invoice.pdf.title.internal
    (util/kw-path :invoices.general column-key)))

(defn can-edit?
  "We check auth for some suitable action"
  []
  (<sub [:auth/global? :new-transfer-batch]))

(defn column-class [column-key]
  (common/css (case column-key
                :application-id :txt--left.ws--nowrap
                :sum            :txt--right
                :internal?      :txt--center
                :txt--left)))

(defn invoice-rows [batch-id idx invoice-id]
  (let [columns   (<sub [::data/batch-column-values invoice-id])
        col-count (+ 2 (count columns))
        color-cls (if (even? idx) :bg--white :bg--gray)
        expanded? (<sub [::data/batch-meta-value batch-id :expanded invoice-id])
        data-row  (->> columns
                       (map (fn [{:keys [key value]}]
                              [:td.pad--h2 {:class (column-class key)} value])))]
    (cond-> [(into [:tr.bd--h-night-sky
                    {:class [color-cls (when-not expanded?
                                         :bd--b-night-sky)]}]
                   (concat [[:td.pad--h2.txt--right (inc idx)]]
                           data-row
                           [[:td.txt--center
                             [components/click-link
                              {:text-loc (if expanded?
                                           :price-catalogue.product-constants.close
                                           :price-catalogue.show)
                               :click    #(>evt [::data/toggle-batch-invoice
                                                 batch-id invoice-id])}]]]))]
      expanded? (conj [:tr.bd--h-night-sky.bd--b-night-sky {:class color-cls}
                       [:td.pad--h2 {:col-span col-count}
                        [invoice/invoice-content invoice-id true]]]))))

(defn invoice-table [batch-id]
  (let [{:keys [invoice-ids]} (<sub [::data/transfer-batch batch-id])
        columns               (<sub [::data/batch-column-keys :selected])
        header-row            (concat [[:th.pad--h2.txt--right "#"]]
                                      (map (fn [col-key]
                                             [:th.pad--h2.ws--nowrap
                                              {:class (column-class col-key)}
                                              (loc (column-loc col-key))])
                                           columns)
                                      [[:th.pad--h2
                                        [components/click-link
                                         {:text-loc :invoice.pdf.title
                                          :click    #(>evt [::data/toggle-batch-invoice batch-id :all])}]]])]
    [:table.reset
     [:thead (into [:tr.bd--b-night-sky] header-row)]
     (some->> invoice-ids
              (map-indexed #(invoice-rows batch-id %1 %2))
              (apply concat)
              (into [:tbody]))]))

(defn integration-buttons [batch-id]
  (let [{:keys [sap? general?]} (<sub [::data/current-organization])
        excel-link              (<sub [::data/transfer-batch-download-link batch-id :sap-excel?])
        xml-link                (<sub [::data/transfer-batch-download-link batch-id :download?])
        auth?                   (can-edit?)]
    [:div.flex--wrap.flex--gap1
     (when excel-link
       [:a.btn.primary {:href excel-link}
        [:i.lupicon-download]
        [:span (loc :invoices.batch.load-excel)]])
     (when xml-link
       [:a.btn.primary {:href xml-link}
        [:i.lupicon-download]
        [:span (loc :invoices.batch.download-xml)]])
     (when (or sap? general?)
       [components/icon-button {:text-loc (if sap?
                                            :invoices.batch.send-idoc-xml
                                            :invoices.batch.send-into-invoicing-system)
                                :enabled? auth?
                                :class    :primary
                                :icon     :lupicon-circle-arrow-right
                                :on-click #(>evt [::data/send-transfer-batch-confirmation batch-id])}])]))

(defn transfer-batch [batch-id]
  (let [{:keys [invoice-ids state rowcount total-cents]
         :as   batch} (<sub [::data/transfer-batch batch-id])
        nbsp          [:span common/nbsp]
        one-many      (fn [n k]
                        [:span.ws--nowrap [:span.txt--bold n]
                         nbsp
                         (path/loc :transfer-batch.total k
                                   (if (= n 1) :one :many))
                         "," nbsp])]

    [:div.flex--column.flex--row-gap1.bd--night-sky.pad--1.bg--gray
     {:id batch-id}
     [:div.flex--between.flex--wrap.flex--gap1.flex--align-center
      [:h2.txt--inc1
       [components/pen-input
        {:value       (:name batch)
         :enabled?    (can-edit?)
         :callback    #(>evt [::data/rename-transfer-batch batch %])
         :placeholder (loc :transfer-batch.no-name)}]]
      [:div.flex--wrap.flex--gap1.flex--align-center
       [:span (path/loc :invoices.batch state)]
       (when (and (can-edit?) (seq invoice-ids))
         (if (= state "open")
           [:button.primary
            {:on-click #(>evt [::data/set-transfer-batch-state-confirm batch :closed])}
            (loc :invoices.batch.close)]
           [:button.secondary
            {:on-click #(>evt [::data/set-transfer-batch-state-confirm batch :open])}
            (loc :invoices.batch.do-open)]))]]
     (into [:div.flex--wrap.flex--column-gap0.flex--row-gap1]
           (for [k     [:created :sent :closed]
                 :let  [ts (k batch)
                       id (str batch-id "-" (name k))]
                 :when ts]
             [:div.flex--column
              [:label.txt--bold {:for id} (path/loc :common k)]
              [:span.bd--t-black.pad--r2 {:id id} (biller-util/datetime-format ts :medium-datetime)]]))
     (if (seq invoice-ids)
       [:div.flex--column.flex--row-gap2
        [invoice-table batch-id]
        [:div.flex--wrap.flex--between.flex--gap1.flex--align-center
         [:div.flex--wrap
          [one-many (count invoice-ids) :invoices]
          [one-many rowcount :rows]
          [:span.ws--nowrap (loc :invoices.batch.total-euros) nbsp
           [:span.txt--bold (biller-util/cents->eur total-cents)]]]
         [integration-buttons batch-id]]]
       [:div.flex--between.flex--gap1.flex--wrap
        [:div.primary-note (loc :transfer-batch.empty)]
        [components/icon-button {:text-loc :transfer-batch.remove
                                 :enabled? (can-edit?)
                                 :icon     :lupicon-remove
                                 :class    :plain
                                 :on-click #(>evt [::data/delete-transfer-batch-confirm batch])}]])]))

(defn fetch-button []
  (r/with-let [waiting?* (atom false)]
    [components/icon-button {:class    :secondary
                             :icon     :lupicon-circle-arrow-down
                             :text-loc :invoices.batch.load-more
                             :wait?    waiting?*
                             :on-click (fn []
                                         ;; Fake wait to dissuade double clicking and give some feedback.
                                         (reset! waiting?* true)
                                         (common/start-delay #(reset! waiting?* false) 2000)
                                         (>evt [::data/fetch-more-batches]))}]))

(defn batch-group [state]
  (let [batch-ids (<sub [::data/batch-ids state])
        open?     (= state :open)]
    [:div.flex--column.flex--row-gap2
     [:div.flex--between.flex--gap1.flex--align-center.flex--wrap
      [:h1.gap--b0 (loc (util/kw-path :biller.batch-title state))]
      (when open?
        [components/icon-button {:text-loc :transfer-batch.create
                                 :class    :primary
                                 :icon     :lupicon-circle-plus
                                 :on-click #(>evt [::data/new-transfer-batch])}])]
     (into [:div.flex--column.flex--row-gap2]
           (if batch-ids
             (for [id batch-ids]
               [transfer-batch id])
             [[:span (loc (if open?
                            :biller.no-open-batches
                            :biller.no-batches))]]))
     ;; If there aren't any closed batches, the fetching would not change the situation.
     (when (and (not open?) batch-ids)
       [fetch-button])]))

(defn column-toggles []
  (when (<sub [::data/non-empty-batches?])
    [:div.flex--wrap.flex--gap1
     [components/toggle-group
      (<sub [:value/get ::data/selected-columns])
      {:items    (map (fn [k]
                        {:text-loc (column-loc k)
                         :value    k})
                      (<sub [::data/batch-column-keys :all]))
       :callback #(>evt [:value/set ::data/selected-columns %])
       :prefix   :filter-tag
       :class    :wrapper--small}]]))

(defn view []
  [:div.flex--column.flex--row-gap4
   [column-toggles]
   [batch-group :open]
   [batch-group :closed]])
