(ns lupapalvelu.stamper
  (:use [clojure.tools.logging]
        [slingshot.slingshot :only [throw+]])
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [sade.strings :as ss]
            [lupapalvelu.mime :as mime])
  (:import [java.io InputStream OutputStream]
           [java.awt Color Image]
           [java.awt.image BufferedImage RenderedImage]
           [javax.imageio ImageIO]
           [com.lowagie.text Document Element Paragraph Phrase Rectangle]
           [com.lowagie.text.pdf BaseFont PdfContentByte PdfGState PdfPageEventHelper PdfTemplate PdfStamper PdfWriter PdfReader]
           [com.lowagie.text.pdf.draw DottedLineSeparator LineSeparator]))

(set! *warn-on-reflection* true)

;;
;; Generate stamp:
;;

(defn make-stamp []
  (with-open [in (io/input-stream (io/resource "public/img/logo.png"))]
    (ImageIO/read in)))

(declare stamp-pdf stamp-image)

;;
;; Stamp with provided image:
;;

(defn stamp [stamp-image content-type in out]
  (cond
    (= content-type "application/pdf")      (do (stamp-pdf stamp-image in out) nil)
    (ss/starts-with content-type "image/")  (do (stamp-image stamp-image content-type in out) nil)
    :else                                   nil))

;;
;; Stamp PDF:
;;

(defn- stamp-pdf [^Image stamp-image ^InputStream in ^OutputStream out]
  (with-open [reader (PdfReader. in)
              stamper (PdfStamper. reader out)]
    (let [stamp (doto (com.lowagie.text.Image/getInstance stamp-image nil false)
                  (.setAbsolutePosition 5.0 5.0))
          gstate (doto (PdfGState.)
                   (.setFillOpacity 0.25)
                   (.setStrokeOpacity 0.25))]
      (doseq [page (range (.getNumberOfPages reader))]
        (doto (.getOverContent stamper (inc page))
          (.saveState)
          (.setGState gstate)
          (.addImage stamp)
          (.restoreState))))))

;;
;; Stamp raster image:
;;

(declare read-image write-image add-stamp)

(defn- stamp-image [stamp-image content-type in out]
  (doto (read-image in)
    (add-stamp stamp-image)
    (write-image (second (s/split content-type #"/")) out)))

(defn- read-image ^BufferedImage [^InputStream in]
  (or (ImageIO/read in) (throw+ "read: unsupported image type")))

(defn- write-image [^RenderedImage image ^String image-type ^OutputStream out]
  (when-not (ImageIO/write image image-type out)
    (throw+ "write: can't write stamped image")))

(defn- add-stamp [^BufferedImage image ^Image stamp]
  (.drawImage (.createGraphics image) stamp 5 (- (.getHeight image nil) (.getHeight stamp nil) 5) nil))
