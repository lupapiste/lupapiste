(ns lupapalvelu.xml.krysp.ymparistolupa-canonical-to-krysp-xml-test
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.document.ymparistolupa-canonical :refer [ymparistolupa-canonical]]
            [lupapalvelu.document.ymparistolupa-canonical-test :refer [application application-yritysmaksaja]]
            [lupapalvelu.xml.krysp.ymparistolupa-mapping :refer [ymparistolupa_to_krysp]]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.xml.validator :as validator]
            [lupapalvelu.xml.emit :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.data.xml :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :as cr]))

(fact "2.1.2: :tag is set" (has-tag ymparistolupa_to_krysp) => true)

(facts "Ymparistolupa type of permit to canonical and then to xml with schema validation"

  (let [canonical (ymparistolupa-canonical application "fi")
        krysp-xml (element-to-xml canonical ymparistolupa_to_krysp)
        xml-s     (indent-str krysp-xml)
        lp-xml    (cr/strip-xml-namespaces (xml/parse xml-s))]

    (validator/validate xml-s (:permitType application) "2.1.2") ; throws exception

    (fact "kiinteistotunnus"
      (xml/get-text lp-xml [:laitoksentiedot :Laitos :kiinttun]) => (:propertyId application))

    (fact "kuvaus"
      (xml/get-text lp-xml [:toiminta :kuvaus]) => "Hankkeen kuvauskentan sisalto"
      (xml/get-text lp-xml [:toiminta :peruste]) => "Hankkeen peruste")

    (fact "hakijat"
      (let [hakijat (xml/select lp-xml [:hakija])]
       (count hakijat) => 2
       (xml/get-text (first hakijat) [:sukunimi]) => "Borga"
       (xml/get-text (second hakijat) [:yTunnus]) => "1060155-5"))

    (fact "luvat"
      (let [luvat (xml/select lp-xml [:voimassaOlevatLuvat :lupa])]
       (count luvat) => 2
       (xml/get-text (first luvat) [:kuvaus]) => "lupapistetunnus"
       (xml/get-text (second luvat) [:tunnistetieto]) => "kuntalupa-123"))


    (fact "sijainti"
      (let [tiedot-sijainnista (xml/select1 lp-xml [:toiminnanSijaintitieto :ToiminnanSijainti])
            sijainti (xml/select1 tiedot-sijainnista [:sijaintitieto :Sijainti])
            osoite (xml/select1 sijainti :osoite)]

        (xml/get-text tiedot-sijainnista :yksilointitieto) => (:id application)
        (xml/get-text osoite [:osoitenimi :teksti]) => "Londb\u00f6lentie 97"
        (xml/get-text sijainti [:piste :Point :pos]) =>  "428195.77099609 6686701.3931274"))

    (fact "maksaja"
      (let [maksaja (xml/select lp-xml [:maksajatieto :Maksaja])]
        (fact "etunimi" (xml/get-text maksaja [:etunimi]) => "Pappa")
        (fact "sukunimi" (xml/get-text maksaja [:sukunimi]) => "Betalare")
        (fact "laskuviite" (xml/get-text maksaja [:laskuviite]) => "1686343528523")))))

(facts "Ymparistolupa with yritysmaksaja"
  (let [canonical (ymparistolupa-canonical application-yritysmaksaja "fi")
        krysp-xml (element-to-xml canonical ymparistolupa_to_krysp)
        xml-s     (indent-str krysp-xml)
        lp-xml    (cr/strip-xml-namespaces (xml/parse xml-s))]

    ; TODO - other fields could/should be tested as well

    (fact "Verkkolaskutus"
      (let [Verkkolaskutus (xml/select lp-xml [:maksajatieto :Maksaja :Verkkolaskutus])
            test-values {:ovtTunnus         "003712345671"
                         :verkkolaskuTunnus "verkkolaskuTunnus"
                         :valittajaTunnus   "BAWCFI22"}]

        (doseq [[k v] test-values]
          (xml/get-text Verkkolaskutus [k]) => v)))
    ))
