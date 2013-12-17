(ns lupapalvelu.document.rakennuslupa_canonical-test
  (:require [lupapalvelu.document.canonical-test-common :refer :all]
            [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.rakennuslupa_canonical :refer :all]
            [lupapalvelu.xml.emit :refer :all]
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :refer :all]
            [lupapalvelu.factlet :as fl]
            [sade.util :refer :all]
            [clojure.data.xml :refer :all]
            [clj-time.core :refer [date-time]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

;;
;; Facts
;;

(facts "Date format"
  (fact (to-xml-date (date-time 2012 1 14)) => "2012-01-14")
  (fact (to-xml-date (date-time 2012 2 29)) => "2012-02-29"))

(def ^:private municipality 753)

(def ^:private nimi {:etunimi {:value "Pena"} :sukunimi {:value "Penttil\u00e4"}})

(def ^:private henkilotiedot (assoc nimi :hetu {:value "210281-9988"} :turvakieltoKytkin {:value true}))

(def ^:private osoite {:katu {:value "katu"} :postinumero {:value "33800"} :postitoimipaikannimi {:value "Tuonela"}})

(def ^:private henkilo
  {:henkilotiedot henkilotiedot
   :yhteystiedot {:puhelin {:value "+358401234567"}
                  :email {:value "pena@example.com"}}
   :osoite osoite})

(def ^:private suunnittelija-henkilo
  (assoc henkilo :henkilotiedot (dissoc henkilotiedot :turvakieltoKytkin)))

(def ^:private yritysnimi-ja-ytunnus
  {:yritysnimi {:value "Solita Oy"} :liikeJaYhteisoTunnus {:value "1060155-5"}})

(def ^:private yritys
  (merge
    yritysnimi-ja-ytunnus
    {:osoite osoite
     :yhteyshenkilo {:henkilotiedot (dissoc henkilotiedot :hetu)
                     :yhteystiedot {:email {:value "solita@solita.fi"},
                                    :puhelin {:value "03-389 1380"}}}}))

(def ^:private hakija-henkilo
  {:id "hakija-henkilo" :schema-info {:name "hakija"
                                      :version 1}
   :data {:henkilo henkilo}})

(def ^:private hakija-yritys
  {:id "hakija-yritys" :schema-info {:name "hakija"
                                     :version 1}
   :data {:_selected {:value "yritys"}, :yritys yritys}})

(def ^:private paasuunnittelija
  {:id "50bc85e4ea3e790c9ff7cdb2"
   :schema-info {:name "paasuunnittelija"
                 :version 1}
   :data (merge
           suunnittelija-henkilo
           {:patevyys {:koulutus {:value "Arkkitehti"}
                       :patevyysluokka {:value "ei tiedossa"}
                       :valmistumisvuosi {:value "2010"}
                       :kokemus {:value "5"}
                       :fise {:value "http://www.ym.fi"}}}
           {:yritys yritysnimi-ja-ytunnus})})

(def ^:private suunnittelija1
  {:id "suunnittelija1" :schema-info {:name "suunnittelija"
                                      :version 1}
   :data (merge suunnittelija-henkilo
                {:kuntaRoolikoodi {:value "ARK-rakennussuunnittelija"}}
                {:patevyys {:koulutus {:value "Arkkitehti"}
                            :patevyysluokka {:value "B"}
                            :valmistumisvuosi {:value "2010"}
                            :kokemus {:value "5"}
                            :fise {:value "http://www.ym.fi"}}}
                {:yritys yritysnimi-ja-ytunnus})})

(def ^:private suunnittelija2
  {:id "suunnittelija2"  :schema-info {:name "suunnittelija"
                                       :version 1}
   :data (merge suunnittelija-henkilo
                {:kuntaRoolikoodi {:value "GEO-suunnittelija"}}
                {:patevyys {:koulutus {:value "El\u00e4m\u00e4n koulu"}
                            :patevyysluokka {:value "AA"}
                            :valmistumisvuosi {:value "2010"}
                            :kokemus {:value "5"}
                            :fise {:value "http://www.ym.fi"}}}
                {:yritys yritysnimi-ja-ytunnus})})

(def ^:private suunnittelija-old-schema-LUPA-771
  {:id "suunnittelija-old-schema-LUPA771" :schema-info {:name "suunnittelija"
                                                        :version 1}
   :data (merge suunnittelija-henkilo
                {:patevyys {:koulutus {:value "Arkkitehti"}
                            :kuntaRoolikoodi {:value "ARK-rakennussuunnittelija"}
                            :patevyysluokka {:value "B"}
                            :valmistumisvuosi {:value "2010"}
                            :kokemus {:value "5"}
                            :fise {:value "http://www.ym.fi"}}})})

(def ^:private suunnittelija-blank-role
  {:id "suunnittelija-blank-role" :schema-info {:name "suunnittelija"
                                                :version 1}
   :data (merge suunnittelija-henkilo
                {:kuntaRoolikoodi {:value ""}}
                {:patevyys {:koulutus {:value "Arkkitehti"}
                            :patevyysluokka {:value "B"}
                            :valmistumisvuosi {:value "2010"}
                            :kokemus {:value "5"}
                            :fise {:value "http://www.ym.fi"}}}
                {:yritys yritysnimi-ja-ytunnus})})

(def ^:private maksaja-henkilo
  {:id "maksaja-henkilo" :schema-info {:name "maksaja"
                                       :version 1}
   :data {:henkilo henkilo}})

(def ^:private maksaja-yritys
  {:id "maksaja-yritys" :schema-info {:name "maksaja"
                                      :version 1}
   :data {:_selected {:value "yritys"}, :yritys yritys}})

(def ^:private tyonjohtaja
  {:id "tyonjohtaja"
   :schema-info {:name "tyonjohtaja", :version 1}
   :data (merge suunnittelija-henkilo
           {:kuntaRoolikoodi {:value "KVV-ty\u00f6njohtaja"}
            :patevyys {:koulutus {:value "Koulutus"}
                       :patevyysvaatimusluokka {:value "AA"}
                       :valmistumisvuosi {:value "2010"}
                       :tyonjohtajaHakemusKytkin {:value "hakemus"}
                       :kokemusvuodet {:value "3"}
                       :valvottavienKohteidenMaara {:value "9"}}
            :vastattavatTyotehtavat {:kiinteistonVesiJaViemarilaitteistonRakentaminen {:value true}
                                     :kiinteistonilmanvaihtolaitteistonRakentaminen {:value true}
                                     :maanrakennustyo {:value true}
                                     :rakennelmaTaiLaitos {:value true}
                                     :muuMika {:value "Muu tyotehtava"}}
            :yritys yritysnimi-ja-ytunnus})})

(def ^:private tyonjohtaja-blank-role-and-blank-qualification
  (-> tyonjohtaja
    (assoc-in [:data :kuntaRoolikoodi :value] "")
    (assoc-in [:data :patevyys :patevyysvaatimusluokka :value] "Ei tiedossa")
    (assoc-in [:data :patevyys :tyonjohtajaHakemusKytkin :value] "nimeaminen")))

(def ^:private rakennuspaikka
  {:id "rakennuspaikka" :schema-info {:name "rakennuspaikka"
                                      :version 1}
   :data {:kiinteisto {:tilanNimi {:value "Hiekkametsa"}
                       :maaraalaTunnus {:value ""}}
          :hallintaperuste {:value "oma"}
          :kaavanaste {:value "yleis"}}})

(def ^:private common-rakennus
  {:rakennuksenOmistajat {:0 {:_selected {:value "henkilo"}
                              :henkilo henkilo
                              :omistajalaji {:value "muu yksityinen henkil\u00f6 tai perikunta"}}}
   :kaytto {:rakentajaTyyppi {:value "muu"}
            :kayttotarkoitus {:value "012 kahden asunnon talot"}}
   :mitat {:tilavuus {:value "1500"}
           :kokonaisala {:value "1000"}
           :kellarinpinta-ala {:value "100"}
           :kerrosluku {:value "2"}
           :kerrosala {:value "180"}}
   :rakenne {:rakentamistapa {:value "elementti"}
             :kantavaRakennusaine {:value "puu"}
             :muuRakennusaine {:value ""}
             :julkisivu {:value "puu"}}
   :lammitys {:lammitystapa {:value "vesikeskus"}
              :lammonlahde {:value "other"}
              :muu-lammonlahde {:value "polttopuillahan tuo"}}
   :varusteet {:hissiKytkin {:value true}
               :kaasuKytkin {:value true}
               :koneellinenilmastointiKytkin {:value true}
               :sahkoKytkin {:value true}
               :saunoja {:value "1"}
               :vaestonsuoja {:value "1"}
               :vesijohtoKytkin {:value true}
               :viemariKytkin {:value true}
               :lamminvesiKytkin {:value true}
               :aurinkopaneeliKytkin {:value true}}
   :verkostoliittymat {:kaapeliKytkin {:value true}
                       :maakaasuKytkin {:value true}
                       :sahkoKytkin {:value true}
                       :vesijohtoKytkin {:value true}
                       :viemariKytkin {:value true}}
   :luokitus {:paloluokka {:value "P1"}
              :energialuokka {:value "C"}
              :energiatehokkuusluku {:value "124"}
              :energiatehokkuusluvunYksikko {:value "kWh/m2"}}
   :huoneistot {:0 {:muutostapa {:value "lis\u00e4ys"}
                    :huoneistoTunnus {:porras {:value "A"}
                                      :huoneistonumero {:value "1"}
                                      :jakokirjain {:value "a"}}
                    :huoneistonTyyppi {:huoneistoTyyppi {:value "asuinhuoneisto"}
                                       :huoneistoala {:value "56"}
                                       :huoneluku {:value "66"}}
                    :keittionTyyppi {:value "keittio"}
                    :varusteet {:parvekeTaiTerassiKytkin {:value true}
                                :WCKytkin {:value true}}}
                :1 {:muutostapa {:value "lis\u00e4ys"}
                    :huoneistoTunnus {}
                    :huoneistonTyyppi {:huoneistoTyyppi {:value "toimitila"}
                                       :huoneistoala {:value "02"}
                                       :huoneluku {:value "12"}}
                    :keittionTyyppi {:value "keittokomero"}
                    :varusteet {:ammeTaiSuihkuKytkin {:value true}
                                :saunaKytkin {:value true}
                                :lamminvesiKytkin {:value true}}}}})

(def ^:private uusi-rakennus
  {:id "uusi-rakennus"
   :created 2
   :schema-info {:name "uusiRakennus"
                 :version 1
                 :op {:name "asuinrakennus"}}
   :data common-rakennus})

(def ^:private rakennuksen-muuttaminen
  {:id "muuttaminen"
   :created 1
   :schema-info {:name "rakennuksen-muuttaminen"
                 :version 1
                 :op {:name "muu-laajentaminen"}}
   :data (conj {:rakennusnro {:value "001"}
                :perusparannuskytkin {:value true}
                :muutostyolaji {:value "muut muutosty\u00f6t"}} common-rakennus)})

(def ^:private laajentaminen
  {:id "laajennus"
   :created 3
   :schema-info {:name "rakennuksen-laajentaminen"
                 :version 1
                 :op {:name "laajentaminen"}}
   :data (conj {:rakennusnro {:value "001"}
                :manuaalinen_rakennusnro {:value "002"}
                :laajennuksen-tiedot {:perusparannuskytkin {:value true}
                                      :mitat {:tilavuus {:value "1500"}
                                              :kerrosala {:value "180"}
                                              :kokonaisala {:value "150"}
                                              :huoneistoala {:0 {:pintaAla {:value "150"}
                                                                 :kayttotarkoitusKoodi {:value "asuntotilaa(ei vapaa-ajan asunnoista)"}}
                                                             :1 {:pintaAla {:value "10"}
                                                                 :kayttotarkoitusKoodi {:value "varastotilaa"}}}}}} common-rakennus)})


(def ^:private purku {:id "purku"
                      :created 4
                      :schema-info {:name "purku"
                                    :version 1
                                    :op {:name "purkaminen"}}
                      :data (conj {:rakennusnro {:value "001"}
                                   :poistumanAjankohta {:value "17.04.2013"},
                                   :poistumanSyy {:value "tuhoutunut"}} common-rakennus)})

(def ^:private aidan-rakentaminen {:data {:kokonaisala {:value "0"}
                                          :kuvaus { :value "Aidan rakentaminen rajalle"}}
                                   :id "aidan-rakentaminen"
                                   :created 5
                                   :schema-info {:removable true
                                                 :op {:id  "5177ac76da060e8cd8348e07"
                                                      :name "aita"}
                                                 :name "kaupunkikuvatoimenpide"
                                                 :version 1}})

(def ^:private puun-kaataminen {:created 6
                                :data { :kuvaus {:value "Puun kaataminen"}}
                                :id "puun kaataminen"
                                :schema-info {:removable true
                                              :op {:id "5177ad63da060e8cd8348e32"
                                                   :name "puun-kaataminen"
                                                   :created  1366797667137}
                                              :name "maisematyo"
                                              :version 1}})

(def ^:private hankkeen-kuvaus-minimum {:id "Hankkeen kuvaus"
                                        :schema-info {:name "hankkeen-kuvaus-minimum" :version 1 :order 1},
                                        :data {:kuvaus {:value "Uuden rakennuksen rakentaminen tontille."}}})

(def ^:private hankkeen-kuvaus
  (->
    (assoc-in hankkeen-kuvaus-minimum [:data :poikkeamat] {:value "Ei poikkeamisia"})
    (assoc-in [:schema-info :name] "hankkeen-kuvaus")))


(def ^:private lisatieto {:id "lisatiedot" :schema-info {:name "lisatiedot"
                                                         :version 1}
                          :data {:suoramarkkinointikielto {:value true}}})

(def ^:private link-permit-data-kuntalupatunnus {:id "123-123-123-123" :type "kuntalupatunnus"})
(def ^:private link-permit-data-lupapistetunnus {:id "LP-753-2013-00002" :type "lupapistetunnus"})
(def ^:private app-linking-to-us {:id "LP-753-2013-00008"})

;TODO LIITETIETO

(def documents
  [hankkeen-kuvaus
   hakija-henkilo
   hakija-yritys
   paasuunnittelija
   suunnittelija1
   suunnittelija2
   maksaja-henkilo
   maksaja-yritys
   tyonjohtaja
   rakennuspaikka
   rakennuksen-muuttaminen
   uusi-rakennus
   laajentaminen
   aidan-rakentaminen
   puun-kaataminen
   purku
   lisatieto])

(fact "Meta test: hakija-henkilo"   hakija-henkilo   => valid-against-current-schema?)
(fact "Meta test: hakija-yritys"    hakija-yritys    => valid-against-current-schema?)
(fact "Meta test: paasuunnittelija" paasuunnittelija => valid-against-current-schema?)
(fact "Meta test: suunnittelija1"   suunnittelija1   => valid-against-current-schema?)
(fact "Meta test: suunnittelija2"   suunnittelija2   => valid-against-current-schema?)
(fact "Meta test: maksaja-henkilo"  maksaja-henkilo  => valid-against-current-schema?)
(fact "Meta test: maksaja-yritys"   maksaja-yritys   => valid-against-current-schema?)
(fact "Meta test: tyonjohtaja"      tyonjohtaja      => valid-against-current-schema?)
(fact "Meta test: rakennuspaikka"   rakennuspaikka   => valid-against-current-schema?)
(fact "Meta test: uusi-rakennus"    uusi-rakennus    => valid-against-current-schema?)
(fact "Meta test: lisatieto"        lisatieto        => valid-against-current-schema?)
(fact "Meta test: hankkeen-kuvaus"  hankkeen-kuvaus  => valid-against-current-schema?)
(fact "Meta test: hankkeen-kuvaus-minimum"  hankkeen-kuvaus-minimum  => valid-against-current-schema?)

;; In case a document was added but forgot to write test above
(validate-all-documents documents)

(def application-rakennuslupa
  {:id "LP-753-2013-00001"
   :permitType "R"
   :municipality municipality
   :auth [{:lastName "Panaani"
           :firstName "Pena"
           :username "pena"
           :type "owner"
           :role "owner"
           :id "777777777777777777000020"}]
   :state "open"
   :opened 1354532324658
   :location {:x 408048, :y 6693225},
   :attachments [],
   :authority {:id "777777777777777777000023"
               :username "sonja"
               :firstName "Sonja"
               :lastName "Sibbo"
               :role "authority"}
   :title "s"
   :created 1354532324658
   :documents documents
   :propertyId "21111111111111"
   :modified 1354532324691
   :address "Katutie 54"
   :statements [{:given 1368080324142
                 :id "518b3ee60364ff9a63c6d6a1"
                 :person {:text "Paloviranomainen"
                          :name "Sonja Sibbo"
                          :email "sonja.sibbo@sipoo.fi"
                          :id "516560d6c2e6f603beb85147"}
                 :requested 1368080102631
                 :status "condition"
                 :text "Savupiippu pit\u00e4\u00e4 olla."}]})

(def application-tyonjohtajan-nimeaminen
  (merge application-rakennuslupa {:id "LP-753-2013-00002"
                                   :organization "753-R"
                                   :state "submitted"
                                   :operations [{:name "tyonjohtajan-nimeaminen"
                                                 :id "5272668be8db5aaa01084601"
                                                 :created 1383229067483}]
                                   :documents [hakija-henkilo
                                               maksaja-henkilo
                                               tyonjohtaja
                                               hankkeen-kuvaus-minimum]
                                   :linkPermitData [link-permit-data-kuntalupatunnus]
                                   :appsLinkingToUs [app-linking-to-us]}))

(def application-suunnittelijan-nimeaminen
  (merge application-rakennuslupa {:id "LP-753-2013-00003"
                                   :organization "753-R"
                                   :state "submitted"
                                   :propertyId "75341600550007"
                                   :operations [{:name "suunnittelijan-nimeaminen"
                                                 :id "527b3392e8dbbb95047a89de"
                                                 :created 1383805842761}]
                                   :documents [hakija-henkilo
                                               maksaja-henkilo
                                               suunnittelija1
                                               hankkeen-kuvaus-minimum]
                                   :linkPermitData [link-permit-data-lupapistetunnus]
                                   :appsLinkingToUs [app-linking-to-us]}))


(defn- validate-minimal-person [person]
  (fact person => (contains {:nimi {:etunimi "Pena" :sukunimi "Penttil\u00e4"}})))

(defn- validate-address [address]
  (let [person-katu (:teksti (:osoitenimi address))
        person-postinumero (:postinumero address)
        person-postitoimipaikannimi (:postitoimipaikannimi address)]
    (fact address => truthy)
    (fact person-katu => "katu")
    (fact person-postinumero =>"33800")
    (fact person-postitoimipaikannimi => "Tuonela")))

(defn- validate-contact [m]
  (fact m => (contains {:puhelin "+358401234567"
                        :sahkopostiosoite "pena@example.com"})))

(defn- validate-person-wo-ssn [person]
  (validate-minimal-person person)
  (validate-contact person)
  (validate-address (:osoite person)))

(defn- validate-person [person]
  (validate-person-wo-ssn person)
  (fact (:henkilotunnus person) => "210281-9988"))

(defn- validate-minimal-company [company]
  (fact company => (contains {:nimi "Solita Oy" :liikeJaYhteisotunnus "1060155-5"}))
  ; postiosoite is required in KRYSP Rakennusvalvonta
  (validate-address (:postiosoite company)))

(defn- validate-company [company]
  (validate-minimal-company company)
  (fact "puhelin" (:puhelin company) => "03-389 1380")
  (fact "sahkopostiosoite" (:sahkopostiosoite company) => "solita@solita.fi"))

(facts "Canonical hakija/henkilo model is correct"
  (let [hakija-model (get-osapuoli-data (:data hakija-henkilo) :hakija)
        henkilo (:henkilo hakija-model)
        ht (:henkilotiedot henkilo)
        yritys (:yritys hakija-model)]
    (fact "model" hakija-model => truthy)
    (fact "kuntaRooliKoodi" (:kuntaRooliKoodi hakija-model) => "Rakennusvalvonta-asian hakija")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi hakija-model) => "hakija")
    (fact "turvakieltoKytkin" (:turvakieltoKytkin hakija-model) => true)
    (validate-person henkilo)
    (fact "yritys is nil" yritys => nil)))

