(ns lupapalvelu.xml.krysp.rakennuslupa_canonical_to_krysp_xml_test
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.document.rakennuslupa_canonical :refer [application-to-canonical
                                                                 jatkoaika-canonical]]
            [lupapalvelu.document.rakennuslupa_canonical-test :refer [application-rakennuslupa
                                                                      application-tyonjohtajan-nimeaminen
                                                                      application-suunnittelijan-nimeaminen
                                                                      jatkolupa-application
                                                                      ]]
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :refer [rakennuslupa_to_krysp
                                                                save-aloitusilmoitus-as-krysp]]
            [lupapalvelu.xml.krysp.validator :refer [validate]]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.xml.krysp.validator :refer :all :as validator]
            [lupapalvelu.xml.emit :refer :all]
            [midje.sweet :refer :all]
            [clojure.data.xml :refer :all]
            [clojure.java.io :refer :all]))

(defn- do-test [application]
  (let [canonical (application-to-canonical application "fi")
        xml (element-to-xml canonical rakennuslupa_to_krysp)
        xml-s (indent-str xml)]

    (fact ":tag is set" (has-tag rakennuslupa_to_krysp) => true)

    ;(println xml-s)
    ;Alla oleva tekee jo validoinnin, mutta annetaan olla tuossa alla viela validointi, jottei tule joku riko olemassa olevaa validointia
    ;; TODO: own test
    (mapping-to-krysp/save-application-as-krysp application "fi" application {:rakennus-ftp-user "sipoo"})

    ;(clojure.pprint/pprint application)

    ;(clojure.pprint/pprint rakennuslupa_to_krysp)
    ;(with-open [out-file (writer "/Users/terotu/jatkolupa_example.xml" )]
    ;    (emit xml out-file))
    (fact "xml exist" xml => truthy)

    (validator/validate xml-s)

    #_(save-aloitusilmoitus-as-krysp
      (assoc application :state "verdictGiven")
      "fi"
      ""
      (lupapalvelu.core/now)
      {:jarjestysnumero 1 :rakennusnro "123"}
      {:id "777777777777777777000017"
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

(facts "Rakennusvalvonta type of permits to canonical and then to xml with schema validation"

  (fact "Rakennuslupa application -> canonical -> xml"
    (do-test application-rakennuslupa))

  (fact "Ty\u00f6njohtaja application -> canonical -> xml"
    (do-test application-tyonjohtajan-nimeaminen))

  (fact "Suunnittelija application -> canonical -> xml"
    (do-test application-suunnittelijan-nimeaminen)))


