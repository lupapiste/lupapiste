(ns lupapalvelu.document.ymparisto-ilmoitukset-canonical-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.document.ymparisto-ilmoitukset-canonical :as yic]
            [lupapalvelu.factlet :as fl]
            [lupapalvelu.document.canonical-test-common :refer :all]
            [lupapalvelu.document.ymparisto-schemas]))

(def ^:private kesto {:created 1391415025497,
                      :data
                      {:kesto
                       {:alku {:modified 1391415615718, :value "03.02.2014"},
                        :kello
                        {:arkisin {:modified 1391415637288, :value "07.00 - 16:00"},
                         :lauantait {:modified 1391415639677, :value "-"},
                         :pyhat {:modified 1391415640276, :value "-"}},
                        :loppu {:modified 1391415618809, :value "07.02.2014"}}},
                      :id "52ef4ef14206428d3c0394b7",
                      :schema-info {:name "ymp-ilm-kesto", :version 1, :order 60}})

(def ^:private meluilmo {:created 1391415025497,
               :data
               {:melu
                {:melu10mdBa {:modified 1391415596372, :value "150"},
                 :mittaus {:modified 1391415612510, :value "dbsid?"},
                 :paivalla {:modified 1391415601672
                            :value "150"},
                 :yolla {:modified 1391415602101, :value "0"}},
                :rakentaminen
                {:koneet
                 {:modified 1391415557870,
                  :value
                  "Murskauksen ja rammeroinnin vaatimat koneet, sek\u00e4 py\u00f6r\u00e4kuormaaja. "},
                 :kuvaus
                 {:modified 1391415519512,
                  :value
                  "Meluilmoitus louhinnasta, rammeroinnista ja murskauksesta"},
                 :melua-aihettava-toiminta
                 {:modified 1391415423129, :value "louhinta"}},
                :tapahtuma
                {:kuvaus
                 {:modified 1391415593261,
                  :value "V\u00e4h\u00e4n virkistyst\u00e4 t\u00e4h\u00e4n v\u00e4liin"},
                 :nimi {:modified 1391415570121, :value "Louhijouden saunailta"},
                 :ulkoilmakonsertti {:modified 1391415571551, :value true}}},
               :id "52ef4ef14206428d3c0394b5",
               :schema-info
               {:order 50,
                :version 1,
                :name "meluilmoitus",
                :op
                {:id "52ef4ef14206428d3c0394b4",
                 :name "meluilmoitus",
                 :created 1391415025497},
                :removable true}})

(def meluilmoitus-application {:sent nil,
                               :neighbors {},
                               :schema-version 1,
                               :authority
                               {:role "authority",
                                :lastName "Borga",
                                :firstName "Pekka",
                                :username "pekka",
                                :id "777777777777777777000033"},
                               :auth
                               [{:lastName "Borga",
                                 :firstName "Pekka",
                                 :username "pekka",
                                 :type "owner",
                                 :role "owner",
                                 :id "777777777777777777000033"}],
                               :drawings [],
                               :submitted 1391415717396,
                               :state "submitted",
                               :permitSubtype nil,
                               :tasks [],
                               :_verdicts-seen-by {},
                               :location {:x 428195.77099609, :y 6686701.3931274},
                               :attachments [],
                               :statements statements,
                               :organization "638-R",
                               :buildings [],
                               :title "Londb\u00f6lentie 97",
                               :started nil,
                               :closed nil,
                               :operations
                               [{:id "52ef4ef14206428d3c0394b4",
                                 :name "meluilmoitus",
                                 :created 1391415025497}],
                               :infoRequest false,
                               :openInfoRequest false,
                               :opened 1391415025497,
                               :created 1391415025497,
                               :_comments-seen-by {},
                               :propertyId "63844900010004",
                               :verdicts [],
                               :documents
                               [henkilohakija
                                meluilmo
                                kesto],
                               :_statements-seen-by {},
                               :modified 1391415696674,
                               :comments [],
                               :address "Londb\u00f6lentie 97",
                               :permitType "YI",
                               :id "LP-638-2014-00001",
                               :municipality "638"})

