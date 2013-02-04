(ns lupapalvelu.xml.krysp.reader-itest
  (:use [lupapalvelu.xml.krysp.reader]
        [midje.sweet]))

(def id "75300301050006")

(fact "two buildings can be extracted"
  (let [xml (building-xml local-test-legacy id)]
    xml => truthy

    (let [buildings (->buildings xml)]

      (fact "two buildings are found"
        buildings => truthy
       (count buildings) => 2)

      (fact "first building has correct data"
        (first buildings) => {:propertyId "75300301050006"
                              :buildingId "001"
                              :usage      "039 muut asuinkerrostalot"
                              :created    "1962"})

      (fact "second building has correct data"
        (second buildings) => {:propertyId "75300301050006"
                               :buildingId "002"
                               :usage      "021 rivitalot"
                               :created    "1998"}))))

(fact "converting krysp to lupapiste domain model"
  (let [xml (building-xml local-test-legacy id)]
    xml => truthy

    (fact "invalid buildingid returns nil"
      (->rakennuksen-muuttaminen xml "007") => nil)

    (fact "valid buildingid returns mapped document"
      (let [rakennus   (->rakennuksen-muuttaminen xml "001")
            huoneistot (:huoneistot rakennus)
            omistajat  (:rakennuksenOmistajat rakennus)]

        (fact "rakennus is not empty" rakennus => truthy)
        (fact "huoneistot is not empty" huoneistot => truthy)
        (fact "omistajat is not empty" omistajat => truthy)

        (fact "without :huoneistot and :omistajat everything matches"
          (dissoc rakennus :huoneistot :rakennuksenOmistajat)
            => (just
                 {:rakennusnro "001"
                  :verkostoliittymat {:viemariKytkin true
                                      :maakaasuKytkin false
                                      :sahkoKytkin true
                                      :vesijohtoKytkin true
                                      :kaapeliKytkin false}
                  :kaytto {:kayttotarkoitus "039 muut asuinkerrostalot"}
                  :mitat {:kerrosluku "5"
                          :kerrosala "1785"
                          :kokonaisala "2582"
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
                                       :postitoimipaikka "HELSINKI"}
                              :yritysnimi "Testiyritys 11477"}})
        ))))
