(ns sade.property
  (:require [sade.strings :as ss]
            [sade.util :as util]
            [sade.municipality :as muni]))

(def property-id-pattern
  "Regex for property id human readable format"
  #"^(\d{1,3})-(\d{1,3})-(\d{1,4})-(\d{1,4})$")

(defn to-property-id [^String human-readable]
  (let [parts (map #(Integer/parseInt % 10) (rest (re-matches property-id-pattern human-readable)))]
    (apply format "%03d%03d%04d%04d" parts)))

(def db-property-id-pattern
  "Regex for splitting db-saved property id to human readable form"
  #"^([0-9]{1,3})([0-9]{1,3})([0-9]{1,4})([0-9]{1,4})$")

(defn to-human-readable-property-id [^String property-id]
  (->> (re-matches db-property-id-pattern property-id)
    rest
    (map util/->int)
    (ss/join "-")))

(defn- take-municipality [[_ s]]
  (let [municipality (ss/zero-pad 3 s)]
    (get muni/municipality-mapping municipality municipality)))

(defn municipality-id-by-property-id [^String property-id]
  (when (and (string? property-id) (> (count property-id) 3))
    (condp re-find (ss/trim property-id)
      property-id-pattern    :>> take-municipality
      db-property-id-pattern :>> take-municipality
      nil)))
