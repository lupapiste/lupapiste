(ns sade.files
  (:require [taoensso.timbre :refer [error warnf]]
            [clojure.java.io :as io]
            [sade.strings :as ss]
            [me.raynes.fs :as fs])
  (:import [java.io InputStream ByteArrayOutputStream PipedInputStream PipedOutputStream FilterInputStream File]
           [java.util.zip ZipOutputStream ZipEntry]))

(defn ^InputStream temp-file-input-stream
  "File given as parameter will be deleted after the returned stream is closed."
  [^File file]
  {:pre [(instance? File file)]}
  (let [i (io/input-stream file)]
    (proxy [FilterInputStream] [i]
      (close []
        (let [^FilterInputStream this this]                 ; HACK just to give type hint to the magical `this`.
          (proxy-super close))
        (when (= (io/delete-file file :could-not) :could-not)
          (warnf "Could not delete temporary file: %s" (.getAbsolutePath file)))))))

(defn ^File temp-file
  "Creates a file that will be deleted when the JVM exits.
   Note: consider using with-temp-file instead!"
  ([^String prefix ^String suffix]
    (doto (File/createTempFile prefix suffix) (.deleteOnExit)))
  ([^String prefix ^String suffix ^File directory]
    (doto (File/createTempFile prefix suffix directory) (.deleteOnExit))))

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

(defn temp-file-zip
  "Builds a zip into a temporary file with the given `content-fn`. Returns the file handle."
  [prefix suffix content-fn]
  (let [temp-file (temp-file prefix suffix)]
    (with-open [zip (ZipOutputStream. (io/output-stream temp-file))]
      (content-fn zip)
      (.finish zip))
    temp-file))

(defn append-stream!
  "Appends the input stream `in` to the zip output stream `zip` with
  the name `file-name`"
  [^ZipOutputStream zip file-name in]
  (when in
    (.putNextEntry zip (ZipEntry. (ss/encode-filename file-name)))
    (io/copy in zip)
    (.closeEntry zip))
  zip)

(defn open-and-append!
  "Calls and opens `content-thunk` and appends its contents to the
  `zip` output stream with the name `file-name`"
  [^ZipOutputStream zip file-name content-thunk]
  (with-open [in ^InputStream (content-thunk)]
    (append-stream! zip file-name in))
  ;; Flush after each attachment to ensure data flows into the output pipe
  (.flush zip)
  zip)

(defn ^File build-zip! [fpaths]
  (temp-file-zip "build-zip-temp-file" ".zip"
                 (fn [zip]
                   (doseq [[file-name file-stream] (map (juxt fs/base-name slurp-bytes) fpaths)]
                     (append-stream! zip file-name file-stream)))))

(defmacro with-zip-file
  "zips given filepaths to temporary zipfile and binds zip path to 'zip-file' symbol
  which can be used in body."
  [fpaths & body]
  `(let [temp-file# (build-zip! ~fpaths)
         ~'zip-file (.getPath temp-file#)]
     (try
       ~@body
       (finally (io/delete-file temp-file#)))))

(defn piped-zip-input-stream
  "Builds a zip input stream. The zip is built by running `content-fn`
  on the corresponding output stream in a future. Returns the input stream."
  [content-fn]
  (let [pos (PipedOutputStream.)
        ;; Use 16 MB pipe buffer
        is (PipedInputStream. pos 16777216)
        zip (ZipOutputStream. pos)]
    (future
      ;; This runs in a separate thread so that the input stream can be returned immediately
      (try
        (content-fn zip)
        (.finish zip)
        (.flush zip)
        (catch Throwable t
          (error t "Error occurred while generating ZIP output stream" (.getMessage t)))
        (finally
          (.close zip)
          (.close pos))))
    is))
