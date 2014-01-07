(ns lupapalvelu.xml.krysp.jatkoaika-canonical-to-krysp-xml-test
  (:require [lupapalvelu.document.yleiset-alueet-canonical :refer [jatkoaika-to-canonical]]
            [lupapalvelu.document.yleiset-alueet-jatkoaika-canonical-test :refer [jatkoaika-application]]
            [lupapalvelu.xml.emit :refer :all]
            [lupapalvelu.xml.krysp.yleiset-alueet-mapping :refer [get-yleiset-alueet-krysp-mapping]]
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
        mapping (get-yleiset-alueet-krysp-mapping lupa-name-key)
        xml (element-to-xml canonical mapping)
        xml-s (indent-str xml)]

    (fact ":tag is set"
      (xml-test-common/has-tag
        (get-yleiset-alueet-krysp-mapping lupa-name-key)) => true)

;    (println "\n xml-s: " xml-s "\n")

;    (println "\n application: ")
;    (clojure.pprint/pprint application)
;    (println "\n")

;    (println "\n yleiset-alueet-krysp-mapping: ")
;    (clojure.pprint/pprint mapping)
;    (println "\n")

    (mapping-to-krysp/save-jatkoaika-as-krysp jatkoaika-application "fi" {:yleiset-alueet-ftp-user "sipoo"})

    (fact "xml exist" xml => truthy)

    (validator/validate xml-s)))
