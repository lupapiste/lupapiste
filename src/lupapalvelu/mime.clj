(ns lupapalvelu.mime
  (:require [clojure.java.io :refer [reader]]
            [clojure.string :refer [join split]]
            [lupapalvelu.logging :as log]
            [pantomime.mime :refer [add-pattern mime-type-of]]
            [sade.strings :as ss])
  (:import [clojure.lang RT]
           [java.util.regex Pattern]))

(set! *warn-on-reflection* true)

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

(defn- make-mime-pattern
  ^Pattern [& {:keys [include-archives?]
               :or   {include-archives? true}}]
  (re-pattern
    (join "|" ["(image/(gif|jpeg|png|tiff|vnd.dwg|x-pict))"
               "(text/(plain|rtf))"
               (str "(application/("
                    (->> ["x-step"
                          "pdf" "postscript"
                          (when include-archives? "zip")
                          (when include-archives? "x-7z-compressed")
                          "rtf" "msword" "vnd\\.ms-excel" "vnd\\.ms-powerpoint"
                          "vnd\\.oasis\\.opendocument\\..+"
                          "vnd\\.openxmlformats-officedocument\\..+"]
                         (keep identity)
                         (join "|"))
                    "))")])))

(def ^Pattern mime-type-pattern (make-mime-pattern))

(def ^Pattern mime-type-pattern-excluding-archives
  "Subset of `mime-type-pattern`, but excludes `.zip` and `.7z`."
  (make-mime-pattern :include-archives? false))

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
      (log/error "allowed-file? must only be called with a string filename, not" (type filename)))))

(defn sanitize-filename [filename]
  (-> filename (ss/suffix "\\") (ss/suffix "/") ss/normalize))

(try
  (add-pattern "application/x-step" ".+\\.ifc$" "foo.ifc")
  (catch AssertionError _)) ; Pattern had already been added
