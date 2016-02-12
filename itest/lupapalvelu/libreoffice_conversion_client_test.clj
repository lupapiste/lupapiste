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
       (with-open
         [xin (io/input-stream file-uri)]
         (let [response (client/convert-to-pdfa file-uri xin)
               file-out (File/createTempFile "test-libre-rtf-" ".pdf")]
           (if client/enabled?
             (do (fact "libre enabled, No connnection error expected "
                       (:archivabilityError response) => nil)
                 (io/copy (:content response) file-out)
                 (let [pdf-content (pdfbox/extract (.getAbsolutePath file-out))
                       rows (remove str/blank? (str/split pdf-content #"\r?\n"))]
                   (fact "PDF data rows "
                         (count rows) => 100
                         (nth rows 1) => "Once there was a miller who was poor, but who had a beautiful"))
                 (.delete file-out))
             (fact "libre not enabled, expect connection error"
                   (:archivabilityError response) => :libre-connection-error)))))