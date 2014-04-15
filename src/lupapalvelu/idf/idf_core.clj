(ns lupapalvelu.idf.idf-core
  (:require [digest]))

(def ^:private secrets {"lupapiste" "TAMAN-MUODOSTI-LUPAPISTE"})

(defn known-partner? [partner-name]
  (contains? secrets partner-name))

(defn calculate-mac [first-name last-name email phone street zip city marketing architect app id ts]
  {:pre [(known-partner? app)]}
  (digest/sha-256 (str first-name last-name email phone street zip city marketing architect app id ts (secrets app))))
