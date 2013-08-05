(ns lupapalvelu.document.rakennuslupa_canonical-test
  (:use [lupapalvelu.document.canonical-test-common]
        [lupapalvelu.document.rakennuslupa_canonical]
        [sade.util :only [contains-value?]]
        [midje.sweet]
        [lupapalvelu.xml.emit]
        [lupapalvelu.xml.krysp.rakennuslupa-mapping]
        [clojure.data.xml]
        [clj-time.core :only [date-time]]))

;;
;; Facts
;;

(facts "Date format"
  (fact (to-xml-date (date-time 2012 1 14)) => "2012-01-14")
  (fact (to-xml-date (date-time 2012 2 29)) => "2012-02-29"))

(def municipality 753)

(def nimi {:etunimi {:value "Pena"} :sukunimi {:value "Penttil\u00e4"}})

(def henkilotiedot (assoc nimi :hetu {:value "210281-9988"}))

(def osoite {:katu {:value "katu"} :postinumero {:value "33800"} :postitoimipaikannimi {:value "Tuonela"}})

(def henkilo
  {:henkilotiedot henkilotiedot
   :yhteystiedot {:puhelin {:value "+358401234567"}
                  :email {:value "pena@example.com"}}
   :osoite osoite
   :turvakieltoKytkin {:value true}})

(def suunnittelija-henkilo
  (assoc henkilo :henkilotiedot nimi))

(def yritys
  {:yritysnimi {:value "Solita Oy"}
   :liikeJaYhteisoTunnus {:value "1060155-5"}
   :osoite osoite
   :yhteyshenkilo {:henkilotiedot nimi
                   :yhteystiedot {:email {:value "solita@solita.fi"},
                                  :puhelin {:value "03-389 1380"}}}})

(def hakija1
  {:id "hakija1" :schema {:info {:name "hakija"}}
   :data {:henkilo henkilo}})

(def hakija2
  {:id "hakija2" :schema {:info {:name "hakija"}}
   :data {:_selected {:value "yritys"}, :yritys yritys}})

(def paasuunnittelija {:id "50bc85e4ea3e790c9ff7cdb2",
   :schema {:info {:name "paasuunnittelija"}}
   :data (merge
           suunnittelija-henkilo
           {:patevyys {:koulutus {:value "Arkkitehti"} :patevyysluokka {:value "ei tiedossa"}}}
           {:yritys   {:yritysnimi {:value "Solita Oy"} :liikeJaYhteisoTunnus {:value "1060155-5"}}})})

(def suunnittelija1
  {:id "suunnittelija1" :schema {:info {:name "suunnittelija"}}
   :data (merge suunnittelija-henkilo
                {:kuntaRoolikoodi {:value "ARK-rakennussuunnittelija"}}
                {:patevyys {:koulutus {:value "Koulutus"} :patevyysluokka {:value "B"}}}
                {:yritys   {:yritysnimi {:value "Solita Oy"} :liikeJaYhteisoTunnus {:value "1060155-5"}}})})

(def suunnittelija2
  {:id "suunnittelija2"  :schema {:info {:name "suunnittelija"}}
   :data (merge suunnittelija-henkilo
                {:kuntaRoolikoodi {:value "GEO-suunnittelija"}}
                {:patevyys {:koulutus {:value "El\u00e4m\u00e4n koulu"} :patevyysluokka {:value "AA"}}}
                {:yritys   {:yritysnimi {:value "Solita Oy"} :liikeJaYhteisoTunnus {:value "1060155-5"}}})})

(def maksaja1
  {:id "maksaja1" :schema {:info {:name "maksaja"}}
   :data {:henkilo henkilo}})

(def maksaja2
  {:id "maksaja2" :schema {:info {:name "maksaja"}}
   :data {:_selected {:value "yritys"}, :yritys yritys}})

(def rakennuspaikka
  {:id "rakennuspaikka" :schema {:info {:name "rakennuspaikka"}}
   :data {:kiinteisto {:tilanNimi {:value "Hiekkametsa"}
                       :maaraalaTunnus {:value ""}}
          :hallintaperuste {:value "oma"}
          :kaavanaste {:value "yleis"}}})

