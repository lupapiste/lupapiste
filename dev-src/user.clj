(ns user
  (:require [monger.operators :refer :all]
            [monger.core :as m]
            [monger.collection :as mc]
            [monger.db :as db]
            [monger.gridfs :as gfs]
            [monger.command :refer [server-status]]
            [clojure.java.io :as io])
  (:import  [java.util.zip ZipInputStream]))

(defn disable-anti-csrf []
  (require 'sade.env)
  ((resolve 'sade.env/enable-feature!) :disable-anti-csrf))

(defn go []
  (println "Loading lupapalvelu.server...")
  (require 'lupapalvelu.server)
  (println "Launching server...")
  ((resolve 'lupapalvelu.server/-main)))

(defn ktag
  "KRYSP mapping tag"
  [s & [children]] {:tag (keyword (clojure.string/replace s #"^[a-z]+:" "")) :child (or children [])})

(defn ktags [c & [children]] (mapv #(ktag % children) (clojure.string/split c #"\s")))

(defn play-with-zip
  "http://docs.oracle.com/javase/8/docs/api/java/util/zip/ZipEntry.html
   http://docs.oracle.com/javase/8/docs/api/java/util/zip/ZipInputStream.html
   http://docs.oracle.com/javase/8/docs/api/java/util/zip/ZipFile.html"
  [path]
  (let [zip-stream (ZipInputStream. (io/input-stream (io/file path)))
        to-zip-entries (fn [s result]
                         (if-let [entry (.getNextEntry s)]
                           (recur s (conj result (bean entry))) ; bean makes it just Clojure friendly 'readonly' map
                           result))
        result (to-zip-entries zip-stream [])]
    result)) ; returns readable zip entries in sequence
