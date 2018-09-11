(ns lupapalvelu.mime
  (:require [clojure.string :refer [split join trim]]
            [clojure.java.io :refer [reader file]]
            [sade.strings :as ss]
            [pantomime.mime :refer [mime-type-of add-pattern]]))

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

(def allowed-extensions
  (keys
    (into (sorted-map)
      (filter #(re-matches mime-type-pattern (second %)) mime-types))))

(defn mime-type [filename]
  (when filename
    (mime-type-of filename)))

(defn allowed-file? [fname-or-file]
  (when fname-or-file
    (when-let [mime-type (mime-type-of fname-or-file)]
      (not (empty? (re-matches mime-type-pattern mime-type))))))

(defn sanitize-filename [filename]
  (-> filename (ss/suffix "\\") (ss/suffix "/")))

(try
  (add-pattern "application/x-extension-ifc" ".+\\.ifc$" "foo.ifc")
  (catch java.lang.AssertionError _)) ; Pattern had already been added
