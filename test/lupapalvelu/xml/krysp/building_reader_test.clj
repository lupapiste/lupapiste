(ns lupapalvelu.xml.krysp.building-reader-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.xml.krysp.building-reader :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [sade.xml :as xml]
            [sade.common-reader :as scr]))

(facts "pysyva-rakennustunnus"
  (fact (pysyva-rakennustunnus nil) => nil)
  (fact (pysyva-rakennustunnus "") => nil)
  (fact (pysyva-rakennustunnus "123456") => nil)
  (fact (pysyva-rakennustunnus "1234567892") => "1234567892")
  (fact (pysyva-rakennustunnus 12345678) => (throws AssertionError)))


(facts "Buildings exist with operation id"
  (let [xml (xml/parse (slurp "resources/krysp/dev/buildings-with-operationIds.xml"))
        buildings (->buildings-summary xml)
        rakennus (first buildings)
        rakennelma (second buildings)]

    (:operationId rakennus) => "abcdefghijklmnopqr"
    (:operationId rakennelma) => "56c72f3cf165623f0132a28b"))

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
    (let [building1  (dissoc (->rakennuksen-tiedot xml building1-id) :kiinttun)
          omistajat1 (:rakennuksenOmistajat building1)]

      (fact "Reader produces valid document (sans kiinttun)"
        (model/validate {:data (tools/wrapped building1)} schema) =not=> model/has-errors?)

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

    (let [building2  (dissoc (->rakennuksen-tiedot xml building2-id) :kiinttun)
          omistajat2 (:rakennuksenOmistajat building2)]

      (fact "Reader produces valid document (sans kiinttun)"
        (model/validate {:data (tools/wrapped building2)} schema) =not=> model/has-errors?)

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
        (get-in owner2 [:yritys :osoite :katu]) => "Krysp-testin tie 1\u20132 d\u2013e A 1"
        (get-in owner2 [:yritys :osoite :postinumero]) => "06500"
        (get-in owner2 [:yritys :osoite :postitoimipaikannimi]) => "PORVOO"))))


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

(testable-privates lupapalvelu.xml.krysp.application-from-krysp
                   get-lp-tunnus group-content-by)

(defn group-xml [filename]
  (->> filename xml/parse scr/strip-xml-namespaces
       (group-content-by get-lp-tunnus lupapalvelu.permit/R) ))

(facts "Buildings from review messages"
       (fact "Simple review"
             (let [simple-group (group-xml "resources/krysp/dev/r-verdict-review.xml")]
               (keys simple-group) => ["LP-186-2014-90009"]
               (->buildings-summary (get simple-group "LP-186-2014-90009"))
               => [{:description  nil,
                    :localShortId "001",
                    :buildingId   "001",
                    :index        "1",
                    :created      "2014",
                    :localId      nil,
                    :usage        "011 yhden asunnon talot",
                    :nationalId   nil,
                    :area         "129",
                    :propertyId   "18600303560006",
                    :operationId  nil}]))
       (fact "FeatureCollection review"
             (let [fc-group (group-xml "resources/krysp/dev/feature-collection.xml")]
               (keys fc-group) => (contains ["LP-020-2016-22222" "LP-020-2016-99999"] :in-any-order)
                              (->buildings-summary (get fc-group "LP-020-2016-22222"))
                              => [{:description  nil,
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
                              => [{:description  nil,
                                   :localShortId nil,
                                   :buildingId   "1234567892",
                                   :index        "001",
                                   :created      "2016",
                                   :localId      nil,
                                   :usage        "811 navetat, sikalat, kanalat yms",
                                   :nationalId   "1234567892",
                                   :area         "2000",
                                   :propertyId   "02012345",
                                   :operationId  nil}])))
