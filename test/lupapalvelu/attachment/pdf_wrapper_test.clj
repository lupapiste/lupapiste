(ns lupapalvelu.attachment.pdf-wrapper-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.attachment.pdf-wrapper :refer :all]
            [clojure.java.io :as io]
            [sade.files :as files]
            [lupapalvelu.pdf.pdfa-conversion :refer :all]))

(testable-privates lupapalvelu.pdf.pdfa-conversion pdf2pdf-key)

(facts "about wrapping JPEGs into PDFs"
  (let [jpeg-file (io/file "dev-resources/cake.jpg")
        color-tiff-file (io/file "dev-resources/cake-lzw.tif")
        bw-tiff-file (io/file "dev-resources/drawing.tif")
        png-file (io/file "dev-resources/cake.png")
        files [[:jpeg jpeg-file] [:tiff color-tiff-file] [:tiff bw-tiff-file] [:png png-file]]
        use-pdf2pdf? (and (pdf2pdf-executable) (pdf2pdf-key))]

    (doseq [[file-format file] files]
      (files/with-temp-file target-file
        (wrap! file-format file target-file "test")

        (fact "Original image and PDF file sizes should be within 35 %"
          (< (- (.length target-file) (.length file)) (* 0.35 (.length file))) => true)

        (when use-pdf2pdf?
          (against-background [(#'lupapalvelu.pdf.pdfa-conversion/store-converted-page-count anything anything) => nil]
                              (files/with-temp-file output-file
                                (fact "The generated PDF file is in valid PDF/A form"
                                  (convert-to-pdf-a target-file output-file) => (contains {:already-valid-pdfa? true
                                                                                           :autoConversion false
                                                                                           :output-file output-file})))))))))
