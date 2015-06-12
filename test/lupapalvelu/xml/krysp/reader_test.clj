(ns lupapalvelu.xml.krysp.reader-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [clj-time.coerce :as coerce]
            [sade.xml :as xml]
            [lupapalvelu.xml.krysp.reader :refer [property-equals ->verdicts ->buildings-summary ->rakennuksen-tiedot ->buildings  wfs-krysp-url rakval-case-type get-app-info-from-message]]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]))

(defn- to-timestamp [yyyy-mm-dd]
  (coerce/to-long (coerce/from-string yyyy-mm-dd)))

(testable-privates lupapalvelu.xml.krysp.reader ->standard-verdicts standard-verdicts-validator simple-verdicts-validator ->simple-verdicts pysyva-rakennustunnus)

(fact "property-equals returns url-encoded data"
  (property-equals "_a_" "_b_") => "%3CPropertyIsEqualTo%3E%3CPropertyName%3E_a_%3C%2FPropertyName%3E%3CLiteral%3E_b_%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")

(fact "property-equals returns url-encoded xml-encoded data"
  (property-equals "<a>" "<b>") => "%3CPropertyIsEqualTo%3E%3CPropertyName%3E%26lt%3Ba%26gt%3B%3C%2FPropertyName%3E%3CLiteral%3E%26lt%3Bb%26gt%3B%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")

(defn verdict-skeleton [poytakirja-xml]
  {:tag :paatostieto
   :content [{:tag :paatostieto
              :content [{:tag :Paatos
                         :content [{:tag :poytakirja :content poytakirja-xml}]}]}]})

(def future-verdict (verdict-skeleton [{:tag :paatoskoodi :content ["hyv\u00e4ksytty"]}
                                       {:tag :paatoksentekija :content [""]}
                                       {:tag :paatospvm :content ["1970-01-02"]}]))

(def past-verdict (verdict-skeleton [{:tag :paatoskoodi :content ["hyv\u00e4ksytty"]}
                                     {:tag :paatoksentekija :content [""]}
                                     {:tag :paatospvm :content ["1970-01-01"]}]))

(facts "standard-verdicts-validator"
  (against-background
    (sade.util/get-timestamp-from-now :day 1) => (+ (to-timestamp "1970-01-01") 100))
  (fact "Missing details"
    (standard-verdicts-validator (verdict-skeleton [])) => {:ok false, :text "info.paatos-details-missing"})
  (fact "Future date"
    (standard-verdicts-validator future-verdict) => {:ok false, :text "info.paatos-future-date"} )
  (fact "Past date"
    (standard-verdicts-validator past-verdict) => nil))

(defn simple-verdicts-skeleton [state verdict-date]
  {:tag :Kayttolupa
   :content [{:tag :kasittelytietotieto
              :content [{:tag :Kasittelytieto
                         :content [{:tag :muutosHetki    :content ["2014-01-29T13:57:15"]}
                                   {:tag :hakemuksenTila :content [state]}]}]}
             {:tag :paatostieto :content [{:tag :Paatos
                                           :content [{:tag :paatosdokumentinPvm :content [verdict-date]}]}]}]})

(facts "simple-verdicts-skeleton"
  (against-background
    (sade.core/now) => 100)
  (fact "Empty state"
    (simple-verdicts-validator (simple-verdicts-skeleton "" "")) => {:ok false, :text "info.application-backend-preverdict-state"})
  (fact "Still in application state"
    (simple-verdicts-validator (simple-verdicts-skeleton "hakemus" "1970-01-02")) => {:ok false, :text "info.application-backend-preverdict-state"})
  (fact "Nil date"
    (simple-verdicts-validator (simple-verdicts-skeleton nil nil)) => {:ok false, :text "info.paatos-date-missing"})
  (fact "Future date"
    (simple-verdicts-validator (simple-verdicts-skeleton "OK" "1970-01-02")) => {:ok false, :text "info.paatos-future-date"} )
  (fact "Past date"
    (simple-verdicts-validator (simple-verdicts-skeleton "OK" "1970-01-01")) => nil))

(facts "pysyva-rakennustunnus"
  (fact (pysyva-rakennustunnus nil) => nil)
  (fact (pysyva-rakennustunnus "") => nil)
  (fact (pysyva-rakennustunnus "123456") => nil)
  (fact (pysyva-rakennustunnus "1234567892") => "1234567892"))

