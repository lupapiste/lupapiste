(ns lupapalvelu.stamper
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error fatal]]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [slingshot.slingshot :refer [throw+]]
            [me.raynes.fs :as fs]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :refer :all]
            [lupapalvelu.pdftk :as pdftk]
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

(def file-types (conj (distinct (for [i (iterator-seq (.getServiceProviders
                                                 (javax.imageio.spi.IIORegistry/getDefaultInstance)
                                                 javax.imageio.spi.ImageReaderSpi false))]
                           (re-find #"\b\w+(?=\.\w+@)" (str i))))
                  "pdf"))
;;
;; Generate stamp:
;;

(defn qrcode ^java.awt.image.BufferedImage [data size]
  (MatrixToImageWriter/toBufferedImage
    (.encode (QRCodeWriter.) data BarcodeFormat/QR_CODE size size)))

(defn draw-text [g text x y]
  (.draw text g x y))

(defn make-stamp
  [^String verdict ^Long created ^Integer transparency info-fields]
  (let [fields (conj
                 (vec
                   (conj info-fields (str verdict \space (format "%td.%<tm.%<tY" (java.util.Date. created)))))
                 "LUPAPISTE.fi")
        font (Font. "Courier" Font/BOLD 12)
        frc (FontRenderContext. nil RenderingHints/VALUE_TEXT_ANTIALIAS_ON RenderingHints/VALUE_FRACTIONALMETRICS_ON)
        texts (remove nil?
                (map
                  (fn [text]
                    (if (seq text)
                      (TextLayout. ^String text font frc)))
                  fields))
        text-widths (map (fn [text] (-> text (.getPixelBounds nil 0 0) (.getWidth))) texts)
        line-height 22
        rect-height (+ (* (count texts) line-height) 10)
        qr-size 70
        width (int (+ (reduce max text-widths) 52))
        height (int (+ qr-size rect-height 5))
        image (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)
        graphics (.createGraphics image)]
    (doto graphics
      (.setColor (Color. 255 255 255 (- 255 transparency)))
      (.fillRect 0 0 width height)
      (.drawImage (qrcode (env/value :host) qr-size) (- width qr-size) (int 0) nil)
      (.translate 0 qr-size)
      (.setStroke (BasicStroke. 2.0 BasicStroke/CAP_ROUND BasicStroke/JOIN_ROUND))
      (.setComposite (AlphaComposite/getInstance AlphaComposite/SRC))
      (.setColor (Color. 0 0 0 255))
      (.drawRoundRect 10 2 (- width 20) rect-height 20 20)
      (.setComposite (AlphaComposite/getInstance AlphaComposite/SRC_OVER))
      (.setFont font))

    (doseq [[i text] (util/indexed texts)]
       (draw-text graphics text (int (/ (- width (nth text-widths i)) 2)) (+ (* (inc i) line-height) 3)))

    (.dispose graphics)
    image))

;;
;; Stamp with provided image:
;;

(declare stamp-pdf stamp-image)

(defn- stamp-stream [stamp content-type in out options]
  (cond
    (= content-type "application/pdf")      (do (stamp-pdf stamp in out options) nil)
    (ss/starts-with content-type "image/")  (do (stamp-image stamp content-type in out options) nil)
    :else                                   nil))

(def- tmp (str (System/getProperty "java.io.tmpdir") env/file-separator))

(defn- retry-stamping [stamp-graphic file-id out options]
  (let [tmp-file-name (str tmp file-id "-" (now) ".pdf")]
    (debugf "Redownloading file %s from DB and running `pdftk - output %s`" file-id tmp-file-name)
    (try
      (with-open [in ((:content (mongo/download file-id)))]
        (pdftk/create-pdftk-file in tmp-file-name))
      (with-open [in (io/input-stream tmp-file-name)]
        (stamp-stream stamp-graphic "application/pdf" in out options))
      (finally
        (fs/delete tmp-file-name)
        nil))))

(defn stamp [stamp file-id out {:keys [x-margin y-margin transparency] :as options}]
  {:pre [(number? x-margin) (number? y-margin) (number? transparency)]}
  (try
    (let [{content-type :contentType :as attachment} (mongo/download file-id)]
      (with-open [in ((:content attachment))]
        (stamp-stream stamp content-type in out options)))
    (catch InvalidPdfException e
      (info (str "Retry stamping because: " (.getMessage e)))
      (retry-stamping stamp file-id out options))))

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
        ; If the visible area does not fit into page, we must crop
        max-x (if (or
                    ; This is possibly better condition than the one below, as it does not trust that page's left side is at 0 position.
                    ; Though, with it some 'problematic pdf' tests in stamper_test.clj will not pass.
                    ;(< (+ (:left page-size) (:width page-size)) (+ (:left sides) (:width sides)))
                    (and (< (:width page-size) (+ (:right sides) (:left sides))) (not= page-rotation 0))
                    (and (not (zero? (:bottom (get-sides page-box)))) (= page-rotation 270)))
                (- (:right sides) (:left sides))
                (:right sides))
        min-y (cond
                (and (zero? (:bottom (get-sides crop-box))) (zero? (:bottom (get-sides page-box)))) 0.0
                (and (= 180 page-rotation) (neg? (:bottom sides))) 0.0 ; LP-6213
                :else (:bottom sides))
        x (- max-x stamp-width (mm->u x-margin))
        y (+ min-y (mm->u y-margin))]
    (debugf "Rotation %s, crop-box with rotation: %s, page-box with rotation: %s,  max-x/min-y: %s/%s, stamp location x/y: %s/%s"
      page-rotation sides page-size max-x min-y x y)
    [x y]))

