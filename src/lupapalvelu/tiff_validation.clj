(ns lupapalvelu.tiff-validation
  (:require [clojure.java.io :as io]
            [taoensso.timbre :refer [error]])
  (:import [javax.imageio ImageIO]
           (javax.imageio.stream FileImageInputStream)
           (com.sun.media.imageio.plugins.tiff BaselineTIFFTagSet)))

(defn- find-tiff-errors [file]
  (let [readers (ImageIO/getImageReadersByFormatName "tiff")
        reader (.next readers)
        _ (.setInput reader (FileImageInputStream. (io/as-file file)))
        metadata (.getImageMetadata reader 0)
        tiff-fields (.getTIFFFields (.getRootIFD metadata))
        allowed-tag-numbers (.getTagNumbers (BaselineTIFFTagSet/getInstance))
        allowed-compression-values #{1 2 3 4}
        allowed-photometric-values #{0 1 2 3 4}]
    (reduce (fn [errors field]
              (let [tag-number (.getTagNumber field)]
                (cond
                  (not (.contains allowed-tag-numbers tag-number)) (conj errors (str "TIFF contains an unknown tag number: " tag-number))
                  (and (= BaselineTIFFTagSet/TAG_COMPRESSION tag-number)
                    (not (contains? allowed-compression-values (.getAsInt field 0)))) (conj errors (str "Invalid compression format: " (.getAsInt field 0)))
                  (and (= BaselineTIFFTagSet/TAG_PHOTOMETRIC_INTERPRETATION tag-number)
                    (not (contains? allowed-photometric-values (.getAsInt field 0)))) (conj errors (str "Invalid color space / photometric interpretation: " (.getAsInt field 0)))
                  :else errors)))
      []
      tiff-fields)))

(defn valid-tiff?
  "Checks that TIFF file metadata is readable, TIFF contains only baseline tags as defined by
  com.sun.media.imageio.plugins.tiff.BaselineTIFFTagSet (effectively meaning that the image is TIFF 6.0),
  PhotometricInterpretation is a baseline value (0-4) and Compression is uncompressed, CCITT 3 modified huffman RLE,
  CCITT 3 or CCITT 4 (values 1-4), as recommended by Arkistolaitos."
  [file]
  (try
    (let [errors (find-tiff-errors file)]
      (if (empty? errors)
        true
        (do
          (error "TIFF image" file "failed validation.")
          (doseq [err errors]
            (error err))
          false)))
    (catch Exception e
      (do (error "TIFF validation for image" file "caused an exception:" e)
          false))))
