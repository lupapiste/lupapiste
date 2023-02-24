(ns lupapalvelu.document.ymparisto-ilmoitukset-canonical-test
  (:require [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.ymparisto-ilmoitukset-canonical :as yic]
            [lupapalvelu.document.ymparisto-schemas]
            [lupapalvelu.factlet :as fl]
            [midje.sweet :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]))

(def- kesto {:id "52ef4ef14206428d3c0394b7"
             :schema-info {:name "ymp-ilm-kesto", :version 1, :order 60}
             :data (tools/wrapped
                     {:kesto
                      {:0 {:alku "03.02.2014"
                           :loppu "7.2.2014"
                           :arkiAlkuAika "7:00", :arkiLoppuAika "21:30:00"
                           :lauantaiAlkuAika "8:00", :lauantaiLoppuAika "20:00:00.0"
                           :sunnuntaiAlkuAika "12:00", :sunnuntaiLoppuAika "18:00"}
                       :3 {:alku "03.02.2016"
                           :loppu "7.2.2016"
                           :arkiAlkuAika "17:00", :arkiLoppuAika "21:30:00"
                           :sunnuntaiAlkuAika "12:00", :sunnuntaiLoppuAika "18:00"}}})})

(def- meluilmo
  {:id "52ef4ef14206428d3c0394b5"
   :created 1391415025497
   :schema-info {:name "meluilmoitus", :version 1, :op {:id "52ef4ef14206428d3c0394b4", :name "meluilmoitus", :created 1391415025497}}
   :data {:melu
          {:melu10mdBa {:value "150"}
           :mittaus {:value "dbsid?"}
           :paivalla {:value "150"}
           :yolla {:value "0"}}
          :rakentaminen {:koneet {:value "Murskauksen ja rammeroinnin vaatimat koneet, sek\u00e4 py\u00f6r\u00e4kuormaaja. "}
                         :kuvaus {:value "Meluilmoitus louhinnasta, rammeroinnista ja murskauksesta"}
                         :melua-aihettava-toiminta {:value "louhinta"}}
          :tapahtuma {:kuvaus {:value "V\u00e4h\u00e4n virkistyst\u00e4 t\u00e4h\u00e4n v\u00e4liin"}
                      :nimi {:value "Louhijouden saunailta"}
                      :ulkoilmakonsertti {:value true}}}})

(def meluilmoitus-application {:sent nil,
                               :neighbors [],
                               :schema-version 1,
                               :authority {:role "authority",
                                           :lastName "Borga",
                                           :firstName "Pekka",
                                           :username "pekka",
                                           :id "777777777777777777000033"},
                               :auth [{:id "777777777777777777000033"
                                       :firstName "Pekka",
                                       :lastName "Borga",
                                       :username "pekka",
                                       :role "writer"}],
                               :drawings [],
                               :submitted 1391415717396,
                               :state "submitted",
                               :permitSubtype nil,
                               :tasks [],
                               :_verdicts-seen-by {},
                               :location [428195.77099609 6686701.3931274],
                               :attachments [],
                               :statements ctc/statements,
                               :organization "638-R",
                               :buildings [],
                               :title "Londb\u00f6lentie 97",
                               :started nil,
                               :closed nil,
                               :primaryOperation {:id "52ef4ef14206428d3c0394b4",
                                                  :name "meluilmoitus",
                                                  :created 1391415025497}
                               :secondaryOperations [],
                               :infoRequest false,
                               :openInfoRequest false,
                               :opened 1391415025497,
                               :created 1391415025497,
                               :_comments-seen-by {},
                               :propertyId "63844900010004",
                               :verdicts [],
                               :documents [(update-in ctc/henkiloilmoittaja
                                                      [:data :henkilo :kytkimet :suoramarkkinointilupa :value]
                                                      (constantly true))
                                           meluilmo
                                           kesto],
                               :_statements-seen-by {},
                               :modified 1391415696674,
                               :comments [],
                               :address "Londb\u00f6lentie 97",
                               :permitType "YI",
                               :id "LP-638-2014-00001",
                               :municipality "638"})


(def meluilmoitus-application-v2
  (ctc/add-ulkomainen-hetu-for-docs meluilmoitus-application))

(ctc/validate-all-documents meluilmoitus-application-v2)

