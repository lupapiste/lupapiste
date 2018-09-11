(ns lupapalvelu.backing-system.krysp.building-reader-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.backing-system.krysp.building-reader :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [net.cgrand.enlive-html :as enlive]
            [sade.xml :as xml]
            [sade.common-reader :as scr]))

(facts "get-non-zero-integer"
  (fact "nil -> nil"
    (get-non-zero-integer-text [{:tag :foo :content [nil]}] :foo) => nil)
  (fact "blank -> nil"
    (get-non-zero-integer-text [{:tag :foo :content [""]}] :foo) => nil
    (get-non-zero-integer-text [{:tag :foo :content ["  "]}] :foo) => nil)
  (fact "Not a number -> nil"
    (get-non-zero-integer-text [{:tag :foo :content ["bah"]}] :foo) => nil)
  (fact "4.1 -> 4"
    (get-non-zero-integer-text [{:tag :foo :content ["4.1"]}] :foo) => "4")
  (fact "4.9 -> 4"
    (get-non-zero-integer-text [{:tag :foo :content ["4.9"]}] :foo) => "4")
  (fact "0 -> nil"
    (get-non-zero-integer-text [{:tag :foo :content ["0"]}] :foo) => nil
    (get-non-zero-integer-text [{:tag :foo :content ["0.0"]}] :foo) => nil)
  (fact "-4.9 -> -4 Not a realistic scenario"
    (get-non-zero-integer-text [{:tag :foo :content ["-4.9"]}] :foo) => "-4"))

(facts "pysyva-rakennustunnus"
  (fact (pysyva-rakennustunnus nil) => nil)
  (fact (pysyva-rakennustunnus "") => nil)
  (fact (pysyva-rakennustunnus "123456") => nil)
  (fact (pysyva-rakennustunnus "1234567892") => "1234567892")
  (fact (pysyva-rakennustunnus 12345678) => (throws AssertionError)))


(facts "Buildings exist with operation id"
  (let [xml        (xml/parse (slurp "resources/krysp/dev/buildings-with-operationIds.xml"))
        buildings  (->buildings-summary xml)
        rakennus   (first buildings)
        rakennelma (second buildings)]

    (:operationId rakennus) => "abcdefghijklmnopqr"
    (fact "using Lupapistetunnus as muuTunnus/sovellus value"
          (:operationId rakennelma) => "56c72f3cf165623f0132a28b")
    (fact "Index used as description if needed"
      (:description rakennus) => "Talo A, Toinen selite"
      (:description rakennelma) => "3")))

