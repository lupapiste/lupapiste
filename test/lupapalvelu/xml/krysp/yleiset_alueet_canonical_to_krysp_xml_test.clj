(ns lupapalvelu.xml.krysp.yleiset-alueet-canonical-to-krysp-xml-test
  (:require [lupapalvelu.document.yleiset-alueet-canonical :refer [application-to-canonical]]
            [lupapalvelu.document.yleiset-alueet-kaivulupa-canonical-test :refer [kaivulupa-application]]
            [lupapalvelu.document.yleiset-alueet-kayttolupa-canonical-test :refer [kayttolupa-application]]
            [lupapalvelu.document.yleiset-alueet-sijoituslupa-canonical-test :refer [sijoituslupa-application]]
            [lupapalvelu.document.yleiset-alueet-kayttolupa-mainostus-viitoitus-canonical-test
             :refer [mainostus-application viitoitus-application]]
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

(defn- do-test [application]
  (let [operation-name-key (-> application :operations first :name keyword)
        lupa-name-key (ya-operation-type-to-schema-name-key operation-name-key)
        canonical (application-to-canonical application "fi")
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

    ;; Alla oleva tekee jo validoinnin,
    ;; mutta annetaan olla tuossa alla viela tuo validointi, jottei joku tule ja riko olemassa olevaa validointia.
    ;; TODO: own test
    (mapping-to-krysp/save-application-as-krysp
      application "fi" application {:krysp {:YA {:ftpUser "dev_sipoo" :version "2.1.2"}}})

    (fact "xml exist" xml => truthy)

    (validator/validate xml-s (:permitType application) "2.1.2")))


(facts "YA permits to canonical and then to xml with schema validation"

    (fact "Kaivulupa application -> canonical -> xml"
      (do-test kaivulupa-application))

    (fact "Kayttolupa application -> canonical -> xml"
      (do-test kayttolupa-application))

    (fact "Sijoituslupa application -> canonical -> xml"
      (do-test sijoituslupa-application))

    (fact "Mainostuslupa application -> canonical -> xml"
      (do-test mainostus-application))

    (fact "Viitoituslupa application -> canonical -> xml"
      (do-test viitoitus-application)))