(def common-rakennus {:rakennuksenOmistajat {:0 {:_selected {:value "henkilo"}
                                                 :henkilo henkilo
                                                 :omistajalaji {:value "muu yksityinen henkilö tai perikunta"}}}
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
                                 :lammonlahde {:value "muu"}
                                 :muu-lammonlahde {:value "polttopuillahan tuo"}}
                      :varusteet {:hissiKytkin {:value true},
                                  :kaasuKytkin {:value true},
                                  :koneellinenilmastointiKytkin {:value true},
                                  :sahkoKytkin {:value true},
                                  :saunoja {:value "1"},
                                  :vaestonsuoja {:value "1"},
                                  :vesijohtoKytkin {:value true},
                                  :viemariKytkin {:value true}
                                  :lamminvesiKytkin {:value true}
                                  :aurinkopaneeliKytkin {:value true}}
                      :verkostoliittymat {:kaapeliKytkin {:value true},
                                          :maakaasuKytkin {:value true},
                                          :sahkoKytkin {:value true},
                                          :vesijohtoKytkin {:value true},
                                          :viemariKytkin {:value true}},
                      :luokitus {:paloluokka {:value "P1"}
                                 :energialuokka {:value "C"}
                                 :energiatehokkuusluku {:value "124"}
                                 :energiatehokkuusluvunYksikko {:value "kWh/m2"}}
                      :huoneistot {:0 {:huoneistoTunnus {:porras {:value "A"} :huoneistonumero {:value "1"} :jakokirjain {:value "a"}}
                                       :huoneistonTyyppi {:huoneistoTyyppi {:value "asuinhuoneisto"}
                                                          :huoneistoala {:value "56"}
                                                          :huoneluku {:value "66"}}
                                       :keittionTyyppi {:value "keittio"}
                                       :varusteet {:parvekeTaiTerassiKytkin {:value true}, :WCKytkin {:value true}}}
                                   :1 {:huoneistoTunnus {},
                                       :huoneistonTyyppi {:huoneistoTyyppi {:value "toimitila"}
                                                          :huoneistoala {:value "02"}
                                                          :huoneluku {:value "12"}}
                                       :keittionTyyppi {:value "keittokomero"},
                                       :varusteet {:ammeTaiSuihkuKytkin {:value true}, :saunaKytkin {:value true}, :lamminvesiKytkin {:value true}}}}})

(def uusi-rakennus
  {:id "uusi-rakennus"
   :created 2
   :schema {:info {:name "uusiRakennus"
                   :op {:name "asuinrakennus"}}}
   :data common-rakennus})

(def rakennuksen-muuttaminen
  {:id "muuttaminen"
   :created 1
   :schema {:info {:name "rakennuksen-muuttaminen"
                   :op {:name "muu-laajentaminen"}}}
   :data (conj {:rakennusnro {:value "001"}
                :perusparannuskytkin {:value true}
                :muutostyolaji {:value "muut muutosty\u00f6t"}} common-rakennus)})

(def laajentaminen
  {:id "laajennus"
   :created 3
   :schema {:info {:name "rakennuksen-laajentaminen"
                   :op {:name "laajentaminen"}}}
   :data (conj {:rakennusnro {:value "001"}
                :laajennuksen-tiedot {:perusparannuskytkin {:value true}
                                      :mitat {:tilavuus {:value "1500"}
                                              :kerrosala {:value "180"}
                                              :kokonaisala {:value "150"}
                                              :huoneistoala {:0 {:pintaAla {:value "150"}
                                                                 :kayttotarkoitusKoodi {:value "asuntotilaa(ei vapaa-ajan asunnoista)"}}
                                                             :1 {:pintaAla {:value "10"}
                                                                 :kayttotarkoitusKoodi {:value "varastotilaa"}}}}}} common-rakennus)})


(def purku {:id "purku"
            :created 4
            :schema {:info {:name "purku"
                            :op {:name "purkaminen"}}}
            :data (conj {:rakennusnro {:value "001"}
                         :poistumanAjankohta {:value "17.04.2013"},
                         :poistumanSyy {:value "tuhoutunut"}} common-rakennus)})

