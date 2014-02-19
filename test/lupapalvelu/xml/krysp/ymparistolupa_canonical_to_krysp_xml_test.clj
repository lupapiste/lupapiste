(ns lupapalvelu.xml.krysp.ymparistolupa-canonical-to-krysp-xml-test
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.document.ymparistolupa-canonical :refer [ymparistolupa-canonical]]
            [lupapalvelu.document.ymparistolupa-canonical-test :refer [application]]
            [lupapalvelu.xml.krysp.ymparistolupa-mapping :refer [ymparistolupa_to_krysp]]
            ;[lupapalvelu.document.validators :refer [dummy-doc]]
            ;[lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.xml.krysp.validator :as validator]
            [lupapalvelu.xml.emit :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.data.xml :refer :all]
            [sade.xml :as xml]
            ;[sade.util :as util]
            [sade.common-reader :as cr]
            ;[clojure.java.io :refer :all]
            ))

(facts "Ymparistolupa type of permits to canonical and then to xml with schema validation"

  (fact "Ymparistolupa yritys -> canonical -> xml"
    (let [canonical (ymparistolupa-canonical application "fi")
          krysp-xml (element-to-xml canonical ymparistolupa_to_krysp)
          xml-s     (indent-str krysp-xml)
          lp-xml    (cr/strip-xml-namespaces (xml/parse xml-s))]

      ;(println xml-s)

      (validator/validate xml-s (:permitType application) "2.1.1") ; throws exception

      (xml/get-text lp-xml [:luvanTunnistetiedot :LupaTunnus :tunnus]) => (:id application)

      (xml/get-text lp-xml [:toiminta :kuvaus]) => "Hankkeen kuvauskentan sisalto"
      (xml/get-text lp-xml [:toiminta :peruste]) => "Hankkeen peruste"

      (let [hakijat (xml/select lp-xml [:hakija])]
        (count hakijat) => 2
        (xml/get-text (first hakijat) [:sukunimi]) => "Borga"
        (xml/get-text (second hakijat) [:liikeJaYhteisotunnus]) => "1060155-5")

      (let [luvat (xml/select lp-xml [:voimassaOlevatLuvat :lupa])]
        (count luvat) => 2
        (xml/get-text (first luvat) [:kuvaus]) => "lupapistetunnus"
        (xml/get-text (second luvat) [:tunnistetieto]) => "kuntalupa-123"))))

