(ns lupapalvelu.pdf-export-itest
  (:require [clojure.java.io :as io]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.pdf-export :as pdf-export]
            [midje.sweet :refer :all]
            [pdfboxing.text :as pdfbox]))

(apply-remote-minimal)

(fact "Generate PDF from an application containing all documents"
  (let [apikey         sonja
        application-id (create-app-id apikey
                                      :municipality sonja-muni
                                      :address      "Paatoskuja 12")
        application    (query-application apikey application-id)
        fpath          "/tmp/test.pdf"
        file           (io/file fpath)]

    (clojure.pprint/pprint application)
    (pdf-export/generate application "fi" file)
    (println (pdfbox/extract fpath))))