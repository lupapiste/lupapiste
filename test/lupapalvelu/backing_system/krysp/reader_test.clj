(ns lupapalvelu.backing-system.krysp.reader-test
  (:require [clojure.java.io :as io]
            [hiccup.core :as hiccup]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.backing-system.krysp.common-reader :refer [rakval-case-type property-equals property-in
                                                                    wfs-krysp-url case-elem-selector get-osoite
                                                                    find-valid-point]]
            [lupapalvelu.backing-system.krysp.reader :as rdr :refer :all]
            [lupapalvelu.backing-system.krysp.review-reader :as review-reader]
            [lupapalvelu.factlet :refer [facts*]]
            [lupapalvelu.krysp-test-util :refer [build-multi-app-xml xml-without-ns-from-file]]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.xml.validator :as xml-validator]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as cr]
            [sade.core :refer [def-]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.xml :as xml]))

(testable-privates lupapalvelu.backing-system.krysp.reader
                   newest-poytakirja-with-attrs
                   ->paatospoytakirja
                   ->standard-verdicts
                   standard-verdicts-validator
                   simple-verdicts-validator
                   ->simple-verdicts
                   party-with-paatos-data
                   resolve-property-id-by-point
                   valid-sijaistustieto?
                   build-jakokirjain
                   locator)

(testable-privates lupapalvelu.backing-system.krysp.application-from-krysp
                   get-lp-tunnus
                   get-tunnus-elems)

(fact "property-equals returns url-encoded data"
  (property-equals "_a_" "_b_") => "%3CPropertyIsEqualTo%3E%3CPropertyName%3E_a_%3C%2FPropertyName%3E%3CLiteral%3E_b_%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")

(fact "property-in returns url-encoded data"
  (property-in "_a_" ["_b_" "_c_"]) => "%3COr%3E%3CPropertyIsEqualTo%3E%3CPropertyName%3E_a_%3C%2FPropertyName%3E%3CLiteral%3E_b_%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E%3CPropertyIsEqualTo%3E%3CPropertyName%3E_a_%3C%2FPropertyName%3E%3CLiteral%3E_c_%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E%3C%2FOr%3E")

(fact "property-equals returns url-encoded xml-encoded data"
  (property-equals "<a>" "<b>") => "%3CPropertyIsEqualTo%3E%3CPropertyName%3E%26lt%3Ba%26gt%3B%3C%2FPropertyName%3E%3CLiteral%3E%26lt%3Bb%26gt%3B%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")

(fact "property-in returns url-encoded xml-encoded data"
  (property-in "<a>" ["<b>" "<c>"]) => "%3COr%3E%3CPropertyIsEqualTo%3E%3CPropertyName%3E%26lt%3Ba%26gt%3B%3C%2FPropertyName%3E%3CLiteral%3E%26lt%3Bb%26gt%3B%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E%3CPropertyIsEqualTo%3E%3CPropertyName%3E%26lt%3Ba%26gt%3B%3C%2FPropertyName%3E%3CLiteral%3E%26lt%3Bc%26gt%3B%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E%3C%2FOr%3E")

(defn verdict-skeleton [poytakirja-xml]
  {:tag :paatostieto
   :content [{:tag :paatostieto
              :content [{:tag :Paatos
                         :content [{:tag :poytakirja :content poytakirja-xml}]}]}]})

(def future-verdict (verdict-skeleton [{:tag :paatoskoodi :content ["hyväksytty"]}
                                       {:tag :paatoksentekija :content [""]}
                                       {:tag :paatospvm :content [(date/iso-date (date/today) :local)]}]))

(defn verdict-to-be-given [date-str]
  {:tag     :paatostieto
   :content [{:tag     :paatostieto
              :content [{:tag     :Paatos
                         :content [{:tag :paivamaarat :content [{:tag :antoPvm :content [date-str]}]}
                                   {:tag :poytakirja :content [{:tag :paatoskoodi :content ["hyväksytty"]}
                                                               {:tag :paatoksentekija :content [""]}
                                                               {:tag :paatospvm :content ["1970-01-01"]}]}]}]}]})

(def past-verdict (verdict-skeleton [{:tag :paatoskoodi :content ["hyväksytty"]}
                                     {:tag :paatoksentekija :content [""]}
                                     {:tag :paatospvm :content ["1970-01-01"]}]))

;; https://www.paikkatietopalvelu.fi/gml/KuntaGML.html
;; "XML-tiedostojen luomisessa on sovittu käytettäväksi seuraavia oletusarvoja ja ehtoja :"
;; - puuttuva päivämäärätieto ilmaistaan arvolla: 1.1.1001
(def kuntagml-year-1001-verdict (verdict-skeleton [{:tag :paatoskoodi :content ["hyväksytty"]}
                                                   {:tag :paatoksentekija :content ["Bad and Ugly"]}
                                                   {:tag :paatospvm :content ["1001-01-01"]}]))


(fact "newest-poytakirja-with-attrs"
  (let [future-pk    (->paatospoytakirja future-verdict)
        past-pk      (->paatospoytakirja past-verdict)
        year-1001-pk (->paatospoytakirja kuntagml-year-1001-verdict)]
    (newest-poytakirja-with-attrs [] []) => nil
    (newest-poytakirja-with-attrs [past-pk] [:random-test-keyword]) => nil
    (newest-poytakirja-with-attrs [past-pk] [:paatospvm]) => past-pk
    (newest-poytakirja-with-attrs [future-pk past-pk] [:paatospvm]) => future-pk
    (newest-poytakirja-with-attrs [past-pk future-pk] [:paatospvm]) => future-pk
    (newest-poytakirja-with-attrs [past-pk future-pk year-1001-pk] [:paatospvm :paatoskoodi]) => future-pk))

(facts "standard-verdicts-validator"
  (fact "Missing details"
    (standard-verdicts-validator (verdict-skeleton []) {}) => {:ok false, :text "info.paatos-details-missing"})
  (fact "Future date"
    (standard-verdicts-validator future-verdict {}) => {:ok false, :text "info.paatos-future-date"} )
  (fact "Verdict to be given on a future date fails if validation is on"
    (standard-verdicts-validator (verdict-to-be-given "2099-01-02") {:validate-verdict-given-date true})
    => {:ok false, :text "info.paatos-future-date"})
  (fact "Verdict given date does not matter if validation is off"
    (standard-verdicts-validator (verdict-to-be-given "2099-01-02") {:validate-verdict-given-date false})
    => nil)
  (fact "Verdict to be given date fails if validation is on and date is 'unknown' by KuntaGML spec"
    (standard-verdicts-validator (verdict-to-be-given "1001-01-01") {:validate-verdict-given-date true})
    => {:ok false, :text "info.paatos-future-date"})
  (fact "Past date"
    (standard-verdicts-validator past-verdict {}) => nil)
  (fact "Date 1001-01-01 considered 'unknown' by KuntaGML spec"
    (standard-verdicts-validator kuntagml-year-1001-verdict {}) => {:ok false, :text "info.paatos-future-date"})
  (fact "Multiple verdicts, latest is valid"
    (standard-verdicts-validator [kuntagml-year-1001-verdict past-verdict] {}) => nil?))

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

