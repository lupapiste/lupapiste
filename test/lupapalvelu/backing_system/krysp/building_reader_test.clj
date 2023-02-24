(ns lupapalvelu.backing-system.krysp.building-reader-test
  (:require [clojure.java.io :as io]
            [lupapalvelu.backing-system.krysp.building-reader :refer :all]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as cr]
            [sade.util :as util]
            [sade.xml :as xml]))

(testable-privates lupapalvelu.backing-system.krysp.building-reader
                   ->rakennelman-tiedot ->Rakennus)

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
  (let [xml        (xml/parse (io/input-stream "resources/krysp/dev/buildings-with-operationIds.xml"))
        buildings  (->buildings-summary xml)
        rakennus   (first buildings)
        rakennelma (second buildings)]

    (:operationId rakennus) => "abcdefghijklmnopqr"
    (fact "using Lupapistetunnus as muuTunnus/sovellus value"
          (:operationId rakennelma) => "56c72f3cf165623f0132a28b")
    (fact "Index used as description if needed"
      (:description rakennus) => "Talo A, Toinen selite"
      (:description rakennelma) => "3")

    (fact "No apartments"
      (:huoneistot rakennus) => nil
      (:huoneistot rakennelma) => nil)))

(facts "KRYSP yhteiset 2.1.0"
  (let [xml          (xml/parse (io/input-stream "dev-resources/krysp/building-2.1.2.xml"))
        buildings    (->buildings-summary xml)
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
        (let [building1  (dissoc (->rakennuksen-tiedot-by-id xml building1-id) :kiinttun)
              omistajat1 (:rakennuksenOmistajat building1)]

        (fact "Reader produces valid document (sans kiinttun)"
          (model/validate {} {:data (tools/wrapped building1)} schema) =not=> model/has-errors?)

        (facts "Owners"
          (fact "Has only one owner (the second is also an empty person)" (count omistajat1) => 1)
          (let [owner (:0 omistajat1)]

            (fact "The owner is prefilled with document schema data"
              (get-in owner [:_selected]) => "henkilo"
              (get-in owner [:henkilo :henkilotiedot :etunimi]) => ""
              (get-in owner [:henkilo :henkilotiedot :sukunimi]) => ""
              (get-in owner [:henkilo :henkilotiedot :turvakieltoKytkin]) => false
              (get-in owner [:henkilo :osoite :katu]) => ""
              (get-in owner [:henkilo :osoite :postinumero]) => ""
              (get-in owner [:henkilo :osoite :maa]) => "FIN"

              owner => contains {:omistajalaji     ""
                                 :muu-omistajalaji ""})))
        (facts "Include personal owner information"
          (let [owner (get-in (->rakennuksen-tiedot-by-id xml building1-id {:include-personal-owner-info? true})
                              [:rakennuksenOmistajat :0])]
            (get-in owner [:_selected]) => "henkilo"
            (get-in owner [:henkilo :henkilotiedot :etunimi]) => "Antero"
            (get-in owner [:henkilo :henkilotiedot :sukunimi]) => "Testaaja"
            (get-in owner [:henkilo :henkilotiedot :turvakieltoKytkin]) => true
            (get-in owner [:henkilo :osoite :katu]) => "Krysp-testin tie 1"
            (get-in owner [:henkilo :osoite :postinumero]) => "06500"
            (get-in owner [:henkilo :osoite :postitoimipaikannimi]) => "PORVOO"
            (get-in owner [:henkilo :osoite :maa]) => "FIN"
            owner => contains {:omistajalaji     ""
                               :muu-omistajalaji ""}))
        (fact "rakentajatyyppi" (get-in building1 [:kaytto :rakentajaTyyppi]) => "liiketaloudellinen")
        (fact "energiatehokkuusluku" (get-in building1 [:luokitus :energiatehokkuusluku]) => truthy)
        (fact "energiatehokkuusluvunYksikko" (get-in building1 [:luokitus :energiatehokkuusluvunYksikko]) => truthy)
        (fact "apartments"
          (:huoneistot building1)
          => {:0 {:WCKytkin true
                  :ammeTaiSuihkuKytkin true
                  :huoneistoTyyppi "asuinhuoneisto"
                  :huoneistoala "141"
                  :huoneistonumero "000"
                  :huoneluku "4"
                  :keittionTyyppi "keittio"
                  :lamminvesiKytkin true
                  :parvekeTaiTerassiKytkin false
                  :saunaKytkin true}})))

      (facts "building2"
        (let [building2  (dissoc (->rakennuksen-tiedot-by-id xml building2-id) :kiinttun)
              omistajat2 (:rakennuksenOmistajat building2)]

          (fact "No apartments"
            (:huoneistot building2) => nil)

          (fact "Reader produces valid document (sans kiinttun)"
            (model/validate {} {:data (tools/wrapped building2)} schema) =not=> model/has-errors?)

          (facts "Owners"
            (fact "Has 2 owners" (count omistajat2) => 2)

            (let [owner1 (:0 omistajat2)
                  owner2 (:1 omistajat2)]
              (get-in owner1 [:_selected]) => "henkilo"
              (get-in owner1 [:henkilo :henkilotiedot :sukunimi]) => ""
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

(fact "KRYSP ->rakennelman-tiedot"
  (let [xml (xml/parse (io/input-stream "resources/krysp/dev/buildings-with-operationIds.xml"))
        rakennelma (->rakennelman-tiedot (-> xml cr/strip-xml-namespaces (xml/select [:Rakennelma]) first))]
    (fact "rakennusnro, rakennelman-kuvaus and kokoontumistilanHenkilomaara are correct"
      rakennelma => {:rakennusnro "103" :rakennelman-kuvaus "Joku rakennelma" :kokoontumistilanHenkilomaara ""
                     :tilapainenRakennelmaKytkin false :tilapainenRakennelmavoimassaPvm nil})))


(facts "KRYSP rakval 2.2.0 ->rakennuksen-tiedot"
  (let [xml        (xml/parse (io/input-stream "dev-resources/krysp/building-2.2.0.xml"))
        building   (->> xml ->buildings-summary first :buildingId (->rakennuksen-tiedot-by-id xml))
        apartments (:huoneistot building)]
    (fact "mitat - kerrosala" (get-in building [:mitat :kerrosala]) => "1785")
    (fact "mitat - rakennusoikeudellinenKerrosala" (get-in building [:mitat :rakennusoikeudellinenKerrosala]) => "1780")
    (fact "omistaja - yrityksen yhteyshenkilo - kytkimet" (get-in building [:rakennuksenOmistajat :0 :yritys :yhteyshenkilo :kytkimet]) => nil)
    (fact "apartments"
      (count apartments) => 21
      (:huoneistot building) => (contains {:0  {:WCKytkin                true
                                                :ammeTaiSuihkuKytkin     true
                                                :huoneistoTyyppi         "asuinhuoneisto"
                                                :huoneistoala            "52,1"
                                                :huoneistonumero         "001"
                                                :huoneluku               "2"
                                                :keittionTyyppi          "keittokomero"
                                                :lamminvesiKytkin        true
                                                :parvekeTaiTerassiKytkin true
                                                :porras                  "A"
                                                :saunaKytkin             true}
                                           :10 {:WCKytkin                true
                                                :ammeTaiSuihkuKytkin     true
                                                :huoneistoTyyppi         "asuinhuoneisto"
                                                :huoneistoala            "86"
                                                :huoneistonumero         "011"
                                                :huoneluku               "3"
                                                :keittionTyyppi          "keittio"
                                                :lamminvesiKytkin        true
                                                :parvekeTaiTerassiKytkin true
                                                :porras                  "A"
                                                :saunaKytkin             true}
                                           :15 {:WCKytkin                true
                                                :ammeTaiSuihkuKytkin     true
                                                :huoneistoTyyppi         "asuinhuoneisto"
                                                :huoneistoala            "86"
                                                :huoneistonumero         "016"
                                                :huoneluku               "3"
                                                :keittionTyyppi          "keittio"
                                                :lamminvesiKytkin        true
                                                :parvekeTaiTerassiKytkin true
                                                :porras                  "A"
                                                :jakokirjain             "a"
                                                :saunaKytkin             true}
                                           :20 {:WCKytkin                true
                                                :ammeTaiSuihkuKytkin     true
                                                :huoneistoTyyppi         "asuinhuoneisto"
                                                :huoneistoala            "114,5"
                                                :huoneistonumero         "021"
                                                :huoneluku               "4"
                                                :keittionTyyppi          "keittio"
                                                :lamminvesiKytkin        true
                                                :parvekeTaiTerassiKytkin true
                                                :porras                  "A"
                                                :saunaKytkin             true}}))))

(facts "Buildings from verdict message"
  (let [xml (xml/parse (io/input-stream "dev-resources/krysp/verdict-r-no-attachments.xml"))
        buildings (->building-document-data xml)
        building1 (first buildings)]
    (count buildings) => 1
    (:jarjestysnumero building1) => "31216"
    (:kiinttun building1) => "63820130310000"
    (:rakennusnro building1) => "123"
    (:valtakunnallinenNumero building1) => "1234567892"))

(testable-privates lupapalvelu.backing-system.krysp.application-from-krysp
                   get-lp-tunnus group-content-by)

(defn group-xml [filename]
  (->> filename io/input-stream xml/parse cr/strip-xml-namespaces
       (group-content-by get-lp-tunnus lupapalvelu.permit/R nil)))

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
                    :building-type nil,
                    :nationalId   nil,
                    :area         "129",
                    :apartments   []
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
                                   :building-type nil,
                                   :nationalId   "1987654324",
                                   :area         "60",
                                   :apartments   [],
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
                                   :building-type nil,
                                   :nationalId   "1234567892",
                                   :area         "2000",
                                   :apartments   []
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
                    :building-type nil,
                    :nationalId   "1987654324",
                    :area         "60",
                    :apartments   []
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
                    :building-type nil,
                    :nationalId   "1234567892",
                    :area         "2000",
                    :apartments   []
                    :propertyId   "02012345",
                    :operationId  nil
                    :location     [347545.336 6977611.366]
                    :location-wgs84 [24.0 62.8965]}]))
       (fact "FeatureCollection+boundedBy+multiple featureMembers review"
             (let [fc-group (group-xml "resources/krysp/dev/feature-collection-with-many-featureMember-elems.xml")]
               (keys fc-group) => (contains ["LP-999-2016-99999" "LP-999-2016-99349"] :in-any-order))))
3
(facts "Zero areas are ignored"
  (let [xml       (xml/parse (io/input-stream "dev-resources/krysp/building-2.1.2.xml"))
        buildings (->buildings-summary xml)
        mitat1    (->> buildings first :buildingId (->rakennuksen-tiedot-by-id xml) :mitat)
        mitat2    (->> buildings last  :buildingId (->rakennuksen-tiedot-by-id xml) :mitat)]
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
        xml       (enlive/at (xml/parse (io/input-stream "dev-resources/krysp/building-2.2.0.xml"))
                             [selector :rakval:tilavuus] (enlive/content  "8992.5")
                             [selector :rakval:kokonaisala] (enlive/content  "224.9")
                             [selector :rakval:kellarinpinta-ala] (enlive/content  "88.2")
                             [selector :rakval:kerrosala] (enlive/content  "200.4")
                             [selector :rakval:rakennusoikeudellinenKerrosala] (enlive/content  "199.3"))
        buildings (->buildings-summary xml)
        mitat     (->> buildings first :buildingId (->rakennuksen-tiedot-by-id xml) :mitat)]
    mitat => (contains {:tilavuus                       "8992"
                        :kokonaisala                    "224"
                        :kellarinpinta-ala              "88"
                        :kerrosala                      "200"
                        :rakennusoikeudellinenKerrosala "199"})))