(facts "Canonical hakija/yritys model is correct"
  (let [hakija-model (get-osapuoli-data (:data hakija-yritys) :hakija)
        henkilo (:henkilo hakija-model)
        yritys (:yritys hakija-model)]
    (fact "model" hakija-model => truthy)
    (fact "kuntaRooliKoodi" (:kuntaRooliKoodi hakija-model) => "Rakennusvalvonta-asian hakija")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi hakija-model) => "hakija")
    (fact "turvakieltoKytkin" (:turvakieltoKytkin hakija-model) => true)
    (validate-minimal-person henkilo)
    (validate-company yritys)))

(fact "Empty body"
  (empty? (get-parties-by-type
    {"paasuunnittelija" [{:data {}}]} :Suunnittelija ["paasuunnittelija"] get-suunnittelija-data)) => truthy)

(facts "Canonical paasuunnittelija/henkilo+yritys model is correct"
  (let [suunnittelija-model (get-suunnittelija-data (:data paasuunnittelija) :paasuunnittelija)
        henkilo (:henkilo suunnittelija-model)
        yritys (:yritys suunnittelija-model)]
    (fact "model" suunnittelija-model => truthy)
    (fact "suunnittelijaRoolikoodi" (:suunnittelijaRoolikoodi suunnittelija-model) => "p\u00e4\u00e4suunnittelija")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi suunnittelija-model) => "p\u00e4\u00e4suunnittelija")
    (fact "koulutus" (:koulutus suunnittelija-model) => "Arkkitehti")
    (fact "patevyysvaatimusluokka" (:patevyysvaatimusluokka suunnittelija-model) => "ei tiedossa")
    (fact "valmistumisvuosi" (:valmistumisvuosi suunnittelija-model) => "2010")
    (fact "kokemusvuodet" (:kokemusvuodet suunnittelija-model) => "5")
    (validate-person henkilo)
    (validate-minimal-company yritys)))

