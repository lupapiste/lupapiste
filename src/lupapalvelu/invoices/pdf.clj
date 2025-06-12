(ns lupapalvelu.invoices.pdf
  "Generates PDF from a (draft or published) invoice."
  (:require [hiccup.core :as h]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.invoices.util :as invoices-util]
            [lupapalvelu.pate.columns :as cols]
            [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.pdf-html :as html]
            [lupapalvelu.pate.pdf-layouts :as layouts]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [lupapalvelu.price-catalogues :as catalogues]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [java.time Duration]))

(set! *warn-on-reflection* true)

(defn pos-or-nil [n]
  (when (and (number? n) (pos? n))
    n))

;; ----------------------------
;; Layouts
;; ----------------------------

(def entry--construction-site
  (list [{:loc    :rakennuspaikka._group_label
          :styles [:pad-before]
          :source :construction-site}]))

(def entry--worktime
  '([{:loc    :invoices.ya.real-work-time
      :source :worktime
      :styles :pad-before}
     {:path :days}
     {:path :billable}]))

(def entry--payer
  '([{:loc    :invoice.pdf.payer
      :styles [:pad-before :bold :border-top]
      :source :payer-name}]
    [{:loc    :y-tunnus
      :source :payer-y}]
    [{:loc    :osoite-maksaja
      :source :payer-address}]
    [{:loc    :yhteystiedot._group_label
      :source :payer-contact}]
    [{:loc    :osapuoli.yritys.verkkolaskutustieto._group_label
      :source :payer-netbilling}]
    [{:loc    :osapuoli.laskuviite
      :source :payer-reference}]))


(defn invoice-table [operation-name
                     {:keys [lang discounts? vat? operation-total-price
                             operation-vat operation-taxfree]}
                     rows]
  (let [loc (partial i18n/localize lang)]
    [:table
     [:caption.left.bold (loc (util/kw-path :operations operation-name))]
     [:thead
      [:tr
       [:th]
       [:th.left {:col-span 2} (loc :invoices.rows.amount)]
       [:th.right.nowrap.pad-left (loc :invoices.rows.unit-price)]
       (when discounts?
         [:th.right.nowrap.pad-left (loc :invoices.rows.discount-percent)])
       (when vat?
         (list [:th.center.nowrap (loc :invoices.rows.product-constants.names.alv)]
               [:th.right.nowrap (loc :invoices.wo-taxes)]))
       [:th.right.nowrap.pad-left (loc :invoices.rows.total)]]]
     [:tbody
      (for [{:keys [discount-index discount-percent vat]
             :as   row} rows]
        [:tr
         [:td.indent (:text row)]
         [:td.right (:units row)]
         [:td.pad-left-small (loc :unit (:unit row))]
         [:td.right.pad-left (:price-per-unit row)]
         (when discounts?
           [:td.right (if (and discount-percent (pos? discount-percent))
                        (cond-> [:span discount-percent]
                          discount-index (conj [:sup (str " " discount-index)]))
                        "")])
         (when vat?
           (if vat
             (list [:td.right.nowrap.pad-left vat]
                   [:td.nowrap.right (:total-taxfree row)])
             (list [:td ""] [:td.nowrap.right (:total-price row)])))
         [:td.right.pad-left (:total-price row)]])]
     [:tfoot
      [:tr
       [:td ""]
       [:td.border-top {:col-span (cond-> 3
                                    discounts? inc)}]
       (when vat?
         (list [:td.border-top.bold.right operation-vat]
               [:td.border-top.bold.right operation-taxfree]))
       [:td.border-top.bold.right operation-total-price]]]]))

(defn entry--invoice-discount-info [source-id info]
  (list [{:loc     (if (or (string? info) (= (count info) 1)) :invoices.rows.comment :invoice.pdf.discounts)
          :source  source-id
          :styles  [:pad-after :indent]
          :post-fn (fn [source]
                     (if (string? source)
                       source
                       (list (vec (cons :ol (map #(vector :li {:value (:discount-index %)} (:comment %))
                                                 source))))))}]))

(defn total-sums-layout [{:keys [invoice-total vat-total taxfree-total lang]}]
  (let [tr (fn [loc-key value]
             (when value
               [:tr
                [:td.bold.pad-right  (i18n/localize lang loc-key)]
                [:td.bold.right.pad-left value]]))]
    [:table
     [:tbody
      (tr :invoices.taxfree-total taxfree-total)
      (tr :invoices.rows.product-constants.names.alv vat-total)
      (tr :invoice.end-sum invoice-total)]]))

