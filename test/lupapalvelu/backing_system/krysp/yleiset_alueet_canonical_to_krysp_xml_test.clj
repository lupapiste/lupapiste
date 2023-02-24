(ns lupapalvelu.backing-system.krysp.yleiset-alueet-canonical-to-krysp-xml-test
  (:require [clojure.data.xml :refer :all]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.backing-system.krysp.canonical-to-krysp-xml-test-common :refer :all :as xml-test-common]
            [lupapalvelu.backing-system.krysp.yleiset-alueet-mapping :refer [yleisetalueet-element-to-xml]]
            [lupapalvelu.document.canonical-common :refer [ya-operation-type-to-schema-name-key]]
            [lupapalvelu.document.yleiset-alueet-canonical :refer [application-to-canonical katselmus-canonical]]
            [lupapalvelu.document.yleiset-alueet-canonical-test-common :refer [hakija-using-finnish-hetu hakija-using-ulkomainen-hetu]]
            [lupapalvelu.document.yleiset-alueet-kaivulupa-canonical-test :refer [kaivulupa-application-with-link-permit-data kaivulupa-application-with-review katselmus]]
            [lupapalvelu.document.yleiset-alueet-kayttolupa-canonical-test :refer [kayttolupa-application]]
            [lupapalvelu.document.yleiset-alueet-kayttolupa-mainostus-viitoitus-canonical-test :refer [mainostus-application viitoitus-application]]
            [lupapalvelu.document.yleiset-alueet-sijoituslupa-canonical-test :refer [sijoituslupa-application sijoituslupa-application-with-extras valmistumisilmoitus]]
            [lupapalvelu.domain :refer [get-document-by-name]]
            [lupapalvelu.xml.emit :refer :all]
            [lupapalvelu.xml.validator :refer :all :as validator]
            [midje.sweet :refer :all]
            [sade.common-reader :as cr]
            [sade.date :as date]
            [sade.xml :as xml]))

(defn- test-application-against-kuntagml-version [application version-str]
  (facts {:midje/description (format "Version %s" version-str)}
    (let [operation-name-key  (-> application :primaryOperation :name keyword)
          lupa-name-key       (ya-operation-type-to-schema-name-key operation-name-key)
          canonical           (application-to-canonical application "fi")
          xml                 (yleisetalueet-element-to-xml canonical lupa-name-key version-str)
          xml-s               (indent-str xml)
          lp-xml              (cr/strip-xml-namespaces (xml/parse xml-s))]

      (fact {:midje/description (format "xml exist" version-str)} xml => truthy)

      ;; Alla oleva tekee jo validoinnin,
      ;; mutta annetaan olla tuossa alla viela tuo validointi, jottei joku tule ja riko olemassa olevaa validointia.

      (fact "KuntaGML generation succeeds"
        (mapping-to-krysp/save-application-as-krysp
          {:application application :organization {:krysp {:YA {:ftpUser "dev_sipoo" :version version-str}}}}
          "fi" application) => empty?
        (provided (lupapalvelu.organization/get-application-organization anything) => {}))

      (validator/validate xml-s (:permitType application) version-str)

      (fact "LP-tunnus"
            (xml/get-text lp-xml [:MuuTunnus :tunnus]) => (:id application)
            (xml/get-text lp-xml [:MuuTunnus :sovellus]) => "Lupapiste")


      (when (= "2.1.3" version-str)
        (when (get-document-by-name application :yleiset-alueet-maksaja)
          (fact "Verkkolaskutus fields are present in xml under yleiset-alueet-maksaja"
                (let [Verkkolaskutus (xml/select lp-xml [:verkkolaskutustieto :Verkkolaskutus])]
                  (xml/get-text Verkkolaskutus [:ovtTunnus]) => "003712345671"
                  (xml/get-text Verkkolaskutus [:verkkolaskuTunnus]) => "laskutunnus-1234"
                  (xml/get-text Verkkolaskutus [:valittajaTunnus]) => "BAWCFI22")))

        (when (:linkPermitData application)
          (fact "Link permit"
                (let [muut-tunnukset (xml/select lp-xml [:MuuTunnus])
                      link-permit (second muut-tunnukset)]
                  (xml/get-text link-permit [:tunnus]) => (-> application :linkPermitData first :id)
                  (xml/get-text link-permit [:sovellus]) => "Viitelupa"))))

      (when (= "2.2.0" version-str)
        (let [drawing-sijainti (map cr/all-of (xml/select lp-xml [:sijaintitieto :Sijainti]))
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
                                                                                        "530859.15649413 6972389.4064941")}}}}}))))))

