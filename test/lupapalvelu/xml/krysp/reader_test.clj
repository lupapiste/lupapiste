(ns lupapalvelu.xml.krysp.reader-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [net.cgrand.enlive-html :as enlive]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [clj-time.coerce :as coerce]
            [sade.core :refer [def-]]
            [sade.common-reader :as cr]
            [sade.strings :as ss]
            [sade.xml :as xml]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.xml.krysp.reader :refer :all]
            [lupapalvelu.xml.krysp.common-reader :refer [rakval-case-type property-equals property-in wfs-krysp-url case-elem-selector]]
            [lupapalvelu.xml.krysp.review-reader :as review-reader]
            [lupapalvelu.xml.validator :as xml-validator]
            [lupapalvelu.krysp-test-util :refer [build-multi-app-xml]]
            [lupapalvelu.permit :as permit]))

(defn- to-timestamp [yyyy-mm-dd]
  (coerce/to-long (coerce/from-string yyyy-mm-dd)))

(testable-privates lupapalvelu.xml.krysp.reader
  ->standard-verdicts
  standard-verdicts-validator
  simple-verdicts-validator
  ->simple-verdicts
  party-with-paatos-data
  valid-sijaistustieto?
  resolve-address-by-point)

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

(def future-verdict (verdict-skeleton [{:tag :paatoskoodi :content ["hyv\u00e4ksytty"]}
                                       {:tag :paatoksentekija :content [""]}
                                       {:tag :paatospvm :content ["1970-01-02"]}]))

(def verdict-to-be-given-in-future {:tag :paatostieto
                                    :content [{:tag :paatostieto
                                               :content [{:tag :Paatos
                                                          :content [{:tag :paivamaarat :content [{:tag :antoPvm :content ["2099-01-02"]}]}
                                                                    {:tag :poytakirja :content [{:tag :paatoskoodi :content ["hyv\u00e4ksytty"]}
                                                                                                {:tag :paatoksentekija :content [""]}
                                                                                                {:tag :paatospvm :content ["1970-01-02"]}]}]}]}]})

(def past-verdict (verdict-skeleton [{:tag :paatoskoodi :content ["hyv\u00e4ksytty"]}
                                     {:tag :paatoksentekija :content [""]}
                                     {:tag :paatospvm :content ["1970-01-01"]}]))

(facts "standard-verdicts-validator"
  (against-background
    (sade.util/get-timestamp-ago :day 1) => (+ (to-timestamp "1970-01-01") 100))
  (fact "Missing details"
    (standard-verdicts-validator (verdict-skeleton []) {}) => {:ok false, :text "info.paatos-details-missing"})
  (fact "Future date"
    (standard-verdicts-validator future-verdict {}) => {:ok false, :text "info.paatos-future-date"} )
  (fact "Verdict to be given on a future date"
    (standard-verdicts-validator verdict-to-be-given-in-future {}) => {:ok false, :text "info.paatos-future-date"} )
  (fact "Past date"
    (standard-verdicts-validator past-verdict {}) => nil))

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
  (let [xml (xml/parse (slurp "resources/krysp/dev/verdict.xml"))
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
        (:viimeinenValitus paivamaarat) => (to-timestamp "2018-09-06")
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
          (:linkkiliitteeseen liite) => "http://localhost:8000/dev/sample-verdict.pdf"
          (:muokkausHetki liite) => (to-timestamp "2013-09-01T12:00:00")
          (:versionumero liite) => "1"
          (get-in liite [:tekija :henkilo :nimi :sukunimi]) => "Tarkkanen"
          (:tyyppi liite) => "paatos"
          (:metadata liite) => {:nimi "arvo"})

        (facts "second verdict"
          (let [poytakirjat2 (-> cases last :paatokset last :poytakirjat)]
            (count poytakirjat2) => 1
            poytakirjat2 => sequential?))))))

(facts "KRYSP verdict 2.1.8"
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-2.1.8.xml"))
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

      (fact "m\u00e4\u00e4r\u00e4ykset"
        (count maaraykset) => 2
        (:sisalto (first maaraykset)) => "Radontekninen suunnitelma"
        (:maaraysaika (first maaraykset)) => (to-timestamp "2013-08-28")
        (:toteutusHetki (last maaraykset)) => (to-timestamp "2013-08-31")))))

