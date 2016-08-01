(ns lupapalvelu.pdftk
  (:require [taoensso.timbre :as timbre :refer [debug info warn error]]
            [clojure.java.shell :as shell]))

(defn- handle-result [result]
  ; POSIX return code 0 signals success, others are failure codes
  (when-not (zero? (:exit result))
    (throw (RuntimeException. (str "pdftk returned " (:exit result) ", STDOUT: " (str (:out result) ", STDERR: " (:err result)))))))

(defn create-pdftk-file [in file-name]
  (handle-result (shell/sh "pdftk" "-" "output" file-name :in in)))

(defn rotate-pdf [in file-name rotation]
  (let [rot (case rotation -90 "left", 90 "right", 180 "down")]
    (handle-result (shell/sh "pdftk" "-" "cat" (str "1-end" rot) "output" file-name :in in))))

(defn uncompress-pdf [in file-name]
  (handle-result (shell/sh "pdftk" "-" "output" file-name "uncompress" :in in)))