(facts "KRYSP verdict"
  (let [xml (xml/parse (io/input-stream "resources/krysp/dev/verdict.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]

    (fact "xml is parsed" cases => truthy)

    (fact "validator finds verdicts" (standard-verdicts-validator xml {}) => nil)

    (fact "xml has 2 cases" (count cases) => 2)
    (fact "second case has 2 verdicts" (-> cases last :paatokset count) => 2)

    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "13-0185-R")

    (let [verdict        (-> cases last :paatokset first)
          lupamaaraykset (:lupamaaraykset verdict)
          paivamaarat    (:paivamaarat verdict)
          poytakirjat    (:poytakirjat verdict)]

      (facts "lupamaaraykset data is correct"
        lupamaaraykset => truthy
        (:autopaikkojaEnintaan lupamaaraykset) => 10
        (:autopaikkojaVahintaan lupamaaraykset) => 1
        (:autopaikkojaRakennettava lupamaaraykset) => 2
        (:autopaikkojaRakennettu lupamaaraykset) => 0
        (:autopaikkojaKiinteistolla lupamaaraykset) => 7
        (:autopaikkojaUlkopuolella lupamaaraykset) => 3
        (:kerrosala lupamaaraykset) => "100"
        (:kokonaisala lupamaaraykset) => "110"
        (:vaaditutTyonjohtajat lupamaaraykset) => "Vastaava työnjohtaja, Vastaava IV-työnjohtaja, Työnjohtaja"
        (let [katselmukset (:vaaditutKatselmukset lupamaaraykset)
              maaraykset   (:maaraykset lupamaaraykset)]
          (facts "katselmukset"
            (count katselmukset) => 2
            (:katselmuksenLaji (first katselmukset)) => "aloituskokous"
            (:tarkastuksenTaiKatselmuksenNimi (last katselmukset)) => "Käyttöönottotarkastus")
          (facts "määräykset"
            (count maaraykset) => 2
            (:sisalto (first maaraykset)) => "Radontekninen suunnitelma"
            (:maaraysaika (first maaraykset)) => (date/timestamp "2013-08-28")
            (:toteutusHetki (last maaraykset)) => (date/timestamp "2013-08-31")))

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
        (:aloitettava paivamaarat) => (date/timestamp "2013-09-01")
        (:lainvoimainen paivamaarat) => (date/timestamp "2013-09-02")
        (:voimassaHetki paivamaarat) => (date/timestamp "2013-09-03")
        (:raukeamis paivamaarat) => (date/timestamp "2013-09-04")
        (:anto paivamaarat) => (date/timestamp "2013-09-05")
        (:viimeinenValitus paivamaarat) => (date/timestamp "2100-09-06")
        (:julkipano paivamaarat) => (date/timestamp "2013-09-07"))

      (facts "pöytäkirjat data is correct"
        poytakirjat    => truthy
        (count poytakirjat) => 2

        (let [pk1   (first poytakirjat)
              liite (:liite pk1)]
          (:paatos pk1) => "Päätös 1"
          (:paatoskoodi pk1) => "myönnetty"
          (:paatoksentekija pk1) => "viranomainen"
          (:paatospvm pk1) => (date/timestamp "2013-08-01")
          (:pykala pk1) => 1
          (:kuvaus liite) => "kuvaus 1"
          (:linkkiliitteeseen liite) => "http://localhost:8000/dev/sample-verdict.pdf"
          (:muokkausHetki liite) => (date/timestamp "2013-09-01T12:00:00")
          (:versionumero liite) => "1"
          (get-in liite [:tekija :henkilo :nimi :sukunimi]) => "Tarkkanen"
          (:tyyppi liite) => "paatos"
          (:metadata liite) => {:nimi "arvo"})

        (facts "second verdict"
          (let [poytakirjat2 (-> cases last :paatokset last :poytakirjat)]
            (count poytakirjat2) => 1
            poytakirjat2 => sequential?))))))

(facts "KRYSP verdict 2.1.8"
  (let [xml (xml/parse (io/input-stream "dev-resources/krysp/verdict-r-2.1.8.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]

    (fact "xml is parsed" cases => truthy)
    (fact "validator finds verdicts" (standard-verdicts-validator xml {}) => nil)

    (let [verdict        (-> cases last :paatokset first)
          lupamaaraykset (:lupamaaraykset verdict)
          maaraykset     (:maaraykset lupamaaraykset)
          vaaditut-erityissuunnitelmat (:vaaditutErityissuunnitelmat lupamaaraykset)]

      (fact "vaaditut erityissuunnitelmat"
        vaaditut-erityissuunnitelmat => sequential?
        vaaditut-erityissuunnitelmat => (just ["ES 1" "ES 22" "ES 333"] :in-any-order))

      (fact "määräykset"
        (count maaraykset) => 2
        (:sisalto (first maaraykset)) => "Radontekninen suunnitelma"
        (:maaraysaika (first maaraykset)) => (date/timestamp "2013-08-28")
        (:toteutusHetki (last maaraykset)) => (date/timestamp "2013-08-31")))))

(facts "KRYSP verdict 2.1.8"
  (let [xml (xml/parse (io/input-stream "dev-resources/krysp/verdict-r-2.1.8-foremen.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]

    (fact "xml is parsed" cases => truthy)
    (fact "validator finds verdicts" (standard-verdicts-validator xml {}) => nil)

    (let [verdict (first (:paatokset (last cases)))
          lupamaaraykset (:lupamaaraykset verdict)
          vaaditut-erityissuunnitelmat (:vaaditutErityissuunnitelmat lupamaaraykset)]

      ;; In xml message, just one vaadittuErityissuunnitelma element is provided
      ;; where there are multiple "vaadittuErityissuunnitelma"s combined as one string, separated by line break.
      ;; Testing here that the reader divides those as different elements properly.
      (fact "vaaditut erityissuunnitelmat style"
        vaaditut-erityissuunnitelmat => sequential?
        vaaditut-erityissuunnitelmat => (just ["Rakennesuunnitelmat" "Vesi- ja viemärisuunnitelmat" "Ilmanvaihtosuunnitelmat"] :in-any-order)))))


(facts "KRYSP verdict 2.2.0"
  (let [xml-s (slurp "dev-resources/krysp/verdict-r-2.2.0.xml")
        xml (xml/parse xml-s)
        cases (->verdicts xml :R permit/read-verdict-xml)]

    (fact "validate xml"
      (try (xml-validator/validate xml-s :R "2.2.0")
           (catch org.xml.sax.SAXParseException e
             (.getMessage e))) => nil)
    (fact "xml is parsed" cases => truthy)
    (fact "validator finds verdicts" (standard-verdicts-validator xml {}) => nil)

    (let [verdict        (-> cases last :paatokset first)
          lupamaaraykset (:lupamaaraykset verdict)
          maaraykset     (:maaraykset lupamaaraykset)]

      (facts "lupamaaraykset data is correct"
        lupamaaraykset => truthy
        (:rakennusoikeudellinenKerrosala lupamaaraykset) => "101"
        (:vaaditutTyonjohtajat lupamaaraykset) => "IV-työnjohtaja, KVV-työnjohtaja, vastaava työnjohtaja"
        (:vaaditutErityissuunnitelmat lupamaaraykset) => (just ["ES 1" "ES 22" "ES 333"] :in-any-order))

      (fact "määräykset"
        (count maaraykset) => 2
        (:sisalto (first maaraykset)) => "Radontekninen suunnitelma"
        (:maaraysaika (first maaraykset)) => (date/timestamp "2013-08-28")
        (:toteutusHetki (last maaraykset)) => (date/timestamp "2013-08-31")))))

(facts "KRYSP verdict 2.2.2"
  (let [xml-s (slurp "dev-resources/krysp/verdict-r-2.2.2.xml")
        xml (xml/parse xml-s)
        cases (->verdicts xml :R permit/read-verdict-xml)]

    (fact "validate xml"
      (try (xml-validator/validate xml-s :R "2.2.2")
           (catch org.xml.sax.SAXParseException e
             (.getMessage e))) => nil)
    (fact "xml is parsed" cases => truthy)
    (fact "validator finds verdicts" (standard-verdicts-validator xml {}) => nil)

    (let [verdict        (-> cases last :paatokset first)
          lupamaaraykset (:lupamaaraykset verdict)]

      (facts "lupamaaraykset data is correct"
        lupamaaraykset => truthy
        (:rakennusoikeudellinenKerrosala lupamaaraykset) => "101"
        (:vaaditutErityissuunnitelmat lupamaaraykset) => (just ["ES 1" "ES 22" "ES 333"] :in-any-order)
        (->> (:vaaditutKatselmukset lupamaaraykset) (map :muuTunnus)) => (just ["" "999"] :in-any-order)
        (->> (:vaaditutKatselmukset lupamaaraykset) (map :muuTunnusSovellus)) => (just ["" "RakApp"] :in-any-order)))))

(facts "KRYSP verdict 2.2.3"
  (let [xml-s (slurp "dev-resources/krysp/verdict-r-2.2.3.xml")
        xml (xml/parse xml-s)
        cases (->verdicts xml :R permit/read-verdict-xml)]
    (fact "validate xml"
      (try (xml-validator/validate xml-s :R "2.2.3")
           (catch org.xml.sax.SAXParseException e
             (.getMessage e))) => nil)
    (fact "xml is parsed" cases => truthy)
    (fact "validator finds verdicts" (standard-verdicts-validator xml {}) => nil)

    (let [verdict        (-> cases last :paatokset first)
          lupamaaraykset (:lupamaaraykset verdict)]

      (facts "lupamaaraykset"
        lupamaaraykset => truthy
        (:rakennusoikeudellinenKerrosala lupamaaraykset) => "101"
        (:vaaditutErityissuunnitelmat lupamaaraykset) => (just ["ES 1" "ES 22" "ES 333"] :in-any-order)
        (->> (:vaaditutKatselmukset lupamaaraykset) (map :muuTunnus)) => (just ["123" "999"] :in-any-order)
        (->> (:vaaditutKatselmukset lupamaaraykset) (map :muuTunnusSovellus)) => (just ["PiliPali" "RakApp"] :in-any-order)
        (:kokoontumistilanHenkilomaara lupamaaraykset) => "5"))))

(facts "CGI sample verdict"
  (let [xml (xml/parse (io/input-stream "dev-resources/krysp/verdict-r.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]
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
        (:aloitettava paivamaarat) => (date/timestamp "2016-10-08")
        (:lainvoimainen paivamaarat) => (date/timestamp "2013-10-08")
        (:voimassaHetki paivamaarat) => (date/timestamp "2018-10-08")
        (:raukeamis paivamaarat) => nil
        (:anto paivamaarat) => (date/timestamp "2013-09-06")
        (:viimeinenValitus paivamaarat) => (date/timestamp "2013-10-07")
        (:julkipano paivamaarat) => (date/timestamp "2013-09-04"))


      (facts "pöytäkirjat data is correct"
        poytakirjat    => truthy
        (count poytakirjat) => 1

        (let [pk1   (first poytakirjat)
              liite (:liite pk1)]
          (:paatos pk1) => nil
          (:paatoskoodi pk1) => "myönnetty"
          (:paatoksentekija pk1) => "Rakennuslautakunta"
          (:paatospvm pk1) => (date/timestamp "2013-09-03")
          (:pykala pk1) => 12
          (:kuvaus liite) => "Päätösote"
          (:linkkiliitteeseen liite) => "http://localhost:8000/dev/sample-attachment.txt"
          (:muokkausHetki liite) => (date/timestamp "2013-09-03T15:27:46Z")
          (:tyyppi liite) => "Päätösote")))))

(facts "Tekla sample verdict"
  (let [xml (xml/parse (io/input-stream "dev-resources/krysp/verdict-r-buildings.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]

    (fact "xml is parsed" cases => truthy)

    (fact "validator finds verdicts" (standard-verdicts-validator xml {}) => nil)

    (fact "xml has one case" (count cases) => 1)
    (fact "case has 1 verdict" (-> cases last :paatokset count) => 1)

    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "109-2014-140")

    (let [verdict        (-> cases first :paatokset first)
          lupamaaraykset (:lupamaaraykset verdict)
          katselmukset   (:vaaditutKatselmukset lupamaaraykset)
          maaraykset     (:maaraykset lupamaaraykset)]

      (facts "lupamaaraykset data is correct"
        lupamaaraykset => truthy
        (:kerrosala lupamaaraykset) => "134"
        (:kokonaisala lupamaaraykset) => "134"
        (:vaaditutTyonjohtajat lupamaaraykset) => "Vastaava työnjohtaja, KVV-työnjohtaja, IV-työnjohtaja"
        (facts "katselmukset"
          (count katselmukset) => 4
          (:katselmuksenLaji (first katselmukset)) => "aloituskokous"
          (:katselmuksenLaji (last katselmukset)) => "loppukatselmus")
        (facts "määräykset"
          (count maaraykset) => 3
          (:sisalto (first maaraykset)) => "Vastaavan työnjohtajan on ylläpidettävä rakennustyön tarkastusasiakirjaa, johon eri rakennusvaiheiden vastuuhenkilöt ja työvaiheiden tarkastuksia suorittavat henkilöt varmentavat tekemänsä tarkastukset (MRL 150 \u00a7 3. mom., MRA 77 \u00a7)."))
      )))

(facts "case not found"
  (let [xml (xml/parse (io/input-stream "dev-resources/krysp/verdict-r-not-found.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]
    (fact "xml is parsed" cases => truthy)
    (fact "xml has no cases" (count cases) => 0)
    (fact "validator does not find verdicts" (standard-verdicts-validator xml {}) => {:ok false, :text "info.no-verdicts-found-from-backend"})))

(facts "nil xml"
  (let [cases (->verdicts nil :R permit/read-verdict-xml)]
    (seq cases) => nil
    (count cases) => 0))

(facts "no verdicts"
  (let [xml (xml/parse (io/input-stream "dev-resources/krysp/verdict-r-no-verdicts.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]
    (fact "xml is parsed" cases => truthy)
    (fact "xml has 1 case" (count cases) => 1)
    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "13-0185-R")
    (fact "validator does not find verdicts" (standard-verdicts-validator xml {}) => {:ok false, :text "info.no-verdicts-found-from-backend"})
    (fact "case has no verdicts" (-> cases last :paatokset count) => 0)))

(facts "KRYSP verdict with reviews"
  (let [xml (-> "resources/krysp/dev/r-verdict-review.xml" io/input-stream xml/parse)
        reviews (review-reader/xml->reviews xml)
        katselmus-tasks (map (partial tasks/katselmus->task {} {} {}) reviews)
        aloitus-review-task (nth katselmus-tasks 0)
        non-empty (complement clojure.string/blank?)]
    (fact "xml has 13 reviews" (count reviews) => 13)
    (fact "huomautukset" (get-in aloitus-review-task [:data :katselmus :huomautukset :kuvaus :value]) => non-empty)
    (fact "katselmuksenLaji" (get-in aloitus-review-task [:data :katselmuksenLaji :value]) => "aloituskokous")
    (fact "tunnustieto" (get-in aloitus-review-task [:data :muuTunnus]) => truthy)
    (fact "lasnaolijat" (get-in (last katselmus-tasks) [:data :katselmus :lasnaolijat :value]) => (contains "Teppo"))))

;; YA verdict

(facts "KRYSP ya-verdict"
  (let [xml (xml/parse (io/input-stream "resources/krysp/dev/verdict-ya.xml"))
        cases (->verdicts xml :YA permit/read-verdict-xml)]

    (fact "xml is parsed" cases => truthy)
    (fact "xml has 1 cases" (count cases) => 1)
    (fact "has 1 verdicts" (-> cases last :paatokset count) => 1)

    (fact "validator finds verdicts" (simple-verdicts-validator xml) => nil)

    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "523")

    (let [verdict (first (:paatokset (last cases)))
          lupamaaraykset (:lupamaaraykset verdict)
          paivamaarat    (:paivamaarat verdict)
          poytakirjat    (:poytakirjat verdict)]

      (facts "lupamaaraykset data is correct"
        lupamaaraykset => truthy
        (:takuuaikaPaivat lupamaaraykset) => "760"
        (let [muutMaaraykset (:muutMaaraykset lupamaaraykset)]
          muutMaaraykset => sequential?
          (count muutMaaraykset) => 2
          (last muutMaaraykset) => "Kaivu: Viheralueet rakennettava."))

      (facts "paivamaarat data is correct"
        paivamaarat => truthy
        (:paatosdokumentinPvm paivamaarat) => (date/timestamp "2014-01-29"))

      (facts "pöytäkirjat data is correct"
        (let [pk1   (first poytakirjat)
              liite (:liite pk1)]
          (:kuvaus liite) => "liite"
          (:linkkiliitteeseen liite) => "http://localhost:8000/dev/sample-attachment.txt"
          (:muokkausHetki liite) => (date/timestamp "2014-01-29T13:58:15")
          (:tyyppi liite) => "Muu liite")))))

(facts "Ymparisto verdicts"
  (doseq [permit-type ["YL" "MAL" "VVVL"]]
    (let [xml (xml/parse (io/input-stream (str "resources/krysp/dev/verdict-" (ss/lower-case permit-type) ".xml")))
          cases (->verdicts xml permit-type permit/read-verdict-xml)]

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

        (facts "lupamaaraykset data is correct"
          lupamaaraykset => truthy
          (:takuuaikaPaivat lupamaaraykset) => "5"
          (let [muutMaaraykset (:muutMaaraykset lupamaaraykset)]
            muutMaaraykset => sequential?
            (count muutMaaraykset) => 1
            (last muutMaaraykset) => "Lupaehdot vapaana tekstinä"))

        (facts "paivamaarat data is correct"
          paivamaarat => truthy
          (:paatosdokumentinPvm paivamaarat) => (date/timestamp "2014-04-11"))

        (facts "pöytäkirjat data is correct"
          (let [pk1   (first poytakirjat)
                liite (:liite pk1)]
            (:kuvaus liite) => "paatoksenTiedot"
            (:linkkiliitteeseen liite) => "http://localhost:8000/dev/sample-attachment.txt"
            (:muokkausHetki liite) => (date/timestamp "2014-03-29T13:58:15")
            (:tyyppi liite) => "Muu liite"))))))

(facts "Maaraykset from verdict message"
  (let [xml (xml/parse (io/input-stream "dev-resources/krysp/verdict-r-empty-maarays-element.xml"))
        verdicts (->verdicts xml :R permit/read-verdict-xml)
        paatokset (:paatokset (first verdicts))
        lupamaaraykset (:lupamaaraykset (first paatokset))
        maaraykset (:maaraykset lupamaaraykset)]
    maaraykset =not=> (contains [nil])
    (count maaraykset) => 3))

(facts "wfs-krysp-url works correctly"
  (fact "without ? returns url with ?"
    (wfs-krysp-url "http://localhost" rakval-case-type (property-equals "test" "lp-1")) => "http://localhost?request=GetFeature&maxFeatures=1000&typeName=rakval%3ARakennusvalvontaAsia&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Etest%3C%2FPropertyName%3E%3CLiteral%3Elp-1%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")
  (fact "with ? returns url with ?"
    (wfs-krysp-url "http://localhost" rakval-case-type (property-equals "test" "lp-1")) => "http://localhost?request=GetFeature&maxFeatures=1000&typeName=rakval%3ARakennusvalvontaAsia&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Etest%3C%2FPropertyName%3E%3CLiteral%3Elp-1%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E")
  (fact "without extraparam returns correct"
    (wfs-krysp-url "http://localhost?output=KRYSP" rakval-case-type (property-equals "test" "lp-1")) => "http://localhost?output=KRYSP&request=GetFeature&maxFeatures=1000&typeName=rakval%3ARakennusvalvontaAsia&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Etest%3C%2FPropertyName%3E%3CLiteral%3Elp-1%3C%2FLiteral%3E%3C%2FPropertyIsEqualTo%3E"))

(facts "Testing information parsed from a verdict xml message for application creation"
  (against-background
    [(sade.env/feature? :disable-ktj-on-create) => true
     (#'lupapalvelu.backing-system.krysp.reader/resolve-property-id-by-point anything)
     => "18600303560006"
     (#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point anything anything)
     => nil])
  (let [xml (xml/parse (io/input-stream "resources/krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml"))
        info (get-app-info-from-message xml "14-0241-R 3")
        {:keys [kuntalupatunnus municipality rakennusvalvontaasianKuvaus vahainenPoikkeaminen rakennuspaikka hakijat]} info]
    (fact "got info" info => truthy)
    (fact "info contains the needed keys" (every? (partial contains info)
                                                  [:id :kuntalupatunnus :municipality :rakennusvalvontaasianKuvaus :vahainenPoikkeaminen :rakennuspaikka :ensimmainen-rakennus :hakijat]))

    (fact "invalid kuntalupatunnus" (get-app-info-from-message xml "invalid-kuntalupatunnus") => nil)

    (fact "kuntalupatunnus" kuntalupatunnus => "14-0241-R 3")
    (fact "municipality" municipality => "186")
    (fact "rakennusvalvontaasianKuvaus"
      rakennusvalvontaasianKuvaus => "Rakennetaan yksikerroksinen lautaverhottu omakotitalo jossa kytketty autokatos/ varasto."
      (read-permit-descriptions-from-xml :R (cr/strip-xml-namespaces xml))
      => (just {:kuntalupatunnus "14-0241-R 3"
                :kuvaus "Rakennetaan yksikerroksinen lautaverhottu omakotitalo jossa kytketty autokatos/ varasto."}))
    (fact "vahainenPoikkeaminen" vahainenPoikkeaminen => "Poikekkaa meillä!")
    (facts "hakijat"
      (fact "count" (count hakijat) => 6)
      (fact "with email address" (filter identity (map #(get-in % [:henkilo :sahkopostiosoite]) hakijat)) => (just #{"pena@example.com" "mikko@example.com" "\\t"})))

    (facts "Rakennuspaikka"
      (let [{:keys [x y address propertyId] :as rakennuspaikka} rakennuspaikka]
        (fact "contains all the needed keys" (every? (-> rakennuspaikka keys set) [:x :y :address :propertyId]))
        (fact "x" x => #(and (instance? Double %) (= 393033.614 %)))
        (fact "y" y => #(and (instance? Double %) (= 6707228.994 %)))
        (fact "address" address => "Kylykuja 3-5 D 35b-c")
        (fact "propertyId" propertyId => "18600303560006"))))
  (facts "Feature collection"
    (let [xml (-> "resources/krysp/dev/feature-collection-with-many-featureMember-elems.xml"
                  slurp
                  (xml/parse-string "utf8"))]
      (fact "No kuntalupatunnus conflict"
        (get-app-info-from-message xml "999-2016-999") => truthy)
      (fact "Only one case element (typically RakennusvalvontaAsia) per kuntalupatunnus is allowed"
        (-> xml
            (enlive/at [:rakval:RakennusvalvontaAsia :rakval:luvanTunnisteTiedot
                        :yht:LupaTunnus :yht:kuntalupatunnus]
                       (enlive/content "double decker"))
            (get-app-info-from-message "double decker")) => nil))))

(facts "poikkeamisasianKuvaus"
  (let [xml (-> (io/input-stream "resources/krysp/dev/verdict-p.xml")
                xml/parse
                cr/strip-xml-namespaces)]
    (read-permit-descriptions-from-xml :P xml)
    => (just {:kuntalupatunnus "13-0192-POI"
              :kuvaus          "Haetaan poikkeamispäätöstä rakentaa..."})))

(facts "Multiple features with different descriptions in the same XML file"
  (let [xml (xml/parse (io/input-stream "resources/krysp/dev/feature-collection-with-many-featureMember-elems.xml"))]
    (fact "rakennusvalvontaasianKuvaus"
      (set (read-permit-descriptions-from-xml :R (cr/strip-xml-namespaces xml))) =>
      #{{:kuntalupatunnus "999-2017-11"
         :kuvaus "Kuvaus 999-2017-11"}
        {:kuntalupatunnus "999-2016-999"
         :kuvaus "Kuvaus 999-2016-999"}})))

(facts "Testing area like location information for application creation"
  (against-background
    (#'lupapalvelu.backing-system.krysp.reader/resolve-property-id-by-point anything) => "89552200010051"
    (#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point anything anything) => nil
    (sade.env/feature? :disable-ktj-on-create) => true)
  (let [xml                    (xml/parse (io/input-stream "resources/krysp/dev/verdict-rakval-with-area-like-location.xml"))
        info                   (get-app-info-from-message xml "895-2015-001")
        {:keys [x y address propertyId]
         :as   rakennuspaikka} (:rakennuspaikka info)]

    (fact "contains all the needed keys" (every? (-> rakennuspaikka keys set) [:x :y :address :propertyId]))

    (fact "Location point coordinates is calculated from area as interior point"
      x => #(and (instance? Double %) (= 192416.901187 %))
      y => #(and (instance? Double %) (= 6745788.046445 %)))

    (fact "Address is not changed, it's same as in xml"
      address => "Pitkäkarta 48")

    (fact "Property id is fetched from service"
      propertyId => "89552200010051")

    (fact "Original area is stored for metadata"
      (:geometry (first (:drawings info))) => (contains "POLYGON((192391.716803 6745749.455827, 192368.715229 6745821.2047, 192396.462342 6745826.766509")
      (get-in (first (:drawings info)) [:geometry-wgs84 :type]) => "Polygon"
      (->> (:drawings info)
           (first)
           :geometry-wgs84
           :coordinates
           (first)
           (first)) => [21.355934608866 60.728233303003])))

(facts "Testing area like location with building location information for application creation"
  (against-background
    (#'lupapalvelu.backing-system.krysp.reader/resolve-property-id-by-point anything) => "89552200010052"
    (#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point anything anything) => nil
    (sade.env/feature? :disable-ktj-on-create) => true)
  (let [xml (xml/parse (io/input-stream "resources/krysp/dev/verdict-rakval-with-building-location.xml"))
        info (get-app-info-from-message xml "895-2015-002")
        rakennuspaikka (:rakennuspaikka info)]

    (let [{:keys [x y address propertyId] :as rakennuspaikka} rakennuspaikka]
      (fact "contains all the needed keys" (every? (-> rakennuspaikka keys set) [:x :y :address :propertyId]))

      (fact "Location point coordinates is taken from first building location"
        x => #(and (instance? Double %) (= 192413.401 %))
        y => #(and (instance? Double %) (= 6745769.046 %)))

      (fact "Address is not changed, it's same as in xml"
        address => "Pitkäkarta 48")

      (fact "Property id is fetched from service"
        propertyId => "89552200010052"))

    (fact "Original area is stored for metadata"
      (:geometry (first (:drawings info))) => (contains "POLYGON((192391.716803 6745749.455827, 192368.715229 6745821.2047, 192396.462342 6745826.766509")
      (get-in (first (:drawings info)) [:geometry-wgs84 :type]) => "Polygon"
      (->> (:drawings info)
           (first)
           :geometry-wgs84
           :coordinates
           (first)
           (first)) => [21.355934608866 60.728233303003])))

(facts "Testing information parsed from verdict xml without location information"
  (against-background
    (#'lupapalvelu.backing-system.krysp.reader/resolve-property-id-by-point anything)
    => "47540208780003"
    (#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point anything anything)
    => nil
    (sade.env/feature? :disable-ktj-on-create) => true)
  (let [xml (xml/parse (io/input-stream "resources/krysp/dev/verdict-rakval-missing-location.xml"))
        info (get-app-info-from-message xml "475-2016-001")
        rakennuspaikka (:rakennuspaikka info)]

    (let [{:keys [x y address propertyId] :as rakennuspaikka} rakennuspaikka]
      (fact "contains all the needed keys" (every? (-> rakennuspaikka keys set) [:x :y :address :propertyId]))

      (fact "Location point coordinates is taken from first building location"
        x => #(and (instance? Double %) (= 219934.582 %))
        y => #(and (instance? Double %) (= 6997077.973 %)))

      (fact "Address is not changed, it's same as in xml"
        address => "Granorsvagen 32b")

      (fact "Property id is fetched from service"
        propertyId => "47540208780003")

      (fact "There should not be drawing when location is point"
        (:drawings info) => nil?))))

(facts "Testing information parsed from verdict xml without address"
  (against-background
    (#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point anything anything)
    => {:address "Testi osoite"}
    (#'lupapalvelu.backing-system.krysp.reader/build-address anything anything) => ""
    (#'lupapalvelu.backing-system.krysp.reader/resolve-property-id-by-point anything) => nil
    (sade.env/feature? :disable-ktj-on-create) => true)
  (let [xml (xml/parse (io/input-stream "resources/krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml"))
        info (get-app-info-from-message xml "14-0241-R 3")
        rakennuspaikka (:rakennuspaikka info)]

    (let [{:keys [x y address propertyId] :as rakennuspaikka} rakennuspaikka]
      (fact "contains all the needed keys" (every? (-> rakennuspaikka keys set) [:x :y :address :propertyId]))

      (fact "x" x => #(and (instance? Double %) (= 393033.614 %)))
      (fact "y" y => #(and (instance? Double %) (= 6707228.994 %)))

      (fact "Address comes from fallback (mocked) service for x and y"
        address => "Testi osoite")                              ; LP-366498

      (fact "Property id is from xml" propertyId => "18600303560006")

      (fact "There should not be drawing when location is point"
        (:drawings info) => nil?))))

(defn- prepare-xml [xml]
  (enlive/select (cr/strip-xml-namespaces xml) case-elem-selector))

(defn hiccup-xml [tags]
  (-> (hiccup/html tags)
      (xml/parse-string "utf8")
      cr/strip-xml-namespaces))

(defn make-point
  ([x y projection]
   [:Point {:srsDimension "2"
            :srsName      (str "urn:x-ogc:def:crs:" projection)}
    [:pos (str x " " y)]])
  ([x y]
   (make-point x y "EPSG:3067")))

(defn make-text
  [text]
  [:teksti {:xml:lang "fi"} text])

(defn make-rakennuksenTiedot [x y address]
  [:rakennuksenTiedot
   [:osoite
    [:osoitenimi (make-text address)]
    [:pistesijainti (make-point x y)]]])

(defn make-sijaintitieto [x y address]
  [:sijaintitieto
   [:Sijainti
    [:osoite [:osoitenimi (make-text address)]]
    [:piste (make-point x y)]]])

(let [xml   (hiccup-xml [:root
                         [:one
                          [:good (make-point 25.2348418944 60.2605051098 "EPSG:4258")]
                          [:bad (make-point 1 2)]]
                         [:two
                          [:good (make-point 408861.408 6682577.759)]
                          [:bad (make-point "x" "y")]]
                         [:three
                          [:good (make-point 2.5502936E7 6708332.0 "EPSG:3879")]
                          [:bad [:Point]]]])
      good1 [402326.492 6681730.17]
      good2 [408861.408 6682577.759]
      good3 [393033.614 6707228.994]]
  (facts "find-valid-point"
    (find-valid-point xml) => good1
    (find-valid-point xml [:root :good]) => good1
    (find-valid-point xml [:two]) => good2
    (find-valid-point xml [:bad]) => nil
    (find-valid-point xml [:one :bad]) => nil
    (find-valid-point xml [:two :bad]) => nil
    (find-valid-point xml [:three :bad]) => nil
    (find-valid-point xml [:nevada]) => nil
    (find-valid-point xml [:three :good]) => good3
    (find-valid-point xml [:three :good :Point]) => nil))

(facts "Tests for resolving coordinates from xml"
  (let [point-xml           (prepare-xml (xml/parse (io/input-stream "resources/krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml")))
        building-xml        (prepare-xml (xml/parse (io/input-stream "resources/krysp/dev/verdict-rakval-with-building-location.xml")))
        structure-xml       (prepare-xml (xml/parse (io/input-stream "resources/krysp/dev/verdict-rakval-with-structure-location.xml")))
        area-xml            (prepare-xml (xml/parse (io/input-stream "resources/krysp/dev/verdict-rakval-with-area-like-location.xml")))
        invalid-xml         (prepare-xml (xml/parse (io/input-stream "resources/krysp/dev/verdict-p.xml")))
        inv-area-xml        (prepare-xml (xml/parse "<rakval:Rakennusvalvonta><rakval:rakennusvalvontaAsiatieto><rakval:RakennusvalvontaAsia><rakval:rakennuspaikkatieto><rakval:Rakennuspaikka><yht:sijaintitieto><yht:Sijainti><yht:alue><gml:Polygon srsName=\"http://www.opengis.net/gml/srs/epsg.xml#3876\"><gml:outerBoundaryIs><gml:LinearRing><gml:coordinates>22464854.301,6735383.759 22464825.926,6735453.497</gml:coordinates></gml:LinearRing></gml:outerBoundaryIs></gml:Polygon></yht:alue></yht:Sijainti></yht:sijaintitieto></rakval:Rakennuspaikka></rakval:rakennuspaikkatieto></rakval:RakennusvalvontaAsia></rakval:rakennusvalvontaAsiatieto></rakval:Rakennusvalvonta>"))
        zero-coordinates    (prepare-xml (xml/parse "<rakval:Rakennusvalvonta><rakval:rakennusvalvontaAsiatieto><rakval:RakennusvalvontaAsia><rakval:rakennuspaikkatieto><rakval:Rakennuspaikka><rakval:sijaintitieto><rakval:Sijainti><yht:piste><gml:Point srsDimension=\"2\" srsName=\"urn:x-ogc:def:crs:EPSG:3876\"><gml:pos>0.0 0.0</gml:pos></gml:Point></yht:piste></rakval:Sijainti></rakval:sijaintitieto></rakval:Rakennuspaikka></rakval:rakennuspaikkatieto></rakval:RakennusvalvontaAsia></rakval:rakennusvalvontaAsiatieto></rakval:Rakennusvalvonta>"))
        non-zero            (prepare-xml (xml/parse "<rakval:Rakennusvalvonta><rakval:rakennusvalvontaAsiatieto><rakval:RakennusvalvontaAsia><rakval:rakennuspaikkatieto><rakval:Rakennuspaikka><rakval:sijaintitieto><rakval:Sijainti><yht:piste><gml:Point srsDimension=\"2\" srsName=\"urn:x-ogc:def:crs:EPSG:3876\"><gml:pos>1.0 2.0</gml:pos></gml:Point></yht:piste></rakval:Sijainti></rakval:sijaintitieto></rakval:Rakennuspaikka></rakval:rakennuspaikkatieto></rakval:RakennusvalvontaAsia></rakval:rakennusvalvontaAsiatieto></rakval:Rakennusvalvonta>"))
        make-rakennuspaikka (fn [coords-str]
                              {:tag     :Rakennuspaikka,
                               :content [{:tag     :sijaintitieto,
                                          :content [{:tag     :Sijainti,
                                                     :content [{:tag     :piste
                                                                :content [{:tag     :Point
                                                                           :content [{:tag :pos :content [coords-str]}]}]}]}]}]})
        reference-xml       (hiccup-xml [:rakval:Rakennusvalvonta
                                         [:rakval:rakennusvalvontaAsiatieto
                                          [:rakval:RakennusvalvontaAsia
                                           [:rakval:referenssiPiste
                                            [:gml:Point {:srsDimension "2" :srsName "urn:x-ogc:def:crs:EPSG:4258"}
                                             [:gml:pos "25.2348418944 60.2605051098"]]]]]])
        property-id-xml     (hiccup-xml [:rakennuspaikanKiinteistotieto
                                         [:RakennuspaikanKiinteisto
                                          [:kiinteistotieto
                                           [:Kiinteisto
                                            [:kiinteistotunnus "753001122"]]]]])]

    (facts "resolve-valid-location"
      (fact "point"
        (let [m (resolve-valid-location point-xml)]
          m => {:address "Kylykuja 3-5 D 35b-c" :propertyId "18600303560006"
                :x       393033.614             :y          6707228.994}
          (locator ::rdr/site {:lang "fi" :xml point-xml}) => m))
      (fact "building-xml"
        (resolve-valid-location building-xml)
        => {:address "Pitkäkarta 48" :propertyId "123"
            :x       192413.401      :y          6745769.046}
        (provided
          (#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point anything anything)
          => nil
          (#'lupapalvelu.backing-system.krysp.reader/resolve-property-id-by-point [192413.401 6745769.046])
          => "123")
        (locator ::rdr/structure {:lang "fi" :xml building-xml})
        => {:address "Pitkäkarta 48"
            :x       192413.401
            :y       6745769.046})
      (facts "structures"
        (fact "structure-xml"
          (resolve-valid-location structure-xml)
          => {:address "Hökkeli Highway" :propertyId "321"
              :x       567668.282        :y          7112614.623}
          (provided
            (#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point 567668.282 7112614.623)
            => {:address "Hökkeli Highway" :propertyId "321"}
            (#'lupapalvelu.backing-system.krysp.reader/resolve-property-id-by-point anything)
            => nil :times 0)
          (locator ::rdr/structure {:lang "fi" :xml structure-xml})
          => {:x 567668.282 :y 7112614.623})
        (let [house-xy   {:x 404958.595 :y 6695667.339 :address "House Halls" :propertyId "123"}
              house      [:Rakennus (make-rakennuksenTiedot (:x house-xy) (:y house-xy)
                                                            (:address house-xy))]
              hut-xy     {:x 402806.052 :y 6694345.405 :address "Baozi Hut" :propertyId "123"}
              hut        [:Rakennelma (make-sijaintitieto (:x hut-xy) (:y hut-xy) (:address hut-xy))]
              bunga-xy   {:x 403885.656 :y 6685873.857 :address "Cowabunga Low" :propertyId "123"}
              bungalow   [:Rakennus (make-sijaintitieto (:x bunga-xy) (:y bunga-xy)
                                                        (:address bunga-xy))]
              bad-house1 [:Rakennus (make-rakennuksenTiedot 1 2 "Bad House 1")]
              bad-house2 [:Rakennus (make-sijaintitieto 1 2 "Bad House 2")]
              bad-hut    [:Rakennelma (make-sijaintitieto 1 2 "Bad Hut")]
              make-xml   (fn [& tags] (hiccup-xml (conj [:RakennusvalvontaAsia] tags)))]
          (against-background
            [(#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point anything anything)
             => {:propertyId "123"}]
            (facts "Pointy houses and structures"
              (resolve-valid-location (make-xml house)) => house-xy
              (resolve-valid-location (make-xml hut)) => hut-xy
              (resolve-valid-location (make-xml bungalow)) => bunga-xy)

            (facts "Location priority order (buildings before structures, rakennus > rakennelma)"
              (resolve-valid-location (make-xml house hut bungalow)) => house-xy
              (resolve-valid-location (make-xml hut house)) => house-xy)

            (facts "Bad locations are ignored"
              (resolve-valid-location (make-xml bad-house1 bad-house2 bad-hut)) => nil
              (resolve-valid-location (make-xml bad-house1 hut bad-house2 bad-hut))
              => hut-xy
              (resolve-valid-location (make-xml bad-house1 hut bad-house2
                                                house bad-hut bungalow))
              => house-xy)

            (facts "Address and location siblings"
              (resolve-valid-location
                (make-xml [:Rakennus
                           [:sijaintitieto
                            [:Sijainti [:piste (make-point 402806.052 6694345.405)]]]
                           [:rakennuksenTiedot
                            [:osoite [:osoitenimi (make-text "Sibling Street")]]]]))
              => {:x 402806.052 :y 6694345.405 :address "Sibling Street" :propertyId "123"}
              (resolve-valid-location
                (make-xml [:Rakennus
                           [:sijaintitieto
                            [:Sijainti
                             [:osoite [:osoitenimi (make-text "Cousin Curve")]]]]
                           [:rakennuksenTiedot
                            [:osoite [:pistesijainti (make-point 402806.052 6694345.405)]]]]))
              => {:x 402806.052 :y 6694345.405 :address "Cousin Curve" :propertyId "123"})

            (fact "Owner location is ignored"
              (resolve-valid-location
                (make-xml [:Rakennus
                           [:omistajatieto
                            [:Omistaja
                             [:henkilo
                              [:osoite
                               [:osoitenimi (make-text "Owner Mansion")]
                               [:pistesijainti (make-point 404958.595 6695667.339)]]]]]]))
              => nil))))

      (fact "area"
        (resolve-valid-location area-xml)
        => {:address "Polygon Square" :propertyId "456"
            :x       192416.901187    :y          6745788.046445}
        (provided
          (#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point 192416.901187 6745788.046445)
          => {:address "Polygon Square"}
          (#'lupapalvelu.backing-system.krysp.reader/resolve-property-id-by-point [192416.901187 6745788.046445])
          => "456")
        (locator ::rdr/area {:xml area-xml}) => {:x 192416.901187 :y 6745788.046445})
      (fact "area with rakennuspaikka fallback (nothing from backend)"
        (resolve-valid-location area-xml)
        => {:address "Pitkäkarta 48" :propertyId "123456789"
            :x       192416.901187   :y          6745788.046445}
        (provided
          (#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point 192416.901187 6745788.046445)
          => nil
          (#'lupapalvelu.backing-system.krysp.reader/resolve-property-id-by-point [192416.901187 6745788.046445])
          => nil))
      (fact "referenssiPiste"
        (resolve-valid-location reference-xml)
        => {:address    "Reference Road 23"
            :propertyId "789"
            :x          402326.492
            :y          6681730.17}
        (provided (lupapalvelu.proxy-services/address-by-point-proxy
                    {:params {:x 402326.492 :y 6681730.17}})
                  => {:body (lupapalvelu.json/encode {:street     " Reference Road "
                                                      :number     "  23  "
                                                      :propertyId " 789 "})})
        (locator ::rdr/reference {:xml reference-xml}) => {:x 402326.492 :y 6681730.17})
      (fact "Propertyd id"
        (resolve-valid-location property-id-xml)
        => {:address    "Broberty Bahn"
            :propertyId "753001122"
            :x          402326.492
            :y          6681730.17}
        (provided
          (lupapalvelu.find-address/search-property-id "fi" "753001122")
          => [{:location {:x 402326.492 :y 6681730.17}}]
          (#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point 402326.492 6681730.17)
          => {:address    "Broberty Bahn"
              :propertyId "Will be ignored"})
        (locator ::rdr/property-id {:xml property-id-xml})
        => {:propertyId "753001122"
            :x          402326.492
            :y          6681730.17}
        (provided
          (lupapalvelu.find-address/search-property-id "fi" "753001122")
          => [{:location {:x 402326.492 :y 6681730.17}}])
        (resolve-valid-location property-id-xml)
        => {:address    "Tuntematon osoite"
            :propertyId "753001122"
            :x          402326.492
            :y          6681730.17}
        (provided
          (lupapalvelu.find-address/search-property-id "fi" "753001122")
          => [{:location {:x 402326.492 :y 6681730.17}}]
          (#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point 402326.492 6681730.17)
          => nil))
      (fact "invalid xml"
        (resolve-valid-location invalid-xml) => nil)
      (fact "invalid area"
        (resolve-valid-location inv-area-xml) => nil)
      (let [override   {:address    "Override Overpass"
                        :propertyId "75341600350362"
                        :x          403521
                        :y          6694756}
            fallback   {:address    "Fallback Falls"
                        :propertyId "753003344"
                        :x          402326.492
                        :y          6681730.17}
            override-fn #(when (= % :override) override)
            fallback-fn #(when (= % :fallback) fallback)]
        (fact "Overrides"
          (resolve-valid-location point-xml override-fn) => override
          (locator ::rdr/override {:default-location-fn override-fn})
          => override)
        (facts "Fallbacks"
          (resolve-valid-location invalid-xml fallback-fn) => fallback
          (locator ::rdr/fallback {:default-location-fn fallback-fn})
          => fallback
          (resolve-valid-location property-id-xml fallback-fn)
          => fallback
          (provided
            (lupapalvelu.find-address/search-property-id "fi" "753001122") => nil)
          (resolve-valid-location invalid-xml fallback) => fallback
          (resolve-valid-location invalid-xml (dissoc fallback :address))
          => (assoc fallback :address "Tuntematon osoite")
          (provided
            (#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point 402326.492 6681730.17)
            => nil
            (#'lupapalvelu.backing-system.krysp.reader/resolve-address anything anything) => nil)
          (resolve-valid-location property-id-xml {:x 192416.901187 :y 6745788.046445})
          => {:address    "Pointy Planes"
              :x          192416.901187
              :y          6745788.046445
              :propertyId "753001122"}
          (provided
            (lupapalvelu.find-address/search-property-id "fi" "753001122") => nil
            (#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point 192416.901187 6745788.046445)
            => {:address "Pointy Planes"}
            (#'lupapalvelu.backing-system.krysp.reader/resolve-property-id-by-point [192416.901187 6745788.046445])
            => nil)
          (resolve-valid-location invalid-xml {:x 192416.901187 :y 6745788.046445})
          => {:address "Tuntematon osoite"
              :x       192416.901187
              :y       6745788.046445}
          (provided
            (lupapalvelu.find-address/search-property-id anything anything) => nil :times 0
            (#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point 192416.901187 6745788.046445)
            => nil
            (#'lupapalvelu.backing-system.krysp.reader/resolve-property-id-by-point [192416.901187 6745788.046445])
            => nil)
          (resolve-valid-location invalid-xml {:x 1 :y 6}) => nil
          (resolve-valid-location invalid-xml {:propertyId "789"})
          => {:x 346276.0 :y 7000176.0 :address "Broberty Falls" :propertyId "789"}
          (provided
            (lupapalvelu.find-address/search-property-id "fi" "789")
            => [{:location {:x 346276.0 :y 7000176.0}}]
            (#'lupapalvelu.backing-system.krysp.reader/resolve-address-by-point 346276.0 7000176.0)
            => {:address "Broberty Falls"}))))))

(facts* "Tests for TJ/suunnittelijan verdicts parsing"
  (let [_ (xml/parse (io/input-stream "dev-resources/krysp/verdict-r-2.1.8-foremen.xml"))
        osapuoli {:alkamisPvm "2015-07-07"
                  :paattymisPvm "2015-07-10"
                  :sijaistettavaHlo "Pena Panaani"
                  :paatosPvm 1435708800000 ; 01.07.2015
                  :paatostyyppi "hyväksytty"}
        sijaistus {:alkamisPvm "07.07.2015"
                   :paattymisPvm "10.07.2015"
                   :sijaistettavaHloEtunimi "Pena"
                   :sijaistettavaHloSukunimi "Panaani"}]

    (facts "Sijaistus"
      (valid-sijaistustieto? nil nil) => falsey
      (valid-sijaistustieto? nil sijaistus) => falsey
      (valid-sijaistustieto? osapuoli nil) => truthy
      (fact "True when values match"
        (valid-sijaistustieto? osapuoli sijaistus) => truthy)
      (fact "Dates must match"
        (valid-sijaistustieto? (assoc osapuoli :alkamisPvm "2015-07-06") sijaistus) => falsey
        (valid-sijaistustieto? osapuoli (assoc sijaistus :paattymisPvm "09.07.2015")) => falsey)
      (fact "Name must match"
        (valid-sijaistustieto? (assoc osapuoli :sijaistettavaHlo "Panaani Pena") sijaistus) => falsey
        (valid-sijaistustieto? osapuoli (assoc sijaistus :sijaistettavaHloEtunimi "Paavo")) => falsey)
      (fact "Name can have whitespace"
        (valid-sijaistustieto? osapuoli (assoc sijaistus :sijaistettavaHloSukunimi "  Panaani ")) => truthy))

    (facts "Osapuoli with paatos data"
      (party-with-paatos-data nil nil) => falsey
      (party-with-paatos-data [osapuoli] sijaistus) => osapuoli
      (fact "PaatosPvm must be there"
        (party-with-paatos-data [(dissoc osapuoli :paatosPvm)] anything) => nil)
      (fact "Paatostyyppi must be correct"
        (party-with-paatos-data [(assoc osapuoli :paatostyyppi "test")] anything) => nil
        (party-with-paatos-data [(assoc osapuoli :paatostyyppi "hylätty")] sijaistus) => truthy)
      (fact "Sijaistus can be nil"
        (party-with-paatos-data [osapuoli] nil) => truthy)
      (fact "Sijaistus can be empty"
        (party-with-paatos-data [osapuoli] {}) => truthy))))

(fact "Poytakirjat in tj/suunnittelija verdicts"
  (let [xml             (->> (io/input-stream "dev-resources/krysp/verdict-r-2.2.3-foremen.xml")
                             xml/parse
                             cr/strip-xml-namespaces)
        yhteystiedot    {:email "r-koulutus-1@638.fi"}
        sijaistus       {:sijaistettavaHloEtunimi nil :sijaistettavaHloSukunimi nil :alkamisPvm nil :paattymisPvm nil}
        data            {:data {:sijaistus sijaistus :yhteystiedot yhteystiedot} }
        osapuoli-type   "tyonjohtaja"
        kuntaRoolikoodi "IV-työnjohtaja"
        poytakirjat     (->tj-suunnittelija-verdicts xml data osapuoli-type kuntaRoolikoodi)]
    (fact "Correct"
      (->> poytakirjat first :poytakirjat first)
      => {:status    "hyvaksytty"                 :paatoksentekija "Pena"
          :paatospvm (date/timestamp "17.1.2018") :pykala          "1"
          :liite     {:kuvaus "kuvaus 1" :linkkiliitteeseen "http://localhost:8000/dev/sample-verdict.pdf"}})
    (facts "tj-suunnitelija-verdicts-validtor"
      (fact "OK"
        (tj-suunnittelija-verdicts-validator data xml osapuoli-type kuntaRoolikoodi) => nil)
      (fact "Verdict date today"
        (tj-suunnittelija-verdicts-validator data
                                             (enlive/at xml
                                                        [:Tyonjohtaja :paatosPvm]
                                                        (enlive/content (date/iso-date (date/today) :local)))
                                             osapuoli-type kuntaRoolikoodi)
        => {:ok false :text "info.paatos-future-date"}))))

(facts "application-state"
  (fact "state found"
    (->> (build-multi-app-xml [[{:pvm "2016-09-09Z" :tila "rakennustyöt aloitettu"}]])
         (cr/strip-xml-namespaces)
         (application-state)) => "rakennustyöt aloitettu")

  (fact "nil xml"
    (application-state nil) => nil)

  (fact "not found"
    (->> (build-multi-app-xml [[{:lp-tunnus "123"}]])
         (cr/strip-xml-namespaces)
         (application-state)) => nil)

  (fact "multiple state - pick the latest"
    (->> (build-multi-app-xml [[{:pvm "2016-09-09Z" :tila "rakennustyöt aloitettu"}
                                {:pvm "2016-09-11Z" :tila "lupa rauennut"}
                                {:pvm "2016-09-10Z" :tila "rakennustyöt keskeytetty"}]])
         (cr/strip-xml-namespaces)
         (application-state)) => "lupa rauennut")
  (fact "multiple state same day - pick correct by 'domain chronological ordering'"
    (->> (build-multi-app-xml [[{:pvm "2016-09-09Z" :tila "rakennustyöt aloitettu"}
                                {:pvm "2016-09-11Z" :tila "lopullinen loppukatselmus tehty"}
                                {:pvm "2016-09-11Z" :tila "lupa rauennut"}
                                {:pvm "2016-09-10Z" :tila "rakennustyöt keskeytetty"}]])
         (cr/strip-xml-namespaces)
         (application-state)) => "lopullinen loppukatselmus tehty")

  (fact "RakennusvalvontaAsia"
    (-> (build-multi-app-xml [[{:pvm "2016-09-09Z" :tila "rakennustyöt aloitettu"}]])
        (cr/strip-xml-namespaces)
        (xml/select [:RakennusvalvontaAsia])
        (first)
        (application-state))
    => "rakennustyöt aloitettu")
  (facts "From XML files"
    (let [file (xml-without-ns-from-file "krysp/verdict-r-2.2.2.xml")]
      (facts "r-verdict 2.2.2"
        (fact "Rakennusvalvonta element"
          (application-state file) => "ei tiedossa")
        (fact "RakennusvalvontaAsia element"
          (-> file (xml/select [:RakennusvalvontaAsia]) first application-state) => "ei tiedossa")))
    (facts "r-review.xml"
      (let [file (xml-without-ns-from-file "krysp/dev/r-verdict-review.xml")]
        (fact "Rakennusvalvonta element"
          (application-state file) => "rakennustyöt aloitettu")
        (fact "RakennusvalvontaAsia element"
          (-> file (xml/select [:RakennusvalvontaAsia]) first application-state) => "rakennustyöt aloitettu")))))

(def- verdict-a
  (krysp-fetch/get-local-application-xml-by-filename "./dev-resources/krysp/verdict-r-2.1.6-type-A.xml" "R"))

(def- verdict-tjo
  (krysp-fetch/get-local-application-xml-by-filename "./dev-resources/krysp/verdict-r-2.1.6-type-TJO.xml" "R"))

(fact "viitelupatunnus-id's can be extracted from messages"
  (let [ids-a (->viitelupatunnukset verdict-a)
        ids-tjo (->viitelupatunnukset verdict-tjo)]
    (facts "the results are not empty"
      (count ids-a) => pos?
      (count ids-tjo) => pos?)
    (fact "The extracted ids are unique"
      (let [x (krysp-fetch/get-local-application-xml-by-filename "dev-resources/krysp/99-0000-13-A.xml" "R")]
        (->> (xml/select x [:viitelupatieto :LupaTunnus :kuntalupatunnus])
             (mapcat :content))
        => (take 9 (repeat "00-0000-TJO 00"))
        (->viitelupatunnukset x) => ["00-0000-TJO 00"]))))

(facts "kuntalupatunnus ids can be extracted from XML"
  (->kuntalupatunnus verdict-a) => "01-0001-12-A"
  (->kuntalupatunnus verdict-tjo) => "01-0012-00-TJO")

(facts "It can be deduced if the verdict is about a foreman or not"
  (is-foreman-application? verdict-a) => false
  (is-foreman-application? verdict-tjo) => true)

(facts "Statements can be read from a Krysp document"
  (-> verdict-a ->lausuntotiedot first :viranomainen) => "Huvikummun Voima"
  (-> verdict-a ->lausuntotiedot first :pyyntoPvm) => "1999-12-24Z"
  (-> verdict-a ->lausuntotiedot first :lausuntoPvm) => "2000-01-05Z"
  (-> verdict-tjo ->lausuntotiedot first :viranomainen) => nil)

(fact "build-jakokirjain"
  (build-jakokirjain "j1" "j2") => "j1-j2"
  (build-jakokirjain "" "j2") => "j2"
  (build-jakokirjain nil "j2") => "j2"
  (build-jakokirjain "j1" "") => "j1"
  (build-jakokirjain "j1" nil) => "j1"
  (build-jakokirjain nil "j2") => "j2"
  (build-jakokirjain "" nil) => nil
  (build-jakokirjain "j1" "") => "j1"
  (build-jakokirjain "" "") => nil
  (build-jakokirjain nil nil) => nil
  (build-jakokirjain "  " "   ") => nil
  (build-jakokirjain "  one  " "  two  ") => "one-two")

(facts "Kuntalupatunnus can be read straight from the XML"
  (letfn [(get-tunnus [filename]
            (xml->kuntalupatunnus
              (krysp-fetch/get-local-application-xml-by-filename (str "./dev-resources/krysp/" filename) "R")))]
    (get-tunnus "verdict-r-2.1.6-type-A.xml") => "01-0001-12-A"
    (get-tunnus "verdict-r.xml") => "13-0185-R"
    (get-tunnus "verdict-r-2.1.6-type-TJO.xml") => "01-0012-00-TJO"
    (get-tunnus "verdict-r-2.2.2-foremen.xml") => "638-2018-0005"))

(fact "get-osoite"
  (get-osoite {:tag :osoitenimi :attrs nil :content
               [{:tag :teksti :attrs nil, :content ["Testikatu"]}
                {:tag :osoitenumero :attrs nil :content ["1"]}]})
  => "Testikatu 1"
  (get-osoite {:tag :osoitenimi :attrs nil :content
               [{:tag :teksti :attrs nil, :content ["Testikatu"]}
                {:tag :osoitenumero :attrs nil :content ["1"]}
                {:tag :jakokirjain :attrs nil :content ["c"]}]})
  => "Testikatu 1c"
  (get-osoite {:tag :osoitenimi :attrs nil :content
               [{:tag :teksti :attrs nil, :content ["Testikatu"]}
                {:tag :osoitenumero :attrs nil :content ["1"]}
                {:tag :osoitenumero2 :attrs nil :content ["8"]}
                {:tag :jakokirjain :attrs nil :content ["c"]}
                {:tag :porras :attrs nil :content ["A"]}
                {:tag :huoneisto :attrs nil :content ["10"]}]})
  => "Testikatu 1\u20138c A 10")

(facts "LP ID validation"
  (against-background [(org/get-krysp-wfs {:organization "123" :permitType "R"}) => {:url "foo-url"}
                       (sade.env/dev-mode?) => false]
                      (fact "nil is returned if LP ID does not match"
                        (krysp-fetch/get-valid-xml-by-application-id {:id "LP-123-123" :organization "123" :permitType "R"})
                        =>
                        nil
                        (provided
                          (permit/fetch-xml-from-krysp "R" "foo-url" nil ["LP-123-123"] :application-id nil)
                          =>
                          (-> (io/resource "krysp/verdict-r-2.2.2.xml") (slurp) (xml/parse))))

                      (let [parsed (-> (io/resource "krysp/verdict-r-2.2.2.xml") (slurp) (xml/parse))
                            id-in-xml (get-lp-tunnus "R" [] (cr/strip-xml-namespaces parsed))]
                        (fact "XML is returned if LP-ID matches"
                          (krysp-fetch/get-valid-xml-by-application-id {:id id-in-xml :organization "123" :permitType "R"})
                          =>
                          map?
                          (provided
                            (permit/fetch-xml-from-krysp "R" "foo-url" nil [id-in-xml] :application-id nil) => parsed)))))

(facts "get-tunnus-elems"
  (fact "verdict-r-2.2.2.xml"
    (->> (xml-without-ns-from-file "krysp/verdict-r-2.2.2.xml")
         (get-tunnus-elems :R)
         (keep xml/text))
    => ["LP-186-2013-00002" "LP-186-2013-00002"])
  (fact "verdict louhi"
    (->> (xml-without-ns-from-file "krysp/verdict-r-feature-collection-louhi.xml")
         (get-tunnus-elems :R)
         (keep xml/text))
    => ["LP-999-2018-12345" "LP-999-2019-11111"])
  (fact "verdict Trimble"
    (->> (xml-without-ns-from-file "krysp/verdict-r-feature-collection-trimble.xml")
         (get-tunnus-elems :R)
         (keep xml/text))
    => ["LP-543-2018-00161"
        "LP-543-2018-00161"
        "LP-543-2019-01196"
        "Lupapiste"
        "toimenpideId"
        "5dcbfadc59412f16618fe93d"]))
