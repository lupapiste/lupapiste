(ns sade.css
  "Garden-related CSS utilities."
  (:require [clojure.java.io :as io]
            [garden.def :refer [defcssfn]]
            [ring.util.codec :refer [url-encode]]
            [sade.strings :as ss]
            [selmer.parser :as parser]))

(def black     "#011627")
(def white     "#ffffff")
(def night-sky "#191147")

(defcssfn url)

(defn svg-url
  ([resource-path context]
   (->> (if context
          (parser/render-file resource-path context)
          (slurp (io/resource resource-path)))
        ss/trim
        (format "'data:image/svg+xml;utf-8,%s'")
        url))
  ([resource-path]
   (svg-url resource-path nil)))

(defn lupapiste-logo-svg-url
  [color]
  (svg-url "templates/lupapiste-logo.djhtml"
           {:color (url-encode color)}))