(def meluilmoitus-yritys-application {:sent nil,
                               :neighbors {},
                               :schema-version 1,
                               :authority
                               {:role "authority",
                                :lastName "Borga",
                                :firstName "Pekka",
                                :username "pekka",
                                :id "777777777777777777000033"},
                               :auth
                               [{:lastName "Borga",
                                 :firstName "Pekka",
                                 :username "pekka",
                                 :type "owner",
                                 :role "owner",
                                 :id "777777777777777777000033"}],
                               :drawings [],
                               :submitted 1391415717396,
                               :state "submitted",
                               :permitSubtype nil,
                               :tasks [],
                               :_verdicts-seen-by {},
                               :location {:x 428195.77099609, :y 6686701.3931274},
                               :attachments [],
                               :statements statements,
                               :organization "638-R",
                               :buildings [],
                               :title "Londb\u00f6lentie 97",
                               :started nil,
                               :closed nil,
                               :operations
                               [{:id "52ef4ef14206428d3c0394b4",
                                 :name "meluilmoitus",
                                 :created 1391415025497}],
                               :infoRequest false,
                               :openInfoRequest false,
                               :opened 1391415025497,
                               :created 1391415025497,
                               :_comments-seen-by {},
                               :propertyId "63844900010004",
                               :verdicts [],
                               :documents
                               [yrityshakija
                                meluilmo
                                kesto],
                               :_statements-seen-by {},
                               :modified 1391415696674,
                               :comments [],
                               :address "Londb\u00f6lentie 97",
                               :permitType "YI",
                               :id "LP-638-2014-00001",
                               :municipality "638"})

(fact "Meta test: kesto"           kesto          => valid-against-current-schema?)
(fact "Meta test: meluilmo"        meluilmo       => valid-against-current-schema?)

(fl/facts* "Meluilmoitus to canonical"
           (let [canonical (yic/meluilmoitus-canonical meluilmoitus-application "fi") => truthy
                 Ilmoitukset (:Ilmoitukset canonical) => truthy
                 toimituksenTiedot (:toimituksenTiedot Ilmoitukset) => truthy
                 aineistonnimi (:aineistonnimi toimituksenTiedot) => (:title meluilmoitus-application)

                 melutarina (:melutarina Ilmoitukset) => truthy
                 Melutarina (:Melutarina melutarina)
                 kasittelytietotieto (:kasittelytietotieto Melutarina) => truthy

                 luvanTunnistetiedot (:luvanTunnistetiedot Melutarina) => truthy
                 LupaTunnus (:LupaTunnus luvanTunnistetiedot) => truthy
                 muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
                 MuuTunnus (:MuuTunnus muuTunnustieto) => truthy
                 tunnus (:tunnus MuuTunnus) => (:id meluilmoitus-application)
                 sovellus (:sovellus MuuTunnus) => "Lupapiste"

                 lausuntotieto (:lausuntotieto Melutarina) => truthy
                 Lausunto (:Lausunto (first lausuntotieto)) => truthy
                 viranomainen (:viranomainen Lausunto) => "Paloviranomainen"
                 pyyntoPvm (:pyyntoPvm Lausunto) => "2013-09-17"
                 lausuntotieto (:lausuntotieto Lausunto) => truthy
                 annettu-lausunto (:Lausunto lausuntotieto) => truthy
                 lausunnon-antanut-viranomainen (:viranomainen annettu-lausunto) => "Paloviranomainen"
                 varsinainen-lausunto (:lausunto annettu-lausunto) => "Lausunto liitteen\u00e4."
                 lausuntoPvm (:lausuntoPvm annettu-lausunto) => "2013-09-17"

                 ilmoittaja (:ilmoittaja Melutarina) => truthy
                 nimi (:nimi ilmoittaja) => "Yksityishenkil\u00f6"
                 postiosoite (:postiosoite ilmoittaja) => truthy
                 osoitenimi (:osoitenimi postiosoite) => truthy
                 teksti (:teksti osoitenimi) => "Murskaajankatu 5"
                 postinumero (:postinumero postiosoite) => "36570"
                 postitoimipaikannimi (:postitoimipaikannimi postiosoite) => "Kaivanto"

                 yhteyshenkilo (:yhteyshenkilo ilmoittaja) => truthy
                 nimi (:nimi yhteyshenkilo) => {:etunimi "Pekka" :sukunimi "Borga"}
                 sahkopostiosoite (:sahkopostiosoite yhteyshenkilo) => "pekka.borga@porvoo.fi"
                 puhelin (:puhelin yhteyshenkilo) => "121212"

                 liikeJaYhteisotunnus (:liikeJaYhteisotunnus ilmoittaja) => nil

                 toiminnanSijainti (:toiminnanSijainti Melutarina) => truthy
                 Osoite (:Osoite toiminnanSijainti) => truthy
                 osoitenimi (:osoitenimi Osoite) => {:teksti "Londb\u00f6lentie 97"}
                 kunta (:kunta Osoite) => "638"
                 Kunta (:Kunta toiminnanSijainti) => "638"
                 Kiinteistorekisterinumero (:Kiinteistorekisterinumero toiminnanSijainti) => (:propertyId meluilmoitus-application)
                 Sijainti (:Sijainti toiminnanSijainti) => truthy
                 osoite (:osoite Sijainti) => truthy
                 osoitenimi (:osoitenimi osoite) => {:teksti "Londb\u00f6lentie 97"}
                 piste (:piste Sijainti) => {:Point {:pos "428195.77099609 6686701.3931274"}}

                 toimintatieto (:toimintatieto Melutarina) => truthy
                 Toiminta (:Toiminta toimintatieto) => truthy
                 yksilointitieto (:yksilointitieto Toiminta) => truthy
                 alkuHetki (:alkuHetki Toiminta) => truthy
                 rakentaminen (:rakentaminen Toiminta) => truthy
                 louhinta (:louhinta rakentaminen) => "Meluilmoitus louhinnasta, rammeroinnista ja murskauksesta"
                 murskaus (:murskaus rakentaminen) => nil
                 paalutus (:paalutus rakentaminen) => nil
                 muu (:muu rakentaminen) => nil

                 tapahtuma (:tapahtuma Toiminta) => truthy
                 ulkoilmakonsertti (:ulkoilmakonsertti tapahtuma) => "Louhijouden saunailta - V\u00e4h\u00e4n virkistyst\u00e4 t\u00e4h\u00e4n v\u00e4liin"
                 muu (:muu tapahtuma) => nil

                 toiminnanKesto (:toiminnanKesto Melutarina) => truthy
                 alkuHetki (:alkuHetki toiminnanKesto) => "2014-02-03T00:00:00"
                 loppuHetki (:loppuHetki toiminnanKesto) => "2014-02-07T00:00:00"
                 arkisin (:arkisin toiminnanKesto) => "07.00 - 16:00"
                 lauantaisin (:lauantaisin toiminnanKesto) => "-"
                 pyhisin (:pyhisin toiminnanKesto) => "-"

                 melutiedot (:melutiedot Melutarina) => truthy
                 koneidenLkm (:koneidenLkm melutiedot) => nil
                 melutaso (:melutaso melutiedot) => truthy
                 db (:db melutaso) => "150"
                 paiva (:paiva melutaso) => "150"
                 yo (:yo melutaso) => "0"
                 mittaaja (:mittaaja melutaso) => "dbsid?"]

;                 (clojure.pprint/pprint canonical)
))

