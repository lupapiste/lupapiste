(ns lupapalvelu.preview
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
    (println "pdf rez: " original-width "x" original-height " crop: " crop-x "x" crop-y " scale by " scale)
    (doto (.createGraphics new-image)
      (.setRenderingHint RenderingHints/KEY_INTERPOLATION, RenderingHints/VALUE_INTERPOLATION_BICUBIC)
      (.setRenderingHint RenderingHints/KEY_RENDERING, RenderingHints/VALUE_RENDER_QUALITY)
      (.setRenderingHint RenderingHints/KEY_ANTIALIASING, RenderingHints/VALUE_ANTIALIAS_ON)
      (.drawImage image, 0, 0, width, height, crop-x, crop-y, original-width, original-height, nil)
      (.dispose))
    new-image))

(defn scale-image-to-output
  "Converts BufferedImage scaling and cropping in to predefined resolution and writes it to given OutputStream"
  [image]
  (let [scaled-image (scale-image image)
        output (ByteArrayOutputStream.)]
    (ImageIOUtil/writeImage scaled-image "jpg" output ImageIOUtil/DEFAULT_SCREEN_RESOLUTION 0.5)
    (ByteArrayInputStream. (.toByteArray output))))

(defn pdf-to-image-input-stream
  "Converts 1. page from PDF to scaled and cropped jpf preview InputStream"
  [pdf-input]
  (let [document (PDDocument/load pdf-input)]
    (try
      (let [image (.. document getDocumentCatalog getAllPages iterator next convertToImage)]
        (scale-image-to-output image))
      (catch Exception e (println e))
      (finally (.close document)))))

(defn raster-to-image-input-stream
  "Converts Raster image to scaled and cropped jpg preview InputStream"
  [input]
  (try
    (let [image (ImageIO/read (if (= (type input) java.lang.String ) (FileInputStream. input) input))]
      (scale-image-to-output image))
    (catch Exception e (println e))))
