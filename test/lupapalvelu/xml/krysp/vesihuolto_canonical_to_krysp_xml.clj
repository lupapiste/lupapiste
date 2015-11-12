(ns lupapalvelu.xml.krysp.vesihuolto-canonical-to-krysp-xml
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.document.vesihuolto-canonical :refer [vapautus-canonical]]
            [lupapalvelu.document.vesihuolto-canonical-test :refer [vapautus-vesijohdosta-ja-viemarista-hakemus]]
            [lupapalvelu.xml.krysp.vesihuolto-mapping :refer [vesihuolto-to-krysp_213 vesihuolto-to-krysp_221]]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.xml.validator :as validator]
            [lupapalvelu.xml.emit :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.data.xml :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :as cr]))

(fact "2.1.3: :tag is set" (has-tag vesihuolto-to-krysp_213) => true)
(fact "2.2.1: :tag is set" (has-tag vesihuolto-to-krysp_221) => true)

(facts "Vesilupalupa type of permit to canonical and then to xml with schema validation"

  (let [canonical (vapautus-canonical vapautus-vesijohdosta-ja-viemarista-hakemus "fi")
        xml_213 (element-to-xml canonical vesihuolto-to-krysp_213)
        xml_221 (element-to-xml canonical vesihuolto-to-krysp_221)
        xml_213_s     (indent-str xml_213)
        xml_221_s     (indent-str xml_221)
        lp-xml_213    (cr/strip-xml-namespaces (xml/parse xml_213_s))
        lp-xml_221    (cr/strip-xml-namespaces (xml/parse xml_221_s))]

    ;(clojure.pprint/pprint canonical)
    ;(println xml_221_s)

    (validator/validate xml_213_s (:permitType vapautus-vesijohdosta-ja-viemarista-hakemus) "2.1.3")
    (validator/validate xml_221_s (:permitType vapautus-vesijohdosta-ja-viemarista-hakemus) "2.2.1")

    (fact "Hakija"
      (let [hakija (xml/select1 lp-xml_221 [:Vesihuoltolaki :Vapautus :Vapautushakemus :hakija])]
        (xml/get-text hakija [:sukunimi]) => "Borga"
        (fact "maa" (xml/get-text hakija [:osoitetieto :Osoite :valtioKansainvalinen]) => "FIN")))
))