(defn make-node [kw-path value]
  (->> (util/split-kw-path kw-path)
       reverse
       (reduce (fn [acc tag]
                 {:tag tag
                  :content (cond-> acc
                             (not (sequential? acc)) vector)})
               value)))

(def olotila (partial make-node :olotilamuutostieto.Olotila.olotila))
(def kaytossaolo (partial make-node :kaytossaolotieto.Kaytossaolo.tilanne))
(def selector1 [[:ValmisRakennus (enlive/attr= :p4:id "valmisrakennus.1")]])
(def runeberg "2020-02-05T00:00:00Z")
(def futureberg "3030-02-05T00:00:00Z")

(defn add-loppuHetki [xml selector future? buildings-summary-checker]
  (let [xml-date (if future? futureberg runeberg)]
    (fact {:midje/description (str selector ": loppuHetki " xml-date)}
      (let [xml (enlive/at xml selector (enlive/append (make-node :loppuHetki xml-date)))]
        (->buildings-summary xml) => buildings-summary-checker))))

(facts "Demolished buidings"
  (let [xml (xml/parse (io/input-stream "dev-resources/krysp/building-2.1.2.xml"))]

    (fact "Both buildings available"
      (count (->Rakennus (cr/strip-xml-namespaces xml))) => 2
      (count (->buildings-summary xml)) => 2)

    (fact "Olotila: purettu"
      (let [xml (enlive/at xml selector1 (enlive/append (olotila "purettu")))]
        (count (enlive/select xml [:ValmisRakennus :olotilamuutostieto :Olotila :olotila])) => 1
        (->Rakennus (cr/strip-xml-namespaces xml)) => (just (contains {:tag :Rakennus}))
        (->buildings-summary xml) => (just (contains {:buildingId "002"}))))

    (fact "Olotila: valmis"
      (let [xml (enlive/at xml selector1 (enlive/append (olotila "valmis")))]
        (count (->Rakennus (cr/strip-xml-namespaces xml))) => 2
        (count (->buildings-summary xml)) => 2))

    (fact "Olotila: valmis and purettu - purettu removed"
      (let [xml (enlive/at xml selector1 (enlive/append [(olotila "valmis") (olotila "purettu")]))]
        (count (->Rakennus (cr/strip-xml-namespaces xml))) => 1
        (->buildings-summary xml) => (just (contains {:buildingId "002"}))))

    (fact "Kaytossaolo: hylätty ränsistymisen takia - nothing is removed"
      (let [xml (enlive/at xml selector1 (enlive/append (kaytossaolo "hylätty ränsistymisen takia")))]
        (count (->Rakennus (cr/strip-xml-namespaces xml))) => 2
        (count (->buildings-summary xml)) => 2))

    (fact "Kaytossalo: tyhjä, tuhoutunut, tyhjillään - nothing is removed"
      (let [xml (enlive/at xml selector1 (enlive/append [(kaytossaolo "tyhjä")
                                                         (kaytossaolo "tuhoutunut")
                                                         (kaytossaolo "tyhjillään")]))]
        (count (->Rakennus (cr/strip-xml-namespaces xml))) => 2
        (->buildings-summary xml) => (just [(contains {:buildingId "1234567892"})
                                            (contains {:buildingId "002"})])))

    (facts "loppuHetki"
      (let [ops-xml      (xml/parse (io/input-stream "resources/krysp/dev/buildings-with-operationIds.xml"))
            selector1    (conj selector1 :Rakennus)
            building1    (contains {:buildingId "1234567892"})
            building2    (contains {:buildingId "002"})
            op-structure (contains {:buildingId "103"})
            op-building  (contains {:buildingId "123456001M"})]
        (fact "Building and structure available"
          (count (->Rakennus (cr/strip-xml-namespaces ops-xml))) => 1
          (count (->buildings-summary ops-xml)) => 2)

        (fact "loppuHetki in the past"
          (add-loppuHetki xml selector1 false (just building2))
          (add-loppuHetki ops-xml [:rakval:Rakennus] false (just op-structure))
          (add-loppuHetki ops-xml [:rakval:Rakennelma] false (just op-building)))

        (fact "loppuHetki in the future"
          (add-loppuHetki xml selector1 true (just building1 building2))
          (add-loppuHetki ops-xml [:rakval:Rakennus] true (just op-structure op-building :in-any-order))
          (add-loppuHetki ops-xml [:rakval:Rakennelma] true (just op-structure op-building :in-any-order)))))))