(fl/facts* "Meluilmoitus yrityshakija to canonical"
           (let [canonical (yic/meluilmoitus-canonical meluilmoitus-yritys-application "fi") => truthy
                 Ilmoitukset (:Ilmoitukset canonical) => truthy
                 toimutuksenTiedot (:toimituksenTiedot Ilmoitukset) => truthy
                 aineistonnimi (:aineistonnimi toimutuksenTiedot) => (:title meluilmoitus-application)

                 melutarina (-> Ilmoitukset :melutarina :Melutarina) => truthy
                 kasittelytietotieto (:kasittelytietotieto melutarina) => truthy

                 luvanTunnistetiedot (:luvanTunnistetiedot melutarina) => truthy
                 LupaTunnus (:LupaTunnus luvanTunnistetiedot) => truthy
                 muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
                 MuuTunnus (:MuuTunnus muuTunnustieto) => truthy
                 tunnus (:tunnus MuuTunnus) => (:id meluilmoitus-application)
                 sovellus (:sovellus MuuTunnus) => "Lupapiste"
                 ilmoittaja (:ilmoittaja melutarina) => {:nimi "Yrtti Oy",
                                                         :postiosoite
                                                         {:osoitenimi {:teksti "H\u00e4meenkatu 3 "},
                                                          :postitoimipaikannimi "kuuva",
                                                          :postinumero "43640"},
                                                         :yhteyshenkilo
                                                         {:nimi {:sukunimi "Yritt\u00e4j\u00e4", :etunimi "Pertti"},
                                                          :puhelin "060222155",
                                                          :sahkopostiosoite "tew@gjr.fi"},
                                                         :liikeJaYhteisotunnus "1060155-5"}]))




