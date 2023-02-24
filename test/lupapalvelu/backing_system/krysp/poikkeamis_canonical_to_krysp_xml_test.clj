(ns lupapalvelu.backing-system.krysp.poikkeamis-canonical-to-krysp-xml-test
  (:require [clojure.data.xml :refer :all]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.backing-system.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.backing-system.krysp.poikkeamis-mapping :as mapping]
            [lupapalvelu.document.poikkeamis-canonical :refer [poikkeus-application-to-canonical get-poikkeamis-conf]]
            [lupapalvelu.document.poikkeamis-canonical-test :refer [poikkari-hakemus suunnitelutarveratkaisu]]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.xml.validator :as validator]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.common-reader :as cr]
            [sade.util :as util]
            [sade.xml :as xml]))

(testable-privates lupapalvelu.backing-system.krysp.poikkeamis-mapping common-map-enums get-mapping)

(background (lupapalvelu.organization/get-application-organization anything) => {})

(fact ":tag is set, 2.1.2" (has-tag mapping/poikkeamis_to_krysp_212) => true)
(fact ":tag is set, 2.1.3" (has-tag mapping/poikkeamis_to_krysp_213) => true)
(fact ":tag is set, 2.1.4" (has-tag mapping/poikkeamis_to_krysp_214) => true)
(fact ":tag is set, 2.1.5" (has-tag mapping/poikkeamis_to_krysp_215) => true)
(fact ":tag is set, 2.2.0" (has-tag mapping/poikkeamis_to_krysp_220) => true)
(fact ":tag is set, 2.2.1" (has-tag mapping/poikkeamis_to_krysp_221) => true)
(fact ":tag is set, 2.2.4" (has-tag mapping/poikkeamis_to_krysp_224) => true)

