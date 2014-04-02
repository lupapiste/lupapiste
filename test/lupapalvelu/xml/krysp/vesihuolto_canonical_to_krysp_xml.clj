(ns lupapalvelu.xml.krysp.vesihuolto-canonical-to-krysp-xml
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.document.vesihuolto-canonical :refer [vapautus-canonical]]
            [lupapalvelu.document.vesihuolto-canonical-test :refer [vapautus-vesijohdosta-ja-viemarista-hakemus]]
            [lupapalvelu.xml.krysp.vesihuolto-mapping :refer [vesihuolto-to-krysp]]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.xml.krysp.validator :as validator]
            [lupapalvelu.xml.emit :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.data.xml :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :as cr]))

(fact "2.1.3: :tag is set" (has-tag vesihuolto-to-krysp) => true)

(facts "Vesilupalupa type of permit to canonical and then to xml with schema validation"

       (let [canonical (vapautus-canonical vapautus-vesijohdosta-ja-viemarista-hakemus "fi")
             krysp-xml (element-to-xml canonical vesihuolto-to-krysp)
             xml-s     (indent-str krysp-xml)
             lp-xml    (cr/strip-xml-namespaces (xml/parse xml-s))]

         ;(clojure.pprint/pprint canonical)
         ;(println xml-s)

         (validator/validate xml-s (:permitType vapautus-vesijohdosta-ja-viemarista-hakemus) "2.1.3")))