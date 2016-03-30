(ns lupapalvelu.pdf.libreoffice-template
  (:require [taoensso.timbre :refer [trace debug debugf info infof warn warnf error fatal]]
            [lupapalvelu.tiedonohjaus :as toj]
            [sade.util :as util]
            [sade.property :as p]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [clojure.xml :as xml]
            [clojure.java.io :as io]
            [clojure.string :as s]))
(def history-template-file "private/lupapiste-history-template.fodt")
(def history-verdict-file "private/lupapiste-verdict-template.fodt")

(defn- xml-escape [text]
  (clojure.string/escape (str text) {\< "&lt;", \> "&gt;", \& "&amp;"}))

(defn xml-table-row [& cols]
  (with-out-str (xml/emit-element {:tag     :table:table-row
                                   :content (map (fn [val] {:tag :table:table-cell :attrs {:office:value-type "string"} :content
                                                                 (map (fn [p] {:tag :text:p :content [(xml-escape p)]})
                                                                      (clojure.string/split val #"\n"))}) cols)})))

(defn- replace-text [line field value]
  (clojure.string/replace line field (str value)))

(defn- localized-text [lang value]
  (if (nil? value) "" (xml-escape (i18n/localize lang value))))

(defn- get-authority [lang {authority :authority :as application}]
  (if (and (:authority application)
           (domain/assigned? application))
    (str (:lastName authority) " " (:firstName authority))
    (i18n/localize lang "application.export.empty")))

(defn- get-operations [{:keys [primaryOperation secondaryOperations]}]
  (clojure.string/join ", " (map (fn [[op c]] (str (if (> c 1) (str c " \u00D7 ")) (i18n/loc "operations" op)))
                                 (frequencies (map :name (remove nil? (conj (seq secondaryOperations) primaryOperation)))))))

(defn common-field-map [application lang]
  {"FOOTER_PAGE" (localized-text lang "application.export.page")
   "FOOTER_DATE" (util/to-local-date (System/currentTimeMillis))


   "FIELD002"    (xml-escape (:address application))

   "FIELD003A"   (localized-text lang "application.muncipality")
   "FIELD003B"   (localized-text lang (str "municipality." (:municipality application)))

   "FIELD004A"   (localized-text lang "application.export.state")
   "FIELD004B"   (localized-text lang (:state application))

   "FIELD005A"   (localized-text lang "kiinteisto.kiinteisto.kiinteistotunnus")
   "FIELD005B"   (xml-escape (if (nil? (:propertyId application)) (i18n/localize lang "application.export.empty") (p/to-human-readable-property-id (:propertyId application))))

   "FIELD006A"   (localized-text lang "submitted")
   "FIELD006B"   (xml-escape (or (util/to-local-date (:submitted application)) "-"))

   "FIELD007A"   (localized-text lang "verdict-attachment-prints-order.order-dialog.lupapisteId")
   "FIELD007B"   (xml-escape (:id application))

   "FIELD008A"   (localized-text lang "applications.authority")
   "FIELD008B"   (xml-escape (get-authority lang application))

   "FIELD009A"   (localized-text lang "application.address")
   "FIELD009B"   (xml-escape (:address application))

   "FIELD010A"   (localized-text lang "applicant")
   "FIELD010B"   (xml-escape (clojure.string/join ", " (:_applicantIndex application)))

   "FIELD011A"   (localized-text lang "selectm.source.label.edit-selected-operations")
   "FIELD011B"   (xml-escape (get-operations application))})

(defn- write-line [line data wrtr]
  (.write wrtr (str (reduce (fn [s [k v]] (if (s/includes? s (str ">" k "<")) (replace-text s k v) s)) line data) "\n")))

(defn- get-table-name [line] (nth (re-find #"<table:table table:name=\"(.*?)\"" line) 1))

(defn- write-table! [rdr wrtr table-rows fields]
  (doseq [line (take-while (fn [line] (not (s/includes? line "</table:table-header-rows>"))) (line-seq rdr))]
    (write-line line fields wrtr)
    (when-let [table-rows2 (get fields (get-table-name line))]
      (write-table! rdr wrtr table-rows2 fields)))
  (write-line "      </table:table-header-rows>" fields wrtr)
  (doseq [row table-rows]
    (.write wrtr (str (apply xml-table-row row) "\n")))

  (doseq [skip-existing-rows (take-while (fn [line] (not (s/includes? line "</table:table>"))) (line-seq rdr))])
  (write-line "</table:table>" fields wrtr))


(defn create-libre-doc [template file fields]
  (with-open [wrtr (io/writer file :encoding "UTF-8" :append true)]
    (with-open [rdr (io/reader template)]
      (doseq [line (line-seq rdr)]
        (write-line line fields wrtr)
        (when-let [table-rows (get fields (get-table-name line))]
          (write-table! rdr wrtr table-rows fields))))))

(defn- build-history-row [data lang]
  ;(debug "row data:" data)
  [""
   (case (:category data)
     :attachment (i18n/localize lang "document")
     :document (i18n/localize lang "application.applicationSummary")
     :request-statement (i18n/localize lang "caseFile.operation.statement.request")
     :request-neighbor (i18n/localize lang "caseFile.operation.neighbor.request")
     :request-review (i18n/localize lang "caseFile.operation.review.request")
     :review (i18n/localize lang "caseFile.operation.review")
     "")
   (str
     (if (= (:category data) :attachment)
       (i18n/localize lang "attachmentType" (get-in data [:type :type-group]) (get-in data [:type :type-id]))
       (:type data))
     " " (:contents data)
     (if (get-in data [:version :major])
       (str ", v. " (get-in data [:version :major]) "." (get-in data [:version :minor]))
       ""))
   (or (util/to-local-date (:ts data)) "-")
   (:user data)])

(defn- build-history-child-rows [action docs lang]
  ;  (debug " docs: " (with-out-str (clojure.pprint/pprint docs)))
  (loop [docs-in docs
         result []]
    (let [[doc-attn & others] docs-in]
      ;(debug " doc-attn: " doc-attn)
      (if (nil? doc-attn)
        result
        (recur others (conj result (build-history-row doc-attn lang)))))))

(defn- build-history-rows [application lang]
  (let [data (toj/generate-case-file-data application)]
    ;(debug " data: " (with-out-str (clojure.pprint/pprint data)))
    (loop [data-in data
           result []]
      (let [[history & older] data-in
            new-result (-> result
                           (conj [(:action history) "" "" (or (util/to-local-date (:start history)) "-") (:user history)])
                           (into (build-history-child-rows " " (:documents history) lang)))]
        (if (nil? older)
          new-result
          (recur older new-result))))))

(defn build-xml-history [application lang]
  (clojure.string/join " " (map #(apply xml-table-row %) (build-history-rows application lang))))

(defn write-history-libre-doc [application lang file]
  (create-libre-doc (io/resource history-template-file) file (assoc (common-field-map application lang)
                                                               "FIELD001" (i18n/localize lang "caseFile.heading")
                                                               "COLTITLE1" (i18n/localize lang "caseFile.action")
                                                               "COLTITLE2" (i18n/localize lang "applications.operation")
                                                               "COLTITLE3" (i18n/localize lang "document")
                                                               "COLTITLE4" (i18n/localize lang "caseFile.documentDate")
                                                               "COLTITLE5" (i18n/localize lang "lisaaja")
                                                               "HISTORYTABLE" (build-history-rows application lang))))


(defn- verdict-dates [lang dates]
  (cond-> []
          (:anto dates) (conj [(i18n/localize lang "verdict.anto") (or (util/to-local-date (:anto dates)) "-")])
          (:lainvoimainen dates) (conj [(i18n/localize lang "verdict.lainvoimainen") (or (util/to-local-date (:lainvoimainen dates)) "-")])
          (:paatosdokumentinPvm dates) (conj [(i18n/localize lang "verdict.paatosdokumentinPvm") (or (util/to-local-date (:paatosdokumentinPvm dates)) "-")])
          (:paatosPvm dates) (conj [(i18n/localize lang "verdict.paatosPvm") (or (util/to-local-date (:paatosPvm dates)) "-")])
          (:julkipano dates) (conj [(i18n/localize lang "verdict.julkipano") (or (util/to-local-date (:julkipano dates)) "-")])
          (:viimeinenValitus dates) (conj [(i18n/localize lang "verdict.viimeinenValitus") (or (util/to-local-date (:viimeinenValitus dates)) "-")])
          (:aloitettava dates) (conj [(i18n/localize lang "verdict.aloitettava") (or (util/to-local-date (:aloitettava dates)) "-")])
          (:voimassaHetki dates) (conj [(i18n/localize lang "verdict.voimassaHetki") (or (util/to-local-date (:voimassaHetki dates)) "-")])
          (:raukeamis dates) (conj [(i18n/localize lang "verdict.raukeamis") (or (util/to-local-date (:raukeamis dates)) "-")])))


(defn write-verdict-libre-doc [application id id2 lang file]
  (let [verdict (first (filter #(= id (:id %)) (:verdicts application)))
        paatos (first (filter #(= id2 (:id %)) (:paatokset verdict)))
        reviews (map (fn [val] [(str (:katselmuksenLaji val)) (str (:tarkastuksenTaiKatselmuksenNimi val))]) (get-in paatos [:lupamaaraykset :vaaditutKatselmukset]))
        orders (map (fn [val] [(str (:sisalto val))]) (get-in paatos [:lupamaaraykset :maaraykset]))
        ]
    (debug "orders :" orders)
    (create-libre-doc (io/resource history-verdict-file) file (assoc (common-field-map application lang)
                                                                "FIELD001" (if (:sopimus verdict) (i18n/localize lang "userInfo.company.contract") (i18n/localize lang "application.verdict.title"))
                                                                "FIELD012" (str (i18n/localize lang "verdict.id") ": " (:kuntalupatunnus verdict))
                                                                "FIELD013" (i18n/localize lang "date")
                                                                "DATESTABLE" (verdict-dates lang (:paivamaarat paatos))
                                                                "FIELD014" (i18n/localize lang "verdict.lupamaaraukset")
                                                                "FIELD015A" (i18n/localize lang "foreman.requiredForemen")
                                                                "FIELD015B" (get-in paatos [:lupamaaraykset :vaaditutTyonjohtajat])
                                                                "FIELD016" (i18n/localize lang "verdict.vaaditutKatselmukset")
                                                                "REVIEWSTABLE" reviews

                                                                "FIELD17" (i18n/localize lang "verdict.maaraykset")
                                                                "ORDERSTABLE" orders
                                                                ))))