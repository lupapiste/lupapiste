(ns lupapalvelu.preview
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn warnf error errorf fatal]])
  (:import (org.apache.pdfbox.pdmodel PDDocument)
           (org.apache.pdfbox.util ImageIOUtil)
           (java.awt.image BufferedImage)
           (java.awt RenderingHints)
           (java.io ByteArrayOutputStream ByteArrayInputStream FileInputStream)
           (javax.imageio ImageIO)))

(def rez 600.0)

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
    (debugf "scale-image rez: " original-width "x" original-height " crop: " crop-x "x" crop-y " scale by " scale)
    (doto (.createGraphics new-image)
      (.setRenderingHint RenderingHints/KEY_INTERPOLATION, RenderingHints/VALUE_INTERPOLATION_BICUBIC)
      (.setRenderingHint RenderingHints/KEY_RENDERING, RenderingHints/VALUE_RENDER_QUALITY)
      (.setRenderingHint RenderingHints/KEY_ANTIALIASING, RenderingHints/VALUE_ANTIALIAS_ON)
      (.drawImage image, 0, 0, width, height, crop-x, crop-y, original-width, original-height, nil)
      (.dispose))
    new-image))

(defn buffered-image-to-input-stream
  "Converts BufferedImage inputStream"
  [image]
  (let [output (ByteArrayOutputStream.)]
    (ImageIOUtil/writeImage image "jpg" output ImageIOUtil/DEFAULT_SCREEN_RESOLUTION 0.5)
    (ByteArrayInputStream. (.toByteArray output))))

(defn pdf-to-buffered-image
  "Converts 1. page from PDF to BufferedImage"
  [pdf-input]
  (with-open [document (PDDocument/load pdf-input)]
    (.. document getDocumentCatalog getAllPages iterator next convertToImage)))

(defn raster-to-buffered-image
  "Converts Raster image to BufferedImage"
  [input]
  (ImageIO/read (if (= (type input) java.lang.String) (FileInputStream. input) input)))

(defn to-buffered-image
  [content content-type]
  (try
    (cond
      (= "application/pdf" content-type) (pdf-to-buffered-image content)
      (re-matches (re-pattern "(image/(gif|jpeg|png|tiff))") content-type) (raster-to-buffered-image content))
    (catch Exception e (errorf "ERROR: preview to-buffered-image failed to read content type: %s, error: %e" content-type e))))

(defn create-preview-input-stream
  [content content-type]
  (when-let [image (to-buffered-image content content-type)]
    (buffered-image-to-input-stream (scale-image image))))