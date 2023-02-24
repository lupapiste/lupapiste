(ns lupapalvelu.mime
  (:require [clojure.java.io :refer [reader]]
            [clojure.string :refer [split join]]
            [pantomime.mime :refer [mime-type-of add-pattern]]
            [sade.strings :as ss]
            [taoensso.timbre :as timbre])
  (:import [clojure.lang RT]
           [java.util.regex Pattern]))

;; Reads mime.types file provided by Apache project.
;; Ring has also some of the most common file extensions mapped, but is missing
;; docx and other MS Office formats.
(def mime-types
  (with-open [resource (RT/resourceAsStream nil "private/mime.types")]
    (into {} (for [line (line-seq (reader resource))
                   :let [^String l (ss/trim line)
                         type-and-exts (split l #"\s+")
                         mime-type (first type-and-exts)]
                   :when (and (not (.isEmpty l)) (not (.startsWith l "#")))]
               (into {} (for [ext (rest type-and-exts)] [ext mime-type]))))))

(def ^Pattern mime-type-pattern
  (re-pattern
    (join "|" [
          "(image/(gif|jpeg|png|tiff|vnd.dwg|x-pict))"
          "(text/(plain|rtf))"
          (str "(application/("
               (join "|"
                     ["x-step"
                      "pdf" "postscript" "zip" "x-7z-compressed"
                      "rtf" "msword" "vnd\\.ms-excel" "vnd\\.ms-powerpoint"
                      "vnd\\.oasis\\.opendocument\\..+"
                      "vnd\\.openxmlformats-officedocument\\..+"]) "))")])))

(def allowed-extensions
  (keys
    (into (sorted-map)
      (filter #(re-matches mime-type-pattern (second %)) mime-types))))

(defn mime-type
  "Best guess for the correct MIME type according to the given
  `filename`. Since `mime-type-of` does not handle every
  character (e.g., #) correctly, the filename is transmuted first."
  [filename]
  (some-> filename str ss/encode-filename mime-type-of))

(defn allowed-file? [filename]
  (when filename
    (if (string? filename)
      (some->> (mime-type filename)
               (re-matches mime-type-pattern)
               seq
               boolean)
      (timbre/error "allowed-file? must only be called with a string filename, not" (type filename)))))

(defn sanitize-filename [filename]
  (-> filename (ss/suffix "\\") (ss/suffix "/") ss/normalize))

(try
  (add-pattern "application/x-step" ".+\\.ifc$" "foo.ifc")
  (catch AssertionError _)) ; Pattern had already been added