(def divider [{:raw [:div.divider]}])

(defn pdf-layout [invoice-layouts]
  (assoc (apply layouts/build-layout
                (concat [layouts/entry--operation
                         entry--construction-site
                         entry--worktime
                         entry--payer]
                        divider
                        invoice-layouts))
         :left-width 40))


;; ----------------------------
;; Properties
;; ----------------------------

(defn- void-anywhere?
  "True if any of the items or any item in their subgroups is nil, blank or empty."
  [items]
  (boolean (some (fn [item]
                   (cond
                     (nil? item)        true
                     (string? item)     (ss/blank? item)
                     (sequential? item) (or (empty? item)
                                            (void-anywhere? item))
                     :else              false))
                 items)))

(defn smart-join
  "Joins array into a string as follows:
   - string items are trimmed
   - blank items are ignored
   - keyword items are treated as separators (:comma, :space, :colon, :newline)
   - separator following nil or another separator is ignored
   - if the separator is the last or the first item in the result, it is ignored.
   - groups [items] are ignored if _any_ item (including subgroups) is nil."
  ([items]
   (smart-join items false))
  ([items group?]
   (loop [[x & xs] (remove nil? items)
          previous nil
          result   []]
     (cond
       (nil? x)
       (->> (->> result
                 reverse
                 (drop-while keyword?)
                 reverse)
            (map (fn [a]
                   (case a
                     :comma   ", "
                     :space   " "
                     :colon   ": "
                     :newline "\n"
                     a)))
            (apply str))

       (string? x)
       (if-let [x (not-empty (ss/trim x))]
         (recur xs x (conj result x))
         (recur xs previous result))

       (keyword? x)
       (if (or (string? previous) group?)
         (recur xs x (conj result x))
         (recur xs previous result))

       (sequential? x)
       (if (void-anywhere? x)
         (recur xs previous result)
         (recur (concat (flatten x) xs) previous result))

       :else (recur xs (str x) (conj result (str x)))))))

(defn workdays
  "Total, billable and free days in the working period. Nil if the times are missing or
  inconsistent."
  [invoice catalogue]
  (let [{:keys [work-start-ms work-end-ms]} invoice
        start-date                          (date/start-of-day work-start-ms)
        end-date                            (date/start-of-day work-end-ms)
        {:keys [no-billing-periods]}        catalogue]
    (when (and start-date end-date (not (date/after? start-date end-date)))
      (let [days      (-> (Duration/between start-date end-date)
                          (.toDays)
                          inc)
            free-days (count (catalogues/no-billing-period-days-in-billable-work-time
                               (date/finnish-date start-date)
                               (date/finnish-date end-date)
                               no-billing-periods))]
        {:days          days
         :free-days     free-days
         :billable-days (- days free-days)}))))

(defn- worktime
  "Working period and the number of billable days as textual representations. The
  non-billable periods in the catalogue are taken into account when calculating the
  latter. Nil if the times are missing or inconsistent."
  [{:keys [lang invoice catalogue]}]
  (when-let [{:keys [days billable-days]} (workdays invoice catalogue)]
    (let [{:keys [work-start-ms
                  work-end-ms]} invoice
          start-date            (date/finnish-date work-start-ms)
          end-date              (date/finnish-date work-end-ms)
          billable-str          (cond
                                  (= billable-days 1)
                                  (i18n/localize lang :invoice.pdf.billable-day)

                                  (pos? billable-days)
                                  (i18n/localize-and-fill lang :invoice.pdf.billable-days
                                                          billable-days)
                                  :else
                                  (i18n/localize lang :invoice.pdf.no-billable-days))
          days-str              (if (= days 1)
                                  start-date
                                  (format "%s \u2013 %s" start-date end-date))]
      {:days     days-str
       :billable billable-str})))

