(ns lupapalvelu.libreoffice-conversion-client-itest
  (:require [midje.sweet :refer :all]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [pdfboxing.text :as pdfbox]
            [lupapalvelu.pdf.libreoffice-conversion-client :as client])
  (:import (java.io File)))

(defn- verify-pdf [response]
  ; In normal code flow conversion is not called when feature is disabled, so no :archivabilityError.
  ; Here we call the service even when it is disabled and assume it service is not running.
  ; So if the service is running in the ci/local env but feature disabled = fail
  (let [file-out (File/createTempFile "test-libre-rtf-" ".pdf")]
    (try
      (fact "libre enabled, No connnection error expected" (:archivabilityError response) => nil)

      (io/copy (:content response) file-out)
      (let [pdf-content (pdfbox/extract (.getAbsolutePath file-out))
            rows (remove s/blank? (s/split pdf-content #"\r?\n"))]
        (fact "PDF data rows"
          (count rows) => 18
          (first rows) => "Lupapiste"
          (second rows) => "P\u00e4\u00e4t\u00f6sote"))

      (finally
        (.delete file-out)))))

;;TODO: run multiple simoultanious requests in pararaller threads
(facts "pdfa-conversion service"
  (fact "input-stream"
    (with-open [xin (io/input-stream (io/resource "sample-paatosote.rtf"))]
      (let [response (client/convert-to-pdfa file-uri xin)]
        (if (client/enabled?)
          (verify-pdf response)
          (fact "libre is not enabled in this ENV [so the service should *NOT* be running in this ENV], expect connection error"
            (:archivabilityError response) => :libre-conversion-error)))))

  (if (client/enabled?)
    (fact "file"
      (let [response (client/convert-to-pdfa file-uri (io/file "dev-resources/sample-paatosote.rtf"))]
        (verify-pdf response)))
    (println "libre conversion disabled, skipped test")))
