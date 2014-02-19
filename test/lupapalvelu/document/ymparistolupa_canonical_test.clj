(ns lupapalvelu.document.ymparistolupa-canonical-test
  (:require [midje.sweet :refer :all]
            [sade.util :as util]
            [lupapalvelu.document.ymparistolupa-canonical :as ylc]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.document.canonical-test-common :refer :all]
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
          :peruste {:value "Hankkeen peruste" :modified 1392811539061}}
   })

(fact "Meta test: kuvaus" kuvaus => valid-against-current-schema?)

(def application {:id "LP-638-2014-00001"
                  :attachments []
                  :auth [{:lastName "Borga" :firstName "Pekka" :username "pekka" :type "owner" :role "owner" :id "777777777777777777000033"}]
                  :authority {:role "authority" :lastName "Borga" :firstName "Pekka" :username "pekka" :id "777777777777777777000033"}
                  :address "Londb\u00f6lentie 97"
                  :created 1391415025497
                  :documents [kuvaus
                              henkilohakija
                              yrityshakija]
                  :drawings []
                  :infoRequest false
                  :location {:x 428195.77099609 :y 6686701.3931274}
                  :neighbors {}
                  :modified 1391415696674
                  :municipality "638"
                  :operations [{:id "abba1" :name "yl-uusi-toiminta" :created 1391415025497}]
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
                  :statements statements
                  :submitted 1391415717396
                  :title "Londb\u00f6lentie 97"})

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
        ]

    (fact "Canonical model has all fields"
      (util/contains-value? canonical nil?) => falsey)

    (count hakijat) => 2

    (let [hakija (first hakijat)
          postiosoite (:postiosoite hakija) => truthy
          osoitenimi (:osoitenimi postiosoite) => truthy
          yhteyshenkilo (:yhteyshenkilo hakija) => truthy]

      (:nimi hakija) => "Yksityishenkil\u00f6"
      (:liikeJaYhteisotunnus hakija) => nil
      (:teksti osoitenimi) => "Murskaajankatu 5"
      (:postinumero postiosoite) => "36570"
      (:postitoimipaikannimi postiosoite) => "Kaivanto"
      (:nimi yhteyshenkilo) => {:etunimi "Pekka" :sukunimi "Borga"}
      (:sahkopostiosoite yhteyshenkilo) => "pekka.borga@porvoo.fi"
      (:puhelin yhteyshenkilo) => "121212")

    (second hakijat) => {:nimi "Yrtti Oy",
                         :postiosoite {:osoitenimi {:teksti "H\u00e4meenkatu 3 "},
                                       :postitoimipaikannimi "kuuva",
                                       :postinumero "43640"},
                         :yhteyshenkilo {:nimi {:sukunimi "Yritt\u00e4j\u00e4", :etunimi "Pertti"},
                                         :puhelin "060222155",
                                         :sahkopostiosoite "tew@gjr.fi"},
                         :liikeJaYhteisotunnus "1060155-5"}

    (get-in ymparistolupa [:toiminta :kuvaus]) => "Hankkeen kuvauskentan sisalto"
    (get-in ymparistolupa [:toiminta :peruste]) => "Hankkeen peruste"

    ; (clojure.pprint/pprint canonical)
))