(def aidan-rakentaminen {:data {:kokonaisala {:value "0"}
                                :kuvaus { :value "Aidan rakentaminen rajalle"}}
                         :id "aidan-rakentaminen"
                         :created 5
                         :schema {:info {:removable true
                                         :op {:id  "5177ac76da060e8cd8348e07"
                                              :name "aita"}
                                         :name "kaupunkikuvatoimenpide"}}})

(def puun-kaataminen {:created 6
                      :data { :kuvaus {:value "Puun kaataminen"}}
                      :id "puun kaataminen"
                      :schema { :info {:removable true
                                       :op {:id "5177ad63da060e8cd8348e32"
                                            :name "puun-kaataminen"
                                            :created  1366797667137}
                                       :name "maisematyo"}}})

(def hankkeen-kuvaus {:id "Hankeen kuvaus" :schema {:info {:name "hankkeen-kuvaus" :order 1}}
                      :data {:kuvaus {:value "Uuden rakennuksen rakentaminen tontille."}
                             :poikkeamat {:value "Ei poikkeamisia"}}})

(def lisatieto {:id "lisatiedot" :schema {:info {:name "lisatiedot"}}
                :data {:suoramarkkinointikielto {:value true}}})

;TODO LIITETIETO

(def documents
  [hankkeen-kuvaus
   hakija1
   hakija2
   paasuunnittelija
   suunnittelija1
   suunnittelija2
   maksaja1
   maksaja2
   rakennuspaikka
   rakennuksen-muuttaminen
   uusi-rakennus
   laajentaminen
   aidan-rakentaminen
   puun-kaataminen
   purku
   lisatieto])

(fact "Meta test: hakija1"          hakija1          => valid-against-current-schema?)
(fact "Meta test: hakija2"          hakija2          => valid-against-current-schema?)
(fact "Meta test: paasuunnittelija" paasuunnittelija => valid-against-current-schema?)
(fact "Meta test: suunnittelija1"   suunnittelija1   => valid-against-current-schema?)
(fact "Meta test: suunnittelija2"   suunnittelija2   => valid-against-current-schema?)
(fact "Meta test: maksaja1"         maksaja1         => valid-against-current-schema?)
(fact "Meta test: maksaja2"         maksaja2         => valid-against-current-schema?)
(fact "Meta test: rakennuspaikka"   rakennuspaikka   => valid-against-current-schema?)
(fact "Meta test: uusi-rakennus"    uusi-rakennus    => valid-against-current-schema?)
(fact "Meta test: lisatieto"        lisatieto        => valid-against-current-schema?)
(fact "Meta test: hankkeen-kuvaus"  hankkeen-kuvaus  => valid-against-current-schema?)

;; In case a document was added but forgot to write test above
(validate-all-documents documents)

(def application
  {:municipality municipality,
   :auth
   [{:lastName "Panaani",
     :firstName "Pena",
     :username "pena",
     :type "owner",
     :role "owner",
     :id "777777777777777777000020"}],
   :state "open"
   :opened 1354532324658
   :location {:x 408048, :y 6693225},
   :attachments [{:id "518ce59b036496133cf5ba7f"
                  :latestVersion {:fileId "518ce59b036496133cf5ba7c"
                                  :version {:major 0
                                            :minor 1}
                                  :size 27726
                                  :created 1368188315224
                                  :filename "1901_001.pdf"
                                  :contentType "application/pdf"
                                  :user {:role "authority"
                                         :lastName "Sibbo"
                                         :firstName "Sonja"
                                         :username "sonja"
                                         :id "777777777777777777000023"}
                                  :stamped false
                                  :accepted nil}
                  :locked true
                  :modified 1368188315224
                  :op nil
                  :state "requires_authority_action"
                  :target {:type "statement"
                           :id "518ce582036496133cf5ba75"}
                  :type {:type-group "muut"
                         :type-id "muu"}
                  :versions [{:fileId "518ce59b036496133cf5ba7c"
                              :version {"major" 0
                                        :minor 1}
                              :size 27726
                              :created 1368188315224
                              :filename "1901_001.pdf"
                              :contentType "application/pdf"
                              :user {:role "authority"
                                     :lastName "Sibbo"
                                     :firstName "Sonja"
                                     :username "sonja"
                                     :id "777777777777777777000023"}
                              :stamped false
                              :accepted nil}]}],
   :authority {:id "777777777777777777000023",
               :username "sonja",
               :firstName "Sonja",
               :lastName "Sibbo",
               :role "authority"},
   :title "s",
   :created 1354532324658,
   :documents documents,
   :propertyId "21111111111111"
   :modified 1354532324691,
   :address "Katutie 54",
   :id "50bc85e4ea3e790c9ff7cdb0"
   :statements [{:given 1368080324142
                 :id "518b3ee60364ff9a63c6d6a1"
                 :person {:text "Paloviranomainen"
                          :name "Sonja Sibbo"
                          :email "sonja.sibbo@sipoo.fi"
                          :id "516560d6c2e6f603beb85147"}
                 :requested 1368080102631
                 :status "condition"
                 :text "Savupiippu pit\u00e4\u00e4 olla."}]})

