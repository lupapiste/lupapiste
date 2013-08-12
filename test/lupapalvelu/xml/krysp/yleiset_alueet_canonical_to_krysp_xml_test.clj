(ns lupapalvelu.xml.krysp.yleiset-alueet-canonical-to-krysp-xml-test
  (:use [midje.sweet]
        #_[lupapalvelu.document.canonical-common :only [by-type]]
        [lupapalvelu.document.yleiset-alueet-kaivulupa-canonical :only [application-to-canonical]]
        [lupapalvelu.document.yleiset-alueet-kaivulupa-canonical-test :only [application]]
        [lupapalvelu.xml.emit]
        [lupapalvelu.xml.krysp.yleiset-alueet-mapping :only [kaivulupa_to_krysp]]
        [clojure.data.xml]
        [clojure.java.io])
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.xml.krysp.validator :as validator]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :as xml-test-common]))


(fact ":tag is set" (xml-test-common/has-tag kaivulupa_to_krysp) => true)

(facts "Kaivulupa to canonical and then to kaivulupa xml with schema validation"
  (let [canonical (application-to-canonical application "fi")
        xml (element-to-xml canonical kaivulupa_to_krysp)
        xml-s (indent-str xml)]

    (println "\n xml-s: ")
    (println xml-s)
    (println "\n")

;    (println "\n application: ")
;    (clojure.pprint/pprint application)
;    (println "\n")
;
;    (println "\n kaivulupa_to_krysp: ")
;    (clojure.pprint/pprint kaivulupa_to_krysp)
;    (println "\n")

    ;; Alla oleva tekee jo validoinnin,
    ;; mutta annetaan olla tuossa alla viela tuo validointi, jottei joku tule ja riko olemassa olevaa validointia.
    (mapping-to-krysp/save-application-as-krysp application "fi" application {:yleiset-alueet-ftp-user "sipoo"}) ;TODO: own test

    (with-open
      [out-file (writer "/Users/jarias/krysp-muunnos-oma-testi.xml" )]
      (emit xml out-file))

    (fact "xml exist" xml => truthy)

    (validator/validate xml-s)
    ))
