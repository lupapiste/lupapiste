(ns user
  (:require [clojure.pprint :as pprint]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [mount.core :as mount]
            [schema.core :as sc]
            [sade.common-reader]
            [sade.xml])
  (:import [java.util.zip ZipInputStream]))

(defonce ns-load-times (atom {}))

(alter-var-root (var clojure.core/load-lib)
                (fn [original-load-lib]
                  (fn [prefix lib & options]
                    (let [start-time (System/nanoTime)
                          return-val (apply original-load-lib prefix lib options)
                          end-time (System/nanoTime)
                          duration (quot (- end-time start-time) 1000000)
                          ns-name (if prefix (str prefix "." lib) (name lib))]
                      (swap! ns-load-times update ns-name (fnil max duration) duration)
                      return-val))))

(defn- ns-load-times-descending [] (reverse (sort-by second @ns-load-times)))
(def lupis-ns? (comp #(or (.startsWith ^String % "lupa") (.startsWith ^String % "sade")) first))

(defn- print-ns-load-times [top-n & [filter-fn]]
  (let [ns-times (if (fn? filter-fn) (filter-fn (ns-load-times-descending)) (ns-load-times-descending))]
    (doseq [ns-time (if (and (number? top-n) (pos? top-n)) (take top-n ns-times) ns-times)]
      (println (first ns-time) (last ns-time) "ms"))))

(defn lupis-ns-times [& [top-n]] (print-ns-load-times top-n (partial filter lupis-ns?)))
(defn other-ns-times [& [top-n]] (print-ns-load-times top-n (partial remove lupis-ns?)))

(defn disable-anti-csrf []
  (require 'sade.env)
  ((resolve 'sade.env/enable-feature!) :disable-anti-csrf))

(defn go []
  (println "Loading lupapalvelu.server...")
  (require 'lupapalvelu.server)
  (println "Launching server...")
  ((resolve 'lupapalvelu.server/-main)))

(defn start []
  (println "Loading lupapalvelu.server...")
  (require 'lupapalvelu.server)
  (println "mount/start ->")
  (mount/start)
  :started)

(defn reset []
  (mount/stop)
  ; (clojure.tools.namespace.repl/refresh :after 'user/start) doesn't work (yet), TODO investigate why
  (mount/start))

(defn ktag
  "KRYSP mapping tag"
  [s & [children]] {:tag (keyword (s/replace s #"^[a-z]+:" "")) :child (or children [])})

(defn ktags [c & [children]] (mapv #(ktag % children) (s/split c #"\s")))

(require ['clojure.java.io :as 'io])
(defn play-with-zip
  "http://docs.oracle.com/javase/8/docs/api/java/util/zip/ZipEntry.html
   http://docs.oracle.com/javase/8/docs/api/java/util/zip/ZipInputStream.html
   http://docs.oracle.com/javase/8/docs/api/java/util/zip/ZipFile.html"
  [path]
  (let [zip-stream (ZipInputStream. (io/input-stream (io/file path)))
        to-zip-entries (fn [^ZipInputStream s result]
                         (if-let [entry (.getNextEntry s)]
                           (recur s (conj result (bean entry))) ; bean makes it just Clojure friendly 'readonly' map
                           result))
        result (to-zip-entries zip-stream [])]
    result))                                                ; returns readable zip entries in sequence

(defn spy [v]
  (println "--spying:")
  (pprint/pprint v)
  v)

(defn stripped-xml-from-file [filename]
  (-> (slurp (io/file filename))
      (sade.xml/parse)
      (sade.common-reader/strip-xml-namespaces)))

; For masochists:
; validate all schemas in development
; (sc/set-fn-validation! true)

(comment
  (require 'midje.repl)
  (midje.repl/load-facts :all))