(facts "Canonical suunnittelija1 model is correct"
  (let [suunnittelija-model (get-suunnittelija-data (:data suunnittelija1) :suunnittelija)]
    (fact "model" suunnittelija-model => truthy)
    (fact "suunnittelijaRoolikoodi" (:suunnittelijaRoolikoodi suunnittelija-model) => "ARK-rakennussuunnittelija")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi suunnittelija-model) => "rakennussuunnittelija")
    (fact "koulutus" (:koulutus suunnittelija-model) => "Arkkitehti")
    (fact "patevyysvaatimusluokka" (:patevyysvaatimusluokka suunnittelija-model) => "B")
    (fact "valmistumisvuosi" (:valmistumisvuosi suunnittelija-model) => "2010")
    (fact "kokemusvuodet" (:kokemusvuodet suunnittelija-model) => "5")
    (fact "henkilo" (:henkilo suunnittelija-model) => truthy)
    (fact "yritys" (:yritys suunnittelija-model) => truthy)))

(facts "Canonical suunnittelija2 model is correct"
  (let [suunnittelija-model (get-suunnittelija-data (:data suunnittelija2) :suunnittelija)]
    (fact "model" suunnittelija-model => truthy)
    (fact "suunnittelijaRoolikoodi" (:suunnittelijaRoolikoodi suunnittelija-model) => "GEO-suunnittelija")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi suunnittelija-model) => "erityissuunnittelija")
    (fact "koulutus" (:koulutus suunnittelija-model) => "El\u00e4m\u00e4n koulu")
    (fact "patevyysvaatimusluokka" (:patevyysvaatimusluokka suunnittelija-model) => "AA")
    (fact "valmistumisvuosi" (:valmistumisvuosi suunnittelija-model) => "2010")
    (fact "kokemusvuodet" (:kokemusvuodet suunnittelija-model) => "5")
    (fact "henkilo" (:henkilo suunnittelija-model) => truthy)
    (fact "yritys" (:yritys suunnittelija-model) => truthy)))

(facts "Transforming old sunnittelija schema to canonical model is correct"
  (let [suunnittelija-model (get-suunnittelija-data (:data suunnittelija-old-schema-LUPA-771) :suunnittelija)]
    (fact "model" suunnittelija-model => truthy)
    (fact "suunnittelijaRoolikoodi" (:suunnittelijaRoolikoodi suunnittelija-model) => "ARK-rakennussuunnittelija")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi suunnittelija-model) => "rakennussuunnittelija")
    (fact "koulutus" (:koulutus suunnittelija-model) => "Arkkitehti")
    (fact "patevyysvaatimusluokka" (:patevyysvaatimusluokka suunnittelija-model) => "B")
    (fact "valmistumisvuosi" (:valmistumisvuosi suunnittelija-model) => "2010")
    (fact "kokemusvuodet" (:kokemusvuodet suunnittelija-model) => "5")))

(facts "Canonical suunnittelija-blank-role model is correct"
  (let [suunnittelija-model (get-suunnittelija-data (:data suunnittelija-blank-role) :suunnittelija)]
    (fact "model" suunnittelija-model => truthy)
    (fact "suunnittelijaRoolikoodi" (:suunnittelijaRoolikoodi suunnittelija-model) => "ei tiedossa")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi suunnittelija-model) => "ei tiedossa")))

