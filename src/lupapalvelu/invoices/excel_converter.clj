(ns lupapalvelu.invoices.excel-converter
  "Converts transferbatches to Excel format. There is a rumor that SAP uses these files to send bills."
  (:require
    [dk.ative.docjure.spreadsheet :as docjure]
    [lupapalvelu.mongo :as mongo]
    [lupapalvelu.reports.excel :as excel]
    [lupapalvelu.organization :as org]
    [sade.strings :as ss])
  (:import (org.apache.poi.ss.usermodel Sheet)))

(def excel-column-headers
  "Temporary definitions. Logic between Finnish and English keys: if it is stored in Mongo, use the same English key.
  Otherwise use the Finnish one from the example excel."
  {:t               "T\n(general format)"
   :tilauslaji      "Tilauslaji\n(general format)"
   :myyntiorg       "Myyntiorg\n(general format)"
   :jakelutie       "Jakelutie\n(general format)"
   :sektori         "Sektori\n(general format)"
   :viitelasku      "Viitelasku\n(general format)"
   :asiakas         "Asiakas\n(general format)"
   :tunniste        "Tunniste\n(general format)"
   :nimi            "Nimi\n(general format)"
   :osoite          "Osoite\n(general format)"
   :postino         "PostiNo\n(text format)"
   :toimipaikka     "Toimipaikka\n(general format)"
   :pvm             "Pvm\n(text format)"
   :maksuehto       "Maksuehto\n(general format)"
   :laskuttaja      "Laskuttaja\n(general format)"
   :asiaviite       "Asiaviite\n(text format)"
   :otsikkoteksti   "Otsikkoteksti\n(general format)"
   :otsikkomuistio  "Otsikkomuistio\n(general format)"
   :nimike          "Nimike\n(general format)"
   :qty             "Määrä\n(general format)"
   :kpl             "Kpl\n(general format)"
   :price-per-unit  "Yksikköhinta\n(general format)"
   :bruttonetto     "Brutto/Netto\n(general format)"
   :tulosyksikko    "Tulosyksikkö\n(general format)"
   :sistilaus       "Sis. tilaus\n(general format)"
   :z               "" ;; SAP requires a Z-column that must be empty. This is it.
   :littera         "Littera\n(general format)"
   :tilastoraportti "Tilastoraportti\n(general format)"
   :ymparistokohde  "Ympäristökohde\n(general format)"
   :toiminto        "Toiminto\n(general format)"
   :varakoodi2      "Varakoodi 2\n(general format)"
   :riviteksti      "Riviteksti\n(general format)"
   :rivimuistio     "Rivimuistio\n(general format)"})

(def first-invoice-row-columns
  "The columns whose values should only be displayed on the first invoice row.
  Shared between all the invoice rows of a single invoice."
  #{:tilauslaji :myyntiorg :jakelutie :sektori :viitelasku :asiakas :tunniste :nimi
    :osoite :postino :toimipaikka :pvm :maksuehto :laskuttaja :asiaviite
    :otsikkoteksti :otsikkomuistio
    :sistilaus :littera :tilastoraportti :ymparistokohde :toiminto :varakoodi2})

(def excel-string-templates
  "Strings that are part of the template.
  Finland-specific format; no need for localizations at this time"
  {:assoc-w-permit  "Liittyy lupaan"
   :distraint-note  "Tämä maksu voidaan ulosmitata ilman tuomiota tai päätöstä MRL 145 § 4 mom."
   :ref             "Viite:"
   :vat             "\"alv 0% viranomaistoiminta\""
   :company-contact "Yrityksen yhteyshenkilö:"})

(def default-organization-constants
  {:tilauslaji       "Z001"
   :maksuehto         "Z034"
   :myyntiorg        1000
   :jakelutie        10
   :sektori          47
   :laskuttaja       10000480
   :nimike           32876600
   :tulosyksikko     147552010})