(facts "KRYSP yhteiset 2.1.0"
  (let [xml (xml/parse (slurp "dev-resources/krysp/building-2.1.2.xml"))
        buildings (->buildings-summary xml)
        building1-id (:buildingId (first buildings))
        building2-id (:buildingId (last buildings))
        schema       (schemas/get-schema (schemas/get-latest-schema-version) "rakennuksen-muuttaminen")]
    (fact "Meta: schema is found" schema => truthy)
    (fact "xml is parsed" buildings => truthy)
    (fact "xml has 2 buildings" (count buildings) => 2)
    (fact "Kiinteistotunnus" (:propertyId (first buildings)) => "63845900130022")
    (fact "Rakennustunnus" building1-id => "1234567892")
    (fact "Kayttotarkoitus" (:usage (first buildings)) => "011 yhden asunnon talot")
    (fact "Alkuhetki year as created" (:created (first buildings)) => "2013")
    (facts "->rakennuksentiedot"
      (facts "building 1"
        (let [building1 (dissoc (->rakennuksen-tiedot xml building1-id) :kiinttun)
              omistajat1 (:rakennuksenOmistajat building1)]

        (fact "Reader produces valid document (sans kiinttun)"
          (model/validate {:data (tools/wrapped building1)} schema) =not=> model/has-errors?)

        (facts "Owners"
          (fact "Has 2 owners" (count omistajat1) => 2)

          (let [owner1 (:0 omistajat1)
                owner2 (:1 omistajat1)]
            (get-in owner1 [:_selected]) => "henkilo"
            (get-in owner1 [:henkilo :henkilotiedot :etunimi]) => "Antero"
            (get-in owner1 [:henkilo :henkilotiedot :sukunimi]) => "Testaaja"
            (get-in owner1 [:henkilo :henkilotiedot :turvakieltoKytkin]) => true
            (get-in owner1 [:henkilo :osoite :katu]) => "Krysp-testin tie 1"
            (get-in owner1 [:henkilo :osoite :postinumero]) => "06500"
            (get-in owner1 [:henkilo :osoite :postitoimipaikannimi]) => "PORVOO"

            (get-in owner1 [:_selected]) => "henkilo"
            (get-in owner2 [:henkilo :henkilotiedot :etunimi]) => "Pauliina"
            (get-in owner2 [:henkilo :henkilotiedot :sukunimi]) => "Testaaja"
            (get-in owner2 [:henkilo :osoite :katu]) => "Krysp-testin tie 1"
            (get-in owner2 [:henkilo :osoite :postinumero]) => "06500"
            (get-in owner2 [:henkilo :osoite :postitoimipaikannimi]) => "PORVOO"
            (get-in owner2 [:henkilo :henkilotiedot :turvakieltoKytkin]) => nil))
        (fact "rakentajatyyppi" (get-in building1 [:kaytto :rakentajaTyyppi]) => "liiketaloudellinen")
        (fact "energiatehokkuusluku" (get-in building1 [:luokitus :energiatehokkuusluku]) => truthy)
        (fact "energiatehokkuusluvunYksikko" (get-in building1 [:luokitus :energiatehokkuusluvunYksikko]) => truthy)))

      (facts "building2"
        (let [building2 (dissoc (->rakennuksen-tiedot xml building2-id) :kiinttun)
              omistajat2 (:rakennuksenOmistajat building2)]

          (fact "Reader produces valid document (sans kiinttun)"
            (model/validate {:data (tools/wrapped building2)} schema) =not=> model/has-errors?)

          (facts "Owners"
            (fact "Has 2 owners" (count omistajat2) => 2)

            (let [owner1 (:0 omistajat2)
                  owner2 (:1 omistajat2)]
              (get-in owner1 [:_selected]) => "henkilo"
              (get-in owner1 [:henkilo :henkilotiedot :sukunimi]) => "Testaaja"
            (get-in owner1 [:omistajalaji]) => nil
              (get-in owner1 [:muu-omistajalaji]) => ", wut?"

              (get-in owner2 [:_selected]) => "yritys"
              (get-in owner2 [:omistajalaji]) => "yksityinen yritys (osake-, avoin- tai kommandiittiyhti\u00f6, osuuskunta)"
              (get-in owner2 [:muu-omistajalaji]) => nil
              (get-in owner2 [:yritys :yhteyshenkilo :henkilotiedot :etunimi]) => "Paavo"
              (get-in owner2 [:yritys :yhteyshenkilo :henkilotiedot :sukunimi]) => "Testaaja"
              (get-in owner2 [:yritys :yhteyshenkilo :yhteystiedot :puhelin]) => "01"
              (get-in owner2 [:yritys :yhteyshenkilo :yhteystiedot :email]) => "paavo@example.com"
              (get-in owner2 [:yritys :yritysnimi]) => "Testaajan Putki Oyj"
              (get-in owner2 [:yritys :liikeJaYhteisoTunnus]) => "123"
              (get-in owner2 [:yritys :osoite :katu]) => "Krysp-testin tie 1\u20132d\u2013e A 1"
              (get-in owner2 [:yritys :osoite :postinumero]) => "06500"
              (get-in owner2 [:yritys :osoite :postitoimipaikannimi]) => "PORVOO"))
          (fact "rakentajatyyppi" (get-in building2 [:kaytto :rakentajaTyyppi]) => "muu"))))))


(facts "KRYSP rakval 2.2.0 ->rakennuksen-tiedot"
    (let [xml      (xml/parse (slurp "dev-resources/krysp/building-2.2.0.xml"))
        building (->> xml ->buildings-summary first :buildingId (->rakennuksen-tiedot xml))]
    (fact "mitat - kerrosala" (get-in building [:mitat :kerrosala]) => "1785")
    (fact "mitat - rakennusoikeudellinenKerrosala" (get-in building [:mitat :rakennusoikeudellinenKerrosala]) => "1780")
    (fact "omistaja - yrityksen yhteyshenkilo - kytkimet" (get-in building [:rakennuksenOmistajat :0 :yritys :yhteyshenkilo :kytkimet]) => nil)))

(facts "Buildings from verdict message"
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-no-attachments.xml"))
        buildings (->buildings xml)
        building1 (first buildings)]
    (count buildings) => 1
    (:jarjestysnumero building1) => "31216"
    (:kiinttun building1) => "63820130310000"
    (:rakennusnro building1) => "123"
    (:valtakunnallinenNumero building1) => "1234567892"))

(testable-privates lupapalvelu.backing-system.krysp.application-from-krysp
                   get-lp-tunnus group-content-by)

(defn group-xml [filename]
  (->> filename xml/parse scr/strip-xml-namespaces
       (group-content-by get-lp-tunnus lupapalvelu.permit/R) ))

