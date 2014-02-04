(ns lupapalvelu.document.poikkeamis-canonical-test
  (:require [lupapalvelu.factlet :as fl]
            [lupapalvelu.document.canonical-test-common :refer :all]
            [lupapalvelu.document.poikkeamis-canonical :refer :all]
            [lupapalvelu.document.poikkeamis-schemas :refer :all]
            [lupapalvelu.document.tools :as tools]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))


(def ^:private statements [{:given 1379423133068
                            :id "52385377da063788effc1e93"
                            :person {:text "Paloviranomainen"
                                     :name "Sonja Sibbo"
                                     :email "sonja.sibbo@sipoo.fi"
                                     :id "516560d6c2e6f603beb85147"}
                            :requested 1379423095616
                            :status "yes"
                            :text "Lausunto liitteen\u00e4."}])

(def ^:private hakija {:id "523844e1da063788effc1c58"
                       :schema-info {:approvable true
                                     :subtype "hakija"
                                     :name "hakija"
                                     :removable true
                                     :repeating true
                                     :version 1
                                     :type "party"
                                     :order 3}
                       :created 1379419361123
                       :data {:yritys {:yhteyshenkilo {:henkilotiedot {:etunimi {:value "Pena"}
                                                                       :sukunimi {:value "Panaani"}
                                                                       :turvakieltoKytkin {:value true}}
                                                       :yhteystiedot {:email {:value "pena@example.com"}
                                                                      :puhelin {:value "0102030405"}}}
                                       :osoite {:katu {:value "Paapankuja 12"}
                                                :postinumero {:value "10203"}
                                                :postitoimipaikannimi {:value "Piippola"}}}
                              :henkilo {:userId {:value "777777777777777777000020"}
                                        :henkilotiedot {:hetu {:value "210281-9988"}
                                                        :etunimi {:value "Pena"}
                                                        :sukunimi {:value "Panaani"}
                                                        :turvakieltoKytkin {:value true}}
                                        :yhteystiedot {:email {:value "pena@example.com"}
                                                       :puhelin {:value "0102030405"}}
                                        :osoite {:katu {:value "Paapankuja 12"}
                                                 :postinumero {:value "10203"}
                                                 :postitoimipaikannimi {:value "Piippola"}}}
                              :_selected {:value "henkilo"}}})

(def ^:private uusi {:created 1379419361123
                     :data {:toimenpiteet  {:Toimenpide {:value "uusi"}
                                               :huoneistoja {:value "1"}
                                               :kayttotarkoitus {:value "011 yhden asunnon talot"}
                                               :kerroksia {:value "2"}
                                               :kerrosala {:value "200"}
                                               :kokonaisala {:value "220"}}
                                        }
                     :id "523844e1da063788effc1c57"
                     :schema-info {:order 50
                                   :version 1
                                   :name "rakennushanke"
                                   :op {:id "523844e1da063788effc1c56"
                                        :name "poikkeamis"
                                        :created 1379419361123}
                                   :removable true}})

(def ^:private uusi2 {:created 1379419361123
                      :data {:toimenpiteet  {:Toimenpide {:value "uusi"}
                                             :kayttotarkoitus {:value "941 talousrakennukset"}
                                             :kerroksia {:value "1"}
                                             :kerrosala {:value "25"}
                                             :kokonaisala {:value "30"}}

                             }
                     :id "523844e1da063788effc1c57"
                     :schema-info {:order 50
                                   :version 1
                                   :name "rakennushanke"
                                   :op {:id "523844e1da063788effc1c56"
                                        :name "poikkeamis"
                                        :created 1379419361123}
                                   :removable true}})



(def ^:private laajennus {:created 1379419361123
                          :data {:kaytettykerrosala {:kayttotarkoitusKoodi {:value "013 muut erilliset talot"}
                                                     :pintaAla {:value "99"}}
                                 :toimenpiteet {:Toimenpide {:value "laajennus"}
                                                :kayttotarkoitus {:value "941 talousrakennukset"}
                                                :kerroksia {:value "1"}
                                                :kerrosala {:value "25"}
                                                :kokonaisala {:value "30"}}}
                          :id "523844e1da063788effc1c57"
                          :schema-info {:order 50
                                        :version 1
                                        :name "rakennushanke"
                                        :op {:id "523844e1da063788effc1c56"
                                             :name "poikkeamis"
                                             :created 1379419361123}
                                        :removable true}})


(def ^:private hanke {:created 1379419361123
                      :data {:kuvaus {:value "Omakotitalon ja tallin rakentaminen."}
                             :poikkeamat {:value "Alueelle ei voimassa olevaa kaava."}}
                      :id "523844e1da063788effc1c59"
                      :schema-info {:approvable true
                                    :name "hankkeen-kuvaus"
                                    :version 1
                                    :order 1}})

(def ^:private maksaja {:created 1379419361123
                        :data {:_selected {:value "yritys"}
                               :laskuviite {:value "LVI99997"}
                               :yritys {:liikeJaYhteisoTunnus {:value "1743842-0"}
                                        :osoite {:katu {:value "Koivukuja 2"}
                                                 :postinumero {:value "23500"}
                                                 :postitoimipaikannimi {:value "Helsinki"}}
                                        :yhteyshenkilo {:henkilotiedot {:etunimi {:value "Toimi"}
                                                                        :sukunimi {:value "Toimari"}}
                                                        :yhteystiedot {:email {:value "paajehu@yit.foo"}
                                                                       :puhelin {:value "020202"}}}
                                        :yritysnimi {:value "YIT"}}}
                        :id "523844e1da063788effc1c5a"
                        :schema-info {:approvable true
                                      :name "maksaja"
                                      :removable true
                                      :repeating true
                                      :version 1
                                      :type "party"
                                      :order 6}})