(defn taggle [tag & v]
  (when-let [text (not-empty (apply str v))]
    (format "<%s>%s</%s>" (name tag) text (name tag))))

(defn fake-xml
  "XML snippet that is good enoug for testing purposes."
  [toimenpide & {:keys [basic no-change work]}]
  (xml/parse-string (taggle :toimenpidetieto
                            (taggle :Toimenpide
                                    (taggle toimenpide
                                            (taggle :muutostyonLaji work)
                                            (taggle :kuvaus "Ignored")
                                            (taggle :perusparannusKytkin basic)
                                            (taggle :rakennustietojaEimuutetaKytkin no-change))))
                    "utf-8"))


(facts "KuntaGML toimenpiteet"
  (fact "uusi"
    (->kuntagml-toimenpide (fake-xml :uusi)) => {:toimenpide "uusi"}
    (->kuntagml-toimenpide (fake-xml :uusi :basic true :no-change true :work "Workgin"))
    => {:toimenpide "uusi"})

  (fact "purkaminen"
    (->kuntagml-toimenpide (fake-xml :purkaminen)) => {:toimenpide "purkaminen"}
    (->kuntagml-toimenpide (fake-xml :purkaminen :basic true :no-change true :work "Workgin"))
    => {:toimenpide "purkaminen"})

  (fact "kaupunkikuvaToimenpide"
    (->kuntagml-toimenpide (fake-xml :kaupunkikuvaToimenpide))
    => {:toimenpide "kaupunkikuvaToimenpide"}
    (->kuntagml-toimenpide (fake-xml :kaupunkikuvaToimenpide
                                     :basic true :no-change true :work "Workgin"))
    => {:toimenpide "kaupunkikuvaToimenpide"})

  (fact "laajennus"
    (->kuntagml-toimenpide (fake-xml :laajennus)) => {:toimenpide "laajennus"}
    (->kuntagml-toimenpide (fake-xml :laajennus :basic false))
    => {:toimenpide          "laajennus"
        :perusparannuskytkin false}
    (->kuntagml-toimenpide (fake-xml :laajennus :basic true :no-change true :work "Working"))
    => {:toimenpide          "laajennus"
        :perusparannuskytkin true})
  (->kuntagml-toimenpide (fake-xml :laajennus :basic "bad"))
  => {:toimenpide          "laajennus"
      :perusparannuskytkin false}

  (fact "uudelleenrakentaminen"
    (->kuntagml-toimenpide (fake-xml :uudelleenrakentaminen))
    => {:toimenpide "uudelleenrakentaminen"}
    (->kuntagml-toimenpide (fake-xml :uudelleenrakentaminen :basic true :no-change true))
    => {:toimenpide          "uudelleenrakentaminen"
        :perusparannuskytkin true}
    (->kuntagml-toimenpide (fake-xml :uudelleenrakentaminen :basic false :work "Working"))
    => {:toimenpide          "uudelleenrakentaminen"
        :perusparannuskytkin false
        :muutostyolaji       "Working"}
    (->kuntagml-toimenpide (fake-xml :uudelleenrakentaminen :basic "bad" :work "Working"))
    => {:toimenpide          "uudelleenrakentaminen"
        :perusparannuskytkin false
        :muutostyolaji       "Working"}
    (->kuntagml-toimenpide (fake-xml :uudelleenrakentaminen :work "Working"))
    => {:toimenpide          "uudelleenrakentaminen"
        :muutostyolaji       "Working"}))

