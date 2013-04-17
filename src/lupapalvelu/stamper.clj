(ns lupapalvelu.stamper
  (:use [clojure.tools.logging]
        [slingshot.slingshot :only [throw+]])
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [sade.strings :as ss]
            [lupapalvelu.mime :as mime])
  (:import [java.io InputStream OutputStream]
           [java.awt Color Image BasicStroke Font]
           [java.awt.geom RoundRectangle2D$Float]
           [java.awt.image BufferedImage RenderedImage]
           [javax.imageio ImageIO]
           [com.lowagie.text.pdf PdfGState PdfStamper PdfReader]))

; (set! *warn-on-reflection* true)

;;
;; Generate stamp:
;;

(defn- calculate-text-widths [texts font]
  (let [i (BufferedImage. 1 1 BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics i)
        _ (.setFont g font)
        fm (.getFontMetrics g)
        widths (map (fn [text] (.stringWidth fm text)) texts)]
    (.dispose g)
    widths))

(defn make-stamp [verdict created username municipality]
  (let [font (Font. "Courier" Font/BOLD 25)
        texts [(str verdict \space (format "%td.%<tm.%<tY" (java.util.Date. created)))
               username
               municipality
               "LUPAPISTE.fi"]
        text-widths (calculate-text-widths texts font)
        width (+ (reduce max text-widths) 80)
        i (BufferedImage. width 190 BufferedImage/TYPE_INT_ARGB)]
    (doto (.createGraphics i)
      (.setColor (Color. 0 0 0 0))
      (.fillRect 0 0 width 300)
      (.setStroke (BasicStroke. 16.0 BasicStroke/CAP_ROUND BasicStroke/JOIN_ROUND))
      (.setColor (Color. 255 255 255 160))
      (.fill (RoundRectangle2D$Float. 20 20 (- width 40) 150 30 30))
      (.setColor (Color. 128 16 16 160))
      (.draw (RoundRectangle2D$Float. 20 20 (- width 40) 150 30 30))
      (.setColor (Color. 128 16 16 192))
      (.setFont font)
      (.drawString (nth texts 0) 40 55)
      (.drawString (nth texts 1) 40 90)
      (.drawString (nth texts 2) 40 115)
      (.drawString (nth texts 3) (int (/ (- width (nth text-widths 3)) 2)) 150)
      (.dispose))
    i))

;;
;; Stamp with provided image:
;;

(declare stamp-pdf stamp-image)

(defn stamp [stamp content-type in out]
  (cond
    (= content-type "application/pdf")      (do (stamp-pdf stamp in out) nil)
    (ss/starts-with content-type "image/")  (do (stamp-image stamp content-type in out) nil)
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
  (doto (.createGraphics image)
    (.drawImage stamp 5 (- (.getHeight image nil) (.getHeight stamp nil) 5) nil)
    (.dispose)))

;;
;; Swing frame for testing stamp:
;;

(defn- lorem-line [c]
  (let [sb (StringBuilder.)]
    (while (< (.length sb) c)
      (.append sb (rand-nth ["lorem" "ipsum" "dolor" "sit" "amet" "consectetur" "adipisicing" "elit" "sed" "do" "eiusmod" "tempor" "incididunt" "ut" "labore" "et" "dolore" "magna" "aliqua"]))
      (.append sb \space))
    (str sb)))

(defn lorem-ipsum [g w h]
  (.setFont g (Font. Font/SERIF Font/PLAIN 18))
  (let [fm (.getFontMetrics g)
        cw (.stringWidth fm "l")
        ch (+ (.getAscent fm) (.getDescent fm))]
    (doseq [[y text] (map vector (range ch h ch) (repeatedly (partial lorem-line (int (/ w cw)))))]
      (.drawString g text cw y))))

(defn- paint-component [g w h]
  (let [i (make-stamp "hyv\u00E4ksytty" (System/currentTimeMillis) "Veikko Viranomainen" "SIPOO")
        iw (.getWidth i)
        ih (.getHeight i)]
    (if false
      (doto g
        (.setColor Color/GRAY)
        (.fillRect 0 0 (int (/ w 2)) (int (/ h 2)))
        (.setColor Color/WHITE)
        (.fillRect (int (/ w 2)) 0 w (int (/ h 2)))
        (.setColor Color/RED)
        (.fillRect 0 (int (/ h 2)) (int (/ w 2)) h)
        (.setColor Color/BLACK)
        (.fillRect (int (/ w 2)) (int (/ h 2)) w h)))
    (if true
      (lorem-ipsum g w h))
    (.drawImage g i (int (/ (- w iw) 2)) (int (/ (- h ih) 2)) nil)))

(defn make-frame []
  (let [f (doto (javax.swing.JFrame. "heelo")
            (.setContentPane
              (doto (proxy [javax.swing.JComponent] []
                      (paint [g]
                        (paint-component g (.getWidth this) (.getHeight this))))))
            (.setMinimumSize (java.awt.Dimension. 100 100))
            (.setDefaultCloseOperation javax.swing.JFrame/DISPOSE_ON_CLOSE)
            (.setAlwaysOnTop true)
            (.setLocationByPlatform true)
            (.pack)
            (.setVisible true))
        repaint (fn [_ _ _ _]
                  (javax.swing.SwingUtilities/invokeLater (fn [] (.repaint f))))]
    (doseq [v [#'make-stamp #'paint-component #'lorem-line #'lorem-ipsum]]
      (add-watch v :repaint repaint))
    f))
