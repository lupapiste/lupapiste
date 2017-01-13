(ns lupapalvelu.pdf.libreoffice-template-statement
  (:require [taoensso.timbre :as log]
            [sade.util :as util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pdf.libreoffice-template :refer [xml-escape child-attachments] :as template]
            [clojure.string :as s]
            [clojure.java.io :as io]))

(defn write-statement-libre-doc [application id lang file]
  (let [statement (first (filter #(= id (:id %)) (:statements application)))
        empty-text (i18n/localize lang "application.export.empty")]
    (template/create-libre-doc (io/resource "private/lupapiste-statement-template.fodt")
                               file
                               (assoc (template/common-field-map application lang)
                                 "FIELD001" (i18n/localize lang "application.statement.status")

                                 "LPTITLE_REQUESTED" (i18n/localize lang "statement.requested")
                                 "LPVALUE_REQUESTED" (or (util/to-local-date (:requested statement)) "-")

                                 "LPTITLE_GIVER" (i18n/localize lang "statement.giver")
                                 "LPVALUE_GIVER" (xml-escape (get-in statement [:person :name]))

                                 "LPTITLE_GIVEN" (i18n/localize lang "export.statement.given")
                                 "LPVALUE_GIVEN" (or (util/to-local-date (:given statement)) "-")

                                 "LPTITLE_STATUS" (i18n/localize lang "statement.title")
                                 "LPVALUE_STATUS" (if (s/blank? (:status statement)) empty-text (xml-escape (:status statement)))

                                 "LPTITLE_TEXT" (i18n/localize lang "statement.statement.text")
                                 "LPVALUE_TEXT" (->> (if (s/blank? (:text statement)) empty-text (:text statement))
                                                     (template/text->libre-paragraphs))

                                 "LPTITLE_CONTENT" (i18n/localize lang "application.statement.desc")
                                 "LPVALUE_CONTENT" (xml-escape (get-in statement [:person :text]))

                                 "LPTITLE_ATTACHMENTS" (i18n/localize lang "verdict.attachments")
                                 "LPVALUE_ATTACHMENTS" (xml-escape (str (count (child-attachments application :statements id)) " " (i18n/localize lang "unit.kpl"))))
                               :pre-escaped)))
