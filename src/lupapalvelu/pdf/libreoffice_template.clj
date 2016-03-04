(ns lupapalvelu.pdf.libreoffice-template
  (:require [taoensso.timbre :refer [trace debug debugf info infof warn warnf error fatal]]
            [lupapalvelu.tiedonohjaus :as toj]
            [lupapalvelu.pdf.libreoffice-conversion-client :as converter]
            [sade.util :as util]
            [sade.property :as p]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :refer [with-lang loc localize]]
            [clojure.xml :refer [emit-element]]
            [clojure.java.io :refer [writer reader resource input-stream]])
  (:import (java.io File)))
(def HISTORY-TEMPLATE "private/lupapiste-history-template.fodt")

(defn- xml-escape [text]
  (clojure.string/escape (str text) {\< "&lt;", \> "&gt;", \& "&amp;"}))

(defn xml-table-row [& cols]
  (with-out-str (emit-element {:tag     :table:table-row
                               :content (map (fn [val] {:tag :table:table-cell :attrs {:office:value-type "string"} :content [{:tag :text:p :content [(xml-escape val)]}]}) cols)})))

(defn- replace-text [line field value]
  (clojure.string/replace line field (str value)))

(defn- localized-text [lang value]
  (if (nil? value) "" (xml-escape (localize lang value))))

;;deprecated
(defn write-xml-case-data! [data lang writer]
  (doseq [history data]
    (with-lang lang
               (.write writer (xml-table-row (:action history) "" (or (util/to-local-date (:start history)) "-") (:user history)))
               (doseq [doc-attn (:documents history)]
                 (.write writer (xml-table-row (:action history)
                                               (str (:type doc-attn) " "
                                                    (:category doc-attn) " "
                                                    (:contents doc-attn) " "
                                                    (:version doc-attn))
                                               (or (util/to-local-date (:ts doc-attn)) "-")
                                               (:user doc-attn)))))))

(defn- build-xml-history-child-rows [action docs]
  (loop [docs-in docs
         result []]
    (let [[doc-attn & others] docs-in]
      (if (nil? doc-attn)
        result
        (recur others (conj result (xml-table-row action
                                                  (str (:type doc-attn) " "
                                                       (:category doc-attn) " "
                                                       (:contents doc-attn) " "
                                                       (:version doc-attn))
                                                  (or (util/to-local-date (:ts doc-attn)) "-")
                                                  (:user doc-attn))))))))

(defn- build-xml-history-rows [application]
  (let [data (toj/generate-case-file-data application)]
    (loop [data-in data
           result []]
      (let [[history & older] data-in
            new-result (-> result
                           (conj (xml-table-row (:action history) "" (or (util/to-local-date (:start history)) "-") (:user history)))
                           (into (build-xml-history-child-rows (:action history) (:documents history))))]
        (if (nil? older)
          new-result
          (recur older new-result))))))

(defn build-xml-history [application]
  (clojure.string/join " " (build-xml-history-rows application)))

(defn- get-authority [lang {authority :authority :as application}]
  (if (and (:authority application)
           (domain/assigned? application))
    (str (:lastName authority) " " (:firstName authority))
    (localize lang "application.export.empty")))

(defn- get-operations [{:keys [primaryOperation secondaryOperations]}]
  (clojure.string/join ", " (map (fn [[op c]] (str (if (> c 1) (str c " \u00D7 ")) (loc "operations" op)))
                                 (frequencies (map :name (remove nil? (conj (seq secondaryOperations) primaryOperation)))))))

(defn common-field-map [application lang]
  {"Footer1" (localized-text lang "application.export.page")
   ;;TODO: FOOTERDATE FOOTERTIME FOOTERPAGE
   "FIELD001"  (localized-text lang "caseFile.heading")
   "FIELD002"  (xml-escape (:address application))

   "FIELD003A" (localized-text lang "application.muncipality")
   "FIELD003B" (localized-text lang (str "municipality." (:municipality application)))

   "FIELD004A" (localized-text lang "application.export.state")
   "FIELD004B" (localized-text lang (:state application))

   "FIELD005A" (localized-text lang "kiinteisto.kiinteisto.kiinteistotunnus")
   "FIELD005B" (xml-escape (if (nil? (:propertyId application)) (localize lang "application.export.empty") (p/to-human-readable-property-id (:propertyId application))))

   "FIELD006A" (localized-text lang "submitted")
   "FIELD006B" (xml-escape (or (util/to-local-date (:submitted application)) "-"))

   "FIELD007A" (localized-text lang "application.id")
   "FIELD007B" (xml-escape (:id application))

   "FIELD008A" (localized-text lang "applications.authority")
   "FIELD008B" (xml-escape (get-authority lang application))

   "FIELD009A" (localized-text lang "application.address")
   "FIELD009B" (xml-escape (:address application))

   "FIELD010A" (localized-text lang "applicant")
   "FIELD010B" (xml-escape (clojure.string/join ", " (:_applicantIndex application)))

   "FIELD011A" (localized-text lang "selectm.source.label.edit-selected-operations")
   "FIELD011B" (xml-escape (get-operations application))})

(defn- formatted-line [line data]
  (reduce (fn [s [k v]] (if (clojure.string/includes? s k) (replace-text s k v) s)) line data))

(defn create-libre-doc [template data file]
  (with-open [wrtr (writer file :encoding "UTF-8" :append true)]
    (with-open [rdr (reader template)]
      (doseq [line (line-seq rdr)]
        (.write wrtr (formatted-line line data))))))

(defn pdfa-from-template [template-file data]
  (let [tmp-file (File/createTempFile "template-" ".fodt")]
    (debug "created temp file: " (.getAbsolutePath tmp-file))
    (create-libre-doc template-file data tmp-file)
    (converter/convert-to-pdfa (.getName tmp-file) (input-stream tmp-file))))

(defn write-history-libre-doc [application lang file]
  (create-libre-doc (resource HISTORY-TEMPLATE) (assoc (common-field-map application lang) "HISTORY_ROWS_PLACEHOLDER" (build-xml-history application)) file))