(facts "KRYSP verdict 2.1.8"
  (let [xml (xml/parse (slurp "resources/krysp/sample/verdict - 2.1.8.xml"))
        cases (->verdicts xml ->standard-verdicts)]

    (fact "xml is parsed" cases => truthy)
    (fact "validator finds verdicts" (standard-verdicts-validator xml) => nil)

    (let [verdict (first (:paatokset (last cases)))
          lupamaaraykset (:lupamaaraykset verdict)
          maaraykset     (:maaraykset lupamaaraykset)]
      (facts "m\u00e4\u00e4r\u00e4ykset"
            (count maaraykset) => 2
            (:sisalto (first maaraykset)) => "Radontekninen suunnitelma"
            (:maaraysaika (first maaraykset)) => (to-timestamp "2013-08-28")
            (:toteutusHetki (last maaraykset)) => (to-timestamp "2013-08-31")))))

(facts "KRYSP verdict"
  (let [xml (xml/parse (slurp "resources/krysp/sample/verdict.xml"))
        cases (->verdicts xml ->standard-verdicts)]

    (fact "xml is parsed" cases => truthy)

    (fact "validator finds verdicts" (standard-verdicts-validator xml) => nil)

    (fact "xml has 2 cases" (count cases) => 2)
    (fact "second case has 2 verdicts" (-> cases last :paatokset count) => 2)

    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "13-0185-R")

    (let [verdict (first (:paatokset (last cases)))
          lupamaaraykset (:lupamaaraykset verdict)
          paivamaarat    (:paivamaarat verdict)
          poytakirjat    (:poytakirjat verdict)]

      (facts "lupamaaraukset data is correct"
        lupamaaraykset => truthy
        (:autopaikkojaEnintaan lupamaaraykset) => 10
        (:autopaikkojaVahintaan lupamaaraykset) => 1
        (:autopaikkojaRakennettava lupamaaraykset) => 2
        (:autopaikkojaRakennettu lupamaaraykset) => 0
        (:autopaikkojaKiinteistolla lupamaaraykset) => 7
        (:autopaikkojaUlkopuolella lupamaaraykset) => 3
        (:kerrosala lupamaaraykset) => "100"
        (:kokonaisala lupamaaraykset) => "110"
        (:vaaditutTyonjohtajat lupamaaraykset) => "Vastaava ty\u00f6njohtaja, Vastaava IV-ty\u00f6njohtaja, Ty\u00f6njohtaja"
        (let [katselmukset (:vaaditutKatselmukset lupamaaraykset)
              maaraykset   (:maaraykset lupamaaraykset)]
          (facts "katselmukset"
            (count katselmukset) => 2
            (:katselmuksenLaji (first katselmukset)) => "aloituskokous"
            (:tarkastuksenTaiKatselmuksenNimi (last katselmukset)) => "K\u00e4ytt\u00f6\u00f6nottotarkastus")
          (facts "m\u00e4\u00e4r\u00e4ykset"
            (count maaraykset) => 2
            (:sisalto (first maaraykset)) => "Radontekninen suunnitelma"
            (:maaraysaika (first maaraykset)) => (to-timestamp "2013-08-28")
            (:toteutusHetki (last maaraykset)) => (to-timestamp "2013-08-31")))

        (facts "second verdict"
          (let [katselmukset2 (-> cases last :paatokset last :lupamaaraykset :vaaditutKatselmukset)]
            (count katselmukset2) => 1
            katselmukset2 => sequential?)
          (let [maaraykset2 (-> cases last :paatokset last :lupamaaraykset :maaraykset)]
            (count maaraykset2) => 1
            maaraykset2 => sequential?))
        )

      (facts "paivamaarat data is correct"
        paivamaarat    => truthy
        (:aloitettava paivamaarat) => (to-timestamp "2013-09-01")
        (:lainvoimainen paivamaarat) => (to-timestamp "2013-09-02")
        (:voimassaHetki paivamaarat) => (to-timestamp "2013-09-03")
        (:raukeamis paivamaarat) => (to-timestamp "2013-09-04")
        (:anto paivamaarat) => (to-timestamp "2013-09-05")
        (:viimeinenValitus paivamaarat) => (to-timestamp "2013-09-06")
        (:julkipano paivamaarat) => (to-timestamp "2013-09-07"))

      (facts "p\u00f6yt\u00e4kirjat data is correct"
        poytakirjat    => truthy
        (count poytakirjat) => 2

        (let [pk1   (first poytakirjat)
              liite (:liite pk1)]
          (:paatos pk1) => "P\u00e4\u00e4t\u00f6s 1"
          (:paatoskoodi pk1) => "my\u00f6nnetty"
          (:paatoksentekija pk1) => "viranomainen"
          (:paatospvm pk1) => (to-timestamp "2013-08-01")
          (:pykala pk1) => 1
          (:kuvaus liite) => "kuvaus 1"
          (:linkkiliitteeseen liite) => "http://localhost:8000/img/under-construction.gif"
          (:muokkausHetki liite) => (to-timestamp "2013-09-01T12:00:00")
          (:versionumero liite) => "1"
          (get-in liite [:tekija :henkilo :nimi :sukunimi]) => "Tarkkanen"
          (:tyyppi liite) => "tyyppi 1"
          (:metadata liite) => {:nimi "arvo"})

        (facts "second verdict"
          (let [poytakirjat2 (-> cases last :paatokset last :poytakirjat)]
            (count poytakirjat2) => 1
            poytakirjat2 => sequential?))))))

