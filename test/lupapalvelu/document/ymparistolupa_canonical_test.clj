(ns lupapalvelu.document.ymparistolupa-canonical-test
  (:require [midje.sweet :refer :all]
            [sade.util :as util]
            [lupapalvelu.document.ymparistolupa-canonical :as ylc]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.document.ymparisto-schemas]))

(def kuvaus
  {:id "53049e12e82cf6ec14b308a8"
   :created 1392811538915
   :schema-info {:name "yl-hankkeen-kuvaus"
                 :op {:id "abba1" :name "yl-uusi-toiminta" :created 1392811538915}
                 :order 1
                 :version 1
                 :removable true}
   :data {:kuvaus {:value "Hankkeen kuvauskentan sisalto" :modified 1392811539061}
          :peruste {:value "Hankkeen peruste" :modified 1392811539061}}})

(def application {:id "LP-638-2014-00001"
                  :attachments []
                  :auth [{:id "777777777777777777000033"
                          :firstName "Pekka"
                          :lastName "Borga"
                          :username "pekka"
                          :type "owner"
                          :role "owner"}]
                  :authority {:id "777777777777777777000033"
                              :firstName "Pekka"
                              :lastName "Borga"
                              :role "authority"
                              :username "pekka"}
                  :address "Londb\u00f6lentie 97"
                  :title "Londb\u00f6lentie 97"
                  :created 1391415025497
                  :documents [kuvaus
                              ctc/henkilohakija
                              (select-keys ctc/henkilohakija [:schema-info])
                              ctc/yrityshakija
                              ctc/henkilomaksaja]
                  :drawings []
                  :infoRequest false
                  :linkPermitData [{:id "LP-638-2013-00099" :type "lupapistetunnus"} {:id "kuntalupa-123" :type "kuntalupatunnus"}]
                  :location [428195.77099609 6686701.3931274]
                  :neighbors []
                  :modified 1391415696674
                  :municipality "638"
                  :primaryOperation {:id "abba1" :name "yl-uusi-toiminta" :created 1391415025497}
                  :secondaryOperations []
                  :openInfoRequest false
                  :opened 1391415025497
                  :organization "638-R"
                  :permitType "YL"
                  :permitSubtype nil
                  :propertyId "63844900010004"
                  :schema-version 1
                  :sent nil
                  :started nil
                  :state "submitted"
                  :statements ctc/statements
                  :submitted 1391415717396})

(ctc/validate-all-documents application)

