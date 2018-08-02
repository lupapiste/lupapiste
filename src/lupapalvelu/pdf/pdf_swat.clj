(ns lupapalvelu.pdf.pdf-swat
  (:require [clojure.java.io :as io])
  (:import [org.apache.pdfbox.pdmodel PDDocument]
           [org.apache.pdfbox.io MemoryUsageSetting]
           [org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline PDDocumentOutline]
           [java.io File]))

(defn rewrite-outline-as-empty-in-place! [^File pdf-file]
  {:pre [(instance? File pdf-file)]}
  (with-open [is (io/input-stream pdf-file)
              document (PDDocument/load is (MemoryUsageSetting/setupMixed (* 500 1024 1024)))]
    (-> (.getDocumentCatalog document)
        (.setDocumentOutline (PDDocumentOutline.)))
    (.save document pdf-file)))
