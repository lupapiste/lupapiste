(ns lupapalvelu.docx-test
  (:require [clojure.java.io :as io]
            [midje.sweet :refer :all]
            [pdfboxing.common]
            [pdfboxing.text :as pdfbox]
            [lupapalvelu.docx :as docx]
            [sade.files :as files]
            [sade.strings :as ss]))

(facts "Yritystilisopimus"
  (let [company {:name "Asiakas Oy", :y "123456-1", :address1 "Osoiterivi 1", :zip "99999", :po "Stockholm"}
        contact {:firstName "Etu", :lastName "Suku"}
        account {:type "TEST", :price "BILLIONS!"}
        pdf-stream (docx/yritystilisopimus company contact account 0)
        pdf-file (files/temp-file "docx-test" "temp")]
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