(facts "Canonical tyonjohtaja model is correct"
  (let [tyonjohtaja-model (get-tyonjohtaja-data (:data tyonjohtaja) :tyonjohtaja)
        henkilo (:henkilo tyonjohtaja-model)
        yritys (:yritys tyonjohtaja-model)]
    (fact "model" tyonjohtaja-model => truthy)
    (fact "tyonjohtajaRooliKoodi" (:tyonjohtajaRooliKoodi tyonjohtaja-model) => "KVV-ty\u00f6njohtaja")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi tyonjohtaja-model) => "ty\u00f6njohtaja")
    (fact "koulutus" (:koulutus tyonjohtaja-model) => "Koulutus")
    (fact "valmistumisvuosi" (:valmistumisvuosi tyonjohtaja-model) => "2010")
    (fact "patevyysvaatimusluokka" (:patevyysvaatimusluokka tyonjohtaja-model) => "AA")
    (fact "kokemusvuodet" (:kokemusvuodet tyonjohtaja-model) => "3")
    (fact "valvottavienKohteidenMaara" (:valvottavienKohteidenMaara tyonjohtaja-model) => "9")
    (fact "tyonjohtajaHakemusKytkin" (:tyonjohtajaHakemusKytkin tyonjohtaja-model) => true)
    (fact "vastattavatTyotehtavat" (:vastattavatTyotehtavat tyonjohtaja-model) =>
      "kiinteistonilmanvaihtolaitteistonRakentaminen,rakennelmaTaiLaitos,maanrakennustyo,kiinteistonVesiJaViemarilaitteistonRakentaminen,Muu tyotehtava")
    (fact "henkilo" (:henkilo tyonjohtaja-model) => truthy)
    (fact "yritys" (:yritys tyonjohtaja-model) => truthy)
    (validate-person henkilo)
    (validate-minimal-company yritys)))

(facts "Canonical tyonjohtaja-blank-role-and-blank-qualification model is correct"
  (let [tyonjohtaja-model (get-tyonjohtaja-data (:data tyonjohtaja-blank-role-and-blank-qualification) :tyonjohtaja)]
    (fact "model" tyonjohtaja-model => truthy)
    (fact "tyonjohtajaRooliKoodi" (:tyonjohtajaRooliKoodi tyonjohtaja-model) => "ei tiedossa")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi tyonjohtaja-model) => "ei tiedossa")
    (fact "patevyysvaatimusluokka" (:patevyysvaatimusluokka tyonjohtaja-model) => "Ei tiedossa")
    (fact "tyonjohtajaHakemusKytkin" (:tyonjohtajaHakemusKytkin tyonjohtaja-model) => false)))

(facts "Canonical maksaja/henkilo model is correct"
  (let [maksaja-model (get-osapuoli-data (:data maksaja-henkilo) :maksaja)
        henkilo (:henkilo maksaja-model)
        yritys (:yritys maksaja-model)]
    (fact "model" maksaja-model => truthy)
    (fact "kuntaRooliKoodi" (:kuntaRooliKoodi maksaja-model) => "Rakennusvalvonta-asian laskun maksaja")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi maksaja-model) => "maksaja")
    (fact "turvakieltoKytkin" (:turvakieltoKytkin maksaja-model) => true)
    (validate-person henkilo)
    (fact "yritys is nil" yritys => nil)))

(facts "Canonical maksaja/yritys model is correct"
  (let [maksaja-model (get-osapuoli-data (:data maksaja-yritys) :maksaja)
        henkilo (:henkilo maksaja-model)
        yritys (:yritys maksaja-model)]
    (fact "model" maksaja-model => truthy)
    (fact "kuntaRooliKoodi" (:kuntaRooliKoodi maksaja-model) => "Rakennusvalvonta-asian laskun maksaja")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi maksaja-model) => "maksaja")
    (validate-minimal-person henkilo)
    (validate-company yritys)))

(testable-privates lupapalvelu.document.canonical-common get-handler)

(facts "Handler is sonja"
  (let [handler (get-handler application-rakennuslupa)
        name (get-in handler [:henkilo :nimi])]
    (fact "handler" handler => truthy)
    (fact "etunimi" (:etunimi name) => "Sonja")
    (fact "sukunimi" (:sukunimi name) => "Sibbo")))

(testable-privates lupapalvelu.document.rakennuslupa_canonical get-operations)

(facts "Toimenpiteet"
  (let [documents (by-type (:documents application-rakennuslupa))
        actions (get-operations documents application-rakennuslupa)]
    ;(clojure.pprint/pprint actions)
    (fact "actions" (seq actions) => truthy)))

(testable-privates lupapalvelu.document.rakennuslupa_canonical get-huoneisto-data)

(facts "Huoneisto is correct"
  (let [huoneistot (get-huoneisto-data (get-in uusi-rakennus [:data :huoneistot]))
        h1 (first huoneistot), h2 (last huoneistot)]
    (fact "h1 huoneistot count" (count huoneistot) => 2)
    (fact "h1 muutostapa" (:muutostapa h1) => "lis\u00e4ys")
    (fact "h1 huoneluku" (:huoneluku h1) => "66")
    (fact "h1 keittionTyyppi" (:keittionTyyppi h1) => "keittio")
    (fact "h1 huoneistoala" (:huoneistoala h1) => "56")
    (fact "h1 huoneistonTyyppi" (:huoneistonTyyppi h1) => "asuinhuoneisto")
    (fact "h1 varusteet: WCKytkin" (-> h1 :varusteet :WCKytkin) => true)
    (fact "h1 varusteet: ammeTaiSuihkuKytkin" (-> h1 :varusteet :ammeTaiSuihkuKytkin) => false)
    (fact "h1 varusteet: saunaKytkin" (-> h1 :varusteet :saunaKytkin) => false)
    (fact "h1 varusteet: parvekeTaiTerassiKytkin" (-> h1 :varusteet :parvekeTaiTerassiKytkin) => true)
    (fact "h1 varusteet: lamminvesiKytkin" (-> h1 :varusteet :lamminvesiKytkin) => false)
    (fact "h1 huoneistotunnus" (:huoneistotunnus h1) => truthy)
    (fact "h1 huoneistotunnus: porras" (-> h1 :huoneistotunnus :porras) => "A")
    (fact "h1 huoneistotunnus: huoneistonumero" (-> h1 :huoneistotunnus :huoneistonumero) => "001")
    (fact "h1 huoneistotunnus: jakokirjain" (-> h1 :huoneistotunnus :jakokirjain) => "a")

    (fact "h2 muutostapa" (:muutostapa h2) => "lis\u00e4ys")
    (fact "h2 huoneluku" (:huoneluku h2) => "12")
    (fact "h2 keittionTyyppi" (:keittionTyyppi h2) => "keittokomero")
    (fact "h2 huoneistoala" (:huoneistoala h2) => "02")
    (fact "h2 huoneistonTyyppi" (:huoneistonTyyppi h2) => "toimitila")
    (fact "h2 varusteet: WCKytkin" (-> h2 :varusteet :WCKytkin) => false)
    (fact "h2 varusteet: ammeTaiSuihkuKytkin" (-> h2 :varusteet :ammeTaiSuihkuKytkin) => true)
    (fact "h2 varusteet: saunaKytkin" (-> h2 :varusteet :saunaKytkin) => true)
    (fact "h2 varusteet: parvekeTaiTerassiKytkin" (-> h2 :varusteet :parvekeTaiTerassiKytkin) => false)
    (fact "h2 varusteet: lamminvesiKytkin" (-> h2 :varusteet :lamminvesiKytkin) => true)
    (fact "h2 huoneistotunnus" (:huoneistotunnus h2) => falsey)))

(testable-privates lupapalvelu.document.rakennuslupa_canonical get-rakennus)

(facts "When muu-lammonlahde is empty, lammonlahde is used"
  (let [rakennus (get-rakennus {:lammitys {:lammitystapa {:value nil}
                                           :lammonlahde  {:value "turve"}
                                           :muu-lammonlahde {:value nil}} }
                               {:id "123" :created nil} application-rakennuslupa)]
    (fact (:polttoaine (:lammonlahde (:rakennuksenTiedot rakennus))) => "turve")))

