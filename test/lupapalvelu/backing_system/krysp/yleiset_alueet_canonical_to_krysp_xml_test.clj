(ns lupapalvelu.backing-system.krysp.yleiset-alueet-canonical-to-krysp-xml-test
  (:require [lupapalvelu.document.yleiset-alueet-canonical :refer [application-to-canonical katselmus-canonical]]
            [lupapalvelu.document.yleiset-alueet-kaivulupa-canonical-test :refer [kaivulupa-application-with-link-permit-data kaivulupa-application-with-review katselmus]]
            [lupapalvelu.document.yleiset-alueet-kayttolupa-canonical-test :refer [kayttolupa-application]]
            [lupapalvelu.document.yleiset-alueet-sijoituslupa-canonical-test :refer [sijoituslupa-application valmistumisilmoitus]]
            [lupapalvelu.document.yleiset-alueet-kayttolupa-mainostus-viitoitus-canonical-test
             :refer [mainostus-application viitoitus-application]]
            [lupapalvelu.xml.emit :refer :all]
            [lupapalvelu.backing-system.krysp.yleiset-alueet-mapping :refer [get-yleiset-alueet-krysp-mapping yleisetalueet-element-to-xml]]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.xml.validator :refer :all :as validator]
            [lupapalvelu.backing-system.krysp.canonical-to-krysp-xml-test-common :refer :all :as xml-test-common]
            [lupapalvelu.document.canonical-common :refer [ya-operation-type-to-schema-name-key]]
            [midje.sweet :refer :all]
            [clojure.data.xml :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :as cr]
            [lupapalvelu.domain :refer [get-document-by-name]]))

