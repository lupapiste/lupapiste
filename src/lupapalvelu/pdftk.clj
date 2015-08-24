(ns lupapalvelu.pdftk
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error fatal]]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]))


(defn create-pdftk-file [in file-name]
  (let [result (shell/sh "pdftk" "-" "output" file-name :in in)]
    ; POSIX return code 0 signals success, others are failure codes
    (when-not (zero? (:exit result))
      (throw (RuntimeException. (str "pdftk returned " (:exit result) ", STDOUT: " (str (:out result) ", STDERR: " (:err result))))))))
