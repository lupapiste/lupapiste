(ns lupapalvelu.xml.krysp.rakennuslupa_canonical_to_krysp_xml_test
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.document.rakennuslupa_canonical :refer [application-to-canonical katselmus-canonical]]
            [lupapalvelu.document.rakennuslupa_canonical-test :refer [application-rakennuslupa
                                                                      application-tyonjohtajan-nimeaminen
                                                                      application-suunnittelijan-nimeaminen
                                                                      jatkolupa-application
                                                                      aloitusoikeus-hakemus]]
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :refer [rakennuslupa_to_krysp_212
                                                                rakennuslupa_to_krysp_213
                                                                rakennuslupa_to_krysp_214
                                                                rakennuslupa_to_krysp_215
                                                                rakennuslupa_to_krysp_216
                                                                rakennuslupa_to_krysp_218
                                                                save-katselmus-as-krysp]]
            [lupapalvelu.xml.validator :refer [validate]]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer [has-tag]]
            [lupapalvelu.xml.validator :refer :all :as validator]
            [lupapalvelu.xml.emit :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.data.xml :refer :all]
            [clojure.java.io :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :as cr]))

(testable-privates lupapalvelu.xml.krysp.rakennuslupa-mapping rakennuslupa-element-to-xml)

(fact "2.1.2: :tag is set" (has-tag rakennuslupa_to_krysp_212) => true)
(fact "2.1.3: :tag is set" (has-tag rakennuslupa_to_krysp_213) => true)
(fact "2.1.4: :tag is set" (has-tag rakennuslupa_to_krysp_214) => true)
(fact "2.1.5: :tag is set" (has-tag rakennuslupa_to_krysp_215) => true)
(fact "2.1.6: :tag is set" (has-tag rakennuslupa_to_krysp_216) => true)
(fact "2.1.8: :tag is set" (has-tag rakennuslupa_to_krysp_218) => true)

