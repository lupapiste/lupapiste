(ns sade.files
  (:require [taoensso.timbre :refer [warnf]]
            [clojure.java.io :as io]
            [sade.strings :as ss]
            [me.raynes.fs.compression :as fsc]
            [me.raynes.fs :as fs])
  (:import (java.io ByteArrayOutputStream)))

(defn ^java.io.InputStream temp-file-input-stream
  "File given as parameter will be deleted after the returned stream is closed."
  [^java.io.File file]
  {:pre [(instance? java.io.File file)]}
  (let [i (io/input-stream file)]
    (proxy [java.io.FilterInputStream] [i]
      (close []
        (proxy-super close)
        (when (= (io/delete-file file :could-not) :could-not)
          (warnf "Could not delete temporary file: %s" (.getAbsolutePath file)))))))

(defn ^java.io.File temp-file
  "Creates a file that will be deleted when the JVM exits.
   Note: consider using with-temp-file instead!"
  ([^String prefix ^String suffix]
    (doto (java.io.File/createTempFile prefix suffix) (.deleteOnExit)))
  ([^String prefix ^String suffix ^java.io.File directory]
    (doto (java.io.File/createTempFile prefix suffix directory) (.deleteOnExit))))

(defn filename-for-pdfa [filename]
  {:pre [(string? filename)]}
  (ss/replace filename #"(-PDFA)?\.(?i)pdf$" ".pdf"))

(defmacro with-temp-file
  "Creates and finally deletes a temp file.
   Given symbol sym hold the temp file and is visible in macro body."
  [sym & body]
  (assert (symbol? sym))
  `(let [prefix# (str ~(str *ns*) \_ (:line ~(meta &form)) \_)
         ~sym (temp-file prefix# ".tmp")]
     (try
       (do ~@body)
       (finally
         (io/delete-file ~sym :silently)))))

(defn slurp-bytes [fpath]
  (with-open [data (io/input-stream (fs/file fpath))]
    (with-open [out (ByteArrayOutputStream.)]
      (io/copy data out)
      (.toByteArray out))))

(defn- zip-files! [file fpaths]
  (let [filename-content-pairs (map (juxt fs/base-name slurp-bytes) fpaths)]
    (with-open [zip (fsc/make-zip-stream filename-content-pairs)]
      (io/copy zip (fs/file file)))
    file))

(defn build-zip! [fpaths]
  (let [temp-file (temp-file "build-zip-temp-file" ".zip")]
    (zip-files! temp-file fpaths)
    temp-file))

(defmacro with-zip-file
  "zips given filepaths to temporary zipfile and binds zip path to 'zip-file' symbol
  which can be used in body."
  [fpaths & body]
  `(let [temp-file# (build-zip! ~fpaths)
         ~'zip-file (.getPath temp-file#)]
     (try
       ~@body
       (finally (io/delete-file temp-file#)))))
