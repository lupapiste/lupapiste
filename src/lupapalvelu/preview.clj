(ns lupapalvelu.preview
  (:require [clojure.java.io :as io])
  (:import (org.apache.pdfbox.pdmodel PDDocument)
           (org.apache.pdfbox.util PDFImageWriter ImageIOUtil)
           (java.awt.image BufferedImage)
           (java.awt RenderingHints)
           (java.io FileOutputStream ByteArrayOutputStream ByteArrayInputStream)))

(def rez 600.0)

(defn pdf-preview-file
  "Quick and dirty preview image file creation. "
  [filename]
  (try (let [document (PDDocument/load filename)
             pdfWriter (PDFImageWriter.)]
         (.writeImage pdfWriter document "jpg" "" 1 Integer/MAX_VALUE "preview_" 1 16))
       true
       (catch Exception e false)))

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
    (println "pdf rez: " original-width "x" original-height " crop: " crop-x "x" crop-y " scale by " scale)
    (doto (.createGraphics new-image)
      (.setRenderingHint RenderingHints/KEY_INTERPOLATION, RenderingHints/VALUE_INTERPOLATION_BICUBIC)
      (.setRenderingHint RenderingHints/KEY_RENDERING, RenderingHints/VALUE_RENDER_QUALITY)
      (.setRenderingHint RenderingHints/KEY_ANTIALIASING, RenderingHints/VALUE_ANTIALIAS_ON)
      (.drawImage image, 0, 0, width, height, crop-x, crop-y, original-width, original-height, nil)
      (.dispose))
    new-image))

(defn output-image
  "Writes BufferedImage to OutputStream"
  [image file-output-stream compression]
  (ImageIOUtil/writeImage image "jpg" file-output-stream ImageIOUtil/DEFAULT_SCREEN_RESOLUTION compression))

(defn pdf-to-image-output
  "Converts 1. page from PDF to BufferedImage scaling and cropping to predefined resolution and writes it to given OutputStream"
  [pdf-input out]
  (let [document (PDDocument/load pdf-input)]
    (try
      (output-image (->> (.. document getDocumentCatalog getAllPages iterator next convertToImage) scale-image) out 0.5)
      true
      (catch Exception e (println e))
      (finally (.close document)))))

(defn pdf-to-image-input-stream
  "Converts 1. page from PDF to scaled and cropped predefined resolution jpg InputStream"
  [pdf-input]
  (let [content (ByteArrayOutputStream.)]
    (pdf-to-image-output pdf-input content)
    (ByteArrayInputStream. (.toByteArray content))))

;;(io/copy (pdf-to-image-input-stream "/home/michaelho/ws/lupapalvelu/problematic-pdfs/Yhdistelmakartta_asema-johto_Oksapolku-Pihkakatu.pdf") (FileOutputStream. "/tmp/a1.jpg"))