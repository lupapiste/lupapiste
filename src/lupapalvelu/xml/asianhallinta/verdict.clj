(ns lupapalvelu.xml.asianhallinta.verdict
  (:require [sade.core :refer [ok fail fail!]]
            [sade.common-reader :as reader]
            [sade.xml :as xml]
            [taoensso.timbre :refer [error warn]]
            [me.raynes.fs :as fs]
            [me.raynes.fs.compression :as fsc]))

(defn- error-and-fail! [error-msg fail-key]
  (error error-msg)
  (fail! fail-key))

(defn- unzip-file [path-to-zip]
  (if-not (fs/exists? path-to-zip)
    (error-and-fail! (str "Could not find file " path-to-zip) :error.integration.asianhallinta-file-not-found)
    (let [tmp-dir (fs/temp-dir "ah")]
      (fsc/unzip path-to-zip tmp-dir)
      tmp-dir)))



(defn process-ah-verdict [path-to-zip ftp-user]
  (try
    (let [unzipped-path (unzip-file path-to-zip)
          xmls (fs/find-files unzipped-path #".*xml$")]

      ; path must contain one xml
      (when-not (= (count xmls) 1)
        (error-and-fail! (str "Expected to find one xml, found " (count xmls)) :error.integration.asianhallinta-too-many-xmls))

      ; parse XML
      (let [parsed-xml (-> (first xmls) slurp xml/parse reader/strip-xml-namespaces xml/xml->edn)]
        ; path must contain all attachments referenced in xml
        )



      (ok))
    (catch Exception e
      (if-let [error-key (some-> e ex-data :object :text)]
        (fail error-key)
        (fail :error.unknown)))))