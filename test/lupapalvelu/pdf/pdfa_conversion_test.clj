(ns lupapalvelu.pdf.pdfa-conversion-test
  (:require
    [clojure.java.io :as io]
    [midje.sweet :refer :all]
    [midje.util :refer [testable-privates]]
    [sade.util :as util]
    [lupapalvelu.pdf.pdfa-conversion :refer :all])
  (:import [java.io FileInputStream File]))

(testable-privates lupapalvelu.pdf.pdfa-conversion pdf2pdf-key)

(when (and (pdf2pdf-executable) (pdf2pdf-key))
  (let [invalid-pdf (io/file "dev-resources/invalid-pdfa.pdf")
        temp-target-path (.getCanonicalPath (File/createTempFile "pdfa-conversion-test" "pdf"))]
    (fact "PDF conversion ok with file"
      (convert-to-pdf-a invalid-pdf
                        temp-target-path) => (contains {:output-file (partial instance? java.io.File)
                                                        :pdfa? true}))

    (fact "Conversion with InputStream is OK"
      (convert-to-pdf-a (FileInputStream. invalid-pdf)
                        temp-target-path) => (contains {:output-file (partial instance? java.io.File)
                                                        :pdfa?        true}))

    (fact "If conversion result is empty, original is returned"
      (convert-to-pdf-a invalid-pdf temp-target-path) => {:pdfa? false}
      (provided
        (#'lupapalvelu.pdf.pdfa-conversion/run-pdf-to-pdf-a-conversion anything anything) => {:pdfa? true
                                                                                              :output-file (File/createTempFile "pdfa-conversion-test" "pdf")}))))
