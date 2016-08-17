(ns lupapalvelu.attachment.pdf-wrapper
  (:require [clojure.java.io :as io])
  (:import [org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream]
           [org.apache.pdfbox.pdmodel.graphics.image LosslessFactory]
           [java.io File ByteArrayOutputStream]
           [org.apache.pdfbox.pdmodel.common PDRectangle PDMetadata]
           [javax.imageio.stream FileImageInputStream]
           [javax.imageio ImageIO]
           [com.twelvemonkeys.imageio.plugins.jpeg JPEGImageReader]
           [org.apache.xmpbox XMPMetadata]
           [org.apache.xmpbox.xml XmpSerializer]
           [org.apache.pdfbox.pdmodel.graphics.color PDOutputIntent]))

(def color-profile "sRGB Color Space Profile.icm")
(def color-space "sRGB IEC61966-2.1")
(def color-registry "http://www.color.org")

(defn- read-images [reader index images]
  (try
    (let [image (.read reader index)]
      (->> {:content image
            :width (.getWidth reader index)
            :length (.getHeight reader index)
            }
           (conj images)
           (read-images reader (inc index))))
    (catch IndexOutOfBoundsException _
      images)))

(defn- read-jpeg [^File file]
  (let [readers (ImageIO/getImageReadersByFormatName "jpeg")
        ^JPEGImageReader reader (.next readers)]
    (.setInput reader (FileImageInputStream. file) false false)
    (read-images reader 0 [])))

(defn- picture-to-pdf [doc jpeg-file]
  (doseq [{:keys [width length content]} (read-jpeg jpeg-file)]
    (let [intended-width (float width)
          intended-length (float length)
          page (PDPage. (PDRectangle. intended-width intended-length))]
      (with-open [contents (PDPageContentStream. doc page)]
        (.addPage doc page)
        (.drawImage contents (LosslessFactory/createFromImage doc content) 0.0 0.0 intended-width intended-length)))))

(defn- metadata-to-pdf [doc pdf-title]
  (with-open [xmp-os (ByteArrayOutputStream.)
              color-is (io/input-stream (io/resource color-profile))]

    ;; Add the metadata and output intent to make the file PDF/A-1b compliant
    ;; Note that we don't copy any existing XMP data (if any) from the TIFF file here
    (let [xmp (XMPMetadata/createXMPMetadata)
          dc (.createAndAddDublinCoreSchema xmp)
          id (.createAndAddPFAIdentificationSchema xmp)
          serializer (XmpSerializer.)
          metadata (PDMetadata. doc)]
      (.setTitle dc pdf-title)
      (doto id
        (.setPart (int 1))
        (.setConformance "B"))
      (.serialize serializer xmp xmp-os true)
      (.importXMPMetadata metadata (.toByteArray xmp-os))
      (let [intent (doto (PDOutputIntent. doc color-is)
                     (.setInfo color-space)
                     (.setOutputCondition color-space)
                     (.setOutputConditionIdentifier color-space)
                     (.setRegistryName color-registry))]
        (doto (.getDocumentCatalog doc)
          (.setMetadata metadata)
          (.addOutputIntent intent))))))

(defn wrap! [^File jpeg-file ^File output-file pdf-title]
  (with-open [doc (PDDocument.)]
    (picture-to-pdf doc jpeg-file)
    (metadata-to-pdf doc pdf-title)
    (.save doc output-file)))
