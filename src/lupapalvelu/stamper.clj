(ns lupapalvelu.stamper
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [slingshot.slingshot :refer [throw+]]
            [sade.strings :as ss]
            [lupapalvelu.mime :as mime])
  (:import [java.io InputStream OutputStream]
           [java.awt Graphics2D Color Image BasicStroke Font AlphaComposite RenderingHints]
           [java.awt.geom RoundRectangle2D$Float]
           [java.awt.font FontRenderContext TextLayout]
           [java.awt.image BufferedImage RenderedImage]
           [javax.imageio ImageIO]
           [com.lowagie.text.pdf PdfGState PdfStamper PdfReader]
           [com.google.zxing BarcodeFormat]
           [com.google.zxing.qrcode QRCodeWriter]
           [com.google.zxing.client.j2se MatrixToImageWriter]))

; (set! *warn-on-reflection* true)

;;
;; Generate stamp:
;;

(defn qrcode ^BufferedImage [data size]
  (MatrixToImageWriter/toBufferedImage
    (.encode (QRCodeWriter.) data BarcodeFormat/QR_CODE size size)))

(defn draw-text [g text x y]
  (.draw text g x y))

(defn make-stamp [verdict created username municipality transparency]
  (let [font (Font. "Courier" Font/BOLD 12)
        frc (FontRenderContext. nil RenderingHints/VALUE_TEXT_ANTIALIAS_ON RenderingHints/VALUE_FRACTIONALMETRICS_ON)
        texts (map (fn [text] (TextLayout. text font frc))
                   [(str verdict \space (format "%td.%<tm.%<tY" (java.util.Date. created)))
                    username
                    municipality
                    "LUPAPISTE.fi"])
        text-widths (map (fn [text] (-> text (.getPixelBounds nil 0 0) (.getWidth))) texts)
        width (int (+ (reduce max text-widths) 52))
        height (int (+ 110 45))
        i (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)]
    (doto (.createGraphics i)
      (.setColor (Color. 255 255 255 (- 255 transparency)))
      (.fillRect 0 0 width height)
      (.drawImage (qrcode "http://lupapiste.fi" 70) (- width 70) (int 5) nil)
      (.translate 0 70)
      (.setStroke (BasicStroke. 2.0 BasicStroke/CAP_ROUND BasicStroke/JOIN_ROUND))
      (.setComposite (AlphaComposite/getInstance AlphaComposite/SRC))
      (.setColor (Color. 0 0 0 255))
      (.drawRoundRect 10 10 (- width 20) 70 20 20)
      (.setComposite (AlphaComposite/getInstance AlphaComposite/SRC_OVER))
      (.setFont font)
      (draw-text (nth texts 0) 22 27)
      (draw-text (nth texts 1) 22 43)
      (draw-text (nth texts 2) 22 55)
      (draw-text (nth texts 3) (int (/ (- width (nth text-widths 3)) 2)) 70)
      (.dispose))
    i))

;;
;; Stamp with provided image:
;;

(declare stamp-pdf stamp-image)

(defn stamp [stamp content-type in out x-margin y-margin transparency]
  (cond
    (= content-type "application/pdf")      (do (stamp-pdf stamp in out x-margin y-margin transparency) nil)
    (ss/starts-with content-type "image/")  (do (stamp-image stamp content-type in out x-margin y-margin transparency) nil)
    :else                                   nil))

;;
;; Stamp PDF:
;;

; iText uses points as units, 1 mm is 2.835 points
(defn- mm->u [mm] (* 2.835 mm))

(defn- transparency->opacity [transparency]
  (- 1.0 (/ (double transparency) 255.0)))

(defn- get-sides [itext-bean]
  (select-keys (bean itext-bean) [:top :right :bottom :left]))

