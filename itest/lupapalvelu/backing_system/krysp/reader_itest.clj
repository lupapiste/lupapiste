(ns lupapalvelu.backing-system.krysp.reader-itest
  (:require [lupapalvelu.backing-system.krysp.building-reader :refer [building-xml ->buildings-summary
                                                                      ->rakennuksen-tiedot-by-id]]
            [lupapalvelu.backing-system.krysp.reader :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.permit :as permit]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.backing-system.krysp.reader ->standard-verdicts ->simple-verdicts)

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
        (dissoc (first buildings) :apartments) =>  {:propertyId    "75300301050006"
                                                     :buildingId    "122334455R"
                                                     :localShortId  "001"
                                                     :nationalId    "122334455R"
                                                     ;; TODO: test localId  (i.e. kunnanSisainenPysyvaRakennusnumero). Add it to building.xml or create similar xml file.
                                                     :localId       nil #_"122334455R"
                                                     :usage         "039 muut asuinkerrostalot"
                                                     :building-type "0110 omakotitalot"
                                                     :area          "2682"
                                                     :index         nil
                                                     :operationId   nil
                                                     :description   nil
                                                     :created       "1962"
                                                     :location      [395320.093 6697384.603]
                                                     :location-wgs84 [25.10015 60.39924]})

      (fact "second building has correct data"
        (second buildings) => {:propertyId     "75300301050006"
                               :buildingId     "199887766E"
                               :localShortId   "002"
                               :localId        nil #_"199887766E"        ;; TODO: test localId  (i.e. kunnanSisainenPysyvaRakennusnumero)
                               :nationalId     "199887766E"
                               :usage          "021 rivitalot"
                               :building-type  nil
                               :area           "281"
                               :index          nil
                               :operationId    nil
                               :description    nil
                               :created        "1998"
                               :location       [395403.406 6698547.3]
                               :location-wgs84 [25.10105 60.4097]
                               :apartments [{:WCKytkin "true"
                                             :ammeTaiSuihkuKytkin "true"
                                             :huoneistoTyyppi "asuinhuoneisto"
                                             :huoneistoala "108"
                                             :huoneistonumero "001"
                                             :huoneluku "4"
                                             :jakokirjain nil
                                             :keittionTyyppi "keittio"
                                             :lamminvesiKytkin "true"
                                             :muutostapa nil
                                             :parvekeTaiTerassiKytkin "true"
                                             :porras "A"
                                             :pysyvaHuoneistotunnus "1234567890"
                                             :saunaKytkin "true"}
                                             {:WCKytkin "true"
                                              :ammeTaiSuihkuKytkin "true"
                                              :huoneistoTyyppi "asuinhuoneisto"
                                              :huoneistoala "106"
                                              :huoneistonumero "002"
                                              :huoneluku "4"
                                              :jakokirjain nil
                                              :keittionTyyppi "keittio"
                                              :lamminvesiKytkin "true"
                                              :muutostapa nil
                                              :parvekeTaiTerassiKytkin "true"
                                              :porras "A"
                                              :pysyvaHuoneistotunnus nil
                                              :saunaKytkin "true"}]}))))

(fact "converting building krysp to lupapiste domain model"
  (let [xml (building-xml local-krysp nil id)]
    xml => truthy

    (fact "invalid buildingid returns nil"
      (->rakennuksen-tiedot-by-id xml "007") => nil)

    (fact "valid buildingid returns mapped document"
      (let [rakennus   (->rakennuksen-tiedot-by-id xml "001")
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
                           :rakentajaTyyppi "muu"
                           :rakennusluokka "0110 omakotitalot"
                           :tilapainenRakennusKytkin true
                           :tilapainenRakennusvoimassaPvm "10.10.2005"}
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
                              :kokoontumistilanHenkilomaara ""
                              :vesijohtoKytkin true
                              :viemariKytkin true
                              :hissiKytkin false
                              :koneellinenilmastointiKytkin true
                              :aurinkopaneeliKytkin false
                              :liitettyJatevesijarjestelmaanKytkin false
                              :saunoja ""}}))

        (fact "there are 21 huoneisto" (count (keys huoneistot)) => 21)

        (fact "the first huoneisto is mapped correctly"
          (:0 huoneistot) => {:huoneistonumero "001"
                              :porras "A"
                              :huoneistoTyyppi "asuinhuoneisto"
                              :huoneistoala "52,1"
                              :huoneluku "2"
                              :keittionTyyppi "keittokomero"
                              :ammeTaiSuihkuKytkin true
                              :lamminvesiKytkin true
                              :parvekeTaiTerassiKytkin true
                              :saunaKytkin true
                              :WCKytkin true})

        (fact "the last huoneisto is mapped correctly"
          (:20 huoneistot) => {:huoneistonumero "021"
                               :porras "A"
                               :huoneistoTyyppi "asuinhuoneisto"
                               :huoneistoala "114,5"
                               :huoneluku "4"
                               :keittionTyyppi "keittio"
                               :ammeTaiSuihkuKytkin true
                               :lamminvesiKytkin true
                               :parvekeTaiTerassiKytkin true
                               :saunaKytkin true
                               :WCKytkin true})

        (fact "there are 2 omistaja" (count (keys omistajat)) => 2)

        (fact "first omistajat is mapped correctly"
          (:0 omistajat) =>
                    {:_selected "yritys"
                     :henkilo {:henkilotiedot {:etunimi "", :hetu nil, :sukunimi "", :turvakieltoKytkin false
                                               :ulkomainenHenkilotunnus "" :not-finnish-hetu false}
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
  (let [xml (rakval-application-xml local-krysp nil [id] :application-id false)]
    xml => truthy
    (count (->verdicts xml :R permit/read-verdict-xml)) => 2))

(fact "converting rakval verdict krysp to lupapiste domain model, using kuntalupatunnus"
  (let [xml (rakval-application-xml local-krysp nil [kuntalupatunnus] :kuntalupatunnus false)]
    xml => truthy
    (count (->verdicts xml :R permit/read-verdict-xml)) => 1))

(fact "converting poikkeamis verdict krysp to lupapiste domain model"
  (let [xml (poik-application-xml local-krysp nil [id] :application-id false)]
    xml => truthy
    (count (->verdicts xml :R permit/read-verdict-xml)) => 1))


(fact "converting ya-verdict krysp to lupapiste domain model"
  (let [xml (ya-application-xml local-krysp nil [id] :application-id false)]
    xml => truthy
    (count (->verdicts xml :YA permit/read-verdict-xml)) => 1))

(facts "converting ymparisto verdicts  krysp to lupapiste domain model"
  (doseq [permit-type ["YL" "MAL" "VVVL"]]

    (let [xml (permit/fetch-xml-from-krysp permit-type local-krysp nil [id] :application-id false)
          cases (->verdicts xml permit-type permit/read-verdict-xml)]

      (fact "xml is read" xml => truthy)
      (fact "xml is parsed" cases => not-empty)
      (fact "xml has 1 cases" (count cases) => 1)
      (fact "has 1 verdicts" (-> cases last :paatokset count) => 1)
      (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => #(.startsWith % "638-2014-")))))
