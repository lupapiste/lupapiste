(ns lupapalvelu.backing-system.krysp.poikkeamis-canonical-to-krysp-xml-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.backing-system.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.document.poikkeamis-canonical :refer [poikkeus-application-to-canonical]]
            [lupapalvelu.document.poikkeamis-canonical-test :refer [poikkari-hakemus suunnitelutarveratkaisu]]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.backing-system.krysp.poikkeamis-mapping :as mapping]
            [lupapalvelu.xml.validator :as  validator]
            [clojure.data.xml :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :as cr]
            [sade.util :as util]))

(testable-privates lupapalvelu.backing-system.krysp.poikkeamis-mapping common-map-enums)

(fact ":tag is set, 2.1.2" (has-tag mapping/poikkeamis_to_krysp_212) => true)
(fact ":tag is set, 2.1.3" (has-tag mapping/poikkeamis_to_krysp_213) => true)
(fact ":tag is set, 2.1.4" (has-tag mapping/poikkeamis_to_krysp_214) => true)
(fact ":tag is set, 2.1.5" (has-tag mapping/poikkeamis_to_krysp_215) => true)
(fact ":tag is set, 2.2.0" (has-tag mapping/poikkeamis_to_krysp_220) => true)
(fact ":tag is set, 2.2.1" (has-tag mapping/poikkeamis_to_krysp_221) => true)

(def poikkeus-canonical (poikkeus-application-to-canonical poikkari-hakemus "fi"))
(def poikkeus-krysp-path [:Popast :poikkeamisasiatieto :Poikkeamisasia])

(facts "Poikkeuslupa to canonical and then to poikkeuslupa xml with schema validation"
  (facts "2.1.2"
    (let [xml_212_s (-> (common-map-enums poikkeus-canonical poikkeus-krysp-path "2.1.2")
                        (element-to-xml mapping/poikkeamis_to_krysp_212)
                        (indent-str))]

      (fact "Mapping"
        ;; Alla oleva tekee jo validoinnin, mutta annetaan olla tuossa alla viela validointi, jottei tule joku riko olemassa olevaa validointia
        (mapping-to-krysp/save-application-as-krysp
          {:application poikkari-hakemus :organization {:krysp {:P {:ftpUser "dev_sipoo" :version "2.1.2"}}}} "fi" poikkari-hakemus))

      (fact "Validate"
        (validator/validate xml_212_s (:permitType poikkari-hakemus) "2.1.2")) ; throws exception

      (fact "Check xml"
        (-> (cr/strip-xml-namespaces (xml/parse xml_212_s))
            (xml/get-text [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosalatieto :kerrosala :pintaAla])) => "200")))


  (facts "2.1.4"
    (let [xml_214_s (-> (common-map-enums poikkeus-canonical poikkeus-krysp-path "2.1.4")
                        (element-to-xml mapping/poikkeamis_to_krysp_214)
                        (indent-str))]

      (mapping-to-krysp/save-application-as-krysp
        {:application poikkari-hakemus :organization {:krysp {:P {:ftpUser "dev_sipoo" :version "2.1.4"}}}} "fi" poikkari-hakemus)

      (validator/validate xml_214_s (:permitType poikkari-hakemus) "2.1.4") ; throws exception

      (fact "Check xml"
        (let [lp-xml    (cr/strip-xml-namespaces (xml/parse xml_214_s))]
          (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosalatieto :kerrosala :pintaAla]) => nil
          (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosala]) => "200"))))


  (facts "2.1.5"
    (let [xml_215_s (-> (common-map-enums poikkeus-canonical poikkeus-krysp-path "2.1.5")
                        (element-to-xml mapping/poikkeamis_to_krysp_215)
                        (indent-str))]

      (mapping-to-krysp/save-application-as-krysp
        {:application poikkari-hakemus :organization {:krysp {:P {:ftpUser "dev_sipoo" :version "2.1.5"}}}} "fi" poikkari-hakemus)

      (validator/validate xml_215_s (:permitType poikkari-hakemus) "2.1.5"))) ; throws exception


  (facts "2.2.0"
    (let [xml_220_s (-> (common-map-enums poikkeus-canonical poikkeus-krysp-path "2.2.0")
                        (element-to-xml mapping/poikkeamis_to_krysp_220)
                        (indent-str))]

      (mapping-to-krysp/save-application-as-krysp
        {:application poikkari-hakemus :organization {:krysp {:P {:ftpUser "dev_sipoo" :version "2.2.0"}}}} "fi" poikkari-hakemus)

      (validator/validate xml_220_s (:permitType poikkari-hakemus) "2.2.0"))) ; throws exception

  (facts "2.2.1"
    (let [xml_s (-> (common-map-enums poikkeus-canonical poikkeus-krysp-path "2.2.1")
                        (element-to-xml mapping/poikkeamis_to_krysp_221)
                        (indent-str))]

      (mapping-to-krysp/save-application-as-krysp
        {:application poikkari-hakemus :organization {:krysp {:P {:ftpUser "dev_sipoo" :version "2.2.1"}}}} "fi" poikkari-hakemus)

      (validator/validate xml_s (:permitType poikkari-hakemus) "2.2.1") ; throws exception

      (facts "Check xml"
        (let [lp-xml     (cr/strip-xml-namespaces (xml/parse xml_s))
              ilmoittaja (xml/select (cr/strip-xml-namespaces (xml/parse xml_s)))]
          (fact "pintaAla"
            (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosalatieto :kerrosala :pintaAla]) => nil)
          (fact "kerrosala"
            (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosala]) => "200")
          (fact "valtioKansainvalinen"
            (xml/get-text lp-xml [:osapuolettieto :Osapuolet :osapuolitieto :Osapuoli :henkilo :osoite :valtioKansainvalinen]) => "FIN")
          (fact "kaavatilanne"
            (xml/get-text lp-xml [:rakennuspaikkatieto :Rakennuspaikka :kaavatilanne]) => "maakuntakaava")
          (fact "avainsanaTieto"
            (map :content (xml/select lp-xml [:avainsanaTieto :Avainsana])) => [])
          (fact "menettelyTOS"
            (xml/get-text lp-xml [:menettelyTOS]) => nil)))))

  (facts "2.2.3"
    (let [xml_s (-> (common-map-enums poikkeus-canonical poikkeus-krysp-path "2.2.3")
                        (element-to-xml mapping/poikkeamis_to_krysp_223)
                        (indent-str))]

      (mapping-to-krysp/save-application-as-krysp
        {:application poikkari-hakemus :organization {:krysp {:P {:ftpUser "dev_sipoo" :version "2.2.3"}}}} "fi" poikkari-hakemus)

      (validator/validate xml_s (:permitType poikkari-hakemus) "2.2.3") ; throws exception

      (facts "Check xml"
        (let [lp-xml     (cr/strip-xml-namespaces (xml/parse xml_s))
              ilmoittaja (xml/select (cr/strip-xml-namespaces (xml/parse xml_s)))]
          (fact "pintaAla"
            (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosalatieto :kerrosala :pintaAla]) => nil)
          (fact "kerrosala"
            (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosala]) => "200")
          (fact "valtioKansainvalinen"
            (xml/get-text lp-xml [:osapuolettieto :Osapuolet :osapuolitieto :Osapuoli :henkilo :osoite :valtioKansainvalinen]) => "FIN")
          (fact "kaavatilanne"
            (xml/get-text lp-xml [:rakennuspaikkatieto :Rakennuspaikka :kaavatilanne]) => "maakuntakaava")
          (fact "avainsanaTieto"
            (map :content (xml/select lp-xml [:avainsanaTieto :Avainsana])) => [["soderkulla"] ["Pena Panaani"]])
          (fact "menettelyTOS"
            (xml/get-text lp-xml [:menettelyTOS]) => "tiedonohjausmenettely"))))))