(def get-osapuoli-data #'lupapalvelu.document.rakennuslupa_canonical/get-osapuoli-data)

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
  (fact (:puhelin company) => "03-389 1380")
  (fact (:sahkopostiosoite company) => "solita@solita.fi"))

(facts "Canonical hakija/henkilo model is correct"
  (let [hakija-model (get-osapuoli-data (:data hakija1) :hakija)
        henkilo (:henkilo hakija-model)
        ht (:henkilotiedot henkilo)
        yritys (:yritys hakija-model)]
    (fact hakija-model => truthy)
    (fact (:kuntaRooliKoodi hakija-model) => "Rakennusvalvonta-asian hakija")
    (fact (:VRKrooliKoodi hakija-model) => "hakija")
    (fact (:turvakieltoKytkin hakija-model) => true)
    (validate-person henkilo)
    (fact yritys => nil)))

(facts "Canonical hakija/yritys model is correct"
  (let [hakija-model (get-osapuoli-data (:data hakija2) :hakija)
        henkilo (:henkilo hakija-model)
        yritys (:yritys hakija-model)]
    (fact hakija-model => truthy)
    (fact (:kuntaRooliKoodi hakija-model) => "Rakennusvalvonta-asian hakija")
    (fact (:VRKrooliKoodi hakija-model) => "hakija")
    (validate-minimal-person henkilo)
    (validate-company yritys)))

(def get-suunnittelija-data #'lupapalvelu.document.rakennuslupa_canonical/get-suunnittelija-data)
(def get-parties-by-type #'lupapalvelu.document.rakennuslupa_canonical/get-parties-by-type)

(fact "Empty body"
  (empty? (get-parties-by-type
    {"paasuunnittelija" [{:data {}}]} :Suunnittelija ["paasuunnittelija"] get-suunnittelija-data)) => truthy)

(facts "Canonical paasuunnittelija/henkilo+yritys model is correct"
  (let [suunnittelija-model (get-suunnittelija-data (:data paasuunnittelija) :paasuunnittelija)
        henkilo (:henkilo suunnittelija-model)
        yritys (:yritys suunnittelija-model)]
    (fact suunnittelija-model => truthy)
    (fact "kuntaRoolikoodi" (:suunnittelijaRoolikoodi suunnittelija-model) => "p\u00e4\u00e4suunnittelija")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi suunnittelija-model) => "p\u00e4\u00e4suunnittelija")
    (fact "koulutus" (:koulutus suunnittelija-model) => "Arkkitehti")
    (fact "patevyysvaatimusluokka" (:patevyysvaatimusluokka suunnittelija-model) => "ei tiedossa")

    (validate-person-wo-ssn henkilo)
    (validate-minimal-company yritys)))