(facts "When muu-lammonlahde is specified, it is used"
  (let [rakennus (get-rakennus {:lammitys {:lammitystapa {:value nil}
                                           :lammonlahde  {:value "other"}
                                           :muu-lammonlahde {:value "fuusioenergialla"}} }
                               {:id "123" :created nil} application-rakennuslupa)]
    (fact (:muu (:lammonlahde (:rakennuksenTiedot rakennus))) => "fuusioenergialla")))

(fl/facts* "Canonical model is correct"
  (let [canonical (application-to-canonical application-rakennuslupa "sv") => truthy
        rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
        rakennusvalvontaasiatieto (:rakennusvalvontaAsiatieto rakennusvalvonta) => truthy
        rakennusvalvontaasia (:RakennusvalvontaAsia rakennusvalvontaasiatieto) => truthy

        toimituksenTiedot (:toimituksenTiedot rakennusvalvonta) => truthy
        aineistonnimi (:aineistonnimi toimituksenTiedot ) => "s"
        lausuntotieto (first (:lausuntotieto rakennusvalvontaasia))  => truthy
        Lausunto (:Lausunto lausuntotieto) => truthy
        viranomainen (:viranomainen Lausunto) => "Paloviranomainen"
        pyyntoPvm (:pyyntoPvm Lausunto) => "2013-05-09"
        lausuntotieto (:lausuntotieto Lausunto) => truthy
        LL (:Lausunto lausuntotieto) => truthy  ;Lausunto oli jo kaytossa, siksi LL
        viranomainen (:viranomainen LL) => "Paloviranomainen"
        lausunto (:lausunto LL) => "Savupiippu pit\u00e4\u00e4 olla."
        lausuntoPvm (:lausuntoPvm LL) => "2013-05-09"
        puoltotieto (:puoltotieto LL) => truthy
        Puolto (:Puolto puoltotieto) => truthy
        puolto (:puolto Puolto) => "ehdoilla"

        osapuolettieto (:osapuolettieto rakennusvalvontaasia) => truthy
        osapuolet (:Osapuolet osapuolettieto) => truthy
        osapuolitieto-hakija (first (:osapuolitieto osapuolet)) => truthy
        hakija-osapuoli1 (:Osapuoli osapuolitieto-hakija) => truthy
        suunnittelijat (:suunnittelijatieto osapuolet) => truthy
        paasuunnitelija (:Suunnittelija (last suunnittelijat)) => truthy
        tyonjohtajat (:tyonjohtajatieto osapuolet) => truthy
        tyonjohtajatieto (:Tyonjohtaja (last tyonjohtajat)) => truthy
        rakennuspaikkatiedot (:rakennuspaikkatieto rakennusvalvontaasia) => truthy
        rakennuspaikkatieto (first rakennuspaikkatiedot) => truthy
        rakennuspaikka (:Rakennuspaikka rakennuspaikkatieto) => truthy
        rakennuspaikanKiinteistotieto (:rakennuspaikanKiinteistotieto rakennuspaikka) => truthy
        RakennuspaikanKiinteistotieto (:RakennuspaikanKiinteisto rakennuspaikanKiinteistotieto) => truthy
        kiinteistotieto (:kiinteistotieto RakennuspaikanKiinteistotieto) => truthy
        Kiinteisto (:Kiinteisto kiinteistotieto) => truthy
        toimenpiteet(:toimenpidetieto rakennusvalvontaasia) => truthy
        toimenpide (:Toimenpide (nth toimenpiteet 1)) => truthy
        muu-muutostyo (:Toimenpide (nth toimenpiteet 0)) => truthy
        laajennus-t (:Toimenpide (nth toimenpiteet 2)) => truthy
        purku-t (:Toimenpide (nth toimenpiteet 3)) => truthy
        kaupunkikuva-t (:Toimenpide (nth toimenpiteet 4)) => truthy
        rakennustieto (:rakennustieto toimenpide) => truthy
        rakennus (:Rakennus rakennustieto) => truthy
        rakennuksen-omistajatieto (:Omistaja(first (:omistajatieto rakennus))) => truthy
        rakennuksentiedot (:rakennuksenTiedot rakennus) => truthy
        lisatiedot (:lisatiedot rakennusvalvontaasia) => truthy
        Lisatiedot (:Lisatiedot lisatiedot) => truthy
        kayttotapaus (:kayttotapaus rakennusvalvontaasia) => truthy
        asianTiedot (:asianTiedot rakennusvalvontaasia) => truthy
        Asiantiedot (:Asiantiedot asianTiedot) => truthy
        vahainen-poikkeaminen (:vahainenPoikkeaminen Asiantiedot) => truthy
        rakennusvalvontasian-kuvaus (:rakennusvalvontaasianKuvaus Asiantiedot) => truthy
        luvanTunnisteTiedot (:luvanTunnisteTiedot rakennusvalvontaasia) => truthy
        LupaTunnus (:LupaTunnus luvanTunnisteTiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        MuuTunnus (:MuuTunnus muuTunnustieto) => truthy
        kasittelynTilatieto (:kasittelynTilatieto rakennusvalvontaasia) => truthy]

    ;(clojure.pprint/pprint canonical)

    (fact "contains nil" (contains-value? canonical nil?) => falsey)
    (fact "paasuunnitelija" paasuunnitelija => (contains {:suunnittelijaRoolikoodi "p\u00e4\u00e4suunnittelija"}))
    (fact "Osapuolien maara" (+ (count suunnittelijat) (count tyonjohtajat) (count (:osapuolitieto osapuolet))) => 8)
    (fact "rakennuspaikkojen maara" (count rakennuspaikkatiedot) => 1)
    (fact "tilanNimi" (:tilannimi Kiinteisto) => "Hiekkametsa")
    (fact "kiinteistotunnus" (:kiinteistotunnus Kiinteisto) => "21111111111111")
    (fact "maaraalaTunnus" (:maaraAlaTunnus Kiinteisto) => nil)
    (fact "kokotilakytkin" (:kokotilaKytkin RakennuspaikanKiinteistotieto) => truthy)
    (fact "hallintaperuste" (:hallintaperuste RakennuspaikanKiinteistotieto) => "oma")

    (fact "Toimenpidetieto"  (count toimenpiteet) => 5)
    (fact "rakentajaTyyppi" (:rakentajatyyppi rakennus) => "muu")
    (fact "kayttotarkoitus" (:kayttotarkoitus rakennuksentiedot) => "012 kahden asunnon talot")
    (fact "rakentamistapa" (:rakentamistapa rakennuksentiedot) => "elementti")
    (fact "rakennuksen omistajalaji" (:omistajalaji (:omistajalaji rakennuksen-omistajatieto)) => "muu yksityinen henkil\u00f6 tai perikunta")
    (fact "KuntaRooliKoodi" (:kuntaRooliKoodi rakennuksen-omistajatieto) => "Rakennuksen omistaja")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi rakennuksen-omistajatieto) => "rakennuksen omistaja")
    (fact "Lisatiedot suoramarkkinointikielto" (:suoramarkkinointikieltoKytkin Lisatiedot) => true)
    (fact "Lisatiedot asiointikieli" (:asioimiskieli Lisatiedot) => "ruotsi")
    (fact "rakennusvalvontasian-kuvaus" rakennusvalvontasian-kuvaus =>"Uuden rakennuksen rakentaminen tontille.\n\nPuun kaataminen:Puun kaataminen")
    (fact "kayttotapaus" kayttotapaus => "Uusi hakemus")

    (fact "Muu tunnus" (:tunnus MuuTunnus) => "LP-753-2013-00001")
    (fact "Sovellus" (:sovellus MuuTunnus) => "Lupapiste")
    (fact "Toimenpiteen kuvaus" (-> toimenpide :uusi :kuvaus) => "Asuinrakennuksen rakentaminen")
    (fact "Toimenpiteen kuvaus" (-> muu-muutostyo :muuMuutosTyo :kuvaus) => "Muu rakennuksen muutosty\u00f6")
    (fact "Muu muutostyon perusparannuskytkin" (-> muu-muutostyo :muuMuutosTyo :perusparannusKytkin) => true)
    (fact "Muutostyon laji" (-> muu-muutostyo :muuMuutosTyo :muutostyonLaji) => "muut muutosty\u00f6t")
    (fact "Laajennuksen kuvaus" (-> laajennus-t :laajennus :kuvaus) => "Rakennuksen laajentaminen tai korjaaminen")
    (fact "muu muutostyon rakennuksen tunnus" (-> muu-muutostyo :rakennustieto :Rakennus :rakennuksenTiedot :rakennustunnus :jarjestysnumero) => 2)
    (fact "Laajennuksen rakennuksen tunnus" (-> laajennus-t :rakennustieto :Rakennus :rakennuksenTiedot :rakennustunnus :jarjestysnumero) => 3)
    (fact "Laajennuksen rakennuksen kiintun" (-> laajennus-t :rakennustieto :Rakennus :rakennuksenTiedot :rakennustunnus :kiinttun) => "21111111111111")
    (fact "Laajennuksen pintaalat" (count (-> laajennus-t :laajennus :laajennuksentiedot :huoneistoala )) => 2)
    (fact "Purkamisen kuvaus" (-> purku-t :purkaminen :kuvaus) => "Rakennuksen purkaminen")
    (fact "Poistuma pvm" (-> purku-t :purkaminen :poistumaPvm) => "2013-04-17")
    (fact "Kaupunkikuvatoimenpiteen kuvaus" (-> kaupunkikuva-t :kaupunkikuvaToimenpide :kuvaus) => "Aidan rakentaminen")
    (fact "Kaupunkikuvatoimenpiteen rakennelman kuvaus" (-> kaupunkikuva-t :rakennelmatieto :Rakennelma :kuvaus :kuvaus) => "Aidan rakentaminen rajalle")))