(fact "muuMuutosTyo"
    (->kuntagml-toimenpide (fake-xml :muuMuutosTyo))
    => {:toimenpide "muuMuutosTyo"}
    (->kuntagml-toimenpide (fake-xml :muuMuutosTyo :basic true :no-change true))
    => {:toimenpide                     "muuMuutosTyo"
        :perusparannuskytkin            true
        :rakennustietojaEimuutetaKytkin true}
    (->kuntagml-toimenpide (fake-xml :muuMuutosTyo :basic false :work "Working"))
    => {:toimenpide          "muuMuutosTyo"
        :perusparannuskytkin false
        :muutostyolaji       "Working"}
    (->kuntagml-toimenpide (fake-xml :muuMuutosTyo :basic "bad" :work "Working"))
    => {:toimenpide          "muuMuutosTyo"
        :perusparannuskytkin false
        :muutostyolaji       "Working"}
    (->kuntagml-toimenpide (fake-xml :muuMuutosTyo :work "Working"))
    => {:toimenpide    "muuMuutosTyo"
        :muutostyolaji "Working"}
    (->kuntagml-toimenpide (fake-xml :muuMuutosTyo :no-change false))
    => {:toimenpide                     "muuMuutosTyo"
        :rakennustietojaEimuutetaKytkin false}
    (->kuntagml-toimenpide (fake-xml :muuMuutosTyo :no-change "bad" :work "Working"))
    => {:toimenpide                     "muuMuutosTyo"
        :muutostyolaji                  "Working"
        :rakennustietojaEimuutetaKytkin false}
    (->kuntagml-toimenpide (fake-xml :muuMuutosTyo :no-change true :basic false :work "Working"))
    => {:toimenpide                     "muuMuutosTyo"
        :muutostyolaji                  "Working"
        :perusparannuskytkin            false
        :rakennustietojaEimuutetaKytkin true})