(defn- stamp-pdf-page
  "Stamp a page. Mutates given Java objects!
   Page numbers start from 1."
  [page-number ^com.lowagie.text.Image stamp {:keys [x-margin y-margin transparency]} ^PdfReader reader ^PdfStamper stamper]
  {:pre [(pos? page-number)]}
  (let [stamp-width (.getPlainWidth stamp)
        stamp-height (.getPlainHeight stamp)
        opacity (transparency->opacity transparency)
        gstate (doto (PdfGState.)
                 (.setFillOpacity opacity)
                 (.setStrokeOpacity opacity))
        page-box (.getPageSize reader page-number)
        crop-box (.getCropBox reader page-number)
        rotation (.getPageRotation reader page-number)
        [x y] (calculate-x-y page-box crop-box rotation stamp-width x-margin y-margin)]
    (doto (.getOverContent stamper page-number)
      (.saveState)
      (.setGState gstate)
      (.addImage stamp stamp-width 0 0 stamp-height x y)
      (.restoreState))))

(defn- stamp-pdf [^Image stamp-image ^InputStream in ^OutputStream out options]
  (with-open [reader (PdfReader. in)
              stamper (PdfStamper. reader out)]
    (let [stamp (com.lowagie.text.Image/getInstance stamp-image nil false)]
      (case (:page options)
        :all   (doseq [i (range (.getNumberOfPages reader))]
                 (stamp-pdf-page (inc i) stamp options reader stamper))
        :last  (stamp-pdf-page (.getNumberOfPages reader) stamp options reader stamper)
        :first (stamp-pdf-page 1 stamp options reader stamper)))))

;;
;; Stamp raster image:
;;

(declare read-image write-image add-stamp mm->p)

(defn- stamp-image [stamp-image content-type in out {:keys [x-margin y-margin]}]
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
    (let [i (make-stamp "hyv\u00E4ksytty" (System/currentTimeMillis) 128 ["SIPOO" "" "Rakennustunnus" "Kuntalupatunnus" "Pykala"])
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
  (let [d "checkouts/lupapiste-aux/problematic-pdfs"
        my-stamp (make-stamp "OK" 0 0 ["Solita Oy"])]
    (doseq [f (remove #(.endsWith (.getName %) "-leima.pdf") (fs/list-dir d))]
      (println f)
      (with-open [my-in  (io/input-stream (str f))
                  my-out (io/output-stream (str f "-leima.pdf"))]
        (try
          (stamp-pdf my-stamp my-in my-out {:x-margin 10 :y-margin 100 :transparency 0 :page :first})
          (catch Throwable t (error t))))))

  ; This REPL-snippet can be used to read metadata from a given pdf. Data is written to target/pdf-metadata-output.txt
  (let [fpath       "checkouts/lupapiste-aux/problematic-pdfs/pohjapiirros.pdf"
        output-path "target/pdf-metadata-output.txt"]
    (with-open [in (io/input-stream fpath)
                reader (PdfReader. in)
                out (io/output-stream output-path)]
      (.write out (.getMetadata reader))
      ;(prn (.getInfo reader)) ; Sometimes PDF-metadata can be stored in the info dictionary
      ))

  )
