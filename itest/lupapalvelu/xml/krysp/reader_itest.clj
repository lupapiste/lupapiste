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
      (let [rakennus  (->rakennuksen-muuttaminen xml "001")
            huoneistot (:huoneistot rakennus)]

        rakennus => truthy

        (fact "there are 21 huoneisto" (count (keys huoneistot)) => 21)

        (fact "first huoneisto is mapped correctly"
          (:0 huoneistot) => {:huoneistoTunnus {:huoneistonumero "016"
                                                :porras "A"}
                              :huoneistonTyyppi {:huoneistoTyyppi "asuinhuoneisto"
                                                 :huoneistoala "86", :huoneluku "3"}
                              :keittionTyyppi "keittio"
                              :varusteet {:ammeTaiSuihku true
                                          :lamminvesi true
                                          :parvekeTaiTerassi true
                                          :sauna true
                                          :wc true}})

        (fact "without :huoneistot everything matches"
          (dissoc rakennus :huoneistot)
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
                  :lammitys {:lammitystapa "vesikeskus"}
                  :varusteet {:kaasuKytkin false
                              :lamminvesiKytkin true
                              :sahkoKytkin true
                              :vaestonsuoja "54"
                              :vesijohtoKytkin true
                              :viemariKytkin true
                              :hissiKytkin false
                              :koneellinenilmastointiKytkin true
                              :aurinkopaneeliKytkin false}}))))))