(defn- stamp-pdf
  "About calculating stamp location, see http://support.itextpdf.com/node/106"
  [^Image stamp-image ^InputStream in ^OutputStream out x-margin y-margin transparency]
  (with-open [reader (PdfReader. in)
              stamper (PdfStamper. reader out)]
    (let [stamp (com.lowagie.text.Image/getInstance stamp-image nil false)
          stamp-width (.getPlainWidth stamp)
          stamp-height (.getPlainHeight stamp)
          opacity (transparency->opacity transparency)
          gstate (doto (PdfGState.)
                   (.setFillOpacity opacity)
                   (.setStrokeOpacity opacity))]
      (doseq [page (range (.getNumberOfPages reader))]
        (let [visible-area (.getCropBox reader (inc page))
              rotation (.getPageRotation reader (inc page))
              rotate? (pos? (mod rotation 180))
              sides   (get-sides visible-area)
              _ (debug "Rotation:" rotation)
              _ (debug "page-size with rotation:" (get-sides (.getPageSizeWithRotation reader (inc page))))
              _ (debug "visible-area without rotation:" sides)
              max-x (if rotate? (:top sides) (:right sides))
              min-y (if rotate? (:left sides) (:bottom sides))
              x (- max-x stamp-width (mm->u x-margin))
              y (+ min-y (mm->u y-margin))
              _ (debug "Stamp location" x y)]
          (doto (.getOverContent stamper (inc page))
            (.saveState)
            (.setGState gstate)
            (.addImage stamp stamp-width 0 0 stamp-height x y)
            (.restoreState)))))))

;;
;; Stamp raster image:
;;

(declare read-image write-image add-stamp mm->p)

(defn- stamp-image [stamp-image content-type in out x-margin y-margin transparency]
  (doto (read-image in)
    (add-stamp stamp-image x-margin y-margin)
    (write-image (second (s/split content-type #"/")) out)))

(defn- read-image ^BufferedImage [^InputStream in]
  (or (ImageIO/read in) (throw+ "read: unsupported image type")))

(defn- write-image [^RenderedImage image ^String image-type ^OutputStream out]
  (when-not (ImageIO/write image image-type out)
    (throw+ "write: can't write stamped image")))

(defn- add-stamp [^BufferedImage image ^Image stamp x-margin y-margin]
  (let [x (- (.getWidth image nil) (.getWidth stamp nil) (mm->p x-margin))
        y (- (.getHeight image nil) (.getHeight stamp nil) (mm->p y-margin))]
    (doto (.createGraphics image)
      (.drawImage stamp x y nil)
      (.dispose))))

; Images are (usually?) printed in 72 DPI. That means 1 mm is 2.835 points.
(defn- mm->p [mm] (int (* 2.835 mm)))

;;
;; Swing frame for testing stamp:
;;

(comment

  (defn- paint-component [g w h]
    (let [i (make-stamp "hyv\u00E4ksytty" (System/currentTimeMillis) "Veikko Viranomainen" "SIPOO" 255)
          iw (.getWidth i)
          ih (.getHeight i)]
      (.setColor g Color/GRAY)
      (.fillRect g 0 0 w h)
      (.drawImage g i (int (/ (- w iw) 2)) (int (/ (- h ih) 2)) nil)))

  (defn make-frame []
    (let [watch-these [#'make-stamp #'paint-component]
          frame (javax.swing.JFrame. "heelo")]
      (doto frame
        (.setContentPane
          (doto (proxy [javax.swing.JComponent] []
                  (paint [g]
                    (paint-component g (.getWidth this) (.getHeight this))))))
        (.addWindowListener
          (proxy [java.awt.event.WindowAdapter] []
            (windowOpened [_]
              (doseq [v watch-these]
                (add-watch v :repaint (fn [_ _ _ _] (javax.swing.SwingUtilities/invokeLater (fn [] (.repaint frame)))))))
            (windowClosing [_]
              (doseq [v watch-these]
                (remove-watch v :repaint)))))
        (.setMinimumSize (java.awt.Dimension. 100 100))
        (.setDefaultCloseOperation javax.swing.JFrame/DISPOSE_ON_CLOSE)
        (.setAlwaysOnTop true)
        (.pack)
        (.setLocationRelativeTo nil)
        (.setVisible true))
      frame)))