(def meluilmoitus-yritys-application {:sent nil,
                                      :neighbors [],
                                      :schema-version 1,
                                      :authority {:role "authority",
                                                  :lastName "Borga",
                                                  :firstName "Pekka",
                                                  :username "pekka",
                                                  :id "777777777777777777000033"},
                                      :auth [{:lastName "Borga",
                                              :firstName "Pekka",
                                              :username "pekka",
                                              :role "writer",
                                              :id "777777777777777777000033"}],
                                      :drawings [],
                                      :submitted 1391415717396,
                                      :state "submitted",
                                      :permitSubtype nil,
                                      :tasks [],
                                      :_verdicts-seen-by {},
                                      :location [428195.77099609 6686701.3931274],
                                      :attachments [],
                                      :statements ctc/statements,
                                      :organization "638-R",
                                      :buildings [],
                                      :title "Londb\u00f6lentie 97",
                                      :started nil,
                                      :closed nil,
                                      :primaryOperation {:id "52ef4ef14206428d3c0394b4",
                                                         :name "meluilmoitus",
                                                         :created 1391415025497}
                                      :secondaryOperations [],
                                      :infoRequest false,
                                      :openInfoRequest false,
                                      :opened 1391415025497,
                                      :created 1391415025497,
                                      :_comments-seen-by {},
                                      :propertyId "63844900010004",
                                      :verdicts [],
                                      :documents [ctc/yritysilmoittaja
                                                  meluilmo
                                                  kesto],
                                      :_statements-seen-by {},
                                      :modified 1391415696674,
                                      :comments [],
                                      :address "Londb\u00f6lentie 97",
                                      :permitType "YI",
                                      :id "LP-638-2014-00001",
                                      :municipality "638"})

(ctc/validate-all-documents meluilmoitus-yritys-application)

