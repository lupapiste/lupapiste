(ns sade.files-test
  (:require [midje.sweet :refer :all]
            [sade.files :as files]))

(facts "with-temp-file"
  (try
    (let [saved-file-ref (atom nil)]
      (files/with-temp-file tmp
        (fact "tmp symbol got File value"
          (instance? java.io.File tmp) => true)
        (fact "temp file exists"
          (.exists tmp) => true)
        (fact "temp file is named after the calling file"
          (.getName tmp) => #"^sade.files-test[_\d]+.tmp$")

        (fact "meta: save reference for further study"
          (reset! saved-file-ref tmp)
          (.exists @saved-file-ref) => true)
        (throw (RuntimeException. "Abort with-temp-file block")))
      (fact "Temp file is deleted in with-temp-file block even if an exception is thrown"
        (.exists @saved-file-ref) => false))
    (catch RuntimeException e)))
