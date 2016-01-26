(ns lupapalvelu.pdf.libreoffice-conversion-client-test
    (:require [lupapalvelu.pdf.libreoffice-conversion-client :as client]
      [lupapalvelu.pdf.pdf-test-util :as util]
      [midje.sweet :refer :all]
      [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
      [clojure.java.io :as io] [clojure.java.io :as io])
  (:import (java.io File)))


;;TODO: move file from test package to here
;(def file-uri (str (.toURI (io/resource "resources/sample-paatosote.rtf"))))

;;TODO: move this in interration test and run multiple simoultanious requests in pararaller threads
#_(facts "Test localhost pdfa-conversion service"
       (with-open
         [xin (io/input-stream file-uri)]
         (let [response (client/convert-to-pdfa file-uri xin)
               file-out (File/createTempFile "test-libre-rtf-" ".pdf")]
           (debug " creating temp file: " file-out " for\n" (keys response) ", body is : " (type (:body response)))
           (io/copy (:body response) file-out))))