(def ^:private rakennuspaikka {:created 1379419361123
                               :data {:hallintaperuste {:value "oma"}
                                      :kaavanaste {:value "ei kaavaa"}
                            :kiinteisto {:maaraalaTunnus {:value "0008"}
                                         :tilanNimi {:value "Omatila"}
                                         :rantaKytkin {:value true}}}
                               :id "523844e1da063788effc1c5b"
                               :schema-info {:approvable true
                                           :name "poikkeusasian-rakennuspaikka"
                                           :version 1
                                           :order 2}})

(def ^:private paasuunnittelija {:created 1379419361123
                                 :data {:henkilotiedot {:etunimi {:value "Pena"}
                                 :sukunimi {:value "Panaani"}
                                 :hetu {:value "210281-9988"}}
                 :osoite {:katu {:value "Paapankuja 12"}
                          :postinumero {:value "10203"}
                          :postitoimipaikannimi {:value "Piippola"}}
                 :patevyys {:koulutus {:value "Arkkitehti"}
                            :valmistumisvuosi {:value "2010"}
                            :patevyysluokka {:value "AA"}
                            :kokemus {:value "3"}
                            :fise {:value "http://www.solita.fi"}}
                 :userId {:value "777777777777777777000020"}
                 :yhteystiedot {:email {:value "pena@example.com"}
                                :puhelin {:value "0102030405"}}}
          :id "523844e1da063788effc1c5d"
          :schema-info {:approvable true
                               :name "paasuunnittelija"
                               :removable false
                               :version 1
                               :type "party"
                               :order 4}})

(def ^:private suunnittelija {:id "523844e1da063788effc1c5e"
                                           :schema-info {:approvable true
                                                         :name "suunnittelija"
                                                         :removable true
                                                         :repeating true
                                                         :version 1
                                                         :type "party"
                                                         :order 5}
                                           :created 1379419361123
                                           :data {:henkilotiedot
                                                  {:etunimi {:value "Pena"}
                                                   :sukunimi {:value "Panaani"}
                                                   :hetu {:value "210281-9988"}}
                                                  :kuntaRoolikoodi {:value "KVV-suunnittelija"}
                                                  :osoite {:katu {:value "Paapankuja 12"}
                                                           :postinumero {:value "10203"}
                                                           :postitoimipaikannimi {:value "Piippola"}}
                                                  :patevyys {:koulutus {:value "El\u00e4m\u00e4n koulu"}
                                                             :valmistumisvuosi {:value "2010"}
                                                             :patevyysluokka {:value "C"}
                                                             :kokemus {:value "3"}
                                                             :fise {:value "http://www.solita.fi"}}
                                                  :userId {:value "777777777777777777000020"}
                                                  :yhteystiedot {:email {:value "pena@example.com"}
                                                                 :puhelin {:value "0102030405"}}
                                                  :yritys {:liikeJaYhteisoTunnus {:value "1743842-0"}
                                                           :yritysnimi {:value "ewq"}}}})

(def ^:private lisaosa {:created 1379419361123
                        :data {:kaavoituksen_ja_alueiden_tilanne {:rajoittuuko_tiehen {:value true}
                                                                  :tienkayttooikeus {:value true}}
                               :luonto_ja_kulttuuri {:kulttuurisesti_merkittava {:value true}}
                               :maisema {:metsan_reunassa {:value true}
                                         :metsassa {:value false}}
                               :merkittavyys {:rakentamisen_vaikutusten_merkittavyys {:value "Vain pient\u00e4 maisemallista haittaa."}}
                               :muut_vaikutukset {:etaisyys_viemariverkosta {:value "2000"}
                                                  :pohjavesialuetta {:value true}}
                               :vaikutukset_yhdyskuntakehykselle {:etaisyys_kauppaan {:value "12"}
                                                                  :etaisyys_kuntakeskuksen_palveluihin {:value "12"}
                                                                  :etaisyys_paivakotiin {:value "11"}
                                                                  :etaisyyys_alakouluun {:value "10"}
                                                                  :etaisyyys_ylakouluun {:value "20"}
                                                                  :muita_vaikutuksia {:value "Maisemallisesti talo tulee sijoittumaan m\u00e4en harjalle."}}
                               :virkistys_tarpeet {:ulkoilu_ja_virkistysaluetta_varattu {:value true}}}
              :id "523844e1da063788effc1c5f"
              :schema-info {:name "suunnittelutarveratkaisun-lisaosa"
                            :version 1
                            :order 52}})

(def ^:private documents [hakija
                uusi
                uusi2
                hanke
                maksaja
                rakennuspaikka
                paasuunnittelija
                suunnittelija
                lisaosa])

(def ^:private documents-for-laajennus [hakija
                laajennus
                hanke
                maksaja
                rakennuspaikka
                paasuunnittelija
                suunnittelija
                lisaosa])

