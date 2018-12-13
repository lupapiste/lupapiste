(ns cljs.lupapalvelu.ui.invoices.util
  (:require [clojure.string :refer [blank? join replace]]))

(defn- split-on-space [s]
  (clojure.string/split s #"\s"))

(defn strip-whitespace [s]
  (->> (split-on-space s)
       (remove blank?)
       (join "")))

(defn num? [val]
  (let [val (strip-whitespace val)]
    (when (not (blank? val))
      (not (js/isNaN val)))))

(defn ->int [val]
  (when (num? val)
    (js/parseInt val)))

(defn comma->dot [val]
  (when val
    (replace val #"," ".")))

(defn ->float [val]
  (let [val-with-dot (comma->dot val)]
    (when (num? val-with-dot)
      (js/parseFloat val-with-dot))))