(testable-privates lupapalvelu.document.rakennuslupa_canonical get-viitelupatieto)

(fl/facts* "Canonical model for tyonjohtajan nimeaminen is correct"
  (let [canonical (application-to-canonical application-tyonjohtajan-nimeaminen "fi") => truthy
        rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
        rakennusvalvontaasiatieto (:rakennusvalvontaAsiatieto rakennusvalvonta) => truthy
        rakennusvalvontaasia (:RakennusvalvontaAsia rakennusvalvontaasiatieto) => truthy

        viitelupatieto (:viitelupatieto rakennusvalvontaasia) => truthy
        viitelupatieto-LupaTunnus (:LupaTunnus viitelupatieto) => truthy
        viitelupatieto-MuuTunnus (-> viitelupatieto-LupaTunnus :muuTunnustieto :MuuTunnus) => falsey

        luvanTunnisteTiedot-MuuTunnus (-> rakennusvalvontaasia
                                        :luvanTunnisteTiedot
                                        :LupaTunnus
                                        :muuTunnustieto
                                        :MuuTunnus) => truthy


        osapuolet-vec (-> rakennusvalvontaasia :osapuolettieto :Osapuolet :osapuolitieto) => truthy

        ;; henkilotyyppinen maksaja
        rooliKoodi-laskun-maksaja "Rakennusvalvonta-asian laskun maksaja"
        maksaja-filter-fn #(= (-> % :Osapuoli :kuntaRooliKoodi) rooliKoodi-laskun-maksaja)
        maksaja-Osapuoli (:Osapuoli (first (filter maksaja-filter-fn osapuolet-vec)))
        maksaja-Osapuoli-henkilo (:henkilo maksaja-Osapuoli)
        maksaja-Osapuoli-yritys (:yritys maksaja-Osapuoli)

        kayttotapaus (:kayttotapaus rakennusvalvontaasia) => truthy
        Asiantiedot (-> rakennusvalvontaasia :asianTiedot :Asiantiedot) => truthy
        vahainen-poikkeaminen (:vahainenPoikkeaminen Asiantiedot) => falsey
        rakennusvalvontasian-kuvaus (:rakennusvalvontaasianKuvaus Asiantiedot) => truthy

        viitelupatieto-LupaTunnus_2 (:LupaTunnus (get-viitelupatieto link-permit-data-lupapistetunnus))]

    (facts "Maksaja is correct"
      (fact "kuntaRooliKoodi" (:kuntaRooliKoodi maksaja-Osapuoli) => "Rakennusvalvonta-asian laskun maksaja")
      (fact "VRKrooliKoodi" (:VRKrooliKoodi maksaja-Osapuoli) => "maksaja")
      (fact "turvakieltoKytkin" (:turvakieltoKytkin maksaja-Osapuoli) => true)
      (validate-person maksaja-Osapuoli-henkilo)
      (fact "yritys is nil" maksaja-Osapuoli-yritys => nil))

    (facts "\"kuntalupatunnus\" type of link permit data"
      (fact "viitelupatieto-MuuTunnus-Tunnus" (-> viitelupatieto-LupaTunnus :muuTunnustieto :MuuTunnus :tunnus) => falsey)
      (fact "viitelupatieto-MuuTunnus-Sovellus" (-> viitelupatieto-LupaTunnus :muuTunnustieto :MuuTunnus :sovellus) => falsey)
      (fact "viitelupatieto-kuntalupatunnus" (:kuntalupatunnus viitelupatieto-LupaTunnus) => "123-123-123-123")
      (fact "viitelupatieto-viittaus" (:viittaus viitelupatieto-LupaTunnus) => "edellinen rakennusvalvonta-asia"))

    (facts "\"lupapistetunnus\" type of link permit data"
      (fact "viitelupatieto-MuuTunnus-Tunnus" (-> viitelupatieto-LupaTunnus_2 :muuTunnustieto :MuuTunnus :tunnus) => "LP-753-2013-00002")
      (fact "viitelupatieto-MuuTunnus-Sovellus" (-> viitelupatieto-LupaTunnus_2 :muuTunnustieto :MuuTunnus :sovellus) => "Lupapiste")
      (fact "viitelupatieto-kuntalupatunnus" (:kuntalupatunnus viitelupatieto-LupaTunnus_2) => falsey)
      (fact "viitelupatieto-viittaus" (:viittaus viitelupatieto-LupaTunnus_2) => "edellinen rakennusvalvonta-asia"))


    (fact "luvanTunnisteTiedot-MuuTunnus-Tunnus" (:tunnus luvanTunnisteTiedot-MuuTunnus) => "LP-753-2013-00002")
    (fact "luvanTunnisteTiedot-MuuTunnus-Sovellus" (:sovellus luvanTunnisteTiedot-MuuTunnus) => "Lupapiste")

    (fact "rakennusvalvontasian-kuvaus" rakennusvalvontasian-kuvaus => "Uuden rakennuksen rakentaminen tontille.")
    (fact "kayttotapaus" kayttotapaus => "Uuden ty\u00f6njohtajan nime\u00e4minen")))


(fl/facts* "Canonical model for suunnittelijan nimeaminen is correct"
  (let [canonical (application-to-canonical application-suunnittelijan-nimeaminen "fi") => truthy
        rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
        rakennusvalvontaasiatieto (:rakennusvalvontaAsiatieto rakennusvalvonta) => truthy
        rakennusvalvontaasia (:RakennusvalvontaAsia rakennusvalvontaasiatieto) => truthy

        viitelupatieto (:viitelupatieto rakennusvalvontaasia) => truthy
        viitelupatieto-LupaTunnus (:LupaTunnus viitelupatieto) => truthy
        viitelupatieto-MuuTunnus (-> viitelupatieto-LupaTunnus :muuTunnustieto :MuuTunnus) => truthy

        osapuolet-vec (-> rakennusvalvontaasia :osapuolettieto :Osapuolet :osapuolitieto) => truthy

        ;; henkilotyyppinen maksaja
        rooliKoodi-laskun-maksaja "Rakennusvalvonta-asian laskun maksaja"
        maksaja-filter-fn #(= (-> % :Osapuoli :kuntaRooliKoodi) rooliKoodi-laskun-maksaja)
        maksaja-Osapuoli (:Osapuoli (first (filter maksaja-filter-fn osapuolet-vec)))
        maksaja-Osapuoli-henkilo (:henkilo maksaja-Osapuoli)
        maksaja-Osapuoli-yritys (:yritys maksaja-Osapuoli)

        luvanTunnisteTiedot-MuuTunnus (-> rakennusvalvontaasia
                                        :luvanTunnisteTiedot
                                        :LupaTunnus
                                        :muuTunnustieto
                                        :MuuTunnus) => truthy

        kayttotapaus (:kayttotapaus rakennusvalvontaasia) => truthy
        Asiantiedot (-> rakennusvalvontaasia :asianTiedot :Asiantiedot) => truthy
        vahainen-poikkeaminen (:vahainenPoikkeaminen Asiantiedot) => falsey
        rakennusvalvontasian-kuvaus (:rakennusvalvontaasianKuvaus Asiantiedot) => truthy]

    (facts "Maksaja is correct"
      (fact "kuntaRooliKoodi" (:kuntaRooliKoodi maksaja-Osapuoli) => "Rakennusvalvonta-asian laskun maksaja")
      (fact "VRKrooliKoodi" (:VRKrooliKoodi maksaja-Osapuoli) => "maksaja")
      (fact "turvakieltoKytkin" (:turvakieltoKytkin maksaja-Osapuoli) => true)
      (validate-person maksaja-Osapuoli-henkilo)
      (fact "yritys is nil" maksaja-Osapuoli-yritys => nil))

    (facts "\"lupapistetunnus\" type of link permit data"
      (fact "viitelupatieto-MuuTunnus-Tunnus" (-> viitelupatieto-LupaTunnus :muuTunnustieto :MuuTunnus :tunnus) => "LP-753-2013-00002")
      (fact "viitelupatieto-MuuTunnus-Sovellus" (-> viitelupatieto-LupaTunnus :muuTunnustieto :MuuTunnus :sovellus) => "Lupapiste")
      (fact "viitelupatieto-kuntalupatunnus" (:kuntalupatunnus viitelupatieto-LupaTunnus) => falsey)
      (fact "viitelupatieto-viittaus" (:viittaus viitelupatieto-LupaTunnus) => "edellinen rakennusvalvonta-asia"))


    (fact "luvanTunnisteTiedot-MuuTunnus-Tunnus" (:tunnus luvanTunnisteTiedot-MuuTunnus) => "LP-753-2013-00003")
    (fact "luvanTunnisteTiedot-MuuTunnus-Sovellus" (:sovellus luvanTunnisteTiedot-MuuTunnus) => "Lupapiste")

    (fact "rakennusvalvontasian-kuvaus" rakennusvalvontasian-kuvaus => "Uuden rakennuksen rakentaminen tontille.")
    (fact "kayttotapaus" kayttotapaus => "Uuden suunnittelijan nime\u00e4minen")))

