(ns lupapalvelu.xml.krysp.rakennuslupa_canonical_to_krysp_xml_test
  (:use [midje.sweet]
        [lupapalvelu.document.rakennuslupa_canonical :only [application-to-canonical]]
        [lupapalvelu.document.rakennuslupa_canonical-test :only [application]]
        [lupapalvelu.xml.emit]
        [lupapalvelu.xml.krysp.rakennuslupa-mapping :only [rakennuslupa_to_krysp save-aloitusilmoitus-as-krysp]]
        [lupapalvelu.xml.krysp.validator :only [validate]]
        [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :only [has-tag]]
        [clojure.data.xml]
        [clojure.java.io])
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]))

(fact ":tag is set" (has-tag rakennuslupa_to_krysp) => true)

(facts "Rakennuslupa to canonical and then to rakennuslupa xml with schema validation"
  (let [canonical (application-to-canonical application "fi")
        xml (element-to-xml canonical rakennuslupa_to_krysp)
        xml-s (indent-str xml)]

    ;(println xml-s)
    ;Alla oleva tekee jo validoinnin, mutta annetaan olla tuossa alla viela validointi, jottei tule joku riko olemassa olevaa validointia
    ;; TODO: own test
    (mapping-to-krysp/save-application-as-krysp application "fi" application {:rakennus-ftp-user "sipoo"})

    ;(clojure.pprint/pprint application)

    ;(clojure.pprint/pprint rakennuslupa_to_krysp)
    ;(with-open [out-file (writer "/Users/terotu/example.xml" )]
    ;    (emit xml out-file))
    (fact "xml exist" xml => truthy)

    (validate xml-s)  ;; in lupapalvelu.xml.krysp.validator

    (save-aloitusilmoitus-as-krysp (assoc application :state "verdictGiven") "fi" "" (lupapalvelu.core/now) {:jarjestysnumero 1 :rakennusnro "123"} {:id "777777777777777777000017"
                                                                                      :email "jussi.viranomainen@tampere.fi"
                                                                                      :enabled true
                                                                                      :role "authority"
                                                                                      :username "jussi"
                                                                                      :organizations ["837-YA"]
                                                                                      :firstName "Jussi"
                                                                                      :lastName "Viranomainen"
                                                                                      :street "Katuosoite 1 a 1"
                                                                                      :phone "1231234567"
                                                                                      :zip "33456"
                                                                                      :city "Tampere"
                                                                                      :private {:salt "$2a$10$Wl49diVWkO6UpBABzjYR4e"
                                                                                                :password "$2a$10$Wl49diVWkO6UpBABzjYR4e8zTwIJBDKiEyvw1O2EMOtV9fqHaXPZq" ;; jussi
                                                                                                :apikey "5051ba0caa2480f374dcfefg"}})

    ))
