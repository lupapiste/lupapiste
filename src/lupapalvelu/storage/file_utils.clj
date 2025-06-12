(ns lupapalvelu.storage.file-utils
  "Utility functions for handling files and file contents."
  (:require [clojure.java.io :as io])
  (:import (java.io PipedInputStream PipedOutputStream)
           (java.util.zip GZIPInputStream GZIPOutputStream)))

(set! *warn-on-reflection* true)

(defn ->gzip
  "Get a gzipped stream of a string or stream"
  [s]
  (let [os (PipedOutputStream.)
        is (PipedInputStream. os 32768)
        gs (GZIPOutputStream. os 32768)]
    (future
      (try
        (io/copy s gs)
        (.finish gs)
        (.flush gs)
        (finally
          (.close gs)
          (.close os))))
    is))

(defn gzip->str [s]
  (with-open [is (GZIPInputStream. s)]
    (slurp is)))
