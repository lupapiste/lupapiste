(ns lupapalvelu.xml.krysp.reader-itest
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.xml.krysp.reader :refer :all]
            [lupapalvelu.xml.krysp.building-reader :refer [building-xml ->buildings-summary ->rakennuksen-tiedot]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.itest-util :refer :all]))

(testable-privates lupapalvelu.xml.krysp.reader ->standard-verdicts ->simple-verdicts)

(def id "75300301050006")
(def kuntalupatunnus "14-0241-R 3")

(def local-krysp  (str (server-address) "/dev/krysp"))

(fact "two buildings can be extracted"
  (let [xml (building-xml local-krysp nil id)]
    xml => truthy

    (let [buildings (->buildings-summary xml)]

      (fact "two buildings are found"
        buildings => truthy
       (count buildings) => 2)

      (fact "first building has correct data"
        (first buildings) => {:propertyId  "75300301050006"
                              :buildingId  "122334455R"
                              :localShortId "001"
                              :nationalId  "122334455R"
                              ;; TODO: test localId  (i.e. kunnanSisainenPysyvaRakennusnumero). Add it to building.xml or create similar xml file.
                              :localId     nil #_"122334455R"
                              :usage       "039 muut asuinkerrostalot"
                              :area        "2682"
                              :index       nil
                              :operationId nil
                              :description nil
                              :created     "1962"})

      (fact "second building has correct data"
        (second buildings) => {:propertyId   "75300301050006"
                               :buildingId   "199887766E"
                               :localShortId "002"
                               :localId      nil #_"199887766E"        ;; TODO: test localId  (i.e. kunnanSisainenPysyvaRakennusnumero)
                               :nationalId   "199887766E"
                               :usage        "021 rivitalot"
                               :area         "281"
                               :index        nil
                               :operationId  nil
                               :description  nil
                               :created     "1998"}))))

