(ns lupapalvelu.preview
  (:require [clojure.java.io :as io])
  (:import (org.apache.pdfbox.pdmodel PDDocument)
           (org.apache.pdfbox.util PDFImageWriter ImageIOUtil)
           (java.awt.image BufferedImage)
           (java.awt RenderingHints)
           (java.io FileOutputStream ByteArrayOutputStream ByteArrayInputStream)))

(defn pdf-preview-file [filename]
  (try (let [document (PDDocument/load filename)
             pdfWriter (PDFImageWriter.)]
         (.writeImage pdfWriter
                      document
                      "jpg"
                      ""
                      1
                      Integer/MAX_VALUE
                      "preview_"
                      1
                      16
                      ))
       true
       (catch Exception e false)))

(def rez 600.0)
(defn scale-image [image] (let [
                                crop-x (if (< (/ (.getHeight image) (.getWidth image)) 5/7) (- (.getWidth image) (/ (.getHeight image) 5/7) ) 0)
                                crop-y (if (< (/ (.getWidth image) (.getHeight image)) 5/7) (- (.getHeight image) (/ (.getWidth image) 5/7) ) 0)
                                scale (min (/ rez (- (.getWidth image) crop-x)) (/ rez (- (.getHeight image) crop-y)))
                                width (* scale (- (.getWidth image) crop-x))
                                height (* scale (- (.getHeight image) crop-y))
                                scaled-image (BufferedImage. width height BufferedImage/TYPE_INT_RGB)]
                            (println "pdf rez: " (.getWidth image) "x" (.getHeight image) " crop: " crop-x "x" crop-y " scale by " scale)
                            (doto (.createGraphics scaled-image)
                              (.setRenderingHint RenderingHints/KEY_INTERPOLATION, RenderingHints/VALUE_INTERPOLATION_BICUBIC)
                              (.setRenderingHint RenderingHints/KEY_RENDERING, RenderingHints/VALUE_RENDER_QUALITY)
                              (.setRenderingHint RenderingHints/KEY_ANTIALIASING, RenderingHints/VALUE_ANTIALIAS_ON)
                              (.drawImage image, 0, 0, width, height, crop-x, crop-y, (.getWidth image), (.getHeight image), nil)
                              (.dispose))
                            scaled-image))

(defn output-image [image file-output-stream compression] (ImageIOUtil/writeImage image "jpg" file-output-stream ImageIOUtil/DEFAULT_SCREEN_RESOLUTION compression))

(defn pdf-to-image-output [pdf-input out]
  (let [document (PDDocument/load pdf-input)]
    (try
      (output-image (->> (.. document getDocumentCatalog getAllPages iterator next convertToImage) scale-image) out 0.5)
      true
      (catch Exception e (println e))
      (finally (.close document)))))


(defn pdf-to-image-input-stream [pdf-input]
  (let [content (ByteArrayOutputStream.)]
    (pdf-to-image-output pdf-input content)
    (ByteArrayInputStream. (.toByteArray content))))

;;(io/copy (pdf-to-image-input-stream "/home/michaelho/ws/lupapalvelu/problematic-pdfs/Yhdistelmakartta_asema-johto_Oksapolku-Pihkakatu.pdf") (FileOutputStream. "/tmp/a1.jpg"))