(defn- make-workbook
  "Creates Docjure excel workbook form the given sheets. Each sheet is
   map with keys :name :header :rows."
  [sheets]
  (let [wb (apply docjure/create-workbook (mapcat (fn [{:keys [name header rows]}] [name (cons header rows)]) sheets))
        header-style (docjure/create-cell-style! wb {:font {:bold true} :wrap true
                                                     :background :pale_blue
                                                     :border-bottom :thin
                                                     :valign :top})
        wrap-style (docjure/create-cell-style! wb {:wrap true :valign :top})]
    (doseq [{:keys [name]} sheets
             :let [^Sheet sheet    (docjure/select-sheet name wb)
                   [header-row & rows] (docjure/row-seq sheet)]]
      (docjure/set-row-style! header-row header-style)
      (mapv #(docjure/set-row-style! % wrap-style) rows)
      (mapv #(.autoSizeColumn sheet %) (range (.getLastCellNum header-row))))
    wb))

(defn- invoice-excel-response
  "HTTP response for excel download. Filename without extension. Each sheet is map with the keys :name :header :rows.
  Currently featuring save-workbook-into-file! for testing purposes"
  [filename sheets]
  (excel/excel-response
    (format "%s.xlsx" (or filename "excel"))
    (excel/xlsx-stream (make-workbook sheets))
    "Exception while generating invoice excel file: "))

(defn- get-organization-invoice-config
  "Gets the excel constants in use for this organization.
  Uses the default values (for the first user Vantaa) for missing keys."
  [organization-id]
  (let [constants (:constants (org/get-invoicing-config organization-id))]
    (conj default-organization-constants (if (map? constants) constants {}))))

(defn- get-enriched-invoice-rows
  "Returns the invoice's invoice rows enriched with the fields from the invoice itself as well as organization
  specific constants. If the same key appears more than once, the conflict is settled in order of importance:
  organization < invoice < invoice-row"
  [invoice]
  (let [application             (mongo/by-id :applications (:application-id invoice) [:address])
        organization-constants  (get-organization-invoice-config (:organization-id invoice))
        invoice-data            (-> invoice (dissoc :operations) (assoc :address (:address application)))]
    (->> invoice :operations (map :invoice-rows) flatten
         (mapv #(merge organization-constants invoice-data %)))))

(defn- fetch-transfer-batch-excel-data
  "Returns the invoice rows for the given transfer-batch as a seq of maps"
  [transfer-batch-id]
  (let [transfer-batch (mongo/by-id :invoice-transfer-batches transfer-batch-id [:invoices])
        invoice-ids    (map :id (:invoices transfer-batch))
        invoices       (map #(mongo/by-id :invoices %) invoice-ids)
        invoice-rows   (map get-enriched-invoice-rows invoices)]
    (->> invoice-rows
         (map #(assoc-in % [0 :first-row?] true)) ;Some info is only shown on the first row of an invoice
         flatten)))

(defn- get-invoice-excel-column-header
  "Returns the text that should be in the first row of the excel in the given column"
  [column]
  (get excel-column-headers column ""))

(defn- get-bill-ref
  "Returns the billing reference for the row, if any"
  [row]
  (let [bill-ref (:billing-reference row)]
    (if (ss/blank? bill-ref)
      (when-let [contact-person (#(and (not (ss/blank? %)) %) (:company-contact-person row))]
        (str contact-person))
      bill-ref)))

(defn- price-per-unit
  [{:keys [sums units]}]
  (if (zero? units)
    0
    (let [ppu (-> sums :with-discount :minor (/ units 100))]
      (cond-> ppu
        (not (integer? ppu)) (-> float (* 100) Math/round (* 0.01))))))

(defn- get-invoice-excel-cell-value
  "Returns a string containing the value for the given cell"
  [row column]
  (let [tmpl excel-string-templates]
    (when (or (:first-row? row) (not (contains? first-invoice-row-columns column)))
      (let [rowtext (str (:code row) " " (:text row))]
        (case column
          :t              (if (:first-row? row) 1 0)
          :tilauslaji     (:tilauslaji row)
          :myyntiorg      (:myyntiorg row)
          :jakelutie      (:jakelutie row)
          :sektori        (:sektori row)
          :laskuttaja     (:laskuttaja row)
          :nimike         (:nimike row)
          :tulosyksikko   (:tulosyksikko row)
          :bruttonetto    "B" ;;constant
          :kpl            ""  ;;Can be empty. It is not 100% sure if this is preferred
          :maksuehto      (:maksuehto row)
          :asiaviite      (:case-reference row)
          :littera        (:letter row)
          :nimi           (:entity-name row)
          :otsikkoteksti  (str (:distraint-note tmpl) " " (:vat tmpl) "\n" (:ref tmpl) " " (get-bill-ref row))
          :otsikkomuistio (str (:assoc-w-permit tmpl) " " (:application-id row) ", " (:address row))
          :qty            (:units row)
          :price-per-unit (price-per-unit row)
          :riviteksti     (ss/trim-to 40 rowtext)
          :rivimuistio    (->> rowtext (drop 40) ss/join not-empty)
          :asiakas        (:sap-number row)
          nil)))))

(defn- get-invoice-excel-row-values
  "Returns a seq containing string values for the given row"
  [data columns]
  (for [column columns] (get-invoice-excel-cell-value data column)))

(defn invoice-excel-conversion
  "Returns a generated RAW excel-file response of the invoices in the given transfer-batch"
  [transfer-batch-id]
  (let [columns [:t :tilauslaji :myyntiorg :jakelutie :sektori :viitelasku :asiakas :tunniste
                 :nimi :osoite :postino :toimipaikka :pvm :maksuehto :laskuttaja :asiaviite
                 :otsikkoteksti :otsikkomuistio :nimike :qty :kpl :price-per-unit :bruttonetto
                 :tulosyksikko :sistilaus :z :littera :tilastoraportti :ymparistokohde :toiminto
                 :varakoodi2 :riviteksti :rivimuistio]
        data    (fetch-transfer-batch-excel-data transfer-batch-id)
        header  (map get-invoice-excel-column-header columns)
        rows    (map #(get-invoice-excel-row-values % columns) data)]
    (invoice-excel-response "Invoice transfer batch" [{:name "Invoices"
                                                       :header header
                                                       :rows rows}])))
