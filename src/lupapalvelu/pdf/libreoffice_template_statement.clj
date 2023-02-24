(ns lupapalvelu.pdf.libreoffice-template-statement
  (:require [clojure.java.io :as io]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pdf.libreoffice-template :refer [xml-escape child-attachments] :as template]
            [sade.date :as date]
            [sade.strings :as ss]))

(defn write-statement-libre-doc [application id lang file]
  (let [statement (first (filter #(= id (:id %)) (:statements application)))
        empty-text (i18n/localize lang "application.export.empty")]
    (template/create-libre-doc (io/resource "private/lupapiste-statement-template.fodt")
                               file
                               (assoc (template/common-field-map application lang)
                                 "FIELD001" (i18n/localize lang "application.statement.status")

                                 "LPTITLE_REQUESTED" (i18n/localize lang "statement.requested")
                                 "LPVALUE_REQUESTED" (or (date/finnish-date (:requested statement) :zero-pad) "-")

                                 "LPTITLE_GIVER" (i18n/localize lang "statement.giver")
                                 "LPVALUE_GIVER" (xml-escape (get-in statement [:person :name]))

                                 "LPTITLE_GIVEN" (i18n/localize lang "export.statement.given")
                                 "LPVALUE_GIVEN" (or (date/finnish-date (:given statement) :zero-pad) "-")

                                 "LPTITLE_STATUS" (i18n/localize lang "statement.title")
                                 "LPVALUE_STATUS" (if (ss/blank? (:status statement)) empty-text (xml-escape (:status statement)))

                                 "LPTITLE_TEXT" (i18n/localize lang "statement.statement.text")
                                 "LPVALUE_TEXT" (->> (if (ss/blank? (:text statement)) empty-text (:text statement))
                                                     (template/text->libre-paragraphs))

                                 "LPTITLE_CONTENT" (i18n/localize lang "application.statement.desc")
                                 "LPVALUE_CONTENT" (xml-escape (get-in statement [:person :text]))

                                 "LPTITLE_ATTACHMENTS" (i18n/localize lang "verdict.attachments")
                                 "LPVALUE_ATTACHMENTS" (xml-escape (str (count (child-attachments application :statements id)) " " (i18n/localize lang "unit.kpl"))))
                               :pre-escaped)))