(def canonical-no-toimenpidetieto (util/dissoc-in poikkeus-canonical [:Popast :poikkeamisasiatieto :Poikkeamisasia :toimenpidetieto]))

(facts "Poikkeaminen without toimenpidetieto is valid"
  (fact "2.1.2"
    (let [xml_212_s (-> (common-map-enums canonical-no-toimenpidetieto poikkeus-krysp-path "2.1.2")
                        (element-to-xml mapping/poikkeamis_to_krysp_212)
                        (indent-str))]

      (validator/validate xml_212_s (:permitType poikkari-hakemus) "2.1.2") => nil))

  (fact "2.1.4"
    (let [xml_214_s (-> (common-map-enums canonical-no-toimenpidetieto poikkeus-krysp-path "2.1.4")
                        (element-to-xml mapping/poikkeamis_to_krysp_214)
                        (indent-str))]

      (validator/validate xml_214_s (:permitType poikkari-hakemus) "2.1.4") => nil))

  (fact "2.1.5"
    (let [xml_215_s (-> (common-map-enums canonical-no-toimenpidetieto poikkeus-krysp-path "2.1.5")
                        (element-to-xml mapping/poikkeamis_to_krysp_215)
                        (indent-str))]

      (validator/validate xml_215_s (:permitType poikkari-hakemus) "2.1.5") => nil))

  (fact "2.2.0"
    (let [xml_220_s (-> (common-map-enums canonical-no-toimenpidetieto poikkeus-krysp-path "2.2.0")
                        (element-to-xml mapping/poikkeamis_to_krysp_220)
                        (indent-str))]

      (validator/validate xml_220_s (:permitType poikkari-hakemus) "2.2.0") => nil))

  (facts "2.2.1"
    (let [xml_221_s (-> (common-map-enums canonical-no-toimenpidetieto poikkeus-krysp-path "2.2.1")
                        (element-to-xml mapping/poikkeamis_to_krysp_221)
                        (indent-str))]

      (validator/validate xml_221_s (:permitType poikkari-hakemus) "2.2.1") => nil)))