(defn- do-test [application validate-tyonjohtaja-type validate-pysyva-tunnus?]
  (let [canonical (application-to-canonical application "fi")
        xml_212 (rakennuslupa-element-to-xml canonical "2.1.2")
        xml_213 (rakennuslupa-element-to-xml canonical "2.1.3")
        xml_214 (rakennuslupa-element-to-xml canonical "2.1.4")
        xml_215 (rakennuslupa-element-to-xml canonical "2.1.5")
        xml_216 (rakennuslupa-element-to-xml canonical "2.1.6")
        xml_218 (rakennuslupa-element-to-xml canonical "2.1.8")
        xml_212_s (indent-str xml_212)
        xml_213_s (indent-str xml_213)
        xml_214_s (indent-str xml_214)
        xml_215_s (indent-str xml_215)
        xml_216_s (indent-str xml_216)
        xml_218_s (indent-str xml_218)]

    (fact "2.1.2: xml exist" xml_212 => truthy)
    (fact "2.1.3: xml exist" xml_213 => truthy)
    (fact "2.1.4: xml exist" xml_214 => truthy)
    (fact "2.1.5: xml exist" xml_215 => truthy)
    (fact "2.1.6: xml exist" xml_216 => truthy)
    (fact "2.1.8: xml exist" xml_218 => truthy)



    (let [lp-xml_212 (cr/strip-xml-namespaces (xml/parse xml_212_s))
          lp-xml_213 (cr/strip-xml-namespaces (xml/parse xml_213_s))
          lp-xml_216 (cr/strip-xml-namespaces (xml/parse xml_216_s))
          lp-xml_218 (cr/strip-xml-namespaces (xml/parse xml_218_s))
          tyonjohtaja_212 (xml/select1 lp-xml_212 [:osapuolettieto :Tyonjohtaja])
          tyonjohtaja_213 (xml/select1 lp-xml_213 [:osapuolettieto :Tyonjohtaja])
          tyonjohtaja_216 (xml/select1 lp-xml_216 [:osapuolettieto :Tyonjohtaja])]

      (fact "saapumisPvm"
        (let [expected (sade.util/to-xml-date (:submitted application))]
          (xml/get-text lp-xml_212 [:luvanTunnisteTiedot :LupaTunnus :saapumisPvm]) => expected
          (xml/get-text lp-xml_213 [:luvanTunnisteTiedot :LupaTunnus :saapumisPvm]) => expected))

      (if validate-tyonjohtaja-type
        (do
          (fact "In KRYSP 2.1.2, patevyysvaatimusluokka/vaadittuPatevyysluokka A are mapped to 'ei tiedossa'"
            (xml/get-text tyonjohtaja_212 :vaadittuPatevyysluokka) => "ei tiedossa"
            (xml/get-text tyonjohtaja_212 :patevyysvaatimusluokka) => "ei tiedossa")

          (fact "In KRYSP 2.1.3, patevyysvaatimusluokka/vaadittuPatevyysluokka A is not mapped"
            (xml/get-text tyonjohtaja_213 :patevyysvaatimusluokka) => "A"
            (xml/get-text tyonjohtaja_213 :vaadittuPatevyysluokka) => "A")

          (when (= :v2 validate-tyonjohtaja-type)
            (fact "In KRYSP 2.1.6, :vainTamaHankeKytkin was added (Yhteiset schema was updated to 2.1.5 and tyonjohtaja along with it)"
              (xml/get-text tyonjohtaja_216 :vainTamaHankeKytkin) => "true")))
        (do
           tyonjohtaja_212 => nil
           tyonjohtaja_213 => nil))

      (if validate-pysyva-tunnus?
        (fact "pysyva rakennusnumero" (xml/get-text lp-xml_212 [:rakennustunnus :valtakunnallinenNumero]) => "1234567892")))


    (let [lp-xml_215 (cr/strip-xml-namespaces (xml/parse xml_215_s))]
      ; Address format has changed in 2.1.5
      (xml/get-text lp-xml_215 [:omistajatieto :Omistaja :yritys :postiosoitetieto :postiosoite :osoitenimi :teksti]) => "katu"
      (xml/get-text lp-xml_215 [:omistajatieto :Omistaja :yritys :postiosoitetieto :postiosoite :kunta]) => "Tuonela"

      ; E-Invoicing fields added in 2.1.5
      (xml/get-text lp-xml_215 [:osapuolitieto :Osapuoli :yritys :verkkolaskutustieto :Verkkolaskutus :ovtTunnus]) => "003712345678"
      (xml/get-text lp-xml_215 [:osapuolitieto :Osapuoli :yritys :verkkolaskutustieto :Verkkolaskutus :verkkolaskuTunnus]) => "laskutunnus-1234"
      (xml/get-text lp-xml_215 [:osapuolitieto :Osapuoli :yritys :verkkolaskutustieto :Verkkolaskutus :valittajaTunnus]) => "BAWCFI22")


    ; Alla oleva tekee jo validoinnin, mutta annetaan olla tuossa alla viela validointi, jottei tule joku riko olemassa olevaa validointia

    (validator/validate xml_212_s (:permitType application) "2.1.2")
    (validator/validate xml_213_s (:permitType application) "2.1.3")
    (validator/validate xml_214_s (:permitType application) "2.1.4")
    (validator/validate xml_215_s (:permitType application) "2.1.5")
    (validator/validate xml_216_s (:permitType application) "2.1.6")
    (validator/validate xml_218_s (:permitType application) "2.1.8")
    ))


(facts "Rakennusvalvonta type of permits to canonical and then to xml with schema validation"

  (fact "Rakennuslupa application -> canonical -> xml"
    (do-test application-rakennuslupa :v1 true))

  (fact "Ty\u00f6njohtaja application -> canonical -> xml"
    (do-test application-tyonjohtajan-nimeaminen :v2 false))

  (fact "Suunnittelija application -> canonical -> xml"
    (do-test application-suunnittelijan-nimeaminen nil false))

  (fact "Aloitusoikeus -> canonical -> xml"
    (do-test aloitusoikeus-hakemus nil false)))


