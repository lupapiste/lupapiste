(ns lupapalvelu.xml.krysp.rakennuslupa_canonical_to_krysp_xml_test
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.document.rakennuslupa_canonical :refer [application-to-canonical katselmus-canonical]]
            [lupapalvelu.document.rakennuslupa_canonical-test :refer [application-rakennuslupa
                                                                      application-tyonjohtajan-nimeaminen
                                                                      application-suunnittelijan-nimeaminen
                                                                      jatkolupa-application
                                                                      aloitusoikeus-hakemus
                                                                      ]]
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :refer [rakennuslupa_to_krysp
                                                                save-aloitusilmoitus-as-krysp

                                                                save-katselmus-as-krysp]]
            [lupapalvelu.document.validators :refer [dummy-doc]]
            [lupapalvelu.xml.krysp.validator :refer [validate]]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.xml.krysp.validator :refer :all :as validator]
            [lupapalvelu.xml.emit :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
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
    (do-test application-suunnittelijan-nimeaminen))

  (fact "aloitusilmoitus -> canonical -> xml"
    (do-test aloitusoikeus-hakemus)))

(let [application (assoc application-rakennuslupa :state "verdictGiven")
      user        {:id "777777777777777777000017"
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
                   :city "Tampere"}]

  (fact "Katselmus data is parsed correctly from task. Not caring about actual mapping here."
    (against-background
      (#'lupapalvelu.xml.krysp.rakennuslupa-mapping/save-katselmus-xml
        application
        "fi"
        "target"
        "2.5.1974"
        {:jarjestysnumero "1" :kiinttun "09100200990013" :rakennusnro "001"}
        user
        "pohjakatselmus"
        :katselmus
        "pidetty"
        "pitaja"
        true
        "kuvaus"
        "Tiivi Taavi, Hipsu ja Lala"
        "Ei poikkeamisia"
        "begin-of-link"
        {:type "task" :id "123"}) => nil)
    (save-katselmus-as-krysp
      application
      {:id "123"
       :schema-info {:name "task-katselmus"}
       :data {:katselmuksenLaji {:value "pohjakatselmus"},
              :vaadittuLupaehtona {:value true} ; missing
              :rakennus
              {:0
               {:rakennus
                {:jarjestysnumero {:value "1"} :rakennusnro {:value "001"} :kiinttun {:value "09100200990013"}}
                :tila {:tila {:value "osittainen"} ; missing
                       :kayttoonottava {:value true}}}} ; missing
              :katselmus {:pitoPvm {:value "2.5.1974"}
                          :pitaja {:value "pitaja"}
                          :huomautukset {:kuvaus {:value "kuvaus"}
                                         :maaraAika {:value "3.5.1974"}; missing
                                         :toteaja {:value "toteaja"} ; missing
                                         :toteamisHetki {:value "1.5.1974"}} ; missing
                          :lasnaolijat {:value "Tiivi Taavi, Hipsu ja Lala"}
                          :poikkeamat {:value "Ei poikkeamisia"}
                          :tila {:value "pidetty"}}}}
      user
      "fi"
      "target"
      "begin-of-link") => nil ))