(facts "KRYSP verdict 2.1.8"
 (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-2.1.8-foremen.xml"))
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
         vaaditut-erityissuunnitelmat => (just ["Rakennesuunnitelmat" "Vesi- ja viem\u00e4risuunnitelmat" "Ilmanvaihtosuunnitelmat"] :in-any-order)))))


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

      (facts "lupamaaraukset data is correct"
        lupamaaraykset => truthy
        (:rakennusoikeudellinenKerrosala lupamaaraykset) => "101"
        (:vaaditutTyonjohtajat lupamaaraykset) => "IV-ty\u00f6njohtaja, KVV-ty\u00f6njohtaja, vastaava ty\u00f6njohtaja"
        (:vaaditutErityissuunnitelmat lupamaaraykset) => (just ["ES 1" "ES 22" "ES 333"] :in-any-order))

      (fact "m\u00e4\u00e4r\u00e4ykset"
        (count maaraykset) => 2
        (:sisalto (first maaraykset)) => "Radontekninen suunnitelma"
        (:maaraysaika (first maaraykset)) => (to-timestamp "2013-08-28")
        (:toteutusHetki (last maaraykset)) => (to-timestamp "2013-08-31")))))

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

(facts "CGI sample verdict"
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r.xml"))
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
          (:linkkiliitteeseen liite) => "http://localhost:8000/dev/sample-attachment.txt"
          (:muokkausHetki liite) => (to-timestamp "2013-09-03T15:27:46")
          (:tyyppi liite) => "P\u00e4\u00e4t\u00f6sote")))))

(facts "Tekla sample verdict"
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-buildings.xml"))
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

      (facts "lupamaaraukset data is correct"
        lupamaaraykset => truthy
        (:kerrosala lupamaaraykset) => "134"
        (:kokonaisala lupamaaraykset) => "134"
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
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-not-found.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]
    (fact "xml is parsed" cases => truthy)
    (fact "xml has no cases" (count cases) => 0)
    (fact "validator does not find verdicts" (standard-verdicts-validator xml {}) => {:ok false, :text "info.no-verdicts-found-from-backend"})))

(facts "nil xml"
  (let [cases (->verdicts nil :R permit/read-verdict-xml)]
    (seq cases) => nil
    (count cases) => 0))

(facts "no verdicts"
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-no-verdicts.xml"))
        cases (->verdicts xml :R permit/read-verdict-xml)]
    (fact "xml is parsed" cases => truthy)
    (fact "xml has 1 case" (count cases) => 1)
    (fact "kuntalupatunnus" (:kuntalupatunnus (last cases)) => "13-0185-R")
    (fact "validator does not find verdicts" (standard-verdicts-validator xml {}) => {:ok false, :text "info.no-verdicts-found-from-backend"})
    (fact "case has no verdicts" (-> cases last :paatokset count) => 0)))

(facts "KRYSP verdict with reviews"
  (let [xml (-> "resources/krysp/dev/r-verdict-review.xml" slurp xml/parse)
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
  (let [xml (xml/parse (slurp "resources/krysp/dev/verdict-ya.xml"))
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
          (:linkkiliitteeseen liite) => "http://localhost:8000/dev/sample-attachment.txt"
          (:muokkausHetki liite) => (to-timestamp "2014-01-29T13:58:15")
          (:tyyppi liite) => "Muu liite")))))

(facts "Ymparisto verdicts"
  (doseq [permit-type ["YL" "MAL" "VVVL"]]
    (let [xml (xml/parse (slurp (str "resources/krysp/dev/verdict-" (ss/lower-case permit-type) ".xml")))
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
          (:linkkiliitteeseen liite) => "http://localhost:8000/dev/sample-attachment.txt"
          (:muokkausHetki liite) => (to-timestamp "2014-03-29T13:58:15")
          (:tyyppi liite) => "Muu liite"))))))

