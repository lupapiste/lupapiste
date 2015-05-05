(ns sade.property
  (:require [clojure.string :as s]
            [sade.util :as util]))

(def property-id-pattern
  "Regex for property id human readable format"
  #"^(\d{1,3})-(\d{1,3})-(\d{1,4})-(\d{1,4})$")

(defn to-property-id [^String human-readable]
  (let [parts (map #(Integer/parseInt % 10) (rest (re-matches property-id-pattern human-readable)))]
    (apply format "%03d%03d%04d%04d" parts)))

(def human-readable-property-id-pattern
  "Regex for splitting db-saved property id to human readable form"
  #"^([0-9]{1,3})([0-9]{1,3})([0-9]{1,4})([0-9]{1,4})$")

(defn to-human-readable-property-id [^String property-id]
  (->> (re-matches human-readable-property-id-pattern property-id)
    rest
    (map util/->int)
    (s/join "-")))
