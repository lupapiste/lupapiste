(ns lupapalvelu.backing-system.krysp.maa-aines-canonical-to-krysp-xml-test
  (:require [clojure.data.xml :refer :all]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :refer :all]
            [lupapalvelu.backing-system.krysp.maa-aines-mapping :refer [maa-aines-element-to-xml ]]
            [lupapalvelu.document.maa-aines-canonical :refer [maa-aines-canonical]]
            [lupapalvelu.document.maa-aines-canonical-test :refer [application application-w-henkilo-hakija application-v2]]
            [lupapalvelu.xml.emit :refer :all]
            [lupapalvelu.xml.validator :as validator]
            [midje.sweet :refer :all]
            [sade.common-reader :as cr]
            [sade.xml :as xml]))

(facts "Maa-aineslupa type of permit to canonical and then to xml with schema validation"

       (let [canonical (maa-aines-canonical application "fi")
             xml_212 (maa-aines-element-to-xml canonical "2.1.2")
             xml_221 (maa-aines-element-to-xml canonical "2.2.1")
             xml_212_s     (indent-str xml_212)
             xml_221_s     (indent-str xml_221)
             lp-xml_212    (cr/strip-xml-namespaces (xml/parse xml_212_s))
             lp-xml_221    (cr/strip-xml-namespaces (xml/parse xml_221_s))]

    (validator/validate xml_212_s (:permitType application) "2.1.2") ; throws exception
    (validator/validate xml_221_s (:permitType application) "2.2.1") ; throws exception

    (fact "property id"
      (xml/get-text lp-xml_212 [:kiinteistotunnus])  => (:propertyId application))

    (fact "kuvaus in koontiKentta element"
      (xml/get-text lp-xml_212 [:koontiKentta]) => "Hankkeen synopsis")

    (fact "kuvaus in asianKuvaus element"
      (xml/get-text lp-xml_212 [:asianKuvaus]) => "Hankkeen synopsis")

    (fact "hakija"
      (let [hakija (xml/select1 lp-xml_221 [:hakija])]
        (xml/get-text hakija [:puhelinnumero]) => "060222155"
        (fact "maa" (xml/get-text hakija [:osoitetieto :Osoite :valtioSuomeksi]) => "Suomi")
        (fact "direct marketing"
              (xml/get-text hakija [:suoramarkkinointikielto]) => "false")))

    (fact "maksaja"
      (let [maksaja (xml/select1 lp-xml_221 [:maksajatieto :Maksaja])]
        (xml/get-text maksaja [:puhelinnumero]) => "121212"
        (xml/get-text maksaja [:henkilotunnus]) => "210281-9988"
        (fact "maa" (xml/get-text maksaja [:valtioSuomeksi]) => "Suomi")
        (fact "direct marketing"
              (xml/get-text maksaja [:suoramarkkinointikielto]) => "true")))

    (fact "sijainti"
      (let [sijainti (xml/select1 lp-xml_212 [:sijaintitieto :Sijainti])
            osoite (xml/select1 sijainti :osoite)]

         (xml/get-text osoite [:osoitenimi :teksti]) => "Londb\u00f6lentie 97"
         (xml/get-text sijainti [:yksilointitieto]) => (:id application)
         (xml/get-text sijainti [:piste :Point :pos]) =>  "428195.77099609 6686701.3931274"))

    ))

(facts "test finnish hetu"
  (let [canonical  (maa-aines-canonical application-w-henkilo-hakija "fi")
        xml_224_s  (indent-str (maa-aines-element-to-xml canonical "2.2.4"))
        lp-xml_224 (cr/strip-xml-namespaces (xml/parse xml_224_s))]
    (fact "hakija"
      (let [hakija (xml/select1 lp-xml_224 [:hakija])]
        (fact "sukunimi" (xml/get-text hakija [:sukunimi]) => "Borga")
        (fact "hetu" (xml/get-text hakija [:henkilotunnus]) => "210281-9988")))

    (fact "maksaja"
      (let [maksaja (xml/select1 lp-xml_224 [:maksajatieto :Maksaja])]
        (fact "sukunimi" (xml/get-text maksaja [:sukunimi]) => "Borga")
        (fact "hetu" (xml/get-text maksaja [:henkilotunnus]) => "210281-9988")))))

(facts "test ulkomainen hetu"
  (let [canonical  (maa-aines-canonical application-v2 "fi")
        xml_224_s  (indent-str (maa-aines-element-to-xml canonical "2.2.4"))
        lp-xml_224 (cr/strip-xml-namespaces (xml/parse xml_224_s))]
    (fact "hakija"
      (let [hakija (xml/select1 lp-xml_224 [:hakija])]
        (fact "sukunimi" (xml/get-text hakija [:sukunimi]) => "Borga")
        (fact "hetu" (xml/get-text hakija [:henkilotunnus]) => nil)
        (fact "ulkomainen hetu" (xml/get-text hakija [:ulkomainenHenkilotunnus]) => "hakija-123")))

    (fact "maksaja"
      (let [maksaja (xml/select1 lp-xml_224 [:maksajatieto :Maksaja])]
        (fact "sukunimi" (xml/get-text maksaja [:sukunimi]) => "Borga")
        (fact "hetu" (xml/get-text maksaja [:henkilotunnus]) => nil)
        (fact "ulkomainen hetu" (xml/get-text maksaja [:ulkomainenHenkilotunnus]) => "ymp-maksaja-123")))) )
