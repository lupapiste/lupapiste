(ns lupapalvelu.xml.krysp.reader-itest
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.xml.krysp.reader :refer :all]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.itest-util :refer :all]))

(testable-privates lupapalvelu.xml.krysp.reader ->verdict ->simple-verdict)

(def id "75300301050006")

(def local-krysp  (str (server-address) "/dev/krysp"))

(fact "two buildings can be extracted"
  (let [xml (building-xml local-krysp id)]
    xml => truthy

    (let [buildings (->buildings-summary xml)]

      (fact "two buildings are found"
        buildings => truthy
       (count buildings) => 2)

      (fact "first building has correct data"
        (first buildings) => {:propertyId "75300301050006"
                              :buildingId "001"
                              :usage      "039 muut asuinkerrostalot"
                              :area "2682"
                              :index nil
                              :created    "1962"})

      (fact "second building has correct data"
        (second buildings) => {:propertyId "75300301050006"
                               :buildingId "002"
                               :usage      "021 rivitalot"
                               :area "281"
                               :index nil
                               :created    "1998"}))))

(fact "converting building krysp to lupapiste domain model"
  (let [xml (building-xml local-krysp id)]
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
          (dissoc rakennus :huoneistot :rakennuksenOmistajat :kiinttun)
            => (just
                 {:rakennusnro "001"
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
                           :postitoimipaikannimi "KERAVA"}
                  :luokitus {:energialuokka "10"
                             :paloluokka "P1 / P2"}
                  :kaytto {:kayttotarkoitus "039 muut asuinkerrostalot"}
                  :mitat {:kerrosluku "5"
                          :kerrosala "1785"
                          :kokonaisala "2682"
                          :kellarinpinta-ala "100"
                          :tilavuus "8240"}
                  :rakenne {:julkisivu "betoni"
                            :kantavaRakennusaine "betoni"
                            :rakentamistapa "elementti"}
                  :lammitys {:lammitystapa "vesikeskus"
                             :lammonlahde  "kauko tai aluel\u00e4mp\u00f6"}
                  :varusteet {:kaasuKytkin false
                              :lamminvesiKytkin true
                              :sahkoKytkin true
                              :vaestonsuoja "54"
                              :vesijohtoKytkin true
                              :viemariKytkin true
                              :hissiKytkin false
                              :koneellinenilmastointiKytkin true
                              :aurinkopaneeliKytkin false}}))

        (fact "there are 21 huoneisto" (count (keys huoneistot)) => 21)

        (fact "first huoneisto is mapped correctly"
          (:0 huoneistot) => {:huoneistoTunnus {:huoneistonumero "016"
                                                :jakokirjain     "a"
                                                :porras "A"}
                              :huoneistonTyyppi {:huoneistoTyyppi "asuinhuoneisto"
                                                 :huoneistoala "86", :huoneluku "3"}
                              :keittionTyyppi "keittio"
                              :varusteet {:ammeTaiSuihkuKytkin true
                                          :lamminvesiKytkin true
                                          :parvekeTaiTerassiKytkin true
                                          :saunaKytkin true
                                          :WCKytkin true}})

        (fact "there are 2 omistaja" (count (keys omistajat)) => 2)

        (fact "first omistajat is mapped correctly"
          (:0 omistajat) =>
                    {:_selected "yritys"
                     :yritys {:liikeJaYhteisoTunnus "1234567-9"
                              :osoite {:katu "Testikatu 1 A 11477"
                                       :postinumero "00380"
                                       :postitoimipaikannimi "HELSINKI"}
                              :yritysnimi "Testiyritys 11477"}})))))

(fact "converting rakval verdict krysp to lupapiste domain model"
  (let [xml (rakval-application-xml local-krysp id false)]
    xml => truthy
    (count (->verdicts xml :RakennusvalvontaAsia ->verdict)) => 2))

(fact "converting poikkeamis verdict krysp to lupapiste domain model"
  (let [xml (poik-application-xml local-krysp id false)]
    xml => truthy
    (count (->verdicts xml :Poikkeamisasia ->verdict)) => 1))


(fact "converting ya-verdict krysp to lupapiste domain model"
  (let [xml (ya-application-xml local-krysp id false)]
    xml => truthy
    (count (->verdicts xml :yleinenAlueAsiatieto ->simple-verdict)) => 1))

(facts "converting ymparisto verdicts  krysp to lupapiste domain model"
  (doseq [permit-type ["YL" "MAL" "VVVL"]]
    (let [getter (permit/get-application-xml-getter permit-type)
          reader (permit/get-verdict-reader permit-type)
          case-elem (permit/get-case-xml-element permit-type)]

      (fact "Application XML getter is set up" getter => fn?)
      (fact "Verdict reader is set ip" reader => fn?)
      (fact "Case element is set" case-elem => keyword?)

      (let [xml (getter local-krysp id false)
            cases (->verdicts xml case-elem reader)]
        (fact "xml is parsed" cases => truthy)
        (fact "xml has 1 cases" (count cases) => 1)
        (fact "has 1 verdicts" (-> cases last :paatokset count) => 1)
        (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => #(.startsWith % "638-2014-"))))))
