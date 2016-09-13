(ns lupapalvelu.docx-test
  (:require [midje.sweet :refer :all]
            [pdfboxing.common]
            [pdfboxing.text :as pdfbox]
            [lupapalvelu.docx :as docx]
            [sade.strings :as ss]
            [clojure.java.io :as io])
  (:import (java.io File)))

(extend-protocol pdfboxing.common/PDFDocument
  java.io.File
  (obtain-document [source]
    (if (pdfboxing.common/is-pdf? source)
      (pdfboxing.common/load-pdf source))))

(facts "Yritystilisopimus"
  (let [company {:name "Asiakas Oy", :y "123456-1", :address1 "Osoiterivi 1", :zip "99999", :po "Stockholm"}
        contact {:firstName "Etu", :lastName "Suku"}
        account {:type "TEST", :price "BILLIONS!"}
        pdf-stream (docx/yritystilisopimus company contact account 0)
        pdf-file (File/createTempFile "docx-test" "temp")]
    (try
      (io/copy pdf-stream pdf-file)
      (let [pdf-content (pdfbox/extract pdf-file)]
        (fact "PDF contains date"
          pdf-content => (contains "01.01.1970"))

        (fact "PDF contains all model data"
          (doseq [[k v] (merge company contact account)]
            (fact {:midje/description k}
              (ss/contains? pdf-content v) => true))))
      (finally
        (io/delete-file pdf-file)))))