(fact "Meta test: hakija"          hakija           => valid-against-current-schema?)
(fact "Meta test: uusi"            uusi             => valid-against-current-schema?)
(fact "Meta test: maksaja"         maksaja          => valid-against-current-schema?)
(fact "Meta test: rakennusapikka"  rakennuspaikka   => valid-against-current-schema?)
(fact "Meta test: paasunnitelija"  paasuunnittelija => valid-against-current-schema?)
(fact "Meta test: suunnittelija"   suunnittelija    => valid-against-current-schema?)
(fact "Meta test: lisaosa"         lisaosa          => valid-against-current-schema?)
(fact "Meta test: laajennus"       laajennus        => valid-against-current-schema?)


(def poikkari-hakemus {:schema-version 1
                       :auth [{:lastName "Panaani"
                               :firstName "Pena"
                               :username "pena"
                               :type "owner"
                               :role "owner"
                               :id "777777777777777777000020"} {:id "777777777777777777000023"
                                                                :username "sonja"
                                                                :firstName "Sonja"
                                                                :lastName "Sibbo"
                                                                :role "writer"}]
                       :submitted 1379422973832
                       :state "submitted"
                       :location {:x 404174.92749023
                                  :y 6690687.4923706}
                       :attachments []
                       :statements statements
                       :organization "753-P"
                       :title "S\u00f6derkullantie 146"
                       :operations [{:id "523844e1da063788effc1c56"
                                     :name "poikkeamis"
                                     :created 1379419361123}]
                       :infoRequest false
                       :opened 1379422973832
                       :created 1379419361123
                       :propertyId "75342700020063"
                       :documents documents
                       :_statements-seen-by {:777777777777777777000023 1379423134104}
                       :_software_version "1.0.5"
                       :modified 1379423133065
                       :comments [{:text ""
                                   :target {:type "attachment"
                                            :id "52385207da063788effc1e24"
                                            :version {:major 1
                                                      :minor 0}
                                            :filename "3171_001taksi.pdf"
                                            :fileId "52385207da063788effc1e21"}
                                   :created 1379422727372
                                   :to nil
                                   :user {:role "applicant"
                                          :lastName "Panaani"
                                          :firstName "Pena"
                                          :username "pena"
                                          :id "777777777777777777000020"}} {:text ""
                                                                            :target {:type "attachment"
                                                                                     :id "5238538dda063788effc1eb2"
                                                                                     :version {:major 0
                                                                                               :minor 1}
                                                                                     :filename "3112_001.pdf"
                                                                                     :fileId "5238538dda063788effc1eaf"}
                                                                            :created 1379423117449
                                                                            :to nil
                                                                            :user {:role "authority"
                                                                                   :lastName "Sibbo"
                                                                                   :firstName "Sonja"
                                                                                   :username "sonja"
                                                                                   :id "777777777777777777000023"}} {:text "Hakemukselle lis\u00e4tty lausunto."
                                                                                                                     :target {:type "statement"
                                                                                                                              :id "52385377da063788effc1e93"}
                                                                                                                     :created 1379423133065
                                                                                                                     :to nil
                                                                                                                     :user {:role "authority"
                                                                                                                            :lastName "Sibbo"
                                                                                                                            :firstName "Sonja"
                                                                                                                            :username "sonja"
                                                                                                                            :id "777777777777777777000023"}}]
                       :address "S\u00f6derkullantie 146"
                       :permitType "P"
                       :permitSubtype "poikkeamislupa"
                       :id "LP-753-2013-00001"
                       :municipality "753"})

(def suunnitelutarveratkaisu (assoc poikkari-hakemus :permitSubtype "suunnittelutarveratkaisu"))


(validate-all-documents documents)


(testable-privates lupapalvelu.document.poikkeamis-canonical get-toimenpiteet)

