(ns lupapalvelu.conversion.util
  (:require [sade.core :refer :all]
            [sade.common-reader :as cr]
            [sade.strings :as ss]
            [sade.xml :refer :all]
            [lupapalvelu.prev-permit :as prev-permit]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]))


(defn normalize-permit-id
  "Viitelupien tunnukset on Factassa tallennettu 'tietokantaformaatissa', josta ne on tunnuksella
  hakemista varten muunnettava yleiseen formaattiin.
  Esimerkki: 12-0477-A 63 -> 63-0447-12-A"
  [id]
  (if (Character/isLetter (last id)) ;; If the input is already in 'ui-format', it's not altered.
    id
    (let [parts (zipmap '(:vuosi :no :tyyppi :kauposa) (ss/split id #"[- ]"))]
      (ss/join "-" ((juxt :kauposa :no :vuosi :tyyppi) parts)))))

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
