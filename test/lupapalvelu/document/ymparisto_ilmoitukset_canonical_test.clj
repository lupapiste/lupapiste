(ns lupapalvelu.document.ymparisto-ilmoitukset-canonical-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.document.ymparisto-ilmoitukset-canonical :as yic]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.factlet :as fl]
            [lupapalvelu.document.canonical-test-common :refer :all]
            [lupapalvelu.document.ymparisto-schemas]))

(def ^:private kesto {:id "52ef4ef14206428d3c0394b7"
                      :schema-info {:name "ymp-ilm-kesto", :version 1, :order 60}
                      :data
                      (tools/wrapped
                        {:kesto
                         {:alku "03.02.2014"
                          :loppu "7.2.2014"
                          :arki {:arkiAlkuAika "7:00", :arkiLoppuAika "21:30"}
                          :lauantai {:lauantaiAlkuAika "7:00", :lauantaiLoppuAika "21:30"}
                          :sunnuntai {:sunnuntaiAlkuAika "7:00", :sunnuntaiLoppuAika "21:30"}}})
                      })

(fact "Meta test: kesto" kesto => valid-against-current-schema?)

(def ^:private meluilmo
  {:id "52ef4ef14206428d3c0394b5"
   :created 1391415025497
   :schema-info {:name "meluilmoitus", :version 1, :op {:id "52ef4ef14206428d3c0394b4", :name "meluilmoitus", :created 1391415025497}}
   :data
   {:melu
    {:melu10mdBa {:value "150"}
     :mittaus {:value "dbsid?"}
     :paivalla {:value "150"}
     :yolla {:value "0"}}
    :rakentaminen
    {:koneet
     {:value "Murskauksen ja rammeroinnin vaatimat koneet, sek\u00e4 py\u00f6r\u00e4kuormaaja. "}
     :kuvaus
     {:value "Meluilmoitus louhinnasta, rammeroinnista ja murskauksesta"}
     :melua-aihettava-toiminta {:value "louhinta"}}
    :tapahtuma {:kuvaus {:value "V\u00e4h\u00e4n virkistyst\u00e4 t\u00e4h\u00e4n v\u00e4liin"}
                :nimi {:value "Louhijouden saunailta"}
                :ulkoilmakonsertti {:value true}}}
   })

(fact "Meta test: meluilmo" meluilmo => valid-against-current-schema?)

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

                 toiminnanSijainti (get-in Melutarina [:toiminnanSijaintitieto :ToiminnanSijainti]) => truthy
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

                 melutiedot (:melutiedot Melutarina) => truthy
                 koneidenLkm (:koneidenLkm melutiedot) => nil
                 melutaso (:melutaso melutiedot) => truthy
                 db (:db melutaso) => "150"
                 paiva (:paiva melutaso) => "150"
                 yo (:yo melutaso) => "0"
                 mittaaja (:mittaaja melutaso) => "dbsid?"]


             (facts "ilmoittaja"
               (let [postiosoite (get-in ilmoittaja [:osoitetieto :Osoite]) => truthy
                     osoitenimi (:osoitenimi postiosoite) => truthy]

                 (:yhteyshenkilonNimi ilmoittaja) => nil
                 (:yrityksenNimi ilmoittaja) => nil
                 (:yTunnus ilmoittaja) => nil

                 (:teksti osoitenimi) => "Murskaajankatu 5"
                 (:postinumero postiosoite) => "36570"
                 (:postitoimipaikannimi postiosoite) => "Kaivanto"
                 (:etunimi ilmoittaja) => "Pekka"
                 (:sukunimi ilmoittaja) => "Borga"
                 (:sahkopostiosoite ilmoittaja) => "pekka.borga@porvoo.fi"
                 (:puhelinnumero ilmoittaja) => "121212")
               )

             (fact "toiminnan kesto"
               (:alkuPvm toiminnanKesto) => "2014-02-03"
               (:loppuPvm toiminnanKesto) => "2014-02-07"
               ; FIXME
               ;arkisin (:arkisin toiminnanKesto) => "07.00 - 16:00"
               ;lauantaisin (:lauantaisin toiminnanKesto) => "-"
               ;pyhisin (:pyhisin toiminnanKesto) => "-"
               )


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
        sovellus (:sovellus MuuTunnus) => "Lupapiste"]

    (fact "yritys-ilmoittaja"
      (:ilmoittaja melutarina) => {:yTunnus "1060155-5"
                                   :yrityksenNimi "Yrtti Oy"
                                   :yhteyshenkilonNimi "Pertti Yritt\u00e4j\u00e4"
                                   :osoitetieto {:Osoite {:osoitenimi {:teksti "H\u00e4meenkatu 3 "},
                                                          :postitoimipaikannimi "kuuva",
                                                          :postinumero "43640"}}
                                   :puhelinnumero "060222155"
                                   :sahkopostiosoite "tew@gjr.fi"})
    ))