(fact "Unsupported/bad"
  (->kuntagml-toimenpide (fake-xml :bad)) => nil
  (->kuntagml-toimenpide (fake-xml :unsupported :basic true :no-change true)) => nil)

(defn set-rakennusluokka [xml rakennusluokka]
  (enlive/at xml
             [:rakval:rakennusluokka]
             (enlive/content rakennusluokka)))

(defn check-rakennusluokka [xml vtj-prt rakennusluokka]
  (facts {:midje/description (str vtj-prt ": " rakennusluokka)}
    (-> (->rakennuksen-tiedot-by-id xml vtj-prt)
        :kaytto :rakennusluokka) => rakennusluokka
    (->> (->buildings-summary xml)
         (util/find-by-key :nationalId vtj-prt)
         :building-type) => rakennusluokka))

(facts "rakennusluokka"
  (facts "fix-rakennusluokka"
    (fix-rakennusluokka nil) => nil
    (fix-rakennusluokka " ") => ""
    (fix-rakennusluokka " unknown ") => "unknown"
    (fix-rakennusluokka "1490") => "1490 kasvihuoneet"
    (fix-rakennusluokka " 0320 ") => "0320 hotellit"
    (fix-rakennusluokka "0611 keskussairaalat") => "0611 keskussairaalat"
    (fix-rakennusluokka " 0711 elokuvateatterit ") => "0711 elokuvateatterit")

  (let [vtj-prt "122334455R"
        xml     (-> "resources/krysp/dev/building.xml"
                    slurp (xml/parse-string "utf8"))]
    (check-rakennusluokka xml vtj-prt "0110 omakotitalot")
    (check-rakennusluokka (set-rakennusluokka xml " 0713 ")
                          vtj-prt
                          "0713 museot ja taidegalleriat")
    (check-rakennusluokka (set-rakennusluokka xml "  ") vtj-prt "")
    (check-rakennusluokka (set-rakennusluokka xml "  Doghouse ") vtj-prt "Doghouse")))