(facts "CGI sample verdict"
  (let [xml (xml/parse (slurp "dev-resources/krysp/cgi-verdict.xml"))
        cases (->verdicts xml ->standard-verdicts)]
    (fact "xml is parsed" cases => truthy)
    (fact "xml has 1 case" (count cases) => 1)
    (fact "case has 1 verdict" (-> cases last :paatokset count) => 1)

    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "13-0185-R")

    (let [verdict         (-> cases first :paatokset first)
          lupamaaraykset (:lupamaaraykset verdict)
          paivamaarat    (:paivamaarat verdict)
          poytakirjat    (:poytakirjat verdict)
          katselmukset   (:vaaditutKatselmukset lupamaaraykset)]

      (fact "paatos" verdict => truthy)
      (fact "lupamaaraykset is parsed" lupamaaraykset => truthy)

      (facts "katselmukset"
        (count katselmukset) => 1
        (:katselmuksenLaji (first katselmukset)) => "loppukatselmus")

      (facts "paivamaarat data is correct"
        paivamaarat    => truthy
        (:aloitettava paivamaarat) => pos?
        (:aloitettava paivamaarat) => (to-timestamp "2016-10-08")
        (:lainvoimainen paivamaarat) => (to-timestamp "2013-10-08")
        (:voimassaHetki paivamaarat) => (to-timestamp "2018-10-08")
        (:raukeamis paivamaarat) => nil
        (:anto paivamaarat) => (to-timestamp "2013-09-06")
        (:viimeinenValitus paivamaarat) => (to-timestamp "2013-10-07")
        (:julkipano paivamaarat) => (to-timestamp "2013-09-04"))


      (facts "p\u00f6yt\u00e4kirjat data is correct"
        poytakirjat    => truthy
        (count poytakirjat) => 1

        (let [pk1   (first poytakirjat)
              liite (:liite pk1)]
          (:paatos pk1) => nil
          (:paatoskoodi pk1) => "my\u00f6nnetty"
          (:paatoksentekija pk1) => "Rakennuslautakunta"
          (:paatospvm pk1) => (to-timestamp "2013-09-03")
          (:pykala pk1) => 12
          (:kuvaus liite) => "P\u00e4\u00e4t\u00f6sote"
          (:linkkiliitteeseen liite) => "http://212.213.116.162:80/186/arkisto/2013/PAATOSOTE_13-0185-R_20130903152736270.rtf"
          (:muokkausHetki liite) => (to-timestamp "2013-09-03T15:27:46")
          (:tyyppi liite) => "P\u00e4\u00e4t\u00f6sote")))))

