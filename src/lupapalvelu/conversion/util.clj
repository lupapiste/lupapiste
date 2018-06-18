(ns lupapalvelu.conversion.util
  (:require [lupapalvelu.application :as application]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer :all]
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

(defn rakennelmatieto->kaupunkikuvatoimenpide [raktieto]
  (let [kaupunkikuvatoimenpide-ei-tunnusta {:kayttotarkoitus {:value ""}
                                            :kokonaisala {:value ""}
                                            :kuvaus {:value (get-in raktieto [:Rakennelma :kuvaus :kuvaus])}}
        tunnus (get-in raktieto [:Rakennelma :tunnus :kunnanSisainenPysyvaRakennusnumero])
        schema (schemas/get-schema 1 "kaupunkikuvatoimenpide")
        data (if (nil? tunnus)
               kaupunkikuvatoimenpide-ei-tunnusta
               (merge kaupunkikuvatoimenpide-ei-tunnusta {:tunnus {:value tunnus}
                                                          :valtakunnallinenNumero {:value (get-in raktieto [:Rakennelma :tunnus :kiinttun])}}))]
    {:id (mongo/create-id)
     :created (now)
     :schema-info (:info schema)
     :data data}))

;; For development purposes

#_(def rak-sanomat
  (->> (list "40-0019-13-A.xml" "18-0483-13-PI.xml" "40-0049-13-P.xml" "68-1147-13-BL.xml" "85-0253-13-PI.xml")
       (map #(str "./src/lupapalvelu/conversion/test-data/" %))
       (map #(lupapalvelu.xml.krysp.application-from-krysp/get-local-application-xml-by-filename % "R"))
       (map lupapalvelu.xml.krysp.reader/->rakennelmatieto)))

#_(def testdata
 (lupapalvelu.xml.krysp.application-from-krysp/get-local-application-xml-by-filename (str "./src/lupapalvelu/conversion/test-data/" "18-0030-13-A.xml") "R"))

#_(def test-docdata
  (lupapalvelu.xml.krysp.reader/->rakennelmatieto testdata))

#_(def app
  (application/make-document))
