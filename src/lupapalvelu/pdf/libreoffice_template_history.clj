(ns lupapalvelu.pdf.libreoffice-template-history
  (:require [lupapalvelu.tiedonohjaus :as toj]
            [sade.util :as util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pdf.libreoffice-template :as template]
            [clojure.java.io :as io]))

(defn- build-history-row [{:keys [type text version category contents ts user]} lang]
  [""
   (str
     (case category
       :document (i18n/localize lang "caseFile.documentSubmitted")
       :request-statement (i18n/localize lang "caseFile.operation.statement.request")
       :request-neighbor (i18n/localize lang "caseFile.operation.neighbor.request")
       :request-neighbor-done (i18n/localize lang "caseFile.operation.neighbor.request.done")
       :request-neighbor-responded (i18n/localize lang "caseFile.operation.neighbor.responded")
       :request-review (i18n/localize lang "caseFile.operation.review.request")
       :review (i18n/localize lang "caseFile.operation.review")
       :tos-function-change (i18n/localize lang "caseFile.tosFunctionChange")
       :tos-function-correction (i18n/localize lang "caseFile.tosFunctionCorrection")
       :handler-change (i18n/localize lang "caseFile.handlerChange")
       :handler-added (i18n/localize lang "caseFile.handlerAdded")
       :handler-removed (i18n/localize lang "caseFile.handlerRemoved")
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

(defn- build-history-child-rows [docs lang]
  (loop [docs-in docs
         result []]
    (let [[doc-attn & others] docs-in]
      (if (nil? doc-attn)
        result
        (recur others (conj result (build-history-row doc-attn lang)))))))

(defn- build-history-rows [application lang]
  (let [data (toj/generate-case-file-data application lang)]
    (loop [data-in data
           result []]
      (let [[history & older] data-in
            new-result (-> result
                           (conj [(:action history) "" (or (util/to-local-date (:start history)) "-") (:user history)])
                           (into (build-history-child-rows (:documents history) lang)))]
        (if (nil? older)
          new-result
          (recur older new-result))))))

(defn write-history-libre-doc [application lang file]
  (template/create-libre-doc (io/resource "private/lupapiste-history-template.fodt")
                             file
                             (assoc (template/common-field-map application lang)
                               "FIELD001" (i18n/localize lang "caseFile.heading")
                               "COLTITLE1" (i18n/localize lang "caseFile.action")
                               "COLTITLE2" (i18n/localize lang "caseFile.event")
                               "COLTITLE3" (i18n/localize lang "caseFile.documentDate")
                               "COLTITLE4" (i18n/localize lang "lisaaja")
                               "HISTORYTABLE" (build-history-rows application lang))))
