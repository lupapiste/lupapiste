(ns lupapalvelu.invoices.pdf
  "Generates PDF from a (draft or published) invoice."
  (:require [hiccup.core :as h]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
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

(defn price->string
  "String representation of price in euros.
  12345 -> '123,45'
  12300 -> '123,00'"
  [price-in-cents]
  {:pre [(integer? price-in-cents)]}
  (ss/replace (format "%.2f" (/ price-in-cents 100.0)) "." ","))

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
  '([{:loc :invoices.ya.real-work-time
      :source :worktime
      :styles :pad-before}
     {:path :days}
     {:path :billable}]))

(def entry--payer
  '([{:loc :invoice.pdf.payer
      :styles [:pad-before :bold :border-top]
      :source :payer-name}]
    [{:loc :y-tunnus
      :source :payer-y}]
    [{:loc :osoite-maksaja
      :source :payer-address}]
    [{:loc :yhteystiedot._group_label
      :source :payer-contact}]
    [{:loc :osapuoli.yritys.verkkolaskutustieto._group_label
      :source :payer-netbilling}]
    [{:loc :osapuoli.laskuviite
      :source :payer-reference}]))


(defn invoice-table [operation-name {:keys [lang discounts? vat?]} rows]
  (let [loc       (partial i18n/localize lang)
        row-total (fn [k]
                    (->> rows
                         (keep k)
                         (apply +)))]
    [:table
     [:thead
      [:tr
       [:th.full-width.left (loc (util/kw-path :operations operation-name))]
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
                   [:td.nowrap.right (price->string (:total-taxfree row))])
             (list [:td ""] [:td.nowrap.right (price->string (:total-price row))])))
         [:td.right.pad-left (price->string (:total-price row))]])]
     [:tfoot
      [:tr
       [:td ""]
       [:td.border-top {:col-span (cond-> 3
                                    discounts? inc)}]
       (when vat?
         (let [vat-total (row-total :vat-amount-minor)]
           (list [:td.border-top.bold.right (str (price->string vat-total) "€")]
                 [:td.border-top.bold.right (price->string (- (row-total :total-price)
                                                              vat-total))])))
       [:td.border-top.bold.right (price->string (row-total :total-price))]]]]))

(defn entry--invoice-discount-info [source-id info]
  (list [{:loc (if (or (string? info) (= (count info) 1)) :invoices.rows.comment :invoice.pdf.discounts)
          :source source-id
          :styles [:pad-after :indent]
          :post-fn (fn [source]
                     (if (string? source)
                       source
                       (list (vec (cons :ol (map #(vector :li {:value (:discount-index %)} (:comment %))
                                                 source))))))}]))


(def entry--invoice-total
  '([{:loc    :invoices.rows.product-constants.names.alv
      :source :vat-total}
     {:unit    :eur}]
    [{:loc    :invoices.taxfree-total
      :source :taxfree-total}
     {:unit    :eur}]
    [{:loc    :billing.excel.sum
      :source :invoice-total
      :styles [:bold]}
     {:styles [:bold]
      :unit    :eur}]))

(def divider [{:raw [:div.divider]}])

