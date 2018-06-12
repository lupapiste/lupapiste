(ns lupapalvelu.conversion.util
  (:require [sade.core :refer :all]
            [sade.common-reader :as cr]
            [sade.strings :as ss]
            [sade.xml :refer :all]
            [lupapalvelu.prev-permit :as prev-permit]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]))

(defn destructure-permit-id
  "Split a permit id into a map of parts. Works regardless of which id-format is used."
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

(def general-permit-id-regex
  "So-called 'general' format, e.g. 63-0447-12-A"
  #"\d{2}-\d{4}-\d{2}-[A-Z]+")

(def database-permit-id-regex
  "So-called 'database' format, e.g. 12-0477-A 63"
  #"\d{2}-\d{4}-[A-Z]+ \d{2}")

(defn normalize-permit-id
  "Viitelupien tunnukset on Factassa tallennettu 'tietokantaformaatissa', josta ne on tunnuksella
  hakemista varten muunnettava yleiseen formaattiin.
  Esimerkki: 12-0477-A 63 -> 63-0447-12-A"
  [id]
  (ss/join "-" ((juxt :kauposa :no :vuosi :tyyppi) (destructure-permit-id id))))

(defn get-kuntalupatunnus [xml]
  (cr/all-of (select1 xml [:rakennusvalvontaAsiatieto :luvanTunnisteTiedot :kuntalupatunnus])))

(defn get-viitelupatunnukset
  "Takes a parsed XML document, returns a list of viitelupatunnus -ids (in 'permit-id'-format) found therein."
  [xml]
  (->> (select xml [:rakennusvalvontaAsiatieto :viitelupatieto])
       (map (comp normalize-permit-id #(get-in % [:LupaTunnus :kuntalupatunnus]) cr/all-of))))

(defn is-foreman-application? [xml]
  (let [permit-type (-> xml get-kuntalupatunnus (ss/split #"-") last)]
    (= "TJO" permit-type)))

(defn get-tyonjohtajat [xml]
  (when (is-foreman-application? xml)
    (as-> xml x
      (krysp-reader/get-asiat-with-kuntalupatunnus x (get-kuntalupatunnus xml))
      (first x)
      (select x [:osapuolettieto :Osapuolet :tyonjohtajatieto :Tyonjohtaja])
      (map cr/all-of x))))

(defn xml->tj-documents [xml]
  (map prev-permit/tyonjohtaja->tj-document (get-tyonjohtajat xml)))