(defn owner-info [data]
  (-> data first :data :rakennuksenOmistajat :0 util/strip-blanks util/strip-empty-maps))

(defn kuntagml-toimenpide [data]
  (-> data first :kuntagml-toimenpide))

(facts "buildings and structures"
  (let [xml   (-> (slurp "dev-resources/krysp/verdict-r-foremen.xml")
                  (xml/parse-string "utf8")
                  cr/strip-xml-namespaces)
        data  (->buildings-and-structures xml)
        opted (->buildings-and-structures xml {:include-kuntagml-toimenpide? true
                                               :include-personal-owner-info? true})]
    (fact "Default (no options)"
      (owner-info data)
      => {:_selected    "henkilo"
          :henkilo      {:henkilotiedot {:not-finnish-hetu  false
                                         :turvakieltoKytkin false}
                         :kytkimet      {:suoramarkkinointilupa false}
                         :osoite        {:maa "FIN"}}
          :omistajalaji "muu yksityinen henkilö tai perikunta"
          :yritys       {:osoite        {:maa "FIN"}
                         :yhteyshenkilo {:henkilotiedot {:turvakieltoKytkin false}}}}
      (kuntagml-toimenpide data) => nil)
    (fact "Both options"
      (owner-info opted)
      => {:_selected    "henkilo"
          :henkilo      {:henkilotiedot {:etunimi           "Veijo"
                                         :not-finnish-hetu  false
                                         :sukunimi          "Viranomainen"
                                         :turvakieltoKytkin false}
                         :kytkimet      {:suoramarkkinointilupa false}
                         :osoite        {:katu                 "Metsänpojankuja 1"
                                         :maa                  "FIN"
                                         :postinumero          "12345"
                                         :postitoimipaikannimi "Espoo"}
                         :yhteystiedot  {:email   "Veijo.Viranomainen@example.com"
                                         :puhelin "0123456789"}}
          :omistajalaji "muu yksityinen henkilö tai perikunta"
          :yritys       {:osoite        {:maa "FIN"}
                         :yhteyshenkilo {:henkilotiedot {:turvakieltoKytkin false}}}}
      (kuntagml-toimenpide opted) => {:perusparannuskytkin false
                                      :toimenpide          "uudelleenrakentaminen"})))