(facts "Canonical suunnittelija1 model is correct"
  (let [suunnittelija-model (get-suunnittelija-data (:data suunnittelija1) :suunnittelija)]
    (fact suunnittelija-model => truthy)
    (fact "kuntaRoolikoodi" (:suunnittelijaRoolikoodi suunnittelija-model) => "ARK-rakennussuunnittelija")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi suunnittelija-model) => "rakennussuunnittelija")
    (fact "koulutus" (:koulutus suunnittelija-model) => "Koulutus")
    (fact "patevyysvaatimusluokka" (:patevyysvaatimusluokka suunnittelija-model) => "B")
    (fact "henkilo" (:henkilo suunnittelija-model) => truthy)
    (fact "yritys" (:yritys suunnittelija-model) => truthy)))

(facts "Canonical suunnittelija2 model is correct"
  (let [suunnittelija-model (get-suunnittelija-data (:data suunnittelija2) :suunnittelija)]
    (fact suunnittelija-model => truthy)
    (fact "kuntaRoolikoodi" (:suunnittelijaRoolikoodi suunnittelija-model) => "GEO-suunnittelija")
    (fact "VRKrooliKoodi" (:VRKrooliKoodi suunnittelija-model) => "erityissuunnittelija")
    (fact "koulutus" (:koulutus suunnittelija-model) => "El\u00e4m\u00e4n koulu")
    (fact "patevyysvaatimusluokka" (:patevyysvaatimusluokka suunnittelija-model) => "AA")
    (fact "henkilo" (:henkilo suunnittelija-model) => truthy)
    (fact "yritys" (:yritys suunnittelija-model) => truthy)))

(facts "Canonical maksaja/henkilo model is correct"
  (let [maksaja-model (get-osapuoli-data (:data maksaja1) :maksaja)
        henkilo (:henkilo maksaja-model)
        yritys (:yritys maksaja-model)]
    (fact maksaja-model => truthy)
    (fact (:kuntaRooliKoodi maksaja-model) => "Rakennusvalvonta-asian laskun maksaja")
    (fact (:VRKrooliKoodi maksaja-model) => "maksaja")
    (validate-person henkilo)
    (fact yritys => nil)))

(facts "Canonical maksaja/yritys model is correct"
  (let [maksaja-model (get-osapuoli-data (:data maksaja2) :maksaja)
        henkilo (:henkilo maksaja-model)
        yritys (:yritys maksaja-model)]
    (fact maksaja-model => truthy)
    (fact (:kuntaRooliKoodi maksaja-model) => "Rakennusvalvonta-asian laskun maksaja")
    (fact (:VRKrooliKoodi maksaja-model) => "maksaja")
    (validate-minimal-person henkilo)
    (validate-company yritys)))