(facts "Maaraykset from verdict message"
  (let [xml (xml/parse (slurp "dev-resources/krysp/verdict-r-empty-maarays-element.xml"))
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
  (against-background (sade.env/feature? :disable-ktj-on-create) => true)
  (let [xml (xml/parse (slurp "resources/krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml"))
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

(facts "Multiple features with different descriptions in the same XML file"
  (let [xml (xml/parse (slurp "resources/krysp/dev/feature-collection-with-many-featureMember-elems.xml"))]
   (fact "rakennusvalvontaasianKuvaus"
         (set (read-permit-descriptions-from-xml :R (cr/strip-xml-namespaces xml))) =>
         #{{:kuntalupatunnus "999-2017-11"
            :kuvaus "Kuvaus 999-2017-11"}
           {:kuntalupatunnus "999-2016-999"
            :kuvaus "Kuvaus 999-2016-999"}})))

(facts "Testing area like location information for application creation"
  (against-background
    (resolve-property-id-by-point anything) => "89552200010051"
    (sade.env/feature? :disable-ktj-on-create) => true)
  (let [xml (xml/parse (slurp "resources/krysp/dev/verdict-rakval-with-area-like-location.xml"))
        info (get-app-info-from-message xml "895-2015-001")
        rakennuspaikka (:rakennuspaikka info)]

    (let [{:keys [x y address propertyId] :as rakennuspaikka} rakennuspaikka]
      (fact "contains all the needed keys" (every? (-> rakennuspaikka keys set) [:x :y :address :propertyId]))

      (fact "Location point coordinates is calculated from area as interior point"
        x => #(and (instance? Double %) (= 192416.901187 %))
        y => #(and (instance? Double %) (= 6745788.046445 %)))

      (fact "Address is not changed, it's same as in xml"
        address => "Pitk\u00e4karta 48")

      (fact "Property id is fetched from service"
        propertyId => "89552200010051"))

    (fact "Original area is stored for metadata"
      (:geometry (first (:drawings info))) => (contains "POLYGON((192391.716803 6745749.455827, 192368.715229 6745821.2047, 192396.462342 6745826.766509")
      (get-in (first (:drawings info)) [:geometry-wgs84 :type]) => "Polygon"
      (->> (:drawings info)
           (first)
           :geometry-wgs84
           :coordinates
           (first)
           (first)) => [21.355934608866 60.728233303])))

(facts "Testing area like location with building location information for application creation"
  (against-background
    (resolve-property-id-by-point anything) => "89552200010052"
    (sade.env/feature? :disable-ktj-on-create) => true)
  (let [xml (xml/parse (slurp "resources/krysp/dev/verdict-rakval-with-building-location.xml"))
        info (get-app-info-from-message xml "895-2015-002")
        rakennuspaikka (:rakennuspaikka info)]

    (let [{:keys [x y address propertyId] :as rakennuspaikka} rakennuspaikka]
      (fact "contains all the needed keys" (every? (-> rakennuspaikka keys set) [:x :y :address :propertyId]))

      (fact "Location point coordinates is taken from first building location"
        x => #(and (instance? Double %) (= 192413.401 %))
        y => #(and (instance? Double %) (= 6745769.046 %)))

      (fact "Address is not changed, it's same as in xml"
        address => "Pitk\u00e4karta 48")

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
           (first)) => [21.355934608866 60.728233303])))

(facts "Testing information parsed from verdict xml without location information"
  (against-background
    (resolve-property-id-by-point anything) => "47540208780003"
    (sade.env/feature? :disable-ktj-on-create) => true)
  (let [xml (xml/parse (slurp "resources/krysp/dev/verdict-rakval-missing-location.xml"))
        info (get-app-info-from-message xml "475-2016-001")
        rakennuspaikka (:rakennuspaikka info)]

    (let [{:keys [x y address propertyId] :as rakennuspaikka} rakennuspaikka]
      (fact "contains all the needed keys" (every? (-> rakennuspaikka keys set) [:x :y :address :propertyId]))

      (fact "Location point coordinates is taken from first building location"
        x => #(and (instance? Double %) (= 219934.582 %))
        y => #(and (instance? Double %) (= 6997077.973 %)))

      (fact "Address is not changed, it's same as in xml"
        address => "Granorsvagen 32")

      (fact "Property id is fetched from service"
        propertyId => "47540208780003")

      (fact "There should not be drawing when location is point"
        (:drawings info) => nil?))))

(facts "Testing information parsed from verdict xml without address"
  (against-background
    (#'lupapalvelu.xml.krysp.reader/resolve-address-by-point anything anything) => "Testi osoite"
    (#'lupapalvelu.xml.krysp.reader/build-address anything anything) => ""
    (sade.env/feature? :disable-ktj-on-create) => true)
  (let [xml (xml/parse (slurp "resources/krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml"))
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

(facts "Tests for resolving coordinates from xml"
  (let [point-xml     (prepare-xml (xml/parse (slurp "resources/krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml")))
        building-xml  (prepare-xml (xml/parse (slurp "resources/krysp/dev/verdict-rakval-with-building-location.xml")))
        area-xml      (prepare-xml (xml/parse (slurp "resources/krysp/dev/verdict-rakval-with-area-like-location.xml")))
        invalid-xml   (prepare-xml (xml/parse (slurp "resources/krysp/dev/verdict-p.xml")))
        inv-area-xml  (prepare-xml (xml/parse "<rakval:Rakennusvalvonta><rakval:rakennusvalvontaAsiatieto><rakval:RakennusvalvontaAsia><rakval:rakennuspaikkatieto><rakval:Rakennuspaikka><yht:sijaintitieto><yht:Sijainti><yht:alue><gml:Polygon srsName=\"http://www.opengis.net/gml/srs/epsg.xml#3876\"><gml:outerBoundaryIs><gml:LinearRing><gml:coordinates>22464854.301,6735383.759 22464825.926,6735453.497</gml:coordinates></gml:LinearRing></gml:outerBoundaryIs></gml:Polygon></yht:alue></yht:Sijainti></yht:sijaintitieto></rakval:Rakennuspaikka></rakval:rakennuspaikkatieto></rakval:RakennusvalvontaAsia></rakval:rakennusvalvontaAsiatieto></rakval:Rakennusvalvonta>"))
        zero-coordinates (prepare-xml (xml/parse "<rakval:Rakennusvalvonta><rakval:rakennusvalvontaAsiatieto><rakval:RakennusvalvontaAsia><rakval:rakennuspaikkatieto><rakval:Rakennuspaikka><rakval:sijaintitieto><rakval:Sijainti><yht:piste><gml:Point srsDimension=\"2\" srsName=\"urn:x-ogc:def:crs:EPSG:3876\"><gml:pos>0.0 0.0</gml:pos></gml:Point></yht:piste></rakval:Sijainti></rakval:sijaintitieto></rakval:Rakennuspaikka></rakval:rakennuspaikkatieto></rakval:RakennusvalvontaAsia></rakval:rakennusvalvontaAsiatieto></rakval:Rakennusvalvonta>"))
        non-zero (prepare-xml (xml/parse "<rakval:Rakennusvalvonta><rakval:rakennusvalvontaAsiatieto><rakval:RakennusvalvontaAsia><rakval:rakennuspaikkatieto><rakval:Rakennuspaikka><rakval:sijaintitieto><rakval:Sijainti><yht:piste><gml:Point srsDimension=\"2\" srsName=\"urn:x-ogc:def:crs:EPSG:3876\"><gml:pos>1.0 2.0</gml:pos></gml:Point></yht:piste></rakval:Sijainti></rakval:sijaintitieto></rakval:Rakennuspaikka></rakval:rakennuspaikkatieto></rakval:RakennusvalvontaAsia></rakval:rakennusvalvontaAsiatieto></rakval:Rakennusvalvonta>"))]

    (fact "Should resolve coordinate type"
      (resolve-coordinate-type point-xml) => :point
      (resolve-coordinate-type building-xml) => :building
      (resolve-coordinate-type area-xml) => :area
      (resolve-coordinate-type invalid-xml) => nil
      )

    (fact "select1 non-zero point"
      (fact "Rakennnuspaikka"
        (select1-non-zero-point point-xml [:rakennuspaikkatieto :Rakennuspaikka :sijaintitieto :Sijainti :piste]) => map?)
      (fact "Rakennus"
        (select1-non-zero-point point-xml [:Rakennus :sijaintitieto :Sijainti :piste :Point]) => map?)
      (fact "zero xml"
        (select1-non-zero-point zero-coordinates [:rakennuspaikkatieto :Rakennuspaikka :sijaintitieto :Sijainti :piste]) => nil?)
      (fact "non-zero xml"
        (select1-non-zero-point non-zero [:rakennuspaikkatieto :Rakennuspaikka :sijaintitieto :Sijainti :piste]) => map?)
      (fact "invalid"
        (select1-non-zero-point inv-area-xml [:Rakennus :sijaintitieto :Sijainti :piste :Point]) => nil))

    (fact "Should resolve coordinates"
      (resolve-coordinates point-xml) => [393033.614 6707228.994]
      (resolve-coordinates building-xml ) => [192413.401 6745769.046]
      (resolve-coordinates area-xml) => [192416.901187 6745788.046445]
      (resolve-coordinates invalid-xml) => nil
      (resolve-coordinates inv-area-xml) => nil)))


(facts* "Tests for TJ/suunnittelijan verdicts parsing"
  (let [_ (xml/parse (slurp "dev-resources/krysp/verdict-r-2.1.8-foremen.xml"))
        osapuoli {:alkamisPvm "2015-07-07"
                  :paattymisPvm "2015-07-10"
                  :sijaistettavaHlo "Pena Panaani"
                  :paatosPvm 1435708800000 ; 01.07.2015
                  :paatostyyppi "hyv\u00e4ksytty"}
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
        (party-with-paatos-data [(assoc osapuoli :paatostyyppi "hyl\u00e4tty")] sijaistus) => truthy)
      (fact "Sijaistus can be nil"
        (party-with-paatos-data [osapuoli] nil) => truthy)
      (fact "Sijaistus can be empty"
        (party-with-paatos-data [osapuoli] {}) => truthy))))

(facts application-state
  (fact "state found"
    (->> (build-multi-app-xml [[{:pvm "2016-09-09Z" :tila "rakennusty\u00f6t aloitettu"}]])
         (cr/strip-xml-namespaces)
         (application-state)) => "rakennusty\u00f6t aloitettu")

  (fact "nil xml"
    (application-state nil) => nil)

  (fact "not found"
    (->> (build-multi-app-xml [[{:lp-tunnus "123"}]])
         (cr/strip-xml-namespaces)
         (application-state)) => nil)

  (fact "multiple state - pick the latest"
    (->> (build-multi-app-xml [[{:pvm "2016-09-09Z" :tila "rakennusty\u00f6t aloitettu"}
                                {:pvm "2016-09-11Z" :tila "lupa rauennut"}
                                {:pvm "2016-09-10Z" :tila "rakennusty\u00f6t keskeytetty"}]])
         (cr/strip-xml-namespaces)
         (application-state)) => "lupa rauennut")
  (fact "multiple state same day - pick correct by 'domain chronological ordering'"
    (->> (build-multi-app-xml [[{:pvm "2016-09-09Z" :tila "rakennusty\u00f6t aloitettu"}
                                {:pvm "2016-09-11Z" :tila "lopullinen loppukatselmus tehty"}
                                {:pvm "2016-09-11Z" :tila "lupa rauennut"}
                                {:pvm "2016-09-10Z" :tila "rakennusty\u00f6t keskeytetty"}]])
         (cr/strip-xml-namespaces)
         (application-state)) => "lopullinen loppukatselmus tehty"))

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
    (facts "the ids are returned in the right format (with type code last)"
      (letfn [(get-code [id]
                (-> id (ss/split #"-") last))
              (code-is-valid? [code]
                (= code (apply str (filter #(Character/isLetter %) code))))
              (every-code-is-valid? [idseq]
                (->> idseq
                     (map (comp code-is-valid? get-code))
                     (every? true?)))]
        (every-code-is-valid? ids-a) => true
        (every-code-is-valid? ids-tjo) => true))))

(facts "kuntalupatunnus ids can be extracted from XML"
  (->kuntalupatunnus verdict-a) => "01-0001-12-A"
  (->kuntalupatunnus verdict-tjo) => "01-0012-00-TJO")

(facts "It can be deduced if the verdict is about a foreman or not"
  (is-foreman-application? verdict-a) => false
  (is-foreman-application? verdict-tjo) => true)
