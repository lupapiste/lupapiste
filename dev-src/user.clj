(ns user
  (:require [monger.operators :refer :all]
            [monger.core :as m]
            [monger.collection :as mc]
            [monger.db :as db]
            [monger.gridfs :as gfs]
            [monger.command :refer [server-status]]))

(defn disable-anti-csrf []
  (require 'sade.env)
  ((resolve 'sade.env/enable-feature!) :disable-anti-csrf))

(defn go []
  (println "Loading lupapalvelu.server...")
  (require 'lupapalvelu.server)
  (println "Launhing server...")
  ((resolve 'lupapalvelu.server/-main)))

(defn ktag
  "KRYSP mapping tag"
  [s & [children]] {:tag (keyword (clojure.string/replace s #"^[a-z]+:" "")) :child (or children [])})

(defn ktags [c & [children]] (mapv #(ktag % children) (clojure.string/split c #"\s")))