(def get-handler #'lupapalvelu.document.rakennuslupa_canonical/get-handler)

(facts "Handler is sonja"
  (let [handler (get-handler application)
        name (get-in handler [:henkilo :nimi])]
    (fact handler => truthy)
    (fact (:etunimi name) => "Sonja")
    (fact (:sukunimi name) => "Sibbo")))

(def get-actions #'lupapalvelu.document.rakennuslupa_canonical/get-operations)

(facts "Toimenpiteet"
  (let [documents (by-type (:documents application))
        actions (get-actions documents application)]
    ;(clojure.pprint/pprint actions)
    (fact "actions" (seq actions) => truthy)
    ))

(def get-huoneisto-data #'lupapalvelu.document.rakennuslupa_canonical/get-huoneisto-data)

(facts "Huoneisto is correct"
  (let [huoneistot (get-huoneisto-data (get-in uusi-rakennus [:data :huoneistot]))
        h1 (first huoneistot), h2 (last huoneistot)]
    (fact (count huoneistot) => 2)
    (fact (:huoneluku h1) => "66")
    (fact (:keittionTyyppi h1) => "keittio")
    (fact (:huoneistoala h1) => "56")
    (fact (:huoneistonTyyppi h1) => "asuinhuoneisto")
    (fact (-> h1 :varusteet :WCKytkin) => true)
    (fact (-> h1 :varusteet :ammeTaiSuihkuKytkin) => false)
    (fact (-> h1 :varusteet :saunaKytkin) => false)
    (fact (-> h1 :varusteet :parvekeTaiTerassiKytkin) => true)
    (fact (-> h1 :varusteet :lamminvesiKytkin) => false)
    (fact (:huoneistotunnus h1) => truthy)
    (fact (-> h1 :huoneistotunnus :porras) => "A")
    (fact (-> h1 :huoneistotunnus :huoneistonumero) => "001")
    (fact (-> h1 :huoneistotunnus :jakokirjain) => "a")

    (fact (:huoneluku h2) => "12")
    (fact (:keittionTyyppi h2) => "keittokomero")
    (fact (:huoneistoala h2) => "02")
    (fact (:huoneistonTyyppi h2) => "toimitila")
    (fact (-> h2 :varusteet :WCKytkin) => false)
    (fact (-> h2 :varusteet :ammeTaiSuihkuKytkin) => true)
    (fact (-> h2 :varusteet :saunaKytkin) => true)
    (fact (-> h2 :varusteet :parvekeTaiTerassiKytkin) => false)
    (fact (-> h2 :varusteet :lamminvesiKytkin) => true)
    (fact (:huoneistotunnus h2) => falsey)))

(def get-rakennus #'lupapalvelu.document.rakennuslupa_canonical/get-rakennus)

(facts "When muu-lammonlahde is empty, lammonlahde is used"
  (let [rakennus (get-rakennus {:lammitys {:lammitystapa {:value nil}
                                           :lammonlahde  {:value "turve"}
                                           :muu-lammonlahde {:value nil}} }
                               {:id "123" :created nil} application)]
    (fact (:polttoaine (:lammonlahde (:rakennuksenTiedot rakennus))) => "turve")))

(facts "When muu-lammonlahde is specified, it is used"
  (let [rakennus (get-rakennus {:lammitys {:lammitystapa {:value nil}
                                           :lammonlahde  {:value "muu"}
                                           :muu-lammonlahde {:value "fuusioenergialla"}} }
                               {:id "123" :created nil} application)]
    (fact (:muu (:lammonlahde (:rakennuksenTiedot rakennus))) => "fuusioenergialla")))

(facts "Canonical model is correct"
  (let [canonical (application-to-canonical application "se")
        rakennusvalvonta (:Rakennusvalvonta canonical)
        rakennusvalvontaasiatieto (:rakennusvalvontaAsiatieto rakennusvalvonta)
        rakennusvalvontaasia (:RakennusvalvontaAsia rakennusvalvontaasiatieto)
        osapuolettieto (:osapuolettieto rakennusvalvontaasia)
        osapuolet (:Osapuolet osapuolettieto)
        osapuolitieto-hakija (first (:osapuolitieto osapuolet))
        paasuunnitelija (:Suunnittelija (last (:suunnittelijatieto osapuolet)))
        hakija-osapuoli1 (:Osapuoli osapuolitieto-hakija)
        suunnittelijat (:suunnittelijatieto osapuolet)
        rakennuspaikkatiedot (:rakennuspaikkatieto rakennusvalvontaasia)
        rakennuspaikkatieto (first rakennuspaikkatiedot)
        rakennuspaikka (:Rakennuspaikka rakennuspaikkatieto)
        rakennuspaikanKiinteistotieto (:rakennuspaikanKiinteistotieto rakennuspaikka)
        RakennuspaikanKiinteistotieto (:RakennuspaikanKiinteisto rakennuspaikanKiinteistotieto)
        kiinteistotieto (:kiinteistotieto RakennuspaikanKiinteistotieto)
        Kiinteisto (:Kiinteisto kiinteistotieto)
        toimenpiteet(:toimenpidetieto rakennusvalvontaasia)
        toimenpide (:Toimenpide (nth toimenpiteet 1))
        muu-muutostyo (:Toimenpide (nth toimenpiteet 0))
        laajennus-t (:Toimenpide (nth toimenpiteet 2))
        purku-t (:Toimenpide (nth toimenpiteet 3))
        kaupunkikuva-t (:Toimenpide (nth toimenpiteet 4))
        rakennustieto (:rakennustieto toimenpide)
        rakennus (:Rakennus rakennustieto)
        rakennuksen-omistajatieto (:Omistaja(first (:omistajatieto rakennus)))
        rakennuksentiedot (:rakennuksenTiedot rakennus)
        lisatiedot (:lisatiedot rakennusvalvontaasia)
        Lisatiedot (:Lisatiedot lisatiedot)
        kayttotapaus (:kayttotapaus rakennusvalvontaasia)
        asianTiedot (:asianTiedot rakennusvalvontaasia)
        Asiantiedot (:Asiantiedot asianTiedot)
        vahainen-poikkeaminen (:vahainenPoikkeaminen Asiantiedot)
        rakennusvalvontasian-kuvaus (:rakennusvalvontaasianKuvaus Asiantiedot)
        luvanTunnisteTiedot (:luvanTunnisteTiedot rakennusvalvontaasia)
        LupaTunnus (:LupaTunnus luvanTunnisteTiedot)
        muuTunnustieto (:muuTunnustieto LupaTunnus)
        MuuTunnus (:MuuTunnus muuTunnustieto)

        lausuntotieto (first (:lausuntotieto rakennusvalvontaasia))
        Lausunto (:Lausunto lausuntotieto)
        pyydetty (:pyydetty Lausunto)
        viranomainen (:viranomainen pyydetty)
        pyyntoPvm (:pyyntoPvm pyydetty)
        lausunto (:lausunto Lausunto)
        lausuntoViranomainen (:viranomainen lausunto)
        lausuntoPvm (:lausuntoPvm lausunto)
        lausuntoType (:lausunto lausunto)
        lausuntoTeksti (:lausunto lausuntoType)
        puoltotieto (:puoltotieto lausunto)
        puolto (:puolto puoltotieto)]
    ;(clojure.pprint/pprint canonical)
    (fact "canonical" canonical => truthy)
    (fact "contains nil" (contains-value? canonical nil?) => falsey)
    (fact "rakennusvalvonta" rakennusvalvonta => truthy)
    (fact "rakennusvalvontaasiatieto" rakennusvalvontaasiatieto  => truthy)
    (fact "rakennusvalvontaasia" rakennusvalvontaasia  => truthy)
    (fact "osapuolettieto" osapuolettieto => truthy)
    (fact "osapuolet" osapuolet => truthy)
    (fact "osapuolitieto" osapuolitieto-hakija => truthy)
    (fact "paasuunnitelija" paasuunnitelija => (contains {:suunnittelijaRoolikoodi "p\u00e4\u00e4suunnittelija"}))
    (fact "hakija-osapuoli1" hakija-osapuoli1 => truthy)
    (fact "Suunnitelijat" suunnittelijat => truthy)
    (fact "Osapuolien maara" (+ (count suunnittelijat) (count (:osapuolitieto osapuolet))) => 7)
    (fact "rakennuspaikkojen maara" (count rakennuspaikkatiedot) => 1)
    (fact "rakennuspaikka" rakennuspaikkatieto => truthy)
    (fact "rakennuspaikanKiinteistotieto" rakennuspaikanKiinteistotieto => truthy)
    (fact "RakennuspaikanKiinteistotieto" RakennuspaikanKiinteistotieto => truthy)
    (fact "kiinteistotieto" kiinteistotieto => truthy)
    (fact "Kiinteisto" Kiinteisto => truthy)
    (fact "tilanNimi" (:tilannimi Kiinteisto) => "Hiekkametsa")
    (fact "kiinteistotunnus" (:kiinteistotunnus Kiinteisto) => "21111111111111")
    (fact "maaraalaTunnus" (:maaraAlaTunnus Kiinteisto) => "")
    (fact "kokotilakytkin" (:kokotilaKytkin RakennuspaikanKiinteistotieto) => truthy)
    (fact "hallintaperuste" (:hallintaperuste RakennuspaikanKiinteistotieto) => "oma")

    (fact "Toimenpidetieto"  (count toimenpiteet) => 5)
    (fact "Rakennus" rakennus => truthy)
    (fact "rakentajaTyyppi" (:rakentajatyyppi rakennus) => "muu")
    (fact "rakennuksentiedot" rakennuksentiedot => truthy)
    (fact "kayttotarkoitus" (:kayttotarkoitus rakennuksentiedot) => "012 kahden asunnon talot")
    (fact "rakentamistapa" (:rakentamistapa rakennuksentiedot) => "elementti")
    (fact "rakennuksen omistajalaji" (:omistajalaji (:omistajalaji rakennuksen-omistajatieto)) => "muu yksityinen henkil\u00f6 tai perikunta")
    (fact "Lisatiedot suoramarkkinointikielto" (:suoramarkkinointikieltoKytkin Lisatiedot) => true)
    (fact "Lisatiedot asiointikieli" (:asioimiskieli Lisatiedot) => "ruotsi")
    (fact "asianTiedot" asianTiedot => truthy)
    (fact "Asiantiedot" Asiantiedot => truthy)
    (fact "rakennusvalvontasian-kuvaus" rakennusvalvontasian-kuvaus =>"Uuden rakennuksen rakentaminen tontille.\n\nPuun kaataminen:Puun kaataminen")
    (fact kayttotapaus => "Uusi hakemus")

    (fact "Luvan tunnistetiedot" luvanTunnisteTiedot => truthy)
    (fact "LupaTunnus" LupaTunnus => truthy)
    (fact "Muut tunnustieto" muuTunnustieto => truthy)
    (fact "MuuTunnus" MuuTunnus => truthy)
    (fact "Muu tunnus" (:tunnus MuuTunnus) => "50bc85e4ea3e790c9ff7cdb0")
    (fact "Sovellus" (:sovellus MuuTunnus) => "Lupapiste")
    (fact "Toimenpiteen kuvaus" (-> toimenpide :uusi :kuvaus) => "Asuinrakennuksen rakentaminen")
    (fact "Toimenpiteen kuvaus" (-> muu-muutostyo :muuMuutosTyo :kuvaus) => "Muu rakennuksen muutosty\u00f6")
    (fact "Muu muutostyon perusparannuskytkin" (-> muu-muutostyo :muuMuutosTyo :perusparannusKytkin) => true)
    (fact "Muutostyon laji" (-> muu-muutostyo :muuMuutosTyo :muutostyonLaji) => "muut muutosty\u00f6t")
    (fact "Laajennuksen kuvaus" (-> laajennus-t :laajennus :kuvaus) => "Rakennuksen laajentaminen tai korjaaminen")
    (fact "muu muutostyon rakennuksen tunnus" (-> muu-muutostyo :rakennustieto :Rakennus :rakennuksenTiedot :rakennustunnus :jarjestysnumero) => "001")
    (fact "Laajennuksen rakennuksen tunnus" (-> laajennus-t :rakennustieto :Rakennus :rakennuksenTiedot :rakennustunnus :jarjestysnumero) => "001")
    (fact "Laajennuksen rakennuksen kiintun" (-> laajennus-t :rakennustieto :Rakennus :rakennuksenTiedot :rakennustunnus :kiinttun) => "21111111111111")
    (fact "Laajennuksen pintaalat" (count (-> laajennus-t :laajennus :laajennuksentiedot :huoneistoala )) => 2)
    (fact "Purkamisen kuvaus" (-> purku-t :purkaminen :kuvaus) => "Rakennuksen purkaminen")
    (fact "Poistuma pvm" (-> purku-t :purkaminen :poistumaPvm) => "2013-04-17")
    (fact "Kaupunkikuvatoimenpiteen kuvaus" (-> kaupunkikuva-t :kaupunkikuvaToimenpide :kuvaus) => "Aidan rakentaminen")
    (fact "Kaupunkikuvatoimenpiteen rakennelman kuvaus" (-> kaupunkikuva-t :rakennelmatieto :Rakennelma :kuvaus :kuvaus) => "Aidan rakentaminen rajalle")

    (fact "Lausunto" lausunto => truthy)
    (fact "viranomainen" viranomainen => "Paloviranomainen")
    (fact "Pyyntopvm" pyyntoPvm => "2013-05-09")
    (fact "Lausunto viranomainen" lausuntoViranomainen => "Sonja Sibbo")
    (fact "Lausunto pvm" lausuntoPvm => "2013-05-09")
    (fact "lausunto teksti osa" lausuntoTeksti => "Savupiippu pit\u00e4\u00e4 olla.")
    (fact "Puolto" puolto => "ehdoilla")

 ;   (clojure.pprint/pprint canonical)
    ))
