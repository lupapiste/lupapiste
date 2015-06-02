(ns lupapalvelu.pdf-export-test
  (:require [clojure.string :as str]
            [lupapalvelu.pdf-export :as pdf-export]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.itest-util :as util]
            [lupapalvelu.i18n :refer [with-lang loc]]
            [midje.sweet :refer :all]
            [pdfboxing.text :as pdfbox]))

(def ignored-schemas #{"hankkeen-kuvaus-jatkoaika"
                       "poikkeusasian-rakennuspaikka"
                       "hulevedet"
                       "talousvedet"
                       "ottamismaara"
                       "ottamis-suunnitelman-laatija"
                       "kaupunkikuvatoimenpide"
                       "task-katselmus"
                       "approval-model-with-approvals"
                       "approval-model-without-approvals"})

(defn- localized-doc-headings [schema-names]
  (map #(loc (str % "._group_label")) schema-names))

(facts "Generate PDF from dummy application with all documents"
  (let [schema-names (remove ignored-schemas (keys (schemas/get-schemas 1)))
        dummy-docs   (map util/dummy-doc schema-names)
        application  (merge domain/application-skeleton {:documents dummy-docs
                                                         :municipality "444"
                                                         :state "draft"})

        file         (java.io.File/createTempFile "test" ".pdf")
        langs        ["fi" "sv"]]

    ; Iterate over all languages
    (doseq [lang langs]
      (pdf-export/generate application lang file)

      (let [pdf-content (pdfbox/extract (.getAbsolutePath file))
            rows        (remove str/blank? (str/split pdf-content #"\n"))]

        (fact "All localized document headers are present in the PDF"
          (with-lang lang
            (doseq [heading (localized-doc-headings schema-names)]
              pdf-content => (contains heading :gaps-ok))))

        (fact "PDF does not contain unlocalized strings"
          (doseq [row rows]
            row =not=> (contains "???")))))

    (.delete file)))