(facts "Tekla sample verdict"
  (let [xml (xml/parse (slurp "dev-resources/krysp/teklap.xml"))
        cases (->verdicts xml ->standard-verdicts)]

    (fact "xml is parsed" cases => truthy)

    (fact "validator finds verdicts" (standard-verdicts-validator xml) => nil)

    (fact "xml has one case" (count cases) => 1)
    (fact "case has 1 verdict" (-> cases last :paatokset count) => 1)

    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "109-2014-140")

    (let [verdict        (-> cases first :paatokset first)
          lupamaaraykset (:lupamaaraykset verdict)
          paivamaarat    (:paivamaarat verdict)
          poytakirjat    (:poytakirjat verdict)
          katselmukset   (:vaaditutKatselmukset lupamaaraykset)
          maaraykset     (:maaraykset lupamaaraykset)]

      (facts "lupamaaraukset data is correct"
        lupamaaraykset => truthy
        (:kerrosala lupamaaraykset) => "134.000"
        (:kokonaisala lupamaaraykset) => "134.000"
        (:vaaditutTyonjohtajat lupamaaraykset) => "Vastaava ty\u00f6njohtaja, KVV-ty\u00f6njohtaja, IV-ty\u00f6njohtaja"
        (facts "katselmukset"
          (count katselmukset) => 4
          (:katselmuksenLaji (first katselmukset)) => "aloituskokous"
          (:katselmuksenLaji (last katselmukset)) => "loppukatselmus")
        (facts "m\u00e4\u00e4r\u00e4ykset"
          (count maaraykset) => 3
          (:sisalto (first maaraykset)) => "Vastaavan ty\u00f6njohtajan on yll\u00e4pidett\u00e4v\u00e4 rakennusty\u00f6n tarkastusasiakirjaa, johon eri rakennusvaiheiden vastuuhenkil\u00f6t ja ty\u00f6vaiheiden tarkastuksia suorittavat henkil\u00f6t varmentavat tekem\u00e4ns\u00e4 tarkastukset (MRL 150 \u00a7 3. mom., MRA 77 \u00a7)."))
      )))

(facts "case not found"
  (let [xml (xml/parse (slurp "dev-resources/krysp/notfound.xml"))
        cases (->verdicts xml ->standard-verdicts)]
    (fact "xml is parsed" cases => truthy)
    (fact "xml has no cases" (count cases) => 0)
    (fact "validator does not find verdicts" (standard-verdicts-validator xml) => {:ok false, :text "info.no-verdicts-found-from-backend"})))

(facts "nil xml"
  (let [cases (->verdicts nil ->standard-verdicts)]
    (seq cases) => nil
    (count cases) => 0))

(facts "no verdicts"
  (let [xml (xml/parse (slurp "dev-resources/krysp/no-verdicts.xml"))
        cases (->verdicts xml ->standard-verdicts)]
    (fact "xml is parsed" cases => truthy)
    (fact "xml has 1 case" (count cases) => 1)
    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "13-0185-R")
    (fact "validator does not find verdicts" (standard-verdicts-validator xml) => {:ok false, :text "info.no-verdicts-found-from-backend"})
    (fact "case has no verdicts" (-> cases last :paatokset count) => 0)))

(facts "KRYSP yhteiset 2.1.0"
  (let [xml (xml/parse (slurp "resources/krysp/sample/sito-porvoo-building.xml"))
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
        (get-in owner1 [:henkilo :henkilotiedot :sukunimi]) => "Pekkala"
        (get-in owner1 [:henkilo :henkilotiedot :turvakieltoKytkin]) => true
        (get-in owner1 [:henkilo :osoite :katu]) => "Uuden-Saksalan tie 1"
        (get-in owner1 [:henkilo :osoite :postinumero]) => "06500"
        (get-in owner1 [:henkilo :osoite :postitoimipaikannimi]) => "PORVOO"

        (get-in owner1 [:_selected]) => "henkilo"
        (get-in owner2 [:henkilo :henkilotiedot :etunimi]) => "Pauliina"
        (get-in owner2 [:henkilo :henkilotiedot :sukunimi]) => "Pekkala"
        (get-in owner2 [:henkilo :osoite :katu]) => "Uuden-Saksalan tie 1"
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
        (get-in owner1 [:henkilo :henkilotiedot :sukunimi]) => "Pekkala"
        (get-in owner1 [:omistajalaji]) => nil
        (get-in owner1 [:muu-omistajalaji]) => ", wut?"

        (get-in owner2 [:_selected]) => "yritys"
        (get-in owner2 [:omistajalaji]) => "yksityinen yritys (osake-, avoin- tai kommandiittiyhti\u00f6, osuuskunta)"
        (get-in owner2 [:muu-omistajalaji]) => nil
        (get-in owner2 [:yritys :yhteyshenkilo :henkilotiedot :etunimi]) => "Paavo"
        (get-in owner2 [:yritys :yhteyshenkilo :henkilotiedot :sukunimi]) => "Pekkala"
        (get-in owner2 [:yritys :yhteyshenkilo :yhteystiedot :puhelin]) => "01"
        (get-in owner2 [:yritys :yhteyshenkilo :yhteystiedot :email]) => "paavo@example.com"
        (get-in owner2 [:yritys :yritysnimi]) => "Pekkalan Putki Oy"
        (get-in owner2 [:yritys :liikeJaYhteisoTunnus]) => "123"
        (get-in owner2 [:yritys :osoite :katu]) => "Uuden-Saksalan tie 1\u20132 d\u2013e A 1"
        (get-in owner2 [:yritys :osoite :postinumero]) => "06500"
        (get-in owner2 [:yritys :osoite :postitoimipaikannimi]) => "PORVOO"))))


