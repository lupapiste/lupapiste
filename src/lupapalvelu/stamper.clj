(ns lupapalvelu.stamper
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error fatal]]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [slingshot.slingshot :refer [throw+]]
            [me.raynes.fs :as fs]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.core :refer [now]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mime :as mime])
  (:import [java.io InputStream OutputStream]
           [java.awt Graphics2D Color Image BasicStroke Font AlphaComposite RenderingHints]
           [java.awt.geom RoundRectangle2D$Float]
           [java.awt.font FontRenderContext TextLayout]
           [java.awt.image BufferedImage RenderedImage]
           [javax.imageio ImageIO]
           [com.lowagie.text Rectangle]
           [com.lowagie.text.exceptions InvalidPdfException]
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

(defn make-stamp [^String verdict ^long created ^String municipality ^Integer transparency]
  (let [font (Font. "Courier" Font/BOLD 12)
        frc (FontRenderContext. nil RenderingHints/VALUE_TEXT_ANTIALIAS_ON RenderingHints/VALUE_FRACTIONALMETRICS_ON)
        texts (map (fn [text] (TextLayout. ^String text font frc))
                   [(str verdict \space (format "%td.%<tm.%<tY" (java.util.Date. created)))
                    municipality
                    "LUPAPISTE.fi"])
        text-widths (map (fn [text] (-> text (.getPixelBounds nil 0 0) (.getWidth))) texts)
        width (int (+ (reduce max text-widths) 52))
        height (int (+ 110 45))
        i (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)]
    (doto (.createGraphics i)
      (.setColor (Color. 255 255 255 (- 255 transparency)))
      (.fillRect 0 0 width height)
      (.drawImage (qrcode (env/value :host) 70) (- width 70) (int 5) nil)
      (.translate 0 70)
      (.setStroke (BasicStroke. 2.0 BasicStroke/CAP_ROUND BasicStroke/JOIN_ROUND))
      (.setComposite (AlphaComposite/getInstance AlphaComposite/SRC))
      (.setColor (Color. 0 0 0 255))
      (.drawRoundRect 10 10 (- width 20) 70 20 20)
      (.setComposite (AlphaComposite/getInstance AlphaComposite/SRC_OVER))
      (.setFont font)
      (draw-text (nth texts 0) 22 27)
      (draw-text (nth texts 1) 22 48)
      (draw-text (nth texts 2) (int (/ (- width (nth text-widths 2)) 2)) 70)
      (.dispose))
    i))

;;
;; Stamp with provided image:
;;

(declare stamp-pdf stamp-image)

(defn- stamp-stream [stamp content-type in out x-margin y-margin transparency]
  (cond
    (= content-type "application/pdf")      (do (stamp-pdf stamp in out x-margin y-margin transparency) nil)
    (ss/starts-with content-type "image/")  (do (stamp-image stamp content-type in out x-margin y-margin transparency) nil)
    :else                                   nil))

(defn- create-pdftk-file [in file-name]
  (let [result (shell/sh "pdftk" "-" "output" file-name :in in)]
    ; POSIX return code 0 signals success, others are failure codes
    (when-not (zero? (:exit result))
      (throw (RuntimeException. (str "pdftk returned " (:exit result) ", STDOUT: " (str (:out result) ", STDERR: " (:err result))))))))

(defn- retry-stamping [stamp-graphic file-id out x-margin y-margin transparency]
  (let [tmp-file-name (str (System/getProperty "java.io.tmpdir") file-id "-" (now) ".pdf")]
    (debugf "Redownloading file %s from DB and running `pdftk - output %s`" file-id tmp-file-name)
    (try
      (with-open [in ((:content (mongo/download file-id)))]
        (create-pdftk-file in tmp-file-name))
      (with-open [in (io/input-stream tmp-file-name)]
        (stamp-stream stamp-graphic "application/pdf" in out x-margin y-margin transparency))
      (finally
        (fs/delete tmp-file-name)
        nil))))

(defn stamp [stamp file-id out x-margin y-margin transparency]
  (try
    (let [{content-type :content-type :as attachment} (mongo/download file-id)]
      (with-open [in ((:content attachment))]
        (stamp-stream stamp content-type in out x-margin y-margin transparency)))
    (catch InvalidPdfException e
      (info (str "Retry stamping because: " (.getMessage e)))
      (retry-stamping stamp file-id out x-margin y-margin transparency))))

;;
;; Stamp PDF:
;;

; iText uses points as units, 1 mm is 2.835 points
(defn- mm->u [mm] (* 2.835 mm))

(defn- transparency->opacity [transparency]
  (- 1.0 (/ (double transparency) 255.0)))

(defn- get-sides [itext-bean]
  (if (map? itext-bean)
    itext-bean
    (select-keys (bean itext-bean) [:top :right :bottom :left :width :height])))

(defn- make-rectangle [{:keys [left bottom right top]}]
  ; (Rectangle. llx lly urx ury)
  (Rectangle. ^Float left ^Float bottom ^Float right ^Float top))

(defn- rotate-rectangle [r rotation]
  (let [^Rectangle rectangle (if (map? r) (make-rectangle r) r)]
    (if (pos? rotation)
     (rotate-rectangle (.rotate rectangle) (- rotation 90))
     rectangle)))

(defn- calculate-x-y
  "About calculating stamp location, see http://support.itextpdf.com/node/106"
  [page-box crop-box page-rotation stamp-width x-margin y-margin]
  (let [visible-area (rotate-rectangle crop-box page-rotation)
        sides (get-sides visible-area)
        page-size (get-sides (rotate-rectangle page-box page-rotation))
        rotate? (pos? (mod page-rotation 180))
        ; If the visible area does not fit into page, we must crop
        max-x (if (or (< (:width page-size) (+ (:right sides) (:left sides)))
                    (and (not (zero? (:bottom (get-sides page-box)))) (= page-rotation 270))
                    )
               (- (:right sides) (:left sides))
               (:right sides))
        min-y (:bottom sides)
        x (- max-x stamp-width (mm->u x-margin))
        y (+ min-y (mm->u y-margin))]
    (debugf "Rotation %s, visible-area with rotation: %s, page with rotation %s,  max-x/min-y: %s/%s, stamp location x/y: %s/%s"
      page-rotation sides page-size max-x min-y x y)
    [x y]))

(defn- stamp-pdf [^Image stamp-image ^InputStream in ^OutputStream out x-margin y-margin transparency]
  (with-open [reader (PdfReader. in)
              stamper (PdfStamper. reader out)]
    (let [stamp (com.lowagie.text.Image/getInstance stamp-image nil false)
          stamp-width (.getPlainWidth stamp)
          stamp-height (.getPlainHeight stamp)
          opacity (transparency->opacity transparency)
          gstate (doto (PdfGState.)
                   (.setFillOpacity opacity)
                   (.setStrokeOpacity opacity))]
      (doseq [i (range (.getNumberOfPages reader))]
        (let [page (inc i)
              page-box (.getPageSize reader page)
              crop-box (.getCropBox reader page)
              rotation (.getPageRotation reader page)
              [x y] (calculate-x-y page-box crop-box rotation stamp-width x-margin y-margin)]
          (doto (.getOverContent stamper page)
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
      frame))

  ; Run this in REPL and check that every new file has been stamped
  (let [d "problematic-pdfs"
        my-stamp (make-stamp "OK" 0 "Solita Oy" 0)]
    (doseq [f (remove #(.endsWith % "-leima.pdf") (fs/list-dir d))]
      (println f)
      (with-open [my-in  (io/input-stream (str d "/" f))
                  my-out (io/output-stream (str d "/" f "-leima.pdf"))]
        (try
          (stamp-pdf my-stamp my-in my-out 10 100 0)
          (catch Throwable t (error t))))))

  )
