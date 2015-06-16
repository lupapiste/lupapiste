(ns lupapalvelu.mime
  (:require [clojure.string :refer [split join trim]]
            [clojure.java.io :refer [reader file]]
            [sade.strings :as ss]))

;; Reads mime.types file provided by Apache project.
;; Ring has also some of the most common file extensions mapped, but is missing
;; docx and other MS Office formats.
(def mime-types
  (with-open [resource (clojure.lang.RT/resourceAsStream nil "private/mime.types")]
    (into {} (for [line (line-seq (reader resource))
                   :let [l (ss/trim line)
                         type-and-exts (split l #"\s+")
                         mime-type (first type-and-exts)]
                   :when (and (not (.isEmpty l)) (not (.startsWith l "#")))]
               (into {} (for [ext (rest type-and-exts)] [ext mime-type]))))))

(def mime-type-pattern
  (re-pattern
    (join "|" [
          "(image/(gif|jpeg|png|tiff|vnd.dwg|x-pict))"
          "(text/(plain|rtf))"
          (str "(application/("
               (join "|"
                     ["x-extension-ifc"
                      "pdf" "postscript" "zip" "x-7z-compressed"
                      "rtf" "msword" "vnd\\.ms-excel" "vnd\\.ms-powerpoint"
                      "vnd\\.oasis\\.opendocument\\..+"
                      "vnd\\.openxmlformats-officedocument\\..+"]) "))")])))

(def mime-type-pattern-preview
  (re-pattern
    (join "|" [
               "(image/(gif|jpeg|png|tiff))"
               "(application/pdf)"])))

(def allowed-extensions
  (keys
    (into (sorted-map)
          (filter #(re-matches mime-type-pattern-preview (second %)) mime-types))))

(defn mime-type [filename]
  (when filename
    (get mime-types (.toLowerCase (ss/suffix filename ".")))))

(defn allowed-file? [filename]
  (when-let [t (mime-type filename)]
      (re-matches mime-type-pattern t)))

(defn sanitize-filename [filename]
  (-> filename (ss/suffix "\\") (ss/suffix "/")))
