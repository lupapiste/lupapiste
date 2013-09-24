(ns lupapalvelu.xml.krysp.yleiset-alueet-canonical-to-krysp-xml-test
  (:use [midje.sweet]
        [lupapalvelu.document.yleiset-alueet-canonical :only [application-to-canonical]]
        [lupapalvelu.document.yleiset-alueet-kaivulupa-canonical-test :only [kaivulupa-application]]
        [lupapalvelu.document.yleiset-alueet-kayttolupa-canonical-test :only [kayttolupa-application]]
        [lupapalvelu.document.yleiset-alueet-sijoituslupa-canonical-test :only [sijoituslupa-application]]
        [lupapalvelu.document.yleiset-alueet-kayttolupa-mainostus-viitoitus-canonical-test
         :only [mainostus-application viitoitus-application]]
        [lupapalvelu.xml.emit]
        [lupapalvelu.xml.krysp.yleiset-alueet-mapping :only [get-yleiset-alueet-krysp-mapping]]
        [clojure.data.xml]
        [clojure.java.io])
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.xml.krysp.validator :as validator]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :as xml-test-common]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [lupapalvelu.document.canonical-common :refer [ya-operation-type-to-schema-name-key]]))

(defn- do-test [application]
  (let [operation-name-key (-> application :operations first :name keyword)
        lupa-name-key (operation-name-key ya-operation-type-to-schema-name-key)
        canonical (application-to-canonical application "fi")
        mapping (get-yleiset-alueet-krysp-mapping lupa-name-key)
        xml (element-to-xml canonical mapping)
        xml-s (indent-str xml)]

    (fact ":tag is set"
      (xml-test-common/has-tag
        (get-yleiset-alueet-krysp-mapping lupa-name-key)) => true)

;    (println "\n xml-s: \n")
;    (println xml-s)
;    (println "\n")

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
      application "fi" application {:yleiset-alueet-ftp-user "sipoo"})

    (fact "xml exist" xml => truthy)

    (validator/validate xml-s)))


(facts "YA permits to canonical and then to xml with schema validation"

  (let [operation-name-key (-> kayttolupa-application :operations first :name keyword)]

    (fact "Kaivulupa application -> canonical -> xml"
      (do-test kaivulupa-application))

    (fact "Kayttolupa application -> canonical -> xml"
      (do-test kayttolupa-application))

    (fact "Sijoituslupa application -> canonical -> xml"
      (do-test sijoituslupa-application))

    (fact "Mainostuslupa application -> canonical -> xml"
      (do-test mainostus-application))

    (fact "Viitoituslupa application -> canonical -> xml"
      (do-test viitoitus-application))))
