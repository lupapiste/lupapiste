(ns lupapalvelu.ui.location.md5-util
  "Calculates md5 hash to a file (or buffer)"
  (:require [goog.crypt.base64 :as base64]
            [goog.crypt.Md5]
            [promesa.core :as promesa]))

(defn ^:private read-slice-from-file
  [file start end on-read-binary-array]
  (let [reader (js/FileReader.)
        wrapped-fn (fn [e] (on-read-binary-array
                             (js/Uint8Array. (.. e -target -result))))]
    (set! (.-onload reader) wrapped-fn)
    (.readAsArrayBuffer reader (.slice file start end))))

(defn ^:private read-slice-from-file-as-promise
  [file start end on-read-binary on-progress]
  (promesa/create
    (fn [resolve _]
      (read-slice-from-file file start end #(resolve (do
                                                       (on-progress end)
                                                       (on-read-binary %)))))))

(defn ^:private update-md5 [md5-acc bytes]
  (.update md5-acc bytes)
  md5-acc)

(defn- ^:private get-md5-diggest [md5-acc]
  (-> (.digest md5-acc) (base64/encodeByteArray)))

(defn ^:private calculate-buffer-ranges [size block-size]
  (letfn [(append-remaining-bytes [buffer-pairs]
            (let [remaining-bytes (mod size block-size)
                  [_ buffer-end] (last buffer-pairs)
                  last-segement-start (or buffer-end 0)
                  last-segment-end (+ last-segement-start remaining-bytes)]
              (if (zero? remaining-bytes)
                buffer-pairs
                (concat buffer-pairs [[last-segement-start last-segment-end]]))))]
    (-> (partition 2 1 (range 0 (inc size) block-size))
        append-remaining-bytes)))

(defn calculater-md5-digest [file on-progress]
  (let [md5-acc (goog.crypt.Md5.)
        block-size (* (.-blockSize md5-acc) 10000)
        size (.-size file)
        buffers (calculate-buffer-ranges size block-size)]

    (loop [remaining-buffers buffers
           promise-acc (promesa/resolved md5-acc)]
      (let [[start end] (first remaining-buffers)
            update-diggest-acc (fn [file start end md5-acc]
                                 (read-slice-from-file-as-promise
                                   file start end
                                   (partial update-md5 md5-acc)
                                   on-progress))]
        (if (and start end)
          (recur (rest remaining-buffers)
                 (.then promise-acc (partial update-diggest-acc file start end)))
          (.then promise-acc get-md5-diggest))))))
