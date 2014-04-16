(ns lupapalvelu.idf.idf-core
  (:require [digest]))


(def utf8 (java.nio.charset.Charset/forName "UTF-8"))

; TODO put these somewhere safe
(def ^:private secrets {"lupapiste" "TAMAN-MUODOSTI-LUPAPISTE"
                        "rakentajafi" "TAMAN-MUODOSTI-RAKENTAJA.FI"})

(defn known-partner? [partner-name]
  (contains? secrets partner-name))

(defn calculate-mac [first-name last-name email phone street zip city marketing architect app id ts]
  {:pre [(known-partner? app)]}
  (let [text (str first-name last-name email phone street zip city marketing architect app id ts (secrets app))]
    (digest/sha-256 (java.io.ByteArrayInputStream. (.getBytes text utf8)))))
