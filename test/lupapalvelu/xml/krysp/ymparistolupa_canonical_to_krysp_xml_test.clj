(ns lupapalvelu.xml.krysp.ymparistolupa-canonical-to-krysp-xml-test
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.document.ymparistolupa-canonical :refer [ymparistolupa-canonical]]
            ;[lupapalvelu.document.ymparisto-ilmoitukset-canonical-test :refer [Ymparistolupa-yritys-application ]]
            [lupapalvelu.xml.krysp.ymparistolupa-mapping :refer [ymparistolupa_to_krysp]]
            ;[lupapalvelu.document.validators :refer [dummy-doc]]
            ;[lupapalvelu.xml.krysp.validator :refer [validate]]
            ;[lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.xml.krysp.validator :as validator]
            [lupapalvelu.xml.emit :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.data.xml :refer :all]
            ;[clojure.java.io :refer :all]
            ))

(defn- do-test [application]
  (let [canonical (ymparistolupa-canonical application "fi")
        xml (element-to-xml canonical ymparistolupa_to_krysp)]

;    (clojure.pprint/pprint canonical)
;    (print (indent-str xml))
    ; Alla oleva tekee jo validoinnin, mutta annetaan olla tuossa alla viela validointi, jottei tule joku riko olemassa olevaa validointia
    ;(mapping-to-krysp/save-application-as-krysp application "fi" application {:krysp {:YL {:ftpUser "sipoo" :version "2.1.1"}}})
    ;(mapping-to-krysp/save-application-as-krysp application "fi" application {:krysp {:YL {:ftpUser "sipoo" :version "2.1.1"}}})

    (validator/validate (indent-str xml) (:permitType application) "2.1.1")
    ))


(facts "Ymparistolupa type of permits to canonical and then to xml with schema validation"

  (fact "Ymparistolupa yritys -> canonical -> xml"
    (do-test {:id "LP-753-2014-0001"
              :title "Ympp\u00e4 1"
              :modified 123456789
              :municipality "753"
              :state "open"
              :permitType "YL"})))