(defn- do-test [application]
  (test-application-against-kuntagml-version application "2.1.2")
  (test-application-against-kuntagml-version application "2.1.3")
  (test-application-against-kuntagml-version application "2.2.0")
  (test-application-against-kuntagml-version application "2.2.1")
  (test-application-against-kuntagml-version application "2.2.3")
  (test-application-against-kuntagml-version application "2.2.4"))

(facts "YA permits to canonical and then to xml with schema validation"

  (fact "Kaivulupa application -> canonical -> xml"
    (do-test kaivulupa-application-with-link-permit-data))

  (fact "Kayttolupa application -> canonical -> xml"
    (do-test kayttolupa-application))

  (fact "Sijoituslupa application -> canonical -> xml"
    (do-test sijoituslupa-application))

  (fact "Sijoituslupa application with extras -> canonical -> xml"
    (do-test sijoituslupa-application-with-extras))

  (fact "Mainostuslupa application -> canonical -> xml"
    (do-test mainostus-application))

  (fact "Viitoituslupa application -> canonical -> xml"
    (do-test viitoitus-application))

  (fact "Valmistumisilmoitus application -> canonical -> xml"
    (do-test valmistumisilmoitus)))

(defn get-osapuolet [app krysp-version]
  (let [operation-name-key (-> app :primaryOperation :name keyword)
        lupa-name-key (ya-operation-type-to-schema-name-key operation-name-key)
        osapuolet (-> (application-to-canonical app "fi")
                      (yleisetalueet-element-to-xml lupa-name-key krysp-version)
                      (indent-str)
                      (xml/parse)
                      (cr/strip-xml-namespaces)
                      (xml/select [:osapuolitieto :Osapuoli]))
        hakija (first
                 (filter #(= "hakija" (-> % xml/select cr/all-of :rooliKoodi)) osapuolet))]
    {:henkilotunnus (xml/get-text hakija [:henkilotieto :Henkilo :henkilotunnus])
     :ulkomainenHenkilotunnus (xml/get-text hakija [:henkilotieto :Henkilo :ulkomainenHenkilotunnus])}))

(facts "ulkomainen hetu/hetu are mapped correctly"
  (let [documents-without-hakja (filter #(not= "hakija-ya" (-> % :schema-info :name)) (:documents sijoituslupa-application))
        documents-with-hakija-using-ulkomainen-hetu (into [hakija-using-ulkomainen-hetu] documents-without-hakja)
        documents-with-hakija-using-finnish-hetu (into [hakija-using-finnish-hetu] documents-without-hakja)
        documents-with-hakija-only-using-finnish-hetu (into [(assoc-in hakija-using-finnish-hetu [:data :_selected] {:modified 1379405016674, :value "henkilo"})]
                                                            documents-without-hakja)]
    (fact "2.2.4 hakija using ulkomainen hetu"
      (get-osapuolet (assoc sijoituslupa-application :documents documents-with-hakija-using-ulkomainen-hetu) "2.2.4")
      => {:henkilotunnus nil :ulkomainenHenkilotunnus "12345"})
    (fact "2.2.3 hakija using ulkomainen hetu"
      (get-osapuolet (assoc sijoituslupa-application :documents documents-with-hakija-using-ulkomainen-hetu) "2.2.3")
      => {:henkilotunnus nil :ulkomainenHenkilotunnus nil})
    (fact "2.2.4 hakija using finnish hetu"
      (get-osapuolet (assoc sijoituslupa-application :documents documents-with-hakija-using-finnish-hetu) "2.2.4")
      => {:henkilotunnus "010203-040A" :ulkomainenHenkilotunnus nil})
    (fact "2.2.3 hakija using finnish hetu"
      (get-osapuolet (assoc sijoituslupa-application :documents documents-with-hakija-using-finnish-hetu) "2.2.3")
      => {:henkilotunnus "010203-040A" :ulkomainenHenkilotunnus nil})
    (fact "2.2.4 test case where not-finnish-hetu and ulkomainenHetu does not have values but hetu has value"
      (get-osapuolet (assoc sijoituslupa-application :documents documents-with-hakija-only-using-finnish-hetu) "2.2.4")
      => {:henkilotunnus "010203-040A" :ulkomainenHenkilotunnus nil})
    (fact "2.2.3 test case where not-finnish-hetu and ulkomainenHetu does not have values but hetu has value"
      (get-osapuolet (assoc sijoituslupa-application :documents documents-with-hakija-only-using-finnish-hetu) "2.2.3")
      => {:henkilotunnus "010203-040A" :ulkomainenHenkilotunnus nil})))


(fact "YA katselmus"
  (let [application kaivulupa-application-with-review
        canonical (katselmus-canonical application katselmus "fi")
        operation-name-key (-> application :primaryOperation :name keyword)
        lupa-name-key (ya-operation-type-to-schema-name-key operation-name-key)

        xml-212 (yleisetalueet-element-to-xml canonical lupa-name-key "2.1.2")
        xml-221 (yleisetalueet-element-to-xml canonical lupa-name-key "2.2.1")
        xml-212s (indent-str xml-212)
        xml-221s (indent-str xml-221)
        _ (cr/strip-xml-namespaces (xml/parse xml-212s))
        lp-xml-221 (cr/strip-xml-namespaces (xml/parse xml-221s))

        katselmus (xml/select1 lp-xml-221 [:katselmustieto :Katselmus])
        huomautus (xml/select1 katselmus [:Huomautus])]

    (validator/validate xml-212s (:permitType application) "2.1.2")
    (validator/validate xml-221s (:permitType application) "2.2.1")

    (fact "pitaja" (xml/get-text katselmus [:pitaja]) => "Viranomaisen nimi")
    (fact "pitoPvm" (xml/get-text katselmus [:pitoPvm]) => (date/xml-date "1974-05-01"))
    (fact "katselmuksenLaji" (xml/get-text katselmus  [:katselmuksenLaji]) => "Muu valvontak\u00e4ynti")
    (fact "vaadittuLupaehtonaKytkin" (xml/get-text katselmus [:vaadittuLupaehtonaKytkin]) => "true")
    (facts "huomautus"
      (fact "kuvaus" (xml/get-text huomautus [:kuvaus]) => "huomautus - kuvaus")
      (fact "maaraAika" (xml/get-text huomautus [:maaraAika]) => (date/xml-date "1974-06-02") )
      (fact "toteamisHetki" (xml/get-text huomautus [:toteamisHetki]) => (date/xml-date "1974-05-02"))
      (fact "toteaja" (xml/get-text huomautus [:toteaja]) => "huomautus - viranomaisen nimi"))
    (fact "katselmuspoytakirja" (xml/get-text katselmus [:katselmuspoytakirja]) => nil)
    (fact "tarkastuksenTaiKatselmuksenNimi" (xml/get-text katselmus [:tarkastuksenTaiKatselmuksenNimi]) => "testikatselmus")
    (fact "lasnaolijat" (xml/get-text katselmus [:lasnaolijat]) => "paikallaolijat")
    (fact "poikkeamat" (xml/get-text katselmus [:poikkeamat]) => "jotain poikkeamia oli")))

(facts "Sijoituslupa with extras"
  (let [operation-name-key (-> sijoituslupa-application-with-extras :primaryOperation :name keyword)
        lupa-name-key      (ya-operation-type-to-schema-name-key operation-name-key)
        xml                (-> (application-to-canonical sijoituslupa-application-with-extras "fi")
                               (yleisetalueet-element-to-xml lupa-name-key "2.2.1")
                               indent-str
                               xml/parse
                               cr/strip-xml-namespaces)
        contents           (fn [selector]
                             (map xml/get-text (xml/select xml selector)))]
    (fact "vastuuhenkilotieto"
      (contents [:vastuuhenkilotieto :rooliKoodi])
      => (just ["lupaehdoista/työmaasta vastaava henkilö" "maksajan vastuuhenkilö"]
               :in-any-order))
    (fact "Party role codes"
      (contents [:Osapuoli :rooliKoodi])
      => (just ["hakija" "hakija" "yhteyshenkilö" "työnsuorittaja"] :in-any-order))))
