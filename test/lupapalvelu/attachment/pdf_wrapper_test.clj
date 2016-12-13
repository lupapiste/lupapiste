(ns lupapalvelu.attachment.pdf-wrapper-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.attachment.pdf-wrapper :refer :all]
            [clojure.java.io :as io]
            [sade.files :as files]
            [lupapalvelu.pdf.pdfa-conversion :refer :all]))

(testable-privates lupapalvelu.pdf.pdfa-conversion pdf2pdf-key)

(facts "about wrapping JPEGs into PDFs"
  (let [jpeg-file (io/file "dev-resources/cake.jpg")]
    (files/with-temp-file target-file
      (wrap! jpeg-file target-file "test")

      (fact "JPG and PDF file sizes should be similar"
        (< (- (.length target-file) (.length jpeg-file)) 10000) => true)

      (when (and (pdf2pdf-executable) (pdf2pdf-key))
        (against-background [(#'lupapalvelu.pdf.pdfa-conversion/store-converted-page-count anything anything) => nil]
                            (files/with-temp-file output-file
                              (fact "The generated PDF file is in valid PDF/A form"
                                (convert-to-pdf-a target-file output-file) => (contains {:already-valid-pdfa? true
                                                                                         :autoConversion false
                                                                                         :output-file output-file}))))))))