;; YA verdict

(facts "KRYSP ya-verdict"
  (let [xml (xml/parse (slurp "resources/krysp/sample/yleiset alueet/ya-verdict.xml"))
        cases (->verdicts xml ->simple-verdicts)]

    (fact "xml is parsed" cases => truthy)
    (fact "xml has 1 cases" (count cases) => 1)
    (fact "has 1 verdicts" (-> cases last :paatokset count) => 1)

    (fact "validator finds verdicts" (simple-verdicts-validator xml) => nil)

    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "523")

    (let [verdict (first (:paatokset (last cases)))
          lupamaaraykset (:lupamaaraykset verdict)
          paivamaarat    (:paivamaarat verdict)
          poytakirjat    (:poytakirjat verdict)]

      (facts "lupamaaraukset data is correct"
        lupamaaraykset => truthy
        (:takuuaikaPaivat lupamaaraykset) => "760"
        (let [muutMaaraykset (:muutMaaraykset lupamaaraykset)]
          muutMaaraykset => sequential?
          (count muutMaaraykset) => 2
          (last muutMaaraykset) => "Kaivu: Viheralueet rakennettava."))

      (facts "paivamaarat data is correct"
        paivamaarat => truthy
        (:paatosdokumentinPvm paivamaarat) => (to-timestamp "2014-01-29"))

      (facts "p\u00f6yt\u00e4kirjat data is correct"
        (let [pk1   (first poytakirjat)
              liite (:liite pk1)]
          (:kuvaus liite) => "liite"
          (:linkkiliitteeseen liite) => "http://demokuopio.vianova.fi/Lupapiste/GetFile.aspx?GET_FILE=1106"
          (:muokkausHetki liite) => (to-timestamp "2014-01-29T13:58:15")
          (:tyyppi liite) => "Muu liite")))))

