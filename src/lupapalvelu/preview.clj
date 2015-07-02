(ns lupapalvelu.preview
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn warnf error errorf fatal]])
  (:import (org.apache.pdfbox.pdmodel PDDocument)
           (org.apache.pdfbox.util ImageIOUtil)
           (java.awt.image BufferedImage)
           (java.awt RenderingHints)
           (java.io ByteArrayOutputStream ByteArrayInputStream FileInputStream)
           (javax.imageio ImageIO)))

(def rez 600.0)

(defn buffered-image-to-input-stream
  "Converts BufferedImage inputStream"
  [image]
  (let [output (ByteArrayOutputStream.)]
    (ImageIOUtil/writeImage image "jpg" output ImageIOUtil/DEFAULT_SCREEN_RESOLUTION 0.5)
    (ByteArrayInputStream. (.toByteArray output))))

(defn scale-image
  "Crops and scales BufferedImage to predefined resolution"
  [image]
  (let [
        original-height (.getHeight image)
        original-width (.getWidth image)
        crop-x (if (< (/ original-height original-width) 5/7) (- original-width (/ original-height 5/7)) 0)
        crop-y (if (< (/ original-width original-height) 5/7) (- original-height (/ original-width 5/7)) 0)
        scale (min (/ rez (- original-width crop-x)) (/ rez (- original-height crop-y)))
        width (* scale (- original-width crop-x))
        height (* scale (- original-height crop-y))
        new-image (BufferedImage. width height BufferedImage/TYPE_INT_RGB)]
    (debugf "scale-image rez: %s x %s, crop: %s x %s, scale by %s" original-width original-height crop-x crop-y scale)
    (doto (.createGraphics new-image)
      (.setRenderingHint RenderingHints/KEY_INTERPOLATION, RenderingHints/VALUE_INTERPOLATION_BICUBIC)
      (.setRenderingHint RenderingHints/KEY_RENDERING, RenderingHints/VALUE_RENDER_QUALITY)
      (.setRenderingHint RenderingHints/KEY_ANTIALIASING, RenderingHints/VALUE_ANTIALIAS_ON)
      (.drawImage image, 0, 0, width, height, crop-x, crop-y, original-width, original-height, nil)
      (.dispose))
    new-image))

(defn pdf-to-buffered-image
  "Converts 1. page from PDF to BufferedImage"
  [pdf-input]
  (with-open [document (PDDocument/load pdf-input)]
    (.. document getDocumentCatalog getAllPages iterator next convertToImage)))

(defn raster-to-buffered-image
  "Converts Raster image to BufferedImage"
  [input]
  (ImageIO/read (if (= (type input) java.lang.String) (FileInputStream. input) input)))

(defn converter [content-type]
  (cond
    (= "application/pdf" content-type) pdf-to-buffered-image
    (re-matches (re-pattern "(image/(gif|jpeg|png|tiff))") content-type) raster-to-buffered-image))

(defn to-buffered-image
  "Tries to read content to image by JAI or apache.pdfbox. Retuns nil on fail"
  [content content-type]
  (try
    (when-let [op (converter content-type)]
      (op content))
    (catch Exception e (errorf "ERROR: preview to-buffered-image failed to read content type: %s, error: %s" content-type e))))

(defn try-create-preview-input-stream
  "Tries to create preview image IF content type can be processed to image by JAI or apache.pdfbox. Retuns nil on fail"
  [content content-type]
  (when-let [image (to-buffered-image content content-type)]
    (buffered-image-to-input-stream (scale-image image))))

(defn placeholder-image [] (ByteArrayInputStream. (byte-array 0)))