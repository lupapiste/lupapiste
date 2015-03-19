(ns lupapalvelu.xml.asianhallinta.verdict
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [sade.core :refer [ok fail] :as sade])
  (:import (java.io FileNotFoundException)))

(defn- make-path [& args]
  (let [args (vec args)
        path (s/join "/" args)]
    (io/make-parents (s/join "/" (conj args ".")))
    path))


(defn- unzip-file [path-to-zip]
  (let [zip-file (io/as-file path-to-zip)]
    (when-not (.exists zip-file)
      (fail :error.unknown))

    (let [base-dir  (System/getProperty "java.io.tmpdir")
          base-name (str (sade/now))
          tmp-dir   (make-path base-dir base-name)]
      (println tmp-dir)
      (ok))))

(defn process-ah-verdict [path-to-zip ftp-user]
  (try
    (unzip-file path-to-zip)
    (catch FileNotFoundException e
      (println "File not found:" (.getMessage e))
      )))