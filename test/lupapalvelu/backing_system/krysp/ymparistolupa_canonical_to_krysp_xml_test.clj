(ns lupapalvelu.backing-system.krysp.ymparistolupa-canonical-to-krysp-xml-test
  (:require [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :refer :all]
            [lupapalvelu.document.ymparistolupa-canonical :refer [ymparistolupa-canonical]]
            [lupapalvelu.document.ymparistolupa-canonical-test :refer [application application-yritysmaksaja]]
            [lupapalvelu.backing-system.krysp.ymparistolupa-mapping :refer [ymparistolupa-element-to-xml ymparistolupa_to_krysp_212 ymparistolupa_to_krysp_221]]
            [lupapalvelu.backing-system.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.xml.validator :as validator]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.data.xml :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :as cr]))

(fact "2.1.2: :tag is set" (has-tag ymparistolupa_to_krysp_212) => true)
(fact "2.2.1: :tag is set" (has-tag ymparistolupa_to_krysp_221) => true)

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
