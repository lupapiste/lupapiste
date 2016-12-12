(ns lupapalvelu.attachment.pdf-wrapper
  (:require [clojure.java.io :as io])
  (:import [org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream]
           [org.apache.pdfbox.pdmodel.graphics.image JPEGFactory]
           [java.io File ByteArrayOutputStream]
           [org.apache.pdfbox.pdmodel.common PDRectangle PDMetadata]
           [javax.imageio.stream FileImageInputStream]
           [javax.imageio ImageIO ImageReader]
           [org.apache.xmpbox XMPMetadata]
           [org.apache.xmpbox.xml XmpSerializer]
           [org.apache.pdfbox.pdmodel.graphics.color PDOutputIntent]
           [javax.imageio.metadata IIOMetadataFormatImpl IIOMetadataNode]))

(def color-profile "sRGB Color Space Profile.icm")
(def color-space "sRGB IEC61966-2.1")
(def color-registry "http://www.color.org")
(def pdf-ppi 72)

(defn- pixels-per-inch [^IIOMetadataNode dimension element-name]
  (let [pixel-sizes (.getElementsByTagName dimension element-name)
        pixel-size (when (pos? (.getLength pixel-sizes))
                     (-> (.item pixel-sizes 0)
                         (.getAttribute "value")))]
    ; The standard metadata attribute has height/width of a pixel in millimeters
    (when pixel-size
      (->> (Double/parseDouble pixel-size)
           (/ 25.4)))))

(defn- read-image-ppi [^File file]
  (with-open [in (FileImageInputStream. file)]
    (let [readers (ImageIO/getImageReadersByFormatName "jpeg")
          ^ImageReader reader (.next readers)
          _ (.setInput reader in false false)
          metadata (.getImageMetadata reader 0)
          tree ^IIOMetadataNode (.getAsTree metadata IIOMetadataFormatImpl/standardMetadataFormatName)
          dimension ^IIOMetadataNode (-> tree (.getElementsByTagName "Dimension") (.item 0))]
      {:width-ppi (or (pixels-per-inch dimension "HorizontalPixelSize") 72.0)
       :height-ppi (or (pixels-per-inch dimension "VerticalPixelSize") 72.0)})))

(defn- jpeg-to-pdf [doc jpeg-file]
  (with-open [is (io/input-stream jpeg-file)]
    (let [pd-image-x-object (JPEGFactory/createFromStream doc is)
          width (.getWidth pd-image-x-object)
          height (.getHeight pd-image-x-object)
          {:keys [width-ppi height-ppi]} (read-image-ppi jpeg-file)
          intended-width (float (* pdf-ppi (/ width width-ppi)))
          intended-height (float (* pdf-ppi (/ height height-ppi)))
          page (PDPage. (PDRectangle. intended-width intended-height))]
      (with-open [contents (PDPageContentStream. ^PDDocument doc page)]
        (.addPage doc page)
        (.drawImage contents pd-image-x-object 0.0 0.0 intended-width intended-height)))))

(defn- metadata-to-pdf [^PDDocument doc pdf-title]
  (with-open [xmp-os (ByteArrayOutputStream.)
              color-is (io/input-stream (io/resource color-profile))]
    ;; Add the metadata and output intent to make the file PDF/A-1b compliant
    ;; Note that we don't copy any existing XMP data (if any) from the original file here
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
    (jpeg-to-pdf doc jpeg-file)
    (metadata-to-pdf doc pdf-title)
    (.save doc output-file)))
