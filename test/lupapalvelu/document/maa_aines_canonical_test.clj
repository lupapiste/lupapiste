(ns lupapalvelu.document.maa-aines-canonical-test
  (:require [midje.sweet :refer :all]
            [sade.util :as util]
            [lupapalvelu.document.maa-aines-canonical :as mac]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.document.ymparisto-schemas]))

(def schema-version 1)

(def maksaja (assoc ctc/henkilohakija :schema-info {:name "ymp-maksaja" :type "party" :version schema-version}))

(def maa-aineslupa-kuvaus {:schema-info {:name "maa-aineslupa-kuvaus" :version schema-version}
                           :data {:kuvaus {:value "Hankkeen synopsis"}}})

(def application {:id "LP-638-2014-00001"
                  :attachments []
                  :auth [{:id "777777777777777777000033"
                          :firstName "Pekka"
                          :lastName "Borga"
                          :username "pekka"
                          :role "writer"}]
                  :authority {:id "777777777777777777000033"
                              :firstName "Pekka"
                              :lastName "Borga"
                              :role "authority"
                              :username "pekka"}
                  :address "Londb\u00f6lentie 97"
                  :created 1391415025497
                  :documents [(update-in ctc/yrityshakija
                                         [:data :yritys :yhteyshenkilo :kytkimet :suoramarkkinointilupa :value]
                                         (constantly true))
                              maa-aineslupa-kuvaus
                              maksaja]
                  :drawings ctc/drawings
                  :infoRequest false
                  :location [428195.77099609 6686701.3931274]
                  :neighbors []
                  :modified 1391415696674
                  :municipality "638"
                  :primaryOperation {:id "abba1" :name "maa-aineslupa" :created 1391415025497}
                  :secondaryOperations []
                  :openInfoRequest false
                  :opened 1391415025497
                  :organization "638-R"
                  :permitType "MAL"
                  :permitSubtype nil
                  :propertyId "63844900010004"
                  :schema-version schema-version
                  :sent nil
                  :started nil
                  :state "submitted"
                  :statements ctc/statements
                  :submitted 1391415717396
                  :title "Londb\u00f6lentie 97"})

(ctc/validate-all-documents application)

(facts* "maa-aineslupa to canonical"
  (let [canonical (mac/maa-aines-canonical application "fi") => truthy
        MaaAinesluvat (:MaaAinesluvat canonical) => truthy
        toimituksenTiedot (:toimituksenTiedot MaaAinesluvat) => truthy
        _ (:aineistonnimi toimituksenTiedot) => (:title application)

        maa-aineslupa-asiatieto (:maaAineslupaAsiatieto MaaAinesluvat) => truthy
        maa-aineslupa (:MaaAineslupaAsia maa-aineslupa-asiatieto)
        _ (:kasittelytietotieto maa-aineslupa) => truthy

        luvanTunnistetiedot (:luvanTunnistetiedot maa-aineslupa) => truthy
        LupaTunnus (:LupaTunnus luvanTunnistetiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        MuuTunnus (:MuuTunnus muuTunnustieto) => truthy
        _ (:tunnus MuuTunnus) => (:id application)
        _ (:sovellus MuuTunnus) => "Lupapiste"

        lausuntotieto (:lausuntotieto maa-aineslupa) => truthy
        Lausunto (:Lausunto (first lausuntotieto)) => truthy
        _ (:viranomainen Lausunto) => "Paloviranomainen"
        _ (:pyyntoPvm Lausunto) => "2013-09-17"
        lausuntotieto (:lausuntotieto Lausunto) => truthy
        annettu-lausunto (:Lausunto lausuntotieto) => truthy
        _ (:viranomainen annettu-lausunto) => "Paloviranomainen"
        _ (:lausunto annettu-lausunto) => "Lausunto liitteen\u00e4."
        _ (:lausuntoPvm annettu-lausunto) => "2013-09-17"

        hakemus (-> maa-aineslupa :hakemustieto :Hakemus) => seq]

    (fact "Canonical model has all fields"
      (util/contains-value? canonical nil?) => falsey)

    (fact "property id"
      (:kiinteistotunnus maa-aineslupa) => "63844900010004")

    (fact "kuvaus"
      (:koontiKentta maa-aineslupa) => "Hankkeen synopsis"
      (:asianKuvaus maa-aineslupa) => "Hankkeen synopsis")

    (fact "hakija"
      (first (:hakija hakemus)) => {:yTunnus "1060155-5"
                                    :yrityksenNimi "Yrtti Oy"
                                    :yhteyshenkilonNimi "Pertti Yritt\u00e4j\u00e4"
                                    :osoitetieto {:Osoite {:osoitenimi {:teksti "H\u00e4meenkatu 3 "},
                                                           :postitoimipaikannimi "kuuva",
                                                           :postinumero "43640"
                                                           :valtioSuomeksi "Suomi"
                                                           :valtioKansainvalinen "FIN"}}
                                    :puhelinnumero "060222155"
                                    :sahkopostiosoite "tew@gjr.fi"
                                    :suoramarkkinointikielto false})

    (facts "maksaja"
      (let [maksaja (get-in maa-aineslupa [:maksajatieto :Maksaja]) => truthy
            postiosoite (get-in maksaja [:osoitetieto :Osoite]) => truthy
            osoitenimi (:osoitenimi postiosoite) => truthy]
        (:teksti osoitenimi) => "Murskaajankatu 5"
        (:postinumero postiosoite) => "36570"
        (:postitoimipaikannimi postiosoite) => "Kaivanto"
        (:valtioKansainvalinen postiosoite) => "FIN"
        (:etunimi maksaja) => "Pekka"
        (:sukunimi maksaja) => "Borga"
        (:henkilotunnus maksaja) => "210281-9988"
        (:sahkopostiosoite maksaja) => "pekka.borga@porvoo.fi"
        (:puhelinnumero maksaja) => "121212"
        (:laskuviite maksaja) => nil
        (:suoramarkkinointikielto maksaja) => true))

    (facts "sijainti"
      (fact "osoite"
        (let [sijainti (-> maa-aineslupa :sijaintitieto first :Sijainti) => truthy
             osoite (:osoite sijainti)]

          (:osoitenimi osoite) => {:teksti "Londb\u00f6lentie 97"}
          (:piste sijainti) => {:Point {:pos "428195.77099609 6686701.3931274"}})))
    ))