(defn payer [lang {:keys [entity-name entity-address
                          postal-code city country
                          billing-reference
                          company-id phone email
                          netbill ovt operator]
                   :as   invoice}]
  (let [ company?  (invoices-util/company-payer? invoice)
        netbilling (fn [field value]
                     [(i18n/localize lang :osapuoli.yritys.verkkolaskutustieto field)
                      :colon
                      (when-not (ss/blank? value)
                        (cond->>  value
                          (= field :valittajaTunnus)
                          (i18n/localize lang :osapuoli.yritys.verkkolaskutustieto field)))])]
    (util/assoc-when-pred {}
                          util/fullish?
                          ;; [company,] firstname lastname
                          :payer-name (ss/trim-join ", "
                                                    (when company? entity-name)
                                                    (invoices-util/person-name invoice))
                          ;; Y-tunnus
                          :payer-y (when company? company-id)
                          ;; katu
                          ;; postinumero postitimipaikannimi
                          ;; [maa if not Finland]
                          :payer-address (smart-join [entity-address :newline
                                                      postal-code [:space city] :newline
                                                      (when-not (or (ss/blank? country) (= country "FIN"))
                                                        [(i18n/localize lang :country country)])])
                          ;; puhelin, email
                          :payer-contact (smart-join [phone :comma email])
                          ;; [Netbilling address: address] \n [OVT: ovt] \n [Relay: relay]
                          :payer-netbilling (when company?
                                              (smart-join [(netbilling :verkkolaskuTunnus netbill) :newline
                                                           (netbilling :ovtTunnus ovt) :newline
                                                           (netbilling :valittajaTunnus operator)]))
                          :payer-reference billing-reference)))