(fact "converting building krysp to lupapiste domain model"
  (let [xml (building-xml local-krysp nil id)]
    xml => truthy

    (fact "invalid buildingid returns nil"
      (->rakennuksen-tiedot xml "007") => nil)

    (fact "valid buildingid returns mapped document"
      (let [rakennus   (->rakennuksen-tiedot xml "001")
            huoneistot (:huoneistot rakennus)
            omistajat  (:rakennuksenOmistajat rakennus)]

        (fact "rakennus is not empty" rakennus => truthy)
        (fact "huoneistot is not empty" huoneistot => truthy)
        (fact "omistajat is not empty" omistajat => truthy)

        (fact "without :huoneistot, :omistajat and :kiinttun everything matches"
          (dissoc rakennus :huoneistot :rakennuksenOmistajat :kiinttun :kunnanSisainenPysyvaRakennusnumero)      ;; TODO: test also "kunnanSisainenPysyvaRakennusnumero" (and remove it from here)
            => (just
                 {:rakennusnro "001"
                  :valtakunnallinenNumero "122334455R"
;                  :kunnanSisainenPysyvaRakennusnumero "481123124R"
                  :manuaalinen_rakennusnro ""
                  :verkostoliittymat {:viemariKytkin true
                                      :maakaasuKytkin false
                                      :sahkoKytkin true
                                      :vesijohtoKytkin true
                                      :kaapeliKytkin false}
                  :osoite {:kunta "245"
                           :lahiosoite "Vehkalantie"
                           :osoitenumero "2"
                           :osoitenumero2 "3"
                           :jakokirjain "a"
                           :jakokirjain2 "b"
                           :porras "G"
                           :huoneisto "99"
                           :postinumero "04200"
                           :postitoimipaikannimi "KERAVA"
                           :maa "FIN"}
                  :luokitus {:energialuokka "10"
                             :paloluokka "P1 / P2"
                             :energiatehokkuusluku ""
                             :energiatehokkuusluvunYksikko "kWh/m2"}
                  :kaytto {:kayttotarkoitus "039 muut asuinkerrostalot"
                           :rakentajaTyyppi nil}
                  :mitat {:kerrosluku "5"
                          :kerrosala "1785"
                          :rakennusoikeudellinenKerrosala ""
                          :kokonaisala "2682"
                          :kellarinpinta-ala "100"
                          :tilavuus "8240"}
                  :rakenne {:julkisivu "betoni"
                            :kantavaRakennusaine "betoni"
                            :rakentamistapa "elementti"
                            :muuMateriaali ""
                            :muuRakennusaine ""}
                  :lammitys {:lammitystapa "vesikeskus"
                             :lammonlahde  "kauko tai aluel\u00e4mp\u00f6"
                             :muu-lammonlahde ""}
                  :varusteet {:kaasuKytkin false
                              :lamminvesiKytkin true
                              :sahkoKytkin true
                              :vaestonsuoja "54"
                              :vesijohtoKytkin true
                              :viemariKytkin true
                              :hissiKytkin false
                              :koneellinenilmastointiKytkin true
                              :aurinkopaneeliKytkin false
                              :liitettyJatevesijarjestelmaanKytkin false
                              :saunoja ""}}))

        (fact "there are 21 huoneisto" (count (keys huoneistot)) => 21)

        (fact "first huoneisto is mapped correctly"
          (:0 huoneistot) => {:huoneistonumero "001"
                              :porras "A"
                              :jakokirjain ""
                              :huoneistoTyyppi "asuinhuoneisto"
                              :huoneistoala "52,1", :huoneluku "2"
                              :keittionTyyppi "keittokomero"
                              :ammeTaiSuihkuKytkin true
                              :lamminvesiKytkin true
                              :parvekeTaiTerassiKytkin true
                              :saunaKytkin true
                              :WCKytkin true})

        (fact "there are 2 omistaja" (count (keys omistajat)) => 2)

        (fact "first omistajat is mapped correctly"
          (:0 omistajat) =>
                    {:_selected "yritys"
                     :henkilo {:henkilotiedot {:etunimi "", :hetu nil, :sukunimi "", :turvakieltoKytkin false}
                               :osoite {:katu "", :postinumero "", :postitoimipaikannimi "" :maa "FIN"}
                               :userId nil
                               :yhteystiedot {:email "", :puhelin ""}
                               :kytkimet {:suoramarkkinointilupa false}}
                     :muu-omistajalaji "", :omistajalaji nil
                     :yritys {:companyId nil
                              :liikeJaYhteisoTunnus "1234567-1"
                              :osoite {:katu "Testikatu 1 A 11477"
                                       :postinumero "00380"
                                       :postitoimipaikannimi "HELSINKI"
                                       :maa "FIN"}
                              :yritysnimi "Testiyritys 11477"
                              :yhteyshenkilo {:henkilotiedot {:etunimi "", :sukunimi "",
                                                              :turvakieltoKytkin false}
                                              :yhteystiedot {:email "", :puhelin ""}}}})))))

(fact "converting rakval verdict krysp to lupapiste domain model, using lupapistetunnus"
  (let [xml (rakval-application-xml local-krysp nil id :application-id false)]
    xml => truthy
    (count (->verdicts xml ->standard-verdicts)) => 2))

(fact "converting rakval verdict krysp to lupapiste domain model, using kuntalupatunnus"
  (let [xml (rakval-application-xml local-krysp nil kuntalupatunnus :kuntalupatunnus false)]
    xml => truthy
    (count (->verdicts xml ->standard-verdicts)) => 1))

(fact "converting poikkeamis verdict krysp to lupapiste domain model"
  (let [xml (poik-application-xml local-krysp nil id :application-id false)]
    xml => truthy
    (count (->verdicts xml ->standard-verdicts)) => 1))


(fact "converting ya-verdict krysp to lupapiste domain model"
  (let [xml (ya-application-xml local-krysp nil id :application-id false)]
    xml => truthy
    (count (->verdicts xml ->simple-verdicts)) => 1))

(facts "converting ymparisto verdicts  krysp to lupapiste domain model"
  (doseq [permit-type ["YL" "MAL" "VVVL"]]
    (let [reader (permit/get-verdict-reader permit-type)]

      (fact "Verdict reader is set ip" reader => fn?)

      (let [xml (permit/fetch-xml-from-krysp permit-type local-krysp nil id :application-id false) => truthy
            cases (->verdicts xml reader)]
        (fact "xml is parsed" cases => truthy)
        (fact "xml has 1 cases" (count cases) => 1)
        (fact "has 1 verdicts" (-> cases last :paatokset count) => 1)
        (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => #(.startsWith % "638-2014-"))))))
