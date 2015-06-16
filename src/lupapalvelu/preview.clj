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

(defn scale-image [image] (let [scale (min (/ 600.0 (.getWidth image)) (/ 600.0 (.getHeight image)))
                                width (* scale (.getWidth image))
                                height (* scale (.getHeight image))
                                scaled-image (BufferedImage. width height BufferedImage/TYPE_INT_RGB)]
                            (doto (.createGraphics scaled-image)
                              (.setRenderingHint RenderingHints/KEY_INTERPOLATION, RenderingHints/VALUE_INTERPOLATION_BICUBIC)
                              (.setRenderingHint RenderingHints/KEY_RENDERING, RenderingHints/VALUE_RENDER_QUALITY)
                              (.setRenderingHint RenderingHints/KEY_ANTIALIASING, RenderingHints/VALUE_ANTIALIAS_ON)
                              (.drawImage image, 0, 0, width, height, nil)
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