(def suunnittelu-canonical (poikkeus-application-to-canonical suunnitelutarveratkaisu "fi"))
(def suunnittelu-krysp-path [:Popast :suunnittelutarveasiatieto :Suunnittelutarveasia])

(facts "Suunnittelutarveratkaisu"
  (facts "2.1.2"
    (let [xml_s (-> (common-map-enums suunnittelu-canonical suunnittelu-krysp-path "2.1.2")
                        (element-to-xml mapping/poikkeamis_to_krysp_212)
                        (indent-str))]

      (fact "Mapping"
        ;; Alla oleva tekee jo validoinnin, mutta annetaan olla tuossa alla viela validointi, jottei tule joku riko olemassa olevaa validointia
        (mapping-to-krysp/save-application-as-krysp
          {:application suunnitelutarveratkaisu :organization {:krysp {:P {:ftpUser "dev_sipoo" :version "2.1.2"}}}} "fi" suunnitelutarveratkaisu))

      (fact "Validate"
        (validator/validate xml_s (:permitType suunnitelutarveratkaisu) "2.1.2")) ; throws exception

      (fact "Check xml"
        (-> (cr/strip-xml-namespaces (xml/parse xml_s))
            (xml/get-text [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosalatieto :kerrosala :pintaAla])) => "200")))

  (facts "2.2.1"
    (let [xml_s (-> (common-map-enums suunnittelu-canonical suunnittelu-krysp-path "2.2.1")
                        (element-to-xml mapping/poikkeamis_to_krysp_221)
                        (indent-str))]

      (mapping-to-krysp/save-application-as-krysp
        {:application suunnitelutarveratkaisu :organization {:krysp {:P {:ftpUser "dev_sipoo" :version "2.2.1"}}}} "fi" suunnitelutarveratkaisu)

      (validator/validate xml_s (:permitType suunnitelutarveratkaisu) "2.2.1") ; throws exception

      (facts "Check xml"
        (let [lp-xml     (cr/strip-xml-namespaces (xml/parse xml_s))
              ilmoittaja (xml/select (cr/strip-xml-namespaces (xml/parse xml_s)))]
          (fact "pintaAla"
            (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosalatieto :kerrosala :pintaAla]) => nil)
          (fact "kerrosala"
            (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosala]) => "200")
          (fact "valtioKansainvalinen"
            (xml/get-text lp-xml [:osapuolettieto :Osapuolet :osapuolitieto :Osapuoli :henkilo :osoite :valtioKansainvalinen]) => "FIN")
          (fact "kaavatilanne"
            (xml/get-text lp-xml [:rakennuspaikkatieto :Rakennuspaikka :kaavatilanne]) => "maakuntakaava")
          (fact "avainsanaTieto"
            (map :content (xml/select lp-xml [:avainsanaTieto :Avainsana])) => [])
          (fact "menettelyTOS"
            (xml/get-text lp-xml [:menettelyTOS]) => nil)))))

  (facts "2.2.3"
    (let [xml_s (-> (common-map-enums suunnittelu-canonical suunnittelu-krysp-path "2.2.3")
                        (element-to-xml mapping/poikkeamis_to_krysp_223)
                        (indent-str))]

      (mapping-to-krysp/save-application-as-krysp
        {:application suunnitelutarveratkaisu :organization {:krysp {:P {:ftpUser "dev_sipoo" :version "2.2.3"}}}} "fi" suunnitelutarveratkaisu)

      (validator/validate xml_s (:permitType suunnitelutarveratkaisu) "2.2.3") ; throws exception

      (facts "Check xml"
        (let [lp-xml     (cr/strip-xml-namespaces (xml/parse xml_s))
              ilmoittaja (xml/select (cr/strip-xml-namespaces (xml/parse xml_s)))]
          (fact "pintaAla"
            (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosalatieto :kerrosala :pintaAla]) => nil)
          (fact "kerrosala"
            (xml/get-text lp-xml [:toimenpidetieto :Toimenpide :tavoitetilatieto :kerrosala]) => "200")
          (fact "valtioKansainvalinen"
            (xml/get-text lp-xml [:osapuolettieto :Osapuolet :osapuolitieto :Osapuoli :henkilo :osoite :valtioKansainvalinen]) => "FIN")
          (fact "kaavatilanne"
            (xml/get-text lp-xml [:rakennuspaikkatieto :Rakennuspaikka :kaavatilanne]) => "maakuntakaava")
          (fact "avainsanaTieto"
            (map :content (xml/select lp-xml [:avainsanaTieto :Avainsana])) => [])
          (fact "menettelyTOS"
            (xml/get-text lp-xml [:menettelyTOS]) => "tiedonohjausmenettely"))))))
