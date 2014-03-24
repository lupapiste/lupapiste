(ns lupapalvelu.document.maa-aines-canonical-test
  (:require [midje.sweet :refer :all]
            [sade.util :as util]
            [lupapalvelu.document.maa-aines-canonical :as mac]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.document.canonical-test-common :refer :all]
            [lupapalvelu.document.ymparisto-schemas]))

(def schema-version 1)

(def maksaja (assoc henkilohakija :schema-info {:name "maksaja" :type "party" :version schema-version}))

(fact "Meta test: maksaja" maksaja => valid-against-current-schema?)

(def maa-aineslupa-kuvaus {:schema-info {:name "maa-aineslupa-kuvaus" :version schema-version}
                           :data {:kuvaus {:value "Hankkeen synopsis"}}})

(fact "Meta test: maa-aineslupa-kuvaus" maa-aineslupa-kuvaus => valid-against-current-schema?)

(def application {:id "LP-638-2014-00001"
                  :attachments []
                  :auth [{:lastName "Borga" :firstName "Pekka" :username "pekka" :type "owner" :role "owner" :id "777777777777777777000033"}]
                  :authority {:role "authority" :lastName "Borga" :firstName "Pekka" :username "pekka" :id "777777777777777777000033"}
                  :address "Londb\u00f6lentie 97"
                  :created 1391415025497
                  :documents [maa-aineslupa-kuvaus
                              yrityshakija
                              maksaja]
                  :drawings drawings
                  :infoRequest false
                  :location {:x 428195.77099609 :y 6686701.3931274}
                  :neighbors {}
                  :modified 1391415696674
                  :municipality "638"
                  :operations [{:id "abba1" :name "maa-aineslupa" :created 1391415025497}]
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
                  :statements statements
                  :submitted 1391415717396
                  :title "Londb\u00f6lentie 97"})

(facts* "maa-aineslupa to canonical"
  (let [canonical (mac/maa-aines-canonical application "fi") => truthy
        MaaAinesluvat (:MaaAinesluvat canonical) => truthy
        toimituksenTiedot (:toimituksenTiedot MaaAinesluvat) => truthy
        aineistonnimi (:aineistonnimi toimituksenTiedot) => (:title application)

        maa-aineslupa-asiatieto (:maaAineslupaAsiatieto MaaAinesluvat) => truthy
        maa-aineslupa (:MaaAineslupaAsia maa-aineslupa-asiatieto)
        kasittelytietotieto (:kasittelytietotieto maa-aineslupa) => truthy

        luvanTunnistetiedot (:luvanTunnistetiedot maa-aineslupa) => truthy
        LupaTunnus (:LupaTunnus luvanTunnistetiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        MuuTunnus (:MuuTunnus muuTunnustieto) => truthy
        tunnus (:tunnus MuuTunnus) => (:id application)
        sovellus (:sovellus MuuTunnus) => "Lupapiste"

        lausuntotieto (:lausuntotieto maa-aineslupa) => truthy
        Lausunto (:Lausunto (first lausuntotieto)) => truthy
        viranomainen (:viranomainen Lausunto) => "Paloviranomainen"
        pyyntoPvm (:pyyntoPvm Lausunto) => "2013-09-17"
        lausuntotieto (:lausuntotieto Lausunto) => truthy
        annettu-lausunto (:Lausunto lausuntotieto) => truthy
        lausunnon-antanut-viranomainen (:viranomainen annettu-lausunto) => "Paloviranomainen"
        varsinainen-lausunto (:lausunto annettu-lausunto) => "Lausunto liitteen\u00e4."
        lausuntoPvm (:lausuntoPvm annettu-lausunto) => "2013-09-17"

        hakemus (-> maa-aineslupa :hakemustieto :Hakemus) => seq
        ]
    ;(clojure.pprint/pprint hakemus)

    (fact "Canonical model has all fields"
      (util/contains-value? canonical nil?) => falsey)

    (fact "property id"
      (:kiinteistotunnus maa-aineslupa) => "63844900010004")

    (fact "kuvaus"
      (:koontiKentta maa-aineslupa) => "Hankkeen synopsis")

    (fact "hakija"
      (first (:hakija hakemus))
      =>
      {:yTunnus "1060155-5"
       :yrityksenNimi "Yrtti Oy"
       :yhteyshenkilonNimi "Pertti Yritt\u00e4j\u00e4"
       :osoitetieto {:Osoite {:osoitenimi {:teksti "H\u00e4meenkatu 3 "},
                              :postitoimipaikannimi "kuuva",
                              :postinumero "43640"}}
       :puhelinnumero "060222155"
       :sahkopostiosoite "tew@gjr.fi"})

    (fact "maksaja"
      (:viranomaismaksujenSuorittaja hakemus)
      =>
      {:nimi {:sukunimi "Borga", :etunimi "Pekka"},
       :puhelin "121212",
       :sahkopostiosoite "pekka.borga@porvoo.fi"
       :osoite {:osoitenimi {:teksti "Murskaajankatu 5"},
                                      :postitoimipaikannimi "Kaivanto",
                                      :postinumero "36570"}
       :henkilotunnus "210281-9988"})

    (facts "sijainti"
      (fact "alueenKiinteistonSijainti"
        (let [sijainti (-> hakemus :alueenKiinteistonSijainti :Sijainti) => truthy
              osoite (:osoite sijainti)]

          (:osoitenimi osoite) => {:teksti "Londb\u00f6lentie 97"}
          (:piste sijainti) => {:Point {:pos "428195.77099609 6686701.3931274"}}))

      (fact "osoite"
        (let [sijainti (-> maa-aineslupa :sijaintitieto :Sijainti) => truthy
             osoite (:osoite sijainti)]

          (:osoitenimi osoite) => {:teksti "Londb\u00f6lentie 97"}
          (:piste sijainti) => {:Point {:pos "428195.77099609 6686701.3931274"}})))

    ))
