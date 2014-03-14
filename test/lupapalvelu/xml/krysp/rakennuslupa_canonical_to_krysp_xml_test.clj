(ns lupapalvelu.xml.krysp.rakennuslupa_canonical_to_krysp_xml_test
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.document.rakennuslupa_canonical :refer [application-to-canonical katselmus-canonical]]
            [lupapalvelu.document.rakennuslupa_canonical-test :refer [application-rakennuslupa
                                                                      application-tyonjohtajan-nimeaminen
                                                                      application-suunnittelijan-nimeaminen
                                                                      jatkolupa-application
                                                                      aloitusoikeus-hakemus
                                                                      ]]
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :refer [rakennuslupa_to_krysp_212
                                                                rakennuslupa_to_krysp_213
                                                                save-katselmus-as-krysp]]
            [lupapalvelu.document.validators :refer [dummy-doc]]
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

(testable-privates lupapalvelu.xml.krysp.rakennuslupa-mapping rakennuslupa-element-to-xml)

(defn- do-test [application validate-tyonjohtaja?]
  (let [canonical (application-to-canonical application "fi")
        xml_212 (rakennuslupa-element-to-xml canonical "2.1.2")
        xml_213 (rakennuslupa-element-to-xml canonical "2.1.3")
        xml_212_s (indent-str xml_212)
        xml_213_s (indent-str xml_213)]

    (fact "2.1.2: :tag is set" (has-tag rakennuslupa_to_krysp_212) => true)
    (fact "2.1.3: :tag is set" (has-tag rakennuslupa_to_krysp_213) => true)

    (fact "2.1.2: xml exist" xml_212 => truthy)
    (fact "2.1.3: xml exist" xml_213 => truthy)

    (let [lp-xml_212 (cr/strip-xml-namespaces (xml/parse xml_212_s))
          lp-xml_213 (cr/strip-xml-namespaces (xml/parse xml_213_s))
          tyonjohtaja_212 (xml/select1 lp-xml_212 [:osapuolettieto :Tyonjohtaja])
          tyonjohtaja_213 (xml/select1 lp-xml_213 [:osapuolettieto :Tyonjohtaja])]

      (if validate-tyonjohtaja?
        (do
          (fact "In KRYSP 2.1.2, patevyysvaatimusluokka A is mapped to 'ei tiedossa'"
            (xml/get-text tyonjohtaja_212 :patevyysvaatimusluokka) => "ei tiedossa")

          (fact "In KRYSP 2.1.3, patevyysvaatimusluokka A not mapped"
            (xml/get-text tyonjohtaja_213 :patevyysvaatimusluokka) => "A"))
        (do
           tyonjohtaja_212 => nil
           tyonjohtaja_213 => nil)))

    ; Alla oleva tekee jo validoinnin, mutta annetaan olla tuossa alla viela validointi, jottei tule joku riko olemassa olevaa validointia
    (mapping-to-krysp/save-application-as-krysp application "fi" application {:krysp {:R {:ftpUser "dev_sipoo" :version "2.1.2"}}})
    (mapping-to-krysp/save-application-as-krysp application "fi" application {:krysp {:R {:ftpUser "dev_sipoo" :version "2.1.3"}}})

    (validator/validate xml_212_s (:permitType application) "2.1.2")
    (validator/validate xml_213_s (:permitType application) "2.1.3")))


(facts "Rakennusvalvonta type of permits to canonical and then to xml with schema validation"

  (fact "Rakennuslupa application -> canonical -> xml"
    (do-test application-rakennuslupa true))

  (fact "Ty\u00f6njohtaja application -> canonical -> xml"
    (do-test application-tyonjohtajan-nimeaminen true))

  (fact "Suunnittelija application -> canonical -> xml"
    (do-test application-suunnittelijan-nimeaminen false))

  (fact "Aloitusoikeus -> canonical -> xml"
    (do-test aloitusoikeus-hakemus false)))


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
        "123" ; task id
        "Pohjakatselmus 1" ; task name
        "2.5.1974"
        [{:tila {:tila "osittainen" :kayttoonottava true}
          :rakennus {:jarjestysnumero "1" :kiinttun "09100200990013" :rakennusnro "001"}}]
        user
        "pohjakatselmus" ; katselmuksen-nimi
        :katselmus ;tyyppi
        "pidetty" ;osittainen
        "pitaja" ;pitaja
        false ;lupaehtona
        {:kuvaus "kuvaus"
         :maaraAika "3.5.1974"
         :toteaja "toteaja"
         :toteamisHetki "1.5.1974"} ;huomautukset
        "Tiivi Taavi, Hipsu ja Lala" ;lasnaolijat
        "Ei poikkeamisia" ;poikkeamat
        "2.1.2" ;krysp-version
        "begin-of-link" ;begin-of-link
        {:type "task" :id "123"} ;attachment-target
        ) => nil)

    (save-katselmus-as-krysp
      application
      {:id "123"
       :taskname "Pohjakatselmus 1"
       :schema-info {:name "task-katselmus"}
       :data {:katselmuksenLaji {:value "pohjakatselmus"},
              :vaadittuLupaehtona {:value false}
              :rakennus {:0
                         {:rakennus
                          {:jarjestysnumero {:value "1"} :rakennusnro {:value "001"} :kiinttun {:value "09100200990013"}}
                          :tila {:tila {:value "osittainen"}
                                 :kayttoonottava {:value true}}}}
              :katselmus {:pitoPvm {:value "2.5.1974"}
                          :pitaja {:value "pitaja"}
                          :huomautukset {:kuvaus {:value "kuvaus"}
                                         :maaraAika {:value "3.5.1974"}
                                         :toteaja {:value "toteaja"}
                                         :toteamisHetki {:value "1.5.1974"}}
                          :lasnaolijat {:value "Tiivi Taavi, Hipsu ja Lala"}
                          :poikkeamat {:value "Ei poikkeamisia"}
                          :tila {:value "pidetty"}}}}
      user
      "fi"
      "2.1.2"
      "target"
      "begin-of-link") => nil ))


(facts "Tyonjohtajan sijaistus"
  (let [canonical (application-to-canonical application-tyonjohtajan-nimeaminen "fi")
        krysp-xml (element-to-xml canonical rakennuslupa_to_krysp_213)
        xml-s     (indent-str krysp-xml)
        lp-xml    (cr/strip-xml-namespaces (xml/parse xml-s))
        sijaistus (xml/select1 lp-xml [:Sijaistus])]

    (validator/validate xml-s (:permitType application-tyonjohtajan-nimeaminen) "2.1.3")

    (fact "sijaistettavan nimi" (xml/get-text sijaistus [:sijaistettavaHlo]) => "Jaska Jokunen")
    (fact "sijaistettava rooli" (xml/get-text sijaistus [:sijaistettavaRooli]) => (xml/get-text lp-xml [:tyonjohtajaRooliKoodi]))
    (fact "sijaistettavan alkamisPvm" (xml/get-text sijaistus [:alkamisPvm]) => "2014-02-13")
    (fact "sijaistettavan paattymisPvm" (xml/get-text sijaistus [:paattymisPvm]) => "2014-02-20")))