(fl/facts* "Meluilmoitus to canonical"
  (let [canonical (yic/meluilmoitus-canonical meluilmoitus-application "fi") => truthy
        Ilmoitukset (:Ilmoitukset canonical) => truthy
        toimituksenTiedot (:toimituksenTiedot Ilmoitukset) => truthy
        _ (:aineistonnimi toimituksenTiedot) => (:title meluilmoitus-application)

        melutarina (:melutarina Ilmoitukset) => truthy
        Melutarina (:Melutarina melutarina)
        _ (:kasittelytietotieto Melutarina) => truthy

        luvanTunnistetiedot (:luvanTunnistetiedot Melutarina) => truthy
        LupaTunnus (:LupaTunnus luvanTunnistetiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        MuuTunnus (:MuuTunnus muuTunnustieto) => truthy
        _ (:tunnus MuuTunnus) => (:id meluilmoitus-application)
        _ (:sovellus MuuTunnus) => "Lupapiste"

        lausuntotieto (:lausuntotieto Melutarina) => truthy
        Lausunto (:Lausunto (first lausuntotieto)) => truthy
        _ (:viranomainen Lausunto) => "Paloviranomainen"
        _ (:pyyntoPvm Lausunto) => (date/xml-date "2013-09-17")
        lausuntotieto (:lausuntotieto Lausunto) => truthy
        annettu-lausunto (:Lausunto lausuntotieto) => truthy
        _ (:viranomainen annettu-lausunto) => "Paloviranomainen"
        _ (:lausunto annettu-lausunto) => "Lausunto liitteen\u00e4."
        _ (:lausuntoPvm annettu-lausunto) => (date/xml-date "2013-09-17")

        ilmoittaja (:ilmoittaja Melutarina) => truthy

        toiminnanSijainti (-> Melutarina :toiminnanSijaintitieto first :ToiminnanSijainti) => truthy
        Osoite (:Osoite toiminnanSijainti) => truthy
        _ (:osoitenimi Osoite) => {:teksti "Londb\u00f6lentie 97"}
        _ (:kunta Osoite) => "638"
        _ (:Kunta toiminnanSijainti) => "638"
        _ (:Kiinteistorekisterinumero toiminnanSijainti) => (:propertyId meluilmoitus-application)
        Sijainti (:Sijainti toiminnanSijainti) => truthy
        osoite (:osoite Sijainti) => truthy
        _ (:osoitenimi osoite) => {:teksti "Londb\u00f6lentie 97"}
        _ (:piste Sijainti) => {:Point {:pos "428195.77099609 6686701.3931274"}}

        toimintatieto (:toimintatieto Melutarina) => truthy
        Toiminta (:Toiminta toimintatieto) => truthy
        _ (:yksilointitieto Toiminta) => truthy
        _ (:alkuHetki Toiminta) => truthy
        rakentaminen (:rakentaminen Toiminta) => truthy
        _ (:louhinta rakentaminen) => "Meluilmoitus louhinnasta, rammeroinnista ja murskauksesta"
        _ (:murskaus rakentaminen) => nil
        _ (:paalutus rakentaminen) => nil
        _ (:muu rakentaminen) => nil

        tapahtuma (:tapahtuma Toiminta) => truthy
        _ (:ulkoilmakonsertti tapahtuma) => "Louhijouden saunailta - V\u00e4h\u00e4n virkistyst\u00e4 t\u00e4h\u00e4n v\u00e4liin"
        _ (:muu tapahtuma) => nil

        toiminnanKesto (:toiminnanKesto Melutarina) => truthy

        melutiedot (:melutiedot Melutarina) => truthy
        _ (:koneidenLkm melutiedot) => "Murskauksen ja rammeroinnin vaatimat koneet, sek\u00e4 py\u00f6r\u00e4kuormaaja. "
        melutaso (:melutaso melutiedot) => truthy
        _ (:db melutaso) => "150"
        _ (:paiva melutaso) => "150"
        _ (:yo melutaso) => "0"
        _ (:mittaaja melutaso) => "dbsid?"]
    (facts "ilmoittaja"
      (let [postiosoite (get-in ilmoittaja [:osoitetieto :Osoite]) => truthy
            osoitenimi (:osoitenimi postiosoite) => truthy]

        (:yhteyshenkilonNimi ilmoittaja) => nil
        (:yrityksenNimi ilmoittaja) => nil
        (:yTunnus ilmoittaja) => nil

        (:teksti osoitenimi) => "Murskaajankatu 5"
        (:postinumero postiosoite) => "36570"
        (:postitoimipaikannimi postiosoite) => "Kaivanto"
        (:valtioSuomeksi postiosoite) => "Suomi"
        (:valtioKansainvalinen postiosoite) => "FIN"
        (:etunimi ilmoittaja) => "Pekka"
        (:sukunimi ilmoittaja) => "Borga"
        (:sahkopostiosoite ilmoittaja) => "pekka.borga@porvoo.fi"
        (:puhelinnumero ilmoittaja) => "121212"
        (:suoramarkkinointikielto ilmoittaja) => false))

    (fact "toiminnan kesto"
      (:alkuPvm toiminnanKesto) => (date/xml-date "2014-02-03")
      (:loppuPvm toiminnanKesto) => (date/xml-date "2014-02-07")

      (:arkiAlkuAika toiminnanKesto) => "07:00:00"
      (:arkiLoppuAika toiminnanKesto) => "21:30:00"
      (:lauantaiAlkuAika toiminnanKesto) => "08:00:00"
      (:lauantaiLoppuAika toiminnanKesto) => "20:00:00.0"
      (:sunnuntaiAlkuAika toiminnanKesto) => "12:00:00"
      (:sunnuntaiLoppuAika toiminnanKesto) => "18:00:00")))

(fl/facts* "Meluilmoitus yritysilmoittaja to canonical"
  (let [canonical (yic/meluilmoitus-canonical meluilmoitus-yritys-application "fi") => truthy
        Ilmoitukset (:Ilmoitukset canonical) => truthy
        toimutuksenTiedot (:toimituksenTiedot Ilmoitukset) => truthy
        _ (:aineistonnimi toimutuksenTiedot) => (:title meluilmoitus-application)

        melutarina (-> Ilmoitukset :melutarina :Melutarina) => truthy
        _ (:kasittelytietotieto melutarina) => truthy

        luvanTunnistetiedot (:luvanTunnistetiedot melutarina) => truthy
        LupaTunnus (:LupaTunnus luvanTunnistetiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        MuuTunnus (:MuuTunnus muuTunnustieto) => truthy
        _ (:tunnus MuuTunnus) => (:id meluilmoitus-application)
        _ (:sovellus MuuTunnus) => "Lupapiste"]
    (fact "yritys-ilmoittaja"
      (:ilmoittaja melutarina) => {:yTunnus "1060155-5"
                                   :yrityksenNimi "Yrtti Oy"
                                   :yhteyshenkilonNimi "Pertti Yritt\u00e4j\u00e4"
                                   :osoitetieto {:Osoite {:osoitenimi {:teksti "H\u00e4meenkatu 3 "},
                                                          :postitoimipaikannimi "kuuva",
                                                          :postinumero "43640"
                                                          :valtioSuomeksi "Suomi"
                                                          :valtioKansainvalinen "FIN" }}
                                   :puhelinnumero "060222155"
                                   :sahkopostiosoite "tew@gjr.fi"
                                   :suoramarkkinointikielto true})))
