(ns lupapalvelu.backing-system.krysp.ymparistolupa-canonical-to-krysp-xml-test
  (:require [clojure.data.xml :refer :all]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :refer :all]
            [lupapalvelu.backing-system.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.backing-system.krysp.ymparistolupa-mapping :refer [ymparistolupa-element-to-xml ymparistolupa_to_krysp_212 ymparistolupa_to_krysp_221 ymparistolupa_to_krysp_223 ymparistolupa_to_krysp_224]]
            [lupapalvelu.document.ymparistolupa-canonical :refer [ymparistolupa-canonical]]
            [lupapalvelu.document.ymparistolupa-canonical-test :refer [application application-v2 application-yritysmaksaja]]
            [lupapalvelu.xml.validator :as validator]
            [midje.sweet :refer :all]
            [sade.common-reader :as cr]
            [sade.xml :as xml]))

(fact "2.1.2: :tag is set" (has-tag ymparistolupa_to_krysp_212) => true)
(fact "2.2.1: :tag is set" (has-tag ymparistolupa_to_krysp_221) => true)
(fact "2.2.3: :tag is set" (has-tag ymparistolupa_to_krysp_223) => true)
(fact "2.2.4: :tag is set" (has-tag ymparistolupa_to_krysp_224) => true)

(facts "Ymparistolupa type of permit to canonical and then to xml with schema validation"

  (let [canonical (ymparistolupa-canonical application "fi")
        xml_212   (ymparistolupa-element-to-xml canonical "2.1.2")
        xml_221   (ymparistolupa-element-to-xml canonical "2.2.1")
        xml_212_s     (indent-str xml_212)
        xml_221_s     (indent-str xml_221)
        lp-xml_212    (cr/strip-xml-namespaces (xml/parse xml_212_s))
        lp-xml_221    (cr/strip-xml-namespaces (xml/parse xml_221_s))]
    (validator/validate xml_212_s (:permitType application) "2.1.2") ; throws exception
    (validator/validate xml_221_s (:permitType application) "2.2.1") ; throws exception

    (fact "kiinteistotunnus"
      (xml/get-text lp-xml_212 [:laitoksentiedot :Laitos :kiinttun]) => (:propertyId application))

    (fact "kuvaus"
      (xml/get-text lp-xml_212 [:toiminta :kuvaus]) => "Hankkeen kuvauskentan sisalto"
      (xml/get-text lp-xml_212 [:toiminta :peruste]) => "Hankkeen peruste")

    (fact "hakijat"
      (let [hakijat (xml/select lp-xml_221 [:hakija])]
       (count hakijat) => 2
       (xml/get-text (first hakijat) [:sukunimi]) => "Borga"
       (xml/get-text (second hakijat) [:yTunnus]) => "1060155-5"

       (fact "maa"
         (xml/get-text (first hakijat) [:osoitetieto :Osoite :valtioKansainvalinen]) => "FIN")))

    (fact "luvat"
      (let [luvat (xml/select lp-xml_212 [:voimassaOlevatLuvat :lupa])]
       (count luvat) => 2
       (xml/get-text (first luvat) [:kuvaus]) => "lupapistetunnus"
       (xml/get-text (second luvat) [:tunnistetieto]) => "kuntalupa-123"))

    (fact "sijainti"
      (let [tiedot-sijainnista (xml/select1 lp-xml_212 [:toiminnanSijaintitieto :ToiminnanSijainti])
            sijainti (xml/select1 tiedot-sijainnista [:sijaintitieto :Sijainti])
            osoite (xml/select1 sijainti :osoite)]
        (xml/get-text tiedot-sijainnista :yksilointitieto) => (:id application)
        (xml/get-text osoite [:osoitenimi :teksti]) => "Londb\u00f6lentie 97"
        (xml/get-text sijainti [:piste :Point :pos]) =>  "428195.77099609 6686701.3931274"))

    (fact "maksaja"
      (let [maksaja (xml/select lp-xml_221 [:maksajatieto :Maksaja])]
        (fact "etunimi" (xml/get-text maksaja [:etunimi]) => "Pappa")
        (fact "sukunimi" (xml/get-text maksaja [:sukunimi]) => "Betalare")
        (fact "laskuviite" (xml/get-text maksaja [:laskuviite]) => "1686343528523")
        (fact "maa"
          (xml/get-text maksaja [:osoitetieto :Osoite :valtioKansainvalinen]) => "FIN")))))

