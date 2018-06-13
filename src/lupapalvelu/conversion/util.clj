(ns lupapalvelu.conversion.util
  (:require [sade.core :refer :all]
            [sade.strings :as ss]))

(def general-permit-id-regex
  "So-called 'general' format, e.g. 63-0447-12-A"
  #"\d{2}-\d{4}-\d{2}-[A-Z]{1,3}")

(def database-permit-id-regex
  "So-called 'database' format, e.g. 12-0477-A 63"
  #"\d{2}-\d{4}-[A-Z]{1,3} \d{2}")

(defn destructure-permit-id
  "Split a permit id into a map of parts. Works regardless of which of the two
  id-format is used. Returns nil if input is invalid."
  [id]
  (let [id-type (cond
                  (= id (re-find general-permit-id-regex id)) :general
                  (= id (re-find database-permit-id-regex id)) :database
                  :else :unknown)]
    (when-not (= :unknown id-type)
      (zipmap (if (= id-type :general)
                '(:kauposa :no :vuosi :tyyppi)
                '(:vuosi :no :tyyppi :kauposa))
              (ss/split id #"[- ]")))))

; (defn xml->tj-documents [xml]
;   (map prev-permit/tyonjohtaja->tj-document (get-tyonjohtajat xml)))

(defn normalize-permit-id
  "Viitelupien tunnukset on Factassa tallennettu 'tietokantaformaatissa', josta ne on tunnuksella
  hakemista varten muunnettava yleiseen formaattiin.
  Esimerkki: 12-0477-A 63 -> 63-0447-12-A"
  [id]
  (ss/join "-" ((juxt :kauposa :no :vuosi :tyyppi) (destructure-permit-id id))))
