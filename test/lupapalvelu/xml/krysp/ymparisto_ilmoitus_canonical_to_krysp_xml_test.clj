(ns lupapalvelu.xml.krysp.ymparisto-ilmoitus-canonical-to-krysp-xml-test
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.document.ymparisto-ilmoitukset-canonical :refer [meluilmoitus-canonical]]
            [lupapalvelu.document.ymparisto-ilmoitukset-canonical-test :refer [meluilmoitus-yritys-application ]]
            [lupapalvelu.xml.krysp.ymparisto-ilmoitukset-mapping :refer [ilmoitus_to_krysp]]
            ;[lupapalvelu.document.validators :refer [dummy-doc]]
            [lupapalvelu.xml.krysp.validator :refer [validate]]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.xml.krysp.validator :refer :all :as validator]
            [lupapalvelu.xml.emit :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.data.xml :refer :all]
            [clojure.java.io :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :as cr]))

(fact "2.1.2: :tag is set" (has-tag ilmoitus_to_krysp) => true)

(defn- do-test [application]
  (let [canonical (meluilmoitus-canonical application "fi")
        xml (element-to-xml canonical ilmoitus_to_krysp)
        xml-s     (indent-str xml)
        lp-xml    (cr/strip-xml-namespaces (xml/parse xml-s))]

    ;(clojure.pprint/pprint canonical)
    ;(println xml-s)
    ; Alla oleva tekee jo validoinnin, mutta annetaan olla tuossa alla viela validointi, jottei tule joku riko olemassa olevaa validointia
    (mapping-to-krysp/save-application-as-krysp application "fi" application {:krysp {:YI {:ftpUser "dev_sipoo" :version "2.1.2"}}})
    (validator/validate (indent-str xml) (:permitType application) "2.1.2")

    (fact "toiminnan kesto"
      (xml/get-text lp-xml [:Melutarina :toiminnanKesto :alkuPvm]) => "2014-02-03"
      (xml/get-text lp-xml [:Melutarina :toiminnanKesto :loppuPvm]) => "2014-02-07"
      (xml/get-text lp-xml [:Melutarina :toiminnanKesto :arkiAlkuAika]) => nil
      (xml/get-text lp-xml [:Melutarina :toiminnanKesto :arkiLoppuAika]) => nil
      (xml/get-text lp-xml [:Melutarina :toiminnanKesto :lauantaiAlkuAika]) => nil
      (xml/get-text lp-xml [:Melutarina :toiminnanKesto :lauantaiLoppuAika]) => nil
      (xml/get-text lp-xml [:Melutarina :toiminnanKesto :sunnuntaiAlkuAika]) => nil
      (xml/get-text lp-xml [:Melutarina :toiminnanKesto :sunnuntaiLoppuAika]) => nil

      )
    ))


(facts "Meluilmoitus type of permits to canonical and then to xml with schema validation"

  (fact "Meluilmoitus yritys -> canonical -> xml"
    (do-test meluilmoitus-yritys-application)))