(facts* "ymparistolupa to canonical"
  (let [canonical (ylc/ymparistolupa-canonical application "fi") => truthy
        Ymparistoluvat (:Ymparistoluvat canonical) => truthy
        toimituksenTiedot (:toimituksenTiedot Ymparistoluvat) => truthy
        aineistonnimi (:aineistonnimi toimituksenTiedot) => (:title application)

        ymparistolupatieto (:ymparistolupatieto Ymparistoluvat) => truthy
        ymparistolupa (:Ymparistolupa ymparistolupatieto)
        kasittelytietotieto (:kasittelytietotieto ymparistolupa) => truthy

        luvanTunnistetiedot (:luvanTunnistetiedot ymparistolupa) => truthy
        LupaTunnus (:LupaTunnus luvanTunnistetiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        MuuTunnus (:MuuTunnus muuTunnustieto) => truthy
        tunnus (:tunnus MuuTunnus) => (:id application)
        sovellus (:sovellus MuuTunnus) => "Lupapiste"

        lausuntotieto (:lausuntotieto ymparistolupa) => truthy
        Lausunto (:Lausunto (first lausuntotieto)) => truthy
        viranomainen (:viranomainen Lausunto) => "Paloviranomainen"
        pyyntoPvm (:pyyntoPvm Lausunto) => "2013-09-17"
        lausuntotieto (:lausuntotieto Lausunto) => truthy
        annettu-lausunto (:Lausunto lausuntotieto) => truthy
        lausunnon-antanut-viranomainen (:viranomainen annettu-lausunto) => "Paloviranomainen"
        varsinainen-lausunto (:lausunto annettu-lausunto) => "Lausunto liitteen\u00e4."
        lausuntoPvm (:lausuntoPvm annettu-lausunto) => "2013-09-17"

        hakijat (:hakija ymparistolupa) => seq
        luvat (get-in ymparistolupa [:voimassaOlevatLuvat :luvat :lupa]) => seq

        maksaja (get-in ymparistolupa [:maksajatieto :Maksaja]) => truthy
        yritysmaksaja (get-in ymparistolupa [:maksajatieto :Maksaja]) => truthy]

    (fact "Canonical model has all fields"
      (util/contains-value? canonical nil?) => falsey)

    (facts "hakijat"
      (count hakijat) => 2

      (let [hakija (first hakijat)
            postiosoite (get-in hakija [:osoitetieto :Osoite]) => truthy
            osoitenimi (:osoitenimi postiosoite) => truthy]

        (:yhteyshenkilonNimi hakija) => nil
        (:yrityksenNimi hakija) => nil
        (:yTunnus hakija) => nil

        (:teksti osoitenimi) => "Murskaajankatu 5"
        (:postinumero postiosoite) => "36570"
        (:postitoimipaikannimi postiosoite) => "Kaivanto"
        (:valtioKansainvalinen postiosoite) => "FIN"
        (:valtioSuomeksi postiosoite) => "Suomi"
        (:etunimi hakija) => "Pekka"
        (:sukunimi hakija) => "Borga"
        (:sahkopostiosoite hakija) => "pekka.borga@porvoo.fi"
        (:puhelinnumero hakija) => "121212")

      (second hakijat) => {:yTunnus "1060155-5"
                           :yrityksenNimi "Yrtti Oy"
                           :yhteyshenkilonNimi "Pertti Yritt\u00e4j\u00e4"
                           :osoitetieto {:Osoite {:osoitenimi {:teksti "H\u00e4meenkatu 3 "},
                                                  :postitoimipaikannimi "kuuva",
                                                  :postinumero "43640"
                                                  :valtioSuomeksi "Suomi"
                                                  :valtioKansainvalinen "FIN"}}
                           :puhelinnumero "060222155"
                           :sahkopostiosoite "tew@gjr.fi"})

    (fact "kiinteistotunnus"
      (get-in ymparistolupa [:laitoksentiedot :Laitos :kiinttun]) => (:propertyId application)
      )

    (facts "kuvaus"
      (get-in ymparistolupa [:toiminta :kuvaus]) => "Hankkeen kuvauskentan sisalto"
      (get-in ymparistolupa [:toiminta :peruste]) => "Hankkeen peruste")

    (facts "luvat"
      (count luvat) => 2
      (first luvat) => {:tunnistetieto "LP-638-2013-00099" :kuvaus "lupapistetunnus"}
      (second luvat) => {:tunnistetieto "kuntalupa-123" :kuvaus "kuntalupatunnus"})

    (facts "sijainti"
      (let [tiedot-sijainnista (get-in ymparistolupa [:toiminnanSijaintitieto :ToiminnanSijainti])
            sijainti (-> tiedot-sijainnista :sijaintitieto first :Sijainti)
            osoite (:osoite sijainti)]

        (:yksilointitieto tiedot-sijainnista) => (:id application)
        (:osoitenimi osoite) => {:teksti "Londb\u00f6lentie 97"}
        (:piste sijainti) => {:Point {:pos "428195.77099609 6686701.3931274"}}))

    (facts "maksaja"
      (let [postiosoite (get-in maksaja [:osoitetieto :Osoite]) => truthy
            osoitenimi (:osoitenimi postiosoite) => truthy]
        (:teksti osoitenimi) => "Satakunnankatu"
        (:postinumero postiosoite) => "33210"
        (:postitoimipaikannimi postiosoite) => "Tammerfors"
        (:etunimi maksaja) => "Pappa"
        (:sukunimi maksaja) => "Betalare"
        (:henkilotunnus maksaja) => "210354-947E"
        (:sahkopostiosoite maksaja) => "pappa@example.com"
        (:puhelinnumero maksaja) => "0400-123456"
        (:laskuviite maksaja) => "1686343528523"))
    ))

(def application-yritysmaksaja
  (merge application {:documents [kuvaus
                                  ctc/henkilohakija
                                  (select-keys ctc/henkilohakija [:schema-info])
                                  ctc/yrityshakija
                                  ctc/yritysmaksaja]}))

(ctc/validate-all-documents application-yritysmaksaja)

(facts* "yritysmaksaja-ymparistolupa to canonical"
  (let [canonical (ylc/ymparistolupa-canonical application-yritysmaksaja "fi") => truthy
        ymparistolupa (get-in canonical [:Ymparistoluvat :ymparistolupatieto :Ymparistolupa])
        yritysmaksaja (get-in ymparistolupa [:maksajatieto :Maksaja]) => truthy]
    (facts "yritysmaksaja"
      (let [Verkkolaskutus (get-in yritysmaksaja [:verkkolaskutustieto :Verkkolaskutus]) => truthy]
        (doseq [[k v] {:ovtTunnus "003712345671"
                       :verkkolaskuTunnus "verkkolaskuTunnus"
                       :valittajaTunnus "BAWCFI22"}]
          (k Verkkolaskutus) => v))

      (let [Osoite (get-in yritysmaksaja [:osoitetieto :Osoite]) => truthy
            osoitenimi (:osoitenimi Osoite) => truthy]
        (:teksti osoitenimi) => "Satakunnankatu"

        (doseq [[k v] {:postitoimipaikannimi "Tammerfors"
                       :postinumero "33210"}]
          (k Osoite) => v))

      (:yrityksenNimi yritysmaksaja) => "Solita Oy"
      (:puhelinnumero yritysmaksaja) => "0400-123456"
      (:sahkopostiosoite yritysmaksaja) => "pappa@example.com"
      (:yTunnus yritysmaksaja) => "1234567-1"
      (:yhteyshenkilonNimi yritysmaksaja) => "Pappa Betalare")))