(facts "Buildings from review messages"
       (fact "Simple review"
             (let [simple-group (group-xml "resources/krysp/dev/r-verdict-review.xml")]
               (keys simple-group) => ["LP-186-2014-90009"]
               (->buildings-summary (get simple-group "LP-186-2014-90009"))
               => [{:description  "1",
                    :localShortId "001",
                    :buildingId   "001",
                    :index        "1",
                    :created      "2014",
                    :localId      nil,
                    :usage        "011 yhden asunnon talot",
                    :nationalId   nil,
                    :area         "129",
                    :propertyId   "18600303560006",
                    :operationId  nil
                    :location     [393033.614 6707228.994]
                    :location-wgs84 [25.0534 60.48698]}]))
       (fact "FeatureCollection review"
             (let [fc-group (group-xml "resources/krysp/dev/feature-collection.xml")]
               (keys fc-group) => (contains ["LP-020-2016-22222" "LP-020-2016-99999"] :in-any-order)
                              (->buildings-summary (get fc-group "LP-020-2016-22222"))
                              => [{:description  "001",
                                   :localShortId "002",
                                   :buildingId   "1987654324",
                                   :index        "001",
                                   :created      "2016",
                                   :localId      nil,
                                   :usage        "941 talousrakennukset",
                                   :nationalId   "1987654324",
                                   :area         "60",
                                   :propertyId   "02054321",
                                   :operationId  nil}]
                              (->buildings-summary (get fc-group "LP-020-2016-99999"))
                              => [{:description  "001",
                                   :localShortId nil,
                                   :buildingId   "1234567892",
                                   :index        "001",
                                   :created      "2016",
                                   :localId      nil,
                                   :usage        "811 navetat, sikalat, kanalat yms",
                                   :nationalId   "1234567892",
                                   :area         "2000",
                                   :propertyId   "02012345",
                                   :operationId  nil
                                   :location     [347545.336 6977611.366]
                                   :location-wgs84 [24.0 62.8965]}]))
       (fact "FeatureCollection+boundedBy review"
             (let [fc-group (group-xml "resources/krysp/dev/feature-collection-having-boundedby.xml")]
               (keys fc-group) => (contains ["LP-020-2016-22222" "LP-020-2016-99999"] :in-any-order)
               (->buildings-summary (get fc-group "LP-020-2016-22222"))
               => [{:description  "001",
                    :localShortId "002",
                    :buildingId   "1987654324",
                    :index        "001",
                    :created      "2016",
                    :localId      nil,
                    :usage        "941 talousrakennukset",
                    :nationalId   "1987654324",
                    :area         "60",
                    :propertyId   "02054321",
                    :operationId  nil}]
               (->buildings-summary (get fc-group "LP-020-2016-99999"))
               => [{:description  "001",
                    :localShortId nil,
                    :buildingId   "1234567892",
                    :index        "001",
                    :created      "2016",
                    :localId      nil,
                    :usage        "811 navetat, sikalat, kanalat yms",
                    :nationalId   "1234567892",
                    :area         "2000",
                    :propertyId   "02012345",
                    :operationId  nil
                    :location     [347545.336 6977611.366]
                    :location-wgs84 [24.0 62.8965]}]))
       (fact "FeatureCollection+boundedBy+multiple featureMembers review"
             (let [fc-group (group-xml "resources/krysp/dev/feature-collection-with-many-featureMember-elems.xml")]
               (keys fc-group) => (contains ["LP-999-2016-99999" "LP-999-2016-99349"] :in-any-order))))

(facts "Zero areas are ignored"
  (let [xml       (xml/parse "dev-resources/krysp/building-2.1.2.xml")
        buildings (->buildings-summary xml)
        mitat1    (->> buildings first :buildingId (->rakennuksen-tiedot xml) :mitat)
        mitat2    (->> buildings last  :buildingId (->rakennuksen-tiedot xml) :mitat)]
    (fact "first building areas"
      mitat1 => (contains {:tilavuus                       "627"
                           :kokonaisala                    "168"
                           :kellarinpinta-ala              "4"
                           :kerrosala                      "161"
                           :rakennusoikeudellinenKerrosala "" ;; Not in schema
                           }))
    (fact "second building areas"
      mitat2 => (contains {:tilavuus                       ""
                           :kokonaisala                    "70"
                           :kellarinpinta-ala              ""
                           :kerrosala                      ""
                           :rakennusoikeudellinenKerrosala "" ;; Not in schema
                           }))))

(facts "Strip decimals from some fields"
  (let [selector  [:rakval:valmisRakennustieto enlive/first-of-type]
        xml       (enlive/at (xml/parse "dev-resources/krysp/building-2.2.0.xml")
                             [selector :rakval:tilavuus] (enlive/content  "8992.5")
                             [selector :rakval:kokonaisala] (enlive/content  "224.9")
                             [selector :rakval:kellarinpinta-ala] (enlive/content  "88.2")
                             [selector :rakval:kerrosala] (enlive/content  "200.4")
                             [selector :rakval:rakennusoikeudellinenKerrosala] (enlive/content  "199.3"))
        buildings (->buildings-summary xml)
        mitat     (->> buildings first :buildingId (->rakennuksen-tiedot xml) :mitat)]
    mitat => (contains {:tilavuus                       "8992"
                        :kokonaisala                    "224"
                        :kellarinpinta-ala              "88"
                        :kerrosala                      "200"
                        :rakennusoikeudellinenKerrosala "199"})))
