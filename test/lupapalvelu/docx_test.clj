(ns lupapalvelu.docx-test
  (:require [midje.sweet :refer :all]
            [pdfboxing.text :as pdfbox]
            [lupapalvelu.docx :as docx]
            [lupapalvelu.test-util :refer :all]
            [sade.strings :as ss]))

(facts "Yritystilisopimus"
  (let [company {:name "Asiakas Oy", :y "123456-1", :address1 "Osoiterivi 1", :zip "99999", :po "Stockholm"}
        contact {:firstName "Etu", :lastName "Suku"}
        account {:type "TEST", :price "BILLIONS!"}
        pdf-stream (docx/yritystilisopimus company contact account 0)
        pdf-content (pdfbox/extract pdf-stream)]

    (fact "PDF contains date"
      pdf-content => (contains "01.01.1970"))

    (fact "PDF contains all model data"
      (doseq [[k v] (merge company contact account)
             :let [result (doc-result (ss/contains? pdf-content v) k)]]
       result => (doc-check true?)))))
