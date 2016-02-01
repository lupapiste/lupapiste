(ns lupapalvelu.libreoffice-conversion-client-test
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [lupapalvelu.pdf.libreoffice-conversion-client :as client]
            [pdfboxing.text :as pdfbox]
            [clojure.string :as str]
            [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]])
  (:import (java.io File)))

(def file-uri (str (.toURI (io/resource "resources/sample-paatosote.rtf"))))

;;TODO: run multiple simoultanious requests in pararaller threads
(facts "Test localhost pdfa-conversion service"
       (if client/enabled?
         (with-open
           [xin (io/input-stream file-uri)]
           (let [response (client/convert-to-pdfa file-uri xin)
                 file-out (File/createTempFile "test-libre-rtf-" ".pdf")]
             ;(debug " creating temp file: " file-out " for\n" (keys response) ", body is : " (type (:body response)))
             (io/copy (:body response) file-out)
             (let [pdf-content (pdfbox/extract (.getAbsolutePath file-out))
                   rows (remove str/blank? (str/split pdf-content #"\r?\n"))]
               (fact "PDF data rows "
                     (count rows) => 36
                     (nth rows 1) => "xxx"))
             (.delete file-out)))
         (debug "   skipping lupapalvelu.libreoffice-conversion-client-test - no libreoffice.url defined !")))


