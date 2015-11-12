(ns lupapalvelu.xml.krysp.maa-aines-canonical-to-xml_212-test
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.document.maa-aines-canonical :refer [maa-aines-canonical]]
            [lupapalvelu.document.maa-aines-canonical-test :refer [application]]
            [lupapalvelu.xml.krysp.maa-aines-mapping :refer [maa-aines-element-to-xml maa-aines_to_krysp_212]]
            [lupapalvelu.xml.validator :as validator]
            [lupapalvelu.xml.emit :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.data.xml :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :as cr]
            ))

(facts "Maa-aineslupa type of permit to canonical and then to xml with schema validation"

  (let [canonical (maa-aines-canonical application "fi")
        xml_212 (maa-aines-element-to-xml canonical "2.1.2")
        xml_212_s     (indent-str xml_212)
        lp-xml_212    (cr/strip-xml-namespaces (xml/parse xml_212_s))]

    ;(clojure.pprint/pprint canonical)
    ;(println xml_212_s)

    (validator/validate xml_212_s (:permitType application) "2.1.2") ; throws exception

    (fact "property id"
      (xml/get-text lp-xml_212 [:kiinteistotunnus])  => (:propertyId application))

    (fact "kuvaus in koontiKentta element"
      (xml/get-text lp-xml_212 [:koontiKentta]) => "Hankkeen synopsis")

    (fact "kuvaus in asianKuvaus element"
      (xml/get-text lp-xml_212 [:asianKuvaus]) => "Hankkeen synopsis")

    (fact "hakija"
      (let [hakija (xml/select1 lp-xml_212 [:hakija])]
        (xml/get-text hakija [:puhelinnumero]) => "060222155"))

    (fact "maksaja"
      (let [maksaja (xml/select1 lp-xml_212 [:maksajatieto])]
       (xml/get-text maksaja [:puhelinnumero]) => "121212"
       (xml/get-text maksaja [:henkilotunnus]) => "210281-9988"))

    (fact "sijainti"
      (let [sijainti (xml/select1 lp-xml_212 [:sijaintitieto :Sijainti])
            osoite (xml/select1 sijainti :osoite)]

         (xml/get-text osoite [:osoitenimi :teksti]) => "Londb\u00f6lentie 97"
         (xml/get-text sijainti [:yksilointitieto]) => (:id application)
         (xml/get-text sijainti [:piste :Point :pos]) =>  "428195.77099609 6686701.3931274"))

    ))
