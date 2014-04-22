(ns lupapalvelu.xml.krysp.jatkoaika-canonical-to-krysp-xml-test
  (:require [lupapalvelu.document.yleiset-alueet-canonical :refer [jatkoaika-to-canonical]]
            [lupapalvelu.document.yleiset-alueet-jatkoaika-canonical-test :refer [jatkoaika-application]]
            [lupapalvelu.xml.emit :refer :all]
            [lupapalvelu.xml.krysp.yleiset-alueet-mapping :refer [get-yleiset-alueet-krysp-mapping yleisetalueet-element-to-xml]]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.xml.krysp.validator :refer :all :as validator]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer :all :as xml-test-common]
            [lupapalvelu.xml.krysp.mapping-common :refer :all :as mapping-common]
            [lupapalvelu.document.canonical-common :refer [ya-operation-type-to-schema-name-key]]
            [midje.sweet :refer :all]
            [clojure.data.xml :refer :all]
            [clojure.java.io :refer :all]))


(facts "Jatkolupa to canonical and then to jatkolupa xml with schema validation"
  (let [operation-name-key (-> jatkoaika-application :linkPermitData first :operation keyword)
        lupa-name-key (ya-operation-type-to-schema-name-key operation-name-key)
        canonical (jatkoaika-to-canonical jatkoaika-application "fi")
        xml-212 (yleisetalueet-element-to-xml canonical lupa-name-key "2.1.2")
        xml-213 (yleisetalueet-element-to-xml canonical lupa-name-key "2.1.3")
        xml-212s (indent-str xml-212)
        xml-213s (indent-str xml-213)]


    (fact ":tag is set"
      (xml-test-common/has-tag
        (get-yleiset-alueet-krysp-mapping lupa-name-key "2.1.2")) => true)

    (fact ":tag is set"
      (xml-test-common/has-tag
        (get-yleiset-alueet-krysp-mapping lupa-name-key "2.1.3")) => true)


    (fact "2.1.2: xml exist" xml-212 => truthy)
    (fact "2.1.3: xml exist" xml-213 => truthy)

;    (println "\n xml-s: " xml-s "\n")

;    (println "\n application: ")
;    (clojure.pprint/pprint application)
;    (println "\n")

;    (println "\n yleiset-alueet-krysp-mapping: ")
;    (clojure.pprint/pprint mapping)
;    (println "\n")

    ;; Alla oleva tekee jo validoinnin,
    ;; mutta annetaan olla tuossa alla viela tuo validointi, jottei joku tule ja riko olemassa olevaa validointia.
    ;; TODO: own test
    (mapping-to-krysp/save-jatkoaika-as-krysp jatkoaika-application "fi" {:krysp {:YA {:ftpUser "dev_sipoo" :version "2.1.2"}}})
    (mapping-to-krysp/save-jatkoaika-as-krysp jatkoaika-application "fi" {:krysp {:YA {:ftpUser "dev_sipoo" :version "2.1.3"}}})


    (validator/validate xml-212s (:permitType jatkoaika-application) "2.1.2")
    (validator/validate xml-213s (:permitType jatkoaika-application) "2.1.3")
   ))
