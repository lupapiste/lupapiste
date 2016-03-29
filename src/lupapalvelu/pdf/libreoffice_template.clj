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

(defn- build-xml-history-row [{:keys [type text version category contents ts user]} lang]
  ;(debug "row data:" data)
  [""
   (str
     (case category
       :document (i18n/localize lang "caseFile.documentSubmitted")
       :request-statement (i18n/localize lang "caseFile.operation.statement.request")
       :request-neighbor (i18n/localize lang "caseFile.operation.neighbor.request")
       :request-review (i18n/localize lang "caseFile.operation.review.request")
       :review (i18n/localize lang "caseFile.operation.review")
       :tos-function-change (i18n/localize lang "caseFile.tosFunctionChange")
       "")
     ": "
     (cond
       text text
       (map? type) (i18n/localize lang "attachmentType" (:type-group type) (:type-id type))
       type (i18n/localize lang type))
     (when contents
       (str ", " contents))
     (when (seq version)
       (str ", v. " (:major version) "." (:minor version))))
   (or (util/to-local-date ts) "-")
   user])

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

   "FIELD001"    (localized-text lang "caseFile.heading")
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

(defn write-history-libre-doc [application lang file]
  (create-libre-doc (io/resource history-template-file) file (assoc (common-field-map application lang)
                                                               "COLTITLE1" (i18n/localize lang "caseFile.action")
                                                               "COLTITLE2" (i18n/localize lang "applications.operation")
                                                               "COLTITLE3" (i18n/localize lang "document")
                                                               "COLTITLE4" (i18n/localize lang "caseFile.documentDate")
                                                               "COLTITLE5" (i18n/localize lang "lisaaja")
                                                               "HISTORYTABLE" (build-history-rows application lang))))

(defn write-verdict-libre-doc [application lang file]
  (create-libre-doc (io/resource history-template-file) file (assoc (common-field-map application lang)
                                                               "COLTITLE1" (i18n/localize lang "caseFile.action")
                                                               "COLTITLE2" (i18n/localize lang "applications.operation")
                                                               "COLTITLE3" (i18n/localize lang "document")
                                                               "COLTITLE4" (i18n/localize lang "caseFile.documentDate")
                                                               "COLTITLE5" (i18n/localize lang "lisaaja")
                                                               )))