(defn- do-test [application]
  (let [operation-name-key (-> application :primaryOperation :name keyword)
        lupa-name-key (ya-operation-type-to-schema-name-key operation-name-key)
        canonical (application-to-canonical application "fi")
        xml-212 (yleisetalueet-element-to-xml canonical lupa-name-key "2.1.2")
        xml-213 (yleisetalueet-element-to-xml canonical lupa-name-key "2.1.3")
        xml-220 (yleisetalueet-element-to-xml canonical lupa-name-key "2.2.0")
        xml-221 (yleisetalueet-element-to-xml canonical lupa-name-key "2.2.1")
        xml-212s (indent-str xml-212)
        xml-213s (indent-str xml-213)
        xml-220s (indent-str xml-220)
        xml-221s (indent-str xml-221)
        lp-xml-212 (cr/strip-xml-namespaces (xml/parse xml-212s))
        lp-xml-213 (cr/strip-xml-namespaces (xml/parse xml-213s))
        lp-xml-220 (cr/strip-xml-namespaces (xml/parse xml-220s))
        _          (cr/strip-xml-namespaces (xml/parse xml-221s))]
    (fact ":tag is set (2.1.2)"
      (xml-test-common/has-tag
        (get-yleiset-alueet-krysp-mapping lupa-name-key "2.1.2")) => true)
    (fact ":tag is set (2.1.3)"
      (xml-test-common/has-tag
        (get-yleiset-alueet-krysp-mapping lupa-name-key "2.1.3")) => true)
    (fact ":tag is set (2.2.0)"
      (xml-test-common/has-tag
        (get-yleiset-alueet-krysp-mapping lupa-name-key "2.2.0")) => true)
    (fact ":tag is set (2.2.1)"
      (xml-test-common/has-tag
        (get-yleiset-alueet-krysp-mapping lupa-name-key "2.2.1")) => true)

    (fact "2.1.2: xml exist" xml-212 => truthy)
    (fact "2.1.3: xml exist" xml-213 => truthy)
    (fact "2.2.0: xml exist" xml-220 => truthy)
    (fact "2.2.1: xml exist" xml-221 => truthy)

    ;; Alla oleva tekee jo validoinnin,
    ;; mutta annetaan olla tuossa alla viela tuo validointi, jottei joku tule ja riko olemassa olevaa validointia.
    (mapping-to-krysp/save-application-as-krysp
      {:application application :organization {:krysp {:YA {:ftpUser "dev_sipoo" :version "2.1.2"}}}}
      "fi" application)
    (mapping-to-krysp/save-application-as-krysp
      {:application application :organization {:krysp {:YA {:ftpUser "dev_sipoo" :version "2.1.3"}}}}
      "fi" application)
    (mapping-to-krysp/save-application-as-krysp
      {:application application :organization {:krysp {:YA {:ftpUser "dev_sipoo" :version "2.2.0"}}}}
      "fi" application)
    (mapping-to-krysp/save-application-as-krysp
      {:application application :organization {:krysp {:YA {:ftpUser "dev_sipoo" :version "2.2.1"}}}}
      "fi" application)

    (validator/validate xml-212s (:permitType application) "2.1.2")
    (validator/validate xml-213s (:permitType application) "2.1.3")
    (validator/validate xml-220s (:permitType application) "2.2.0")
    (validator/validate xml-221s (:permitType application) "2.2.1")

    (fact "LP-tunnus"
      (xml/get-text lp-xml-212 [:MuuTunnus :tunnus]) => (:id application)
      (xml/get-text lp-xml-213 [:MuuTunnus :sovellus]) => "Lupapiste")

    (when (get-document-by-name application :yleiset-alueet-maksaja)
      (fact "Verkkolaskutus fields are present in xml under yleiset-alueet-maksaja"
        (let [Verkkolaskutus (xml/select lp-xml-213 [:verkkolaskutustieto :Verkkolaskutus])]
          (xml/get-text Verkkolaskutus [:ovtTunnus]) => "003712345671"
          (xml/get-text Verkkolaskutus [:verkkolaskuTunnus]) => "laskutunnus-1234"
          (xml/get-text Verkkolaskutus [:valittajaTunnus]) => "BAWCFI22")))

    (when (:linkPermitData application)
      (fact "Link permit"
        (let [muut-tunnukset (xml/select lp-xml-213 [:MuuTunnus])
              link-permit (second muut-tunnukset)]
          (xml/get-text link-permit [:tunnus]) => (-> application :linkPermitData first :id)
          (xml/get-text link-permit [:sovellus]) => "Viitelupa")))

    ;; In YA krysp 2.2.0 (Yht 2.1.5) has more keys defined for drawings.
    (let [drawing-sijainti (map cr/all-of (xml/select lp-xml-220 [:sijaintitieto :Sijainti]))
          Sijainti-piste (-> drawing-sijainti first :piste :Point :pos) => truthy
          PisteSijanti   (second drawing-sijainti) => truthy
          LineString1    (nth drawing-sijainti 2) => truthy
          LineString2    (nth drawing-sijainti 3) => truthy
          Alue           (nth drawing-sijainti 4) => truthy]

      (fact "Sijainti-piste-xy" Sijainti-piste => (str (-> application :location first) " " (-> application :location second)))
      (fact "Point drawing" PisteSijanti => {:nimi "Piste"
                                             :kuvaus "Piste jutska"
                                             :korkeusTaiSyvyys "345"
                                             :piste {:Point {:pos "530851.15649413 6972373.1564941"}}})
      (fact "LineString drawing 1" LineString1 => {:nimi "alue"
                                                   :kuvaus "alue 1"
                                                   :korkeusTaiSyvyys "1000"
                                                   :viiva {:LineString {:pos '("530856.65649413 6972312.1564941"
                                                                               "530906.40649413 6972355.6564941"
                                                                               "530895.65649413 6972366.9064941"
                                                                               "530851.15649413 6972325.9064941"
                                                                               "530856.65649413 6972312.4064941")}}})
      (fact "LineString drawing 2" LineString2 => {:nimi "Viiva"
                                                   :kuvaus "Viiiva"
                                                   :korkeusTaiSyvyys "111"
                                                   :viiva {:LineString {:pos '("530825.15649413 6972348.9064941"
                                                                               "530883.65649413 6972370.1564941"
                                                                               "530847.65649413 6972339.4064941"
                                                                               "530824.90649413 6972342.4064941")}}})
      (fact "Area drawing" Alue => {:nimi "Alueen nimi"
                                    :kuvaus "Alueen kuvaus"
                                    :korkeusTaiSyvyys "333"
                                    :pintaAla "402"
                                    :alue {:Polygon {:exterior {:LinearRing {:pos '("530859.15649413 6972389.4064941"
                                                                                    "530836.40649413 6972367.4064941"
                                                                                    "530878.40649413 6972372.6564941"
                                                                                    "530859.15649413 6972389.4064941")}}}}}))))