(facts "Katselmus"
  (let [application (assoc application-rakennuslupa
                      :state "verdictGiven"
                      :buildings [{:index "1" :propertyId "09100200990013" :localShortId "001" :nationalId "1234567892"}])
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
            :rakennus {:jarjestysnumero "1" :kiinttun "09100200990013" :rakennusnro "001" :valtakunnallinenNumero "1234567892"}}]
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
        "begin-of-link") => nil
      )))


(facts "Tyonjohtajan sijaistus"
  (let [canonical (application-to-canonical application-tyonjohtajan-nimeaminen "fi")
        xml_213 (rakennuslupa-element-to-xml canonical "2.1.3")
        xml_215 (rakennuslupa-element-to-xml canonical "2.1.5")
        xml_213_s (indent-str xml_213)
        xml_215_s (indent-str xml_215)]

    (validator/validate xml_213_s (:permitType application-tyonjohtajan-nimeaminen) "2.1.3")
    (validator/validate xml_215_s (:permitType application-tyonjohtajan-nimeaminen) "2.1.5")

    (facts "2.1.3"
      (let [lp-xml (cr/strip-xml-namespaces (xml/parse xml_213_s))
            tyonjohtaja (xml/select1 lp-xml [:Tyonjohtaja])
            sijaistus (xml/select1 lp-xml [:Sijaistus])]
        (fact "sijaistettavan nimi" (xml/get-text sijaistus [:sijaistettavaHlo]) => "Jaska Jokunen")
        (fact "sijaistettava rooli" (xml/get-text sijaistus [:sijaistettavaRooli]) => (xml/get-text lp-xml [:tyonjohtajaRooliKoodi]))
        (fact "sijaistettavan alkamisPvm" (xml/get-text sijaistus [:alkamisPvm]) => "2014-02-13")
        (fact "sijaistettavan paattymisPvm" (xml/get-text sijaistus [:paattymisPvm]) => "2014-02-20")
        (fact "postiosoite"
          (xml/get-text tyonjohtaja [:yritys :postiosoite :osoitenimi :teksti]) => "katu")))

    (facts "2.1.5"
      (let [lp-xml (cr/strip-xml-namespaces (xml/parse xml_215_s))
            tyonjohtaja (xml/select1 lp-xml [:Tyonjohtaja])
            vastuu (xml/select tyonjohtaja [:vastattavaTyotieto])]
        (fact "rooli" (xml/get-text tyonjohtaja [:tyonjohtajaRooliKoodi]) => "KVV-ty\u00f6njohtaja")
        (fact "sijaistettavan nimi" (xml/get-text tyonjohtaja [:sijaistettavaHlo]) => "Jaska Jokunen")
        (fact "5 vastuuta" (count vastuu) => 5)
        (fact "vastuun alkamisPvm" (xml/get-text tyonjohtaja [:alkamisPvm]) => "2014-02-13")
        (fact "vastuun paattymisPvm" (xml/get-text tyonjohtaja [:paattymisPvm]) => "2014-02-20")
        (fact "vastattavaTyo"
          (map #(xml/get-text % [:vastattavaTyo]) vastuu) => (just #{"Rakennuksen purkaminen"
                                                                     "IV-laitoksen korjaus- ja muutosty\u00f6"
                                                                     "Uudisrakennusty\u00f6 ilman maanrakennust\u00f6it\u00e4"
                                                                     "Maanrakennusty\u00f6t"
                                                                     "Muu tyotehtava"}))
        (fact "postiosoite"
          (xml/get-text tyonjohtaja [:yritys :postiosoitetieto :postiosoite :osoitenimi :teksti]) => "katu")))))