(def poikkeus-canonical (poikkeus-application-to-canonical poikkari-hakemus "fi"))
(def poikkeus-krysp-path (get (get-poikkeamis-conf "poikkeamislupa") :asia-path))

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
              _ (xml/select (cr/strip-xml-namespaces (xml/parse xml_s)))]
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
              _ (xml/select (cr/strip-xml-namespaces (xml/parse xml_s)))]
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
            (xml/get-text lp-xml [:menettelyTOS]) => "tiedonohjausmenettely")))))

  (fact "2.2.4"
    (let [xml_s (-> (common-map-enums poikkeus-canonical poikkeus-krysp-path "2.2.4")
                    (element-to-xml mapping/poikkeamis_to_krysp_224)
                    (indent-str))]

      (mapping-to-krysp/save-application-as-krysp
        {:application poikkari-hakemus :organization {:krysp {:P {:ftpUser "dev_sipoo" :version "2.2.4"}}}} "fi" poikkari-hakemus)

      (validator/validate xml_s (:permitType poikkari-hakemus) "2.2.4") ; throws exception

      (facts "Check xml"
        (let [lp-xml     (cr/strip-xml-namespaces (xml/parse xml_s))
              _ (xml/select (cr/strip-xml-namespaces (xml/parse xml_s)))]
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
              _ (xml/select (cr/strip-xml-namespaces (xml/parse xml_s)))]
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
              _ (xml/select (cr/strip-xml-namespaces (xml/parse xml_s)))]
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
            (xml/get-text lp-xml [:menettelyTOS]) => "tiedonohjausmenettely")))))

  (facts "2.2.4"
    (let [xml_s (-> (common-map-enums suunnittelu-canonical suunnittelu-krysp-path "2.2.4")
                    (element-to-xml mapping/poikkeamis_to_krysp_224)
                    (indent-str))]

      (mapping-to-krysp/save-application-as-krysp
        {:application suunnitelutarveratkaisu :organization {:krysp {:P {:ftpUser "dev_sipoo" :version "2.2.4"}}}} "fi" suunnitelutarveratkaisu)

      (validator/validate xml_s (:permitType suunnitelutarveratkaisu) "2.2.4") ; throws exception

      (facts "Check xml"
        (let [lp-xml     (cr/strip-xml-namespaces (xml/parse xml_s))
              _ (xml/select (cr/strip-xml-namespaces (xml/parse xml_s)))]
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

(defn- change-value-in-canonical
  [canonical path value]
  (if canonical
    (map #(assoc-in % path value) canonical)
    canonical))

(defn- get-element-xml-value
  [canonical krysp-version path krysp-path]
  (-> (common-map-enums canonical krysp-path krysp-version)
      (element-to-xml (get-mapping krysp-version))
      indent-str
      xml/parse
      cr/strip-xml-namespaces
      (xml/get-text path)))

(facts "new enum values in 2.2.4"

  (facts "Suunnittelutarveratkaisu"
    (let [path-rakennuspaikkatieto [:Popast :suunnittelutarveasiatieto :Suunnittelutarveasia :rakennuspaikkatieto]
          path-hallintaperuste [:Rakennuspaikka :rakennuspaikanKiinteistotieto 0 :RakennuspaikanKiinteisto :hallintaperuste]
          suunnittelu-new-enum-values (update-in suunnittelu-canonical path-rakennuspaikkatieto change-value-in-canonical path-hallintaperuste "muu oikeus")
          hallintaperuste [:RakennuspaikanKiinteisto :hallintaperuste]]

      (fact "hallintaperuste 'muu oikeus' doesn't change in 2.2.4"
        (get-element-xml-value suunnittelu-new-enum-values "2.2.4" hallintaperuste suunnittelu-krysp-path)
        => "muu oikeus")

      (fact "hallintaperuste 'muu oikeus' changes to 'ei tiedossa' pre 2.2.4"
        (get-element-xml-value suunnittelu-new-enum-values "2.2.3" hallintaperuste suunnittelu-krysp-path)
        => "ei tiedossa")

      (fact "hallintaperuste 'oma' doesn't change 2.2.4"
        (get-element-xml-value suunnittelu-canonical "2.2.4" hallintaperuste suunnittelu-krysp-path)
        => "oma")

      (fact "hallintaperuste 'oma' doesn't change pre 2.2.4"
        (get-element-xml-value suunnittelu-canonical "2.2.1" hallintaperuste suunnittelu-krysp-path)
        => "oma")))

  (facts "Poikkeamislupa"
    (let [path-rakennuspaikkatieto [:Popast :poikkeamisasiatieto :Poikkeamisasia :rakennuspaikkatieto]
          path-hallintaperuste [:Rakennuspaikka :rakennuspaikanKiinteistotieto 0 :RakennuspaikanKiinteisto :hallintaperuste]
          poikkeus-new-enum-values (update-in poikkeus-canonical path-rakennuspaikkatieto change-value-in-canonical path-hallintaperuste "muu oikeus")
          hallintaperuste [:RakennuspaikanKiinteisto :hallintaperuste]]

      (fact "hallintaperuste is 'muu oikeus' in 2.2.4"
        (get-element-xml-value poikkeus-new-enum-values "2.2.4" hallintaperuste poikkeus-krysp-path)
        => "muu oikeus")

      (fact "hallintaperuste 'muu oikeus' changes to 'ei tiedossa' pre 2.2.4"
        (get-element-xml-value poikkeus-new-enum-values "2.2.1" hallintaperuste poikkeus-krysp-path)
        => "ei tiedossa")

      (fact "hallintaperuste 'oma' doesn't change 2.2.4"
        (get-element-xml-value poikkeus-canonical "2.2.4" hallintaperuste poikkeus-krysp-path)
        => "oma")

      (fact "hallintaperuste 'oma' doesn't change pre 2.2.4"
        (get-element-xml-value poikkeus-canonical "2.2.1" hallintaperuste poikkeus-krysp-path)
        => "oma"))))
