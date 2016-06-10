(ns lupapalvelu.libreoffice-conversion-client-itest
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [lupapalvelu.pdf.libreoffice-conversion-client :as client]
            [pdfboxing.text :as pdfbox]
            [clojure.string :as str]
            [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]])
  (:import (java.io File)))

(def file-uri (str (.toURI (io/resource "sample-paatosote.rtf"))))

;;TODO: run multiple simoultanious requests in pararaller threads
(facts "pdfa-conversion service"
  ; In normal code flow conversion is not called when feature is disabled, so no :archivabilityError.
  ; Here we call the service even when it is disabled and assume it service is not running.
  ; So if the service is running in the ci/local env but feature disabled = fail
  (with-open [xin (io/input-stream file-uri)]
    (let [response (client/convert-to-pdfa file-uri xin)
          file-out (File/createTempFile "test-libre-rtf-" ".pdf")]
      (if client/enabled?
        (try
          (fact "libre enabled, No connnection error expected" (:archivabilityError response) => nil)

          (io/copy (:content response) file-out)
          (let [pdf-content (pdfbox/extract (.getAbsolutePath file-out))
                rows (remove str/blank? (str/split pdf-content #"\r?\n"))]
            (fact "PDF data rows "
              (count rows) => 100
              (nth rows 1) => "Once there was a miller who was poor, but who had a beautiful"))
          (finally
            (.delete file-out)))

        (fact "libre is not enabled in this ENV [so the service should *NOT* be running in this ENV], expect connection error"
          (:archivabilityError response) => :libre-conversion-error)))))
