(ns lupapalvelu.attachment.pdf-wrapper
  (:require [clojure.java.io :as io])
  (:import [org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream]
           [org.apache.pdfbox.pdmodel.graphics.image JPEGFactory LosslessFactory PDImageXObject CCITTFactory]
           [java.io File ByteArrayOutputStream]
           [org.apache.pdfbox.pdmodel.common PDRectangle PDMetadata]
           [javax.imageio.stream FileImageInputStream]
           [javax.imageio ImageIO ImageReader]
           [org.apache.xmpbox XMPMetadata]
           [org.apache.xmpbox.xml XmpSerializer]
           [org.apache.pdfbox.pdmodel.graphics.color PDOutputIntent]
           [javax.imageio.metadata IIOMetadataFormatImpl IIOMetadataNode]
           [com.github.jaiimageio.impl.plugins.tiff TIFFImageMetadata]
           [java.awt.image BufferedImage]))

(def color-profile "sRGB Color Space Profile.icm")
(def color-space "sRGB IEC61966-2.1")
(def color-registry "http://www.color.org")
(def pdf-ppi 72)

(def width-field 256)
(def length-field 257)
(def x-resolution 282)
(def y-resolution 283)

(defn- tag-value [ifd tag]
  (-> (.getTIFFField ifd tag) (.getAsInt 0)))

(defn- tiff->pd-image-x-object [^PDDocument doc ^BufferedImage image]
  (if (and (= BufferedImage/TYPE_BYTE_BINARY (.getType image)) (= (-> image .getColorModel .getPixelSize) 1))
    ; Only B&W images can be compressed
    (CCITTFactory/createFromImage doc image)
    (LosslessFactory/createFromImage doc image)))

(defn- read-tiff-images
  ([reader doc]
    (read-tiff-images reader doc 0 []))
  ([reader doc index images]
   (try
     (let [image (.read reader index)
           ^TIFFImageMetadata metadata (.getImageMetadata reader index)
           ifd (.getRootIFD metadata)]
       (->> {:pd-image-x-object (tiff->pd-image-x-object doc image)
             :width (tag-value ifd width-field)
             :length (tag-value ifd length-field)
             :width-ppi (tag-value ifd x-resolution)
             :length-ppi (tag-value ifd y-resolution)}
            (conj images)
            (read-tiff-images reader doc (inc index))))
     (catch IndexOutOfBoundsException _
       images))))

(defn- pixels-per-inch [^IIOMetadataNode dimension element-name]
  (let [pixel-sizes (.getElementsByTagName dimension element-name)
        pixel-size (when (pos? (.getLength pixel-sizes))
                     (-> (.item pixel-sizes 0)
                         (.getAttribute "value")))]
    ; The standard metadata attribute has height/width of a pixel in millimeters
    (when pixel-size
      (->> (Double/parseDouble pixel-size)
           (/ 25.4)))))

(defn- read-jpeg-image [^ImageReader reader ^File jpeg-file ^PDDocument doc]
  (with-open [is (io/input-stream jpeg-file)]
    (let [pd-image-x-object (JPEGFactory/createFromStream doc is)
          width (.getWidth pd-image-x-object)
          height (.getHeight pd-image-x-object)
          metadata (.getImageMetadata reader 0)
          tree ^IIOMetadataNode (.getAsTree metadata IIOMetadataFormatImpl/standardMetadataFormatName)
          dimension ^IIOMetadataNode (-> tree (.getElementsByTagName "Dimension") (.item 0))]
      [{:width-ppi (or (pixels-per-inch dimension "HorizontalPixelSize") 72.0)
        :length-ppi (or (pixels-per-inch dimension "VerticalPixelSize") 72.0)
        :width width
        :length height
        :pd-image-x-object pd-image-x-object}])))

(defn- read-png-image [^ImageReader reader ^PDDocument doc]
  (let [image (.read reader 0)
        pd-image-x-object (LosslessFactory/createFromImage doc image)
        width (.getWidth pd-image-x-object)
        height (.getHeight pd-image-x-object)
        metadata (.getImageMetadata reader 0)
        tree ^IIOMetadataNode (.getAsTree metadata IIOMetadataFormatImpl/standardMetadataFormatName)
        dimension ^IIOMetadataNode (-> tree (.getElementsByTagName "Dimension") (.item 0))]
    [{:width-ppi (or (pixels-per-inch dimension "HorizontalPixelSize") 72.0)
      :length-ppi (or (pixels-per-inch dimension "VerticalPixelSize") 72.0)
      :width width
      :length height
      :pd-image-x-object pd-image-x-object}]))

(defn- read-images [^File file image-format ^PDDocument doc]
  {:post [(sequential? %)]}
  (with-open [in (FileImageInputStream. file)]
    (let [readers (ImageIO/getImageReadersByFormatName (name image-format))
          ^ImageReader reader (.next readers)]
      (.setInput reader in false false)
      (case image-format
        :jpeg (read-jpeg-image reader file doc)
        :tiff (doall (read-tiff-images reader doc))
        :png (read-png-image reader doc)))))

(defn- set-metadata-to-pdf! [^PDDocument doc pdf-title]
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

(defn wrap! [image-format ^File image-file ^File output-file pdf-title]
  {:pre [(keyword? image-format) (some? image-file) (some? output-file)]}

  (with-open [doc (PDDocument.)]
    (doseq [{:keys [width length width-ppi length-ppi ^PDImageXObject pd-image-x-object]} (read-images image-file image-format doc)]
      (let [intended-width (float (* pdf-ppi (/ width width-ppi)))
            intended-length (float (* pdf-ppi (/ length length-ppi)))
            page (PDPage. (PDRectangle. intended-width intended-length))]
        (with-open [contents (PDPageContentStream. doc page)]
          (.addPage doc page)
          (.drawImage contents pd-image-x-object 0.0 0.0 intended-width intended-length))))

    (set-metadata-to-pdf! doc pdf-title)
    (.save doc output-file)))