(facts "case where finnish hetu is used"
  (let [canonical  (ymparistolupa-canonical application "fi")
        xml_224_s  (indent-str (ymparistolupa-element-to-xml canonical "2.2.4"))
        lp-xml_224 (cr/strip-xml-namespaces (xml/parse xml_224_s))]
    (validator/validate xml_224_s (:permitType application) "2.2.4")

    (facts "hakija has correct info"
      (let [hakija-henkilo (-> lp-xml_224 (xml/select [:hakija]) first)]
        (fact "sukunimi" (xml/get-text hakija-henkilo [:sukunimi]) => "Borga")
        (fact "hetu" (xml/get-text hakija-henkilo [:henkilotunnus]) => "210281-9988")))

    (facts "maksaja has correct info"
      (let [maksaja (xml/select lp-xml_224 [:maksajatieto :Maksaja])]
        (fact "sukunimi" (xml/get-text maksaja [:sukunimi]) => "Betalare")
        (fact "hetu" (xml/get-text maksaja [:henkilotunnus]) => "210354-947E")))))

(facts "case where ulkomainen hetu is used"
  (let [canonical  (ymparistolupa-canonical application-v2 "fi")
        xml_224_s  (indent-str (ymparistolupa-element-to-xml canonical "2.2.4"))
        lp-xml_224 (cr/strip-xml-namespaces (xml/parse xml_224_s))]
    (validator/validate xml_224_s (:permitType application-v2) "2.2.4")

    (facts "hakija has correct info"
      (let [hakija-henkilo (-> lp-xml_224 (xml/select [:hakija]) first)]
        (fact "sukunimi" (xml/get-text hakija-henkilo [:sukunimi]) => "Borga")
        (fact "hetu" (xml/get-text hakija-henkilo [:henkilotunnus]) => nil)
        (fact "ulkomainen hetu" (xml/get-text hakija-henkilo [:ulkomainenHenkilotunnus]) => "hakija-123")))

    (facts "maksaja has correct info"
      (let [maksaja (xml/select lp-xml_224 [:maksajatieto :Maksaja])]
        (fact "sukunimi" (xml/get-text maksaja [:sukunimi]) => "Betalare")
        (fact "hetu" (xml/get-text maksaja [:henkilotunnus]) => nil)
        (fact "ulkomainen hetu" (xml/get-text maksaja [:ulkomainenHenkilotunnus]) => "ymp-maksaja-123")))))

(facts "Ymparistolupa with yritysmaksaja"
  (let [canonical (ymparistolupa-canonical application-yritysmaksaja "fi")
        xml_212   (ymparistolupa-element-to-xml canonical "2.1.2")
        xml_221   (ymparistolupa-element-to-xml canonical "2.2.1")
        xml_212_s     (indent-str xml_212)
        xml_221_s     (indent-str xml_221)
        lp-xml_212    (cr/strip-xml-namespaces (xml/parse xml_212_s))
        _    (cr/strip-xml-namespaces (xml/parse xml_221_s))]

    ; TODO - other fields could/should be tested as well

    (fact "Verkkolaskutus"
      (let [Verkkolaskutus (xml/select lp-xml_212 [:maksajatieto :Maksaja :Verkkolaskutus])
            test-values {:ovtTunnus         "003712345671"
                         :verkkolaskuTunnus "verkkolaskuTunnus"
                         :valittajaTunnus   "BAWCFI22"}]
        (doseq [[k v] test-values]
          (xml/get-text Verkkolaskutus [k]) => v)))))