(def ^:private authority-user-jussi {:id "777777777777777777000017"
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
                                     :city "Tampere"})

(fl/facts* "Canonical model for aloitusilmoitus is correct"
           (let [canonical (katselmus-canonical
                             (assoc application-rakennuslupa :state "verdictGiven")
                             "sv"
                             1354532324658
                             {:rakennusnro "002" :jarjestysnumero 1}
                             authority-user-jussi
                             "Aloitusilmoitus" :katselmus nil nil nil nil nil nil)
                 Rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
                 toimituksenTiedot (:toimituksenTiedot Rakennusvalvonta) => truthy
                 kuntakoodi (:kuntakoodi toimituksenTiedot) => truthy
                 rakennusvalvontaAsiatieto (:rakennusvalvontaAsiatieto Rakennusvalvonta) => truthy
                 RakennusvalvontaAsia (:RakennusvalvontaAsia rakennusvalvontaAsiatieto) => truthy
                 kasittelynTilatieto (:kasittelynTilatieto RakennusvalvontaAsia)
                 Tilamuutos (:Tilamuutos kasittelynTilatieto) => truthy
                 tila (:tila Tilamuutos) => "p\u00e4\u00e4t\u00f6s toimitettu"

                 luvanTunnisteTiedot (:luvanTunnisteTiedot RakennusvalvontaAsia) => truthy
                 LupaTunnus (:LupaTunnus luvanTunnisteTiedot) => truthy
                 muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
                 mt (:MuuTunnus muuTunnustieto) => truthy

                 tunnus (:tunnus mt) => "LP-753-2013-00001"
                 sovellus (:sovellus mt) => "Lupapiste"

                 osapuolettieto (:osapuolettieto RakennusvalvontaAsia) => truthy
                 Osapuolet (:Osapuolet osapuolettieto) => truthy
                 osapuolitieto (:osapuolitieto Osapuolet) => truthy
                 Osapuoli (:Osapuoli osapuolitieto) => truthy
                 kuntaRooliKoodi (:kuntaRooliKoodi Osapuoli) => "Ilmoituksen tekij\u00e4"
                 henkilo (:henkilo Osapuoli) => truthy
                 nimi (:nimi henkilo) => truthy
                 etunimi (:etunimi nimi) => "Jussi"
                 sukunimi (:sukunimi nimi) => "Viranomainen"
                 osoite (:osoite henkilo) => truthy
                 osoitenimi (-> osoite :osoitenimi :teksti) => "Katuosoite 1 a 1"
                 puhelin (:puhelin henkilo) => "1231234567"

                 katselmustieto (:katselmustieto RakennusvalvontaAsia) => truthy
                 Katselmus (:Katselmus katselmustieto) => truthy
                 rakennustunnus (:rakennustunnus Katselmus) => truthy
                 jarjestysnumero (:jarjestysnumero rakennustunnus)
                 rakennusnumero (:rakennusnro rakennustunnus) => "002"
                 kiinttun (:kiinttun rakennustunnus) => "21111111111111"
                 pitoPvm (:pitoPvm Katselmus) => "2012-12-03"
                 katselmuksenLaji (:katselmuksenLaji Katselmus)
                 tarkastuksenTaiKatselmuksenNimi (:tarkastuksenTaiKatselmuksenNimi Katselmus)
                 kayttotapaus (:kayttotapaus RakennusvalvontaAsia) => "Aloitusilmoitus"]))


(fl/facts* "Canonical model for erityissuunnitelma is correct"
           (let [canonical (unsent-attachments-to-canonical
                             (assoc application-rakennuslupa :state "verdictGiven")
                             "sv")

                 Rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
                 toimituksenTiedot (:toimituksenTiedot Rakennusvalvonta) => truthy
                 kuntakoodi (:kuntakoodi toimituksenTiedot) => truthy
                 rakennusvalvontaAsiatieto (:rakennusvalvontaAsiatieto Rakennusvalvonta) => truthy
                 RakennusvalvontaAsia (:RakennusvalvontaAsia rakennusvalvontaAsiatieto) => truthy
                 kasittelynTilatieto (:kasittelynTilatieto RakennusvalvontaAsia)
                 Tilamuutos (:Tilamuutos kasittelynTilatieto) => truthy

                 luvanTunnisteTiedot (:luvanTunnisteTiedot RakennusvalvontaAsia) => truthy
                 LupaTunnus (:LupaTunnus luvanTunnisteTiedot) => truthy
                 muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
                 mt (:MuuTunnus muuTunnustieto) => truthy

                 osapuolettieto (:osapuolettieto RakennusvalvontaAsia) => truthy
                 Osapuolet (:Osapuolet osapuolettieto) => truthy
                 osapuolitieto (:osapuolitieto Osapuolet) => truthy
                 Osapuoli (:Osapuoli osapuolitieto) => truthy
                 henkilo (:henkilo Osapuoli) => truthy
                 nimi (:nimi henkilo) => truthy
                 osoite (:osoite henkilo) => truthy]

             (fact "tila" (:tila Tilamuutos) => "p\u00e4\u00e4t\u00f6s toimitettu")
             (fact "tunnus" (:tunnus mt) => "LP-753-2013-00001")
             (fact "sovellus" (:sovellus mt) => "Lupapiste")
             (fact "kayttotapaus" (:kayttotapaus RakennusvalvontaAsia) => "Liitetiedoston lis\u00e4ys")

             (facts "Osapuolet"
               (fact "kuntaRooliKoodi" (:kuntaRooliKoodi Osapuoli) => "ei tiedossa")
               (fact "etunimi" (:etunimi nimi) => "Pena")
               (fact "sukunimi" (:sukunimi nimi) => "Penttil\u00e4")
               (fact "osoitenimi" (-> osoite :osoitenimi :teksti) => "katu")
               (fact "puhelin" (:puhelin henkilo) => "+358401234567"))))



;Jatkolupa

