(ns lupapalvelu.invoices.integration.excel-converter
  "Converts transfer batches to SAP Excel format."
  (:require [dk.ative.docjure.spreadsheet :as docjure]
            [lupapalvelu.invoices.util :as invoices-util]
            [lupapalvelu.organization :as org]
            [lupapalvelu.reports.excel :as excel]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [org.apache.poi.ss.usermodel Row Sheet]
           [org.apache.poi.xssf.usermodel XSSFWorkbook]))

(set! *warn-on-reflection* true)

(def default-organization-constants
  {:tilauslaji   "Z001"
   :maksuehto    "Z034"
   :myyntiorg    1000
   :jakelutie    10
   :sektori      47
   :laskuttaja   10000480
   :nimike       32876600
   :tulosyksikko 147552010})

(defn template-string
  "Strings that are part of the template.
  Finland-specific format; no need for localizations at this time."
  [string-key row]
  (case string-key
    :otsikkoteksti
    (str "Tämä maksu voidaan ulosmitata ilman tuomiota tai päätöstä MRL 145 § 4 mom. \"alv 0% viranomaistoiminta\""
         (some->> (or (some-> row :billing-reference ss/blank-as-nil)
                      (invoices-util/person-name row))
                  (str "\nViite: ")))

    :otsikkomuistio
    (format "Liittyy lupaan %s, %s" (:application-id row) (:application-address row))))

(defn- price-per-unit
  [{:keys [units total-cents]}]
  (if (or (nil? units) (zero? units))
    0
    (invoices-util/cents->num (/ total-cents units))))

(defn- fmt
  ([txt fmt-key]
   (format "%s\n(%s format)" txt (name fmt-key)))
  ([txt]
   (fmt txt :general)))

(def column-definitions
  "A definition consists of of the column header, row data accessor and an optional flag,
  whether the column data is shown only in the first invoice row. Accessor can be
  fn/keyword (receives row data as argument), nil or string. The order of the definitions
  dictate the order of the columns."
  [[(fmt "T") #(if (:first-row? %) 1 0)]
   [(fmt "Tilauslaji") :tilauslaji true]
   [(fmt "Myyntiorg") :myyntiorg true]
   [(fmt "Jakelutie") :jakelutie true]
   [(fmt "Sektori") :sektori true]
   [(fmt "Viitelasku") nil  true]
   [(fmt "Asiakas") :sap-number true]
   [(fmt "Tunniste")
    #(if (invoices-util/company-payer? %)
       (:company-id %)
       (:person-id %))
    true]
   [(fmt "Nimi") invoices-util/payer-name true]
   [(fmt "Osoite") :entity-address true]
   [(fmt "PostiNo") :postal-code true]
   [(fmt "Toimipaikka") :city true]
   [(fmt "Pvm" :text) nil true]
   [(fmt "Maksuehto") :maksuehto true]
   [(fmt "Laskuttaja") :laskuttaja true]
   [(fmt "Asiaviite" :text) :case-reference true]
   [(fmt "Otsikkoteksti") #(template-string :otsikkoteksti %) true]
   [(fmt "Otsikkomuistio") #(template-string :otsikkomuistio %) true]
   [(fmt "Nimike") :nimike]
   [(fmt "Määrä") :units]
   [(fmt "Kpl") ""]
   [(fmt "Yksikköhinta") price-per-unit]
   [(fmt "Brutto/Netto") "B"]
   [(fmt "Tulosyksikkö") :tulosyksikko]
   [(fmt "Sis. tilaus") nil true]
   ["" nil] ;; Empty Z column is required by SAP
   [(fmt "Littera") :letter true]
   [(fmt "Tilastoraportti") nil true]
   [(fmt "Ympäristökohde") nil true]
   [(fmt "Toiminto") nil true]
   [(fmt "Varakoodi 2") nil true]
   [(fmt "Riviteksti") (util/fn->> :rowtext (ss/trim-to 40))]
   [(fmt "Rivimuistio") (util/fn->> :rowtext (drop 40) ss/join ss/trim ss/blank-as-nil)]])

(defn- make-workbook
  "Creates Docjure excel workbook form the given sheets. Each sheet is
   map with keys :name :header :rows."
  ^XSSFWorkbook [sheets]
  (let [wb           (apply docjure/create-workbook (mapcat (fn [{:keys [name header rows]}] [name (cons header rows)]) sheets))
        header-style (docjure/create-cell-style! wb {:font          {:bold true} :wrap true
                                                     :background    :pale_blue
                                                     :border-bottom :thin
                                                     :valign        :top})
        wrap-style   (docjure/create-cell-style! wb {:wrap true :valign :top})]
    (doseq [{:keys [name]} sheets
            :let           [^Sheet sheet    (docjure/select-sheet name wb)
                            [^Row header-row & rows] (docjure/row-seq sheet)]]
      (docjure/set-row-style! header-row header-style)
      (mapv #(docjure/set-row-style! % wrap-style) rows)
      (mapv #(.autoSizeColumn sheet %) (range (.getLastCellNum header-row))))
    wb))

(defn- invoice-excel-response
  "HTTP response for excel download. Each sheet is map with the keys :name :header :rows.
  Currently featuring save-workbook-into-file! for testing purposes"
  [lang batch sheets]
  (excel/excel-response
    (invoices-util/transfer-batch-filename batch :excel lang)
    (excel/xlsx-stream (make-workbook sheets))
    "Exception while generating invoice excel file: "))

(defn- get-enriched-invoice-rows
  "Returns the invoice's invoice rows enriched with the fields from the invoice itself as well as organization
  specific constants. If the same key appears more than once, the conflict is settled in order of importance:
  organization < invoice < invoice-row"
  [constants {:keys [enriched-data] :as invoice}]
  (let [ invoice-data (merge constants
                             (-> invoice
                                 (dissoc :operations :enriched-data :rows)
                                 (assoc :application-address (-> enriched-data :application :address))))]
    (->> invoice
         :rows
         (map-indexed (fn [i {:keys [code text] :as row}]
                        (cond-> (merge invoice-data
                                       row
                                       {:rowtext (ss/trim-join " " code text)})
                          ;; Some info is only shown on the first row of an invoice
                          (zero? i) (assoc :first-row? true)))))))

(defn- fetch-transfer-batch-excel-data
  "Returns the invoice rows for the given transfer-batch as a seq of maps"
  [{:keys [organization-id] :as batch}]
  (let [constants (->> (org/get-invoicing-config organization-id)
                       :constants
                       (merge default-organization-constants))]
    (mapcat (partial get-enriched-invoice-rows constants)
            (invoices-util/transfer-batch-invoices batch))))

(defn- get-invoice-excel-row-values
  "Returns a string rows for the given `data-row`."
  [{:keys [first-row?] :as data-row}]
  (for [[_ accessor first-only?] column-definitions]
    (cond
      (and first-only?
           (not first-row?)) nil
      (nil? accessor)        nil
      (string? accessor)     accessor
      :else                  (accessor data-row))))

(defn transfer-batch-excel
  "Returns a generated RAW excel-file response of the invoices in the given transfer-batch"
  [lang batch]
  (let [data   (fetch-transfer-batch-excel-data batch)
        header (map first column-definitions)
        rows   (map get-invoice-excel-row-values data)]
    (invoice-excel-response lang
                            batch
                            [{:name   "Siirtoerä"
                              :header header
                              :rows   rows}])))
