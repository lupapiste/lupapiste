(ns lupapalvelu.xml.asianhallinta.verdict
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [sade.core :refer [ok fail] :as sade]
            [me.raynes.fs :as fs])
  (:import (java.io FileNotFoundException)))

(defn- unzip-file [path-to-zip]
  (let [zip-file (io/as-file path-to-zip)]
    (when-not (.exists zip-file)
      (fail :error.unknown))

    (let [tmp-dir (fs/temp-dir "ah")]
      (ok))))




(defn process-ah-verdict [path-to-zip ftp-user]
  (try
    (unzip-file path-to-zip)
    (catch FileNotFoundException e
      (println "File not found:" (.getMessage e))
      )))