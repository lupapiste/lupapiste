(ns user
  (:require [lupapalvelu.server :as server]
            [sade.env :as env]
            [lupapalvelu.fixture :as fixture]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [monger.core :as m]
            [monger.collection :as mc]
            [monger.db :as db]
            [monger.gridfs :as gfs]
            [monger.command :refer [server-status]]))

(defn disable-anti-csrf []
  (env/enable-feature! :disable-anti-csrf))

(def go server/-main)
(println "Ready! To start Lupapiste server eval (go)")

(defn ktag
  "KRYSP mapping tag"
  [s & [children]] {:tag (keyword (clojure.string/replace s #"^[a-z]+:" "")) :child (or children [])})

(defn ktags [c & [children]] (mapv #(ktag % children) (clojure.string/split c #"\s")))
