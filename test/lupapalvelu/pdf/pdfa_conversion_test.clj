(ns lupapalvelu.pdf.pdfa-conversion-test
  (:require
    [clojure.java.io :as io]
    [midje.sweet :refer :all]
    [midje.util :refer [testable-privates]]
    [sade.files :as files]
    [sade.util :as util]
    [lupapalvelu.pdf.pdfa-conversion :refer :all])
  (:import [java.io FileInputStream File]))

(testable-privates lupapalvelu.pdf.pdfa-conversion pdf2pdf-key)

(when (and (pdf2pdf-executable) (pdf2pdf-key))
  (against-background [(#'lupapalvelu.pdf.pdfa-conversion/store-converted-page-count anything anything) => nil]
    (facts "PDF/A conversion"
      (let [invalid-pdf (io/file "dev-resources/invalid-pdfa.pdf")]
        (files/with-temp-file output-file
          (fact "PDF conversion ok with file"
            (convert-to-pdf-a invalid-pdf output-file) => (contains {:output-file output-file
                                                                     :pdfa? true
                                                                     :autoConversion true})))

        (files/with-temp-file output-file
          (fact "Conversion with InputStream is OK"
            (convert-to-pdf-a (FileInputStream. invalid-pdf) output-file) => (contains {:output-file output-file
                                                                                        :pdfa? true
                                                                                        :autoConversion true})))

        (files/with-temp-file empty-file
          (files/with-temp-file output-file
            (fact "If conversion result is empty, original is returned"
              (convert-to-pdf-a invalid-pdf output-file) => {:pdfa? false}
              (provided
                (#'lupapalvelu.pdf.pdfa-conversion/run-pdf-to-pdf-a-conversion anything anything nil) => {:pdfa? true
                                                                                                          :output-file empty-file}))))))))