(defn pdf-layout [invoice-layouts]
  (assoc (apply layouts/build-layout
                (concat [layouts/entry--operation
                         entry--construction-site
                         entry--worktime
                         entry--payer]
                        divider
                        invoice-layouts
                        divider
                        [entry--invoice-total]))
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

(defn payer [lang application]
  (let [{:keys [_selected henkilo yritys]
         :as   m}                  (-> application
                                       :documents
                                       (domain/get-documents-by-subtype "maksaja")
                                       first
                                       :data
                                       tools/unwrapped)
        company?                   (= _selected "yritys")
        yhteyshenkilo              (if company? (:yhteyshenkilo yritys) henkilo)
        {:keys [etunimi sukunimi]} (:henkilotiedot yhteyshenkilo)
        {:keys [email puhelin]}    (:yhteystiedot yhteyshenkilo)
        {:keys [katu postinumero
                maa]}              (:osoite (if company? yritys henkilo))
        postitoimipaikannimi       (-> (if company? yritys henkilo) :osoite :postitoimipaikannimi)
        netbilling                 (fn [field]
                                     [(i18n/localize lang :osapuoli.yritys.verkkolaskutustieto field)
                                      :colon
                                      (when-let [value (ss/blank-as-nil (get-in yritys [:verkkolaskutustieto field]))]
                                        (cond->>  value
                                          (= field :valittajaTunnus) (i18n/localize lang :osapuoli.yritys.verkkolaskutustieto field)))])]
    (util/assoc-when-pred {}
                          util/fullish?
                          ;; [company,] firstname lastname
                          :payer-name (smart-join [(when company?
                                                     [(:yritysnimi yritys)])
                                                   :comma
                                                   etunimi :space sukunimi])
                          ;; Y-tunnus
                          :payer-y (when company?
                                     (ss/trim (:liikeJaYhteisoTunnus yritys)))
                          ;; katu
                          ;; postinumero postitimipaikannimi
                          ;; [maa if not Finland]
                          :payer-address (smart-join [katu :newline
                                                      postinumero [:space postitoimipaikannimi] :newline
                                                      (when-not (or (ss/blank? maa) (= maa "FIN"))
                                                        [(i18n/localize lang :country maa)])])
                          ;; puhelin, email
                          :payer-contact (smart-join [puhelin :comma email])
                          ;; [Nebilling address: address] \n [OVT: ovt] \n [Relay: relay]
                          :payer-netbilling (when company?
                                              (smart-join [(netbilling :verkkolaskuTunnus) :newline
                                                           (netbilling :ovtTunnus) :newline
                                                           (netbilling :valittajaTunnus)]))
                          :payer-reference (ss/trim (:laskuviite m)))))


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

(defn invoice-operation
  [lang {:keys [invoice-rows] :as operation}]
  (let [invoice-rows (remove #(or (zero? (:price-per-unit %)) (zero? (:units %)))
                             invoice-rows)]
    (when-let [rows (some->> (seq invoice-rows)
                             (map (fn [{:keys [sums text code comment
                                               vat-percentage vat-amount-minor]
                                        :as   m}]
                                    (let [text (smart-join [code :space text])]
                                      (util/assoc-when-pred
                                        (select-keys m [:discount-percent :unit
                                                        :units :vat-amount-minor])
                                        util/fullish?
                                        :text text
                                        :comment (ss/trim comment)
                                        ;; 24% (12.00€)
                                        :vat (when (and vat-percentage vat-amount-minor)
                                               (i18n/localize-and-fill lang :invoice.vat-info
                                                                       vat-percentage
                                                                       (price->string vat-amount-minor)))
                                        ;; In euros, could be double
                                        :price-per-unit (-> m :price-per-unit (* 100) double Math/round price->string)
                                        :total-taxfree (when vat-amount-minor
                                                         (-> sums
                                                             :with-discount
                                                             :minor
                                                             (- vat-amount-minor)))
                                        ;; In cents, integer
                                        :total-price (-> sums :with-discount :minor))))))]
      (util/assoc-when-pred (operation-discounts rows)
                            util/fullish?
                            :operation-name (:name operation)
                            :vat? (when (some :vat rows) true)
                            :operation-id (:operation-id operation)
                            :operation-total-price (->> invoice-rows
                                                        (map (util/fn-> :sums :with-discount :minor))
                                                        (apply +)
                                                        price->string)))))

(defn invoice-properties
  "Options are have been `tools/unwrapped` and organization is not a delay."
  [{:keys [lang application invoice] :as options}]
  (util/filter-map-by-val util/fullish?
                          (merge options
                                 (payer lang application)
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
                                  :invoice-operations (keep (partial invoice-operation lang)
                                                            (:operations invoice))}

                                 (let [total     (some-> invoice :sum :minor)
                                       vat-total (some-> invoice :vat-total-minor pos-or-nil)]
                                   {:invoice-total (some-> total price->string)
                                    :vat-total     (some-> vat-total price->string)
                                    :taxfree-total (when (and total vat-total)
                                                     (price->string (- total vat-total)))}))))

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
  [{:keys [invoice-operations] :as properties}]
  (let [{:keys [properties
                layouts]} (reduce (fn [{:keys [properties layouts]}
                                       {:keys [discounts? rows operation-name
                                               discount-info vat? operation-total-price]}]
                                    (let [table    (invoice-table operation-name
                                                                  (assoc properties
                                                                         :discounts? discounts?
                                                                         :vat?       vat?)
                                                                  rows)
                                          total-id (keyword (gensym "total"))
                                          info-id  (keyword (gensym "info"))]
                                      {:properties (apply merge (concat [properties
                                                                         {total-id operation-total-price}
                                                                         {info-id discount-info}]))
                                       :layouts    (concat layouts
                                                           [{:raw table}]
                                                           (entry--invoice-discount-info info-id
                                                                                         discount-info))}))
                                  {:properties properties}
                                  invoice-operations)]
    (cols/content properties (pdf-layout layouts))))

(def invoice-footer
  [:div.footer])

(defn invoice-html [properties]
  {:header (html/html (invoice-header properties))
   :body   (html/html (invoice-body properties))
   :footer (h/html invoice-footer)})

(defn create-invoice-pdf
  "Returns :pdf-file-stream, :filename map or :error map."
  [{:keys [lang application created organization]} {:keys [price-catalogue-id] :as invoice}]
  (let [properties (invoice-properties {:lang         lang
                                        :created      created
                                        :organization (force organization)
                                        :application  (tools/unwrapped application)
                                        :invoice      invoice
                                        :catalogue    (catalogues/fetch-price-catalogue-by-id price-catalogue-id)})
        pdf        (html-pdf/html->pdf (invoice-html properties))]
    (if (:ok pdf)
      (assoc pdf :filename (i18n/localize-and-fill lang
                                                   :invoice.pdf.filename
                                                   (:id application)
                                                   (date/finnish-date created)))
      {:error :invoice.pdf.error})))