(def jatkolupa-application
  {:schema-version 1,
   :auth
   [{:lastName "Panaani",
     :firstName "Pena",
     :username "pena",
     :type "owner",
     :role "owner",
     :id "777777777777777777000020"}],
   :submitted 1384167310181,
   :state "submitted",
   :permitSubtype nil,
   :location {:x 411063.82824707, :y 6685145.8129883},
   :attachments [],
   :organization "753-R",
   :title "It\u00e4inen Hangelbyntie 163",
   :operations
   [{:id "5280b764420622588b2f04fc",
     :name "jatkoaika",
     :created 1384167268234}],
   :infoRequest false,
   :openInfoRequest false,
   :opened 1384167310181,
   :created 1384167268234,
   :propertyId "75340800010051",
   :documents
   [{:created 1384167268234,
     :data
     {:kuvaus
      {:modified 1384167309006,
       :value
       "Pari vuotta jatko-aikaa, ett\u00e4 saadaan rakennettua loppuun."}},
     :id "5280b764420622588b2f04fd",
     :schema-info
     {:order 1,
      :version 1,
      :name "hankkeen-kuvaus-minimum",
      :approvable true,
      :op
      {:id "5280b764420622588b2f04fc",
       :name "jatkoaika",
       :created 1384167268234},
      :removable true}}
    hakija-henkilo],
   :_software_version "1.0.5",
   :modified 1384167309006,
   :allowedAttachmentTypes
   [["hakija"
     ["valtakirja"
      "ote_kauppa_ja_yhdistysrekisterista"
      "ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta"]]
    ["rakennuspaikan_hallinta"
     ["jaljennos_myonnetyista_lainhuudoista"
      "jaljennos_kauppakirjasta_tai_muusta_luovutuskirjasta"
      "rasitustodistus"
      "todistus_erityisoikeuden_kirjaamisesta"
      "jaljennos_vuokrasopimuksesta"
      "jaljennos_perunkirjasta"]]
    ["rakennuspaikka"
     ["ote_alueen_peruskartasta"
      "ote_asemakaavasta_jos_asemakaava_alueella"
      "ote_kiinteistorekisteristerista"
      "tonttikartta_tarvittaessa"
      "selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista"
      "kiinteiston_vesi_ja_viemarilaitteiston_suunnitelma"]]
    ["paapiirustus"
     ["asemapiirros"
    "pohjapiirros"
    "leikkauspiirros"
    "julkisivupiirros"]]
    ["ennakkoluvat_ja_lausunnot"
     ["naapurien_suostumukset"
    "selvitys_naapurien_kuulemisesta"
    "elyn_tai_kunnan_poikkeamapaatos"
    "suunnittelutarveratkaisu"
    "ymparistolupa"]]
    ["muut"
     ["selvitys_rakennuspaikan_terveellisyydesta"
      "selvitys_rakennuspaikan_korkeusasemasta"
      "selvitys_liittymisesta_ymparoivaan_rakennuskantaan"
      "julkisivujen_varityssuunnitelma"
      "selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta"
      "piha_tai_istutussuunnitelma"
      "selvitys_rakenteiden_kokonaisvakavuudesta_ja_lujuudesta"
      "selvitys_rakennuksen_kosteusteknisesta_toimivuudesta"
      "selvitys_rakennuksen_aaniteknisesta_toimivuudesta"
      "selvitys_sisailmastotavoitteista_ja_niihin_vaikuttavista_tekijoista"
      "energiataloudellinen_selvitys"
      "paloturvallisuussuunnitelma"
      "liikkumis_ja_esteettomyysselvitys"
      "kerrosalaselvitys"
      "vaestonsuojasuunnitelma"
      "rakennukseen_tai_sen_osaan_kohdistuva_kuntotutkimus_jos_korjaus_tai_muutostyo"
      "selvitys_rakennuksen_rakennustaiteellisesta_ja_kulttuurihistoriallisesta_arvosta_jos_korjaus_tai_muutostyo"
      "selvitys_kiinteiston_jatehuollon_jarjestamisesta"
      "rakennesuunnitelma"
      "ilmanvaihtosuunnitelma"
      "lammityslaitesuunnitelma"
      "radontekninen_suunnitelma"
      "kalliorakentamistekninen_suunnitelma"
      "paloturvallisuusselvitys"
      "suunnitelma_paloilmoitinjarjestelmista_ja_koneellisesta_savunpoistosta"
      "merkki_ja_turvavalaistussuunnitelma"
      "sammutusautomatiikkasuunnitelma"
      "rakennusautomaatiosuunnitelma"
      "valaistussuunnitelma"
      "selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta"
      "selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta"
      "muu"]]],
   :comments [],
   :address "It\u00e4inen Hangelbyntie 163",
   :permitType "R",
   :id "LP-753-2013-00005",
   :municipality "753"
   :authority {:id "777777777777777777000023"
               :username "sonja"
               :firstName "Sonja"
               :lastName "Sibbo"
               :role "authority"}
  :linkPermitData [{:id "LP-753-2013-00001", :type "lupapistetunnus"}]})

(fl/facts* "Canonical model for jatkoaika is correct"
  (let [canonical (application-to-canonical jatkolupa-application "sv")]
    ;(clojure.pprint/pprint canonical)
    ;TODO tests
    ))

(fl/facts* "Canonical model for katselmus is correct"
           (let [canonical (katselmus-canonical
                             (assoc application-rakennuslupa :state "verdictGiven")
                             "fi"
                             1354532324658
                             {:rakennusnro "002" :jarjestysnumero 1}
                             authority-user-jussi
                             "pohjakatselmus" :katselmus "pidetty" "Sonja Silja" true "Saunan ovi pit\u00e4\u00e4 vaihtaa 900mm leve\u00e4ksi.
Piha-alue siivottava v\u00e4litt\u00f6m\u00e4sti." "Tiivi Taavi, Hipsu ja Lala" "Ei poikkeamisia")

                 Rakennusvalvonta (:Rakennusvalvonta canonical) => truthy
                 toimituksenTiedot (:toimituksenTiedot Rakennusvalvonta) => truthy
                 kuntakoodi (:kuntakoodi toimituksenTiedot) => truthy
                 rakennusvalvontaAsiatieto (:rakennusvalvontaAsiatieto Rakennusvalvonta) => truthy
                 RakennusvalvontaAsia (:RakennusvalvontaAsia rakennusvalvontaAsiatieto) => truthy
                 kasittelynTilatieto (:kasittelynTilatieto RakennusvalvontaAsia)
                 Tilamuutos (:Tilamuutos kasittelynTilatieto) => truthy
                 tila (:tila Tilamuutos) => "p\u00e4\u00e4t\u00f6s toimitettu"

                 luvanTunnisteTiedot (:luvanTunnisteTiedot RakennusvalvontaAsia) => truthy
                 LupaTunnus (:LupaTunnus luvanTunnisteTiedot) => truthy
                 muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
                 mt (:MuuTunnus muuTunnustieto) => truthy

                 tunnus (:tunnus mt) => "LP-753-2013-00001"
                 sovellus (:sovellus mt) => "Lupapiste"

                 osapuolettieto (:osapuolettieto RakennusvalvontaAsia) => truthy
                 Osapuolet (:Osapuolet osapuolettieto) => truthy
                 osapuolitieto (:osapuolitieto Osapuolet) => truthy
                 Osapuoli (:Osapuoli osapuolitieto) => truthy
                 kuntaRooliKoodi (:kuntaRooliKoodi Osapuoli) => "Ilmoituksen tekij\u00e4"
                 henkilo (:henkilo Osapuoli) => truthy
                 nimi (:nimi henkilo) => truthy
                 etunimi (:etunimi nimi) => "Jussi"
                 sukunimi (:sukunimi nimi) => "Viranomainen"
                 osoite (:osoite henkilo) => truthy
                 osoitenimi (-> osoite :osoitenimi :teksti) => "Katuosoite 1 a 1"
                 puhelin (:puhelin henkilo) => "1231234567"

                 katselmustieto (:katselmustieto RakennusvalvontaAsia) => truthy
                 Katselmus (:Katselmus katselmustieto) => truthy
                 rakennustunnus (:rakennustunnus Katselmus) => truthy
                 jarjestysnumero (:jarjestysnumero rakennustunnus)
                 rakennusnumero (:rakennusnro rakennustunnus) => "002"
                 kiinttun (:kiinttun rakennustunnus) => "21111111111111"
                 pitoPvm (:pitoPvm Katselmus) => "2012-12-03"
                 osittainen (:osittainen Katselmus) => "pidetty"
                 pitaja (:pitaja Katselmus) => "Sonja Silja"
                 huomautukset (-> Katselmus :huomautukset :huomautus :kuvaus) => "Saunan ovi pit\u00e4\u00e4 vaihtaa 900mm leve\u00e4ksi.
Piha-alue siivottava v\u00e4litt\u00f6m\u00e4sti."
                 katselmuksenLaji (:katselmuksenLaji Katselmus) => "pohjakatselmus"
                 lasnaolijat (:lasnaolijat Katselmus ) => "Tiivi Taavi, Hipsu ja Lala"
                 poikkeamat (:poikkeamat Katselmus) => "Ei poikkeamisia"
                 tarkastuksenTaiKatselmuksenNimi (:tarkastuksenTaiKatselmuksenNimi Katselmus) => "pohjakatselmus"
                 kayttotapaus (:kayttotapaus RakennusvalvontaAsia) => "Uusi katselmus"]))