(fl/fact*
  (let [application (tools/unwrapped poikkari-hakemus)
        canonical (poikkeus-application-to-canonical application "fi" ) => truthy
        Popast (:Popast canonical) => truthy
        toimituksenTiedot (:toimituksenTiedot Popast) => truthy
        aineistonnimi (:aineistonnimi toimituksenTiedot) => (:title poikkari-hakemus)
        aineistotoimittaja (:aineistotoimittaja toimituksenTiedot) => "lupapiste@solita.fi"
        tila (:tila toimituksenTiedot) => "keskener\u00e4inen"
        kuntakoodi (:kuntakoodi toimituksenTiedot) => (:municipality poikkari-hakemus)

        suunnittelutarveasiatieto (:suunnittelutarveasiatieto Popast) => nil
        poikkeamisasiatieto (:poikkeamisasiatieto Popast) => truthy
        Poikkeamisasia (:Poikkeamisasia poikkeamisasiatieto)
        ;abstarctPoikkeamistype
        kasittelynTilatieto (:kasittelynTilatieto Poikkeamisasia) => truthy
        Tilamuutos (:Tilamuutos kasittelynTilatieto) => truthy
        pvm (:pvm Tilamuutos) => "2013-09-17"

        kuntakoodi (:kuntakoodi Poikkeamisasia) => (:municipality poikkari-hakemus)

        luvanTunnistetiedot (:luvanTunnistetiedot Poikkeamisasia) => truthy
        LupaTunnus (:LupaTunnus luvanTunnistetiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        MuuTunnus (:MuuTunnus muuTunnustieto) => truthy
        tunnus (:tunnus MuuTunnus) => "LP-753-2013-00001"
        sovellus (:sovellus MuuTunnus) => "Lupapiste"

        osapuolettieto (:osapuolettieto Poikkeamisasia) => truthy
        Osapuolet (:Osapuolet osapuolettieto) => truthy
        osapuolitieto (:osapuolitieto Osapuolet) => truthy

        ;Maksaja
        maksaja (some #(when (= (get-in % [:Osapuoli :VRKrooliKoodi] %) "maksaja") %) osapuolitieto) => truthy
        Osapuoli (:Osapuoli maksaja) => truthy
        _ (:turvakieltoKytkin Osapuoli) => false
        henkilo (:henkilo Osapuoli) => truthy
        _ (get-in henkilo [:nimi :etunimi]) => "Toimi"
        _ (get-in henkilo [:nimi :sukunimi]) => "Toimari"
        _ (:puhelin henkilo) => "020202"
        _ (:sahkopostiosoite henkilo) => "paajehu@yit.foo"
        yritys (:yritys Osapuoli) => truthy
        _ (:nimi yritys ) => "YIT"
        _ (:liikeJaYhteisotunnus yritys) => "1743842-0"
        postiosoite (:postiosoite yritys) => truthy
        _ (get-in postiosoite [:osoitenimi :teksti]) => "Koivukuja 2"
        _ (:postinumero postiosoite) => "23500"
        _ (:postitoimipaikannimi postiosoite) => "Helsinki"

        ;Hakija
        hakija (some #(when (= (get-in % [:Osapuoli :VRKrooliKoodi] %) "hakija") %) osapuolitieto) => truthy
        Osapuoli (:Osapuoli hakija) => truthy
        _ (:turvakieltoKytkin Osapuoli) => true
        henkilo (:henkilo Osapuoli) => truthy
        _ (get-in henkilo [:nimi :etunimi]) => "Pena"
        _ (get-in henkilo [:nimi :sukunimi]) => "Panaani"
        _ (:henkilotunnus henkilo) => "210281-9988"
        osoite (:osoite henkilo) => truthy
        _ (get-in osoite [:osoitenimi :teksti]) => "Paapankuja 12"
        _ (:postinumero osoite) => "10203"
        _ (:postitoimipaikannimi osoite) => "Piippola"
        _ (:puhelin henkilo) => "0102030405"
        _ (:sahkopostiosoite henkilo) => "pena@example.com"
        yritys (:yritys Osapuoli) => nil

        ;Paasuunnittelija
        suunnittelijatieto (:suunnittelijatieto Osapuolet) => truthy
        paasuunnittelija (some #(when (= (get-in % [:Suunnittelija :VRKrooliKoodi] %) "p\u00e4\u00e4suunnittelija") %) suunnittelijatieto) => truthy
        Suunnittelija (:Suunnittelija paasuunnittelija) => truthy
        henkilo (:henkilo Suunnittelija) => truthy
        _ (get-in henkilo [:nimi :etunimi]) => "Pena"
        _ (get-in henkilo [:nimi :sukunimi]) => "Panaani"
        _ (:henkilotunnus henkilo) => "210281-9988"
        _ (:puhelin henkilo) => "0102030405"
        _ (:sahkopostiosoite henkilo) => "pena@example.com"
        osoite (:osoite henkilo) => truthy
        _ (get-in osoite [:osoitenimi :teksti]) => "Paapankuja 12"
        _ (:postinumero osoite) => "10203"
        _ (:postitoimipaikannimi osoite) => "Piippola"
        _ (:koulutus Suunnittelija) => "Arkkitehti"
        _ (:patevyysvaatimusluokka Suunnittelija) => "AA"
        _ (:valmistumisvuosi Suunnittelija) => "2010"
        _ (:kokemusvuodet Suunnittelija) => "3"

        ;Suunnittelija
        suunnittelijatieto (:suunnittelijatieto Osapuolet) => truthy
        suunnittelija (some #(when (= (get-in % [:Suunnittelija :suunnittelijaRoolikoodi] %) "KVV-suunnittelija") %) suunnittelijatieto) => truthy
        Suunnittelija (:Suunnittelija suunnittelija) => truthy
        henkilo (:henkilo Suunnittelija) => truthy
        _ (get-in henkilo [:nimi :etunimi]) => "Pena"
        _ (get-in henkilo [:nimi :sukunimi]) => "Panaani"
        _ (:henkilotunnus henkilo) => "210281-9988"
        _ (:puhelin henkilo) => "0102030405"
        _ (:sahkopostiosoite henkilo) => "pena@example.com"
        osoite (:osoite henkilo) => truthy
        _ (get-in osoite [:osoitenimi :teksti]) => "Paapankuja 12"
        _ (:postinumero osoite) => "10203"
        _ (:postitoimipaikannimi osoite) => "Piippola"
        _ (:koulutus Suunnittelija) => "El\u00e4m\u00e4n koulu"
        _ (:patevyysvaatimusluokka Suunnittelija) => "C"
        _ (:valmistumisvuosi Suunnittelija) => "2010"
        _ (:kokemusvuodet Suunnittelija) => "3"

        rakennuspaikkatieto (:rakennuspaikkatieto Poikkeamisasia) => truthy
        Rakennuspaikkaf (first rakennuspaikkatieto) => truthy
        Rakennuspaikka (:Rakennuspaikka Rakennuspaikkaf) => truthy
        rakennuspaikanKiinteistotieto (:rakennuspaikanKiinteistotieto Rakennuspaikka) => truthy
        RakennuspaikanKiinteisto (:RakennuspaikanKiinteisto rakennuspaikanKiinteistotieto) => truthy
        kiinteistotieto (:kiinteistotieto RakennuspaikanKiinteisto) => truthy
        Kiinteisto (:Kiinteisto kiinteistotieto) => truthy
        tilannimi (:tilannimi Kiinteisto) => "Omatila"
        kiinteistotunnus (:kiinteistotunnus Kiinteisto) => "75342700020063"
        maaraalaTunnus (:maaraAlaTunnus Kiinteisto) => "M0008"
        rantaKytkin (:rantaKytkin Kiinteisto) => true
        kokotilaKytkin (:kokotilaKytkin RakennuspaikanKiinteisto) => false
        hallintaperuste (:hallintaperuste RakennuspaikanKiinteisto) => "oma"

        toimenpidetieto (:toimenpidetieto Poikkeamisasia) => truthy
        toimenpide-count (count toimenpidetieto) => 2
        uusi (some #(when (= (get-in % [:Toimenpide :tavoitetilatieto :Tavoitetila :paakayttotarkoitusKoodi]) "011 yhden asunnon talot") %) toimenpidetieto)
        uusi (:Toimenpide uusi)
        rakennustunnus (:rakennustunnus uusi) => nil
        _ (:liitetieto uusi) => nil
        kuvauskoodi (:kuvausKoodi uusi) => "uusi"
        kerrosalatieto (:kerrosalatieto uusi) => nil
        tavoitetilatieto (:tavoitetilatieto uusi) => truthy
        Tavoitetila (:Tavoitetila tavoitetilatieto) => truthy
        paakayttotarkoitusKoodi (:paakayttotarkoitusKoodi Tavoitetila) => "011 yhden asunnon talot"
        rakennuksenKerrosluku (:rakennuksenKerrosluku Tavoitetila) => "2"
        kokonaisala (:kokonaisala Tavoitetila) => "220"
        huoneistoja (:asuinhuoneitojenLkm Tavoitetila) => "1"
        kerrosalatieto (:kerrosalatieto Tavoitetila) => truthy
        kerrosala (:kerrosala kerrosalatieto) => truthy
        pintala (:pintaAla kerrosala) => "200"
        paakayttotarkoitusKoodi (:paakayttotarkoitusKoodi kerrosala) => "011 yhden asunnon talot"

        uusit (some #(when (= (get-in % [:Toimenpide :tavoitetilatieto :Tavoitetila :paakayttotarkoitusKoodi]) "941 talousrakennukset") %) toimenpidetieto)
        uusit (:Toimenpide uusit)
        rakennustunnus (:rakennustunnus uusit) => nil
        _ (:liitetieto uusit) => nil
        kuvauskoodi (:kuvausKoodi uusit) => "uusi"
        kerrosalatieto (:kerrosalatieto uusit) => nil
        tavoitetilatieto (:tavoitetilatieto uusit) => truthy
        Tavoitetila (:Tavoitetila tavoitetilatieto) => truthy
        paakayttotarkoitusKoodi (:paakayttotarkoitusKoodi Tavoitetila) => "941 talousrakennukset"
        rakennuksenKerrosluku (:rakennuksenKerrosluku Tavoitetila) => "1"
        kokonaisala (:kokonaisala Tavoitetila) => "30"
        huoneistoja (:asuinhuoneitojenLkm Tavoitetila) => nil
        kerrosalatieto (:kerrosalatieto Tavoitetila) => truthy
        kerrosala (:kerrosala kerrosalatieto) => truthy
        pintala (:pintaAla kerrosala) => "25"
        paakayttotarkoitusKoodi (:paakayttotarkoitusKoodi kerrosala) => "941 talousrakennukset"

        laajennus-tp (get-toimenpiteet (tools/unwrapped [laajennus]))
        laajennus-tp (:Toimenpide (first laajennus-tp))
        rakennustunnus (:rakennustunnus laajennus-tp) => nil
        _ (:liitetieto laajennus-tp) => nil
        kuvauskoodi (:kuvausKoodi laajennus-tp) => "laajennus"
        kerrosalatieto (:kerrosalatieto laajennus-tp) => truthy
        kerrosala (:kerrosala kerrosalatieto) => truthy
        pintala (:pintaAla kerrosala) => "99"
        paakayttotarkoitusKoodi (:paakayttotarkoitusKoodi kerrosala) => "013 muut erilliset talot"
        tavoitetilatieto (:tavoitetilatieto laajennus-tp) => truthy
        Tavoitetila (:Tavoitetila tavoitetilatieto) => truthy
        paakayttotarkoitusKoodi (:paakayttotarkoitusKoodi Tavoitetila) => "941 talousrakennukset"
        rakennuksenKerrosluku (:rakennuksenKerrosluku Tavoitetila) => "1"
        kokonaisala (:kokonaisala Tavoitetila) => "30"
        huoneistoja (:asuinhuoneitojenLkm Tavoitetila) => nil
        kerrosalatieto (:kerrosalatieto Tavoitetila) => truthy
        kerrosala (:kerrosala kerrosalatieto) => truthy
        pintala (:pintaAla kerrosala) => "25"
        paakayttotarkoitusKoodi (:paakayttotarkoitusKoodi kerrosala) => "941 talousrakennukset"

        lausuntotieto (:lausuntotieto Poikkeamisasia) => truthy
        Lausunto (:Lausunto (first lausuntotieto)) => truthy
        viranomainen (:viranomainen Lausunto) => "Paloviranomainen"
        pyyntoPvm (:pyyntoPvm Lausunto) => "2013-09-17"
        lausuntotieto (:lausuntotieto Lausunto) => truthy
        annettu-lausunto (:Lausunto lausuntotieto) => truthy
        lausunnon-antanut-viranomainen (:viranomainen annettu-lausunto) => "Paloviranomainen"
        varsinainen-lausunto (:lausunto annettu-lausunto) => "Lausunto liitteen\u00e4."
        lausuntoPvm (:lausuntoPvm annettu-lausunto) => "2013-09-17"

        puoltotieto (:puoltotieto annettu-lausunto) => truthy
        Puolto (:Puolto puoltotieto) => truthy
        puolto (:puolto Puolto) => "puoltaa"
        lausuntoId (:id Lausunto) => "52385377da063788effc1e93"

        lisatietotieto (:lisatietotieto Poikkeamisasia) => truthy
        Lisatieto (:Lisatieto lisatietotieto) => truthy
        asioimiskieli (:asioimiskieli Lisatieto) => "suomi"
        suoramarkkinointikielto  (:suoramarkkinointikieltoKytkin Lisatieto) => nil?

        ;end of abstarctPoikkeamistype
        kaytttotapaus (:kayttotapaus Poikkeamisasia) => "Uusi poikkeamisasia"

        asianTiedot (:asianTiedot Poikkeamisasia) => truthy
        Asiantiedot (:Asiantiedot asianTiedot) => truthy
        vahainenPoikkeaminen (:vahainenPoikkeaminen Asiantiedot) => "Alueelle ei voimassa olevaa kaava."
        kuvaus (:poikkeamisasianKuvaus Asiantiedot) => "Omakotitalon ja tallin rakentaminen."]))


;Suunnitelutarveratkaisu

(fl/fact*
  (let [application (tools/unwrapped suunnitelutarveratkaisu)
        canonical (poikkeus-application-to-canonical application "fi" ) => truthy
        Popast (:Popast canonical) => truthy
        toimituksenTiedot (:toimituksenTiedot Popast) => truthy
        aineistonnimi (:aineistonnimi toimituksenTiedot) => (:title suunnitelutarveratkaisu)
        aineistotoimittaja (:aineistotoimittaja toimituksenTiedot) => "lupapiste@solita.fi"
        tila (:tila toimituksenTiedot) => "keskener\u00e4inen"
        kuntakoodi (:kuntakoodi toimituksenTiedot) => (:municipality suunnitelutarveratkaisu)

        suunnittelutarveasiatieto (:suunnittelutarveasiatieto Popast) => truthy
        poikkeamisasiatieto (:poikkeamisasiatieto Popast) => nil
        Suunnittelutarveasia (:Suunnittelutarveasia suunnittelutarveasiatieto)
        ;abstarctPoikkeamistype
        kasittelynTilatieto (:kasittelynTilatieto Suunnittelutarveasia) => truthy
        Tilamuutos (:Tilamuutos kasittelynTilatieto) => truthy
        pvm (:pvm Tilamuutos) => "2013-09-17"

        kuntakoodi (:kuntakoodi Suunnittelutarveasia) => (:municipality poikkari-hakemus)

        luvanTunnistetiedot (:luvanTunnistetiedot Suunnittelutarveasia) => truthy
        LupaTunnus (:LupaTunnus luvanTunnistetiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        MuuTunnus (:MuuTunnus muuTunnustieto) => truthy
        tunnus (:tunnus MuuTunnus) => "LP-753-2013-00001"
        sovellus (:sovellus MuuTunnus) => "Lupapiste"

        osapuolettieto (:osapuolettieto Suunnittelutarveasia) => truthy
        Osapuolet (:Osapuolet osapuolettieto) => truthy
        osapuolitieto (:osapuolitieto Osapuolet) => truthy

        ;Maksaja
        maksaja (some #(when (= (get-in % [:Osapuoli :VRKrooliKoodi] %) "maksaja") %) osapuolitieto) => truthy
        Osapuoli (:Osapuoli maksaja) => truthy
        _ (:turvakieltoKytkin Osapuoli) => false
        henkilo (:henkilo Osapuoli) => truthy
        _ (get-in henkilo [:nimi :etunimi]) => "Toimi"
        _ (get-in henkilo [:nimi :sukunimi]) => "Toimari"
        _ (:puhelin henkilo) => "020202"
        _ (:sahkopostiosoite henkilo) => "paajehu@yit.foo"
        yritys (:yritys Osapuoli) => truthy
        _ (:nimi yritys ) => "YIT"
        _ (:liikeJaYhteisotunnus yritys) => "1743842-0"
        postiosoite (:postiosoite yritys) => truthy
        _ (get-in postiosoite [:osoitenimi :teksti]) => "Koivukuja 2"
        _ (:postinumero postiosoite) => "23500"
        _ (:postitoimipaikannimi postiosoite) => "Helsinki"

        ;Hakija
        hakija (some #(when (= (get-in % [:Osapuoli :VRKrooliKoodi] %) "hakija") %) osapuolitieto) => truthy
        Osapuoli (:Osapuoli hakija) => truthy
        _ (:turvakieltoKytkin Osapuoli) => true
        henkilo (:henkilo Osapuoli) => truthy
        _ (get-in henkilo [:nimi :etunimi]) => "Pena"
        _ (get-in henkilo [:nimi :sukunimi]) => "Panaani"
        _ (:henkilotunnus henkilo) => "210281-9988"
        osoite (:osoite henkilo) => truthy
        _ (get-in osoite [:osoitenimi :teksti]) => "Paapankuja 12"
        _ (:postinumero osoite) => "10203"
        _ (:postitoimipaikannimi osoite) => "Piippola"
        _ (:puhelin henkilo) => "0102030405"
        _ (:sahkopostiosoite henkilo) => "pena@example.com"
        yritys (:yritys Osapuoli) => nil

        ;Paassuunnitelija
        suunnittelijatieto (:suunnittelijatieto Osapuolet) => truthy
        paasuunnittelija (some #(when (= (get-in % [:Suunnittelija :VRKrooliKoodi] %) "p\u00e4\u00e4suunnittelija") %) suunnittelijatieto) => truthy
        Suunnittelija (:Suunnittelija paasuunnittelija) => truthy
        henkilo (:henkilo Suunnittelija) => truthy
        _ (get-in henkilo [:nimi :etunimi]) => "Pena"
        _ (get-in henkilo [:nimi :sukunimi]) => "Panaani"
        _ (:puhelin henkilo) => "0102030405"
        _ (:sahkopostiosoite henkilo) => "pena@example.com"
        osoite (:osoite henkilo) => truthy
        _ (get-in osoite [:osoitenimi :teksti]) => "Paapankuja 12"
        _ (:postinumero osoite) => "10203"
        _ (:postitoimipaikannimi osoite) => "Piippola"
        _ (:koulutus Suunnittelija) => "Arkkitehti"
        _ (:patevyysvaatimusluokka Suunnittelija) => "AA"

        ;Suunnitelija
        suunnittelijatieto (:suunnittelijatieto Osapuolet) => truthy
        suunnittelija (some #(when (= (get-in % [:Suunnittelija :suunnittelijaRoolikoodi] %) "KVV-suunnittelija") %) suunnittelijatieto) => truthy
        Suunnittelija (:Suunnittelija suunnittelija) => truthy
        henkilo (:henkilo Suunnittelija) => truthy
        _ (get-in henkilo [:nimi :etunimi]) => "Pena"
        _ (get-in henkilo [:nimi :sukunimi]) => "Panaani"
        _ (:puhelin henkilo) => "0102030405"
        _ (:sahkopostiosoite henkilo) => "pena@example.com"
        osoite (:osoite henkilo) => truthy
        _ (get-in osoite [:osoitenimi :teksti]) => "Paapankuja 12"
        _ (:postinumero osoite) => "10203"
        _ (:postitoimipaikannimi osoite) => "Piippola"
        _ (:koulutus Suunnittelija) => "El\u00e4m\u00e4n koulu"
        _ (:patevyysvaatimusluokka Suunnittelija) => "C"

        rakennuspaikkatieto (:rakennuspaikkatieto Suunnittelutarveasia) => truthy
        Rakennuspaikkaf (first rakennuspaikkatieto) => truthy
        Rakennuspaikka (:Rakennuspaikka Rakennuspaikkaf) => truthy
        rakennuspaikanKiinteistotieto (:rakennuspaikanKiinteistotieto Rakennuspaikka) => truthy
        RakennuspaikanKiinteisto (:RakennuspaikanKiinteisto rakennuspaikanKiinteistotieto) => truthy
        kiinteistotieto (:kiinteistotieto RakennuspaikanKiinteisto) => truthy
        Kiinteisto (:Kiinteisto kiinteistotieto) => truthy
        tilannimi (:tilannimi Kiinteisto) => "Omatila"
        kiinteistotunnus (:kiinteistotunnus Kiinteisto) => "75342700020063"
        maaraalaTunnus (:maaraAlaTunnus Kiinteisto) => "M0008"
        rantaKytkin (:rantaKytkin Kiinteisto) => true
        kokotilaKytkin (:kokotilaKytkin RakennuspaikanKiinteisto) => false
        hallintaperuste (:hallintaperuste RakennuspaikanKiinteisto) => "oma"

        toimenpidetieto (:toimenpidetieto Suunnittelutarveasia) => truthy
        toimenpide-count (count toimenpidetieto) => 2
        uusi (some #(when (= (get-in % [:Toimenpide :tavoitetilatieto :Tavoitetila :paakayttotarkoitusKoodi]) "011 yhden asunnon talot") %) toimenpidetieto)
        uusi (:Toimenpide uusi)
        rakennustunnus (:rakennustunnus uusi) => nil
        _ (:liitetieto uusi) => nil
        kuvauskoodi (:kuvausKoodi uusi) => "uusi"
        kerrosalatieto (:kerrosalatieto uusi) => nil
        tavoitetilatieto (:tavoitetilatieto uusi) => truthy
        Tavoitetila (:Tavoitetila tavoitetilatieto) => truthy
        paakayttotarkoitusKoodi (:paakayttotarkoitusKoodi Tavoitetila) => "011 yhden asunnon talot"
        rakennuksenKerrosluku (:rakennuksenKerrosluku Tavoitetila) => "2"
        kokonaisala (:kokonaisala Tavoitetila) => "220"
        huoneistoja (:asuinhuoneitojenLkm Tavoitetila) => "1"
        kerrosalatieto (:kerrosalatieto Tavoitetila) => truthy
        kerrosala (:kerrosala kerrosalatieto) => truthy
        pintala (:pintaAla kerrosala) => "200"
        paakayttotarkoitusKoodi (:paakayttotarkoitusKoodi kerrosala) => "011 yhden asunnon talot"

        uusit (some #(when (= (get-in % [:Toimenpide :tavoitetilatieto :Tavoitetila :paakayttotarkoitusKoodi]) "941 talousrakennukset") %) toimenpidetieto)
        uusit (:Toimenpide uusit)
        rakennustunnus (:rakennustunnus uusit) => nil
        _ (:liitetieto uusit) => nil
        kuvauskoodi (:kuvausKoodi uusit) => "uusi"
        kerrosalatieto (:kerrosalatieto uusit) => nil
        tavoitetilatieto (:tavoitetilatieto uusit) => truthy
        Tavoitetila (:Tavoitetila tavoitetilatieto) => truthy
        paakayttotarkoitusKoodi (:paakayttotarkoitusKoodi Tavoitetila) => "941 talousrakennukset"
        rakennuksenKerrosluku (:rakennuksenKerrosluku Tavoitetila) => "1"
        kokonaisala (:kokonaisala Tavoitetila) => "30"
        huoneistoja (:asuinhuoneitojenLkm Tavoitetila) => nil
        kerrosalatieto (:kerrosalatieto Tavoitetila) => truthy
        kerrosala (:kerrosala kerrosalatieto) => truthy
        pintala (:pintaAla kerrosala) => "25"
        paakayttotarkoitusKoodi (:paakayttotarkoitusKoodi kerrosala) => "941 talousrakennukset"

        laajennus-tp (get-toimenpiteet (tools/unwrapped [laajennus]))
        laajennus-tp (:Toimenpide (first laajennus-tp))
        rakennustunnus (:rakennustunnus laajennus-tp) => nil
        kuvauskoodi (:kuvausKoodi laajennus-tp) => "laajennus"
        kerrosalatieto (:kerrosalatieto laajennus-tp) => truthy
        kerrosala (:kerrosala kerrosalatieto) => truthy
        pintala (:pintaAla kerrosala) => "99"
        paakayttotarkoitusKoodi (:paakayttotarkoitusKoodi kerrosala) => "013 muut erilliset talot"
        tavoitetilatieto (:tavoitetilatieto laajennus-tp) => truthy
        Tavoitetila (:Tavoitetila tavoitetilatieto) => truthy
        paakayttotarkoitusKoodi (:paakayttotarkoitusKoodi Tavoitetila) => "941 talousrakennukset"
        rakennuksenKerrosluku (:rakennuksenKerrosluku Tavoitetila) => "1"
        kokonaisala (:kokonaisala Tavoitetila) => "30"
        huoneistoja (:asuinhuoneitojenLkm Tavoitetila) => nil
        kerrosalatieto (:kerrosalatieto Tavoitetila) => truthy
        kerrosala (:kerrosala kerrosalatieto) => truthy
        pintala (:pintaAla kerrosala) => "25"
        paakayttotarkoitusKoodi (:paakayttotarkoitusKoodi kerrosala) => "941 talousrakennukset"

        lausuntotieto (:lausuntotieto Suunnittelutarveasia) => truthy
        Lausunto (:Lausunto (first lausuntotieto)) => truthy
        viranomainen (:viranomainen Lausunto) => "Paloviranomainen"
        pyyntoPvm (:pyyntoPvm Lausunto) => "2013-09-17"
        lausuntotieto (:lausuntotieto Lausunto) => truthy
        annettu-lausunto (:Lausunto lausuntotieto) => truthy
        lausunnon-antanut-viranomainen (:viranomainen annettu-lausunto) => "Paloviranomainen"
        varsinainen-lausunto (:lausunto annettu-lausunto) => "Lausunto liitteen\u00e4."
        lausuntoPvm (:lausuntoPvm annettu-lausunto) => "2013-09-17"

        puoltotieto (:puoltotieto annettu-lausunto) => truthy
        Puolto (:Puolto puoltotieto) => truthy
        puolto (:puolto Puolto) => "puoltaa"
        lausuntoId (:id Lausunto) => "52385377da063788effc1e93"

        lisatietotieto (:lisatietotieto Suunnittelutarveasia) => truthy
        Lisatieto (:Lisatieto lisatietotieto) => truthy
        asioimiskieli (:asioimiskieli Lisatieto) => "suomi"
        suoramarkkinointikielto  (:suoramarkkinointikieltoKytkin Lisatieto) => nil?

        ;end of abstarctPoikkeamistype
        kaytttotapaus (:kayttotapaus Suunnittelutarveasia) => "Uusi suunnittelutarveasia"

        asianTiedot (:asianTiedot Suunnittelutarveasia) => truthy
        Asiantiedot (:Asiantiedot asianTiedot) => truthy
        vahainenPoikkeaminen (:vahainenPoikkeaminen Asiantiedot) => "Alueelle ei voimassa olevaa kaava."
        kuvaus (:suunnittelutarveasianKuvaus Asiantiedot) => "Omakotitalon ja tallin rakentaminen."]))




