(ns cljs.lupapalvelu.ui.invoices.util
  (:require [clojure.string :refer [blank? join]]))

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

(defn num [val]
  (when (num? val)
    (js/parseInt val)))