(facts "Ymparisto verdicts"
  (doseq [permit-type ["yl" "mal" "vvvl"]]
    (let [xml (xml/parse (slurp (str "resources/krysp/sample/verdict-" permit-type ".xml")))
          cases (->verdicts xml ->simple-verdicts)]

      (fact "xml is parsed" cases => truthy)
      (fact "xml has 1 cases" (count cases) => 1)
      (fact "has 1 verdicts" (-> cases last :paatokset count) => 1)

      (fact "validator finds verdicts" (simple-verdicts-validator xml) => nil)

      (fact "kuntalupatunnus"
        (:kuntalupatunnus (last cases)) => #(.startsWith % "638-2014-"))

    (let [verdict (first (:paatokset (last cases)))
          lupamaaraykset (:lupamaaraykset verdict)
          paivamaarat    (:paivamaarat verdict)
          poytakirjat    (:poytakirjat verdict)]

      (facts "lupamaaraukset data is correct"
        lupamaaraykset => truthy
        (:takuuaikaPaivat lupamaaraykset) => "5"
        (let [muutMaaraykset (:muutMaaraykset lupamaaraykset)]
          muutMaaraykset => sequential?
          (count muutMaaraykset) => 1
          (last muutMaaraykset) => "Lupaehdot vapaana tekstin\u00e4"))

      (facts "paivamaarat data is correct"
        paivamaarat => truthy
        (:paatosdokumentinPvm paivamaarat) => (to-timestamp "2014-04-11"))

      (facts "p\u00f6yt\u00e4kirjat data is correct"
        (let [pk1   (first poytakirjat)
              liite (:liite pk1)]
          (:kuvaus liite) => "paatoksenTiedot"
          (:linkkiliitteeseen liite) => "http://localhost:8000/img/under-construction.gif"
          (:muokkausHetki liite) => (to-timestamp "2014-03-29T13:58:15")
          (:tyyppi liite) => "Muu liite"))))))

(facts "Buildings from verdict message"
  (let [xml (xml/parse (slurp "resources/krysp/sample/sito-porvoo-LP-638-2013-00024-paatos-ilman-liitteita.xml"))
        buildings (->buildings xml)
        building1 (first buildings)]
    (count buildings) => 1
    (:jarjestysnumero building1) => "31216"
    (:kiinttun building1) => "63820130310000"
    (:rakennusnro building1) => "123"
    (:valtakunnallinenNumero building1) => "1234567892"))

(facts "wfs-krysp-url works correctly"
  (fact "without ? returns url with ?"
    (wfs-krysp-url "http://localhost" rakval-case-type (property-equals "test" "lp-1")) => "http://localhost?request=GetFeature&typeName=rakval%3ARakennusvalvontaAsia&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Etest%3C%2FPropertyName%3E%3CLiteral%3Elp-1%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")
  (fact "with ? returns url with ?"
    (wfs-krysp-url "http://localhost" rakval-case-type (property-equals "test" "lp-1")) => "http://localhost?request=GetFeature&typeName=rakval%3ARakennusvalvontaAsia&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Etest%3C%2FPropertyName%3E%3CLiteral%3Elp-1%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")
  (fact "without extraparam returns correct"
    (wfs-krysp-url "http://localhost?output=KRYSP" rakval-case-type (property-equals "test" "lp-1")) => "http://localhost?output=KRYSP&request=GetFeature&typeName=rakval%3ARakennusvalvontaAsia&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Etest%3C%2FPropertyName%3E%3CLiteral%3Elp-1%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E"))



(facts* "Testing information parsed from a verdict xml message for application creation"
  (let [xml (xml/parse (slurp "resources/krysp/sample/verdict-rakval-from-kuntalupatunnus-query.xml"))
        info (get-app-info-from-message xml "14-0241-R 3") => truthy
        {:keys [id kuntalupatunnus municipality rakennusvalvontaasianKuvaus vahainenPoikkeaminen rakennuspaikka ensimmainen-rakennus hakijat]} info]

    (fact "info contains the needed keys" (every? (partial contains info)
                                            [:id :kuntalupatunnus :municipality :rakennusvalvontaasianKuvaus :vahainenPoikkeaminen :rakennuspaikka :ensimmainen-rakennus :hakijat]))

    (fact "invalid kuntalupatunnus" (get-app-info-from-message xml "invalid-kuntalupatunnus") => nil)

    (fact "kuntalupatunnus" kuntalupatunnus => "14-0241-R 3")
    (fact "municipality" municipality => "186")
    (fact "rakennusvalvontaasianKuvaus" rakennusvalvontaasianKuvaus => "Rakennetaan yksikerroksinen lautaverhottu omakotitalo jossa kytketty autokatos/ varasto.")
    (fact "vahainenPoikkeaminen" vahainenPoikkeaminen => "Poikekkaa meill\u00e4!")
    (facts "hakijat"
      (fact "count" (count hakijat) => 6)
      (fact "with email address" (filter identity (map #(get-in % [:henkilo :sahkopostiosoite]) hakijat)) => (just #{"pena@example.com" "mikko@example.com" " \\t   "})))

    (facts "Rakennuspaikka"
      (let [{:keys [x y address propertyId] :as rakennuspaikka} rakennuspaikka]
        (fact "contains all the needed keys" (every? (-> rakennuspaikka keys set) [:x :y :address :propertyId]))
        (fact "x" x => #(and (instance? Double %) (= 393033.614 %)))
        (fact "y" y => #(and (instance? Double %) (= 6707228.994 %)))
        (fact "address" address => "Kylykuja 3-5 D 35b-c")
        (fact "propertyId" propertyId => "18600303560006")))))



