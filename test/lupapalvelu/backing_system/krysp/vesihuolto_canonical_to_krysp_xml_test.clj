(ns lupapalvelu.backing-system.krysp.vesihuolto-canonical-to-krysp-xml-test
  (:require [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.document.vesihuolto-canonical :refer [vapautus-canonical]]
            [lupapalvelu.document.vesihuolto-canonical-test :refer [vapautus-vesijohdosta-ja-viemarista-hakemus]]
            [lupapalvelu.backing-system.krysp.vesihuolto-mapping :refer [vesihuolto-to-krysp_213 vesihuolto-to-krysp_221]]
            [lupapalvelu.backing-system.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.xml.validator :as validator]
            [lupapalvelu.xml.emit :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.data.xml :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :as cr]))

(testable-privates lupapalvelu.backing-system.krysp.vesihuolto-mapping common-map-enums)

(fact "2.1.3: :tag is set" (has-tag vesihuolto-to-krysp_213) => true)
(fact "2.2.1: :tag is set" (has-tag vesihuolto-to-krysp_221) => true)

(def canonical (vapautus-canonical vapautus-vesijohdosta-ja-viemarista-hakemus "fi"))

(facts "Vesilupalupa type of permit to canonical and then to xml with schema validation"
  (fact "2.1.3"
    (let [xml_213_s (-> (common-map-enums canonical "2.1.3")
                        (element-to-xml vesihuolto-to-krysp_213)
                        (indent-str))
          lp-xml_213    (cr/strip-xml-namespaces (xml/parse xml_213_s))]

      (validator/validate xml_213_s (:permitType vapautus-vesijohdosta-ja-viemarista-hakemus) "2.1.3")))

  (fact "2.2.1"
    (let [xml_221_s (-> (common-map-enums canonical "2.2.1")
                        (element-to-xml vesihuolto-to-krysp_221)
                        (indent-str))
          lp-xml_221    (cr/strip-xml-namespaces (xml/parse xml_221_s))]

      (validator/validate xml_221_s (:permitType vapautus-vesijohdosta-ja-viemarista-hakemus) "2.2.1")

      (fact "Hakija"
        (let [hakija (xml/select1 lp-xml_221 [:Vesihuoltolaki :Vapautus :Vapautushakemus :hakija])]
          (xml/get-text hakija [:sukunimi]) => "Borga"
          (fact "maa" (xml/get-text hakija [:osoitetieto :Osoite :valtioKansainvalinen]) => "FIN"))))))
