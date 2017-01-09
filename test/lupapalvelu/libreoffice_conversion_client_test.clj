(ns lupapalvelu.libreoffice-conversion-client-test
  (:require [taoensso.timbre :as timbre]
            [midje.sweet :refer :all]
            [lupapalvelu.pdf.libreoffice-conversion-client :as client]))

(timbre/with-level :fatal
  (facts "Exception from HTTP request"
    (against-background
      (sade.http/post anything anything) =throws=> (Exception. "expected"))

    (let [original-name    "file.rtf"
          original-content "not really an RFT file..."
          result (client/convert-to-pdfa original-name (java.io.ByteArrayInputStream. (.getBytes original-content)))]

      (fact "original filename is returned, with archivabilityError"
        result  => (contains {:filename original-name, :archivabilityError :libre-conversion-error}))

      (fact "original content is returned"
        (slurp (:content result)) => original-content))))
