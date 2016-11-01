(ns sade.files
  (:require [taoensso.timbre :as timbre :refer [warnf]]
            [clojure.java.io :as io]
            [sade.strings :as ss]))

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
  "Creates a file that will be deleted when the JVM exits."
  ([^String prefix ^String suffix]
    (doto (java.io.File/createTempFile prefix suffix) (.deleteOnExit)))
  ([^String prefix ^String suffix ^java.io.File directory]
    (doto (java.io.File/createTempFile prefix suffix directory) (.deleteOnExit))))

(defn filename-for-pdfa [filename]
  {:pre [(string? filename)]}
  (ss/replace filename #"(-PDFA)?\.(?i)pdf$" ".pdf"))

(defmacro with-temp-file
  "Creates and deletes a temp file.
   sym is visible in body, file-pattern should be in form of filename.ext"
  [sym & body]
  (assert (symbol? sym))
  `(let [prefix# (str ~(str *ns*) \_ (:line ~(meta &form)) \_)
         ~sym (temp-file prefix# ".tmp")]
     (try
       (do ~@body)
       (finally
         (io/delete-file ~sym :silently)))))