(facts "YA permits to canonical and then to xml with schema validation"

    (fact "Kaivulupa application -> canonical -> xml"
      (do-test kaivulupa-application-with-link-permit-data))

    (fact "Kayttolupa application -> canonical -> xml"
      (do-test kayttolupa-application))

    (fact "Sijoituslupa application -> canonical -> xml"
      (do-test sijoituslupa-application))

    (fact "Mainostuslupa application -> canonical -> xml"
      (do-test mainostus-application))

    (fact "Viitoituslupa application -> canonical -> xml"
      (do-test viitoitus-application))

    (fact "Valmistumisilmoitus application -> canonical -> xml"
      (do-test valmistumisilmoitus)))


(fact "YA katselmus"
  (let [application kaivulupa-application-with-review
        canonical (katselmus-canonical application katselmus "fi")
        operation-name-key (-> application :primaryOperation :name keyword)
        lupa-name-key (ya-operation-type-to-schema-name-key operation-name-key)

        xml-212 (yleisetalueet-element-to-xml canonical lupa-name-key "2.1.2")
        xml-212s (indent-str xml-212)
        _ (cr/strip-xml-namespaces (xml/parse xml-212s))

        xml-221 (yleisetalueet-element-to-xml canonical lupa-name-key "2.2.1")
        xml-221s (indent-str xml-221)
        lp-xml-221 (cr/strip-xml-namespaces (xml/parse xml-221s))

        katselmus (xml/select1 lp-xml-221 [:katselmustieto :Katselmus])
        huomautus (xml/select1 katselmus [:Huomautus])]

    (validator/validate xml-212s (:permitType application) "2.1.2")
    (validator/validate xml-221s (:permitType application) "2.2.1")

    (fact "pitaja" (xml/get-text katselmus [:pitaja]) => "Viranomaisen nimi")
    (fact "pitoPvm" (xml/get-text katselmus [:pitoPvm]) => "1974-05-01")
    (fact "katselmuksenLaji" (xml/get-text katselmus  [:katselmuksenLaji]) => "Muu valvontak\u00e4ynti")
    (fact "vaadittuLupaehtonaKytkin" (xml/get-text katselmus [:vaadittuLupaehtonaKytkin]) => "true")
    (facts "huomautus"
      (fact "kuvaus" (xml/get-text huomautus [:kuvaus]) => "huomautus - kuvaus")
      (fact "maaraAika" (xml/get-text huomautus [:maaraAika]) => "1974-06-02" )
      (fact "toteamisHetki" (xml/get-text huomautus [:toteamisHetki]) => "1974-05-02")
      (fact "toteaja" (xml/get-text huomautus [:toteaja]) => "huomautus - viranomaisen nimi"))
    (fact "katselmuspoytakirja" (xml/get-text katselmus [:katselmuspoytakirja]) => nil)
    (fact "tarkastuksenTaiKatselmuksenNimi" (xml/get-text katselmus [:tarkastuksenTaiKatselmuksenNimi]) => "testikatselmus")
    (fact "lasnaolijat" (xml/get-text katselmus [:lasnaolijat]) => "paikallaolijat")
    (fact "poikkeamat" (xml/get-text katselmus [:poikkeamat]) => "jotain poikkeamia oli")
    )

  )