(defn- operation-discounts
  "Processes `invoice-rows` for discount info. Notable use cases:
   - No discounts
   - Discounts without comments
   - Some discounts have comments, some not."
  [invoice-rows]
  (let [{:keys [dis-count last-index
                xs]} (reduce (fn [{:keys [last-index] :as acc}
                                  {:keys [discount-percent comment] :as m}]
                               (let [discount? (pos-or-nil discount-percent)
                                     comment?  (and discount? (ss/not-blank? comment))
                                     index     (when comment? (inc last-index))]
                                 (cond-> acc
                                   discount? (update :dis-count inc)
                                   index     (assoc :last-index index)
                                   true      (update :xs conj (cond-> m
                                                                index (assoc :discount-index index))))))
                             {:dis-count  0  ; Total number of discounts
                              :last-index 0  ; Number of discounts with comments
                              :xs         [] ; Invoice rows
                              }
                             invoice-rows)
        info         (->> (filter :discount-index xs)
                          (map #(select-keys % [:discount-index :comment]))
                          seq)]
    (util/assoc-when {:discounts? (pos? dis-count)
                      :rows       (cond->> xs
                                    (= dis-count 1) (map #(dissoc % :discount-index)))}
                     :discount-info (if (= dis-count last-index 1)
                                      (-> info first :comment)
                                      info))))

(defn- key-cents->eur
  ([cents-key coll symbol?]
   (invoices-util/euro-format (apply + (keep cents-key coll))
                              {:cents? true :symbol? symbol?}))
  ([cents-key coll]
   (key-cents->eur cents-key coll false)))

(defn invoice-operation
  [lang {:keys [rows]} {op-id :id :as operation}]
  (when-let [rows (some->> (filter #(= (:operation-id %) op-id) rows)
                           (remove #(or (zero? (:price-per-unit %)) (zero? (:units %))))
                           (map (fn [{:keys [total-cents price-per-unit text code comment vat-info]
                                      :as   m}]
                                  (let [text                    (smart-join [code :space text])
                                        {:keys [vat-cents vat-percentage
                                                taxfree-cents]} vat-info]
                                    (util/assoc-when-pred
                                      (select-keys m [:discount-percent :unit :total-cents
                                                      :units])
                                      util/fullish?
                                      :text text
                                      :comment (ss/trim comment)
                                      ;; 24% (12.00€)
                                      :vat-cents vat-cents
                                      :taxfree-cents (or taxfree-cents total-cents)
                                      :vat (when vat-info
                                             (i18n/localize-and-fill lang
                                                                     :invoice.vat-info
                                                                     vat-percentage
                                                                     (invoices-util/euro-format vat-cents
                                                                                                {:cents? true})))
                                      :price-per-unit (invoices-util/euro-format price-per-unit
                                                                                 {:symbol? false})
                                      :total-taxfree (invoices-util/euro-format taxfree-cents
                                                                                {:cents? true :symbol? false})
                                      :total-price (invoices-util/euro-format total-cents
                                                                              {:cents? true :symbol? false}))))))]
    (util/assoc-when-pred (operation-discounts rows)
                          util/fullish?
                          :operation-name (:name operation)
                          :vat? (some :vat rows)
                          :operation-id op-id
                          :operation-total-price (key-cents->eur :total-cents rows)
                          :operation-vat (key-cents->eur :vat-cents rows true)
                          :operation-taxfree (key-cents->eur :taxfree-cents rows))))

(defn invoice-properties
  "Options are have been `tools/unwrapped` and organization is not a delay."
  [{:keys [lang application invoice] :as options}]
  (util/filter-map-by-val util/fullish?
                          (merge options
                                 (payer lang invoice)
                                 {:lang               lang
                                  :application-id     (:id application)
                                  :construction-site  (ss/join "\n" [(pdf/property-id application)
                                                                     (:address application)])
                                  :worktime           (worktime options)
                                  :organization-name  (html/organization-name lang options)
                                  :operations         (assoc-in (pdf/operations options) [0 ::styles :text] :bold)
                                  :title              (i18n/localize lang (if (:organization-internal-invoice? invoice)
                                                                            :invoice.pdf.title.internal
                                                                            :invoice.pdf.title))
                                  :invoice-operations (keep (partial invoice-operation lang invoice)
                                                            (:operations invoice))
                                  :invoice-total      (some-> invoice :sum-total-cents
                                                              (invoices-util/euro-format {:cents? true}))
                                  :vat-total          (some-> invoice :sum-vat-info :vat-cents
                                                              (invoices-util/euro-format {:cents? true}))
                                  :taxfree-total      (some-> invoice :sum-vat-info :taxfree-cents
                                                              (invoices-util/euro-format {:cents? true}))})))

;; ----------------------------
;; PDF
;; ----------------------------

(defn invoice-header
  [{:keys [lang organization-name title application-id created invoice]}]
  [:div.header
   [:div.section.header
    [:div.row
     [:div.cell.cell--40 organization-name]
     [:div.cell.cell--20.center
      (when (= (:state invoice) "draft")
        [:span.preview (i18n/localize lang :price-catalogue.draft)])]
     [:div.cell.cell--40.right
      [:div.permit (ss/join-non-blanks " " [title (:backend-id invoice)])]]]
    [:div.row
     [:div.cell.cell--40 application-id]
     [:div.cell.cell--20.center [:div (date/finnish-date created)]]
     [:div.cell.cell--40.right.page-number.nowrap
      (i18n/localize lang :pdf.page)
      " " [:span.pageNumber ""]]]]])

(defn invoice-body
  "In addition to regular layout stuff, dynamically creates operation invoice sections."
  [{:keys [invoice-operations ] :as properties}]
  (let [{:keys [properties
                layouts]} (reduce (fn [{:keys [properties layouts]}
                                       {:keys [operation-name operation-total-price discount-info
                                               rows]
                                        :as   op-data}]
                                    (let [table    (invoice-table operation-name
                                                                  (merge properties
                                                                         (select-keys op-data
                                                                                      [:vat? :discounts?
                                                                                       :operation-total-price
                                                                                       :operation-vat
                                                                                       :operation-taxfree]))
                                                                  rows)
                                          total-id (keyword (gensym "total"))
                                          info-id  (keyword (gensym "info"))]
                                      {:properties (apply merge (concat [properties
                                                                         {total-id operation-total-price}
                                                                         {info-id discount-info}]))
                                       :layouts    (concat layouts
                                                           [{:raw table}]
                                                           (entry--invoice-discount-info info-id
                                                                                         discount-info)
                                                           )}))
                                  {:properties properties}
                                  invoice-operations)]
    (cols/content properties (pdf-layout
                               (concat layouts
                                       divider
                                       [{:raw (total-sums-layout properties)}]) ))))

(def invoice-footer
  [:div.footer])

(defn invoice-html [properties]
  {:header (html/html (invoice-header properties))
   :body   (html/html (invoice-body properties))
   :footer (h/html invoice-footer)})

(defn create-invoice-pdf
  "Returns :pdf-file-stream, :filename map or :error map."
  [{:keys [application created organization data system]} {:keys [price-catalogue-id] :as invoice}]
  (let [lang       (:lang data :fi)
        properties (invoice-properties {:lang         lang
                                        :created      created
                                        :organization (force organization)
                                        :application  (tools/unwrapped application)
                                        :invoice      invoice
                                        :catalogue    (catalogues/fetch-price-catalogue-by-id price-catalogue-id)})
        pdf        (html-pdf/html->pdf system (invoice-html properties))]
    (if (:ok pdf)
      (assoc pdf :filename (i18n/localize-and-fill lang
                                                   :invoice.pdf.filename
                                                   (:id application)
                                                   (date/finnish-date created)))
      {:error :invoice.pdf.error})))
